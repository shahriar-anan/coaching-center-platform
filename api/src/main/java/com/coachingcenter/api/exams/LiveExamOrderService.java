package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
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

	private final LiveExamMemberships memberships;

	private final LiveExamCatalogVisibility visibility;

	private final UserRepository users;

	private final StudentProfileRepository profiles;

	private final ObjectProvider<LiveExamPaymentGateway> gateways;

	private final StudentPhoneGuard phones;

	private final LiveExamOrderBundle bundles;

	private final LiveExamAccess access;

	private final Clock clock;

	public LiveExamOrderService(LiveExamOrderRepository orders, LiveExamOrderTx tx, LiveExamModuleRepository modules,
			LiveExamRepository exams, LiveExamCatalogVisibility visibility, UserRepository users,
			StudentProfileRepository profiles, ObjectProvider<LiveExamPaymentGateway> gateways, StudentPhoneGuard phones,
			LiveExamOrderBundle bundles, LiveExamMemberships memberships, LiveExamAccess access, Clock clock) {
		this.memberships = memberships;
		this.orders = orders;
		this.tx = tx;
		this.modules = modules;
		this.exams = exams;
		this.visibility = visibility;
		this.users = users;
		this.profiles = profiles;
		this.gateways = gateways;
		this.phones = phones;
		this.bundles = bundles;
		this.access = access;
		this.clock = clock;
	}

	public InitiateResult initiate(UUID studentId, JsonNode body) {
		phones.require(studentId);
		Instant now = clock.instant();
		Target target = readTarget(body, now);
		rejectOwned(studentId, target, now);
		if (gateways.getIfAvailable() == null) {
			throw unavailable();
		}
		Optional<LiveExamOrder> open = findOpen(studentId, target);
		if (open.isPresent()) {
			return checkout(open.get(), false, studentId, target.productName());
		}
		if (LiveExamOrderBundle.SCOPE.equals(target.scope())) {
			LiveExamOrderBundle.Inserted inserted = tx.insertBundle(studentId, target.moduleId(), target.examIds(),
					target.amount(), target.listPrice(), target.discountPercent());
			return checkout(inserted.order(), inserted.created(), studentId, target.productName());
		}
		LiveExamOrder order;
		try {
			order = tx.insert(studentId, target.scope(), target.moduleId(), target.examId(), target.amount(),
					target.listPrice(), target.discountPercent());
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

	private Target readTarget(JsonNode body, Instant now) {
		boolean module = present(body, "moduleId");
		boolean exam = present(body, "examId");
		boolean bundle = body.has("examIds") && !body.get("examIds").isNull();
		if ((module ? 1 : 0) + (exam ? 1 : 0) + (bundle ? 1 : 0) != 1) {
			throw validationFailed();
		}
		if (bundle) {
			return readBundle(body.get("examIds"), now);
		}
		if (exam) {
			// An exam is sold on its own, whichever package it is in (R5). It needs a published home category.
			LiveExam row = exams.findByIdAndDeletedAtIsNull(readId(body, "examId")).orElseThrow(this::notFound);
			if (!LiveExam.PUBLISHED.equals(row.getStatus()) || visibility.branchHidden(row.getCategoryId())) {
				throw notFound();
			}
			return new Target(LiveExamOrder.EXAM, null, row.getId(), examAmount(row, now), row.getTitleEn(),
					List.of(row.getId()), listPrice(row.getPrice()),
					activePercent(row.getDiscountPercent(), row.getDiscountEndsAt(), now));
		}
		UUID moduleId = readId(body, "moduleId");
		LiveExamModule row = publishedModule(moduleId);
		BigDecimal price = requirePrice(row.getPrice());
		return new Target(LiveExamOrder.MODULE, row.getId(), null,
				charge(price, row.getDiscountPercent(), row.getDiscountEndsAt(), now), row.getTitleEn(), List.of(), price,
				activePercent(row.getDiscountPercent(), row.getDiscountEndsAt(), now));
	}

	/**
	 * What an exam costs now: its price less the discount active at {@code now}. An exam with no price, or a free one, is not
	 * for sale on its own (NR-01, B5 and B7).
	 */
	private static BigDecimal examAmount(LiveExam row, Instant now) {
		if (row.isFree() || row.getPrice() == null) {
			throw notForSale();
		}
		return charge(row.getPrice(), row.getDiscountPercent(), row.getDiscountEndsAt(), now);
	}

	/**
	 * A bundle (plan D1): 1 to 20 distinct exams of one module, each published and priced as for one exam. The amount is
	 * the sum of the prices, and the product name is the module's title.
	 */
	private Target readBundle(JsonNode node, Instant now) {
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
		LiveExamModule parent = publishedModule(commonModule(rows));
		if (rows.stream().anyMatch(row -> !LiveExam.PUBLISHED.equals(row.getStatus()))) {
			throw notFound();
		}
		BigDecimal list = BigDecimal.ZERO.setScale(2);
		BigDecimal amount = BigDecimal.ZERO.setScale(2);
		Set<Integer> percents = new HashSet<>();
		for (LiveExam row : rows) {
			if (row.isFree()) {
				throw notForSale();
			}
			BigDecimal price = requirePrice(row.getPrice());
			list = list.add(price);
			amount = amount.add(charge(price, row.getDiscountPercent(), row.getDiscountEndsAt(), now));
			percents.add(activePercent(row.getDiscountPercent(), row.getDiscountEndsAt(), now));
		}
		// The order shows one percent only when every exam in it had the same discount.
		Integer percent = percents.size() == 1 ? percents.iterator().next() : null;
		return new Target(LiveExamOrderBundle.SCOPE, parent.getId(), null, amount, parent.getTitleEn(), List.copyOf(ids),
				list, percent);
	}

	/** The package every exam of a bundle is in: the oldest one they all share. A bundle is sold through one package. */
	private UUID commonModule(List<LiveExam> rows) {
		List<UUID> common = new ArrayList<>(memberships.activeModuleIds(rows.get(0).getId()));
		for (LiveExam row : rows) {
			common.retainAll(memberships.activeModuleIds(row.getId()));
		}
		if (common.isEmpty()) {
			throw validationFailed();
		}
		return common.get(0);
	}

	private LiveExamModule publishedModule(UUID moduleId) {
		LiveExamModule module = modules.findByIdAndDeletedAtIsNull(moduleId).orElseThrow(this::notFound);
		if (!LiveExamModule.PUBLISHED.equals(module.getStatus()) || visibility.branchHidden(module.getCategoryId())) {
			throw notFound();
		}
		return module;
	}

	/**
	 * Refuses a student who can already sit what is bought (ALREADY_PURCHASED). One whose access has run out may buy again
	 * (A2'). A package counts as held while one of its paid exams can still be sat.
	 */
	private void rejectOwned(UUID studentId, Target target, Instant now) {
		if (LiveExamOrder.MODULE.equals(target.scope())) {
			AccessState state = access.moduleState(studentId, target.moduleId(), now);
			if (state.granted() && !state.expired()) {
				throw alreadyPurchased();
			}
			return;
		}
		for (UUID examId : target.examIds()) {
			LiveExam exam = exams.findByIdAndDeletedAtIsNull(examId).orElseThrow(this::notFound);
			if (access.canSit(studentId, exam, now)) {
				throw alreadyPurchased();
			}
		}
	}

	private Optional<LiveExamOrder> findOpen(UUID studentId, Target target) {
		if (LiveExamOrderBundle.SCOPE.equals(target.scope())) {
			return bundles.findOpen(studentId, target.moduleId(), target.examIds()).flatMap(orders::findById);
		}
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

	private static BigDecimal listPrice(BigDecimal price) {
		return price == null ? null : price.setScale(2, RoundingMode.UNNECESSARY);
	}

	/** What the student pays at {@code now}: the price less the discount active then, to two places. */
	private static BigDecimal charge(BigDecimal price, Integer discountPercent, Instant discountEndsAt, Instant now) {
		return ExamPricing.effective(price, discountPercent, discountEndsAt, now).setScale(2, RoundingMode.HALF_UP);
	}

	/** The discount percent the student sees at {@code now}, or null once it has ended (or was never set). */
	private static Integer activePercent(Integer discountPercent, Instant discountEndsAt, Instant now) {
		return discountPercent != null && (discountEndsAt == null || now.isBefore(discountEndsAt)) ? discountPercent : null;
	}

	private static ApiException notForSale() {
		return new ApiException(ErrorCode.EXAM_NOT_FOR_SALE, HttpStatus.CONFLICT, "This exam is not for sale.");
	}

	/** The exam's title, or the module's title for a module order and for a bundle (which has no exam id). */
	private String productName(LiveExamOrder order) {
		if (LiveExamOrder.EXAM.equals(order.getScope())) {
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
				order.getScope(), order.getModuleId(), order.getExamId(), examIds(order), order.getListPrice(),
				order.getDiscountPercent(), order.getPaidAt(), order.getReceiptNo());
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

	/** {@code listPrice} and {@code discountPercent} are what the student saw at checkout (R19). */
	public record OrderView(UUID orderId, String tranId, String status, BigDecimal amount, String redirectUrl,
			String scope, UUID moduleId, UUID examId, List<UUID> examIds, BigDecimal listPrice, Integer discountPercent,
			Instant paidAt, String receiptNo) {
	}

	/**
	 * {@code examIds} lists the exams owned checks cover: the bundle's, the one exam, or none for a module. {@code amount} is
	 * what the student pays now, and {@code listPrice} and {@code discountPercent} are what they see.
	 */
	private record Target(String scope, UUID moduleId, UUID examId, BigDecimal amount, String productName,
			List<UUID> examIds, BigDecimal listPrice, Integer discountPercent) {
	}

}
