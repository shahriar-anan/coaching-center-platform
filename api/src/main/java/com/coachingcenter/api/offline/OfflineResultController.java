package com.coachingcenter.api.offline;

import static com.coachingcenter.api.offline.OfflineSupport.actorId;
import static com.coachingcenter.api.offline.OfflineSupport.denied;
import static com.coachingcenter.api.offline.OfflineSupport.require;

import java.util.List;
import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.files.FileResponses;
import com.coachingcenter.api.offline.OfflineResultService.BatchExam;
import com.coachingcenter.api.offline.OfflineResultService.ClassResults;
import com.coachingcenter.api.offline.OfflineResultService.Preview;
import com.coachingcenter.api.offline.OfflineResultService.SheetFile;

/**
 * Exam results of a batch (results-api-contract, staff routes). Staff and the Master Admin read the sheets (OFFLINE_BATCH_ROUTINE or
 * OFFLINE_RESULT_UPLOAD) and upload, preview and discard a staged sheet (OFFLINE_RESULT_UPLOAD). Only the Master Admin publishes
 * (OFFLINE_RESULT_PUBLISH). A student never reaches these routes.
 */
@RestController
public class OfflineResultController {

	private final OfflineResultService results;

	private final PermissionChecker permissions;

	public OfflineResultController(OfflineResultService results, PermissionChecker permissions) {
		this.results = results;
		this.permissions = permissions;
	}

	@GetMapping("/api/v1/offline-batches/{batchId}/results")
	public List<BatchExam> batchExams(@PathVariable UUID batchId) {
		requireRead();
		return results.batchExams(batchId);
	}

	@PostMapping(value = "/api/v1/offline-classes/{classId}/results/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public Preview preview(@PathVariable UUID classId, @RequestParam("file") MultipartFile file,
			@RequestParam("examType") String examType, @RequestParam(value = "passMark", required = false) String passMark) {
		require(permissions, Permissions.OFFLINE_RESULT_UPLOAD);
		return results.preview(classId, file, examType, passMark);
	}

	@PostMapping(value = "/api/v1/offline-classes/{classId}/results", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public ClassResults save(@PathVariable UUID classId, @RequestParam("file") MultipartFile file,
			@RequestParam("examType") String examType, @RequestParam(value = "passMark", required = false) String passMark) {
		require(permissions, Permissions.OFFLINE_RESULT_UPLOAD);
		return results.save(classId, actorId(), file, examType, passMark);
	}

	@GetMapping("/api/v1/offline-classes/{classId}/results")
	public ClassResults get(@PathVariable UUID classId) {
		requireRead();
		return results.classResults(classId);
	}

	@PostMapping("/api/v1/offline-classes/{classId}/results/publish")
	public ClassResults publish(@PathVariable UUID classId) {
		require(permissions, Permissions.OFFLINE_RESULT_PUBLISH);
		return results.publish(classId, actorId());
	}

	@DeleteMapping("/api/v1/offline-classes/{classId}/results/staged")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void discard(@PathVariable UUID classId) {
		require(permissions, Permissions.OFFLINE_RESULT_UPLOAD);
		results.discardStaged(classId, actorId());
	}

	/** The uploaded file as it was, for staff to check against the sheet. Private: no shared cache may keep it. */
	@GetMapping("/api/v1/offline-classes/{classId}/results/{sheetId}/file")
	public ResponseEntity<InputStreamResource> file(@PathVariable UUID classId, @PathVariable UUID sheetId) {
		requireRead();
		SheetFile file = results.file(classId, sheetId);
		return FileResponses.attachment(file.body(), file.contentType(), file.byteSize(), file.fileName(), "private, no-cache");
	}

	/** Staff who may read the results: the routine's editors and the result uploaders. */
	private void requireRead() {
		if (!permissions.has(Permissions.OFFLINE_BATCH_ROUTINE) && !permissions.has(Permissions.OFFLINE_RESULT_UPLOAD)) {
			throw denied();
		}
	}

}
