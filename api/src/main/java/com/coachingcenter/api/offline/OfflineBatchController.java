package com.coachingcenter.api.offline;

import static com.coachingcenter.api.offline.OfflineSupport.actorId;
import static com.coachingcenter.api.offline.OfflineSupport.require;

import java.util.List;
import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.files.FileResponses;
import com.coachingcenter.api.offline.OfflineBatchService.BatchStudent;
import com.coachingcenter.api.offline.OfflineBatchService.BatchView;

import tools.jackson.databind.JsonNode;

/**
 * Offline batches (plan section 5). The Master Admin manages them (OFFLINE_BATCH_MANAGE). Staff and the Master Admin view them and
 * upload the routine (OFFLINE_BATCH_ROUTINE). A student never reaches these routes; their own batch's routine comes later.
 */
@RestController
public class OfflineBatchController {

	private final OfflineBatchService batches;

	private final PermissionChecker permissions;

	public OfflineBatchController(OfflineBatchService batches, PermissionChecker permissions) {
		this.batches = batches;
		this.permissions = permissions;
	}

	@PostMapping("/api/v1/offline-batches")
	@ResponseStatus(HttpStatus.CREATED)
	public BatchView create(@RequestBody JsonNode body) {
		require(permissions, Permissions.OFFLINE_BATCH_MANAGE);
		return batches.create(actorId(), body);
	}

	@PatchMapping("/api/v1/offline-batches/{id}")
	public BatchView update(@PathVariable UUID id, @RequestBody JsonNode body) {
		require(permissions, Permissions.OFFLINE_BATCH_MANAGE);
		return batches.update(id, body);
	}

	/**
	 * The batch photo for the staff screens (the batch page and the batch form). The upload and removal routes share this path; before
	 * this route a GET here answered 405, so the photo never showed to staff. Private: no shared cache keeps it.
	 */
	@GetMapping("/api/v1/offline-batches/{id}/photo")
	public ResponseEntity<InputStreamResource> photo(@PathVariable UUID id) {
		require(permissions, Permissions.OFFLINE_BATCH_ROUTINE);
		OfflineBatchService.PhotoFile file = batches.openStaffPhoto(id);
		return FileResponses.inline(file.body(), file.contentType() == null ? MediaType.IMAGE_JPEG_VALUE : file.contentType(),
				file.byteSize(), "batch-photo", "private, no-cache");
	}

	/** Publishes a draft batch (Master Admin, V103). */
	@PostMapping("/api/v1/offline-batches/{id}/publish")
	public BatchView publish(@PathVariable UUID id) {
		require(permissions, Permissions.OFFLINE_BATCH_MANAGE);
		return batches.publish(id, actorId());
	}

	/** Moves a published batch back to draft so its form can be edited (Master Admin, V103). */
	@PostMapping("/api/v1/offline-batches/{id}/unpublish")
	public BatchView unpublish(@PathVariable UUID id) {
		require(permissions, Permissions.OFFLINE_BATCH_MANAGE);
		return batches.unpublish(id);
	}

	@PostMapping("/api/v1/offline-batches/{id}/archive")
	public BatchView archive(@PathVariable UUID id) {
		require(permissions, Permissions.OFFLINE_BATCH_MANAGE);
		return batches.archive(id);
	}

	@GetMapping("/api/v1/offline-batches")
	public List<BatchView> list(@RequestParam(required = false) String status) {
		require(permissions, Permissions.OFFLINE_BATCH_ROUTINE);
		return batches.list(status);
	}

	@GetMapping("/api/v1/offline-batches/{id}")
	public BatchView get(@PathVariable UUID id) {
		require(permissions, Permissions.OFFLINE_BATCH_ROUTINE);
		return batches.get(id);
	}

	@GetMapping("/api/v1/offline-batches/{id}/students")
	public List<BatchStudent> students(@PathVariable UUID id) {
		require(permissions, Permissions.OFFLINE_BATCH_ROUTINE);
		return batches.students(id);
	}

	@PutMapping(value = "/api/v1/offline-batches/{id}/routine-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public BatchView uploadRoutine(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
		require(permissions, Permissions.OFFLINE_BATCH_ROUTINE);
		return batches.uploadRoutine(id, file, actorId());
	}

	/** Replaces the batch photo (V93). The picture is re-encoded and resized on the server, as the profile photo is. */
	@PutMapping(value = "/api/v1/offline-batches/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public BatchView uploadPhoto(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
		require(permissions, Permissions.OFFLINE_BATCH_MANAGE);
		return batches.uploadPhoto(id, file, actorId());
	}

	@DeleteMapping("/api/v1/offline-batches/{id}/photo")
	public BatchView removePhoto(@PathVariable UUID id) {
		require(permissions, Permissions.OFFLINE_BATCH_MANAGE);
		return batches.removePhoto(id);
	}

	/** The routine for staff screens. Private: the browser may keep it, no shared cache may. */
	@GetMapping("/api/v1/offline-batches/{id}/routine.pdf")
	public ResponseEntity<InputStreamResource> routine(@PathVariable UUID id) {
		require(permissions, Permissions.OFFLINE_BATCH_ROUTINE);
		OfflineBatchService.RoutineFile file = batches.openRoutine(id);
		return FileResponses.inline(file.body(), MediaType.APPLICATION_PDF_VALUE, file.byteSize(), file.fileName(),
				"private, no-cache");
	}

}
