package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

@Service
class LiveExamOrderTx {

	private static final Logger log = LoggerFactory.getLogger(LiveExamOrderTx.class);

	/** The payment channel of an order that Master Admin confirmed after checking the payment (R-8). */
	static final String MANUAL_CHANNEL = "MANUAL-CONFIRMED";

	private final LiveExamOrderRepository orders;

	private final LiveExamGrantRepository grants;

	private final ExamMailService examMail;

	private final AfterCommit afterCommit;

	private final StudentNoticeWriter notices;

	private final Clock clock;

	private final AuditRecorder audit;

	private final LiveExamOrderBundle bundles;

	private final LiveExamRepository exams;

	private final LiveExamAccess access;

	LiveExamOrderTx(LiveExamOrderRepository orders, LiveExamGrantRepository grants, ExamMailService examMail,
			AfterCommit afterCommit, StudentNoticeWriter notices, Clock clock, AuditRecorder audit,
			LiveExamOrderBundle bundles, LiveExamRepository exams, LiveExamAccess access) {
		this.orders = orders;
		this.grants = grants;
		this.examMail = examMail;
		this.afterCommit = afterCommit;
		this.notices = notices;
		this.clock = clock;
		this.audit = audit;
		this.bundles = bundles;
		this.exams = exams;
		this.access = access;
	}

	/** An order at its list price with no discount, for callers that predate the discount (NR-01, B5). */
	public LiveExamOrder insert(UUID studentId, String scope, UUID moduleId, UUID examId, BigDecimal amount) {
		return insert(studentId, scope, moduleId, examId, amount, amount, null);
	}

	@Transactional
	public LiveExamOrder insert(UUID studentId, String scope, UUID moduleId, UUID examId, BigDecimal amount,
			BigDecimal listPrice, Integer discountPercent) {
		Instant now = clock.instant();
		LiveExamOrder order = new LiveExamOrder();
		order.setId(UUID.randomUUID());
		order.setStudentId(studentId);
		order.setScope(scope);
		order.setModuleId(moduleId);
		order.setExamId(examId);
		order.setAmount(amount);
		order.setListPrice(listPrice);
		order.setDiscountPercent(discountPercent);
		order.setCurrency(LiveExamOrder.BDT);
		order.setMethod(LiveExamOrder.SSLCOMMERZ);
		order.setTranId(tranId());
		order.setStatus(LiveExamOrder.INITIATED);
		order.setCreatedAt(now);
		order.setUpdatedAt(now);
		return orders.saveAndFlush(order);
	}

	/**
	 * One bundle order and one {@code live_exam_order_exams} row per exam, in one transaction (plan D1). Under the
	 * student's lock, an open bundle with the same exam set is returned instead, so a double click gets one order.
	 */
	@Transactional
	public LiveExamOrderBundle.Inserted insertBundle(UUID studentId, UUID moduleId, List<UUID> examIds,
			BigDecimal amount, BigDecimal listPrice, Integer discountPercent) {
		bundles.lockStudent(studentId);
		Optional<UUID> open = bundles.findOpen(studentId, moduleId, examIds);
		if (open.isPresent()) {
			return new LiveExamOrderBundle.Inserted(orders.findById(open.get()).orElseThrow(LiveExamOrderTx::notFound),
					false);
		}
		LiveExamOrder order = insert(studentId, LiveExamOrderBundle.SCOPE, moduleId, null, amount, listPrice,
				discountPercent);
		bundles.insertExams(order.getId(), examIds);
		return new LiveExamOrderBundle.Inserted(order, true);
	}

	@Transactional
	public LiveExamOrder mark(String tranId, String status) {
		LiveExamOrder order = orders.lockByTranId(tranId).orElseThrow(LiveExamOrderTx::notFound);
		if (!LiveExamOrder.SUCCEEDED.equals(order.getStatus())) {
			order.setStatus(status);
			order.setUpdatedAt(clock.instant());
		}
		return order;
	}

	@Transactional
	public LiveExamOrder verify(String tranId, LiveExamPaymentGateway.Verification verification) {
		LiveExamOrder order = orders.lockByTranId(tranId).orElseThrow(LiveExamOrderTx::notFound);
		if (LiveExamOrder.SUCCEEDED.equals(order.getStatus())) {
			log.info("live exam payment already granted tranId={}", tranId);
			return order;
		}
		if (verification.outcome() == LiveExamPaymentGateway.Verification.Outcome.UNREACHABLE) {
			throw unavailable();
		}
		if (!matches(order, verification)) {
			order.setStatus(LiveExamOrder.FAILED);
			order.setUpdatedAt(clock.instant());
			log.info("live exam payment rejected tranId={}", tranId);
			return order;
		}
		order.setStatus(LiveExamOrder.SUCCEEDED);
		order.setValId(verification.valId());
		order.setPaymentChannel(verification.paymentChannel());
		order.setUpdatedAt(clock.instant());
		writeGrant(order);
		UUID orderId = order.getId();
		afterCommit.run(() -> examMail.onOrderSucceeded(orderId));
		log.info("live exam payment granted tranId={}", tranId);
		return order;
	}

	@Transactional
	public LiveExamOrder cancel(UUID orderId, UUID studentId) {
		LiveExamOrder order = orders.lockById(orderId).orElseThrow(LiveExamOrderTx::notFound);
		if (!order.getStudentId().equals(studentId)) {
			throw notFound();
		}
		if (!LiveExamOrder.INITIATED.equals(order.getStatus())) {
			throw new ApiException(ErrorCode.PAYMENT_NOT_PENDING, HttpStatus.CONFLICT, "Payment is not pending.");
		}
		order.setStatus(LiveExamOrder.CANCELLED);
		order.setUpdatedAt(clock.instant());
		return order;
	}

	/**
	 * Master Admin confirms a pending order after checking the payment at SSLCommerz (R-8). The order becomes paid, the
	 * channel says it was confirmed by hand, and access is granted as for any paid order.
	 */
	@Transactional
	public LiveExamOrder confirm(UUID orderId, UUID actorId, String reason) {
		LiveExamOrder order = requirePending(orderId);
		order.setStatus(LiveExamOrder.SUCCEEDED);
		order.setPaymentChannel(MANUAL_CHANNEL);
		order.setConfirmedBy(actorId);
		order.setConfirmReason(reason);
		order.setUpdatedAt(clock.instant());
		writeGrant(order);
		UUID id = order.getId();
		afterCommit.run(() -> examMail.onOrderSucceeded(id));
		audit.record(AuditRecorder.ORDER_CONFIRMED, AuditRecorder.LIVE_EXAM_ORDER_ENTITY, id);
		return order;
	}

	/** Master Admin rejects a pending order: it becomes FAILED with the reason, and grants nothing. */
	@Transactional
	public LiveExamOrder reject(UUID orderId, String reason) {
		LiveExamOrder order = requirePending(orderId);
		order.setStatus(LiveExamOrder.FAILED);
		order.setRejectedReason(reason);
		order.setUpdatedAt(clock.instant());
		audit.record(AuditRecorder.ORDER_REJECTED, AuditRecorder.LIVE_EXAM_ORDER_ENTITY, order.getId());
		return order;
	}

	private LiveExamOrder requirePending(UUID orderId) {
		LiveExamOrder order = orders.lockById(orderId).orElseThrow(LiveExamOrderTx::notFound);
		if (!LiveExamOrder.INITIATED.equals(order.getStatus())) {
			throw new ApiException(ErrorCode.ORDER_NOT_PENDING, HttpStatus.CONFLICT,
					"Only a pending order can be confirmed or rejected.");
		}
		return order;
	}

	/**
	 * Writes the access an order paid for. A package or exam the student can already sit is left alone, so a replay adds
	 * nothing. One whose access has run out gets a new grant, from its own start (A2'); the old one is retired first.
	 */
	private void writeGrant(LiveExamOrder order) {
		if (LiveExamOrderBundle.SCOPE.equals(order.getScope())) {
			writeBundleGrants(order);
			return;
		}
		if (grants.existsByOrderId(order.getId())) {
			return;
		}
		Instant now = clock.instant();
		UUID studentId = order.getStudentId();
		if (LiveExamOrder.MODULE.equals(order.getScope())) {
			AccessState state = access.moduleState(studentId, order.getModuleId(), now);
			if (state.granted() && !state.expired()) {
				return;
			}
			access.retireExpiredGrants(studentId, LiveExamOrder.MODULE, order.getModuleId(), null, now, LiveExamAccess.RETIRED);
			grantOnce(order, LiveExamOrder.MODULE, order.getModuleId(), null, LiveExamGrant.SOURCE_ORDER, now);
			notices.purchaseGranted(studentId, order.getModuleId(), LiveExamOrder.MODULE);
			return;
		}
		LiveExam exam = exams.findById(order.getExamId()).orElseThrow(LiveExamOrderTx::notFound);
		if (access.canSit(studentId, exam, now)) {
			return;
		}
		access.retireExpiredGrants(studentId, LiveExamOrder.EXAM, null, exam, now, LiveExamAccess.RETIRED);
		grantOnce(order, LiveExamOrder.EXAM, null, exam.getId(), LiveExamGrant.SOURCE_ORDER, now);
		notices.purchaseGranted(studentId, exam.getId(), LiveExamOrder.EXAM);
	}

	/**
	 * One exam grant per bundle exam ({@code source = BUNDLE}). The check is per exam, so a replay adds nothing and an exam
	 * the student can already sit is skipped while the rest are granted. A bundle exam grant names no package (R5).
	 */
	private void writeBundleGrants(LiveExamOrder order) {
		Instant now = clock.instant();
		for (UUID examId : bundles.examIds(order.getId())) {
			LiveExam exam = exams.findById(examId).orElseThrow(LiveExamOrderTx::notFound);
			if (access.canSit(order.getStudentId(), exam, now)) {
				continue;
			}
			access.retireExpiredGrants(order.getStudentId(), LiveExamOrder.EXAM, null, exam, now, LiveExamAccess.RETIRED);
			grantOnce(order, LiveExamOrder.EXAM, null, examId, LiveExamGrant.SOURCE_BUNDLE, now);
			notices.purchaseGranted(order.getStudentId(), examId, LiveExamOrder.EXAM);
		}
	}

	private void grantOnce(LiveExamOrder order, String scope, UUID moduleId, UUID examId, String source, Instant now) {
		LiveExamGrant grant = new LiveExamGrant();
		grant.setId(UUID.randomUUID());
		grant.setStudentId(order.getStudentId());
		grant.setScope(scope);
		grant.setModuleId(moduleId);
		grant.setExamId(examId);
		grant.setOrderId(order.getId());
		grant.setSource(source);
		grant.setCreatedAt(now);
		grants.saveAndFlush(grant);
	}

	private static boolean matches(LiveExamOrder order, LiveExamPaymentGateway.Verification verification) {
		if (verification.outcome() != LiveExamPaymentGateway.Verification.Outcome.VALID) {
			return false;
		}
		if (verification.tranId() == null || !verification.tranId().equals(order.getTranId())) {
			return false;
		}
		if (verification.currency() == null || !LiveExamOrder.BDT.equals(verification.currency().trim())) {
			return false;
		}
		return verification.amount() != null && verification.amount().compareTo(order.getAmount()) == 0;
	}

	private static String tranId() {
		return UUID.randomUUID().toString().replace("-", "").substring(0, 30);
	}

	private static ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	private static ApiException unavailable() {
		return new ApiException(ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
				"The payment gateway is unavailable.");
	}

}
