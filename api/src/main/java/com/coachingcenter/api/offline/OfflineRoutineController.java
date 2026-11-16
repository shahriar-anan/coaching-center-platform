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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.files.FileResponses;
import com.coachingcenter.api.offline.OfflineBatchService.RoutineFile;
import com.coachingcenter.api.offline.OfflineRoutineService.RoutineSummary;
import com.coachingcenter.api.offline.OfflineRoutineService.RoutineView;

import tools.jackson.databind.JsonNode;

/**
 * Monthly routines of an offline batch (plan section 6, stage 2). Staff and the Master Admin upload, check and confirm them
 * (OFFLINE_BATCH_ROUTINE). A student never reaches these routes: the student copy reaches students through their own routes, which
 * stage 3 adds.
 */
@RestController
public class OfflineRoutineController {

	private final OfflineRoutineService routines;

	private final PermissionChecker permissions;

	public OfflineRoutineController(OfflineRoutineService routines, PermissionChecker permissions) {
		this.routines = routines;
		this.permissions = permissions;
	}

	@PostMapping(value = "/api/v1/offline-batches/{id}/routines", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public RoutineView upload(@PathVariable UUID id, @RequestParam("file") MultipartFile file,
			@RequestParam(value = "examStartsAt", required = false) String examStartsAt,
			@RequestParam(value = "examMinutes", required = false) Integer examMinutes) {
		require(permissions, Permissions.OFFLINE_BATCH_ROUTINE);
		return routines.upload(id, file, actorId(), examStartsAt, examMinutes);
	}

	@GetMapping("/api/v1/offline-batches/{id}/routines")
	public List<RoutineSummary> list(@PathVariable UUID id) {
		require(permissions, Permissions.OFFLINE_BATCH_ROUTINE);
		return routines.list(id);
	}

	@GetMapping("/api/v1/offline-routines/{id}")
	public RoutineView get(@PathVariable UUID id) {
		require(permissions, Permissions.OFFLINE_BATCH_ROUTINE);
		return routines.get(id);
	}

	@PatchMapping("/api/v1/offline-routines/{id}/rows/{index}")
	public RoutineView editRow(@PathVariable UUID id, @PathVariable int index, @RequestBody JsonNode body) {
		require(permissions, Permissions.OFFLINE_BATCH_ROUTINE);
		return routines.editRow(id, index, body, actorId());
	}

	@PostMapping("/api/v1/offline-routines/{id}/instructor-matches")
	public RoutineView matchInstructor(@PathVariable UUID id, @RequestBody JsonNode body) {
		require(permissions, Permissions.OFFLINE_BATCH_ROUTINE);
		return routines.matchInstructor(id, body, actorId());
	}

	@PostMapping("/api/v1/offline-routines/{id}/confirm")
	public RoutineView confirm(@PathVariable UUID id) {
		require(permissions, Permissions.OFFLINE_BATCH_ROUTINE);
		return routines.confirm(id, actorId());
	}

	@DeleteMapping("/api/v1/offline-routines/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void discard(@PathVariable UUID id) {
		require(permissions, Permissions.OFFLINE_BATCH_ROUTINE);
		routines.discard(id, actorId());
	}

	/** The original PDF as the staff uploaded it. Private: the browser may keep it, no shared cache may. */
	@GetMapping("/api/v1/offline-routines/{id}/original.pdf")
	public ResponseEntity<InputStreamResource> original(@PathVariable UUID id) {
		require(permissions, Permissions.OFFLINE_BATCH_ROUTINE);
		return pdf(routines.openFile(id, false));
	}

	/** The student copy, for staff to check before the students see it. */
	@GetMapping("/api/v1/offline-routines/{id}/student.pdf")
	public ResponseEntity<InputStreamResource> student(@PathVariable UUID id) {
		require(permissions, Permissions.OFFLINE_BATCH_ROUTINE);
		return pdf(routines.openFile(id, true));
	}

	private static ResponseEntity<InputStreamResource> pdf(RoutineFile file) {
		return FileResponses.inline(file.body(), MediaType.APPLICATION_PDF_VALUE, file.byteSize(), file.fileName(), "private, no-cache");
	}

}
