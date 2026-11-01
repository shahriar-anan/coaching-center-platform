package com.coachingcenter.api.exams;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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

@RestController
public class PackageRevisionController {

	private final PackageRevisionService revisions;

	private final PackageStagedService staged;

	private final PermissionChecker permissions;

	public PackageRevisionController(PackageRevisionService revisions, PackageStagedService staged, PermissionChecker permissions) {
		this.revisions = revisions;
		this.staged = staged;
		this.permissions = permissions;
	}

	/** What is waiting on the package and on each of its exams, so the package screen can list it (NR-01, B4). */
	@GetMapping("/api/v1/live-exam-modules/{id}/staged-summary")
	public PackageStagedService.StagedSummary stagedSummary(@PathVariable UUID id) {
		require(Permissions.LIVE_EXAM_PACKAGE_EDIT);
		return staged.summary(id);
	}

	/**
	 * Publishes, in one transaction, the package's staged revision and every member exam's staged revision. The body may name the
	 * revision ids that were read: {@code {"revisionId": "...", "examRevisionIds": {"<examId>": "..."}}}. A stale id, or an exam
	 * id with nothing staged, is refused and nothing is published. The Master Admin only.
	 */
	@PostMapping("/api/v1/live-exam-modules/{id}/publish-changes")
	public PackageStagedService.PublishedChanges publishChanges(@PathVariable UUID id,
			@RequestBody(required = false) JsonNode body) {
		require(Permissions.LIVE_EXAM_PUBLISH);
		require(Permissions.EXAM_PUBLISH);
		String revisionId = body != null && body.hasNonNull("revisionId") ? body.get("revisionId").asText() : null;
		return staged.publishChanges(id, revisionId, examRevisionIds(body), actorId());
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

	/** The exam revision ids in the body, keyed by exam id. A key that is not a UUID is a validation failure. */
	private static Map<UUID, String> examRevisionIds(JsonNode body) {
		Map<UUID, String> ids = new LinkedHashMap<>();
		if (body == null || !body.hasNonNull("examRevisionIds")) {
			return ids;
		}
		for (Map.Entry<String, JsonNode> entry : body.get("examRevisionIds").properties()) {
			UUID examId;
			try {
				examId = UUID.fromString(entry.getKey());
			}
			catch (IllegalArgumentException exception) {
				throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
			}
			ids.put(examId, entry.getValue().isNull() ? null : entry.getValue().asText());
		}
		return ids;
	}

	private static UUID actorId() {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor) {
			return actor.userId();
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

}
