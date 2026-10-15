package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

import tools.jackson.databind.JsonNode;

@Service
public class McqAttemptService {

	private final ExamModuleRepository modules;

	private final ExamNodeRepository nodes;

	private final PaperComponentRepository components;

	private final ComponentQuestionRepository links;

	private final QuestionRepository questions;

	private final QuestionOptionRepository options;

	private final ExamAccessService access;

	private final McqAttemptRepository attempts;

	private final McqAttemptQuestionRepository attemptQuestions;

	private final McqAttemptOptionRepository attemptOptions;

	private final McqAnswerRepository answers;

	private final PlatformTransactionManager transactionManager;

	public McqAttemptService(ExamModuleRepository modules, ExamNodeRepository nodes,
			PaperComponentRepository components, ComponentQuestionRepository links, QuestionRepository questions,
			QuestionOptionRepository options, ExamAccessService access, McqAttemptRepository attempts,
			McqAttemptQuestionRepository attemptQuestions, McqAttemptOptionRepository attemptOptions,
			McqAnswerRepository answers, PlatformTransactionManager transactionManager) {
		this.modules = modules;
		this.nodes = nodes;
		this.components = components;
		this.links = links;
		this.questions = questions;
		this.options = options;
		this.access = access;
		this.attempts = attempts;
		this.attemptQuestions = attemptQuestions;
		this.attemptOptions = attemptOptions;
		this.answers = answers;
		this.transactionManager = transactionManager;
	}

	@Transactional
	public AttemptView start(UUID moduleId, UUID componentId, UUID studentId) {
		Context ctx = requireMcqContext(moduleId, componentId);
		access.requireAccess(ctx.module, studentId);
		if (!"ACTIVE".equals(ExamComponentLifecycle.derive(ctx.component))) {
			throw conflict(ErrorCode.COMPONENT_NOT_ACTIVE, "Component is not active.");
		}
		if (attempts.findByComponentIdAndStudentIdAndDeletedAtIsNull(componentId, studentId).isPresent()) {
			throw conflict(ErrorCode.ATTEMPT_ALREADY_USED, "Attempt already exists.");
		}
		List<ComponentQuestion> rows = links.findByComponentIdAndDeletedAtIsNullOrderByPositionAsc(componentId);
		if (rows.isEmpty()) {
			throw conflict(ErrorCode.COMPONENT_EMPTY, "Component has no questions.");
		}
		ExamNode paper = nodes.findByIdAndDeletedAtIsNull(ctx.component.getPaperId()).orElseThrow(this::notFound);
		Integer passPercent = paper.getPassPercent();
		BigDecimal maxScore = BigDecimal.ZERO;
		McqAttempt attempt = new McqAttempt();
		attempt.setComponentId(componentId);
		attempt.setStudentId(studentId);
		attempt.setStartedAt(Instant.now());
		attempt.setDurationSeconds(ctx.component.getDurationSeconds() == null ? 0 : ctx.component.getDurationSeconds());
		attempt.setPassPercent(passPercent);
		attempt.setStatus("IN_PROGRESS");
		for (ComponentQuestion row : rows) {
			maxScore = maxScore.add(requireQuestion(row.getQuestionId()).getMarks());
		}
		attempt.setMaxScore(maxScore);
		attempt = attempts.save(attempt);
		for (ComponentQuestion row : rows) {
			snapshotQuestion(attempt.getId(), row);
		}
		return buildView(attempt, ctx.component, studentId, false, true);
	}

	@Transactional
	public AttemptView getStudent(UUID moduleId, UUID componentId, UUID studentId) {
		Context ctx = requireMcqContext(moduleId, componentId);
		McqAttempt attempt = attempts.findByComponentIdAndStudentIdAndDeletedAtIsNull(componentId, studentId)
			.orElse(null);
		if (attempt == null) {
			if (attempts.existsByComponentIdAndStudentIdNotAndDeletedAtIsNull(componentId, studentId)) {
				throw forbidden();
			}
			throw notFound();
		}
		if (!attempt.getStudentId().equals(studentId)) {
			throw forbidden();
		}
		requireOwnerOrAccess(ctx.module, attempt, studentId);
		finalizeIfExpired(attempt, ctx.component);
		boolean inProgress = "IN_PROGRESS".equals(attempt.getStatus());
		return buildView(attempt, ctx.component, studentId, false, inProgress);
	}

	@Transactional
	public AttemptView saveAnswers(UUID moduleId, UUID componentId, UUID studentId, JsonNode body) {
		Context ctx = requireMcqContext(moduleId, componentId);
		McqAttempt attempt = requireOwnedAttempt(componentId, studentId);
		requireOwnerOrAccess(ctx.module, attempt, studentId);
		if (!"IN_PROGRESS".equals(attempt.getStatus())) {
			throw conflict(ErrorCode.SUBMISSION_LOCKED, "Attempt is locked.");
		}
		if (isExpired(attempt, ctx.component)) {
			finalizeExpired(attempt.getId(), ctx.component.getId());
			throw conflict(ErrorCode.ATTEMPT_EXPIRED, "Attempt has expired.");
		}
		JsonNode answersNode = body.get("answers");
		if (answersNode == null || !answersNode.isArray()) {
			throw validationFailed();
		}
		Map<UUID, McqAttemptQuestion> questionsById = new HashMap<>();
		for (McqAttemptQuestion row : attemptQuestions.findByAttemptIdAndDeletedAtIsNullOrderByPositionAsc(attempt.getId())) {
			questionsById.put(row.getId(), row);
		}
		java.util.Set<UUID> kept = new java.util.HashSet<>();
		for (JsonNode entry : answersNode) {
			UUID attemptQuestionId = UUID.fromString(entry.get("attemptQuestionId").asText());
			McqAttemptQuestion question = questionsById.get(attemptQuestionId);
			if (question == null) {
				throw validationFailed();
			}
			UUID optionId = null;
			if (entry.has("attemptOptionId") && !entry.get("attemptOptionId").isNull()) {
				optionId = UUID.fromString(entry.get("attemptOptionId").asText());
				validateOption(question.getId(), optionId);
			}
			McqAnswer answer = answers.findByAttemptIdAndAttemptQuestionIdAndDeletedAtIsNull(attempt.getId(),
					attemptQuestionId)
				.orElseGet(() -> {
					McqAnswer created = new McqAnswer();
					created.setAttemptId(attempt.getId());
					created.setAttemptQuestionId(attemptQuestionId);
					return created;
				});
			answer.setAttemptOptionId(optionId);
			answers.save(answer);
			kept.add(attemptQuestionId);
		}
		for (McqAnswer existing : answers.findByAttemptIdAndDeletedAtIsNull(attempt.getId())) {
			if (!kept.contains(existing.getAttemptQuestionId())) {
				existing.setAttemptOptionId(null);
				answers.save(existing);
			}
		}
		return buildView(attempt, ctx.component, studentId, false, true);
	}

	@Transactional
	public AttemptView submit(UUID moduleId, UUID componentId, UUID studentId) {
		Context ctx = requireMcqContext(moduleId, componentId);
		McqAttempt attempt = requireOwnedAttempt(componentId, studentId);
		requireOwnerOrAccess(ctx.module, attempt, studentId);
		if (!"IN_PROGRESS".equals(attempt.getStatus())) {
			return buildView(attempt, ctx.component, studentId, false, false);
		}
		if (isExpired(attempt, ctx.component)) {
			finalizeIfExpired(attempt, ctx.component);
			return buildView(attempt, ctx.component, studentId, false, false);
		}
		scoreAndSubmit(attempt, Instant.now());
		return buildView(attempt, ctx.component, studentId, false, false);
	}

	@Transactional
	public List<AttemptView> listAdmin(UUID componentId) {
		PaperComponent component = components.findByIdAndDeletedAtIsNull(componentId).orElseThrow(this::notFound);
		if (!"MCQ".equals(component.getComponentType())) {
			throw validationFailed();
		}
		return attempts.findByComponentIdAndDeletedAtIsNullOrderByStartedAtAsc(componentId)
			.stream()
			.map(attempt -> {
				finalizeIfExpired(attempt, component);
				return buildView(attempt, component, attempt.getStudentId(), true, false);
			})
			.toList();
	}

	private void snapshotQuestion(UUID attemptId, ComponentQuestion link) {
		Question question = requireQuestion(link.getQuestionId());
		McqAttemptQuestion row = new McqAttemptQuestion();
		row.setAttemptId(attemptId);
		row.setQuestionId(question.getId());
		row.setPosition(link.getPosition());
		row.setStem(question.getStem());
		row.setExplanation(question.getExplanation());
		row.setMarks(question.getMarks());
		row = attemptQuestions.save(row);
		for (QuestionOption option : options.findByQuestionIdAndDeletedAtIsNullOrderByPositionAsc(question.getId())) {
			McqAttemptOption snap = new McqAttemptOption();
			snap.setAttemptQuestionId(row.getId());
			snap.setSourceOptionId(option.getId());
			snap.setText(option.getText());
			snap.setPosition(option.getPosition());
			snap.setCorrect(option.isCorrect());
			attemptOptions.save(snap);
		}
	}

	private void finalizeExpired(UUID attemptId, UUID componentId) {
		TransactionTemplate template = new TransactionTemplate(transactionManager);
		template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		template.executeWithoutResult(status -> {
			McqAttempt current = attempts.findById(attemptId).orElseThrow(this::notFound);
			PaperComponent component = components.findByIdAndDeletedAtIsNull(componentId).orElseThrow(this::notFound);
			if ("IN_PROGRESS".equals(current.getStatus()) && isExpired(current, component)) {
				scoreAndSubmit(current, deadlineAt(current, component));
			}
		});
	}

	private void finalizeIfExpired(McqAttempt attempt, PaperComponent component) {
		if (!"IN_PROGRESS".equals(attempt.getStatus())) {
			return;
		}
		if (isExpired(attempt, component)) {
			scoreAndSubmit(attempt, deadlineAt(attempt, component));
		}
	}

	private void scoreAndSubmit(McqAttempt attempt, Instant submittedAt) {
		BigDecimal score = BigDecimal.ZERO;
		for (McqAttemptQuestion question : attemptQuestions
			.findByAttemptIdAndDeletedAtIsNullOrderByPositionAsc(attempt.getId())) {
			McqAnswer answer = answers
				.findByAttemptIdAndAttemptQuestionIdAndDeletedAtIsNull(attempt.getId(), question.getId())
				.orElse(null);
			BigDecimal awarded = BigDecimal.ZERO;
			if (answer != null && answer.getAttemptOptionId() != null) {
				McqAttemptOption chosen = attemptOptions.findById(answer.getAttemptOptionId()).orElse(null);
				if (chosen != null && chosen.isCorrect()) {
					awarded = question.getMarks();
					score = score.add(awarded);
				}
			}
			if (answer == null) {
				answer = new McqAnswer();
				answer.setAttemptId(attempt.getId());
				answer.setAttemptQuestionId(question.getId());
			}
			answer.setMarksAwarded(awarded);
			answers.save(answer);
		}
		attempt.setScore(score);
		attempt.setSubmittedAt(submittedAt);
		attempt.setStatus("SUBMITTED");
	}

	private AttemptView buildView(McqAttempt attempt, PaperComponent component, UUID viewerId, boolean adminView,
			boolean hideReview) {
		boolean inProgress = "IN_PROGRESS".equals(attempt.getStatus());
		boolean showReview = adminView
				|| (!hideReview && !inProgress && (component.isImmediateResults() || component.isResultsPublished()));
		boolean mcqOnly = isMcqOnlyPaper(component.getPaperId());
		Instant serverNow = Instant.now();
		Instant deadline = deadlineAt(attempt, component);
		List<QuestionView> questionViews = new ArrayList<>();
		for (McqAttemptQuestion question : attemptQuestions
			.findByAttemptIdAndDeletedAtIsNullOrderByPositionAsc(attempt.getId())) {
			McqAnswer answer = answers
				.findByAttemptIdAndAttemptQuestionIdAndDeletedAtIsNull(attempt.getId(), question.getId())
				.orElse(null);
			List<OptionView> optionViews = new ArrayList<>();
			for (McqAttemptOption option : attemptOptions
				.findByAttemptQuestionIdAndDeletedAtIsNullOrderByPositionAsc(question.getId())) {
				Boolean correct = showReview ? option.isCorrect() : null;
				optionViews.add(new OptionView(option.getId(), option.getText(), option.getPosition(), correct));
			}
			BigDecimal marksAwarded = showReview && answer != null ? answer.getMarksAwarded() : null;
			String explanation = showReview ? question.getExplanation() : null;
			UUID chosen = answer == null ? null : answer.getAttemptOptionId();
			questionViews.add(new QuestionView(question.getId(), question.getQuestionId(), question.getStem(),
					explanation, question.getMarks(), marksAwarded, chosen, optionViews));
		}
		BigDecimal score = showReview ? attempt.getScore() : null;
		BigDecimal maxScore = showReview ? attempt.getMaxScore() : null;
		Boolean passed = null;
		if (showReview && mcqOnly && attempt.getPassPercent() != null && attempt.getScore() != null) {
			passed = passed(attempt.getScore(), attempt.getMaxScore(), attempt.getPassPercent());
		}
		return new AttemptView(attempt.getId(), attempt.getComponentId(), attempt.getStudentId(), attempt.getStatus(),
				attempt.getStartedAt(), attempt.getSubmittedAt(), attempt.getDurationSeconds(), serverNow, deadline,
				score, maxScore, passed, questionViews);
	}

	private static boolean passed(BigDecimal score, BigDecimal maxScore, int passPercent) {
		if (maxScore.compareTo(BigDecimal.ZERO) == 0) {
			return passPercent == 0;
		}
		BigDecimal left = score.multiply(BigDecimal.valueOf(100));
		BigDecimal right = maxScore.multiply(BigDecimal.valueOf(passPercent));
		return left.compareTo(right) >= 0;
	}

	private boolean isMcqOnlyPaper(UUID paperId) {
		for (PaperComponent row : components.findByPaperIdAndDeletedAtIsNull(paperId)) {
			if ("WRITTEN".equals(row.getComponentType())) {
				return false;
			}
		}
		return true;
	}

	private Instant deadlineAt(McqAttempt attempt, PaperComponent component) {
		Instant timerEnd = attempt.getStartedAt().plusSeconds(attempt.getDurationSeconds());
		Instant windowEnd = component.getEndsAt();
		if (windowEnd == null) {
			return timerEnd;
		}
		return timerEnd.isBefore(windowEnd) ? timerEnd : windowEnd;
	}

	private boolean isExpired(McqAttempt attempt, PaperComponent component) {
		return !Instant.now().isBefore(deadlineAt(attempt, component));
	}

	private void validateOption(UUID attemptQuestionId, UUID attemptOptionId) {
		boolean found = attemptOptions.findByAttemptQuestionIdAndDeletedAtIsNullOrderByPositionAsc(attemptQuestionId)
			.stream()
			.anyMatch(option -> option.getId().equals(attemptOptionId));
		if (!found) {
			throw validationFailed();
		}
	}

	private McqAttempt requireOwnedAttempt(UUID componentId, UUID studentId) {
		return attempts.findByComponentIdAndStudentIdAndDeletedAtIsNull(componentId, studentId)
			.orElseThrow(this::notFound);
	}

	private void requireOwnerOrAccess(ExamModule module, McqAttempt attempt, UUID studentId) {
		if (attempt.getStudentId().equals(studentId) && "IN_PROGRESS".equals(attempt.getStatus())) {
			return;
		}
		access.requireAccess(module, studentId);
		if (!attempt.getStudentId().equals(studentId)) {
			throw forbidden();
		}
	}

	private Context requireMcqContext(UUID moduleId, UUID componentId) {
		ExamModule module = modules.findByIdAndDeletedAtIsNull(moduleId).orElseThrow(this::notFound);
		PaperComponent component = components.findByIdAndDeletedAtIsNull(componentId).orElseThrow(this::notFound);
		if (!"MCQ".equals(component.getComponentType())) {
			throw validationFailed();
		}
		ExamNode paper = nodes.findByIdAndDeletedAtIsNull(component.getPaperId()).orElseThrow(this::notFound);
		if (!moduleId.equals(paper.getModuleId())) {
			throw notFound();
		}
		return new Context(module, component);
	}

	private Question requireQuestion(UUID questionId) {
		return questions.findByIdAndDeletedAtIsNull(questionId).orElseThrow(this::notFound);
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

	public record AttemptView(UUID id, UUID componentId, UUID studentId, String status, Instant startedAt,
			Instant submittedAt, int durationSeconds, Instant serverNow, Instant deadlineAt, BigDecimal score,
			BigDecimal maxScore, Boolean passed, List<QuestionView> questions) {
	}

	public record QuestionView(UUID id, UUID questionId, String stem, String explanation, BigDecimal marks,
			BigDecimal marksAwarded, UUID chosenOptionId, List<OptionView> options) {
	}

	public record OptionView(UUID id, String text, int position, Boolean correct) {
	}

}
