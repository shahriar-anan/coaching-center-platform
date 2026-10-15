package com.coachingcenter.api.purchases;

import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.web.PageResponse;

import tools.jackson.databind.JsonNode;

@RestController
public class PaymentAccessController {

	private final PaymentAccessService paymentAccess;

	private final CourseRosterService roster;

	private final PermissionChecker permissions;

	public PaymentAccessController(PaymentAccessService paymentAccess, CourseRosterService roster,
			PermissionChecker permissions) {
		this.paymentAccess = paymentAccess;
		this.roster = roster;
		this.permissions = permissions;
	}

	private static final Set<String> PAYMENT_STATUSES = Set.of("PENDING", "SUCCESS", "FAILED");

	private static final Set<String> PAYABLE_TYPES = Set.of("COURSE_ENROLLMENT", "BOOK_ORDER", "EXAM_MODULE");

	@GetMapping("/api/v1/payments")
	public PageResponse<PaymentAccessService.PaymentView> listPayments(
			@RequestParam(defaultValue = "PENDING") String status, @RequestParam(required = false) String payableType,
			@PageableDefault(size = 20) Pageable pageable) {
		require(Permissions.PAYMENT_VERIFY);
		if (!PAYMENT_STATUSES.contains(status)) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
		}
		String type = payableType == null || payableType.isBlank() ? null : payableType;
		if (type != null && !PAYABLE_TYPES.contains(type)) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
		}
		return PageResponse.from(paymentAccess.list(status, type, pageable));
	}

	@GetMapping("/api/v1/book-orders")
	public PageResponse<PaymentAccessService.AdminBookOrderView> listBookOrders(
			@PageableDefault(size = 20) Pageable pageable) {
		require(Permissions.ORDER_FULFILL);
		return PageResponse.from(paymentAccess.listBookOrders(pageable));
	}

	@PostMapping("/api/v1/payments/{id}/verify")
	public PaymentAccessService.VerifyPaymentResult verifyPayment(@PathVariable UUID id, @RequestBody JsonNode body) {
		require(Permissions.PAYMENT_VERIFY);
		return paymentAccess.verifyPayment(id, body);
	}

	@GetMapping("/api/v1/courses/{id}/enrollments")
	public PageResponse<CourseRosterService.RosterItem> enrollments(@PathVariable UUID id,
			@RequestParam(required = false) Integer opening, @PageableDefault(size = 20) Pageable pageable) {
		require(Permissions.COURSE_READ);
		return PageResponse.from(roster.list(id, opening, pageable));
	}

	@PostMapping("/api/v1/courses/{id}/admissions")
	public ResponseEntity<PaymentAccessService.AdmissionResult> admit(@PathVariable UUID id,
			@RequestBody JsonNode body) {
		require(Permissions.ENROLLMENT_GRANT);
		return ResponseEntity.status(HttpStatus.CREATED).body(paymentAccess.admitStudent(id, body));
	}

	@PatchMapping("/api/v1/book-orders/{id}")
	public PaymentAccessService.BookOrderStatusResult updateOrder(@PathVariable UUID id, @RequestBody JsonNode body) {
		if (!body.hasNonNull("status")) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
		}
		String status = body.get("status").asText();
		if (!"COLLECTED".equals(status)) {
			require(Permissions.ORDER_FULFILL);
		}
		return paymentAccess.updateOrderStatus(id, body, actor().role());
	}

	private void require(String permission) {
		if (!permissions.has(permission)) {
			throw denied();
		}
	}

	private CurrentActor actor() {
		var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext()
			.getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor) {
			return actor;
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

	private static ApiException denied() {
		return new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
	}

}
