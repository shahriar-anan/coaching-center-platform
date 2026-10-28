package com.coachingcenter.api.exams;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

import tools.jackson.databind.JsonNode;

/** Written retake grants (R14). Master Admin and System Admin; an instructor cannot grant. */
@RestController
public class RetakeGrantController {

	private final RetakeGrantService grants;

	private final PermissionChecker permissions;

	public RetakeGrantController(RetakeGrantService grants, PermissionChecker permissions) {
		this.grants = grants;
		this.permissions = permissions;
	}

	@PostMapping("/api/v1/live-exams/{id}/retake-grants")
	public ResponseEntity<RetakeGrantService.GrantView> grant(@PathVariable UUID id, @RequestBody JsonNode body) {
		require();
		JsonNode student = body.get("studentId");
		if (student == null || student.isNull() || student.asText().isBlank()) {
			throw validationFailed();
		}
		UUID studentId;
		try {
			studentId = UUID.fromString(student.asText());
		}
		catch (IllegalArgumentException exception) {
			throw validationFailed();
		}
		JsonNode reason = body.get("reason");
		String note = reason == null || reason.isNull() ? null : reason.asText();
		return ResponseEntity.status(HttpStatus.CREATED).body(grants.grant(id, studentId, note, actor().userId()));
	}

	@DeleteMapping("/api/v1/live-exams/{id}/retake-grants/{grantId}")
	public ResponseEntity<Void> revoke(@PathVariable UUID id, @PathVariable UUID grantId) {
		require();
		grants.revoke(id, grantId, actor().userId());
		return ResponseEntity.noContent().build();
	}

	private void require() {
		if (!permissions.has(Permissions.LIVE_EXAM_RETAKE_GRANT)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
	}

	private static ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
	}

	private static CurrentActor actor() {
		var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor) {
			return actor;
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

}
