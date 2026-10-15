package com.coachingcenter.api.exams;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.purchases.Payment;
import com.coachingcenter.api.purchases.PaymentProperties;
import com.coachingcenter.api.purchases.PaymentRepository;

import tools.jackson.databind.JsonNode;

@Service
public class ExamModulePurchaseService {

	private final ExamModuleRepository modules;

	private final ExamModulePurchaseRepository purchases;

	private final PaymentRepository payments;

	private final PaymentProperties paymentProperties;

	public ExamModulePurchaseService(ExamModuleRepository modules, ExamModulePurchaseRepository purchases,
			PaymentRepository payments, PaymentProperties paymentProperties) {
		this.modules = modules;
		this.purchases = purchases;
		this.payments = payments;
		this.paymentProperties = paymentProperties;
	}

	@Transactional
	public PurchaseResult purchase(UUID moduleId, UUID studentId, JsonNode body) {
		ExamModule module = requireForSale(moduleId);
		if (purchases.existsByStudentIdAndModuleIdAndStatusAndDeletedAtIsNull(studentId, moduleId, "ACTIVE")) {
			throw conflict(ErrorCode.ALREADY_PURCHASED, "You already have access to this exam module.");
		}
		String method = requiredText(body, "method");
		String transactionRef = requiredText(body, "transactionRef");
		validatePaymentMethod(method);
		ensureUniqueTransactionRef(transactionRef);
		ExamModulePurchase purchase = new ExamModulePurchase();
		purchase.setStudentId(studentId);
		purchase.setModuleId(moduleId);
		purchase.setStatus("AWAITING_PAYMENT");
		purchase.setPricePaid(module.getPrice());
		purchase = purchases.save(purchase);
		Payment payment = createPayment(studentId, purchase.getId(), module.getPrice(), method, transactionRef);
		return new PurchaseResult(purchase.getId(), payment.getId(), purchase.getStatus(), payment.getStatus(),
				payment.getAmount(), payment.getMethod(), payment.getTransactionRef(), paymentNumber(method));
	}

	@Transactional(readOnly = true)
	public List<MyPurchaseView> listMyPurchases(UUID studentId) {
		return purchases.findByStudentIdAndDeletedAtIsNullOrderByCreatedAtDesc(studentId).stream().map(purchase -> {
			Payment payment = payments.findByPayableTypeAndPayableIdAndDeletedAtIsNull("EXAM_MODULE", purchase.getId())
				.orElse(null);
			return new MyPurchaseView(purchase.getId(), purchase.getModuleId(), purchase.getStatus(),
					payment == null ? null : payment.getStatus(), purchase.getPricePaid(),
					payment == null ? null : payment.getMethod(),
					payment == null ? null : payment.getTransactionRef());
		}).toList();
	}

	public static boolean canActivate(ExamModule module) {
		return "PUBLISHED".equals(module.getStatus()) && "PURCHASE_REQUIRED".equals(module.getAccessMode())
				&& module.getPrice() != null;
	}

	private ExamModule requireForSale(UUID moduleId) {
		ExamModule module = modules.findByIdAndDeletedAtIsNull(moduleId)
			.orElseThrow(() -> notFound("Exam module not found."));
		if (!canActivate(module)) {
			throw conflict(ErrorCode.EXAM_NOT_FOR_SALE, "This exam module is not for sale.");
		}
		return module;
	}

	private Payment createPayment(UUID studentId, UUID purchaseId, java.math.BigDecimal amount, String method,
			String transactionRef) {
		Payment payment = new Payment();
		payment.setStudentId(studentId);
		payment.setPayableType("EXAM_MODULE");
		payment.setPayableId(purchaseId);
		payment.setAmount(amount);
		payment.setMethod(method);
		payment.setTransactionRef(transactionRef);
		payment.setStatus("PENDING");
		try {
			return payments.save(payment);
		}
		catch (DataIntegrityViolationException exception) {
			throw conflict(ErrorCode.DUPLICATE_TRANSACTION_REF, "Transaction reference is already in use.");
		}
	}

	private void ensureUniqueTransactionRef(String transactionRef) {
		if (payments.existsByTransactionRef(transactionRef)) {
			throw conflict(ErrorCode.DUPLICATE_TRANSACTION_REF, "Transaction reference is already in use.");
		}
	}

	private String paymentNumber(String method) {
		if ("BKASH".equals(method)) {
			return paymentProperties.bkashNumber();
		}
		if ("NAGAD".equals(method)) {
			return paymentProperties.nagadNumber();
		}
		throw validationFailed();
	}

	private static void validatePaymentMethod(String method) {
		if (!"BKASH".equals(method) && !"NAGAD".equals(method)) {
			throw validationFailed();
		}
	}

	private static String requiredText(JsonNode body, String field) {
		JsonNode node = body.get(field);
		if (node == null || node.isNull() || node.asText().isBlank()) {
			throw validationFailed();
		}
		return node.asText().trim();
	}

	private static ApiException notFound(String message) {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, message);
	}

	private static ApiException conflict(ErrorCode code, String message) {
		return new ApiException(code, HttpStatus.CONFLICT, message);
	}

	private static ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
	}

	public record PurchaseResult(UUID purchaseId, UUID paymentId, String purchaseStatus, String paymentStatus,
			java.math.BigDecimal amount, String method, String transactionRef, String paymentNumber) {
	}

	public record MyPurchaseView(UUID purchaseId, UUID moduleId, String purchaseStatus, String paymentStatus,
			java.math.BigDecimal pricePaid, String method, String transactionRef) {
	}

}
