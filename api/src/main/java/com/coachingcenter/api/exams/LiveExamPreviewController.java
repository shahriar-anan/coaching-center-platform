package com.coachingcenter.api.exams;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.config.AppProperties;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

/** The student preview of an exam (C10'): the paper without the key, for promotion. */
@RestController
public class LiveExamPreviewController {

	private final LiveExamPreviewService previews;

	private final PermissionChecker permissions;

	private final AppProperties appProperties;

	public LiveExamPreviewController(LiveExamPreviewService previews, PermissionChecker permissions,
			AppProperties appProperties) {
		this.previews = previews;
		this.permissions = permissions;
		this.appProperties = appProperties;
	}

	@GetMapping("/api/v1/live-exams/{id}/preview")
	public LiveExamPreviewService.PreviewView preview(@PathVariable UUID id) {
		requireStudent();
		return previews.preview(id);
	}

	@GetMapping("/api/v1/live-exams/{id}/preview/question-pdf")
	public ResponseEntity<byte[]> paperPdf(@PathVariable UUID id) {
		requireStudent();
		return ResponseEntity.ok()
			.contentType(MediaType.APPLICATION_PDF)
			.header(HttpHeaders.CONTENT_DISPOSITION, "inline")
			.header(HttpHeaders.CACHE_CONTROL, "no-store")
			.header("X-Content-Type-Options", "nosniff")
			.header("Content-Security-Policy", frameAncestors())
			.body(previews.paperPdf(id));
	}

	private String frameAncestors() {
		StringBuilder policy = new StringBuilder("frame-ancestors 'self'");
		if (appProperties.cors() != null && appProperties.cors().allowedOrigins() != null) {
			for (String origin : appProperties.cors().allowedOrigins()) {
				if (origin != null && !origin.isBlank()) {
					policy.append(' ').append(origin.trim());
				}
			}
		}
		return policy.toString();
	}

	private void requireStudent() {
		if (!permissions.has(Permissions.LIVE_EXAM_BROWSE)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
	}

}
