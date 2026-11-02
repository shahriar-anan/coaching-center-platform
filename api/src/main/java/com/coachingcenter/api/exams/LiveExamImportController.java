package com.coachingcenter.api.exams;

import java.io.IOException;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

/** Replaces an MCQ exam's questions from a Word file: a preview that saves nothing, then the apply (plan: mcq-docx-upload). */
@RestController
public class LiveExamImportController {

	private final LiveExamImportService imports;

	private final PermissionChecker permissions;

	public LiveExamImportController(LiveExamImportService imports, PermissionChecker permissions) {
		this.imports = imports;
		this.permissions = permissions;
	}

	@PostMapping(value = "/api/v1/live-exams/{id}/questions/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public LiveExamImportService.McqImportView preview(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
		require(Permissions.EXAM_MANAGE);
		return imports.preview(id, bytes(file));
	}

	@PostMapping(value = "/api/v1/live-exams/{id}/questions/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public LiveExamImportService.ImportApplied apply(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
		require(Permissions.EXAM_MANAGE);
		return imports.apply(id, bytes(file), actorId());
	}

	private static byte[] bytes(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
		}
		try {
			return file.getBytes();
		}
		catch (IOException exception) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
		}
	}

	private void require(String permission) {
		if (!permissions.has(permission)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
	}

	private static UUID actorId() {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor) {
			return actor.userId();
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}
}
