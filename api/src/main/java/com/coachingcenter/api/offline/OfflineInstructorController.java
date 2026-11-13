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
import com.coachingcenter.api.offline.OfflineInstructorService.InstructorView;

import tools.jackson.databind.JsonNode;

/**
 * Offline instructors (plan section 6). Staff and the Master Admin list, create and rename them (OFFLINE_BATCH_ROUTINE). Only the Master
 * Admin archives one (OFFLINE_BATCH_MANAGE). Instructors have no account, so no student route reaches these.
 */
@RestController
public class OfflineInstructorController {

	private final OfflineInstructorService instructors;

	private final PermissionChecker permissions;

	public OfflineInstructorController(OfflineInstructorService instructors, PermissionChecker permissions) {
		this.instructors = instructors;
		this.permissions = permissions;
	}

	@GetMapping("/api/v1/offline-instructors")
	public List<InstructorView> list(@RequestParam(required = false) String q,
			@RequestParam(required = false, defaultValue = "false") boolean includeArchived) {
		require(permissions, Permissions.OFFLINE_BATCH_ROUTINE);
		return instructors.list(q, includeArchived);
	}

	@PostMapping("/api/v1/offline-instructors")
	@ResponseStatus(HttpStatus.CREATED)
	public InstructorView create(@RequestBody JsonNode body) {
		require(permissions, Permissions.OFFLINE_BATCH_ROUTINE);
		return instructors.create(actorId(), body);
	}

	@PatchMapping("/api/v1/offline-instructors/{id}")
	public InstructorView update(@PathVariable UUID id, @RequestBody JsonNode body) {
		require(permissions, Permissions.OFFLINE_BATCH_ROUTINE);
		return instructors.update(id, body);
	}

	@PostMapping("/api/v1/offline-instructors/{id}/archive")
	public InstructorView archive(@PathVariable UUID id) {
		require(permissions, Permissions.OFFLINE_BATCH_MANAGE);
		return instructors.archive(id);
	}

}
