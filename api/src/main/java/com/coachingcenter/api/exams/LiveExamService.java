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
import com.coachingcenter.api.common.error.FieldErrorDetail;
import com.coachingcenter.api.files.LocalFileStorage;
import com.coachingcenter.api.users.AdminProfileRepository;

import tools.jackson.databind.JsonNode;

@Service
public class LiveExamService {

	static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

	private static final UUID NO_HIDDEN_CATEGORY = new UUID(0L, 0L);

	private static final DateTimeFormatter DHAKA_TIME = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

	/** Column width of {@code title_en} and {@code title_bn} (V25). */
	private static final int MAX_TITLE_LENGTH = 500;

	/** The shortest exam, in minutes. Set by the client rule that an exam is at least two minutes. */
	private static final int MIN_DURATION_MINUTES = 2;

	/** The largest negative mark per wrong answer that fits {@code NUMERIC(5,2)}. */
	private static final BigDecimal MAX_NEGATIVE_MARKS = new BigDecimal("999.99");

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

	private final StudentNoticeWriter notices;

	private final LiveExamMemberships memberships;

	private final LiveExamQuestionPaperRepository papers;

	private final LiveExamInstructorRepository instructors;

	private final AdminProfileRepository adminProfiles;

	private final LiveExamRevisionService staging;

	private final RichTextSanitizer sanitizer;

	public LiveExamService(LiveExamRepository exams, LiveExamQuestionRepository questions,
			LiveExamOptionRepository options, LiveExamModuleRepository modules, LiveExamCategoryRepository categories,
			LiveExamAccess access, LiveExamCatalogVisibility visibility, LocalFileStorage files, ExamMailService examMail,
			AfterCommit afterCommit, Clock clock, AuditRecorder audit,
			LiveExamParticipantRepository participants, StudentNoticeWriter notices,
			LiveExamMemberships memberships, LiveExamQuestionPaperRepository papers,
			LiveExamInstructorRepository instructors, AdminProfileRepository adminProfiles,
			LiveExamRevisionService staging, RichTextSanitizer sanitizer) {
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
		this.notices = notices;
		this.memberships = memberships;
		this.papers = papers;
		this.instructors = instructors;
		this.adminProfiles = adminProfiles;
		this.staging = staging;
		this.sanitizer = sanitizer;
	}

	/**
	 * A new draft exam. With a {@code moduleId} it is created inside that package: the category comes from the package,
	 * the exam joins it, and it takes the package's default instructor. With none it is a standalone exam in the
	 * {@code categoryId} of the body (R2).
	 */
	@Transactional
	public ExamView create(UUID moduleId, JsonNode body) {
		LiveExamModule module = moduleId == null ? null : requireModule(moduleId);
		UUID categoryId = module != null ? module.getCategoryId() : readId(body, "categoryId");
		visibility.requireOpen(categoryId);
		String examType = requiredText(body, "examType");
		if (!LiveExam.MCQ.equals(examType) && !LiveExam.WRITTEN.equals(examType)) {
			throw validationFailed();
		}
		LiveExam exam = new LiveExam();
		exam.setCategoryId(categoryId);
		if (module != null) {
			exam.setModuleId(module.getId());
			exam.setInstructorId(defaultInstructor(module.getId()));
		}
		exam.setMode(LiveExam.MODE_LIVE);
		exam.setAnswersChangeable(true);
		exam.setExamType(examType);
		exam.setStatus(LiveExam.DRAFT);
		exam.setFinallySaved(false);
		applyEdit(exam, body, true);
		LiveExam saved = exams.save(exam);
		if (module != null) {
			memberships.attach(module.getId(), saved.getId(), clock.instant());
		}
		return view(saved, true);
	}

	/**
	 * Edits an exam's settings. A published exam takes the edit as a staged revision (E31): the rows and the students' view
	 * stay as they are until the Master Admin publishes. A draft takes the edit directly.
	 */
	@Transactional
	public ExamView update(UUID id, JsonNode body, UUID actorId) {
		LiveExam exam = requireEditable(id);
		if (LiveExam.PUBLISHED.equals(exam.getStatus())) {
			ExamState working = staging.working(exam);
			LiveExam probe = settingsOf(exam, working);
			applyEdit(probe, body, false);
			working.readSettingsFrom(probe);
			staging.stage(exam, working, actorId);
			return view(exam, true);
		}
		applyEdit(exam, body, false);
		exam.setFinallySaved(false);
		return view(exam, true);
	}

	/**
	 * Applies the fields of a create or update body to {@code exam} and checks the result as a whole: the window against
	 * the duration, and the price against the discount and the free flag. There is no lock (E27, E30): the schedule, the
	 * scoring and the text can change at any time. {@code popular} is not read here; it has its own route.
	 */
	private void applyEdit(LiveExam exam, JsonNode body, boolean create) {
		rejectMaximumOnMcq(exam.getExamType(), body);
		applyText(exam, body, create);
		if (!create && body.has("categoryId")) {
			applyCategory(exam, body);
		}
		if (create || body.has("paperLanguage")) {
			exam.setPaperLanguage(readPaperLanguage(body));
		}
		if (body.has("mode") && !body.get("mode").isNull()) {
			applyMode(exam, readMode(body), create);
		}
		applySchedule(exam, body, create);
		if (create || body.has("passPercent")) {
			exam.setPassPercent(readPassPercent(body, !create));
		}
		if (LiveExam.WRITTEN.equals(exam.getExamType()) && body.has("maximum")) {
			exam.setWrittenMaximum(readMaximum(body, !create));
		}
		if (body.has("price")) {
			exam.setPrice(readPrice(body.get("price")));
		}
		if (body.has("free")) {
			exam.setFree(readBoolean(body, "free"));
		}
		if (body.has("discountPercent")) {
			exam.setDiscountPercent(readOptionalInt(body, "discountPercent"));
		}
		if (body.has("discountEndsAt")) {
			Instant discountEndsAt = readOptionalInstant(body, "discountEndsAt");
			ExamPricing.requireNotPast(discountEndsAt, exam.getDiscountEndsAt(), clock.instant());
			exam.setDiscountEndsAt(discountEndsAt);
		}
		if (body.has("accessDays")) {
			Integer days = readOptionalInt(body, "accessDays");
			if (days != null && days <= 0) {
				throw validationFailed();
			}
			exam.setAccessDays(days);
		}
		ExamPricing.validate(exam.getPrice(), exam.isFree(), exam.getDiscountPercent(), exam.getDiscountEndsAt());
		if (body.has("negativeMarks")) {
			exam.setNegativeMarks(readNegativeMarks(body.get("negativeMarks")));
		}
		if (body.has("answersChangeable")) {
			exam.setAnswersChangeable(readBoolean(body, "answersChangeable"));
		}
		if (body.has("previewEnabled")) {
			exam.setPreviewEnabled(readBoolean(body, "previewEnabled"));
		}
	}

	/**
	 * The titles and the rich text. A create may leave the description and rules blank (R1); publish refuses them. Both
	 * the draft and the staged edit come through here, and the rich text is cleaned before it is stored (R3).
	 */
	private void applyText(LiveExam exam, JsonNode body, boolean create) {
		for (String field : List.of("titleEn", "titleBn", "descriptionEn", "descriptionBn", "rulesEn", "rulesBn")) {
			if (!create && (!body.has(field) || body.get(field).isNull())) {
				continue;
			}
			boolean richText = field.startsWith("description") || field.startsWith("rules");
			String value = create && richText ? textOrBlank(body, field) : requiredText(body, field);
			switch (field) {
				case "titleEn" -> exam.setTitleEn(value);
				case "titleBn" -> exam.setTitleBn(value);
				case "descriptionEn" -> exam.setDescriptionEn(sanitizer.clean(value));
				case "descriptionBn" -> exam.setDescriptionBn(sanitizer.clean(value));
				case "rulesEn" -> exam.setRulesEn(sanitizer.clean(value));
				default -> exam.setRulesBn(sanitizer.clean(value));
			}
		}
	}

	/**
	 * The category of any exam, in a package or standalone (NR-01 UI Step 1 fix). It must be a category that exists and is
	 * not archived, and not under an archived branch. An exam that already sits in a category that has since been archived
	 * keeps it when the body repeats that same id, so other fields can still be saved.
	 */
	private void applyCategory(LiveExam exam, JsonNode body) {
		UUID requested = readOptionalId(body.get("categoryId"));
		if (requested != null && requested.equals(exam.getCategoryId())) {
			return;
		}
		boolean choosable = requested != null && categories.findByIdAndDeletedAtIsNull(requested).isPresent()
				&& !visibility.branchHidden(requested);
		if (!choosable) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.",
					List.of(new FieldErrorDetail("categoryId", "Choose a category that is not archived.")));
		}
		exam.setCategoryId(requested);
	}

	/** The id in a JSON node, or null when the node is missing, null or not a UUID. */
	private static UUID readOptionalId(JsonNode node) {
		if (node == null || node.isNull()) {
			return null;
		}
		try {
			return UUID.fromString(node.asText().trim());
		}
		catch (IllegalArgumentException exception) {
			return null;
		}
	}

	/** The mode can change only while the exam is a draft that nobody has started. */
	private void applyMode(LiveExam exam, String mode, boolean create) {
		if (mode.equals(exam.getMode())) {
			return;
		}
		if (!create && (!LiveExam.DRAFT.equals(exam.getStatus()) || participants.existsByExamId(exam.getId()))) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.",
					List.of(new FieldErrorDetail("mode", "The mode can change only on a draft that nobody has started.")));
		}
		exam.setMode(mode);
		if (LiveExam.MODE_PRACTICE.equals(mode)) {
			exam.setLiveAt(null);
			exam.setEndsAt(null);
		}
	}

	/**
	 * The window and the duration. A request that sets {@code liveAt} without {@code endAt} gets the window
	 * {@code liveAt + duration}, which is what the current web sends. A duration change on a window that was exactly one
	 * duration long moves the end with it, for the same reason.
	 */
	private void applySchedule(LiveExam exam, JsonNode body, boolean create) {
		Instant previousStart = exam.getLiveAt();
		Instant previousEnd = exam.getEndsAt();
		int previousDuration = exam.getDurationMinutes();
		boolean startSent = body.has("liveAt");
		boolean endSent = body.has("endAt");
		if (startSent) {
			exam.setLiveAt(readOptionalInstant(body, "liveAt"));
		}
		if (endSent) {
			exam.setEndsAt(readOptionalInstant(body, "endAt"));
		}
		if (create || (body.has("durationMinutes") && !body.get("durationMinutes").isNull())) {
			exam.setDurationMinutes(readDuration(body));
		}
		if (!endSent && !LiveExam.MODE_PRACTICE.equals(exam.getMode())) {
			boolean oneDurationLong = previousStart != null && previousEnd != null
					&& previousEnd.equals(previousStart.plus(Duration.ofMinutes(previousDuration)));
			if (exam.getLiveAt() == null) {
				exam.setEndsAt(null);
			}
			else if (startSent || oneDurationLong || exam.getEndsAt() == null) {
				exam.setEndsAt(windowEnd(exam));
			}
		}
		ExamWindow.validate(exam.getMode(), exam.getLiveAt(), exam.getEndsAt(), exam.getDurationMinutes());
	}

	@Transactional
	public QuestionView addQuestion(UUID examId, JsonNode body, UUID actorId) {
		LiveExam exam = requireMcq(examId);
		requireStructureOpen(exam.getId());
		if (LiveExam.PUBLISHED.equals(exam.getStatus())) {
			return stagedAddQuestion(exam, body, actorId);
		}
		markDraft(exam);
		LiveExamQuestion question = new LiveExamQuestion();
		question.setExamId(exam.getId());
		boolean multiple = readMultipleAnswer(body, false);
		applyQuestion(question, body, nextQuestionPosition(exam.getId()), multiple);
		questions.save(question);
		List<LiveExamOption> saved = replaceOptions(question.getId(), body.get("options"), multiple);
		return questionView(question, saved);
	}

	@Transactional
	public QuestionView updateQuestion(UUID examId, UUID questionId, JsonNode body, UUID actorId) {
		LiveExam exam = requireMcq(examId);
		if (LiveExam.PUBLISHED.equals(exam.getStatus())) {
			return stagedUpdateQuestion(exam, questionId, body, actorId);
		}
		markDraft(exam);
		LiveExamQuestion question = questions.findByIdAndExamIdAndDeletedAtIsNull(questionId, examId)
			.orElseThrow(this::notFound);
		if (optionCountChanges(question.getId(), body.get("options"))) {
			requireStructureOpen(exam.getId());
		}
		int position = body.has("position") && !body.get("position").isNull() ? body.get("position").asInt()
				: question.getPosition();
		boolean multiple = readMultipleAnswer(body, question.isMultipleAnswer());
		applyQuestion(question, body, position, multiple);
		return questionView(question, replaceOptions(question.getId(), body.get("options"), multiple));
	}

	@Transactional
	public void deleteQuestion(UUID examId, UUID questionId, UUID actorId) {
		LiveExam exam = requireMcq(examId);
		requireStructureOpen(exam.getId());
		if (LiveExam.PUBLISHED.equals(exam.getStatus())) {
			stagedDeleteQuestion(exam, questionId, actorId);
			return;
		}
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
	 * A draft copy of the exam, taken from its working copy: the staged revision when one waits, else the published rows. It
	 * carries the texts, settings and questions, in the same category and in every package the source is in. The dates, the
	 * price, the discount and the popular flag stay empty, so the copy is not scheduled or sold by accident.
	 */
	@Transactional
	public ExamView copy(UUID id, UUID actorId) {
		LiveExam source = require(id);
		visibility.requireOpen(source.getCategoryId());
		ExamState working = staging.working(source);
		List<UUID> packageIds = memberships.activeModuleIds(source.getId());
		List<LiveExamModule> targets = new ArrayList<>();
		for (UUID packageId : packageIds) {
			if (!packageId.equals(packageIds.get(0))) {
				modules.findByIdAndDeletedAtIsNull(packageId)
					.filter(found -> !LiveExamModule.ARCHIVED.equals(found.getStatus()))
					.ifPresent(targets::add);
			}
			else {
				targets.add(requireModule(packageId));
			}
		}
		LiveExam copy = new LiveExam();
		if (!targets.isEmpty()) {
			copy.setModuleId(targets.get(0).getId());
		}
		copy.setCategoryId(source.getCategoryId());
		copy.setInstructorId(source.getInstructorId());
		copy.setMode(working.mode);
		copy.setAnswersChangeable(working.answersChangeable);
		copy.setNegativeMarks(working.negativeMarks);
		copy.setPreviewEnabled(working.previewEnabled);
		copy.setFree(working.free);
		copy.setAccessDays(working.accessDays);
		copy.setExamType(source.getExamType());
		String titleEn = working.titleEn + " (copy)";
		String titleBn = working.titleBn + " (কপি)";
		if (titleEn.length() > MAX_TITLE_LENGTH || titleBn.length() > MAX_TITLE_LENGTH) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
					"The title is too long to copy. Shorten the title to fit 500 characters with the copy suffix, then copy again.");
		}
		copy.setTitleEn(titleEn);
		copy.setTitleBn(titleBn);
		copy.setDescriptionEn(working.descriptionEn);
		copy.setDescriptionBn(working.descriptionBn);
		copy.setRulesEn(working.rulesEn);
		copy.setRulesBn(working.rulesBn);
		copy.setPaperLanguage(working.paperLanguage);
		copy.setDurationMinutes(working.durationMinutes);
		copy.setPassPercent(working.passPercent);
		copy.setWrittenMaximum(working.writtenMaximum);
		copy.setStatus(LiveExam.DRAFT);
		copy.setFinallySaved(false);
		LiveExamQuestionPaper sourcePaper = working.paperId == null ? null
				: papers.findById(working.paperId).orElse(null);
		if (sourcePaper != null) {
			byte[] bytes;
			try {
				bytes = java.nio.file.Files.readAllBytes(files.resolve(sourcePaper.getStoredFileName()));
			}
			catch (IOException exception) {
				throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
			}
			LocalFileStorage.StoredFile stored = files.save(sourcePaper.getOriginalFileName(), sourcePaper.getContentType(),
					new java.io.ByteArrayInputStream(bytes), bytes.length);
			files.commitReplacement(stored.storedFileName(), null);
			copy.setQuestionStoredFileName(stored.storedFileName());
			copy.setQuestionOriginalFileName(stored.originalFileName());
			copy.setQuestionByteSize(stored.byteSize());
			copy.setQuestionContentType(sourcePaper.getContentType());
		}
		LiveExam saved = exams.save(copy);
		if (copy.getQuestionStoredFileName() != null) {
			saved.setQuestionPaperId(papers.save(paperVersion(saved.getId(), 1, copy, actorId)).getId());
		}
		Instant now = clock.instant();
		for (LiveExamModule target : targets) {
			memberships.attach(target.getId(), saved.getId(), now);
		}
		for (ExamState.QuestionState sourceQuestion : working.questions) {
			LiveExamQuestion question = new LiveExamQuestion();
			question.setExamId(saved.getId());
			question.setStem(sourceQuestion.stem);
			question.setMarks(sourceQuestion.marks);
			question.setExplanation(sourceQuestion.explanation);
			question.setPosition(sourceQuestion.position);
			question.setMultipleAnswer(sourceQuestion.multipleAnswer);
			LiveExamQuestion savedQuestion = questions.save(question);
			for (ExamState.OptionState sourceOption : sourceQuestion.options) {
				LiveExamOption option = new LiveExamOption();
				option.setQuestionId(savedQuestion.getId());
				option.setOptionText(sourceOption.text);
				option.setCorrect(sourceOption.correct);
				option.setPosition(sourceOption.position);
				options.save(option);
			}
		}
		return view(saved, true);
	}

	/**
	 * Stores the PDF as the next paper version. The earlier versions and their files stay: a student who sat one of them
	 * and the evaluator who marks it still read that version (E29).
	 */
	@Transactional
	public ExamView uploadQuestionPdf(UUID examId, MultipartFile file, UUID actorId) {
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
		LocalFileStorage.StoredFile stored = files.save(file.getOriginalFilename(), "application/pdf",
				new java.io.ByteArrayInputStream(bytes), bytes.length);
		// No earlier file is replaced; the call only removes this one if the transaction rolls back.
		files.commitReplacement(stored.storedFileName(), null);
		if (LiveExam.PUBLISHED.equals(exam.getStatus())) {
			// The new version waits in the revision; students keep the published paper until the publish (E29).
			LiveExam probe = new LiveExam();
			probe.setQuestionStoredFileName(stored.storedFileName());
			probe.setQuestionOriginalFileName(stored.originalFileName());
			probe.setQuestionByteSize(stored.byteSize());
			probe.setQuestionContentType("application/pdf");
			int version = papers.findTopByExamIdOrderByVersionDesc(exam.getId()).map(paper -> paper.getVersion() + 1).orElse(1);
			ExamState working = staging.working(exam);
			working.paperId = papers.save(paperVersion(exam.getId(), version, probe, actorId)).getId();
			staging.stage(exam, working, actorId);
			return view(exam, true);
		}
		exam.setQuestionStoredFileName(stored.storedFileName());
		exam.setQuestionOriginalFileName(stored.originalFileName());
		exam.setQuestionByteSize(stored.byteSize());
		exam.setQuestionContentType("application/pdf");
		int version = papers.findTopByExamIdOrderByVersionDesc(exam.getId()).map(paper -> paper.getVersion() + 1).orElse(1);
		exam.setQuestionPaperId(papers.save(paperVersion(exam.getId(), version, exam, actorId)).getId());
		return view(exam, true);
	}

	/** Takes a draft or published exam off the student website. Its posted results stay readable. */
	@Transactional
	public ExamView archive(UUID id) {
		LiveExam exam = require(id);
		if (!LiveExam.ARCHIVED.equals(exam.getStatus())) {
			exam.setStatus(LiveExam.ARCHIVED);
			audit.record(AuditRecorder.LIVE_EXAM_ARCHIVE, AuditRecorder.LIVE_EXAM_ENTITY, exam.getId());
		}
		return view(exam, true);
	}

	@Transactional
	public ExamView finalSave(UUID id) {
		LiveExam exam = requireEditable(id);
		exam.setFinallySaved(true);
		return view(exam, true);
	}

	/** Publishes without a revision id: the compatibility path (contracts section 7, item 4). */
	@Transactional
	public ExamView publish(UUID id) {
		return publish(id, null, null);
	}

	/**
	 * Publishes an exam. A published exam with a waiting revision publishes the revision (E31); the row lock is taken first, so
	 * a sitting cannot start or post halfway through. Without a revision, a published exam is published again as before
	 * (compatibility rule 4). {@code revisionId} must name the waiting revision when one is sent.
	 */
	@Transactional
	public ExamView publish(UUID id, String revisionId, UUID actorId) {
		LiveExam exam = exams.lockById(id).orElseThrow(this::notFound);
		if (LiveExam.ARCHIVED.equals(exam.getStatus())) {
			throw new ApiException(ErrorCode.EXAM_ARCHIVED, HttpStatus.CONFLICT, "An archived exam cannot be changed.");
		}
		if (LiveExam.PUBLISHED.equals(exam.getStatus()) && staging.hasRevision(exam.getId())) {
			staging.publish(exam, revisionId, actorId);
			return view(exam, true);
		}
		if (revisionId != null) {
			throw new ApiException(ErrorCode.PENDING_CHANGES_STALE, HttpStatus.CONFLICT,
					"There are no staged changes to publish. Read the exam again.");
		}
		if (!exam.isFinallySaved()) {
			throw new ApiException(ErrorCode.FINAL_SAVE_REQUIRED, HttpStatus.CONFLICT, "Publish requires a final save.");
		}
		if (exam.getPassPercent() == null) {
			throw new ApiException(ErrorCode.PASS_PERCENT_REQUIRED, HttpStatus.CONFLICT,
					"Publish requires a pass percent from 0 through 100.");
		}
		LiveExamRevisionService.requireTexts(exam.getDescriptionEn(), exam.getDescriptionBn(), exam.getRulesEn(),
				exam.getRulesBn());
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
		// An ended window is fine: archived participants can still sit the exam (D20). Windows may overlap (D17).
		visibility.requireOpen(exam.getCategoryId());
		exam.setStatus(LiveExam.PUBLISHED);
		if (exam.getPublishedAt() == null) {
			exam.setPublishedAt(clock.instant());
		}
		UUID examId = exam.getId();
		notices.published(examId, exams.findActiveStudentsWithAccessToExam(examId));
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
		if (!student) {
			// The staff list keeps the package's own order (membership position) unless the caller asked for a sort.
			Page<LiveExam> staff = pageable.getSort().isSorted()
					? exams.findByModuleIdAndDeletedAtIsNull(moduleId, ordered)
					: exams.findStaffByModuleIdInPackageOrder(moduleId,
							PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()));
			return staff.map(exam -> view(exam, true));
		}
		Page<LiveExam> page = exams.findAccessibleToStudent(moduleId, studentId, examType, ordered);
		Map<UUID, Mine> mine = mineByExam(studentId, page.getContent().stream().map(LiveExam::getId).toList());
		return page.map(exam -> studentView(exam, mine.get(exam.getId()), studentId));
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
			// An exam with no start time stays on the routine, last, with its status until a date is set.
			if (exam.getLiveAt() == null || end(exam).isAfter(now)) {
				open.add(exam);
			}
		}
		open.sort(Comparator.comparing(LiveExam::getLiveAt, Comparator.nullsLast(Comparator.naturalOrder()))
			.thenComparing(LiveExam::getId));
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
		Map<UUID, Mine> mine = mineByExam(studentId, shown.stream().map(LiveExam::getId).toList());
		for (LiveExam exam : shown) {
			Mine own = mine.get(exam.getId());
			content.add(routineView(exam, byId, LiveExamStudentStatus.of(exam, own == null ? null : own.latest(), now)));
		}
		return new PageImpl<>(content, PageRequest.of(page, size), open.size());
	}

	@Transactional(readOnly = true)
	public ExamView get(UUID id, boolean staff, UUID studentId) {
		LiveExam exam = require(id);
		if (!staff && (!LiveExam.PUBLISHED.equals(exam.getStatus()) || !visibleToStudents(exam)
				|| !access.state(studentId, exam, clock.instant()).granted())) {
			throw notFound();
		}
		if (staff) {
			return view(exam, true);
		}
		return studentView(exam, mineByExam(studentId, List.of(exam.getId())).get(exam.getId()), studentId);
	}

	/** The published exam as one student sees it: their access, their attempts, and their status on the latest one. */
	private ExamView studentView(LiveExam exam, Mine mine, UUID studentId) {
		ExamView plain = view(exam, false);
		Instant now = clock.instant();
		LiveExamStudentStatus.Result status = LiveExamStudentStatus.of(exam, mine == null ? null : mine.latest(), now);
		AccessState state = access.state(studentId, exam, now);
		AccessView accessView = new AccessView(state.granted(), state.source(),
				state.endsAt() == null ? null : state.endsAt().toString(), state.expired());
		return new ExamView(plain.id(), plain.moduleId(), plain.examType(), plain.paperLanguage(), plain.titleEn(),
				plain.titleBn(), plain.descriptionEn(), plain.descriptionBn(), plain.rulesEn(), plain.rulesBn(),
				plain.liveAt(), plain.endAt(), plain.durationMinutes(), plain.passPercent(), plain.maximum(),
				plain.status(), plain.finallySaved(), plain.price(), plain.questionPdf(), plain.questions(),
				status.status(), status.retakeAvailable(), null, plain.mode(), plain.phase(), plain.categoryId(),
				plain.packages(), null, plain.negativeMarks(), plain.answersChangeable(), plain.previewEnabled(),
				plain.popular(), plain.accessDays(), plain.pricing(), null, accessView,
				mine == null ? 0 : mine.attempts(), status.status(), plain.coverUrl());
	}

	/** One student's attempts at one exam: the latest, and how many there are. */
	private record Mine(LiveExamParticipant latest, int attempts) {
	}

	private Map<UUID, Mine> mineByExam(UUID studentId, List<UUID> examIds) {
		Map<UUID, Mine> byExam = new HashMap<>();
		if (!examIds.isEmpty()) {
			for (LiveExamParticipant participant : participants.findByStudentIdAndExamIdInAndDeletedAtIsNull(studentId,
					examIds)) {
				Mine known = byExam.get(participant.getExamId());
				if (known == null) {
					byExam.put(participant.getExamId(), new Mine(participant, 1));
				}
				else {
					LiveExamParticipant latest = participant.getAttemptNo() > known.latest().getAttemptNo() ? participant
							: known.latest();
					byExam.put(participant.getExamId(), new Mine(latest, known.attempts() + 1));
				}
			}
		}
		return byExam;
	}

	/** An unfinished exam is a draft: only a first save of a published one changes nothing, and a draft takes the edit. */
	private void markDraft(LiveExam exam) {
		if (!LiveExam.PUBLISHED.equals(exam.getStatus())) {
			exam.setFinallySaved(false);
		}
	}

	private void applyQuestion(LiveExamQuestion question, JsonNode body, int position, boolean multiple) {
		question.setStem(requiredText(body, "stem"));
		question.setMarks(readMarks(body));
		if (body.has("explanation") && !body.get("explanation").isNull() && !body.get("explanation").asText().isBlank()) {
			question.setExplanation(body.get("explanation").asText().trim());
		}
		else if (body.has("explanation")) {
			question.setExplanation(null);
		}
		question.setPosition(position);
		question.setMultipleAnswer(multiple);
	}

	/** The body's {@code multipleAnswer}, or the question's current setting when the body does not say (C14). */
	private static boolean readMultipleAnswer(JsonNode body, boolean current) {
		JsonNode node = body.get("multipleAnswer");
		if (node == null || node.isNull()) {
			return current;
		}
		if (!node.isBoolean()) {
			throw validationFailed();
		}
		return node.booleanValue();
	}

	/**
	 * Replaces a question's options. A single-answer question has exactly one correct option, a multi-answer one at least
	 * one, and every question has at least two options. When the count stays the same the rows are updated in place, so an
	 * answer already given still points at its option (R16, answers keep their ids).
	 */
	private List<LiveExamOption> replaceOptions(UUID questionId, JsonNode node, boolean multiple) {
		if (node == null || !node.isArray() || node.size() < 2) {
			throw validationFailed();
		}
		int correct = 0;
		List<String> texts = new ArrayList<>();
		for (JsonNode option : node) {
			if (option.has("correct") && option.get("correct").asBoolean()) {
				correct++;
			}
			String text = option.has("text") ? option.get("text").asText().trim() : "";
			if (text.isBlank()) {
				throw validationFailed();
			}
			texts.add(text);
		}
		if (multiple ? correct < 1 : correct != 1) {
			throw validationFailed();
		}
		List<LiveExamOption> current = options.findByQuestionIdAndDeletedAtIsNullOrderByPositionAscIdAsc(questionId);
		List<LiveExamOption> saved = new ArrayList<>();
		if (current.size() == node.size()) {
			for (int position = 0; position < node.size(); position++) {
				LiveExamOption row = current.get(position);
				row.setOptionText(texts.get(position));
				row.setCorrect(node.get(position).has("correct") && node.get(position).get("correct").asBoolean());
				row.setPosition(position);
				saved.add(row);
			}
			return saved;
		}
		options.deleteAll(current);
		options.flush();
		for (int position = 0; position < node.size(); position++) {
			LiveExamOption row = new LiveExamOption();
			row.setQuestionId(questionId);
			row.setOptionText(texts.get(position));
			row.setCorrect(node.get(position).has("correct") && node.get(position).get("correct").asBoolean());
			row.setPosition(position);
			saved.add(options.save(row));
		}
		return saved;
	}

	/** Once anyone has started the exam, its questions and options cannot be added or removed (R16). */
	private void requireStructureOpen(UUID examId) {
		if (participants.existsByExamId(examId)) {
			throw new ApiException(ErrorCode.QUESTION_STRUCTURE_LOCKED, HttpStatus.CONFLICT,
					"A student has started this exam, so its questions and options can no longer be added or removed.");
		}
	}

	private boolean optionCountChanges(UUID questionId, JsonNode node) {
		return node != null && node.isArray()
				&& node.size() != options.findByQuestionIdAndDeletedAtIsNullOrderByPositionAscIdAsc(questionId).size();
	}

	/** A question added to a published exam: it goes into the staged revision, with ids the publish will give rows. */
	private QuestionView stagedAddQuestion(LiveExam exam, JsonNode body, UUID actorId) {
		QuestionInput input = QuestionInput.parse(body);
		boolean multiple = input.multiple(false);
		input.requireKey(multiple);
		ExamState working = staging.working(exam);
		ExamState.QuestionState question = new ExamState.QuestionState();
		question.id = UUID.randomUUID();
		question.position = input.position() != null ? input.position() : nextStagedPosition(working);
		applyInput(question, input, multiple);
		working.questions.add(question);
		staging.stage(exam, working, actorId);
		return stagedQuestionView(question);
	}

	/** A question edit on a published exam: the option count is locked after a start, as for a draft (R16). */
	private QuestionView stagedUpdateQuestion(LiveExam exam, UUID questionId, JsonNode body, UUID actorId) {
		ExamState working = staging.working(exam);
		ExamState.QuestionState question = working.question(questionId);
		if (question == null) {
			throw notFound();
		}
		QuestionInput input = QuestionInput.parse(body);
		boolean multiple = input.multiple(question.multipleAnswer);
		input.requireKey(multiple);
		if (input.options().size() != question.options.size()) {
			requireStructureOpen(exam.getId());
		}
		if (input.position() != null) {
			question.position = input.position();
		}
		applyInput(question, input, multiple);
		staging.stage(exam, working, actorId);
		return stagedQuestionView(question);
	}

	private void stagedDeleteQuestion(LiveExam exam, UUID questionId, UUID actorId) {
		ExamState working = staging.working(exam);
		if (working.question(questionId) == null) {
			throw notFound();
		}
		working.questions.removeIf(question -> questionId.equals(question.id));
		working.questions.sort(java.util.Comparator.comparingInt(question -> question.position));
		int position = 0;
		for (ExamState.QuestionState question : working.questions) {
			question.position = position++;
		}
		staging.stage(exam, working, actorId);
	}

	/**
	 * Writes a question's wording, marks, key and explanation into the staged state. The option count decides the ids: the
	 * same count keeps every option id (so an answer still points at it); a new count gets fresh ids.
	 */
	private static void applyInput(ExamState.QuestionState question, QuestionInput input, boolean multiple) {
		question.stem = input.stem();
		question.marks = input.marks();
		if (input.explanationSent()) {
			question.explanation = input.explanation();
		}
		question.multipleAnswer = multiple;
		if (question.options.size() == input.options().size()) {
			for (int index = 0; index < input.options().size(); index++) {
				ExamState.OptionState option = question.options.get(index);
				option.text = input.options().get(index).text();
				option.correct = input.options().get(index).correct();
				option.position = index;
			}
			return;
		}
		question.options = new ArrayList<>();
		for (int index = 0; index < input.options().size(); index++) {
			ExamState.OptionState option = new ExamState.OptionState();
			option.id = UUID.randomUUID();
			option.text = input.options().get(index).text();
			option.correct = input.options().get(index).correct();
			option.position = index;
			question.options.add(option);
		}
	}

	private static int nextStagedPosition(ExamState working) {
		int max = -1;
		for (ExamState.QuestionState question : working.questions) {
			max = Math.max(max, question.position);
		}
		return max + 1;
	}

	private static QuestionView stagedQuestionView(ExamState.QuestionState question) {
		List<OptionView> optionViews = new ArrayList<>();
		for (ExamState.OptionState option : question.options) {
			optionViews.add(new OptionView(option.id, option.text, option.correct, option.position));
		}
		return new QuestionView(question.id, question.stem, question.marks, question.explanation, question.position,
				optionViews, question.multipleAnswer);
	}

	private int nextQuestionPosition(UUID examId) {
		int max = -1;
		for (LiveExamQuestion question : questions.findByExamIdAndDeletedAtIsNullOrderByPositionAscIdAsc(examId)) {
			max = Math.max(max, question.getPosition());
		}
		return max + 1;
	}

	/**
	 * The exam as staff or a public reader sees it. Staff see the working copy: the staged revision applied to the published
	 * rows, when one is waiting (E31). Students and the public never see a revision.
	 */
	private ExamView view(LiveExam exam, boolean staff) {
		ExamState working = staff ? staging.staged(exam).orElse(null) : null;
		LiveExam shown = working == null ? exam : settingsOf(exam, working);
		List<QuestionView> questionViews = null;
		if (staff && LiveExam.MCQ.equals(exam.getExamType())) {
			questionViews = working == null ? loadQuestions(exam.getId()) : stagedQuestionViews(working);
		}
		Boolean questionPdf = null;
		if (staff) {
			questionPdf = working == null ? exam.getQuestionStoredFileName() != null : working.paperId != null;
		}
		int maximum = working == null ? maximum(exam) : stagedMaximum(exam, working);
		Instant now = clock.instant();
		return new ExamView(exam.getId(), memberships.primaryModuleId(exam.getId()).orElse(null), shown.getExamType(),
				shown.getPaperLanguage(), shown.getTitleEn(), shown.getTitleBn(), shown.getDescriptionEn(),
				shown.getDescriptionBn(), shown.getRulesEn(), shown.getRulesBn(), format(shown.getLiveAt()),
				format(end(shown)), shown.getDurationMinutes(), shown.getPassPercent(), maximum, exam.getStatus(),
				exam.isFinallySaved(), shown.getPrice(), questionPdf, questionViews, null, null,
				staff && exam.getAnswersReleasedAt() != null ? exam.getAnswersReleasedAt().toString() : null,
				shown.getMode(), ExamWindow.phase(shown, now), shown.getCategoryId(), packagesOf(exam.getId()),
				staff ? instructorOf(shown) : null, shown.getNegativeMarks(), shown.isAnswersChangeable(),
				shown.isPreviewEnabled(), exam.isPopular(), shown.getAccessDays(), ExamPricing.view(shown, now, false),
				staff ? working != null : null, null, null, null, ExamCoverService.coverPath(exam));
	}

	/**
	 * A transient copy of the row that carries the staged settings, for reads only. It is never saved: the row keeps its id
	 * and status, and the settings come from the revision.
	 */
	private static LiveExam settingsOf(LiveExam row, ExamState working) {
		LiveExam shown = new LiveExam();
		shown.setExamType(row.getExamType());
		shown.setStatus(row.getStatus());
		shown.setCategoryId(row.getCategoryId());
		shown.setInstructorId(row.getInstructorId());
		shown.setPopular(row.isPopular());
		working.applySettingsTo(shown);
		return shown;
	}

	private List<QuestionView> stagedQuestionViews(ExamState working) {
		List<QuestionView> views = new ArrayList<>();
		for (ExamState.QuestionState question : working.questions) {
			List<OptionView> optionViews = new ArrayList<>();
			for (ExamState.OptionState option : question.options) {
				optionViews.add(new OptionView(option.id, option.text, option.correct, option.position));
			}
			views.add(new QuestionView(question.id, question.stem, question.marks, question.explanation, question.position,
					optionViews, question.multipleAnswer));
		}
		return views;
	}

	/** The maximum of the working copy: the written maximum for a written exam, else the sum of the question marks. */
	private static int stagedMaximum(LiveExam row, ExamState working) {
		if (LiveExam.WRITTEN.equals(row.getExamType())) {
			return working.writtenMaximum == null ? 0 : working.writtenMaximum;
		}
		return working.questions.stream().mapToInt(question -> question.marks).sum();
	}

	private List<PackageRef> packagesOf(UUID examId) {
		List<PackageRef> refs = new ArrayList<>();
		for (UUID moduleId : memberships.activeModuleIds(examId)) {
			modules.findByIdAndDeletedAtIsNull(moduleId)
				.ifPresent(module -> refs.add(new PackageRef(module.getId(), module.getTitleEn(), module.getTitleBn())));
		}
		return refs;
	}

	private InstructorRef instructorOf(LiveExam exam) {
		if (exam.getInstructorId() == null) {
			return null;
		}
		String name = adminProfiles.findById(exam.getInstructorId()).map(profile -> profile.getFullName()).orElse(null);
		return new InstructorRef(exam.getInstructorId(), name);
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
				question.getPosition(), optionViews, question.isMultipleAnswer());
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
		// The package is shown for display only: the one the exam joined first, or none for a standalone exam.
		LiveExamModule module = memberships.primaryModuleId(exam.getId()).flatMap(modules::findByIdAndDeletedAtIsNull)
			.orElse(null);
		return new RoutineView(exam.getId(), module == null ? null : module.getId(), exam.getExamType(), exam.getTitleEn(),
				exam.getTitleBn(), module == null ? null : module.getTitleEn(), module == null ? null : module.getTitleBn(),
				categoryPath(exam.getCategoryId(), categoriesById), format(exam.getLiveAt()), format(end(exam)),
				exam.getDurationMinutes(), status.status(), status.retakeAvailable());
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

	/** The window end as it is stored. Read it; {@link #windowEnd(LiveExam)} computes it from the start and duration. */
	private static Instant end(LiveExam exam) {
		return exam.getEndsAt();
	}

	/** The window end from the start and duration: {@code liveAt + duration}, or null when there is no start. */
	private static Instant windowEnd(LiveExam exam) {
		if (exam.getLiveAt() == null) {
			return null;
		}
		return exam.getLiveAt().plus(Duration.ofMinutes(exam.getDurationMinutes()));
	}

	private UUID defaultInstructor(UUID moduleId) {
		return instructors.findById(moduleId).map(LiveExamInstructor::getInstructorId).orElse(null);
	}

	/** A paper version for the exam's current question PDF, written by {@code actorId}. */
	private LiveExamQuestionPaper paperVersion(UUID examId, int version, LiveExam exam, UUID actorId) {
		LiveExamQuestionPaper paper = new LiveExamQuestionPaper();
		paper.setExamId(examId);
		paper.setVersion(version);
		paper.setStoredFileName(exam.getQuestionStoredFileName());
		paper.setOriginalFileName(exam.getQuestionOriginalFileName());
		paper.setByteSize(exam.getQuestionByteSize());
		paper.setContentType(exam.getQuestionContentType());
		paper.setCreatedAt(clock.instant());
		paper.setCreatedBy(actorId);
		return paper;
	}

	private static String format(Instant instant) {
		if (instant == null) {
			return null;
		}
		return DHAKA_TIME.format(instant.atZone(DHAKA));
	}

	/** Students see a published exam whose home category branch is open. A package never hides an exam (contracts section 8). */
	private boolean visibleToStudents(LiveExam exam) {
		return !visibility.branchHidden(exam.getCategoryId());
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

	/** An archived exam cannot be changed. Staff reads still use {@link #require(UUID)}. */
	private LiveExam requireEditable(UUID id) {
		LiveExam exam = require(id);
		if (LiveExam.ARCHIVED.equals(exam.getStatus())) {
			throw new ApiException(ErrorCode.EXAM_ARCHIVED, HttpStatus.CONFLICT, "An archived exam cannot be changed.");
		}
		return exam;
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
		if (node == null || !node.isIntegralNumber() || node.intValue() < MIN_DURATION_MINUTES) {
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

	/** Zero or more marks taken off a wrong answer, to two decimals. Null (or zero) means no negative marking. */
	private static BigDecimal readNegativeMarks(JsonNode node) {
		if (node == null || node.isNull()) {
			return null;
		}
		BigDecimal value;
		if (node.isNumber()) {
			value = node.decimalValue();
		}
		else {
			try {
				value = new BigDecimal(node.asText().trim());
			}
			catch (NumberFormatException exception) {
				throw validationFailed();
			}
		}
		if (value.signum() < 0 || value.compareTo(MAX_NEGATIVE_MARKS) > 0 || value.stripTrailingZeros().scale() > 2) {
			throw validationFailed();
		}
		return value.setScale(2, RoundingMode.UNNECESSARY);
	}

	private static boolean readBoolean(JsonNode body, String field) {
		JsonNode node = body.get(field);
		if (node == null || !node.isBoolean()) {
			throw validationFailed();
		}
		return node.booleanValue();
	}

	private static Integer readOptionalInt(JsonNode body, String field) {
		JsonNode node = body.get(field);
		if (node == null || node.isNull()) {
			return null;
		}
		if (!node.isIntegralNumber()) {
			throw validationFailed();
		}
		return node.intValue();
	}

	private static String readMode(JsonNode body) {
		String mode = requiredText(body, "mode");
		if (!LiveExam.MODE_LIVE.equals(mode) && !LiveExam.MODE_PRACTICE.equals(mode)) {
			throw validationFailed();
		}
		return mode;
	}

	private static UUID readId(JsonNode body, String field) {
		JsonNode node = body == null ? null : body.get(field);
		if (node == null || node.isNull()) {
			throw validationFailed();
		}
		try {
			return UUID.fromString(node.asText().trim());
		}
		catch (IllegalArgumentException exception) {
			throw validationFailed();
		}
	}

	private static int readMarks(JsonNode body) {
		JsonNode node = body.get("marks");
		if (node == null || !node.isIntegralNumber() || node.intValue() <= 0) {
			throw validationFailed();
		}
		return node.intValue();
	}

	/** BN, EN or BOTH; absent or null is no paper language at all (D5, C2). */
	private static String readPaperLanguage(JsonNode body) {
		JsonNode node = body.get("paperLanguage");
		if (node == null || node.isNull()) {
			return null;
		}
		String language = node.asText();
		if (!LiveExam.BN.equals(language) && !LiveExam.EN.equals(language) && !LiveExam.BOTH.equals(language)) {
			throw validationFailed();
		}
		return language;
	}

	/** A create's rich text may be left out or blank; it is stored as an empty string, because the column is not null. */
	private static String textOrBlank(JsonNode body, String field) {
		JsonNode node = body.get(field);
		return node == null || node.isNull() ? "" : node.asText().trim();
	}

	private static String requiredText(JsonNode body, String field) {
		JsonNode node = body.get(field);
		if (node == null || node.isNull() || node.asText().isBlank()) {
			throw validationFailed();
		}
		return node.asText().trim();
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

	public record PackageRef(UUID id, String titleEn, String titleBn) {
	}

	public record InstructorRef(UUID id, String name) {
	}

	/**
	 * The exam as staff or a student reads it. {@code moduleId} is the first package the exam joined, for display; it is
	 * null for a standalone exam. {@code pendingChanges}, {@code instructor} are staff only; {@code access},
	 * {@code attempts} and {@code myStatusDetail} are student only.
	 */
	public record ExamView(UUID id, UUID moduleId, String examType, String paperLanguage, String titleEn, String titleBn,
			String descriptionEn, String descriptionBn, String rulesEn, String rulesBn, String liveAt, String endAt,
			int durationMinutes, Integer passPercent, int maximum, String status, boolean finallySaved, BigDecimal price,
			Boolean questionPdf, List<QuestionView> questions, String myStatus,
			Boolean retakeAvailable, String answersReleasedAt, String mode, String phase, UUID categoryId,
			List<PackageRef> packages, InstructorRef instructor, BigDecimal negativeMarks, boolean answersChangeable,
			boolean previewEnabled, boolean popular, Integer accessDays, PriceView pricing, Boolean pendingChanges,
			AccessView access, Integer attempts, String myStatusDetail, String coverUrl) {
	}

	/** A student's access to one exam. {@code endsAt} is an ISO-8601 instant, null when access never ends. */
	public record AccessView(boolean granted, String source, String endsAt, boolean expired) {
	}

	public record QuestionView(UUID id, String stem, int marks, String explanation, int position, List<OptionView> options,
			boolean multipleAnswer) {
	}

	public record OptionView(UUID id, String text, boolean correct, int position) {
	}

}
