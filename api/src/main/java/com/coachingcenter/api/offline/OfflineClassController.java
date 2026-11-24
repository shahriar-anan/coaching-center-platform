package com.coachingcenter.api.offline;

import static com.coachingcenter.api.offline.OfflineSupport.actorId;
import static com.coachingcenter.api.offline.OfflineSupport.require;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.offline.OfflineClassService.ClassView;

import tools.jackson.databind.JsonNode;

/**
 * Offline classes (plan section 6). Staff and the Master Admin see, add, edit and delete the classes of a batch (OFFLINE_BATCH_ROUTINE).
 * Since 24 Nov a class carries no pay: instructors are paid by hand ({@link OfflineInstructorPayController}). Students never reach these
 * routes.
 */
@RestController
public class OfflineClassController {

	private final OfflineClassService classes;

	private final PermissionChecker permissions;

	public OfflineClassController(OfflineClassService classes, PermissionChecker permissions) {
		this.classes = classes;
		this.permissions = permissions;
	}

	@GetMapping("/api/v1/offline-batches/{id}/classes")
	public List<ClassView> list(@PathVariable UUID id, @RequestParam(required = false) String month,
			@RequestParam(required = false) String status, @RequestParam(required = false) UUID instructorId) {
		require(permissions, Permissions.OFFLINE_BATCH_ROUTINE);
		return classes.list(id, OfflineClassService.month(month), status, instructorId);
	}

	@PostMapping("/api/v1/offline-batches/{id}/classes")
	@ResponseStatus(HttpStatus.CREATED)
	public ClassView create(@PathVariable UUID id, @RequestBody JsonNode body) {
		require(permissions, Permissions.OFFLINE_BATCH_ROUTINE);
		return classes.create(id, actorId(), body);
	}

	@PatchMapping("/api/v1/offline-classes/{id}")
	public ClassView update(@PathVariable UUID id, @RequestBody JsonNode body) {
		require(permissions, Permissions.OFFLINE_BATCH_ROUTINE);
		return classes.update(id, actorId(), body);
	}

	/** Deletes a class by hand (16 Nov). Both admin roles, as they add and edit classes. Answers 204. */
	@org.springframework.web.bind.annotation.DeleteMapping("/api/v1/offline-classes/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable UUID id) {
		require(permissions, Permissions.OFFLINE_BATCH_ROUTINE);
		classes.delete(id, actorId());
	}

}
