package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.web.PageResponse;
import com.coachingcenter.api.users.StudentProfileRepository;

import tools.jackson.databind.JsonNode;

@Service
public class ExamResultService {

	private final ExamModuleRepository modules;

	private final ExamNodeRepository nodes;

	private final PaperComponentRepository components;

	private final WrittenSubmissionRepository submissions;

	private final WrittenSubmissionQuestionRepository submissionQuestions;

	private final McqAttemptRepository attempts;

	private final McqAttemptQuestionRepository attemptQuestions;

	private final McqAttemptOptionRepository attemptOptions;

	private final McqAnswerRepository answers;

	private final ExamAccessService access;

	private final StudentProfileRepository studentProfiles;

	private final WrittenSubmissionService writtenSubmissions;

	public ExamResultService(ExamModuleRepository modules, ExamNodeRepository nodes,
			PaperComponentRepository components, WrittenSubmissionRepository submissions,
			WrittenSubmissionQuestionRepository submissionQuestions, McqAttemptRepository attempts,
			McqAttemptQuestionRepository attemptQuestions, McqAttemptOptionRepository attemptOptions,
			McqAnswerRepository answers, ExamAccessService access, StudentProfileRepository studentProfiles,
			WrittenSubmissionService writtenSubmissions) {
		this.modules = modules;
		this.nodes = nodes;
		this.components = components;
		this.submissions = submissions;
		this.submissionQuestions = submissionQuestions;
		this.attempts = attempts;
		this.attemptQuestions = attemptQuestions;
		this.attemptOptions = attemptOptions;
		this.answers = answers;
		this.access = access;
		this.studentProfiles = studentProfiles;
		this.writtenSubmissions = writtenSubmissions;
	}

	@Transactional
	public WrittenSubmissionService.SubmissionView evaluate(UUID componentId, UUID submissionId, UUID evaluatorId,
			JsonNode body) {
		PaperComponent component = requireWrittenComponent(componentId);
		if (component.isResultsPublished()) {
			throw conflict(ErrorCode.RESULTS_PUBLISHED, "Results are published.");
		}
		WrittenSubmission submission = submissions.findByIdAndDeletedAtIsNull(submissionId)
			.orElseThrow(this::notFound);
		if (!submission.getComponentId().equals(componentId)) {
			throw notFound();
		}
		if ("DRAFT".equals(submission.getStatus())) {
			throw conflict(ErrorCode.SUBMISSION_LOCKED, "Submission is locked.");
		}
		if (!"UNDER_REVIEW".equals(submission.getStatus()) && !"EVALUATED".equals(submission.getStatus())) {
			throw validationFailed();
		}
		List<WrittenSubmissionQuestion> questions = submissionQuestions
			.findBySubmissionIdAndDeletedAtIsNullOrderByPositionAsc(submission.getId());
		Map<UUID, WrittenSubmissionQuestion> byId = new HashMap<>();
		for (WrittenSubmissionQuestion question : questions) {
			byId.put(question.getId(), question);
		}
		JsonNode marksNode = body.get("marks");
		if (marksNode == null || !marksNode.isArray() || marksNode.size() != questions.size()) {
			throw marksOutOfRange();
		}
		Set<UUID> seen = new HashSet<>();
		for (JsonNode entry : marksNode) {
			UUID submissionQuestionId = UUID.fromString(entry.get("submissionQuestionId").asText());
			if (!seen.add(submissionQuestionId) || !byId.containsKey(submissionQuestionId)) {
				throw marksOutOfRange();
			}
			BigDecimal marks = entry.get("marks").decimalValue();
			WrittenSubmissionQuestion question = byId.get(submissionQuestionId);
			if (marks.compareTo(BigDecimal.ZERO) < 0 || marks.compareTo(question.getMaxMarks()) > 0) {
				throw marksOutOfRange();
			}
			question.setMarksAwarded(marks);
		}
		String feedback = body.has("feedback") && !body.get("feedback").isNull() ? body.get("feedback").asText() : "";
		submission.setFeedback(feedback);
		submission.setStatus("EVALUATED");
		submission.setEvaluatedAt(Instant.now());
		submission.setEvaluatedBy(evaluatorId);
		submissions.save(submission);
		return writtenSubmissions.buildAdminView(submission, component);
	}

	@Transactional
	public void publish(UUID componentId) {
		PaperComponent component = components.findByIdAndDeletedAtIsNull(componentId).orElseThrow(this::notFound);
		if ("MCQ".equals(component.getComponentType())) {
			if (!"ENDED".equals(ExamComponentLifecycle.derive(component))) {
				throw conflict(ErrorCode.COMPONENT_NOT_ENDED, "Component has not ended.");
			}
		}
		else if ("WRITTEN".equals(component.getComponentType())) {
			if (submissions.countByComponentIdAndStatusAndDeletedAtIsNull(componentId, "UNDER_REVIEW") > 0) {
				throw conflict(ErrorCode.RESULTS_NOT_READY, "Results are not ready.");
			}
		}
		else {
			throw validationFailed();
		}
		component.setResultsPublished(true);
		components.save(component);
	}

	@Transactional
	public void unpublish(UUID componentId) {
		PaperComponent component = components.findByIdAndDeletedAtIsNull(componentId).orElseThrow(this::notFound);
		component.setResultsPublished(false);
		components.save(component);
	}

	@Transactional(readOnly = true)
	public ComponentResultView studentComponentResult(UUID moduleId, UUID componentId, UUID studentId) {
		Context ctx = requireModuleComponent(moduleId, componentId);
		if ("MCQ".equals(ctx.component.getComponentType())) {
			McqAttempt attempt = attempts.findByComponentIdAndStudentIdAndDeletedAtIsNull(componentId, studentId)
				.orElseThrow(this::notFound);
			if (!visibleMcq(ctx.component, attempt)) {
				throw resultsNotPublished();
			}
			return mcqResult(attempt, ctx.component, true);
		}
		WrittenSubmission submission = submissions.findByComponentIdAndStudentIdAndDeletedAtIsNull(componentId, studentId)
			.orElseThrow(this::notFound);
		if ("DRAFT".equals(submission.getStatus())) {
			throw notFound();
		}
		if (!ctx.component.isResultsPublished()) {
			throw resultsNotPublished();
		}
		return writtenResult(submission, ctx.component, true);
	}

	@Transactional(readOnly = true)
	public ComponentResultView adminComponentResult(UUID componentId, UUID studentId) {
		PaperComponent component = components.findByIdAndDeletedAtIsNull(componentId).orElseThrow(this::notFound);
		if ("MCQ".equals(component.getComponentType())) {
			McqAttempt attempt = attempts.findByComponentIdAndStudentIdAndDeletedAtIsNull(componentId, studentId)
				.orElseThrow(this::notFound);
			return mcqResult(attempt, component, true);
		}
		WrittenSubmission submission = submissions.findByComponentIdAndStudentIdAndDeletedAtIsNull(componentId, studentId)
			.orElseThrow(this::notFound);
		return writtenResult(submission, component, true);
	}

	@Transactional(readOnly = true)
	public PaperResultView studentPaperResult(UUID moduleId, UUID paperId, UUID studentId) {
		ExamNode paper = requirePaperInModule(moduleId, paperId);
		List<PaperComponent> componentRows = components.findByPaperIdAndDeletedAtIsNull(paperId);
		List<ComponentResultView> visible = new ArrayList<>();
		BigDecimal combinedScore = null;
		BigDecimal combinedMax = BigDecimal.ZERO;
		boolean hasMcq = false;
		boolean hasWritten = false;
		boolean mcqVisible = false;
		boolean writtenVisible = false;
		BigDecimal mcqScore = null;
		BigDecimal writtenScore = null;
		Integer mcqPassPercent = null;
		Integer writtenPassPercent = null;
		boolean mcqSat = false;
		boolean writtenSat = false;
		for (PaperComponent component : componentRows) {
			if ("MCQ".equals(component.getComponentType())) {
				hasMcq = true;
				Optional<McqAttempt> attempt = attempts.findByComponentIdAndStudentIdAndDeletedAtIsNull(component.getId(),
						studentId);
				if (attempt.isEmpty()) {
					continue;
				}
				if ("SUBMITTED".equals(attempt.get().getStatus())) {
					mcqSat = true;
					mcqPassPercent = attempt.get().getPassPercent();
				}
				if (visibleMcq(component, attempt.get())) {
					mcqVisible = true;
					ComponentResultView result = mcqResult(attempt.get(), component, true);
					visible.add(result);
					mcqScore = result.score();
					combinedMax = combinedMax.add(result.maxScore());
				}
			}
			else if ("WRITTEN".equals(component.getComponentType())) {
				hasWritten = true;
				Optional<WrittenSubmission> submission = submissions
					.findByComponentIdAndStudentIdAndDeletedAtIsNull(component.getId(), studentId);
				if (submission.isEmpty()) {
					continue;
				}
				if (!"DRAFT".equals(submission.get().getStatus())) {
					writtenSat = true;
				}
				if (!"EVALUATED".equals(submission.get().getStatus())) {
					continue;
				}
				if (submission.get().getPassPercent() != null) {
					writtenPassPercent = submission.get().getPassPercent();
				}
				if (component.isResultsPublished()) {
					writtenVisible = true;
					ComponentResultView result = writtenResult(submission.get(), component, true);
					visible.add(result);
					writtenScore = result.score();
					combinedMax = combinedMax.add(result.maxScore());
				}
			}
		}
		if (visible.isEmpty()) {
			if (mcqSat || writtenSat) {
				throw resultsNotPublished();
			}
			throw notFound();
		}
		Integer passPercent = mcqPassPercent != null ? mcqPassPercent : writtenPassPercent;
		Boolean passed = null;
		Integer rank = null;
		Integer cohortSize = null;
		if (hasMcq && hasWritten) {
			if (mcqVisible && writtenVisible && mcqScore != null && writtenScore != null) {
				combinedScore = mcqScore.add(writtenScore);
				passed = passed(combinedScore, combinedMax, passPercent);
			}
		}
		else if (hasMcq && mcqVisible && mcqScore != null) {
			passed = passed(mcqScore, combinedMax, passPercent);
		}
		else if (hasWritten && writtenVisible) {
			passed = passed(writtenScore, combinedMax, passPercent);
		}
		if (allComponentsPublished(paperId) && paperScoreVisible(hasMcq, hasWritten, mcqVisible, writtenVisible)) {
			RankInfo rankInfo = rankForPaper(paperId, studentId);
			rank = rankInfo.rank();
			cohortSize = rankInfo.cohortSize();
		}
		return new PaperResultView(paperId, visible, combinedScore, combinedMax, passed, rank, cohortSize);
	}

	@Transactional(readOnly = true)
	public PageResponse<LeaderboardRow> leaderboard(UUID paperId, Pageable pageable, boolean studentView, UUID studentId,
			UUID moduleId) {
		ExamNode paper = nodes.findByIdAndDeletedAtIsNull(paperId).orElseThrow(this::notFound);
		if (studentView) {
			access.requireAccess(modules.findByIdAndDeletedAtIsNull(moduleId).orElseThrow(this::notFound), studentId);
			if (!allComponentsPublished(paperId)) {
				throw resultsNotPublished();
			}
		}
		List<LeaderboardRow> rows = buildLeaderboard(paperId);
		int start = (int) pageable.getOffset();
		int end = Math.min(start + pageable.getPageSize(), rows.size());
		List<LeaderboardRow> slice = start >= rows.size() ? List.of() : rows.subList(start, end);
		Page<LeaderboardRow> page = new PageImpl<>(slice, pageable, rows.size());
		return PageResponse.from(page);
	}

	@Transactional(readOnly = true)
	public List<QuestionStatView> questionStats(UUID componentId) {
		PaperComponent component = components.findByIdAndDeletedAtIsNull(componentId).orElseThrow(this::notFound);
		if ("MCQ".equals(component.getComponentType())) {
			return mcqQuestionStats(componentId);
		}
		if ("WRITTEN".equals(component.getComponentType())) {
			return writtenQuestionStats(componentId);
		}
		throw validationFailed();
	}

	private List<QuestionStatView> mcqQuestionStats(UUID componentId) {
		Map<UUID, McqStatBuilder> builders = new HashMap<>();
		for (McqAttempt attempt : attempts.findByComponentIdAndDeletedAtIsNullOrderByStartedAtAsc(componentId)) {
			if (!"SUBMITTED".equals(attempt.getStatus())) {
				continue;
			}
			for (McqAttemptQuestion question : attemptQuestions
				.findByAttemptIdAndDeletedAtIsNullOrderByPositionAsc(attempt.getId())) {
				McqStatBuilder builder = builders.computeIfAbsent(question.getQuestionId(),
						id -> new McqStatBuilder(question.getQuestionId()));
				builder.attempts++;
				McqAnswer answer = answers
					.findByAttemptIdAndAttemptQuestionIdAndDeletedAtIsNull(attempt.getId(), question.getId())
					.orElse(null);
				UUID chosen = answer == null ? null : answer.getAttemptOptionId();
				if (chosen != null) {
					McqAttemptOption option = attemptOptions.findById(chosen).orElse(null);
					if (option != null) {
						if (option.isCorrect()) {
							builder.correctCount++;
						}
						UUID optionKey = option.getSourceOptionId() != null ? option.getSourceOptionId() : option.getId();
						builder.optionCounts.merge(optionKey, 1, Integer::sum);
					}
				}
				for (McqAttemptOption option : attemptOptions
					.findByAttemptQuestionIdAndDeletedAtIsNullOrderByPositionAsc(question.getId())) {
					UUID optionKey = option.getSourceOptionId() != null ? option.getSourceOptionId() : option.getId();
					builder.options.putIfAbsent(optionKey,
							new OptionStatSeed(optionKey, option.getText(), option.getPosition()));
				}
			}
		}
		List<QuestionStatView> result = new ArrayList<>();
		for (McqStatBuilder builder : builders.values()) {
			int attemptsCount = builder.attempts;
			int correctPercent = attemptsCount == 0 ? 0
					: BigDecimal.valueOf(builder.correctCount * 100L)
						.divide(BigDecimal.valueOf(attemptsCount), 0, RoundingMode.HALF_UP)
						.intValue();
			List<OptionStatView> options = new ArrayList<>();
			for (Map.Entry<UUID, OptionStatSeed> entry : builder.options.entrySet()) {
				int selected = builder.optionCounts.getOrDefault(entry.getKey(), 0);
				int selectedPercent = attemptsCount == 0 ? 0
						: BigDecimal.valueOf(selected * 100L)
							.divide(BigDecimal.valueOf(attemptsCount), 0, RoundingMode.HALF_UP)
							.intValue();
				options.add(new OptionStatView(entry.getValue().sourceOptionId(), entry.getValue().text(),
						entry.getValue().position(), selected, selectedPercent));
			}
			options.sort(Comparator.comparingInt(OptionStatView::position));
			result.add(new QuestionStatView(builder.questionId, attemptsCount, builder.correctCount, correctPercent,
					null, null, null, options));
		}
		result.sort(Comparator.comparing(QuestionStatView::questionId));
		return result;
	}

	private List<QuestionStatView> writtenQuestionStats(UUID componentId) {
		Map<UUID, WrittenStatBuilder> builders = new HashMap<>();
		for (WrittenSubmission submission : submissions.findByComponentIdAndDeletedAtIsNull(componentId)) {
			if (!"EVALUATED".equals(submission.getStatus())) {
				continue;
			}
			for (WrittenSubmissionQuestion question : submissionQuestions
				.findBySubmissionIdAndDeletedAtIsNullOrderByPositionAsc(submission.getId())) {
				WrittenStatBuilder builder = builders.computeIfAbsent(question.getQuestionId(),
						id -> new WrittenStatBuilder(question.getQuestionId(), question.getMaxMarks()));
				builder.evaluatedCount++;
				builder.totalMarks = builder.totalMarks.add(question.getMarksAwarded());
			}
		}
		List<QuestionStatView> result = new ArrayList<>();
		for (WrittenStatBuilder builder : builders.values()) {
			BigDecimal averageMarks = builder.evaluatedCount == 0 ? BigDecimal.ZERO
					: builder.totalMarks.divide(BigDecimal.valueOf(builder.evaluatedCount), 2, RoundingMode.HALF_UP);
			int averagePercent = builder.evaluatedCount == 0 || builder.maxMarks.compareTo(BigDecimal.ZERO) == 0 ? 0
					: builder.totalMarks.multiply(BigDecimal.valueOf(100))
						.divide(builder.maxMarks.multiply(BigDecimal.valueOf(builder.evaluatedCount)), 0,
								RoundingMode.HALF_UP)
						.intValue();
			result.add(new QuestionStatView(builder.questionId, null, null, null, builder.evaluatedCount, averageMarks,
					averagePercent, List.of()));
		}
		result.sort(Comparator.comparing(QuestionStatView::questionId));
		return result;
	}

	private List<LeaderboardRow> buildLeaderboard(UUID paperId) {
		PaperScoreBoard board = computePaperScores(paperId);
		List<Map.Entry<UUID, BigDecimal>> ordered = board.scores.entrySet().stream()
			.sorted((a, b) -> b.getValue().compareTo(a.getValue()))
			.toList();
		List<LeaderboardRow> rows = new ArrayList<>();
		int currentRank = 0;
		int position = 0;
		BigDecimal last = null;
		for (Map.Entry<UUID, BigDecimal> entry : ordered) {
			position++;
			if (last == null || entry.getValue().compareTo(last) != 0) {
				currentRank = position;
				last = entry.getValue();
			}
			String name = studentProfiles.findById(entry.getKey()).map(profile -> profile.getFullName()).orElse("Student");
			Boolean passedFlag = passed(entry.getValue(), board.maxScore, board.passPercent);
			rows.add(new LeaderboardRow(name, currentRank, entry.getValue(), board.maxScore, passedFlag));
		}
		return rows;
	}

	private RankInfo rankForPaper(UUID paperId, UUID studentId) {
		PaperScoreBoard board = computePaperScores(paperId);
		List<Map.Entry<UUID, BigDecimal>> ordered = board.scores.entrySet().stream()
			.sorted((a, b) -> b.getValue().compareTo(a.getValue()))
			.toList();
		int currentRank = 0;
		int position = 0;
		BigDecimal last = null;
		Integer studentRank = null;
		for (Map.Entry<UUID, BigDecimal> entry : ordered) {
			position++;
			if (last == null || entry.getValue().compareTo(last) != 0) {
				currentRank = position;
				last = entry.getValue();
			}
			if (entry.getKey().equals(studentId)) {
				studentRank = currentRank;
			}
		}
		return new RankInfo(studentRank, ordered.size());
	}

	private PaperScoreBoard computePaperScores(UUID paperId) {
		List<PaperComponent> componentRows = components.findByPaperIdAndDeletedAtIsNull(paperId);
		boolean hasMcq = componentRows.stream().anyMatch(c -> "MCQ".equals(c.getComponentType()));
		boolean hasWritten = componentRows.stream().anyMatch(c -> "WRITTEN".equals(c.getComponentType()));
		Map<UUID, BigDecimal> mcqScores = new HashMap<>();
		Map<UUID, BigDecimal> writtenScores = new HashMap<>();
		BigDecimal paperMax = BigDecimal.ZERO;
		Integer passPercent = null;
		for (PaperComponent component : componentRows) {
			if ("MCQ".equals(component.getComponentType())) {
				for (McqAttempt attempt : attempts.findByComponentIdAndDeletedAtIsNullOrderByStartedAtAsc(component.getId())) {
					if (!"SUBMITTED".equals(attempt.getStatus()) || attempt.getScore() == null) {
						continue;
					}
					passPercent = attempt.getPassPercent();
					mcqScores.put(attempt.getStudentId(), attempt.getScore());
					paperMax = paperMax.add(attempt.getMaxScore());
				}
			}
			else if ("WRITTEN".equals(component.getComponentType())) {
				for (WrittenSubmission submission : submissions.findByComponentIdAndDeletedAtIsNull(component.getId())) {
					if (!"EVALUATED".equals(submission.getStatus())) {
						continue;
					}
					passPercent = submission.getPassPercent();
					writtenScores.put(submission.getStudentId(), sumWrittenMarks(submission.getId()));
					paperMax = paperMax.add(sumWrittenMax(submission.getId()));
				}
			}
		}
		Map<UUID, BigDecimal> scores = new HashMap<>();
		if (hasMcq && hasWritten) {
			for (Map.Entry<UUID, BigDecimal> entry : mcqScores.entrySet()) {
				BigDecimal written = writtenScores.get(entry.getKey());
				if (written != null) {
					scores.put(entry.getKey(), entry.getValue().add(written));
				}
				else {
					scores.put(entry.getKey(), entry.getValue());
				}
			}
			for (Map.Entry<UUID, BigDecimal> entry : writtenScores.entrySet()) {
				if (!mcqScores.containsKey(entry.getKey())) {
					scores.put(entry.getKey(), entry.getValue());
				}
			}
		}
		else if (hasMcq) {
			scores.putAll(mcqScores);
		}
		else {
			scores.putAll(writtenScores);
		}
		return new PaperScoreBoard(scores, paperMax, passPercent);
	}

	private ComponentResultView mcqResult(McqAttempt attempt, PaperComponent component, boolean showReview) {
		boolean visible = showReview;
		List<McqResultQuestionView> questions = new ArrayList<>();
		for (McqAttemptQuestion question : attemptQuestions
			.findByAttemptIdAndDeletedAtIsNullOrderByPositionAsc(attempt.getId())) {
			McqAnswer answer = answers
				.findByAttemptIdAndAttemptQuestionIdAndDeletedAtIsNull(attempt.getId(), question.getId())
				.orElse(null);
			List<McqResultOptionView> options = new ArrayList<>();
			for (McqAttemptOption option : attemptOptions
				.findByAttemptQuestionIdAndDeletedAtIsNullOrderByPositionAsc(question.getId())) {
				Boolean correct = visible ? option.isCorrect() : null;
				options.add(new McqResultOptionView(option.getId(), option.getText(), option.getPosition(), correct));
			}
			BigDecimal marksAwarded = visible && answer != null ? answer.getMarksAwarded() : null;
			String explanation = visible ? question.getExplanation() : null;
			UUID chosen = answer == null ? null : answer.getAttemptOptionId();
			questions.add(new McqResultQuestionView(question.getId(), question.getStem(), explanation,
					question.getMarks(), marksAwarded, chosen, options));
		}
		BigDecimal score = visible ? attempt.getScore() : null;
		BigDecimal maxScore = visible ? attempt.getMaxScore() : null;
		return new ComponentResultView(component.getId(), "MCQ", score, maxScore, null, questions, List.of());
	}

	private ComponentResultView writtenResult(WrittenSubmission submission, PaperComponent component,
			boolean showReview) {
		boolean visible = showReview;
		List<WrittenResultQuestionView> questions = new ArrayList<>();
		BigDecimal score = BigDecimal.ZERO;
		BigDecimal maxScore = BigDecimal.ZERO;
		for (WrittenSubmissionQuestion question : submissionQuestions
			.findBySubmissionIdAndDeletedAtIsNullOrderByPositionAsc(submission.getId())) {
			maxScore = maxScore.add(question.getMaxMarks());
			if (visible && question.getMarksAwarded() != null) {
				score = score.add(question.getMarksAwarded());
			}
			String explanation = visible ? question.getExplanation() : null;
			BigDecimal marksAwarded = visible ? question.getMarksAwarded() : null;
			questions.add(new WrittenResultQuestionView(question.getId(), question.getStem(), explanation,
					question.getMaxMarks(), marksAwarded));
		}
		String feedback = visible ? submission.getFeedback() : null;
		BigDecimal visibleScore = visible ? score : null;
		return new ComponentResultView(component.getId(), "WRITTEN", visibleScore, maxScore, feedback, List.of(),
				questions);
	}

	private BigDecimal sumWrittenMarks(UUID submissionId) {
		BigDecimal total = BigDecimal.ZERO;
		for (WrittenSubmissionQuestion question : submissionQuestions
			.findBySubmissionIdAndDeletedAtIsNullOrderByPositionAsc(submissionId)) {
			if (question.getMarksAwarded() != null) {
				total = total.add(question.getMarksAwarded());
			}
		}
		return total;
	}

	private BigDecimal sumWrittenMax(UUID submissionId) {
		BigDecimal total = BigDecimal.ZERO;
		for (WrittenSubmissionQuestion question : submissionQuestions
			.findBySubmissionIdAndDeletedAtIsNullOrderByPositionAsc(submissionId)) {
			total = total.add(question.getMaxMarks());
		}
		return total;
	}

	private boolean visibleMcq(PaperComponent component, McqAttempt attempt) {
		return component.isImmediateResults() || component.isResultsPublished();
	}

	private boolean allComponentsPublished(UUID paperId) {
		for (PaperComponent component : components.findByPaperIdAndDeletedAtIsNull(paperId)) {
			if (!component.isResultsPublished()) {
				return false;
			}
		}
		return true;
	}

	private static boolean paperScoreVisible(boolean hasMcq, boolean hasWritten, boolean mcqVisible,
			boolean writtenVisible) {
		if (hasMcq && hasWritten) {
			return mcqVisible && writtenVisible;
		}
		return mcqVisible || writtenVisible;
	}

	private static Boolean passed(BigDecimal score, BigDecimal maxScore, Integer passPercent) {
		if (passPercent == null || score == null || maxScore == null) {
			return null;
		}
		if (maxScore.compareTo(BigDecimal.ZERO) == 0) {
			return passPercent == 0;
		}
		return score.multiply(BigDecimal.valueOf(100)).compareTo(maxScore.multiply(BigDecimal.valueOf(passPercent))) >= 0;
	}

	private PaperComponent requireWrittenComponent(UUID componentId) {
		PaperComponent component = components.findByIdAndDeletedAtIsNull(componentId).orElseThrow(this::notFound);
		if (!"WRITTEN".equals(component.getComponentType())) {
			throw validationFailed();
		}
		return component;
	}

	private ExamNode requirePaperInModule(UUID moduleId, UUID paperId) {
		ExamNode paper = nodes.findByIdAndDeletedAtIsNull(paperId).orElseThrow(this::notFound);
		if (!moduleId.equals(paper.getModuleId())) {
			throw notFound();
		}
		return paper;
	}

	private Context requireModuleComponent(UUID moduleId, UUID componentId) {
		ExamModule module = modules.findByIdAndDeletedAtIsNull(moduleId).orElseThrow(this::notFound);
		PaperComponent component = components.findByIdAndDeletedAtIsNull(componentId).orElseThrow(this::notFound);
		ExamNode paper = nodes.findByIdAndDeletedAtIsNull(component.getPaperId()).orElseThrow(this::notFound);
		if (!moduleId.equals(paper.getModuleId())) {
			throw notFound();
		}
		return new Context(module, component, paper);
	}

	private record Context(ExamModule module, PaperComponent component, ExamNode paper) {
	}

	private record RankInfo(Integer rank, int cohortSize) {
	}

	private record PaperScoreBoard(Map<UUID, BigDecimal> scores, BigDecimal maxScore, Integer passPercent) {
	}

	private static class McqStatBuilder {

		private final UUID questionId;

		private int attempts;

		private int correctCount;

		private final Map<UUID, Integer> optionCounts = new HashMap<>();

		private final Map<UUID, OptionStatSeed> options = new HashMap<>();

		private McqStatBuilder(UUID questionId) {
			this.questionId = questionId;
		}

	}

	private record OptionStatSeed(UUID sourceOptionId, String text, int position) {
	}

	private static class WrittenStatBuilder {

		private final UUID questionId;

		private final BigDecimal maxMarks;

		private int evaluatedCount;

		private BigDecimal totalMarks = BigDecimal.ZERO;

		private WrittenStatBuilder(UUID questionId, BigDecimal maxMarks) {
			this.questionId = questionId;
			this.maxMarks = maxMarks;
		}

	}

	private ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	private static ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
	}

	private static ApiException conflict(ErrorCode code, String message) {
		return new ApiException(code, HttpStatus.CONFLICT, message);
	}

	private static ApiException marksOutOfRange() {
		return new ApiException(ErrorCode.MARKS_OUT_OF_RANGE, HttpStatus.BAD_REQUEST, "Marks are out of range.");
	}

	private static ApiException resultsNotPublished() {
		return new ApiException(ErrorCode.RESULTS_NOT_PUBLISHED, HttpStatus.FORBIDDEN, "Results are not published.");
	}

	public record ComponentResultView(UUID componentId, String componentType, BigDecimal score, BigDecimal maxScore,
			String feedback, List<McqResultQuestionView> mcqQuestions, List<WrittenResultQuestionView> writtenQuestions) {
	}

	public record McqResultQuestionView(UUID id, String stem, String explanation, BigDecimal maxMarks,
			BigDecimal marksAwarded, UUID chosenOptionId, List<McqResultOptionView> options) {
	}

	public record McqResultOptionView(UUID id, String text, int position, Boolean correct) {
	}

	public record WrittenResultQuestionView(UUID id, String stem, String explanation, BigDecimal maxMarks,
			BigDecimal marksAwarded) {
	}

	public record PaperResultView(UUID paperId, List<ComponentResultView> components, BigDecimal combinedScore,
			BigDecimal maxScore, Boolean passed, Integer rank, Integer cohortSize) {
	}

	public record LeaderboardRow(String studentName, Integer rank, BigDecimal score, BigDecimal maxScore,
			Boolean passed) {
	}

	public record QuestionStatView(UUID questionId, Integer attempts, Integer correctCount, Integer correctPercent,
			Integer evaluatedCount, BigDecimal averageMarks, Integer averagePercent, List<OptionStatView> options) {
	}

	public record OptionStatView(UUID optionId, String text, int position, int selectedCount, int selectedPercent) {
	}

}
