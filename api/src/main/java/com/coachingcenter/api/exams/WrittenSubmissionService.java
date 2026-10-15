package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.web.PageResponse;
import com.coachingcenter.api.files.ExamImageStorage;

import tools.jackson.databind.JsonNode;

@Service
public class WrittenSubmissionService {

	private final ExamModuleRepository modules;

	private final ExamNodeRepository nodes;

	private final PaperComponentRepository components;

	private final ComponentQuestionRepository links;

	private final QuestionRepository questions;

	private final ExamAccessService access;

	private final WrittenSubmissionRepository submissions;

	private final WrittenSubmissionImageRepository images;

	private final WrittenSubmissionQuestionRepository submissionQuestions;

	private final ExamImageStorage imageStorage;

	public WrittenSubmissionService(ExamModuleRepository modules, ExamNodeRepository nodes,
			PaperComponentRepository components, ComponentQuestionRepository links, QuestionRepository questions,
			ExamAccessService access, WrittenSubmissionRepository submissions,
			WrittenSubmissionImageRepository images, WrittenSubmissionQuestionRepository submissionQuestions,
			ExamImageStorage imageStorage) {
		this.modules = modules;
		this.nodes = nodes;
		this.components = components;
		this.links = links;
		this.questions = questions;
		this.access = access;
		this.submissions = submissions;
		this.images = images;
		this.submissionQuestions = submissionQuestions;
		this.imageStorage = imageStorage;
	}

	@Transactional
	public SubmissionView uploadImage(UUID moduleId, UUID componentId, UUID studentId, MultipartFile file) {
		Context ctx = requireWrittenContext(moduleId, componentId);
		WrittenSubmission submission = submissions.findByComponentIdAndStudentIdAndDeletedAtIsNull(componentId, studentId)
			.orElse(null);
		if (submission == null) {
			access.requireAccess(ctx.module, studentId);
			if (links.findByComponentIdAndDeletedAtIsNullOrderByPositionAsc(componentId).isEmpty()) {
				throw conflict(ErrorCode.COMPONENT_EMPTY, "Component has no questions.");
			}
			submission = new WrittenSubmission();
			submission.setComponentId(componentId);
			submission.setStudentId(studentId);
			submission.setStatus("DRAFT");
			submission = submissions.save(submission);
		}
		else {
			requireOwnerOrAccess(ctx.module, submission, studentId);
		}
		requireDraftBeforeDeadline(ctx.component, submission);
		ExamImageStorage.StoredExamImage stored = imageStorage.store(file);
		List<WrittenSubmissionImage> existing = images
			.findBySubmissionIdAndDeletedAtIsNullOrderByPositionAsc(submission.getId());
		WrittenSubmissionImage row = new WrittenSubmissionImage();
		row.setSubmissionId(submission.getId());
		row.setStoredFileName(stored.storedFileName());
		row.setOriginalFileName(stored.originalFileName());
		row.setByteSize(stored.byteSize());
		row.setContentType(stored.contentType());
		row.setPosition(existing.size());
		images.save(row);
		return buildView(submission, ctx.component, studentId, false);
	}

	@Transactional
	public SubmissionView reorderImages(UUID moduleId, UUID componentId, UUID studentId, JsonNode body) {
		Context ctx = requireWrittenContext(moduleId, componentId);
		WrittenSubmission submission = requireOwnedSubmission(componentId, studentId);
		requireOwnerOrAccess(ctx.module, submission, studentId);
		requireDraftBeforeDeadline(ctx.component, submission);
		JsonNode idsNode = body.get("imageIds");
		if (idsNode == null || !idsNode.isArray()) {
			throw validationFailed();
		}
		List<WrittenSubmissionImage> rows = images
			.findBySubmissionIdAndDeletedAtIsNullOrderByPositionAsc(submission.getId());
		if (idsNode.size() != rows.size()) {
			throw validationFailed();
		}
		Map<UUID, WrittenSubmissionImage> byId = new HashMap<>();
		for (WrittenSubmissionImage row : rows) {
			byId.put(row.getId(), row);
		}
		Set<UUID> seen = new HashSet<>();
		for (int i = 0; i < idsNode.size(); i++) {
			UUID imageId = UUID.fromString(idsNode.get(i).asText());
			if (!seen.add(imageId) || !byId.containsKey(imageId)) {
				throw validationFailed();
			}
			byId.get(imageId).setPosition(i);
		}
		return buildView(submission, ctx.component, studentId, false);
	}

	@Transactional
	public SubmissionView deleteImage(UUID moduleId, UUID componentId, UUID studentId, UUID imageId) {
		Context ctx = requireWrittenContext(moduleId, componentId);
		WrittenSubmission submission = requireOwnedSubmission(componentId, studentId);
		requireOwnerOrAccess(ctx.module, submission, studentId);
		requireDraftBeforeDeadline(ctx.component, submission);
		WrittenSubmissionImage image = images.findByIdAndSubmissionIdAndDeletedAtIsNull(imageId, submission.getId())
			.orElseThrow(this::notFound);
		image.setDeletedAt(Instant.now());
		imageStorage.delete(image.getStoredFileName());
		repackPositions(submission.getId());
		return buildView(submission, ctx.component, studentId, false);
	}

	@Transactional
	public SubmissionView submit(UUID moduleId, UUID componentId, UUID studentId) {
		Context ctx = requireWrittenContext(moduleId, componentId);
		WrittenSubmission submission = requireOwnedSubmission(componentId, studentId);
		if (!"DRAFT".equals(submission.getStatus())) {
			throw conflict(ErrorCode.SUBMISSION_LOCKED, "Submission is locked.");
		}
		requireOwnerOrAccess(ctx.module, submission, studentId);
		if (deadlinePassed(ctx.component)) {
			throw conflict(ErrorCode.DEADLINE_PASSED, "Deadline has passed.");
		}
		List<WrittenSubmissionImage> remaining = images
			.findBySubmissionIdAndDeletedAtIsNullOrderByPositionAsc(submission.getId());
		if (remaining.isEmpty()) {
			throw new ApiException(ErrorCode.SUBMISSION_EMPTY, HttpStatus.BAD_REQUEST, "Submission has no images.");
		}
		ExamNode paper = nodes.findByIdAndDeletedAtIsNull(ctx.component.getPaperId()).orElseThrow(this::notFound);
		submission.setStatus("UNDER_REVIEW");
		submission.setSubmittedAt(Instant.now());
		submission.setPassPercent(paper.getPassPercent());
		submissions.save(submission);
		snapshotQuestions(submission.getId(), componentId);
		return buildView(submission, ctx.component, studentId, false);
	}

	@Transactional(readOnly = true)
	public SubmissionView getStudent(UUID moduleId, UUID componentId, UUID studentId) {
		Context ctx = requireWrittenContext(moduleId, componentId);
		WrittenSubmission submission = submissions.findByComponentIdAndStudentIdAndDeletedAtIsNull(componentId, studentId)
			.orElse(null);
		if (submission == null) {
			if (submissions.existsByComponentIdAndStudentIdNotAndDeletedAtIsNull(componentId, studentId)) {
				throw forbidden();
			}
			throw notFound();
		}
		requireOwnerOrAccess(ctx.module, submission, studentId);
		return buildView(submission, ctx.component, studentId, ctx.component.isResultsPublished());
	}

	@Transactional(readOnly = true)
	public PageResponse<AdminSubmissionSummary> listAdmin(UUID componentId, String status, Pageable pageable) {
		PaperComponent component = components.findByIdAndDeletedAtIsNull(componentId).orElseThrow(this::notFound);
		if (!"WRITTEN".equals(component.getComponentType())) {
			throw validationFailed();
		}
		Page<WrittenSubmission> page = submissions.findByComponentIdAndStatusAndDeletedAtIsNull(componentId, status,
				pageable);
		return PageResponse.from(page.map(row -> new AdminSubmissionSummary(row.getId(), row.getStudentId(),
				row.getStatus(), row.getSubmittedAt())));
	}

	@Transactional(readOnly = true)
	public void ensureCanDownloadImage(UUID imageId, UUID viewerId, boolean adminRead) {
		WrittenSubmissionImage image = images.findById(imageId).filter(row -> row.getDeletedAt() == null)
			.orElseThrow(this::notFound);
		WrittenSubmission submission = submissions.findByIdAndDeletedAtIsNull(image.getSubmissionId())
			.orElseThrow(this::notFound);
		if (adminRead) {
			return;
		}
		if (!submission.getStudentId().equals(viewerId)) {
			throw forbidden();
		}
	}

	private void snapshotQuestions(UUID submissionId, UUID componentId) {
		for (ComponentQuestion link : links.findByComponentIdAndDeletedAtIsNullOrderByPositionAsc(componentId)) {
			Question question = questions.findByIdAndDeletedAtIsNull(link.getQuestionId()).orElseThrow(this::notFound);
			WrittenSubmissionQuestion row = new WrittenSubmissionQuestion();
			row.setSubmissionId(submissionId);
			row.setQuestionId(question.getId());
			row.setStem(question.getStem());
			row.setExplanation(question.getExplanation());
			row.setMaxMarks(question.getMarks());
			row.setPosition(link.getPosition());
			submissionQuestions.save(row);
		}
	}

	private void repackPositions(UUID submissionId) {
		List<WrittenSubmissionImage> rows = images.findBySubmissionIdAndDeletedAtIsNullOrderByPositionAsc(submissionId);
		for (int i = 0; i < rows.size(); i++) {
			rows.get(i).setPosition(i);
		}
	}

	private SubmissionView buildView(WrittenSubmission submission, PaperComponent component, UUID viewerId,
			boolean showExplanation) {
		List<ImageView> imageViews = new ArrayList<>();
		for (WrittenSubmissionImage image : images
			.findBySubmissionIdAndDeletedAtIsNullOrderByPositionAsc(submission.getId())) {
			imageViews.add(new ImageView(image.getId(), image.getPosition()));
		}
		List<QuestionView> questionViews = new ArrayList<>();
		for (WrittenSubmissionQuestion question : submissionQuestions
			.findBySubmissionIdAndDeletedAtIsNullOrderByPositionAsc(submission.getId())) {
			String explanation = showExplanation ? question.getExplanation() : null;
			BigDecimal marksAwarded = showExplanation ? question.getMarksAwarded() : null;
			questionViews.add(new QuestionView(question.getId(), question.getQuestionId(), question.getStem(),
					explanation, question.getMaxMarks(), marksAwarded, question.getPosition()));
		}
		return new SubmissionView(submission.getId(), submission.getComponentId(), submission.getStudentId(),
				submission.getStatus(), submission.getSubmittedAt(), submission.getPassPercent(), imageViews,
				questionViews);
	}

	SubmissionView buildAdminView(WrittenSubmission submission, PaperComponent component) {
		return buildView(submission, component, submission.getStudentId(), true);
	}

	private void requireDraftBeforeDeadline(PaperComponent component, WrittenSubmission submission) {
		if (!"DRAFT".equals(submission.getStatus())) {
			throw conflict(ErrorCode.SUBMISSION_LOCKED, "Submission is locked.");
		}
		if (deadlinePassed(component)) {
			throw conflict(ErrorCode.DEADLINE_PASSED, "Deadline has passed.");
		}
	}

	private boolean deadlinePassed(PaperComponent component) {
		return component.getDeadlineAt() != null && !Instant.now().isBefore(component.getDeadlineAt());
	}

	private WrittenSubmission requireOwnedSubmission(UUID componentId, UUID studentId) {
		return submissions.findByComponentIdAndStudentIdAndDeletedAtIsNull(componentId, studentId)
			.orElseThrow(this::notFound);
	}

	private void requireOwnerOrAccess(ExamModule module, WrittenSubmission submission, UUID studentId) {
		if (submission.getStudentId().equals(studentId) && "DRAFT".equals(submission.getStatus())) {
			return;
		}
		access.requireAccess(module, studentId);
		if (!submission.getStudentId().equals(studentId)) {
			throw forbidden();
		}
	}

	private Context requireWrittenContext(UUID moduleId, UUID componentId) {
		ExamModule module = modules.findByIdAndDeletedAtIsNull(moduleId).orElseThrow(this::notFound);
		PaperComponent component = components.findByIdAndDeletedAtIsNull(componentId).orElseThrow(this::notFound);
		if (!"WRITTEN".equals(component.getComponentType())) {
			throw validationFailed();
		}
		ExamNode paper = nodes.findByIdAndDeletedAtIsNull(component.getPaperId()).orElseThrow(this::notFound);
		if (!moduleId.equals(paper.getModuleId())) {
			throw notFound();
		}
		return new Context(module, component);
	}

	private record Context(ExamModule module, PaperComponent component) {
	}

	private ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	private static ApiException forbidden() {
		return new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
	}

	private static ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
	}

	private static ApiException conflict(ErrorCode code, String message) {
		return new ApiException(code, HttpStatus.CONFLICT, message);
	}

	public record SubmissionView(UUID id, UUID componentId, UUID studentId, String status, Instant submittedAt,
			Integer passPercent, List<ImageView> images, List<QuestionView> questions) {
	}

	public record ImageView(UUID id, int position) {
	}

	public record QuestionView(UUID id, UUID questionId, String stem, String explanation, java.math.BigDecimal maxMarks,
			java.math.BigDecimal marksAwarded, int position) {
	}

	public record AdminSubmissionSummary(UUID id, UUID studentId, String status, Instant submittedAt) {
	}

}
