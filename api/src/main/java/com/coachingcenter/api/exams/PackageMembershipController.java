package com.coachingcenter.api.exams;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

import tools.jackson.databind.JsonNode;

@RestController
public class PackageMembershipController {

	private final PackageMembershipService memberships;

	private final PermissionChecker permissions;

	public PackageMembershipController(PackageMembershipService memberships, PermissionChecker permissions) {
		this.memberships = memberships;
		this.permissions = permissions;
	}

	@PostMapping("/api/v1/live-exam-modules/{id}/exams/{examId}")
	public PackageMembershipService.MembershipView add(@PathVariable UUID id, @PathVariable UUID examId) {
		require(Permissions.LIVE_EXAM_PACKAGE_EDIT);
		return memberships.add(id, examId);
	}

	@DeleteMapping("/api/v1/live-exam-modules/{id}/exams/{examId}")
	public ResponseEntity<Void> remove(@PathVariable UUID id, @PathVariable UUID examId) {
		require(Permissions.LIVE_EXAM_PACKAGE_EDIT);
		memberships.remove(id, examId);
		return ResponseEntity.noContent().build();
	}

	/** Body {@code {"examIds":[…]}}: every exam of the package, in the new order. */
	@PatchMapping("/api/v1/live-exam-modules/{id}/exams/order")
	public PackageMembershipService.MembershipView reorder(@PathVariable UUID id, @RequestBody JsonNode body) {
		require(Permissions.LIVE_EXAM_PACKAGE_EDIT);
		JsonNode ids = body == null ? null : body.get("examIds");
		if (ids == null || !ids.isArray()) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
		}
		List<UUID> examIds = new ArrayList<>();
		for (JsonNode node : ids) {
			examIds.add(UUID.fromString(node.asText()));
		}
		return memberships.reorder(id, examIds);
	}

	private void require(String permission) {
		if (!permissions.has(permission)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
	}

}
