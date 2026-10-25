package com.coachingcenter.api.exams;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Master Admin decides a pending order after checking the payment at SSLCommerz (R-8). */
@RestController
public class LiveExamOrderDecisionController {

	private final LiveExamOrderService orders;

	private final PermissionChecker permissions;

	public LiveExamOrderDecisionController(LiveExamOrderService orders, PermissionChecker permissions) {
		this.orders = orders;
		this.permissions = permissions;
	}

	@PostMapping("/api/v1/live-exam-orders/{id}/confirm")
	public LiveExamOrderService.OrderView confirm(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
		require(Permissions.LIVE_EXAM_ORDER_CONFIRM);
		return orders.confirm(id, actor().userId(), request.reason().trim());
	}

	@PostMapping("/api/v1/live-exam-orders/{id}/reject")
	public LiveExamOrderService.OrderView reject(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
		require(Permissions.LIVE_EXAM_ORDER_CONFIRM);
		return orders.reject(id, request.reason().trim());
	}

	private void require(String permission) {
		if (!permissions.has(permission)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
	}

	private static CurrentActor actor() {
		var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor) {
			return actor;
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

	/** The reason is required, 1 to 500 characters, for both confirm and reject. */
	public record ReasonRequest(@NotBlank @Size(max = 500) String reason) {
	}

}
