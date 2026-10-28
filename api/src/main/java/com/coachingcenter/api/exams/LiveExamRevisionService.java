package com.coachingcenter.api.exams;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

import tools.jackson.databind.ObjectMapper;

/**
 * Staged edits to a published exam (NR-01, E31 to E34). A published exam stays as it is while staff edit it: the edits are
 * kept as a revision, the full proposed state of the exam, until the Master Admin reads the change list and publishes
 * them. Students, the sitting and the scoring read the published rows throughout (R20).
 *
 * <p>
 * Draft exams take no revision; their edits go straight to the rows.
 */
@Service
public class LiveExamRevisionService {

	private static final Logger LOG = LoggerFactory.getLogger(LiveExamRevisionService.class);

	private final LiveExamRepository exams;

	private final LiveExamRevisionRepository revisions;

	private final LiveExamModuleRevisionRepository moduleRevisions;

	private final LiveExamModuleRepository modules;

	private final LiveExamQuestionRepository questions;

	private final LiveExamOptionRepository options;

	private final LiveExamQuestionPaperRepository papers;

	private final LiveExamParticipantRepository participants;

	private final LiveExamChangeLogRepository changeLog;

	private final ExamChangeListBuilder builder;

	private final McqRescorer rescorer;

	private final StudentNoticeWriter notices;

	private final AfterCommit afterCommit;

	private final AuditRecorder audit;

	private final LiveExamCatalogVisibility visibility;

	private final Clock clock;

	private final ObjectMapper json;

	public LiveExamRevisionService(LiveExamRepository exams, LiveExamRevisionRepository revisions,
			LiveExamModuleRevisionRepository moduleRevisions, LiveExamModuleRepository modules,
			LiveExamQuestionRepository questions, LiveExamOptionRepository options,
			LiveExamQuestionPaperRepository papers, LiveExamParticipantRepository participants,
			LiveExamChangeLogRepository changeLog, ExamChangeListBuilder builder, McqRescorer rescorer,
			StudentNoticeWriter notices, AfterCommit afterCommit, AuditRecorder audit,
			LiveExamCatalogVisibility visibility, Clock clock, ObjectMapper json) {
		this.exams = exams;
		this.revisions = revisions;
		this.moduleRevisions = moduleRevisions;
		this.modules = modules;
		this.questions = questions;
		this.options = options;
		this.papers = papers;
		this.participants = participants;
		this.changeLog = changeLog;
		this.builder = builder;
		this.rescorer = rescorer;
		this.notices = notices;
		this.afterCommit = afterCommit;
		this.audit = audit;
		this.visibility = visibility;
		this.clock = clock;
		this.json = json;
	}

	/** The published state of an exam, read from its rows. {@code withQuestions} is false when only the settings matter. */
	ExamState published(LiveExam exam, boolean withQuestions) {
		ExamState state = ExamState.of(exam);
		if (withQuestions && LiveExam.MCQ.equals(exam.getExamType())) {
			state.questions = questionsOf(exam.getId());
		}
		return state;
	}

	/** What staff see and edit: the revision when one is waiting, else the published state. */
	ExamState working(LiveExam exam) {
		return revisions.findByExamId(exam.getId()).map(revision -> read(revision.getPayload()))
			.orElseGet(() -> published(exam, true));
	}

	/** The waiting revision as a state, or empty when nothing is staged. Staff reads use it for the working copy. */
	Optional<ExamState> staged(LiveExam exam) {
		return revisions.findByExamId(exam.getId()).map(revision -> read(revision.getPayload()));
	}

	boolean hasRevision(UUID examId) {
		return revisions.findByExamId(examId).isPresent();
	}

	/**
	 * Keeps {@code state} as the exam's staged edit. A state that equals the published one drops the revision instead, so an
	 * edit that was undone leaves nothing waiting.
	 */
	void stage(LiveExam exam, ExamState state, UUID actorId) {
		ChangeList changes = builder.build("", published(exam, true), state, 0, 0);
		Optional<LiveExamRevision> existing = revisions.findByExamId(exam.getId());
		if (ExamChangeListBuilder.isEmpty(changes)) {
			existing.ifPresent(revisions::delete);
			return;
		}
		Instant now = clock.instant();
		LiveExamRevision revision = existing.orElseGet(() -> {
			LiveExamRevision created = new LiveExamRevision();
			created.setExamId(exam.getId());
			created.setCreatedBy(actorId);
			created.setCreatedAt(now);
			return created;
		});
		revision.setPayload(write(state));
		revision.setUpdatedBy(actorId);
		revision.setUpdatedAt(now);
		revisions.save(revision);
	}

	/** The change list for the waiting revision, or not found when nothing is staged. */
	@Transactional(readOnly = true)
	public ChangeList changeList(UUID examId) {
		LiveExam exam = exam(examId);
		LiveExamRevision revision = revisions.findByExamId(examId).orElseThrow(this::notFound);
		return changes(exam, revision);
	}

	@Transactional
	public void discard(UUID examId) {
		exam(examId);
		LiveExamRevision revision = revisions.findByExamId(examId).orElseThrow(this::noPending);
		revisions.delete(revision);
		audit.record(AuditRecorder.LIVE_EXAM_REVISION_DISCARD, AuditRecorder.LIVE_EXAM_ENTITY, examId);
	}

	private ChangeList changes(LiveExam exam, LiveExamRevision revision) {
		ExamState before = published(exam, true);
		ExamState after = read(revision.getPayload());
		return builder.build(token(revision), before, after, submittedMcq(exam), exams.findActiveStudentsWithAccessToExam(exam.getId()).size());
	}

	private int submittedMcq(LiveExam exam) {
		if (!LiveExam.MCQ.equals(exam.getExamType())) {
			return 0;
		}
		int count = 0;
		for (LiveExamParticipant participant : participants.findByExamIdAndDeletedAtIsNull(exam.getId())) {
			if (LiveExamParticipant.SUBMITTED.equals(participant.getStatus())) {
				count++;
			}
		}
		return count;
	}

	/**
	 * Publishes the waiting revision of {@code exam}, which the caller has locked. A missing {@code revisionId} is the
	 * current website's publish button: it is accepted and logged, and goes away when the new UI sends the id.
	 */
	ChangeList publish(LiveExam exam, String revisionId, UUID actorId) {
		LiveExamRevision revision = revisions.findByExamId(exam.getId()).orElseThrow(this::noPending);
		String token = token(revision);
		if (revisionId != null && !token.equals(revisionId)) {
			throw new ApiException(ErrorCode.PENDING_CHANGES_STALE, HttpStatus.CONFLICT,
					"The staged changes have changed since you read them. Read them again before publishing.");
		}
		if (revisionId == null) {
			LOG.warn("Exam {} published a staged edit without a revisionId (compatibility rule 4).", exam.getId());
		}
		ExamState before = published(exam, true);
		ExamState after = read(revision.getPayload());
		requirePublishable(exam, after);
		List<UUID> recipients = exams.findActiveStudentsWithAccessToExam(exam.getId());
		ChangeList changes = builder.build(token, before, after, submittedMcq(exam), recipients.size());
		if (structureChanged(changes, before, after) && participants.existsByExamId(exam.getId())) {
			throw new ApiException(ErrorCode.QUESTION_STRUCTURE_LOCKED, HttpStatus.CONFLICT,
					"A student has started this exam since these edits were staged. Questions and options can no longer be added or removed; discard the staged changes and edit again.");
		}
		apply(exam, after);
		exams.flush();
		int rescored = 0;
		if (LiveExam.MCQ.equals(exam.getExamType()) && ExamChangeListBuilder.scoringChanged(changes)) {
			questions.flush();
			options.flush();
			rescored = rescorer.rescore(exam.getId());
		}
		LiveExamChangeLog entry = new LiveExamChangeLog();
		entry.setExamId(exam.getId());
		entry.setChanges(write(changes));
		entry.setRescored(rescored);
		entry.setPublishedBy(actorId);
		entry.setPublishedAt(clock.instant());
		changeLog.save(entry);
		audit.record(AuditRecorder.LIVE_EXAM_REVISION_PUBLISH, AuditRecorder.LIVE_EXAM_ENTITY, exam.getId());
		revisions.delete(revision);
		if (changes.impact().notifiesStudents()) {
			UUID examId = exam.getId();
			afterCommit.run(() -> notices.examUpdated(examId, recipients));
		}
		return changes;
	}

	/** The checks a first publish makes, applied to any state of the exam. */
	void requirePublishable(LiveExam exam, ExamState state) {
		if (state.passPercent == null) {
			throw new ApiException(ErrorCode.PASS_PERCENT_REQUIRED, HttpStatus.CONFLICT,
					"Publish requires a pass percent from 0 through 100.");
		}
		if (blank(state.descriptionEn) || blank(state.descriptionBn) || blank(state.rulesEn) || blank(state.rulesBn)) {
			throw new ApiException(ErrorCode.EXAM_TEXT_REQUIRED, HttpStatus.CONFLICT,
					"Publish requires the English and Bangla descriptions and rules.");
		}
		if (LiveExam.MCQ.equals(exam.getExamType())) {
			if (state.questions.isEmpty()) {
				throw new ApiException(ErrorCode.QUESTIONS_REQUIRED, HttpStatus.CONFLICT,
						"An MCQ exam needs at least one question.");
			}
		}
		else if (state.writtenMaximum == null || state.paperId == null) {
			throw new ApiException(ErrorCode.WRITTEN_PAPER_REQUIRED, HttpStatus.CONFLICT,
					"A written exam needs a maximum and a question PDF.");
		}
		ExamWindow.validate(state.mode, ExamState.instant(state.liveAt), ExamState.instant(state.endsAt),
				state.durationMinutes);
		ExamPricing.validate(state.price, state.free, state.discountPercent, ExamState.instant(state.discountEndsAt));
		// An ended window is fine: archived participants can still sit the exam (D20). Windows may overlap (D17).
		visibility.requireOpen(exam.getCategoryId());
	}

	/** True when the edit adds or removes a question or changes the number of options in one. */
	private static boolean structureChanged(ChangeList changes, ExamState before, ExamState after) {
		for (QuestionChange change : changes.questions()) {
			if ("ADDED".equals(change.change()) || "REMOVED".equals(change.change())) {
				return true;
			}
		}
		for (ExamState.QuestionState question : after.questions) {
			ExamState.QuestionState earlier = before.question(question.id);
			if (earlier != null && earlier.options.size() != question.options.size()) {
				return true;
			}
		}
		return false;
	}

	private void apply(LiveExam exam, ExamState after) {
		after.applySettingsTo(exam);
		if (LiveExam.WRITTEN.equals(exam.getExamType())) {
			exam.setQuestionPaperId(after.paperId);
			mirrorPaper(exam, after.paperId == null ? null : papers.findById(after.paperId).orElse(null));
		}
		else {
			syncQuestions(exam.getId(), after);
		}
	}

	/** The legacy {@code question_*} columns follow the published paper until a later migration drops them. */
	private static void mirrorPaper(LiveExam exam, LiveExamQuestionPaper paper) {
		exam.setQuestionStoredFileName(paper == null ? null : paper.getStoredFileName());
		exam.setQuestionOriginalFileName(paper == null ? null : paper.getOriginalFileName());
		exam.setQuestionByteSize(paper == null ? null : paper.getByteSize());
		exam.setQuestionContentType(paper == null ? null : paper.getContentType());
	}

	private void syncQuestions(UUID examId, ExamState after) {
		Instant now = clock.instant();
		Map<UUID, LiveExamQuestion> rows = new HashMap<>();
		for (LiveExamQuestion row : questions.findByExamIdAndDeletedAtIsNullOrderByPositionAscIdAsc(examId)) {
			rows.put(row.getId(), row);
		}
		for (ExamState.QuestionState state : after.questions) {
			LiveExamQuestion row = rows.remove(state.id);
			if (row == null) {
				row = new LiveExamQuestion();
				row.setExamId(examId);
			}
			row.setStem(state.stem);
			row.setMarks(state.marks);
			row.setExplanation(state.explanation);
			row.setPosition(state.position);
			row.setMultipleAnswer(state.multipleAnswer);
			row = questions.save(row);
			syncOptions(row.getId(), state);
		}
		for (LiveExamQuestion removed : rows.values()) {
			for (LiveExamOption option : options.findByQuestionIdAndDeletedAtIsNullOrderByPositionAscIdAsc(removed.getId())) {
				option.setDeletedAt(now);
			}
			removed.setDeletedAt(now);
		}
	}

	private void syncOptions(UUID questionId, ExamState.QuestionState state) {
		Map<UUID, LiveExamOption> rows = new HashMap<>();
		for (LiveExamOption row : options.findByQuestionIdAndDeletedAtIsNullOrderByPositionAscIdAsc(questionId)) {
			rows.put(row.getId(), row);
		}
		for (ExamState.OptionState option : state.options) {
			LiveExamOption row = rows.remove(option.id);
			if (row == null) {
				row = new LiveExamOption();
				row.setQuestionId(questionId);
			}
			row.setOptionText(option.text);
			row.setCorrect(option.correct);
			row.setPosition(option.position);
			options.save(row);
		}
		options.deleteAll(rows.values());
	}

	private List<ExamState.QuestionState> questionsOf(UUID examId) {
		List<LiveExamQuestion> rows = questions.findByExamIdAndDeletedAtIsNullOrderByPositionAscIdAsc(examId);
		Map<UUID, List<LiveExamOption>> byQuestion = new HashMap<>();
		if (!rows.isEmpty()) {
			List<UUID> ids = rows.stream().map(LiveExamQuestion::getId).toList();
			for (LiveExamOption option : options.findByQuestionIdInAndDeletedAtIsNullOrderByPositionAscIdAsc(ids)) {
				byQuestion.computeIfAbsent(option.getQuestionId(), ignored -> new ArrayList<>()).add(option);
			}
		}
		List<ExamState.QuestionState> states = new ArrayList<>();
		for (LiveExamQuestion row : rows) {
			ExamState.QuestionState state = new ExamState.QuestionState();
			state.id = row.getId();
			state.stem = row.getStem();
			state.marks = row.getMarks();
			state.explanation = row.getExplanation();
			state.position = row.getPosition();
			state.multipleAnswer = row.isMultipleAnswer();
			for (LiveExamOption option : byQuestion.getOrDefault(row.getId(), List.of())) {
				ExamState.OptionState optionState = new ExamState.OptionState();
				optionState.id = option.getId();
				optionState.text = option.getOptionText();
				optionState.correct = option.isCorrect();
				optionState.position = option.getPosition();
				state.options.add(optionState);
			}
			states.add(state);
		}
		return states;
	}

	/** Every exam and package with staged changes, newest first, for the "changes waiting" badge (E32). */
	@Transactional(readOnly = true)
	public PendingChanges pending() {
		List<PendingItem> items = new ArrayList<>();
		int examCount = 0;
		for (LiveExamRevision revision : revisions.findAllByOrderByUpdatedAtDesc()) {
			Optional<LiveExam> exam = exams.findByIdAndDeletedAtIsNull(revision.getExamId());
			if (exam.isPresent()) {
				examCount++;
				items.add(new PendingItem("EXAM", exam.get().getId(), exam.get().getTitleEn(), exam.get().getTitleBn(),
						revision.getUpdatedAt(), revision.getUpdatedBy()));
			}
		}
		int packageCount = 0;
		for (LiveExamModuleRevision revision : moduleRevisions.findAllByOrderByUpdatedAtDesc()) {
			Optional<LiveExamModule> module = modules.findByIdAndDeletedAtIsNull(revision.getModuleId());
			if (module.isPresent()) {
				packageCount++;
				items.add(new PendingItem("PACKAGE", module.get().getId(), module.get().getTitleEn(),
						module.get().getTitleBn(), revision.getUpdatedAt(), revision.getUpdatedBy()));
			}
		}
		items.sort(Comparator.comparing(PendingItem::updatedAt).reversed());
		return new PendingChanges(examCount, packageCount, items);
	}

	/** Counts and rows of the badge. {@code updatedAt} is an ISO-8601 instant. */
	public record PendingChanges(int exams, int packages, List<PendingItem> items) {
	}

	public record PendingItem(String kind, UUID id, String titleEn, String titleBn, Instant updatedAt, UUID updatedBy) {
	}

	/**
	 * Names the revision and its content: it changes whenever the staged state does, so a Master Admin who read the change
	 * list cannot publish edits made after it.
	 */
	String token(LiveExamRevision revision) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(revision.getPayload().getBytes(StandardCharsets.UTF_8));
			return revision.getId() + "-" + HexFormat.of().formatHex(digest, 0, 8);
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private ExamState read(String payload) {
		return json.readValue(payload, ExamState.class);
	}

	private String write(Object value) {
		return json.writeValueAsString(value);
	}

	private LiveExam exam(UUID examId) {
		return exams.findByIdAndDeletedAtIsNull(examId).orElseThrow(this::notFound);
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}

	private ApiException noPending() {
		return new ApiException(ErrorCode.NO_PENDING_CHANGES, HttpStatus.CONFLICT, "There are no staged changes.");
	}

	private ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

}
