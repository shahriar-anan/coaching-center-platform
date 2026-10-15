package com.coachingcenter.api.files;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.exams.WrittenSubmissionImage;
import com.coachingcenter.api.exams.WrittenSubmissionImageRepository;
import com.coachingcenter.api.exams.WrittenSubmissionService;

@RestController
public class ExamFileController {

	private final WrittenSubmissionImageRepository images;

	private final WrittenSubmissionService submissions;

	private final LocalFileStorage fileStorage;

	private final PermissionChecker permissions;

	public ExamFileController(WrittenSubmissionImageRepository images, WrittenSubmissionService submissions,
			LocalFileStorage fileStorage, PermissionChecker permissions) {
		this.images = images;
		this.submissions = submissions;
		this.fileStorage = fileStorage;
		this.permissions = permissions;
	}

	@GetMapping("/api/v1/exam-files/{id}")
	public ResponseEntity<InputStreamResource> download(@PathVariable UUID id) throws Exception {
		WrittenSubmissionImage image = images.findById(id).filter(row -> row.getDeletedAt() == null)
			.orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found."));
		UUID viewerId = actor().userId();
		boolean adminRead = permissions.has(Permissions.EXAM_READ);
		submissions.ensureCanDownloadImage(id, viewerId, adminRead);
		Path path = fileStorage.resolve(image.getStoredFileName());
		if (!Files.exists(path)) {
			throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
		}
		InputStreamResource body = new InputStreamResource(Files.newInputStream(path));
		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + image.getOriginalFileName() + "\"")
			.contentType(MediaType.parseMediaType(image.getContentType()))
			.contentLength(image.getByteSize())
			.body(body);
	}

	private com.coachingcenter.api.auth.permission.CurrentActor actor() {
		var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext()
			.getAuthentication();
		if (authentication != null
				&& authentication.getPrincipal() instanceof com.coachingcenter.api.auth.permission.CurrentActor actor) {
			return actor;
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

}
