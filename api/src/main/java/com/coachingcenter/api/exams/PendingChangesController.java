package com.coachingcenter.api.exams;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

/** The "changes waiting" badge: every exam and package with a staged edit, newest first (E32). */
@RestController
public class PendingChangesController {

	private final LiveExamRevisionService revisions;

	private final PermissionChecker permissions;

	public PendingChangesController(LiveExamRevisionService revisions, PermissionChecker permissions) {
		this.revisions = revisions;
		this.permissions = permissions;
	}

	@GetMapping("/api/v1/live-exam-pending-changes")
	public LiveExamRevisionService.PendingChanges pending() {
		if (!permissions.has(Permissions.EXAM_MANAGE)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
		return revisions.pending();
	}

}
