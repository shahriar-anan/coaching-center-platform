package com.coachingcenter.api.exams;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
import com.coachingcenter.api.common.config.AppProperties;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.web.PageResponse;

import tools.jackson.databind.JsonNode;

@RestController
public class LiveExamSittingController {

	private final LiveExamSittingService sittings;

	private final LiveExamService exams;

	private final PermissionChecker permissions;

	private final AppProperties appProperties;

	public LiveExamSittingController(LiveExamSittingService sittings, LiveExamService exams, PermissionChecker permissions,
			AppProperties appProperties) {
		this.sittings = sittings;
		this.exams = exams;
		this.permissions = permissions;
		this.appProperties = appProperties;
	}

	@GetMapping("/api/v1/me/exam-routine")
	public PageResponse<LiveExamService.RoutineView> routine(@PageableDefault(size = 20) Pageable pageable) {
		requireStudent();
		return PageResponse.from(exams.routine(actorId(), pageable));
	}

	@PostMapping("/api/v1/live-exams/{id}/take")
	public LiveExamSittingService.TakeView take(@PathVariable UUID id, @RequestBody(required = false) JsonNode body) {
		requireStudent();
		return sittings.take(id, actorId(), body);
	}

	@PostMapping("/api/v1/live-exams/{id}/retake")
	public LiveExamSittingService.TakeView retake(@PathVariable UUID id) {
		requireStudent();
		return sittings.retake(id, actorId());
	}

	@PutMapping("/api/v1/live-exams/{id}/answers")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void saveAnswers(@PathVariable UUID id, @RequestBody JsonNode body) {
		requireStudent();
		sittings.saveAnswers(id, actorId(), body);
	}

	@PostMapping("/api/v1/live-exams/{id}/submit")
	public LiveExamSittingService.PostedResult submit(@PathVariable UUID id) {
		requireStudent();
		return sittings.submit(id, actorId());
	}

	@GetMapping("/api/v1/live-exams/{id}/result")
	public LiveExamSittingService.ReviewedResult result(@PathVariable UUID id) {
		requireStudent();
		return sittings.result(id, actorId());
	}

	@GetMapping("/api/v1/live-exams/{id}/question-pdf")
	public ResponseEntity<byte[]> questionPdf(@PathVariable UUID id) {
		requireStudent();
		return ResponseEntity.ok()
			.contentType(MediaType.APPLICATION_PDF)
			.header(HttpHeaders.CONTENT_DISPOSITION, "inline")
			.header(HttpHeaders.CACHE_CONTROL, "no-store")
			.header("X-Content-Type-Options", "nosniff")
			.header("Content-Security-Policy", frameAncestors())
			.body(sittings.questionPdf(id, actorId()));
	}

	@PostMapping(value = "/api/v1/live-exams/{id}/answer-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void uploadAnswer(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
		requireStudent();
		sittings.uploadAnswer(id, actorId(), file);
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

	private static UUID actorId() {
		var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null
				&& authentication.getPrincipal() instanceof com.coachingcenter.api.auth.permission.CurrentActor actor) {
			return actor.userId();
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

}
