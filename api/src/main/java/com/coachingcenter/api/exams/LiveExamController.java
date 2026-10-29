package com.coachingcenter.api.exams;

import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.web.PageResponse;

import tools.jackson.databind.JsonNode;

@RestController
public class LiveExamController {

	private final LiveExamService exams;

	private final LiveExamSittingService sittings;

	private final LiveExamRemoval removal;

	private final PermissionChecker permissions;

	private final ExamCoverService covers;

	public LiveExamController(LiveExamService exams, LiveExamSittingService sittings, LiveExamRemoval removal,
			PermissionChecker permissions, ExamCoverService covers) {
		this.removal = removal;
		this.exams = exams;
		this.sittings = sittings;
		this.permissions = permissions;
		this.covers = covers;
	}

	@PostMapping("/api/v1/live-exam-modules/{moduleId}/exams")
	public ResponseEntity<LiveExamService.ExamView> create(@PathVariable UUID moduleId, @RequestBody JsonNode body) {
		require(Permissions.EXAM_MANAGE);
		requirePricePermission(body);
		return ResponseEntity.status(HttpStatus.CREATED).body(exams.create(moduleId, body));
	}

	/** A standalone exam: it sits in a category and in no package until it is added to one (R2). */
	@PostMapping("/api/v1/live-exams")
	public ResponseEntity<LiveExamService.ExamView> createStandalone(@RequestBody JsonNode body) {
		require(Permissions.EXAM_MANAGE);
		requirePricePermission(body);
		return ResponseEntity.status(HttpStatus.CREATED).body(exams.create(null, body));
	}

	@GetMapping("/api/v1/live-exam-modules/{moduleId}/exams")
	public PageResponse<LiveExamService.ExamView> list(@PathVariable UUID moduleId,
			@RequestParam(required = false) String examType, @PageableDefault(size = 20) Pageable pageable) {
		String type = LiveExamTypeFilter.parse(examType);
		if (permissions.has(Permissions.EXAM_READ)) {
			return PageResponse.from(exams.list(moduleId, pageable, false, null));
		}
		if (permissions.has(Permissions.LIVE_EXAM_BROWSE)) {
			return PageResponse.from(exams.list(moduleId, pageable, true, actorId(), type));
		}
		throw denied();
	}

	@GetMapping("/api/v1/live-exams/{id}")
	public Object get(@PathVariable UUID id) {
		if (permissions.has(Permissions.EXAM_READ)) {
			return exams.get(id, true, null);
		}
		if (permissions.has(Permissions.LIVE_EXAM_BROWSE)) {
			return sittings.studentExam(id, actorId());
		}
		throw denied();
	}

	@PatchMapping("/api/v1/live-exams/{id}")
	public LiveExamService.ExamView update(@PathVariable UUID id, @RequestBody JsonNode body) {
		require(Permissions.EXAM_MANAGE);
		requirePricePermission(body);
		return exams.update(id, body, actorId());
	}

	/** A draft copy of this exam in the same module, with the title suffixed and no date or price. */
	@PostMapping("/api/v1/live-exams/{id}/copy")
	public ResponseEntity<LiveExamService.ExamView> copy(@PathVariable UUID id) {
		require(Permissions.EXAM_MANAGE);
		return ResponseEntity.status(HttpStatus.CREATED).body(exams.copy(id, actorId()));
	}

	@PostMapping("/api/v1/live-exams/{id}/final-save")
	public LiveExamService.ExamView finalSave(@PathVariable UUID id) {
		require(Permissions.EXAM_MANAGE);
		return exams.finalSave(id);
	}

	@PostMapping("/api/v1/live-exams/{id}/publish")
	public LiveExamService.ExamView publish(@PathVariable UUID id,
			@RequestBody(required = false) JsonNode body) {
		require(Permissions.EXAM_PUBLISH);
		String revisionId = body != null && body.hasNonNull("revisionId") ? body.get("revisionId").asText() : null;
		return exams.publish(id, revisionId, actorId());
	}

	@PostMapping("/api/v1/live-exams/{id}/archive")
	public LiveExamService.ExamView archive(@PathVariable UUID id) {
		require(Permissions.EXAM_DELETE);
		return exams.archive(id);
	}

	@DeleteMapping("/api/v1/live-exams/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		require(Permissions.EXAM_DELETE);
		removal.delete(id);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/api/v1/live-exams/{id}/questions/{questionId}")
	public ResponseEntity<Void> deleteQuestion(@PathVariable UUID id, @PathVariable UUID questionId) {
		require(Permissions.EXAM_MANAGE);
		exams.deleteQuestion(id, questionId, actorId());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/api/v1/live-exams/{id}/questions")
	public ResponseEntity<LiveExamService.QuestionView> addQuestion(@PathVariable UUID id, @RequestBody JsonNode body) {
		require(Permissions.EXAM_MANAGE);
		return ResponseEntity.status(HttpStatus.CREATED).body(exams.addQuestion(id, body, actorId()));
	}

	@PatchMapping("/api/v1/live-exams/{id}/questions/{questionId}")
	public LiveExamService.QuestionView updateQuestion(@PathVariable UUID id, @PathVariable UUID questionId,
			@RequestBody JsonNode body) {
		require(Permissions.EXAM_MANAGE);
		return exams.updateQuestion(id, questionId, body, actorId());
	}

	/** The exam's cover for staff screens. The public cover route serves published exams only, so a draft shows here. */
	@GetMapping("/api/v1/live-exams/{id}/cover")
	public ResponseEntity<InputStreamResource> staffCover(@PathVariable UUID id) {
		require(Permissions.EXAM_READ);
		ModuleCoverService.CoverFile file = covers.open(id);
		return ResponseEntity.ok()
			.header(HttpHeaders.CACHE_CONTROL, "private, no-cache")
			.contentType(MediaType.parseMediaType(file.contentType()))
			.contentLength(file.byteSize())
			.body(new InputStreamResource(file.body()));
	}

	/** Replaces the exam's cover (E1). The change is live at once, like the package cover. */
	@PostMapping(value = "/api/v1/live-exams/{id}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public LiveExamService.ExamView uploadCover(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
		require(Permissions.MODULE_COVER_MANAGE);
		covers.upload(id, file);
		return exams.get(id, true, null);
	}

	@DeleteMapping("/api/v1/live-exams/{id}/cover")
	public LiveExamService.ExamView removeCover(@PathVariable UUID id) {
		require(Permissions.MODULE_COVER_MANAGE);
		covers.remove(id);
		return exams.get(id, true, null);
	}

	@PostMapping(value = "/api/v1/live-exams/{id}/question-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public LiveExamService.ExamView uploadQuestionPdf(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
		require(Permissions.EXAM_MANAGE);
		return exams.uploadQuestionPdf(id, file, actorId());
	}

	private void require(String permission) {
		if (!permissions.has(permission)) {
			throw denied();
		}
	}

	private void requirePricePermission(JsonNode body) {
		boolean commercial = body.has("price") || body.has("free") || body.has("discountPercent")
				|| body.has("discountEndsAt") || body.has("accessDays");
		if (commercial && !permissions.has(Permissions.EXAM_PRICE)) {
			throw denied();
		}
	}

	private static ApiException denied() {
		return new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
	}

	private static UUID actorId() {
		var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof com.coachingcenter.api.auth.permission.CurrentActor actor) {
			return actor.userId();
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

}
