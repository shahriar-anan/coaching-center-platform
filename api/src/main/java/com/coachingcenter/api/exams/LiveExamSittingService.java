package com.coachingcenter.api.exams;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

	private final LocalFileStorage files;

	private final PdfCompressor pdfs;

	private final Clock clock;

	private final StudentPhoneGuard phones;

	private final StudentNoticeWriter notices;

	public LiveExamSittingService(LiveExamService exams, LiveExamRepository examRepository,
			LiveExamQuestionRepository questions, LiveExamOptionRepository options,
			LiveExamParticipantRepository participants, LiveExamAnswerRepository answers, LocalFileStorage files,
			PdfCompressor pdfs, Clock clock, StudentPhoneGuard phones, StudentNoticeWriter notices) {
		this.exams = exams;
		this.examRepository = examRepository;
		this.questions = questions;
		this.options = options;
		this.participants = participants;
		this.answers = answers;
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
		LiveExamParticipant participant = participants.findByExamIdAndStudentIdAndDeletedAtIsNull(examId, studentId)
			.orElse(null);
		if (participant != null && !clock.instant().isBefore(participant.getDeadlineAt())) {
			postIfDue(participant, exam);
		}
		boolean open = participant != null && clock.instant().isBefore(participant.getDeadlineAt());
		boolean review = participant != null && participant.getSubmittedAt() != null
				&& !clock.instant().isBefore(participant.getDeadlineAt());
		List<StudentQuestion> questionViews = null;
		Boolean questionPdf = null;
		if (participant != null && (open || review) && LiveExam.MCQ.equals(exam.getExamType())) {
			questionViews = questionViews(examId, participant.getId(), review);
		}
		if (open && LiveExam.WRITTEN.equals(exam.getExamType()) && exam.getQuestionStoredFileName() != null) {
			questionPdf = true;
		}
		return new StudentExam(shell.id(), shell.moduleId(), shell.examType(), shell.paperLanguage(), shell.titleEn(),
				shell.titleBn(), shell.descriptionEn(), shell.descriptionBn(), shell.rulesEn(), shell.rulesBn(),
				shell.liveAt(), shell.endAt(), shell.durationMinutes(), shell.passPercent(), shell.maximum(),
				shell.status(), shell.finallySaved(), shell.price(), questionPdf, questionViews, shell.myStatus(),
				shell.retakeAvailable(), sittingView(participant),
				participant != null && participant.getAnswerStoredFileName() != null);
	}

	@Transactional
	public TakeView take(UUID examId, UUID studentId, JsonNode ignored) {
		phones.require(studentId);
		exams.get(examId, false, studentId);
		lockExam(examId);
		LiveExam exam = requireExam(examId);
		LiveExamParticipant existing = participants.findByExamIdAndStudentIdAndDeletedAtIsNull(examId, studentId)
			.orElse(null);
		Instant now = clock.instant();
		if (existing != null) {
			if (!now.isBefore(existing.getDeadlineAt())) {
				throw notOpen();
			}
			return takeView(existing);
		}
		Instant end = end(exam);
		if (exam.getLiveAt() == null || now.isBefore(exam.getLiveAt()) || !now.isBefore(end)) {
			throw notOpen();
		}
		LiveExamParticipant participant = start(exam, studentId, LiveExamParticipant.LIVE, now, end);
		return takeView(participant);
	}

	@Transactional
	public TakeView retake(UUID examId, UUID studentId) {
		phones.require(studentId);
		exams.get(examId, false, studentId);
		lockExam(examId);
		LiveExam exam = requireExam(examId);
		if (LiveExam.WRITTEN.equals(exam.getExamType())) {
			throw new ApiException(ErrorCode.RETAKE_NOT_ALLOWED, HttpStatus.CONFLICT,
					"A written exam has no later attempt.");
		}
		if (participants.findByExamIdAndStudentIdAndDeletedAtIsNull(examId, studentId).isPresent()) {
			throw new ApiException(ErrorCode.RETAKE_NOT_ALLOWED, HttpStatus.CONFLICT,
					"A live participant cannot retake, and a retake can be taken once.");
		}
		Instant now = clock.instant();
		if (exam.getLiveAt() == null || now.isBefore(end(exam))) {
			throw notOpen();
		}
		LiveExamParticipant participant = start(exam, studentId, LiveExamParticipant.RETAKE, now,
				now.plus(Duration.ofMinutes(exam.getDurationMinutes())));
		return takeView(participant);
	}

	@Transactional
	public void saveAnswers(UUID examId, UUID studentId, JsonNode body) {
		exams.get(examId, false, studentId);
		LiveExam exam = requireExam(examId);
		if (!LiveExam.MCQ.equals(exam.getExamType())) {
			throw new ApiException(ErrorCode.EXAM_TYPE_MISMATCH, HttpStatus.CONFLICT, "Only an MCQ exam saves options.");
		}
		LiveExamParticipant participant = answerable(examId, studentId);
		JsonNode selected = body.get("answers");
		if (selected == null || !selected.isArray()) {
			throw validationFailed();
		}
		Map<UUID, LiveExamQuestion> byId = new HashMap<>();
		for (LiveExamQuestion question : questions.findByExamIdAndDeletedAtIsNullOrderByPositionAscIdAsc(exam.getId())) {
			byId.put(question.getId(), question);
		}
		Set<UUID> seen = new HashSet<>();
		List<LiveExamAnswer> rows = new ArrayList<>();
		for (JsonNode answer : selected) {
			UUID questionId = readId(answer, "questionId");
			UUID optionId = readId(answer, "optionId");
			if (!seen.add(questionId) || !byId.containsKey(questionId) || !optionBelongs(questionId, optionId)) {
				throw validationFailed();
			}
			LiveExamAnswer row = new LiveExamAnswer();
			row.setParticipantId(participant.getId());
			row.setQuestionId(questionId);
			row.setOptionId(optionId);
			rows.add(row);
		}
		answers.deleteByParticipantId(participant.getId());
		answers.flush();
		answers.saveAll(rows);
	}

	@Transactional
	public PostedResult submit(UUID examId, UUID studentId) {
		exams.get(examId, false, studentId);
		LiveExam exam = requireExam(examId);
		LiveExamParticipant participant = participants.findByExamIdAndStudentIdAndDeletedAtIsNull(examId, studentId)
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

	@Transactional
	public ReviewedResult result(UUID examId, UUID studentId) {
		LiveExam exam = requireExam(examId);
		LiveExamParticipant participant = participants.findByExamIdAndStudentIdAndDeletedAtIsNull(examId, studentId)
			.orElseThrow(this::notFound);
		if (clock.instant().isBefore(participant.getDeadlineAt())) {
			if (participant.getSubmittedAt() == null) {
				throw notOpen();
			}
		}
		else {
			postIfDue(participant, exam);
		}
		boolean review = !clock.instant().isBefore(participant.getDeadlineAt());
		List<StudentQuestion> questionViews = review && LiveExam.MCQ.equals(exam.getExamType())
				? questionViews(examId, participant.getId(), true) : null;
		Integer score = participant.getScore();
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
		return new ReviewedResult(score, maximum(exam), passed, participant.getStatus(), questionViews, comment,
				evaluatedPdf, participant.getKind());
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

	@Transactional(readOnly = true)
	public byte[] questionPdf(UUID examId, UUID studentId) {
		exams.get(examId, false, studentId);
		LiveExam exam = requireExam(examId);
		LiveExamParticipant participant = participants.findByExamIdAndStudentIdAndDeletedAtIsNull(examId, studentId)
			.orElse(null);
		if (participant == null || !clock.instant().isBefore(participant.getDeadlineAt())
				|| exam.getQuestionStoredFileName() == null) {
			throw notFound();
		}
		try {
			return java.nio.file.Files.readAllBytes(files.resolve(exam.getQuestionStoredFileName()));
		}
		catch (IOException exception) {
			throw notFound();
		}
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
		String previous = participant.getAnswerStoredFileName();
		LocalFileStorage.StoredFile stored = files.save(file.getOriginalFilename(), "application/pdf",
				new ByteArrayInputStream(compressed), compressed.length, MAX_PDF_BYTES);
		participant.setAnswerStoredFileName(stored.storedFileName());
		participant.setAnswerOriginalFileName(stored.originalFileName());
		participant.setAnswerByteSize(stored.byteSize());
		participant.setAnswerContentType("application/pdf");
		files.commitReplacement(stored.storedFileName(), previous);
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
				participant.setScore(0);
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
		int score = 0;
		boolean answered = false;
		Map<UUID, UUID> correct = correctOptions(exam.getId());
		Map<UUID, Integer> marks = marks(exam.getId());
		for (LiveExamAnswer answer : answers.findByParticipantId(participant.getId())) {
			answered = true;
			if (answer.getOptionId().equals(correct.get(answer.getQuestionId()))) {
				score += marks.getOrDefault(answer.getQuestionId(), 0);
			}
		}
		participant.setScore(score);
		participant.setPassed(answered && passed(score, exam));
		participant.setStatus(LiveExamParticipant.SUBMITTED);
		if (writeNotice) {
			notices.mcqScore(participant.getStudentId(), exam.getId(), participant.getId());
		}
	}

	private PostedResult posted(LiveExamParticipant participant, LiveExam exam) {
		Integer score = participant.getScore();
		Boolean passed = participant.getPassed();
		if (LiveExam.WRITTEN.equals(exam.getExamType())
				&& !LiveExamParticipant.FAILED_TO_SUBMIT.equals(participant.getStatus())
				&& participant.getPublishedAt() == null) {
			score = null;
			passed = null;
		}
		return new PostedResult(participant.getId(), score, maximum(exam), passed, participant.getStatus());
	}

	private boolean passed(int score, LiveExam exam) {
		int percent = exam.getPassPercent() == null ? 0 : exam.getPassPercent();
		return (long) score * 100 >= (long) percent * maximum(exam);
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
	 * {@code review} means the student's own sitting is over. The correct options and explanations are shown only then,
	 * and only once staff released the answers for the exam (R-4), so nobody sees them while others may still sit.
	 */
	private List<StudentQuestion> questionViews(UUID examId, UUID participantId, boolean review) {
		boolean reveal = review && examRepository.answersReleased(examId);
		Map<UUID, UUID> selected = new HashMap<>();
		for (LiveExamAnswer answer : answers.findByParticipantId(participantId)) {
			selected.put(answer.getQuestionId(), answer.getOptionId());
		}
		List<StudentQuestion> views = new ArrayList<>();
		for (LiveExamQuestion question : questions.findByExamIdAndDeletedAtIsNullOrderByPositionAscIdAsc(examId)) {
			List<StudentOption> optionViews = new ArrayList<>();
			for (LiveExamOption option : options.findByQuestionIdAndDeletedAtIsNullOrderByPositionAscIdAsc(question.getId())) {
				optionViews.add(new StudentOption(option.getId(), option.getOptionText(),
						reveal ? option.isCorrect() : null, option.getId().equals(selected.get(question.getId()))));
			}
			views.add(new StudentQuestion(question.getId(), question.getStem(), question.getMarks(),
					reveal ? question.getExplanation() : null, optionViews));
		}
		return views;
	}

	private Map<UUID, UUID> correctOptions(UUID examId) {
		Map<UUID, UUID> correct = new HashMap<>();
		for (LiveExamQuestion question : questions.findByExamIdAndDeletedAtIsNullOrderByPositionAscIdAsc(examId)) {
			for (LiveExamOption option : options.findByQuestionIdAndDeletedAtIsNullOrderByPositionAscIdAsc(question.getId())) {
				if (option.isCorrect()) {
					correct.put(question.getId(), option.getId());
				}
			}
		}
		return correct;
	}

	private Map<UUID, Integer> marks(UUID examId) {
		Map<UUID, Integer> marks = new HashMap<>();
		for (LiveExamQuestion question : questions.findByExamIdAndDeletedAtIsNullOrderByPositionAscIdAsc(examId)) {
			marks.put(question.getId(), question.getMarks());
		}
		return marks;
	}

	private boolean optionBelongs(UUID questionId, UUID optionId) {
		for (LiveExamOption option : options.findByQuestionIdAndDeletedAtIsNullOrderByPositionAscIdAsc(questionId)) {
			if (option.getId().equals(optionId)) {
				return true;
			}
		}
		return false;
	}

	private LiveExamParticipant openParticipant(UUID examId, UUID studentId) {
		LiveExamParticipant participant = participants.findByExamIdAndStudentIdAndDeletedAtIsNull(examId, studentId)
			.orElseThrow(LiveExamSittingService::notOpen);
		if (!LiveExamParticipant.IN_PROGRESS.equals(participant.getStatus())
				|| !clock.instant().isBefore(participant.getDeadlineAt())) {
			throw new ApiException(ErrorCode.DEADLINE_PASSED, HttpStatus.CONFLICT, "The sitting deadline has passed.");
		}
		return participant;
	}

	private LiveExamParticipant answerable(UUID examId, UUID studentId) {
		LiveExamParticipant participant = participants.findByExamIdAndStudentIdAndDeletedAtIsNull(examId, studentId)
			.orElseThrow(LiveExamSittingService::notOpen);
		if (!clock.instant().isBefore(participant.getDeadlineAt())
				|| LiveExamParticipant.FAILED_TO_SUBMIT.equals(participant.getStatus())) {
			throw new ApiException(ErrorCode.DEADLINE_PASSED, HttpStatus.CONFLICT, "The sitting deadline has passed.");
		}
		return participant;
	}

	private LiveExamParticipant start(LiveExam exam, UUID studentId, String kind, Instant started, Instant deadline) {
		LiveExamParticipant participant = new LiveExamParticipant();
		participant.setExamId(exam.getId());
		participant.setStudentId(studentId);
		participant.setKind(kind);
		participant.setStartedAt(started);
		participant.setDeadlineAt(deadline);
		participant.setStatus(LiveExamParticipant.IN_PROGRESS);
		return participants.save(participant);
	}

	/** The student's own sitting, or null when there is none. Only ever the requesting student's row. */
	private SittingView sittingView(LiveExamParticipant participant) {
		if (participant == null) {
			return null;
		}
		return new SittingView(participant.getKind(), format(participant.getStartedAt()),
				format(participant.getDeadlineAt()),
				participant.getSubmittedAt() == null ? null : format(participant.getSubmittedAt()));
	}

	private TakeView takeView(LiveExamParticipant participant) {
		return new TakeView(participant.getId(), participant.getExamId(), participant.getKind(), format(clock.instant()),
				format(participant.getDeadlineAt()), format(participant.getStartedAt()));
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
		try {
			return UUID.fromString(answer.get(field).asText());
		}
		catch (IllegalArgumentException exception) {
			throw validationFailed();
		}
	}

	private static Instant end(LiveExam exam) {
		if (exam.getLiveAt() == null) {
			return null;
		}
		return exam.getLiveAt().plus(Duration.ofMinutes(exam.getDurationMinutes()));
	}

	private static String format(Instant instant) {
		return DHAKA_TIME.format(instant.atZone(DHAKA));
	}

	private static ApiException notOpen() {
		return new ApiException(ErrorCode.SITTING_NOT_OPEN, HttpStatus.CONFLICT, "The sitting is not open.");
	}

	private static ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
	}

	private ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	public record StudentExam(UUID id, UUID moduleId, String examType, String paperLanguage, String titleEn,
			String titleBn, String descriptionEn, String descriptionBn, String rulesEn, String rulesBn, String liveAt,
			String endAt, int durationMinutes, Integer passPercent, int maximum, String status, boolean finallySaved,
			java.math.BigDecimal price, Boolean questionPdf, List<StudentQuestion> questions, String myStatus,
			Boolean retakeAvailable, SittingView sitting, Boolean answerStored) {
	}

	public record StudentQuestion(UUID id, String stem, int marks, String explanation, List<StudentOption> options) {
	}

	public record StudentOption(UUID id, String text, Boolean correct, Boolean selected) {
	}

	/** The student's own open or past sitting: kind, start, deadline, and when they submitted early (or null). */
	public record SittingView(String kind, String startedAt, String deadlineAt, String submittedAt) {
	}

	public record TakeView(UUID id, UUID examId, String kind, String serverTime, String deadlineAt, String startedAt) {
	}

	public record PostedResult(UUID participantId, Integer score, int maximum, Boolean passed, String status) {
	}

	public record ReviewedResult(Integer score, int maximum, Boolean passed, String status, List<StudentQuestion> questions,
			String comment, Boolean evaluatedPdf, String kind) {
	}

}
