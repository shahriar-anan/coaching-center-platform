package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.auth.StudentPhoneGuard;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.users.StudentProfile;
import com.coachingcenter.api.users.StudentProfileRepository;
import com.coachingcenter.api.users.User;
import com.coachingcenter.api.users.UserRepository;

import tools.jackson.databind.JsonNode;

@Service
public class LiveExamOrderService {

	private static final Logger log = LoggerFactory.getLogger(LiveExamOrderService.class);

	private final LiveExamOrderRepository orders;

	private final LiveExamOrderTx tx;

	private final LiveExamModuleRepository modules;

	private final LiveExamRepository exams;

	private final LiveExamRosterRepository roster;

	private final LiveExamGrantRepository grants;

	private final LiveExamCatalogVisibility visibility;

	private final UserRepository users;

	private final StudentProfileRepository profiles;

	private final ObjectProvider<LiveExamPaymentGateway> gateways;

	private final StudentPhoneGuard phones;

	private final LiveExamOrderBundle bundles;

	public LiveExamOrderService(LiveExamOrderRepository orders, LiveExamOrderTx tx, LiveExamModuleRepository modules,
			LiveExamRepository exams, LiveExamRosterRepository roster, LiveExamGrantRepository grants,
			LiveExamCatalogVisibility visibility, UserRepository users, StudentProfileRepository profiles,
			ObjectProvider<LiveExamPaymentGateway> gateways, StudentPhoneGuard phones, LiveExamOrderBundle bundles) {
		this.orders = orders;
		this.tx = tx;
		this.modules = modules;
		this.exams = exams;
		this.roster = roster;
		this.grants = grants;
		this.visibility = visibility;
		this.users = users;
		this.profiles = profiles;
		this.gateways = gateways;
		this.phones = phones;
		this.bundles = bundles;
	}

	public InitiateResult initiate(UUID studentId, JsonNode body) {
		phones.require(studentId);
		Target target = readTarget(body);
		rejectOwned(studentId, target);
		if (gateways.getIfAvailable() == null) {
			throw unavailable();
		}
		Optional<LiveExamOrder> open = findOpen(studentId, target);
		if (open.isPresent()) {
			return checkout(open.get(), false, studentId, target.productName());
		}
		if (LiveExamOrderBundle.SCOPE.equals(target.scope())) {
			LiveExamOrderBundle.Inserted inserted = tx.insertBundle(studentId, target.moduleId(), target.examIds(),
					target.amount());
			return checkout(inserted.order(), inserted.created(), studentId, target.productName());
		}
		LiveExamOrder order;
		try {
			order = tx.insert(studentId, target.scope(), target.moduleId(), target.examId(), target.amount());
		}
		catch (DataIntegrityViolationException exception) {
			if (!isOpenOrderRace(exception)) {
				throw exception;
			}
			order = findOpen(studentId, target).orElseThrow(this::notFound);
			return checkout(order, false, studentId, target.productName());
		}
		return checkout(order, true, studentId, target.productName());
	}

	private InitiateResult checkout(LiveExamOrder order, boolean created, UUID studentId, String productName) {
		try {
			return new InitiateResult(created, view(order, redirect(order, studentId, productName)));
		}
		catch (ApiException exception) {
			if (exception.code() != ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE) {
				throw exception;
			}
			throw unavailable(order);
		}
	}

	public OrderView returned(String tranId, String bodyTranId, String valId) {
		rejectTranMismatch(tranId, bodyTranId);
		if (valId == null || valId.isBlank()) {
			return lookupReturn(tranId);
		}
		return grantOrLeavePending(tranId, valId);
	}

	private OrderView lookupReturn(String tranId) {
		LiveExamOrder existing = orders.findByTranId(tranId).orElseThrow(this::notFound);
		LiveExamPaymentGateway.Lookup lookup;
		try {
			lookup = gateway().lookup(existing.getTranId(), existing.getAmount(), existing.getCurrency());
		}
		catch (RuntimeException exception) {
			log.warn("sslcommerz query unreachable tranId={} error={}", tranId, exception.getClass().getSimpleName());
			return view(existing);
		}
		return switch (lookup.kind()) {
			case PAID -> grantOrLeavePending(tranId, lookup.valId());
			case FAILED -> view(tx.mark(tranId, LiveExamOrder.FAILED));
			case CANCELLED -> view(tx.mark(tranId, LiveExamOrder.CANCELLED));
			case NONE, UNREACHABLE -> view(existing);
		};
	}

	private OrderView grantOrLeavePending(String tranId, String valId) {
		try {
			return grant(tranId, valId);
		}
		catch (ApiException exception) {
			if (exception.code() != ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE) {
				throw exception;
			}
			log.warn("sslcommerz return left pending tranId={}", tranId);
			return view(orders.findByTranId(tranId).orElseThrow(this::notFound));
		}
	}

	public OrderView ipn(String tranId, String bodyTranId, String valId) {
		rejectTranMismatch(tranId, bodyTranId);
		return grant(tranId, valId);
	}

	private OrderView grant(String tranId, String valId) {
		LiveExamOrder existing = orders.findByTranId(tranId).orElseThrow(this::notFound);
		if (LiveExamOrder.SUCCEEDED.equals(existing.getStatus())) {
			log.info("live exam payment already granted tranId={}", tranId);
			return view(existing);
		}
		return view(tx.verify(tranId, gateway().verify(tranId, valId, existing.getAmount(), existing.getCurrency())));
	}

	@Transactional(readOnly = true)
	public OrderView get(UUID orderId, UUID studentId) {
		LiveExamOrder order = orders.findById(orderId).orElseThrow(this::notFound);
		if (!order.getStudentId().equals(studentId)) {
			throw notFound();
		}
		return view(order);
	}

	public OrderView cancel(UUID orderId, UUID studentId) {
		return view(tx.cancel(orderId, studentId));
	}

	/** Master Admin confirms a pending order (R-8). The reason is required by the caller. */
	public OrderView confirm(UUID orderId, UUID actorId, String reason) {
		return view(tx.confirm(orderId, actorId, reason));
	}

	/** Master Admin rejects a pending order (R-8). The reason is required by the caller. */
	public OrderView reject(UUID orderId, String reason) {
		return view(tx.reject(orderId, reason));
	}

	@Transactional(readOnly = true)
	public PaymentStatus paymentStatus(String tranId, UUID studentId) {
		LiveExamOrder order = orders.findByTranId(tranId).orElseThrow(this::notFound);
		if (!order.getStudentId().equals(studentId)) {
			throw notFound();
		}
		return new PaymentStatus(publicStatus(order.getStatus()), productName(order));
	}

	private String redirect(LiveExamOrder order, UUID studentId, String productName) {
		User user = users.findById(studentId).orElseThrow(this::notFound);
		StudentProfile profile = profiles.findById(studentId).orElse(null);
		String name = profile == null || profile.getFullName().isBlank() ? user.getPhone() : profile.getFullName();
		return gateway()
			.start(new LiveExamPaymentGateway.Checkout(order.getTranId(), order.getAmount(), LiveExamOrder.BDT,
					productName, name, user.getEmail(), user.getPhone()))
			.redirectUrl();
	}

	private LiveExamPaymentGateway gateway() {
		LiveExamPaymentGateway gateway = gateways.getIfAvailable();
		if (gateway == null) {
			throw unavailable();
		}
		return gateway;
	}

	private static ApiException unavailable() {
		return new ApiException(ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
				"The payment gateway is unavailable.");
	}

	private static ApiException unavailable(LiveExamOrder order) {
		return new ApiException(ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
				"The payment gateway is unavailable.", order.getId(), order.getTranId());
	}

	private Target readTarget(JsonNode body) {
		boolean module = present(body, "moduleId");
		boolean exam = present(body, "examId");
		boolean bundle = body.has("examIds") && !body.get("examIds").isNull();
		if ((module ? 1 : 0) + (exam ? 1 : 0) + (bundle ? 1 : 0) != 1) {
			throw validationFailed();
		}
		if (bundle) {
			return readBundle(body.get("examIds"));
		}
		if (exam) {
			LiveExam row = exams.findByIdAndDeletedAtIsNull(readId(body, "examId")).orElseThrow(this::notFound);
			LiveExamModule parent = publishedModule(row.getModuleId());
			if (!LiveExam.PUBLISHED.equals(row.getStatus())) {
				throw notFound();
			}
			return new Target(LiveExamOrder.EXAM, parent.getId(), row.getId(), requirePrice(row.getPrice()),
					row.getTitleEn(), List.of(row.getId()));
		}
		UUID moduleId = readId(body, "moduleId");
		LiveExamModule row = publishedModule(moduleId);
		return new Target(LiveExamOrder.MODULE, row.getId(), null, requirePrice(row.getPrice()), row.getTitleEn(),
				List.of());
	}

	/**
	 * A bundle (plan D1): 1 to 20 distinct exams of one module, each published and priced as for one exam. The amount is
	 * the sum of the prices, and the product name is the module's title.
	 */
	private Target readBundle(JsonNode node) {
		if (!node.isArray() || node.isEmpty() || node.size() > LiveExamOrderBundle.MAX_EXAMS) {
			throw validationFailed();
		}
		Set<UUID> ids = new LinkedHashSet<>();
		for (JsonNode item : node) {
			if (!item.isValueNode() || !ids.add(parseId(item.asText()))) {
				throw validationFailed();
			}
		}
		List<LiveExam> rows = new ArrayList<>();
		for (UUID id : ids) {
			rows.add(exams.findByIdAndDeletedAtIsNull(id).orElseThrow(this::notFound));
		}
		UUID moduleId = rows.get(0).getModuleId();
		if (rows.stream().anyMatch(row -> !row.getModuleId().equals(moduleId))) {
			throw validationFailed();
		}
		LiveExamModule parent = publishedModule(moduleId);
		if (rows.stream().anyMatch(row -> !LiveExam.PUBLISHED.equals(row.getStatus()))) {
			throw notFound();
		}
		BigDecimal amount = BigDecimal.ZERO.setScale(2);
		for (LiveExam row : rows) {
			amount = amount.add(requirePrice(row.getPrice()));
		}
		return new Target(LiveExamOrderBundle.SCOPE, parent.getId(), null, amount, parent.getTitleEn(), List.copyOf(ids));
	}

	private LiveExamModule publishedModule(UUID moduleId) {
		LiveExamModule module = modules.findByIdAndDeletedAtIsNull(moduleId).orElseThrow(this::notFound);
		if (!LiveExamModule.PUBLISHED.equals(module.getStatus()) || visibility.branchHidden(module.getCategoryId())) {
			throw notFound();
		}
		return module;
	}

	private void rejectOwned(UUID studentId, Target target) {
		if (roster.existsByModuleIdAndStudentId(target.moduleId(), studentId)
				|| grants.existsByStudentIdAndScopeAndModuleIdAndRevokedAtIsNull(studentId, LiveExamOrder.MODULE, target.moduleId())) {
			throw alreadyPurchased();
		}
		for (UUID examId : target.examIds()) {
			if (grants.existsByStudentIdAndScopeAndExamIdAndRevokedAtIsNull(studentId, LiveExamOrder.EXAM, examId)) {
				throw alreadyPurchased();
			}
		}
	}

	private Optional<LiveExamOrder> findOpen(UUID studentId, Target target) {
		if (target.examId() == null) {
			return orders.findByStudentIdAndScopeAndModuleIdAndExamIdIsNullAndStatus(studentId, LiveExamOrder.MODULE,
					target.moduleId(), LiveExamOrder.INITIATED);
		}
		return orders.findByStudentIdAndScopeAndExamIdAndStatus(studentId, LiveExamOrder.EXAM, target.examId(),
				LiveExamOrder.INITIATED);
	}

	private static boolean isOpenOrderRace(DataIntegrityViolationException exception) {
		String message = exception.getMostSpecificCause().getMessage();
		return message != null && (message.contains("live_exam_orders_open_module_unique")
				|| message.contains("live_exam_orders_open_exam_unique"));
	}

	private static void rejectTranMismatch(String pathTranId, String bodyTranId) {
		if (bodyTranId != null && !bodyTranId.isBlank() && !bodyTranId.equals(pathTranId)) {
			throw validationFailed();
		}
	}

	private static boolean present(JsonNode body, String field) {
		return body.has(field) && !body.get(field).isNull() && !body.get(field).asText().isBlank();
	}

	private static UUID readId(JsonNode body, String field) {
		return parseId(body.get(field).asText());
	}

	private static UUID parseId(String value) {
		try {
			return UUID.fromString(value.trim());
		}
		catch (IllegalArgumentException exception) {
			throw validationFailed();
		}
	}

	private static BigDecimal requirePrice(BigDecimal price) {
		if (price == null) {
			throw new ApiException(ErrorCode.PRICE_REQUIRED, HttpStatus.CONFLICT, "Price is required.");
		}
		return price.setScale(2, RoundingMode.UNNECESSARY);
	}

	private String productName(LiveExamOrder order) {
		if (order.getExamId() != null) {
			return exams.findByIdAndDeletedAtIsNull(order.getExamId()).orElseThrow(this::notFound).getTitleEn();
		}
		return modules.findByIdAndDeletedAtIsNull(order.getModuleId()).orElseThrow(this::notFound).getTitleEn();
	}

	static String publicStatus(String status) {
		return switch (status) {
			case LiveExamOrder.INITIATED -> "PENDING";
			case LiveExamOrder.SUCCEEDED -> "PAID";
			case LiveExamOrder.FAILED -> "FAILED";
			case LiveExamOrder.CANCELLED -> "CANCELLED";
			default -> throw new IllegalStateException("Unknown order status.");
		};
	}

	private OrderView view(LiveExamOrder order) {
		return view(order, null);
	}

	private OrderView view(LiveExamOrder order, String redirectUrl) {
		return new OrderView(order.getId(), order.getTranId(), order.getStatus(), order.getAmount(), redirectUrl,
				order.getScope(), order.getModuleId(), order.getExamId(), examIds(order));
	}

	/** The exams the order covers: the bundle's list, the one exam, or null for a module (it covers future exams too). */
	private List<UUID> examIds(LiveExamOrder order) {
		if (LiveExamOrderBundle.SCOPE.equals(order.getScope())) {
			return bundles.examIds(order.getId());
		}
		return order.getExamId() == null ? null : List.of(order.getExamId());
	}

	private static ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
	}

	private static ApiException alreadyPurchased() {
		return new ApiException(ErrorCode.ALREADY_PURCHASED, HttpStatus.CONFLICT, "You already have access.");
	}

	private ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	public record PaymentStatus(String status, String examName) {
	}

	public record InitiateResult(boolean created, OrderView order) {
	}

	public record OrderView(UUID orderId, String tranId, String status, BigDecimal amount, String redirectUrl,
			String scope, UUID moduleId, UUID examId, List<UUID> examIds) {
	}

	/** {@code examIds} lists the exams owned checks cover: the bundle's, the one exam, or none for a module. */
	private record Target(String scope, UUID moduleId, UUID examId, BigDecimal amount, String productName,
			List<UUID> examIds) {
	}

}
