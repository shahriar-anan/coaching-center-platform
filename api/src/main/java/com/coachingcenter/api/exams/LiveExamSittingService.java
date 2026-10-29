package com.coachingcenter.api.exams;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.coachingcenter.api.auth.StudentPhoneGuard;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.files.LocalFileStorage;

import tools.jackson.databind.JsonNode;

@Service
public class LiveExamSittingService {

	static final long MAX_PDF_BYTES = 80L * 1024 * 1024;

	private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

	private static final DateTimeFormatter DHAKA_TIME = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

	private final LiveExamService exams;

	private final LiveExamRepository examRepository;

	private final LiveExamQuestionRepository questions;

	private final LiveExamOptionRepository options;

	private final LiveExamParticipantRepository participants;

	private final LiveExamAnswerRepository answers;

	private final LiveExamAnswerConfirmationRepository confirmations;

	private final LiveExamQuestionPaperRepository papers;

	private final LiveExamRetakeGrantRepository retakeGrants;

	private final LiveExamAccess access;

	private final LiveExamLeaderboardService leaderboards;

	private final LiveExamStudentStanding standings;

	private final LocalFileStorage files;

	private final PdfCompressor pdfs;

	private final Clock clock;

	private final StudentPhoneGuard phones;

	private final StudentNoticeWriter notices;

	public LiveExamSittingService(LiveExamService exams, LiveExamRepository examRepository,
			LiveExamQuestionRepository questions, LiveExamOptionRepository options,
			LiveExamParticipantRepository participants, LiveExamAnswerRepository answers,
			LiveExamAnswerConfirmationRepository confirmations, LiveExamQuestionPaperRepository papers,
			LiveExamRetakeGrantRepository retakeGrants, LiveExamAccess access, LiveExamLeaderboardService leaderboards,
			LiveExamStudentStanding standings, LocalFileStorage files, PdfCompressor pdfs, Clock clock,
			StudentPhoneGuard phones, StudentNoticeWriter notices) {
		this.exams = exams;
		this.examRepository = examRepository;
		this.questions = questions;
		this.options = options;
		this.participants = participants;
		this.answers = answers;
		this.confirmations = confirmations;
		this.papers = papers;
		this.retakeGrants = retakeGrants;
		this.access = access;
		this.leaderboards = leaderboards;
		this.standings = standings;
		this.files = files;
		this.pdfs = pdfs;
		this.clock = clock;
		this.phones = phones;
		this.notices = notices;
	}

	@Transactional
	public StudentExam studentExam(UUID examId, UUID studentId) {
		LiveExamService.ExamView shell = exams.get(examId, false, studentId);
		LiveExam exam = requireExam(examId);
		LiveExamParticipant participant = latest(examId, studentId).orElse(null);
		Instant now = clock.instant();
		if (participant != null && !now.isBefore(participant.getDeadlineAt())) {
			postIfDue(participant, exam);
		}
		LiveExamStudentStanding.Standing standing = standings.of(studentId, exam, now);
		boolean posted = participant != null && participant.getSubmittedAt() != null;
		boolean open = participant != null && now.isBefore(participant.getDeadlineAt()) && !posted;
		boolean mcq = LiveExam.MCQ.equals(exam.getExamType());
		List<StudentQuestion> questionViews = null;
		Boolean questionPdf = null;
		// A finished MCQ attempt shows the key and the explanations at once (D21), while a running one shows neither.
		if (participant != null && mcq && (open || posted)) {
			questionViews = questionViews(examId, participant.getId(), posted);
		}
		if (open && LiveExam.WRITTEN.equals(exam.getExamType()) && paperFile(exam, participant.getQuestionPaperId()) != null) {
			questionPdf = true;
		}
		AccessState state = standing.access();
		return new StudentExam(shell.id(), shell.moduleId(), shell.examType(), shell.paperLanguage(), shell.titleEn(),
				shell.titleBn(), shell.descriptionEn(), shell.descriptionBn(), shell.rulesEn(), shell.rulesBn(),
				shell.liveAt(), shell.endAt(), shell.durationMinutes(), shell.passPercent(), shell.maximum(),
				shell.status(), shell.finallySaved(), shell.price(), questionPdf, questionViews,
				standing.status().status(), standing.status().retakeAvailable(), sittingView(participant),
				participant != null && participant.getAnswerStoredFileName() != null, standing.status().detail(),
				standing.attempts(),
				new AccessView(state.granted(), state.source(), state.endsAt() == null ? null : state.endsAt().toString(),
						state.expired()), shell.coverUrl());
	}

	/** The first attempt. A student with an open attempt gets it back; one with a finished attempt must use {@link #retake}. */
	@Transactional
	public TakeView take(UUID examId, UUID studentId, JsonNode ignored) {
		phones.require(studentId);
		exams.get(examId, false, studentId);
		lockExam(examId);
		return firstAttempt(requireExam(examId), studentId);
	}

	/**
	 * A later attempt. MCQ: any number, once the previous attempt is finished. Written: one, with a retake grant. A student
	 * who never started an MCQ exam whose window has ended gets their first (archived) attempt here, as the current web
	 * sends them (contracts §7.9).
	 */
	@Transactional
	public TakeView retake(UUID examId, UUID studentId) {
		phones.require(studentId);
		exams.get(examId, false, studentId);
		lockExam(examId);
		LiveExam exam = requireExam(examId);
		boolean written = LiveExam.WRITTEN.equals(exam.getExamType());
		List<LiveExamParticipant> attempts = participants
			.findByExamIdAndStudentIdAndDeletedAtIsNullOrderByAttemptNoDesc(examId, studentId);
		Instant now = clock.instant();
		if (attempts.isEmpty()) {
			if (written) {
				throw retakeNotAllowed("A written exam has no later attempt until one is granted.");
			}
			String phase = ExamWindow.phase(exam, now);
			if (ExamWindow.ENDED.equals(phase)) {
				return firstAttempt(exam, studentId);
			}
			if (ExamWindow.PRACTICE.equals(phase)) {
				throw retakeNotAllowed("Take the exam first.");
			}
			throw notOpen();
		}
		LiveExamParticipant latest = attempts.get(0);
		LiveExamRetakeGrant grant = null;
		if (written) {
			grant = retakeGrants.findActive(examId, studentId).filter(found -> found.getUsedParticipantId() == null)
				.orElseThrow(() -> retakeNotAllowed("A written exam has one retake, and only when staff grant it."));
		}
		// A sitting past its deadline counts as finished even before the deadline job has posted it.
		postIfDue(latest, exam);
		if (LiveExamParticipant.IN_PROGRESS.equals(latest.getStatus())) {
			throw new ApiException(ErrorCode.ATTEMPT_IN_PROGRESS, HttpStatus.CONFLICT, "An attempt is already in progress.");
		}
		requireSittable(exam, studentId, now);
		if (written && attempts.size() > 1) {
			throw retakeNotAllowed("A written exam has one retake.");
		}
		LiveExamParticipant participant = start(exam, studentId, LiveExamParticipant.RETAKE, latest.getAttemptNo() + 1, now);
		if (grant != null) {
			grant.setUsedParticipantId(participant.getId());
			retakeGrants.save(grant);
		}
		return takeView(participant);
	}

	/** Replaces the whole selection with the payload (the questions it leaves out are cleared). */
	@Transactional
	public void saveAnswers(UUID examId, UUID studentId, JsonNode body) {
		exams.get(examId, false, studentId);
		LiveExam exam = requireExam(examId);
		requireMcq(exam);
		LiveExamParticipant participant = answerable(examId, studentId);
		JsonNode selected = body.get("answers");
		if (selected == null || !selected.isArray()) {
			throw validationFailed();
		}
		Map<UUID, Set<UUID>> requested = new LinkedHashMap<>();
		for (JsonNode answer : selected) {
			UUID questionId = readId(answer, "questionId");
			if (requested.put(questionId, optionIds(answer)) != null) {
				throw validationFailed();
			}
		}
		// A question the payload names counts as confirmed (R9); one it leaves out is cleared unless it is final.
		apply(exam, participant, requested, new HashSet<>(requested.keySet()), true);
	}

	/** One question's selection. {@code confirm} makes a multi-answer question final when "answers can be changed" is off. */
	@Transactional
	public SavedAnswer saveAnswer(UUID examId, UUID studentId, UUID questionId, JsonNode body) {
		exams.get(examId, false, studentId);
		LiveExam exam = requireExam(examId);
		requireMcq(exam);
		LiveExamParticipant participant = answerable(examId, studentId);
		if (body == null || !body.isObject()) {
			throw validationFailed();
		}
		Set<UUID> chosen = optionIds(body);
		boolean confirm = body.has("confirm") && body.get("confirm").asBoolean();
		if (confirm && chosen.isEmpty()) {
			throw validationFailed();
		}
		Map<UUID, Set<UUID>> requested = new LinkedHashMap<>();
		requested.put(questionId, chosen);
		Set<UUID> confirmed = new HashSet<>();
		if (confirm) {
			confirmed.add(questionId);
		}
		apply(exam, participant, requested, confirmed, false);
		boolean final_ = confirmations.existsById(new LiveExamAnswerConfirmation.Key(participant.getId(), questionId));
		return new SavedAnswer(questionId, List.copyOf(chosen), !exam.isAnswersChangeable() && final_);
	}

	@Transactional
	public PostedResult submit(UUID examId, UUID studentId) {
		exams.get(examId, false, studentId);
		LiveExam exam = requireExam(examId);
		LiveExamParticipant participant = latest(examId, studentId)
			.orElseThrow(LiveExamSittingService::notOpen);
		if (clock.instant().isBefore(participant.getStartedAt())) {
			throw notOpen();
		}
		if (clock.instant().isBefore(participant.getDeadlineAt())) {
			if (LiveExam.MCQ.equals(exam.getExamType()) && participant.getSubmittedAt() == null) {
				post(participant, exam);
			}
			return posted(participant, exam);
		}
		postIfDue(participant, exam);
		return posted(participant, exam);
	}

	/** The student's result. {@code attempt} picks one attempt by number; without it, the latest. */
	@Transactional
	public ReviewedResult result(UUID examId, UUID studentId, Integer attempt) {
		LiveExam exam = requireExam(examId);
		LiveExamParticipant participant = participants
			.findByExamIdAndStudentIdAndDeletedAtIsNullOrderByAttemptNoDesc(examId, studentId)
			.stream()
			.filter(found -> attempt == null || found.getAttemptNo() == attempt)
			.findFirst()
			.orElseThrow(this::notFound);
		if (clock.instant().isBefore(participant.getDeadlineAt())) {
			if (participant.getSubmittedAt() == null) {
				throw notOpen();
			}
		}
		else {
			postIfDue(participant, exam);
		}
		boolean mcq = LiveExam.MCQ.equals(exam.getExamType());
		// MCQ: the answers and explanations come with the result, as soon as it is posted (D21).
		List<StudentQuestion> questionViews = mcq && participant.getSubmittedAt() != null
				? questionViews(examId, participant.getId(), true) : null;
		BigDecimal score = McqScoring.plain(participant.getScore());
		Boolean passed = participant.getPassed();
		String comment = participant.getComment();
		Boolean evaluatedPdf = participant.getEvaluatedStoredFileName() == null ? null : Boolean.TRUE;
		if (LiveExam.WRITTEN.equals(exam.getExamType())
				&& !LiveExamParticipant.FAILED_TO_SUBMIT.equals(participant.getStatus())
				&& participant.getPublishedAt() == null) {
			score = null;
			passed = null;
			comment = null;
			evaluatedPdf = null;
		}
		LiveExamEvaluationService.RetakeGrantView grant = LiveExam.WRITTEN.equals(exam.getExamType())
				? retakeGrants.findActive(examId, studentId)
					.map(found -> new LiveExamEvaluationService.RetakeGrantView(found.getId(), found.getUsedParticipantId() != null))
					.orElse(null)
				: null;
		return new ReviewedResult(score, maximum(exam), passed, participant.getStatus(), questionViews, comment,
				evaluatedPdf, LiveExamParticipant.legacyKind(participant.getKind()), participant.getKind(),
				participant.getAttemptNo(), leaderboards.positionOf(exam, participant), grant);
	}

	/** The student's own attempts, oldest first. */
	@Transactional
	public List<AttemptView> attempts(UUID examId, UUID studentId) {
		LiveExam exam = requireExam(examId);
		if (!access.hasEver(studentId, exam)) {
			throw notFound();
		}
		List<AttemptView> views = new ArrayList<>();
		List<LiveExamParticipant> own = participants
			.findByExamIdAndStudentIdAndDeletedAtIsNullOrderByAttemptNoDesc(examId, studentId);
		for (int index = own.size() - 1; index >= 0; index--) {
			LiveExamParticipant participant = own.get(index);
			postIfDue(participant, exam);
			boolean written = LiveExam.WRITTEN.equals(exam.getExamType());
			boolean showScore = written
					? LiveExamParticipant.FAILED_TO_SUBMIT.equals(participant.getStatus()) || participant.getPublishedAt() != null
					: participant.getSubmittedAt() != null;
			views.add(new AttemptView(participant.getId(), participant.getAttemptNo(), participant.getKind(),
					format(participant.getStartedAt()), format(participant.getDeadlineAt()),
					participant.getSubmittedAt() == null ? null : format(participant.getSubmittedAt()), participant.getStatus(),
					showScore ? McqScoring.plain(participant.getScore()) : null, showScore ? participant.getPassed() : null));
		}
		return views;
	}

	@Transactional
	public void settleParticipant(UUID participantId) {
		LiveExamParticipant participant = participants.findById(participantId).orElseThrow(this::notFound);
		if (participant.getDeletedAt() != null) {
			throw notFound();
		}
		LiveExam exam = requireExam(participant.getExamId());
		if (!clock.instant().isBefore(participant.getDeadlineAt())) {
			postIfDue(participant, exam);
		}
	}

	@Transactional
	public void finalizeDue(UUID examId) {
		LiveExam exam = requireExam(examId);
		for (LiveExamParticipant participant : participants.findByExamIdAndDeletedAtIsNull(examId)) {
			if (!clock.instant().isBefore(participant.getDeadlineAt())) {
				postIfDue(participant, exam);
			}
		}
	}

	/** The question paper of the student's open sitting: the version they were given, not the exam's current one (E29). */
	@Transactional(readOnly = true)
	public byte[] questionPdf(UUID examId, UUID studentId) {
		exams.get(examId, false, studentId);
		LiveExam exam = requireExam(examId);
		LiveExamParticipant participant = latest(examId, studentId)
			.orElse(null);
		if (participant == null || !clock.instant().isBefore(participant.getDeadlineAt())) {
			throw notFound();
		}
		String stored = paperFile(exam, participant.getQuestionPaperId());
		if (stored == null) {
			throw notFound();
		}
		try {
			return java.nio.file.Files.readAllBytes(files.resolve(stored));
		}
		catch (IOException exception) {
			throw notFound();
		}
	}

	/**
	 * The stored file of a paper version. A sitting with no version recorded gets the exam's published paper, and an exam
	 * from before paper versions its own question columns. Null when the exam has no paper.
	 */
	String paperFile(LiveExam exam, UUID paperId) {
		UUID id = paperId != null ? paperId : exam.getQuestionPaperId();
		if (id != null) {
			Optional<LiveExamQuestionPaper> paper = papers.findById(id);
			if (paper.isPresent()) {
				return paper.get().getStoredFileName();
			}
		}
		return exam.getQuestionStoredFileName();
	}

	@Transactional
	public void uploadAnswer(UUID examId, UUID studentId, MultipartFile file) {
		exams.get(examId, false, studentId);
		LiveExam exam = requireExam(examId);
		if (!LiveExam.WRITTEN.equals(exam.getExamType())) {
			throw new ApiException(ErrorCode.EXAM_TYPE_MISMATCH, HttpStatus.CONFLICT, "Only a written exam accepts a PDF.");
		}
		LiveExamParticipant participant = openParticipant(examId, studentId);
		if (file == null || file.getSize() > MAX_PDF_BYTES) {
			throw new ApiException(ErrorCode.FILE_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE, "File is too large.");
		}
		byte[] original;
		try {
			original = file.getBytes();
		}
		catch (IOException exception) {
			throw validationFailed();
		}
		if (original.length < 5 || original[0] != '%' || original[1] != 'P' || original[2] != 'D' || original[3] != 'F') {
			throw validationFailed();
		}
		byte[] compressed = pdfs.compress(original);
		if (!clock.instant().isBefore(participant.getDeadlineAt())) {
			throw new ApiException(ErrorCode.DEADLINE_PASSED, HttpStatus.CONFLICT, "The sitting deadline has passed.");
		}
		// A written script is submitted once. A second upload would replace what the student already confirmed.
		if (participant.getAnswerStoredFileName() != null) {
			throw new ApiException(ErrorCode.ANSWER_ALREADY_SUBMITTED, HttpStatus.CONFLICT,
					"The answer script was already submitted.");
		}
		LocalFileStorage.StoredFile stored = files.save(file.getOriginalFilename(), "application/pdf",
				new ByteArrayInputStream(compressed), compressed.length, MAX_PDF_BYTES);
		participant.setAnswerStoredFileName(stored.storedFileName());
		participant.setAnswerOriginalFileName(stored.originalFileName());
		participant.setAnswerByteSize(stored.byteSize());
		participant.setAnswerContentType("application/pdf");
		files.commitReplacement(stored.storedFileName(), null);
	}

	private TakeView firstAttempt(LiveExam exam, UUID studentId) {
		Instant now = clock.instant();
		LiveExamParticipant existing = latest(exam.getId(), studentId).orElse(null);
		if (existing != null) {
			// An open attempt is handed back; a finished one means the student retakes instead.
			if (existing.getSubmittedAt() != null || !now.isBefore(existing.getDeadlineAt())) {
				throw notOpen();
			}
			return takeView(existing);
		}
		requireSittable(exam, studentId, now);
		if (!ExamWindow.canStartFirstAttempt(exam, now)) {
			throw notOpen();
		}
		// Live or archived is decided here and never again (R18): the kind is stored and nothing recomputes it.
		LiveExamParticipant participant = start(exam, studentId, ExamWindow.firstAttemptKind(exam, now), 1, now);
		return takeView(participant);
	}

	/** Holds access that has not ended. A student with no access at all gets a 404, as the exam read does. */
	private void requireSittable(LiveExam exam, UUID studentId, Instant now) {
		AccessState state = access.state(studentId, exam, now);
		if (!state.granted()) {
			throw notFound();
		}
		if (state.expired()) {
			throw new ApiException(ErrorCode.ACCESS_EXPIRED, HttpStatus.CONFLICT, "Your access to this exam has ended.");
		}
	}

	private void postIfDue(LiveExamParticipant participant, LiveExam exam) {
		if (clock.instant().isBefore(participant.getDeadlineAt())) {
			return;
		}
		if (participant.getSubmittedAt() == null) {
			post(participant, exam);
			return;
		}
		if (LiveExam.MCQ.equals(exam.getExamType())) {
			scoreMcq(participant, exam, false);
		}
	}

	private void post(LiveExamParticipant participant, LiveExam exam) {
		participant.setSubmittedAt(clock.instant());
		if (LiveExam.WRITTEN.equals(exam.getExamType())) {
			if (participant.getAnswerStoredFileName() == null) {
				participant.setStatus(LiveExamParticipant.FAILED_TO_SUBMIT);
				participant.setScore(BigDecimal.ZERO);
				participant.setPassed(false);
			}
			else {
				participant.setStatus(LiveExamParticipant.SUBMITTED);
			}
			return;
		}
		scoreMcq(participant, exam, true);
	}

	private void scoreMcq(LiveExamParticipant participant, LiveExam exam, boolean writeNotice) {
		Map<UUID, Set<UUID>> chosen = new HashMap<>();
		for (LiveExamAnswer answer : answers.findByParticipantId(participant.getId())) {
			chosen.computeIfAbsent(answer.getQuestionId(), ignored -> new HashSet<>()).add(answer.getOptionId());
		}
		List<McqScoring.QuestionKey> key = key(exam.getId());
		BigDecimal score = McqScoring.total(key, chosen, exam.getNegativeMarks()).setScale(2, RoundingMode.HALF_UP);
		participant.setScore(score);
		participant.setPassed(!chosen.isEmpty() && McqScoring.passed(score, McqScoring.maximum(key), exam.getPassPercent()));
		participant.setStatus(LiveExamParticipant.SUBMITTED);
		if (writeNotice) {
			notices.mcqScore(participant.getStudentId(), exam.getId(), participant.getId());
		}
	}

	private PostedResult posted(LiveExamParticipant participant, LiveExam exam) {
		BigDecimal score = McqScoring.plain(participant.getScore());
		Boolean passed = participant.getPassed();
		if (LiveExam.WRITTEN.equals(exam.getExamType())
				&& !LiveExamParticipant.FAILED_TO_SUBMIT.equals(participant.getStatus())
				&& participant.getPublishedAt() == null) {
			score = null;
			passed = null;
		}
		return new PostedResult(participant.getId(), score, maximum(exam), passed, participant.getStatus(),
				participant.getAttemptNo(), leaderboards.positionOf(exam, participant));
	}

	/** The paper's answer key, one entry per question in position order. */
	private List<McqScoring.QuestionKey> key(UUID examId) {
		List<McqScoring.QuestionKey> key = new ArrayList<>();
		for (LiveExamQuestion question : questions.findByExamIdAndDeletedAtIsNullOrderByPositionAscIdAsc(examId)) {
			Set<UUID> correct = new HashSet<>();
			for (LiveExamOption option : options.findByQuestionIdAndDeletedAtIsNullOrderByPositionAscIdAsc(question.getId())) {
				if (option.isCorrect()) {
					correct.add(option.getId());
				}
			}
			key.add(new McqScoring.QuestionKey(question.getId(), question.getMarks(), correct));
		}
		return key;
	}

	/** The student's latest attempt on the exam, or empty. */
	private Optional<LiveExamParticipant> latest(UUID examId, UUID studentId) {
		return participants.findByExamIdAndStudentIdAndDeletedAtIsNullOrderByAttemptNoDesc(examId, studentId).stream()
			.findFirst();
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

	/**
	 * {@code reveal} adds the correct options and explanations: only once the student's own attempt is posted (D21). The
	 * exam's "answers released" switch is no longer read (P-d). A student mid-sitting reads the published rows, so a reload
	 * shows new wording (R17).
	 */
	private List<StudentQuestion> questionViews(UUID examId, UUID participantId, boolean reveal) {
		Map<UUID, Set<UUID>> selected = new HashMap<>();
		for (LiveExamAnswer answer : answers.findByParticipantId(participantId)) {
			selected.computeIfAbsent(answer.getQuestionId(), ignored -> new HashSet<>()).add(answer.getOptionId());
		}
		List<LiveExamQuestion> rows = questions.findByExamIdAndDeletedAtIsNullOrderByPositionAscIdAsc(examId);
		Map<UUID, List<LiveExamOption>> optionsByQuestion = new HashMap<>();
		if (!rows.isEmpty()) {
			for (LiveExamOption option : options.findByQuestionIdInAndDeletedAtIsNullOrderByPositionAscIdAsc(
					rows.stream().map(LiveExamQuestion::getId).toList())) {
				optionsByQuestion.computeIfAbsent(option.getQuestionId(), ignored -> new ArrayList<>()).add(option);
			}
		}
		List<StudentQuestion> views = new ArrayList<>();
		for (LiveExamQuestion question : rows) {
			Set<UUID> chosen = selected.getOrDefault(question.getId(), Set.of());
			List<StudentOption> optionViews = new ArrayList<>();
			for (LiveExamOption option : optionsByQuestion.getOrDefault(question.getId(), List.of())) {
				optionViews.add(new StudentOption(option.getId(), option.getOptionText(), reveal ? option.isCorrect() : null,
						chosen.contains(option.getId())));
			}
			views.add(new StudentQuestion(question.getId(), question.getStem(), question.getMarks(),
					reveal ? question.getExplanation() : null, optionViews, question.isMultipleAnswer()));
		}
		return views;
	}

	/**
	 * Validates the payload against the paper and the "answers can be changed" rule (R9), then stores it. {@code
	 * replaceMissing} clears the questions the payload leaves out, except the ones that are final.
	 */
	private void apply(LiveExam exam, LiveExamParticipant participant, Map<UUID, Set<UUID>> requested, Set<UUID> confirm,
			boolean replaceMissing) {
		Map<UUID, LiveExamQuestion> byId = new HashMap<>();
		for (LiveExamQuestion question : questions.findByExamIdAndDeletedAtIsNullOrderByPositionAscIdAsc(exam.getId())) {
			byId.put(question.getId(), question);
		}
		Map<UUID, Set<UUID>> optionIdsByQuestion = new HashMap<>();
		if (!byId.isEmpty()) {
			for (LiveExamOption option : options.findByQuestionIdInAndDeletedAtIsNullOrderByPositionAscIdAsc(List.copyOf(byId.keySet()))) {
				optionIdsByQuestion.computeIfAbsent(option.getQuestionId(), ignored -> new HashSet<>()).add(option.getId());
			}
		}
		for (Map.Entry<UUID, Set<UUID>> entry : requested.entrySet()) {
			LiveExamQuestion question = byId.get(entry.getKey());
			if (question == null || !optionIdsByQuestion.getOrDefault(entry.getKey(), Set.of()).containsAll(entry.getValue())
					|| (!question.isMultipleAnswer() && entry.getValue().size() > 1)) {
				throw validationFailed();
			}
		}
		Map<UUID, Set<UUID>> current = new HashMap<>();
		for (LiveExamAnswer answer : answers.findByParticipantId(participant.getId())) {
			current.computeIfAbsent(answer.getQuestionId(), ignored -> new HashSet<>()).add(answer.getOptionId());
		}
		Set<UUID> confirmed = new HashSet<>();
		confirmations.findByParticipantId(participant.getId()).forEach(row -> confirmed.add(row.getQuestionId()));
		boolean changeable = exam.isAnswersChangeable();
		Set<UUID> locked = new HashSet<>();
		if (!changeable) {
			for (LiveExamQuestion question : byId.values()) {
				boolean answered = !current.getOrDefault(question.getId(), Set.of()).isEmpty();
				// A single-answer question is final at its first choice; a multi-answer one when the student confirms it.
				if (confirmed.contains(question.getId()) || (!question.isMultipleAnswer() && answered)) {
					locked.add(question.getId());
				}
			}
			for (Map.Entry<UUID, Set<UUID>> entry : requested.entrySet()) {
				if (locked.contains(entry.getKey()) && !entry.getValue().equals(current.getOrDefault(entry.getKey(), Set.of()))) {
					throw new ApiException(ErrorCode.ANSWER_LOCKED, HttpStatus.CONFLICT, "This answer is final.");
				}
			}
		}
		Map<UUID, Set<UUID>> next = new LinkedHashMap<>();
		if (!replaceMissing) {
			current.forEach((questionId, chosen) -> next.put(questionId, chosen));
		}
		else {
			for (UUID questionId : locked) {
				if (current.containsKey(questionId)) {
					next.put(questionId, current.get(questionId));
				}
			}
		}
		requested.forEach((questionId, chosen) -> {
			if (chosen.isEmpty()) {
				next.remove(questionId);
			}
			else {
				next.put(questionId, chosen);
			}
		});
		answers.deleteByParticipantId(participant.getId());
		answers.flush();
		List<LiveExamAnswer> rows = new ArrayList<>();
		next.forEach((questionId, chosen) -> chosen.forEach(optionId -> {
			LiveExamAnswer row = new LiveExamAnswer();
			row.setParticipantId(participant.getId());
			row.setQuestionId(questionId);
			row.setOptionId(optionId);
			rows.add(row);
		}));
		answers.saveAll(rows);
		if (!changeable) {
			for (Map.Entry<UUID, Set<UUID>> entry : requested.entrySet()) {
				LiveExamQuestion question = byId.get(entry.getKey());
				boolean final_ = !entry.getValue().isEmpty() && (!question.isMultipleAnswer() || confirm.contains(entry.getKey()));
				if (final_ && !confirmed.contains(entry.getKey())) {
					LiveExamAnswerConfirmation row = new LiveExamAnswerConfirmation();
					row.setParticipantId(participant.getId());
					row.setQuestionId(entry.getKey());
					row.setConfirmedAt(clock.instant());
					confirmations.save(row);
				}
			}
		}
	}

	/** The options a payload item selects: {@code optionIds}, or the older single {@code optionId}. */
	private static Set<UUID> optionIds(JsonNode item) {
		Set<UUID> chosen = new LinkedHashSet<>();
		JsonNode many = item.get("optionIds");
		if (many != null && !many.isNull()) {
			if (!many.isArray()) {
				throw validationFailed();
			}
			for (JsonNode id : many) {
				chosen.add(parseId(id));
			}
			return chosen;
		}
		chosen.add(readId(item, "optionId"));
		return chosen;
	}

	private static void requireMcq(LiveExam exam) {
		if (!LiveExam.MCQ.equals(exam.getExamType())) {
			throw new ApiException(ErrorCode.EXAM_TYPE_MISMATCH, HttpStatus.CONFLICT, "Only an MCQ exam saves options.");
		}
	}

	private LiveExamParticipant openParticipant(UUID examId, UUID studentId) {
		LiveExamParticipant participant = latest(examId, studentId)
			.orElseThrow(LiveExamSittingService::notOpen);
		if (!LiveExamParticipant.IN_PROGRESS.equals(participant.getStatus())
				|| !clock.instant().isBefore(participant.getDeadlineAt())) {
			throw new ApiException(ErrorCode.DEADLINE_PASSED, HttpStatus.CONFLICT, "The sitting deadline has passed.");
		}
		return participant;
	}

	/** The attempt that takes answers: the latest, before its deadline and before it is submitted. */
	private LiveExamParticipant answerable(UUID examId, UUID studentId) {
		LiveExamParticipant participant = latest(examId, studentId)
			.orElseThrow(LiveExamSittingService::notOpen);
		if (!clock.instant().isBefore(participant.getDeadlineAt())
				|| LiveExamParticipant.FAILED_TO_SUBMIT.equals(participant.getStatus())) {
			throw new ApiException(ErrorCode.DEADLINE_PASSED, HttpStatus.CONFLICT, "The sitting deadline has passed.");
		}
		// A submitted MCQ shows its key at once, so it can no longer change.
		if (participant.getSubmittedAt() != null) {
			throw new ApiException(ErrorCode.ANSWER_ALREADY_SUBMITTED, HttpStatus.CONFLICT, "The attempt was already submitted.");
		}
		return participant;
	}

	private LiveExamParticipant start(LiveExam exam, UUID studentId, String kind, int attemptNo, Instant started) {
		LiveExamParticipant participant = new LiveExamParticipant();
		participant.setExamId(exam.getId());
		participant.setStudentId(studentId);
		participant.setKind(kind);
		participant.setAttemptNo(attemptNo);
		participant.setStartedAt(started);
		participant.setDeadlineAt(ExamWindow.deadline(exam, started));
		participant.setStatus(LiveExamParticipant.IN_PROGRESS);
		// A written sitting keeps the paper version it was given, so a later PDF change does not reach it (E29).
		participant.setQuestionPaperId(exam.getQuestionPaperId());
		return participants.save(participant);
	}

	/** The student's own sitting, or null when there is none. Only ever the requesting student's row. */
	private SittingView sittingView(LiveExamParticipant participant) {
		if (participant == null) {
			return null;
		}
		return new SittingView(LiveExamParticipant.legacyKind(participant.getKind()), participant.getKind(),
				participant.getAttemptNo(), format(participant.getStartedAt()), format(participant.getDeadlineAt()),
				participant.getSubmittedAt() == null ? null : format(participant.getSubmittedAt()));
	}

	private TakeView takeView(LiveExamParticipant participant) {
		return new TakeView(participant.getId(), participant.getExamId(),
				LiveExamParticipant.legacyKind(participant.getKind()), participant.getKind(), format(clock.instant()),
				format(participant.getDeadlineAt()), format(participant.getStartedAt()), participant.getAttemptNo());
	}

	private LiveExam requireExam(UUID id) {
		return examRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(this::notFound);
	}

	private void lockExam(UUID examId) {
		examRepository.lockById(examId).orElseThrow(this::notFound);
	}

	private static UUID readId(JsonNode answer, String field) {
		if (answer == null || !answer.has(field) || answer.get(field).isNull()) {
			throw validationFailed();
		}
		return parseId(answer.get(field));
	}

	private static UUID parseId(JsonNode node) {
		try {
			return UUID.fromString(node.asText());
		}
		catch (IllegalArgumentException exception) {
			throw validationFailed();
		}
	}

	private static String format(Instant instant) {
		return DHAKA_TIME.format(instant.atZone(DHAKA));
	}

	private static ApiException notOpen() {
		return new ApiException(ErrorCode.SITTING_NOT_OPEN, HttpStatus.CONFLICT, "The sitting is not open.");
	}

	private static ApiException retakeNotAllowed(String message) {
		return new ApiException(ErrorCode.RETAKE_NOT_ALLOWED, HttpStatus.CONFLICT, message);
	}

	private static ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
	}

	private ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	/**
	 * {@code myStatus} is the legacy status the current web knows; {@code myStatusDetail} also carries ARCHIVED_OPEN,
	 * PRACTICE_OPEN and EXPIRED. {@code sitting} is the latest attempt; {@code attempts} counts them.
	 */
	public record StudentExam(UUID id, UUID moduleId, String examType, String paperLanguage, String titleEn,
			String titleBn, String descriptionEn, String descriptionBn, String rulesEn, String rulesBn, String liveAt,
			String endAt, int durationMinutes, Integer passPercent, int maximum, String status, boolean finallySaved,
			java.math.BigDecimal price, Boolean questionPdf, List<StudentQuestion> questions, String myStatus,
			Boolean retakeAvailable, SittingView sitting, Boolean answerStored, String myStatusDetail, int attempts,
			AccessView access, String coverUrl) {
	}

	public record AccessView(boolean granted, String source, String endsAt, boolean expired) {
	}

	public record StudentQuestion(UUID id, String stem, int marks, String explanation, List<StudentOption> options,
			boolean multipleAnswer) {
	}

	public record StudentOption(UUID id, String text, Boolean correct, Boolean selected) {
	}

	/**
	 * The student's own open or past sitting: kind, start, deadline, and when they submitted early (or null). {@code kind}
	 * is the legacy LIVE or RETAKE; {@code attemptKind} is the full value (NR-01, contracts §7.7).
	 */
	public record SittingView(String kind, String attemptKind, int attemptNo, String startedAt, String deadlineAt,
			String submittedAt) {
	}

	public record TakeView(UUID id, UUID examId, String kind, String attemptKind, String serverTime, String deadlineAt,
			String startedAt, int attemptNo) {
	}

	public record PostedResult(UUID participantId, BigDecimal score, int maximum, Boolean passed, String status,
			int attemptNo, LiveExamLeaderboardService.Position position) {
	}

	public record ReviewedResult(BigDecimal score, int maximum, Boolean passed, String status, List<StudentQuestion> questions,
			String comment, Boolean evaluatedPdf, String kind, String attemptKind, int attemptNo,
			LiveExamLeaderboardService.Position position, LiveExamEvaluationService.RetakeGrantView retakeGrant) {
	}

	/** One of the student's attempts. New route, so it carries {@code attemptKind} alone. */
	public record AttemptView(UUID id, int attemptNo, String attemptKind, String startedAt, String deadlineAt,
			String submittedAt, String status, BigDecimal score, Boolean passed) {
	}

	public record SavedAnswer(UUID questionId, List<UUID> optionIds, boolean confirmed) {
	}

}
