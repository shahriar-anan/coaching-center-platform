package com.coachingcenter.api.exams;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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

import jakarta.validation.Valid;

@RestController
public class LiveExamGrantAdminController {

	private final LiveExamGrantAdminService grants;

	private final PermissionChecker permissions;

	public LiveExamGrantAdminController(LiveExamGrantAdminService grants, PermissionChecker permissions) {
		this.grants = grants;
		this.permissions = permissions;
	}

	/**
	 * Gives access by hand. Without a {@code payment} it is No charge, with a required {@code reason}. With one, a payment plan
	 * is opened too.
	 */
	@PostMapping("/api/v1/live-exam-grants")
	public ResponseEntity<LiveExamGrantAdminService.GrantView> grant(
			@Valid @RequestBody LiveExamGrantAdminService.GrantRequest request) {
		require(Permissions.LIVE_EXAM_GRANT);
		return ResponseEntity.status(HttpStatus.CREATED).body(grants.grant(actor(), request));
	}

	/**
	 * The price an enrolment of one package or exam would charge, for the enrol form. Exactly one of the two ids is given. A
	 * {@code studentId} adds the warnings (unfinished plan, access held, roster, direct enrolment and overlaps). Plan figures in
	 * the overlaps are sent only to a caller who may see installments.
	 */
	@GetMapping("/api/v1/live-exam-grants/quote")
	public LiveExamGrantAdminService.QuoteView quote(@RequestParam String scope,
			@RequestParam(required = false) UUID moduleId, @RequestParam(required = false) UUID examId,
			@RequestParam(required = false) UUID studentId) {
		require(Permissions.LIVE_EXAM_GRANT);
		return grants.quote(scope, moduleId, examId, studentId, permissions.has(Permissions.LIVE_EXAM_INSTALLMENT));
	}

	/** {@code reason} is required, 1 to 500 characters. Master Admin only. */
	@DeleteMapping("/api/v1/live-exam-grants/{id}")
	public LiveExamGrantAdminService.GrantView revoke(@PathVariable UUID id,
			@RequestParam(required = false) String reason) {
		require(Permissions.LIVE_EXAM_REVOKE);
		return grants.revoke(actor(), id, reason);
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
