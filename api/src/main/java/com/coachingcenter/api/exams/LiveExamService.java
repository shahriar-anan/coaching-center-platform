package com.coachingcenter.api.exams;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.files.LocalFileStorage;

import tools.jackson.databind.JsonNode;

@Service
public class LiveExamService {

	static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

	private static final Duration GAP = Duration.ofMinutes(5);

	private static final DateTimeFormatter DHAKA_TIME = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

	private final LiveExamRepository exams;

	private final LiveExamQuestionRepository questions;

	private final LiveExamOptionRepository options;

	private final LiveExamModuleRepository modules;

	private final LiveExamRosterRepository roster;

	private final LiveExamCatalogVisibility visibility;

	private final LocalFileStorage files;

	private final Clock clock;

	public LiveExamService(LiveExamRepository exams, LiveExamQuestionRepository questions,
			LiveExamOptionRepository options, LiveExamModuleRepository modules, LiveExamRosterRepository roster,
			LiveExamCatalogVisibility visibility, LocalFileStorage files, Clock clock) {
		this.exams = exams;
		this.questions = questions;
		this.options = options;
		this.modules = modules;
		this.roster = roster;
		this.visibility = visibility;
		this.files = files;
		this.clock = clock;
	}

	@Transactional
	public ExamView create(UUID moduleId, JsonNode body) {
		LiveExamModule module = requireModule(moduleId);
		visibility.requireOpen(module.getCategoryId());
		String examType = requiredText(body, "examType");
		if (!LiveExam.MCQ.equals(examType) && !LiveExam.WRITTEN.equals(examType)) {
			throw validationFailed();
		}
		rejectMaximumOnMcq(examType, body);
		LiveExam exam = new LiveExam();
		exam.setModuleId(module.getId());
		exam.setExamType(examType);
		exam.setTitleEn(requiredText(body, "titleEn"));
		exam.setTitleBn(requiredText(body, "titleBn"));
		exam.setDescriptionEn(requiredText(body, "descriptionEn"));
		exam.setDescriptionBn(requiredText(body, "descriptionBn"));
		exam.setRulesEn(requiredText(body, "rulesEn"));
		exam.setRulesBn(requiredText(body, "rulesBn"));
		exam.setLiveAt(readInstant(body, "liveAt"));
		exam.setDurationMinutes(readDuration(body));
		exam.setPassPercent(readPassPercent(body, false));
		if (LiveExam.WRITTEN.equals(examType)) {
			exam.setWrittenMaximum(readMaximum(body, false));
		}
		exam.setStatus(LiveExam.DRAFT);
		exam.setFinallySaved(false);
		return view(exams.save(exam), true);
	}

	@Transactional
	public ExamView update(UUID id, JsonNode body) {
		LiveExam exam = require(id);
		boolean schedule = body.has("liveAt") || body.has("durationMinutes");
		boolean scoring = body.has("passPercent")
				|| (LiveExam.WRITTEN.equals(exam.getExamType()) && body.has("maximum"));
		boolean locked = !clock.instant().isBefore(exam.getLiveAt());
		if (locked && (schedule || scoring)) {
			throw lockedContent();
		}
		rejectMaximumOnMcq(exam.getExamType(), body);
		if (body.has("titleEn") && !body.get("titleEn").isNull()) {
			exam.setTitleEn(requiredText(body, "titleEn"));
		}
		if (body.has("titleBn") && !body.get("titleBn").isNull()) {
			exam.setTitleBn(requiredText(body, "titleBn"));
		}
		if (body.has("descriptionEn") && !body.get("descriptionEn").isNull()) {
			exam.setDescriptionEn(requiredText(body, "descriptionEn"));
		}
		if (body.has("descriptionBn") && !body.get("descriptionBn").isNull()) {
			exam.setDescriptionBn(requiredText(body, "descriptionBn"));
		}
		if (body.has("rulesEn") && !body.get("rulesEn").isNull()) {
			exam.setRulesEn(requiredText(body, "rulesEn"));
		}
		if (body.has("rulesBn") && !body.get("rulesBn").isNull()) {
			exam.setRulesBn(requiredText(body, "rulesBn"));
		}
		if (body.has("liveAt") && !body.get("liveAt").isNull()) {
			exam.setLiveAt(readInstant(body, "liveAt"));
		}
		if (body.has("durationMinutes") && !body.get("durationMinutes").isNull()) {
			exam.setDurationMinutes(readDuration(body));
		}
		if (body.has("passPercent")) {
			exam.setPassPercent(readPassPercent(body, true));
		}
		if (LiveExam.WRITTEN.equals(exam.getExamType()) && body.has("maximum")) {
			exam.setWrittenMaximum(readMaximum(body, true));
		}
		if (!locked) {
			exam.setStatus(LiveExam.DRAFT);
			exam.setFinallySaved(false);
		}
		return view(exam, true);
	}

	@Transactional
	public QuestionView addQuestion(UUID examId, JsonNode body) {
		LiveExam exam = requireMcq(examId);
		markDraft(exam);
		LiveExamQuestion question = new LiveExamQuestion();
		question.setExamId(exam.getId());
		applyQuestion(question, body, nextQuestionPosition(exam.getId()));
		questions.save(question);
		List<LiveExamOption> saved = replaceOptions(question.getId(), body.get("options"));
		return questionView(question, saved);
	}

	@Transactional
	public QuestionView updateQuestion(UUID examId, UUID questionId, JsonNode body) {
		LiveExam exam = requireMcq(examId);
		markDraft(exam);
		LiveExamQuestion question = questions.findByIdAndExamIdAndDeletedAtIsNull(questionId, examId)
			.orElseThrow(this::notFound);
		int position = body.has("position") && !body.get("position").isNull() ? body.get("position").asInt()
				: question.getPosition();
		applyQuestion(question, body, position);
		return questionView(question, replaceOptions(question.getId(), body.get("options")));
	}

	@Transactional
	public ExamView uploadQuestionPdf(UUID examId, MultipartFile file) {
		LiveExam exam = require(examId);
		if (!LiveExam.WRITTEN.equals(exam.getExamType())) {
			throw new ApiException(ErrorCode.EXAM_TYPE_MISMATCH, HttpStatus.CONFLICT, "Only a written exam has a question PDF.");
		}
		markDraft(exam);
		if (file == null || file.isEmpty()) {
			throw validationFailed();
		}
		byte[] bytes;
		try {
			bytes = file.getBytes();
		}
		catch (IOException exception) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
		}
		if (bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D' || bytes[3] != 'F') {
			throw validationFailed();
		}
		String previous = exam.getQuestionStoredFileName();
		LocalFileStorage.StoredFile stored = files.save(file.getOriginalFilename(), "application/pdf",
				new java.io.ByteArrayInputStream(bytes), bytes.length);
		exam.setQuestionStoredFileName(stored.storedFileName());
		exam.setQuestionOriginalFileName(stored.originalFileName());
		exam.setQuestionByteSize(stored.byteSize());
		exam.setQuestionContentType("application/pdf");
		files.commitReplacement(stored.storedFileName(), previous);
		return view(exam, true);
	}

	@Transactional
	public ExamView finalSave(UUID id) {
		LiveExam exam = require(id);
		exam.setFinallySaved(true);
		return view(exam, true);
	}

	@Transactional
	public ExamView publish(UUID id) {
		LiveExam exam = require(id);
		if (!exam.isFinallySaved()) {
			throw new ApiException(ErrorCode.FINAL_SAVE_REQUIRED, HttpStatus.CONFLICT, "Publish requires a final save.");
		}
		if (exam.getPassPercent() == null) {
			throw new ApiException(ErrorCode.PASS_PERCENT_REQUIRED, HttpStatus.CONFLICT,
					"Publish requires a pass percent from 0 through 100.");
		}
		if (LiveExam.MCQ.equals(exam.getExamType())) {
			if (questions.findByExamIdAndDeletedAtIsNullOrderByPositionAscIdAsc(exam.getId()).isEmpty()) {
				throw new ApiException(ErrorCode.QUESTIONS_REQUIRED, HttpStatus.CONFLICT,
						"An MCQ exam needs at least one question.");
			}
		}
		else if (exam.getWrittenMaximum() == null || exam.getQuestionStoredFileName() == null) {
			throw new ApiException(ErrorCode.WRITTEN_PAPER_REQUIRED, HttpStatus.CONFLICT,
					"A written exam needs a maximum and a question PDF.");
		}
		if (!clock.instant().isBefore(end(exam))) {
			throw new ApiException(ErrorCode.EXAM_WINDOW_ENDED, HttpStatus.CONFLICT, "The exam window has already ended.");
		}
		visibility.requireOpen(requireModule(exam.getModuleId()).getCategoryId());
		rejectWindowConflict(exam);
		exam.setStatus(LiveExam.PUBLISHED);
		return view(exam, true);
	}

	@Transactional(readOnly = true)
	public Page<ExamView> list(UUID moduleId, Pageable pageable, boolean student, UUID studentId) {
		LiveExamModule module = requireModule(moduleId);
		Pageable ordered = ordered(pageable);
		if (student && (!LiveExamModule.PUBLISHED.equals(module.getStatus())
				|| visibility.branchHidden(module.getCategoryId())
				|| !roster.existsByModuleIdAndStudentId(moduleId, studentId))) {
			return Page.empty(ordered);
		}
		Page<LiveExam> page = student
				? exams.findByModuleIdAndStatusAndDeletedAtIsNull(moduleId, LiveExam.PUBLISHED, ordered)
				: exams.findByModuleIdAndDeletedAtIsNull(moduleId, ordered);
		return page.map(exam -> view(exam, !student));
	}

	@Transactional(readOnly = true)
	public ExamView get(UUID id, boolean staff, UUID studentId) {
		LiveExam exam = require(id);
		if (!staff && (!LiveExam.PUBLISHED.equals(exam.getStatus()) || !visibleModule(exam)
				|| !roster.existsByModuleIdAndStudentId(exam.getModuleId(), studentId))) {
			throw notFound();
		}
		return view(exam, staff);
	}

	private void rejectWindowConflict(LiveExam exam) {
		Instant start = exam.getLiveAt();
		Instant end = end(exam);
		for (LiveExam other : exams.findByModuleIdAndStatusAndDeletedAtIsNull(exam.getModuleId(), LiveExam.PUBLISHED)) {
			if (other.getId().equals(exam.getId())) {
				continue;
			}
			Instant otherStart = other.getLiveAt();
			Instant otherEnd = end(other);
			boolean thisFirst = !start.isAfter(otherStart);
			Instant earlierEnd = thisFirst ? end : otherEnd;
			Instant laterStart = thisFirst ? otherStart : start;
			if (laterStart.isBefore(earlierEnd.plus(GAP))) {
				throw new ApiException(ErrorCode.EXAM_WINDOW_CONFLICT, HttpStatus.CONFLICT,
						"This window conflicts with " + other.getTitleEn() + " (" + format(other.getLiveAt()) + " to "
								+ format(otherEnd) + ").");
			}
		}
	}

	private void markDraft(LiveExam exam) {
		if (!clock.instant().isBefore(exam.getLiveAt())) {
			throw lockedContent();
		}
		exam.setStatus(LiveExam.DRAFT);
		exam.setFinallySaved(false);
	}

	private void applyQuestion(LiveExamQuestion question, JsonNode body, int position) {
		question.setStem(requiredText(body, "stem"));
		question.setMarks(readMarks(body));
		if (body.has("explanation") && !body.get("explanation").isNull() && !body.get("explanation").asText().isBlank()) {
			question.setExplanation(body.get("explanation").asText().trim());
		}
		else if (body.has("explanation")) {
			question.setExplanation(null);
		}
		question.setPosition(position);
	}

	private List<LiveExamOption> replaceOptions(UUID questionId, JsonNode node) {
		if (node == null || !node.isArray() || node.size() < 2) {
			throw validationFailed();
		}
		int correct = 0;
		for (JsonNode option : node) {
			if (option.has("correct") && option.get("correct").asBoolean()) {
				correct++;
			}
		}
		if (correct != 1) {
			throw validationFailed();
		}
		options.deleteAll(options.findByQuestionIdAndDeletedAtIsNullOrderByPositionAscIdAsc(questionId));
		options.flush();
		List<LiveExamOption> saved = new ArrayList<>();
		int position = 0;
		for (JsonNode option : node) {
			String text = option.has("text") ? option.get("text").asText().trim() : "";
			if (text.isBlank()) {
				throw validationFailed();
			}
			LiveExamOption row = new LiveExamOption();
			row.setQuestionId(questionId);
			row.setOptionText(text);
			row.setCorrect(option.has("correct") && option.get("correct").asBoolean());
			row.setPosition(position++);
			saved.add(options.save(row));
		}
		return saved;
	}

	private int nextQuestionPosition(UUID examId) {
		int max = -1;
		for (LiveExamQuestion question : questions.findByExamIdAndDeletedAtIsNullOrderByPositionAscIdAsc(examId)) {
			max = Math.max(max, question.getPosition());
		}
		return max + 1;
	}

	private ExamView view(LiveExam exam, boolean staff) {
		List<QuestionView> questionViews = null;
		if (staff && LiveExam.MCQ.equals(exam.getExamType())) {
			questionViews = loadQuestions(exam.getId());
		}
		Boolean questionPdf = null;
		if (staff) {
			questionPdf = exam.getQuestionStoredFileName() != null;
		}
		return new ExamView(exam.getId(), exam.getModuleId(), exam.getExamType(), exam.getTitleEn(), exam.getTitleBn(),
				exam.getDescriptionEn(), exam.getDescriptionBn(), exam.getRulesEn(), exam.getRulesBn(),
				format(exam.getLiveAt()), format(end(exam)), exam.getDurationMinutes(), exam.getPassPercent(),
				maximum(exam), exam.getStatus(), exam.isFinallySaved(), questionPdf, questionViews);
	}

	private List<QuestionView> loadQuestions(UUID examId) {
		List<LiveExamQuestion> rows = questions.findByExamIdAndDeletedAtIsNullOrderByPositionAscIdAsc(examId);
		List<UUID> ids = rows.stream().map(LiveExamQuestion::getId).toList();
		Map<UUID, List<LiveExamOption>> byQuestion = new HashMap<>();
		if (!ids.isEmpty()) {
			for (LiveExamOption option : options.findByQuestionIdInAndDeletedAtIsNullOrderByPositionAscIdAsc(ids)) {
				byQuestion.computeIfAbsent(option.getQuestionId(), ignored -> new ArrayList<>()).add(option);
			}
		}
		List<QuestionView> views = new ArrayList<>();
		for (LiveExamQuestion question : rows) {
			views.add(questionView(question, byQuestion.getOrDefault(question.getId(), List.of())));
		}
		return views;
	}

	private static QuestionView questionView(LiveExamQuestion question, List<LiveExamOption> rows) {
		List<OptionView> optionViews = new ArrayList<>();
		for (LiveExamOption option : rows) {
			optionViews.add(new OptionView(option.getId(), option.getOptionText(), option.isCorrect(), option.getPosition()));
		}
		return new QuestionView(question.getId(), question.getStem(), question.getMarks(), question.getExplanation(),
				question.getPosition(), optionViews);
	}

	private int maximum(LiveExam exam) {
		if (LiveExam.WRITTEN.equals(exam.getExamType())) {
			return exam.getWrittenMaximum() == null ? 0 : exam.getWrittenMaximum();
		}
		int sum = 0;
		for (LiveExamQuestion question : questions.findByExamIdAndDeletedAtIsNullOrderByPositionAscIdAsc(exam.getId())) {
			sum += question.getMarks();
		}
		return sum;
	}

	private static Instant end(LiveExam exam) {
		return exam.getLiveAt().plus(Duration.ofMinutes(exam.getDurationMinutes()));
	}

	private static String format(Instant instant) {
		return DHAKA_TIME.format(instant.atZone(DHAKA));
	}

	private boolean visibleModule(LiveExam exam) {
		return modules.findByIdAndDeletedAtIsNull(exam.getModuleId())
			.filter(module -> LiveExamModule.PUBLISHED.equals(module.getStatus()))
			.filter(module -> !visibility.branchHidden(module.getCategoryId()))
			.isPresent();
	}

	private LiveExam requireMcq(UUID examId) {
		LiveExam exam = require(examId);
		if (!LiveExam.MCQ.equals(exam.getExamType())) {
			throw new ApiException(ErrorCode.EXAM_TYPE_MISMATCH, HttpStatus.CONFLICT, "Only an MCQ exam has questions.");
		}
		return exam;
	}

	private LiveExam require(UUID id) {
		return exams.findByIdAndDeletedAtIsNull(id).orElseThrow(this::notFound);
	}

	private LiveExamModule requireModule(UUID id) {
		LiveExamModule module = modules.findByIdAndDeletedAtIsNull(id).orElseThrow(this::notFound);
		if (LiveExamModule.ARCHIVED.equals(module.getStatus())) {
			throw new ApiException(ErrorCode.MODULE_ARCHIVED, HttpStatus.CONFLICT, "An archived module cannot be changed.");
		}
		return module;
	}

	private static Pageable ordered(Pageable pageable) {
		if (pageable.getSort().isSorted()) {
			return pageable;
		}
		return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Order.asc("liveAt")));
	}

	private static void rejectMaximumOnMcq(String examType, JsonNode body) {
		if (LiveExam.MCQ.equals(examType) && body.has("maximum") && !body.get("maximum").isNull()) {
			throw validationFailed();
		}
	}

	private static Instant readInstant(JsonNode body, String field) {
		JsonNode node = body.get(field);
		if (node == null || node.isNull() || node.asText().isBlank()) {
			throw validationFailed();
		}
		try {
			return OffsetDateTime.parse(node.asText().trim()).toInstant();
		}
		catch (DateTimeParseException exception) {
			try {
				return Instant.parse(node.asText().trim());
			}
			catch (DateTimeParseException ignored) {
				throw validationFailed();
			}
		}
	}

	private static int readDuration(JsonNode body) {
		JsonNode node = body.get("durationMinutes");
		if (node == null || !node.isIntegralNumber() || node.intValue() <= 0) {
			throw validationFailed();
		}
		return node.intValue();
	}

	private static Integer readPassPercent(JsonNode body, boolean allowNull) {
		if (!body.has("passPercent") || body.get("passPercent").isNull()) {
			if (allowNull) {
				return null;
			}
			return null;
		}
		JsonNode node = body.get("passPercent");
		if (!node.isIntegralNumber()) {
			throw validationFailed();
		}
		int value = node.intValue();
		if (value < 0 || value > 100) {
			throw validationFailed();
		}
		return value;
	}

	private static Integer readMaximum(JsonNode body, boolean allowNull) {
		if (!body.has("maximum") || body.get("maximum").isNull()) {
			return null;
		}
		JsonNode node = body.get("maximum");
		if (!node.isIntegralNumber() || node.intValue() <= 0) {
			throw validationFailed();
		}
		return node.intValue();
	}

	private static int readMarks(JsonNode body) {
		JsonNode node = body.get("marks");
		if (node == null || !node.isIntegralNumber() || node.intValue() <= 0) {
			throw validationFailed();
		}
		return node.intValue();
	}

	private static String requiredText(JsonNode body, String field) {
		JsonNode node = body.get(field);
		if (node == null || node.isNull() || node.asText().isBlank()) {
			throw validationFailed();
		}
		return node.asText().trim();
	}

	private static ApiException lockedContent() {
		return new ApiException(ErrorCode.CONTENT_LOCKED, HttpStatus.CONFLICT,
				"The question paper and the schedule are locked.");
	}

	private static ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
	}

	private ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	public record ExamView(UUID id, UUID moduleId, String examType, String titleEn, String titleBn, String descriptionEn,
			String descriptionBn, String rulesEn, String rulesBn, String liveAt, String endAt, int durationMinutes,
			Integer passPercent, int maximum, String status, boolean finallySaved, Boolean questionPdf,
			List<QuestionView> questions) {
	}

	public record QuestionView(UUID id, String stem, int marks, String explanation, int position, List<OptionView> options) {
	}

	public record OptionView(UUID id, String text, boolean correct, int position) {
	}

}
