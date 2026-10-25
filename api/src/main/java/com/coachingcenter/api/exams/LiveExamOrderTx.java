package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

@Service
class LiveExamOrderTx {

	private static final Logger log = LoggerFactory.getLogger(LiveExamOrderTx.class);

	private final LiveExamOrderRepository orders;

	private final LiveExamGrantRepository grants;

	private final ExamMailService examMail;

	private final AfterCommit afterCommit;

	private final Clock clock;

	LiveExamOrderTx(LiveExamOrderRepository orders, LiveExamGrantRepository grants, ExamMailService examMail,
			AfterCommit afterCommit, Clock clock) {
		this.orders = orders;
		this.grants = grants;
		this.examMail = examMail;
		this.afterCommit = afterCommit;
		this.clock = clock;
	}

	@Transactional
	public LiveExamOrder insert(UUID studentId, String scope, UUID moduleId, UUID examId, BigDecimal amount) {
		Instant now = clock.instant();
		LiveExamOrder order = new LiveExamOrder();
		order.setId(UUID.randomUUID());
		order.setStudentId(studentId);
		order.setScope(scope);
		order.setModuleId(moduleId);
		order.setExamId(examId);
		order.setAmount(amount);
		order.setCurrency(LiveExamOrder.BDT);
		order.setMethod(LiveExamOrder.SSLCOMMERZ);
		order.setTranId(tranId());
		order.setStatus(LiveExamOrder.INITIATED);
		order.setCreatedAt(now);
		order.setUpdatedAt(now);
		return orders.saveAndFlush(order);
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

	private void writeGrant(LiveExamOrder order) {
		boolean held = LiveExamOrder.MODULE.equals(order.getScope())
				? grants.existsByStudentIdAndScopeAndModuleIdAndRevokedAtIsNull(order.getStudentId(), LiveExamOrder.MODULE, order.getModuleId())
				: grants.existsByStudentIdAndScopeAndExamIdAndRevokedAtIsNull(order.getStudentId(), LiveExamOrder.EXAM, order.getExamId());
		if (held || grants.existsByOrderId(order.getId())) {
			return;
		}
		LiveExamGrant grant = new LiveExamGrant();
		grant.setId(UUID.randomUUID());
		grant.setStudentId(order.getStudentId());
		grant.setScope(order.getScope());
		grant.setModuleId(order.getModuleId());
		grant.setExamId(order.getExamId());
		grant.setOrderId(order.getId());
		grant.setSource(LiveExamGrant.SOURCE_ORDER);
		grant.setCreatedAt(clock.instant());
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
