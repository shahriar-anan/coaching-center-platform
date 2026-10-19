package com.coachingcenter.api.exams;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

import tools.jackson.databind.JsonNode;

@RestController
public class LiveExamEvaluationController {

	private final LiveExamEvaluationService evaluations;

	private final PermissionChecker permissions;

	public LiveExamEvaluationController(LiveExamEvaluationService evaluations, PermissionChecker permissions) {
		this.evaluations = evaluations;
		this.permissions = permissions;
	}

	@PostMapping("/api/v1/live-exam-modules/{moduleId}/instructor")
	public ResponseEntity<LiveExamEvaluationService.Assignment> assign(@PathVariable UUID moduleId,
			@RequestBody JsonNode body) {
		require(Permissions.LIVE_EXAM_ASSIGN);
		JsonNode instructorId = body.get("instructorId");
		if (instructorId == null || instructorId.isNull() || instructorId.asText().isBlank()) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
		}
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(evaluations.assign(moduleId, UUID.fromString(instructorId.asText())));
	}

	@GetMapping("/api/v1/live-exam-modules/{moduleId}/submissions")
	public List<LiveExamEvaluationService.SubmissionView> list(@PathVariable UUID moduleId) {
		requireReader();
		return evaluations.list(moduleId, actor());
	}

	@GetMapping("/api/v1/live-exam-participants/{id}")
	public LiveExamEvaluationService.SubmissionView open(@PathVariable UUID id) {
		requireReader();
		return evaluations.open(id, actor());
	}

	@GetMapping("/api/v1/live-exam-participants/{id}/answer-pdf")
	public ResponseEntity<byte[]> answerPdf(@PathVariable UUID id) {
		require(Permissions.LIVE_EXAM_EVALUATE);
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(evaluations.answerPdf(id, actor()));
	}

	@PostMapping(value = "/api/v1/live-exam-participants/{id}/evaluation", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public LiveExamEvaluationService.SubmissionView evaluate(@PathVariable UUID id, @RequestParam("marks") int marks,
			@RequestParam(value = "comment", required = false) String comment, @RequestParam("file") MultipartFile file) {
		require(Permissions.LIVE_EXAM_EVALUATE);
		return evaluations.evaluate(id, actor(), marks, comment, file);
	}

	@GetMapping("/api/v1/live-exams/{id}/leaderboard")
	public LiveExamEvaluationService.Leaderboard leaderboard(@PathVariable UUID id) {
		if (permissions.has(Permissions.LIVE_EXAM_EVALUATE)) {
			return evaluations.leaderboard(id, actor(), true);
		}
		if (permissions.has(Permissions.LIVE_EXAM_BROWSE)) {
			return evaluations.leaderboard(id, actor(), false);
		}
		throw denied();
	}

	@GetMapping("/api/v1/live-exams/{id}/evaluated-pdf")
	public ResponseEntity<byte[]> evaluatedPdf(@PathVariable UUID id) {
		if (!permissions.has(Permissions.LIVE_EXAM_BROWSE)) {
			throw denied();
		}
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(evaluations.evaluatedPdf(id, actor().userId()));
	}

	private void require(String permission) {
		if (!permissions.has(permission)) {
			throw denied();
		}
	}

	private void requireReader() {
		if (!permissions.has(Permissions.LIVE_EXAM_EVALUATE) && !permissions.has(Permissions.EXAM_READ)) {
			throw denied();
		}
	}

	private static ApiException denied() {
		return new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
	}

	private static CurrentActor actor() {
		var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor) {
			return actor;
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

}
