package com.coachingcenter.api.exams;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.common.web.PageResponse;

import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

import tools.jackson.databind.JsonNode;

@RestController
public class ExamComponentController {

	private final ExamComponentService components;

	private final McqAttemptService mcqAttempts;

	private final WrittenSubmissionService writtenSubmissions;

	private final ExamResultService results;

	private final PermissionChecker permissions;

	public ExamComponentController(ExamComponentService components, McqAttemptService mcqAttempts,
			WrittenSubmissionService writtenSubmissions, ExamResultService results, PermissionChecker permissions) {
		this.components = components;
		this.mcqAttempts = mcqAttempts;
		this.writtenSubmissions = writtenSubmissions;
		this.results = results;
		this.permissions = permissions;
	}

	@PostMapping("/api/v1/exam-papers/{paperId}/components")
	public ResponseEntity<ExamComponentService.ComponentView> createComponent(@PathVariable UUID paperId,
			@RequestBody JsonNode body) {
		require(Permissions.EXAM_MANAGE);
		return ResponseEntity.status(HttpStatus.CREATED).body(components.createComponent(paperId, body));
	}

	@GetMapping("/api/v1/exam-components/{componentId}")
	public ExamComponentService.ComponentView getComponent(@PathVariable UUID componentId) {
		require(Permissions.EXAM_READ);
		return components.getComponent(componentId);
	}

	@PatchMapping("/api/v1/exam-components/{componentId}")
	public ExamComponentService.ComponentView patchComponent(@PathVariable UUID componentId, @RequestBody JsonNode body) {
		require(Permissions.EXAM_MANAGE);
		return components.patchComponent(componentId, body);
	}

	@PostMapping("/api/v1/exam-components/{componentId}/schedule")
	public ExamComponentService.ComponentView scheduleComponent(@PathVariable UUID componentId) {
		require(Permissions.EXAM_MANAGE);
		return components.scheduleComponent(componentId);
	}

	@GetMapping("/api/v1/exam-papers/{paperId}")
	public ExamComponentService.PaperView getPaper(@PathVariable UUID paperId) {
		require(Permissions.EXAM_READ);
		return components.getPaper(paperId);
	}

	@PatchMapping("/api/v1/exam-papers/{paperId}")
	public ExamComponentService.PaperView patchPaper(@PathVariable UUID paperId, @RequestBody JsonNode body) {
		require(Permissions.EXAM_MANAGE);
		return components.patchPaper(paperId, body);
	}

	@PostMapping("/api/v1/exam-components/{componentId}/questions")
	public ResponseEntity<ExamComponentService.QuestionView> createQuestion(@PathVariable UUID componentId,
			@RequestBody JsonNode body) {
		require(Permissions.EXAM_MANAGE);
		return ResponseEntity.status(HttpStatus.CREATED).body(components.createQuestion(componentId, body));
	}

	@PostMapping("/api/v1/exam-components/{componentId}/questions/{questionId}")
	public ResponseEntity<Void> attachQuestion(@PathVariable UUID componentId, @PathVariable UUID questionId) {
		require(Permissions.EXAM_MANAGE);
		components.attachQuestion(componentId, questionId);
		return ResponseEntity.noContent().build();
	}

	@PatchMapping("/api/v1/questions/{questionId}")
	public ExamComponentService.QuestionView updateQuestion(@PathVariable UUID questionId, @RequestBody JsonNode body) {
		require(Permissions.EXAM_MANAGE);
		return components.updateQuestion(questionId, body);
	}

	@GetMapping("/api/v1/questions/{questionId}")
	public ExamComponentService.QuestionView getQuestion(@PathVariable UUID questionId) {
		require(Permissions.EXAM_READ);
		return components.getQuestion(questionId);
	}

	@PostMapping("/api/v1/exam-components/{componentId}/questions/reorder")
	public ResponseEntity<Void> reorderQuestions(@PathVariable UUID componentId, @RequestBody JsonNode body) {
		require(Permissions.EXAM_MANAGE);
		components.reorderQuestions(componentId, body);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/api/v1/exam-components/{componentId}/questions/{questionId}")
	public ResponseEntity<Void> detachQuestion(@PathVariable UUID componentId, @PathVariable UUID questionId) {
		require(Permissions.EXAM_MANAGE);
		components.detachQuestion(componentId, questionId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/api/v1/exam-components/{componentId}/attempts")
	public java.util.List<McqAttemptService.AttemptView> listAttempts(@PathVariable UUID componentId) {
		require(Permissions.EXAM_READ);
		return mcqAttempts.listAdmin(componentId);
	}

	@GetMapping("/api/v1/exam-components/{componentId}/submissions")
	public PageResponse<WrittenSubmissionService.AdminSubmissionSummary> listSubmissions(
			@PathVariable UUID componentId, @RequestParam String status, @PageableDefault(size = 20) Pageable pageable) {
		require(Permissions.EXAM_READ);
		return writtenSubmissions.listAdmin(componentId, status, pageable);
	}

	@PostMapping("/api/v1/exam-components/{componentId}/submissions/{submissionId}/evaluation")
	public WrittenSubmissionService.SubmissionView evaluateSubmission(@PathVariable UUID componentId,
			@PathVariable UUID submissionId, @RequestBody JsonNode body) {
		require(Permissions.EXAM_EVALUATE);
		return results.evaluate(componentId, submissionId, actor().userId(), body);
	}

	@PostMapping("/api/v1/exam-components/{componentId}/results/publish")
	public ResponseEntity<Void> publishResults(@PathVariable UUID componentId) {
		require(Permissions.EXAM_EVALUATE);
		results.publish(componentId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/api/v1/exam-components/{componentId}/results/unpublish")
	public ResponseEntity<Void> unpublishResults(@PathVariable UUID componentId) {
		require(Permissions.EXAM_EVALUATE);
		results.unpublish(componentId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/api/v1/exam-components/{componentId}/students/{studentId}/result")
	public ExamResultService.ComponentResultView adminStudentResult(@PathVariable UUID componentId,
			@PathVariable UUID studentId) {
		require(Permissions.EXAM_READ);
		return results.adminComponentResult(componentId, studentId);
	}

	@GetMapping("/api/v1/exam-papers/{paperId}/leaderboard")
	public PageResponse<ExamResultService.LeaderboardRow> paperLeaderboard(@PathVariable UUID paperId,
			@PageableDefault(size = 20) Pageable pageable) {
		require(Permissions.EXAM_READ);
		return results.leaderboard(paperId, pageable, false, null, null);
	}

	@GetMapping("/api/v1/exam-components/{componentId}/question-stats")
	public java.util.List<ExamResultService.QuestionStatView> questionStats(@PathVariable UUID componentId) {
		require(Permissions.EXAM_READ);
		return results.questionStats(componentId);
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

	private void require(String permission) {
		if (!permissions.has(permission)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
	}

}
