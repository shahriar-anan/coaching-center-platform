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

@RestController
public class PackageRevisionController {

	private final PackageRevisionService revisions;

	private final PermissionChecker permissions;

	public PackageRevisionController(PackageRevisionService revisions, PermissionChecker permissions) {
		this.revisions = revisions;
		this.permissions = permissions;
	}

	/** The staged change list of a published package. 404 when nothing is staged (NR-01, B4). */
	@GetMapping("/api/v1/live-exam-modules/{id}/pending-changes")
	public ChangeList pendingChanges(@PathVariable UUID id) {
		require(Permissions.LIVE_EXAM_PACKAGE_EDIT);
		return revisions.pendingChanges(id);
	}

	@DeleteMapping("/api/v1/live-exam-modules/{id}/pending-changes")
	public ResponseEntity<Void> discard(@PathVariable UUID id) {
		require(Permissions.LIVE_EXAM_PACKAGE_EDIT);
		revisions.discard(id);
		return ResponseEntity.noContent().build();
	}

	private void require(String permission) {
		if (!permissions.has(permission)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
	}

}
