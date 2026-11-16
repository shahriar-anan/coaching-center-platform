package com.coachingcenter.api.offline;

import static com.coachingcenter.api.offline.OfflineSupport.actorId;
import static com.coachingcenter.api.offline.OfflineSupport.require;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.offline.OfflineOtherPaymentService.OtherPaymentView;

import tools.jackson.databind.JsonNode;

/**
 * Other payments to an offline instructor (17 Nov). Whoever settles classes records them (both admin roles); the Master Admin, who
 * approves class payments, approves, rejects and voids them.
 */
@RestController
public class OfflineOtherPaymentController {

	private final OfflineOtherPaymentService payments;

	private final PermissionChecker permissions;

	public OfflineOtherPaymentController(OfflineOtherPaymentService payments, PermissionChecker permissions) {
		this.payments = payments;
		this.permissions = permissions;
	}

	@GetMapping("/api/v1/offline-instructors/{id}/other-payments")
	public List<OtherPaymentView> forInstructor(@PathVariable UUID id) {
		require(permissions, Permissions.OFFLINE_CLASS_SETTLE);
		return payments.forInstructor(id);
	}

	@PostMapping("/api/v1/offline-instructors/{id}/other-payments")
	@ResponseStatus(HttpStatus.CREATED)
	public OtherPaymentView record(@PathVariable UUID id, @RequestBody JsonNode body) {
		require(permissions, Permissions.OFFLINE_CLASS_SETTLE);
		return payments.record(id, actorId(), permissions.has(Permissions.OFFLINE_CLASS_APPROVE), body);
	}

	@GetMapping("/api/v1/offline-other-payments")
	public List<OtherPaymentView> inStatus(@RequestParam(required = false) String status) {
		require(permissions, Permissions.OFFLINE_CLASS_SETTLE);
		return payments.inStatus(status);
	}

	@PostMapping("/api/v1/offline-other-payments/{id}/approve")
	public OtherPaymentView approve(@PathVariable UUID id) {
		require(permissions, Permissions.OFFLINE_CLASS_APPROVE);
		return payments.approve(id, actorId());
	}

	@PostMapping("/api/v1/offline-other-payments/{id}/reject")
	public OtherPaymentView reject(@PathVariable UUID id, @RequestBody(required = false) JsonNode body) {
		require(permissions, Permissions.OFFLINE_CLASS_APPROVE);
		return payments.reject(id, actorId(), body);
	}

	@PostMapping("/api/v1/offline-other-payments/{id}/void")
	public OtherPaymentView voidPayment(@PathVariable UUID id, @RequestBody(required = false) JsonNode body) {
		require(permissions, Permissions.OFFLINE_CLASS_APPROVE);
		return payments.voidPayment(id, actorId(), body);
	}

}
