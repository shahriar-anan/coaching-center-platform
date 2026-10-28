package com.coachingcenter.api.exams;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

/**
 * Reading and discarding the staged edit of a published exam (E31, E32). Publishing it is the exam publish route. The
 * Master Admin and the System Admin may read and discard; only the Master Admin publishes.
 */
@RestController
public class LiveExamRevisionController {

	private final LiveExamRevisionService revisions;

	private final PermissionChecker permissions;

	public LiveExamRevisionController(LiveExamRevisionService revisions, PermissionChecker permissions) {
		this.revisions = revisions;
		this.permissions = permissions;
	}

	/** The field-by-field change list, or 404 when nothing is staged. */
	@GetMapping("/api/v1/live-exams/{id}/pending-changes")
	public ChangeList changes(@PathVariable UUID id) {
		require();
		return revisions.changeList(id);
	}

	@DeleteMapping("/api/v1/live-exams/{id}/pending-changes")
	public ResponseEntity<Void> discard(@PathVariable UUID id) {
		require();
		revisions.discard(id);
		return ResponseEntity.noContent().build();
	}

	private void require() {
		if (!permissions.has(Permissions.EXAM_MANAGE)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
	}

}
