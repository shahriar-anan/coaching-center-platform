package com.coachingcenter.api.exams;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

import jakarta.validation.Valid;

@RestController
public class LiveExamGrantAdminController {

	private final LiveExamGrantAdminService grants;

	private final PermissionChecker permissions;

	public LiveExamGrantAdminController(LiveExamGrantAdminService grants, PermissionChecker permissions) {
		this.grants = grants;
		this.permissions = permissions;
	}

	@PostMapping("/api/v1/live-exam-grants")
	public ResponseEntity<LiveExamGrantAdminService.GrantView> grant(
			@Valid @RequestBody LiveExamGrantAdminService.GrantRequest request) {
		require(Permissions.LIVE_EXAM_GRANT);
		return ResponseEntity.status(HttpStatus.CREATED).body(grants.grant(actor(), request));
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

}
