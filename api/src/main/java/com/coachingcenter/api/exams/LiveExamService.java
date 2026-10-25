package com.coachingcenter.api.exams;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.files.LocalFileStorage;

import tools.jackson.databind.JsonNode;

@Service
public class LiveExamService {

	static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

	private static final Duration GAP = Duration.ofMinutes(5);

	private static final UUID NO_HIDDEN_CATEGORY = new UUID(0L, 0L);

	private static final DateTimeFormatter DHAKA_TIME = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

	private final LiveExamRepository exams;

	private final LiveExamQuestionRepository questions;

	private final LiveExamOptionRepository options;

	private final LiveExamModuleRepository modules;

	private final LiveExamCategoryRepository categories;

	private final LiveExamAccess access;

	private final LiveExamCatalogVisibility visibility;

	private final LocalFileStorage files;

	private final ExamMailService examMail;

	private final AfterCommit afterCommit;

	private final Clock clock;

	private final AuditRecorder audit;

	private final LiveExamParticipantRepository participants;

	public LiveExamService(LiveExamRepository exams, LiveExamQuestionRepository questions,
			LiveExamOptionRepository options, LiveExamModuleRepository modules, LiveExamCategoryRepository categories,
			LiveExamAccess access, LiveExamCatalogVisibility visibility, LocalFileStorage files, ExamMailService examMail,
			AfterCommit afterCommit, Clock clock, AuditRecorder audit,
			LiveExamParticipantRepository participants) {
		this.exams = exams;
		this.questions = questions;
		this.options = options;
		this.modules = modules;
		this.categories = categories;
		this.access = access;
		this.visibility = visibility;
		this.files = files;
		this.examMail = examMail;
		this.afterCommit = afterCommit;
		this.clock = clock;
		this.audit = audit;
		this.participants = participants;
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
		exam.setPaperLanguage(readPaperLanguage(body));
		exam.setLiveAt(readOptionalInstant(body, "liveAt"));
		exam.setDurationMinutes(readDuration(body));
		exam.setPassPercent(readPassPercent(body, false));
		if (LiveExam.WRITTEN.equals(examType)) {
			exam.setWrittenMaximum(readMaximum(body, false));
		}
		exam.setStatus(LiveExam.DRAFT);
		exam.setFinallySaved(false);
		if (body.has("price")) {
			exam.setPrice(readPrice(body.get("price")));
		}
		return view(exams.save(exam), true);
	}

	@Transactional
	public ExamView update(UUID id, JsonNode body) {
		LiveExam exam = require(id);
		boolean schedule = body.has("liveAt") || body.has("durationMinutes");
		boolean scoring = body.has("passPercent")
				|| (LiveExam.WRITTEN.equals(exam.getExamType()) && body.has("maximum"));
		boolean locked = exam.getLiveAt() != null && !clock.instant().isBefore(exam.getLiveAt());
		if (body.has("paperLanguage") && !body.get("paperLanguage").isNull()) {
			if (locked) {
				throw lockedContent();
			}
			exam.setPaperLanguage(readPaperLanguage(body));
		}
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
		if (body.has("liveAt")) {
			if (body.get("liveAt").isNull()) {
				exam.setLiveAt(null);
			}
			else {
				exam.setLiveAt(readInstant(body, "liveAt"));
			}
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
		if (body.has("price")) {
			exam.setPrice(readPrice(body.get("price")));
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
	public void deleteQuestion(UUID examId, UUID questionId) {
		LiveExam exam = requireMcq(examId);
		markDraft(exam);
		LiveExamQuestion question = questions.findByIdAndExamIdAndDeletedAtIsNull(questionId, examId)
			.orElseThrow(this::notFound);
		Instant now = clock.instant();
		for (LiveExamOption option : options.findByQuestionIdAndDeletedAtIsNullOrderByPositionAscIdAsc(questionId)) {
			option.setDeletedAt(now);
		}
		question.setDeletedAt(now);
		int position = 0;
		for (LiveExamQuestion remaining : questions.findByExamIdAndDeletedAtIsNullOrderByPositionAscIdAsc(examId)) {
			if (!remaining.getId().equals(questionId)) {
				remaining.setPosition(position++);
			}
		}
		audit.record(AuditRecorder.LIVE_EXAM_QUESTION_DELETE, AuditRecorder.LIVE_EXAM_ENTITY, examId);
	}

	/**
	 * A draft copy in the same module: texts, settings and content (MCQ questions with their options, or the question
	 * PDF as a new stored file). The date and the price stay empty, so the copy is not scheduled or sold by accident.
	 */
	@Transactional
	public ExamView copy(UUID id) {
		LiveExam source = require(id);
		LiveExamModule module = requireModule(source.getModuleId());
		visibility.requireOpen(module.getCategoryId());
		LiveExam copy = new LiveExam();
		copy.setModuleId(module.getId());
		copy.setExamType(source.getExamType());
		copy.setTitleEn(source.getTitleEn() + " (copy)");
		copy.setTitleBn(source.getTitleBn() + " (কপি)");
		copy.setDescriptionEn(source.getDescriptionEn());
		copy.setDescriptionBn(source.getDescriptionBn());
		copy.setRulesEn(source.getRulesEn());
		copy.setRulesBn(source.getRulesBn());
		copy.setPaperLanguage(source.getPaperLanguage());
		copy.setDurationMinutes(source.getDurationMinutes());
		copy.setPassPercent(source.getPassPercent());
		copy.setWrittenMaximum(source.getWrittenMaximum());
		copy.setStatus(LiveExam.DRAFT);
		copy.setFinallySaved(false);
		if (source.getQuestionStoredFileName() != null) {
			byte[] bytes;
			try {
				bytes = java.nio.file.Files.readAllBytes(files.resolve(source.getQuestionStoredFileName()));
			}
			catch (IOException exception) {
				throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
			}
			LocalFileStorage.StoredFile stored = files.save(source.getQuestionOriginalFileName(),
					source.getQuestionContentType(), new java.io.ByteArrayInputStream(bytes), bytes.length);
			copy.setQuestionStoredFileName(stored.storedFileName());
			copy.setQuestionOriginalFileName(stored.originalFileName());
			copy.setQuestionByteSize(stored.byteSize());
			copy.setQuestionContentType(source.getQuestionContentType());
		}
		LiveExam saved = exams.save(copy);
		for (LiveExamQuestion sourceQuestion : questions.findByExamIdAndDeletedAtIsNullOrderByPositionAscIdAsc(source.getId())) {
			LiveExamQuestion question = new LiveExamQuestion();
			question.setExamId(saved.getId());
			question.setStem(sourceQuestion.getStem());
			question.setMarks(sourceQuestion.getMarks());
			question.setExplanation(sourceQuestion.getExplanation());
			question.setPosition(sourceQuestion.getPosition());
			LiveExamQuestion savedQuestion = questions.save(question);
			for (LiveExamOption sourceOption : options.findByQuestionIdAndDeletedAtIsNullOrderByPositionAscIdAsc(sourceQuestion.getId())) {
				LiveExamOption option = new LiveExamOption();
				option.setQuestionId(savedQuestion.getId());
				option.setOptionText(sourceOption.getOptionText());
				option.setCorrect(sourceOption.isCorrect());
				option.setPosition(sourceOption.getPosition());
				options.save(option);
			}
		}
		return view(saved, true);
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
		if (exam.getLiveAt() != null) {
			if (!clock.instant().isBefore(end(exam))) {
				throw new ApiException(ErrorCode.EXAM_WINDOW_ENDED, HttpStatus.CONFLICT, "The exam window has already ended.");
			}
			rejectWindowConflict(exam);
		}
		visibility.requireOpen(requireModule(exam.getModuleId()).getCategoryId());
		exam.setStatus(LiveExam.PUBLISHED);
		UUID examId = exam.getId();
		afterCommit.run(() -> examMail.onExamPublished(examId));
		return view(exam, true);
	}

	@Transactional(readOnly = true)
	public Page<ExamView> list(UUID moduleId, Pageable pageable, boolean student, UUID studentId) {
		return list(moduleId, pageable, student, studentId, LiveExamTypeFilter.ANY);
	}

	/** {@code examType} applies to the student read only, as {@link LiveExamTypeFilter} parsed it. */
	@Transactional(readOnly = true)
	public Page<ExamView> list(UUID moduleId, Pageable pageable, boolean student, UUID studentId, String examType) {
		LiveExamModule module = requireModule(moduleId);
		Pageable ordered = ordered(pageable);
		if (student && (!LiveExamModule.PUBLISHED.equals(module.getStatus())
				|| visibility.branchHidden(module.getCategoryId()))) {
			return Page.empty(ordered);
		}
		Page<LiveExam> page = student
				? exams.findAccessibleToStudent(moduleId, studentId, examType, ordered)
				: exams.findByModuleIdAndDeletedAtIsNull(moduleId, ordered);
		if (!student) {
			return page.map(exam -> view(exam, true));
		}
		Map<UUID, LiveExamParticipant> mine = participantsByExam(studentId,
				page.getContent().stream().map(LiveExam::getId).toList());
		return page.map(exam -> studentView(exam, mine.get(exam.getId())));
	}

	@Transactional(readOnly = true)
	public Page<RoutineView> routine(UUID studentId, Pageable pageable) {
		Set<UUID> hidden = visibility.hiddenCategoryIds();
		if (hidden.isEmpty()) {
			hidden = Set.of(NO_HIDDEN_CATEGORY);
		}
		Instant now = clock.instant();
		List<LiveExam> open = new ArrayList<>();
		for (LiveExam exam : exams.findVisibleRoutineCandidates(studentId, hidden)) {
			if (exam.getLiveAt() != null && end(exam).isAfter(now)) {
				open.add(exam);
			}
		}
		open.sort(Comparator.comparing(LiveExam::getLiveAt).thenComparing(LiveExam::getId));
		Map<UUID, LiveExamCategory> byId = new HashMap<>();
		for (LiveExamCategory category : categories.findAll()) {
			byId.put(category.getId(), category);
		}
		int page = pageable.getPageNumber();
		int size = pageable.getPageSize();
		int from = Math.min(page * size, open.size());
		int to = Math.min(from + size, open.size());
		List<RoutineView> content = new ArrayList<>();
		List<LiveExam> shown = open.subList(from, to);
		Map<UUID, LiveExamParticipant> mine = participantsByExam(studentId, shown.stream().map(LiveExam::getId).toList());
		for (LiveExam exam : shown) {
			content.add(routineView(exam, byId, LiveExamStudentStatus.of(exam, mine.get(exam.getId()), now)));
		}
		return new PageImpl<>(content, PageRequest.of(page, size), open.size());
	}

	@Transactional(readOnly = true)
	public ExamView get(UUID id, boolean staff, UUID studentId) {
		LiveExam exam = require(id);
		if (!staff && (!LiveExam.PUBLISHED.equals(exam.getStatus()) || !visibleModule(exam)
				|| !access.hasExam(studentId, exam))) {
			throw notFound();
		}
		if (staff) {
			return view(exam, true);
		}
		return studentView(exam, participantsByExam(studentId, List.of(exam.getId())).get(exam.getId()));
	}

	private ExamView studentView(LiveExam exam, LiveExamParticipant participant) {
		ExamView plain = view(exam, false);
		LiveExamStudentStatus.Result status = LiveExamStudentStatus.of(exam, participant, clock.instant());
		return new ExamView(plain.id(), plain.moduleId(), plain.examType(), plain.paperLanguage(), plain.titleEn(),
				plain.titleBn(), plain.descriptionEn(), plain.descriptionBn(), plain.rulesEn(), plain.rulesBn(),
				plain.liveAt(), plain.endAt(), plain.durationMinutes(), plain.passPercent(), plain.maximum(),
				plain.status(), plain.finallySaved(), plain.price(), plain.questionPdf(), plain.questions(),
				status.status(), status.retakeAvailable());
	}

	private Map<UUID, LiveExamParticipant> participantsByExam(UUID studentId, List<UUID> examIds) {
		Map<UUID, LiveExamParticipant> byExam = new HashMap<>();
		if (!examIds.isEmpty()) {
			for (LiveExamParticipant participant : participants.findByStudentIdAndExamIdInAndDeletedAtIsNull(studentId,
					examIds)) {
				byExam.put(participant.getExamId(), participant);
			}
		}
		return byExam;
	}

	private void rejectWindowConflict(LiveExam exam) {
		Instant start = exam.getLiveAt();
		Instant end = end(exam);
		for (LiveExam other : exams.findByModuleIdAndStatusAndDeletedAtIsNull(exam.getModuleId(), LiveExam.PUBLISHED)) {
			if (other.getId().equals(exam.getId()) || other.getLiveAt() == null) {
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
								+ format(otherEnd) + ").",
								new WindowClash(other.getId(), other.getTitleEn(), other.getTitleBn(), otherStart, otherEnd));
			}
		}
	}

	/** The exam that a new window clashes with, so the editor can name it and link to it. */
	public record WindowClash(UUID id, String titleEn, String titleBn, Instant liveAt, Instant endAt) {
	}

	private void markDraft(LiveExam exam) {
		if (exam.getLiveAt() != null && !clock.instant().isBefore(exam.getLiveAt())) {
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
		return new ExamView(exam.getId(), exam.getModuleId(), exam.getExamType(), exam.getPaperLanguage(),
				exam.getTitleEn(), exam.getTitleBn(), exam.getDescriptionEn(), exam.getDescriptionBn(), exam.getRulesEn(),
				exam.getRulesBn(), format(exam.getLiveAt()), format(end(exam)), exam.getDurationMinutes(),
				exam.getPassPercent(), maximum(exam), exam.getStatus(), exam.isFinallySaved(), exam.getPrice(),
				questionPdf, questionViews, null, null);
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

	private RoutineView routineView(LiveExam exam, Map<UUID, LiveExamCategory> categoriesById,
			LiveExamStudentStatus.Result status) {
		LiveExamModule module = modules.findByIdAndDeletedAtIsNull(exam.getModuleId()).orElseThrow(this::notFound);
		return new RoutineView(exam.getId(), module.getId(), exam.getExamType(), exam.getTitleEn(), exam.getTitleBn(),
				module.getTitleEn(), module.getTitleBn(), categoryPath(module.getCategoryId(), categoriesById),
				format(exam.getLiveAt()), format(end(exam)), exam.getDurationMinutes(), status.status(),
				status.retakeAvailable());
	}

	private static List<CategoryName> categoryPath(UUID categoryId, Map<UUID, LiveExamCategory> categoriesById) {
		List<CategoryName> path = new ArrayList<>();
		Set<UUID> seen = new HashSet<>();
		UUID id = categoryId;
		while (id != null && seen.add(id)) {
			LiveExamCategory category = categoriesById.get(id);
			if (category == null) {
				break;
			}
			path.add(new CategoryName(category.getId(), category.getTitleEn(), category.getTitleBn()));
			id = category.getParentId();
		}
		Collections.reverse(path);
		return path;
	}

	private static Instant end(LiveExam exam) {
		if (exam.getLiveAt() == null) {
			return null;
		}
		return exam.getLiveAt().plus(Duration.ofMinutes(exam.getDurationMinutes()));
	}

	private static String format(Instant instant) {
		if (instant == null) {
			return null;
		}
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
		return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
				Sort.by(Sort.Order.asc("liveAt").with(Sort.NullHandling.NULLS_LAST)));
	}

	private static void rejectMaximumOnMcq(String examType, JsonNode body) {
		if (LiveExam.MCQ.equals(examType) && body.has("maximum") && !body.get("maximum").isNull()) {
			throw validationFailed();
		}
	}

	private static Instant readOptionalInstant(JsonNode body, String field) {
		if (!body.has(field) || body.get(field).isNull()) {
			return null;
		}
		return readInstant(body, field);
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

	private static BigDecimal readPrice(JsonNode node) {
		if (node == null || node.isNull()) {
			return null;
		}
		BigDecimal price;
		if (node.isNumber()) {
			price = node.decimalValue();
		}
		else {
			try {
				price = new BigDecimal(node.asText().trim());
			}
			catch (NumberFormatException exception) {
				throw validationFailed();
			}
		}
		if (price.compareTo(BigDecimal.ZERO) <= 0 || price.scale() > 2) {
			throw validationFailed();
		}
		return price.setScale(2, RoundingMode.UNNECESSARY);
	}

	private static int readMarks(JsonNode body) {
		JsonNode node = body.get("marks");
		if (node == null || !node.isIntegralNumber() || node.intValue() <= 0) {
			throw validationFailed();
		}
		return node.intValue();
	}

	private static String readPaperLanguage(JsonNode body) {
		String language = requiredText(body, "paperLanguage");
		if (!LiveExam.BN.equals(language) && !LiveExam.EN.equals(language)) {
			throw validationFailed();
		}
		return language;
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

	public record RoutineView(UUID id, UUID moduleId, String examType, String titleEn, String titleBn,
			String moduleTitleEn, String moduleTitleBn, List<CategoryName> categories, String liveAt, String endAt,
			int durationMinutes, String myStatus, Boolean retakeAvailable) {
	}

	public record CategoryName(UUID id, String titleEn, String titleBn) {
	}

	public record ExamView(UUID id, UUID moduleId, String examType, String paperLanguage, String titleEn, String titleBn,
			String descriptionEn, String descriptionBn, String rulesEn, String rulesBn, String liveAt, String endAt,
			int durationMinutes, Integer passPercent, int maximum, String status, boolean finallySaved, BigDecimal price,
			Boolean questionPdf, List<QuestionView> questions, String myStatus,
			Boolean retakeAvailable) {
	}

	public record QuestionView(UUID id, String stem, int marks, String explanation, int position, List<OptionView> options) {
	}

	public record OptionView(UUID id, String text, boolean correct, int position) {
	}

}
