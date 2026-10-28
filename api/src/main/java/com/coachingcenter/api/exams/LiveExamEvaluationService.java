package com.coachingcenter.api.exams;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.files.LocalFileStorage;
import com.coachingcenter.api.users.AdminProfileRepository;
import com.coachingcenter.api.users.StudentProfileRepository;
import com.coachingcenter.api.users.User;
import com.coachingcenter.api.users.UserRepository;

@Service
public class LiveExamEvaluationService {

	private final LiveExamRepository exams;

	private final LiveExamModuleRepository modules;

	private final LiveExamMemberships memberships;

	private final LiveExamParticipantRepository participants;

	private final LiveExamInstructorRepository instructors;

	private final LiveExamQuestionRepository questions;

	private final LiveExamService examViews;

	private final LiveExamSittingService sittings;

	private final UserRepository users;

	private final StudentProfileRepository students;

	private final AdminProfileRepository admins;

	private final LocalFileStorage files;

	private final PdfCompressor pdfs;

	private final Clock clock;

	private final AuditRecorder audit;

	private final StudentNoticeWriter notices;

	public LiveExamEvaluationService(LiveExamRepository exams, LiveExamModuleRepository modules,
			LiveExamParticipantRepository participants, LiveExamInstructorRepository instructors,
			LiveExamQuestionRepository questions, LiveExamService examViews, LiveExamSittingService sittings,
			UserRepository users, StudentProfileRepository students, AdminProfileRepository admins, LocalFileStorage files,
			PdfCompressor pdfs, Clock clock, AuditRecorder audit, StudentNoticeWriter notices,
			LiveExamMemberships memberships) {
		this.memberships = memberships;
		this.exams = exams;
		this.modules = modules;
		this.participants = participants;
		this.instructors = instructors;
		this.questions = questions;
		this.examViews = examViews;
		this.sittings = sittings;
		this.users = users;
		this.students = students;
		this.admins = admins;
		this.files = files;
		this.pdfs = pdfs;
		this.clock = clock;
		this.audit = audit;
		this.notices = notices;
	}

	@Transactional
	public Assignment assign(UUID moduleId, UUID instructorId) {
		if (modules.findByIdAndDeletedAtIsNull(moduleId).isEmpty()) {
			throw notFound();
		}
		if (instructors.existsById(moduleId)) {
			throw new ApiException(ErrorCode.INSTRUCTOR_ALREADY_ASSIGNED, HttpStatus.CONFLICT,
					"This module already has an instructor.");
		}
		User instructor = users.findByIdAndDeletedAtIsNull(instructorId).orElseThrow(this::notFound);
		if (!Role.INSTRUCTOR.name().equals(instructor.getRole())) {
			throw validationFailed();
		}
		LiveExamInstructor row = new LiveExamInstructor();
		row.setModuleId(moduleId);
		row.setInstructorId(instructorId);
		row.setAssignedAt(clock.instant());
		instructors.save(row);
		return new Assignment(moduleId, instructorId);
	}

	@Transactional
	public void unassign(UUID moduleId) {
		if (modules.findByIdAndDeletedAtIsNull(moduleId).isEmpty()) {
			throw notFound();
		}
		if (!instructors.existsById(moduleId)) {
			throw notFound();
		}
		instructors.deleteById(moduleId);
		audit.record(AuditRecorder.LIVE_EXAM_INSTRUCTOR_UNASSIGN, AuditRecorder.LIVE_EXAM_MODULE_ENTITY, moduleId);
	}

	@Transactional
	public List<SubmissionView> list(UUID moduleId, CurrentActor actor) {
		requireReader(moduleId, actor);
		List<SubmissionView> views = new ArrayList<>();
		for (LiveExam exam : exams.findByModuleIdAndStatusAndDeletedAtIsNull(moduleId, LiveExam.PUBLISHED)) {
			if (!LiveExam.WRITTEN.equals(exam.getExamType())) {
				continue;
			}
			LiveExamModule module = moduleOf(exam);
			for (LiveExamParticipant participant : participants.findByExamIdAndDeletedAtIsNull(exam.getId())) {
				views.add(view(participant, exam, module));
			}
		}
		views.sort(Comparator.comparing(SubmissionView::studentName));
		return views;
	}

	@Transactional
	public SubmissionView open(UUID participantId, CurrentActor actor) {
		LiveExamParticipant participant = participants.findById(participantId).orElseThrow(this::notFound);
		if (participant.getDeletedAt() != null) {
			throw notFound();
		}
		LiveExam exam = requireExam(participant.getExamId());
		requireReader(primaryModuleId(exam), actor);
		sittings.settleParticipant(participantId);
		return view(participant, exam, moduleOf(exam));
	}

	@Transactional
	public SubmissionView evaluate(UUID participantId, CurrentActor actor, int marks, String comment, MultipartFile file) {
		LiveExamParticipant participant = participants.findById(participantId).orElseThrow(this::notFound);
		if (participant.getDeletedAt() != null) {
			throw notFound();
		}
		LiveExam exam = requireExam(participant.getExamId());
		if (!LiveExam.WRITTEN.equals(exam.getExamType())) {
			throw new ApiException(ErrorCode.EXAM_TYPE_MISMATCH, HttpStatus.CONFLICT, "Only a written exam is marked.");
		}
		requireMarker(primaryModuleId(exam), actor);
		sittings.settleParticipant(participantId);
		if (!LiveExamParticipant.SUBMITTED.equals(participant.getStatus()) || participant.getAnswerStoredFileName() == null) {
			throw new ApiException(ErrorCode.EVALUATION_NOT_ALLOWED, HttpStatus.CONFLICT,
					"A paper with no stored PDF cannot be marked.");
		}
		int maximum = maximum(exam);
		if (marks < 0 || marks > maximum) {
			throw new ApiException(ErrorCode.MARKS_OUT_OF_RANGE, HttpStatus.BAD_REQUEST,
					"Marks must be from 0 through the maximum.");
		}
		// A new marked PDF is optional when a PDF is already stored; the first save must send one.
		boolean released = participant.getPublishedAt() != null;
		boolean hadPending = participant.hasPendingRevision();
		String current = released && hadPending ? participant.getPendingStoredFileName() : participant.getEvaluatedStoredFileName();
		boolean sendsFile = file != null && !file.isEmpty();
		if (!sendsFile && current == null) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "A marked PDF is required the first time.");
		}
		LocalFileStorage.StoredFile stored = sendsFile ? storeMarkedPdf(file) : null;
		String discard;
		if (released) {
			// A released result keeps its mark and PDF for the student. A save now is a correction, held until release.
			discard = hadPending ? participant.getPendingStoredFileName() : null;
			participant.setPendingScore(BigDecimal.valueOf(marks));
			participant.setPendingPassed(passed(marks, exam, maximum));
			participant.setPendingComment(comment == null || comment.isBlank() ? null : comment.trim());
			participant.setPendingEvaluatedBy(actor.userId());
			participant.setPendingEvaluatedAt(clock.instant());
			if (stored != null) {
				participant.setPendingStoredFileName(stored.storedFileName());
				participant.setPendingOriginalFileName(stored.originalFileName());
				participant.setPendingByteSize(stored.byteSize());
				participant.setPendingContentType("application/pdf");
			}
			else if (!hadPending) {
				participant.setPendingStoredFileName(participant.getEvaluatedStoredFileName());
				participant.setPendingOriginalFileName(participant.getEvaluatedOriginalFileName());
				participant.setPendingByteSize(participant.getEvaluatedByteSize());
				participant.setPendingContentType(participant.getEvaluatedContentType());
			}
			if (discard != null && discard.equals(participant.getEvaluatedStoredFileName())) {
				discard = null;
			}
		}
		else {
			discard = participant.getEvaluatedStoredFileName();
			participant.setScore(BigDecimal.valueOf(marks));
			participant.setPassed(passed(marks, exam, maximum));
			participant.setComment(comment == null || comment.isBlank() ? null : comment.trim());
			participant.setEvaluatedBy(actor.userId());
			participant.setEvaluatedAt(clock.instant());
			if (stored != null) {
				participant.setEvaluatedStoredFileName(stored.storedFileName());
				participant.setEvaluatedOriginalFileName(stored.originalFileName());
				participant.setEvaluatedByteSize(stored.byteSize());
				participant.setEvaluatedContentType("application/pdf");
			}
		}
		// Any save changes the mark, so the paper is no longer ready until the marker flags it again.
		participant.setReadyForReleaseAt(null);
		if (stored != null) {
			files.commitReplacement(stored.storedFileName(), discard);
		}
		return view(participant, exam, moduleOf(exam));
	}

	@Transactional
	public SubmissionView publish(UUID participantId, CurrentActor actor) {
		if (actor.role() != Role.MASTER_ADMIN) {
			throw denied();
		}
		LiveExamParticipant participant = participants.findById(participantId).orElseThrow(this::notFound);
		if (participant.getDeletedAt() != null) {
			throw notFound();
		}
		LiveExam exam = requireExam(participant.getExamId());
		if (!LiveExam.WRITTEN.equals(exam.getExamType())) {
			throw new ApiException(ErrorCode.EXAM_TYPE_MISMATCH, HttpStatus.CONFLICT, "Only a written exam is published.");
		}
		sittings.settleParticipant(participantId);
		if (!LiveExamParticipant.SUBMITTED.equals(participant.getStatus()) || participant.getAnswerStoredFileName() == null
				|| markedAt(participant) == null || scoreOf(participant) == null) {
			throw new ApiException(ErrorCode.EVALUATION_NOT_ALLOWED, HttpStatus.CONFLICT,
					"A draft mark is required before publish.");
		}
		// A released paper with no correction waiting is already published; anything else is released now.
		if (participant.hasPendingRevision() || participant.getPublishedAt() == null) {
			Instant now = clock.instant();
			release(participant, now);
			notices.writtenResult(participant.getStudentId(), exam.getId(), participant.getId(), now);
			audit.record(AuditRecorder.LIVE_EXAM_RESULT_PUBLISH, AuditRecorder.LIVE_EXAM_PARTICIPANT_ENTITY, participant.getId());
		}
		return view(participant, exam, moduleOf(exam));
	}

	/** Publishes every draft of one written exam in one transaction. Only the Master Admin reaches this. */
	@Transactional
	public PublishedCount publishAll(UUID examId, CurrentActor actor) {
		return publishAll(examId, actor, false);
	}

	/**
	 * Releases a written exam's results. With {@code readyOnly}, only the papers the marker flagged ready go out, and the
	 * completeness gate does not apply: the marker has said those papers are finished.
	 */
	@Transactional
	public PublishedCount publishAll(UUID examId, CurrentActor actor, boolean readyOnly) {
		if (actor.role() != Role.MASTER_ADMIN) {
			throw denied();
		}
		LiveExam exam = requireExam(examId);
		if (!LiveExam.WRITTEN.equals(exam.getExamType())) {
			throw new ApiException(ErrorCode.EXAM_TYPE_MISMATCH, HttpStatus.CONFLICT, "Only a written exam is published.");
		}
		// Sittings whose deadline has passed are posted first, so the counts below are final.
		sittings.finalizeDue(examId);
		List<LiveExamParticipant> all = participants.findByExamIdAndDeletedAtIsNull(examId);
		int awaiting = 0;
		int open = 0;
		for (LiveExamParticipant participant : all) {
			if (LiveExamParticipant.IN_PROGRESS.equals(participant.getStatus())) {
				open++;
			}
			else if (LiveExamParticipant.SUBMITTED.equals(participant.getStatus()) && markedAt(participant) == null) {
				awaiting++;
			}
		}
		if (!readyOnly && (awaiting > 0 || open > 0)) {
			throw new ApiException(ErrorCode.EXAM_MARKING_INCOMPLETE, HttpStatus.CONFLICT,
					"Every paper must be marked and every sitting closed before the results are released.",
					new MarkingIncomplete(awaiting, open));
		}
		Instant now = clock.instant();
		int published = 0;
		for (LiveExamParticipant participant : all) {
			boolean ready = LiveExamParticipant.SUBMITTED.equals(participant.getStatus())
					&& participant.getAnswerStoredFileName() != null && markedAt(participant) != null
					&& scoreOf(participant) != null;
			boolean released = participant.getPublishedAt() != null && !participant.hasPendingRevision();
			if (!ready || released || (readyOnly && participant.getReadyForReleaseAt() == null)) {
				continue;
			}
			release(participant, now);
			notices.writtenResult(participant.getStudentId(), exam.getId(), participant.getId(), now);
			audit.record(AuditRecorder.LIVE_EXAM_RESULT_PUBLISH, AuditRecorder.LIVE_EXAM_PARTICIPANT_ENTITY, participant.getId());
			published++;
		}
		return new PublishedCount(published);
	}

	@Transactional
	public Leaderboard leaderboard(UUID examId, CurrentActor actor, boolean marker) {
		LiveExam exam = requireExam(examId);
		if (marker) {
			requireMarker(primaryModuleId(exam), actor);
		}
		else {
			examViews.get(examId, false, actor.userId());
		}
		if (exam.getLiveAt() == null) {
			return new Leaderboard(List.of());
		}
		Instant openAt = exam.getLiveAt().plus(Duration.ofMinutes(exam.getDurationMinutes())).plus(Duration.ofMinutes(5));
		if (clock.instant().isBefore(openAt)) {
			return new Leaderboard(List.of());
		}
		int maximum = maximum(exam);
		List<BoardRow> scored = new ArrayList<>();
		for (LiveExamParticipant participant : participants.findByExamIdAndDeletedAtIsNull(examId)) {
			if (!LiveExamParticipant.LIVE.equals(participant.getKind())) {
				continue;
			}
			if (LiveExam.MCQ.equals(exam.getExamType())) {
				if (!LiveExamParticipant.SUBMITTED.equals(participant.getStatus()) || participant.getScore() == null) {
					continue;
				}
			}
			else if (LiveExamParticipant.FAILED_TO_SUBMIT.equals(participant.getStatus())) {
				// A missing PDF is already scored zero and does not wait for publish.
			}
			else if (participant.getPublishedAt() == null || participant.getScore() == null) {
				continue;
			}
			scored.add(new BoardRow(participant.getStudentId(), studentName(participant.getStudentId()),
					McqScoring.plain(participant.getScore()), maximum, participant.getPassed() != null && participant.getPassed()));
		}
		scored.sort(Comparator.comparing(BoardRow::score).reversed().thenComparing(BoardRow::name));
		// "mine" is for a student reading the list, and only their own row. Staff never get it.
		boolean student = !marker && actor.role() == Role.STUDENT;
		List<LeaderboardRow> rows = new ArrayList<>();
		int rank = 1;
		BigDecimal previous = null;
		for (int index = 0; index < scored.size(); index++) {
			BoardRow row = scored.get(index);
			if (previous != null && row.score().compareTo(previous) != 0) {
				rank = index + 1;
			}
			previous = row.score();
			boolean mine = student && actor.userId().equals(row.studentId());
			rows.add(new LeaderboardRow(row.name(), rank, row.score(), row.maximum(), row.passed(), mine));
		}
		return new Leaderboard(rows);
	}

	@Transactional(readOnly = true)
	public byte[] answerPdf(UUID participantId, CurrentActor actor) {
		LiveExamParticipant participant = participants.findById(participantId).orElseThrow(this::notFound);
		if (participant.getDeletedAt() != null) {
			throw notFound();
		}
		LiveExam exam = requireExam(participant.getExamId());
		requireMarker(primaryModuleId(exam), actor);
		if (participant.getAnswerStoredFileName() == null) {
			throw notFound();
		}
		try {
			return Files.readAllBytes(files.resolve(participant.getAnswerStoredFileName()));
		}
		catch (IOException exception) {
			throw notFound();
		}
	}

	@Transactional(readOnly = true)
	public byte[] evaluatedPdf(UUID examId, UUID studentId) {
		LiveExamParticipant participant = participants.findByExamIdAndStudentIdAndDeletedAtIsNull(examId, studentId)
			.orElseThrow(this::notFound);
		if (participant.getPublishedAt() == null || participant.getEvaluatedStoredFileName() == null) {
			throw notFound();
		}
		try {
			return Files.readAllBytes(files.resolve(participant.getEvaluatedStoredFileName()));
		}
		catch (IOException exception) {
			throw notFound();
		}
	}

	/** The marked PDF for staff readers, published or not. An unassigned instructor gets 404, not 403. */
	@Transactional(readOnly = true)
	public byte[] staffEvaluatedPdf(UUID participantId, CurrentActor actor) {
		LiveExamParticipant participant = participants.findById(participantId).orElseThrow(this::notFound);
		if (participant.getDeletedAt() != null) {
			throw notFound();
		}
		LiveExam exam = requireExam(participant.getExamId());
		String stored = storedFileOf(participant);
		if (!readerOf(primaryModuleId(exam), actor) || stored == null) {
			throw notFound();
		}
		try {
			return Files.readAllBytes(files.resolve(stored));
		}
		catch (IOException exception) {
			throw notFound();
		}
	}

	/** The written question paper for staff readers. An unassigned instructor gets 404, not 403. */
	@Transactional(readOnly = true)
	public byte[] questionPdf(UUID examId, CurrentActor actor) {
		LiveExam exam = requireExam(examId);
		if (!readerOf(primaryModuleId(exam), actor) || exam.getQuestionStoredFileName() == null) {
			throw notFound();
		}
		if (actor.role() == Role.INSTRUCTOR && !windowEnded(exam)) {
			throw notFound();
		}
		try {
			return Files.readAllBytes(files.resolve(exam.getQuestionStoredFileName()));
		}
		catch (IOException exception) {
			throw notFound();
		}
	}

	private SubmissionView view(LiveExamParticipant participant, LiveExam exam, LiveExamModule module) {
		String evaluatorName = null;
		UUID evaluator = evaluatorOf(participant);
		if (evaluator != null) {
			evaluatorName = admins.findById(evaluator).map(profile -> profile.getFullName()).orElse(null);
		}
		boolean pending = participant.hasPendingRevision();
		return new SubmissionView(participant.getId(), exam.getId(), participant.getStudentId(),
				studentName(participant.getStudentId()), participant.getStatus(), McqScoring.plain(scoreOf(participant)), maximum(exam),
				passedOf(participant), LiveExamParticipant.legacyKind(participant.getKind()), participant.getKind(),
				participant.getAnswerStoredFileName() != null,
				storedFileOf(participant) == null ? null : Boolean.TRUE, evaluatorName,
				participant.getPublishedAt() != null && !pending, commentOf(participant), originalFileOf(participant),
				module.getId(), module.getTitleEn(), module.getTitleBn(), exam.getTitleEn(), exam.getTitleBn(),
				participant.getSubmittedAt(), markedAt(participant), participant.getPublishedAt(), pending ? Boolean.TRUE : null,
				participant.getReadyForReleaseAt() == null ? null : Boolean.TRUE);
	}

	private LiveExamModule moduleOf(LiveExam exam) {
		return modules.findById(primaryModuleId(exam)).orElseThrow(this::notFound);
	}

	/**
	 * The marker flags a paper ready for release, or clears the flag. Only a paper with a mark can be flagged. The Master
	 * Admin still decides when it goes out.
	 */
	@Transactional
	public SubmissionView markReady(UUID participantId, CurrentActor actor, boolean ready) {
		LiveExamParticipant participant = participants.findById(participantId).orElseThrow(this::notFound);
		if (participant.getDeletedAt() != null) {
			throw notFound();
		}
		LiveExam exam = requireExam(participant.getExamId());
		if (!LiveExam.WRITTEN.equals(exam.getExamType())) {
			throw new ApiException(ErrorCode.EXAM_TYPE_MISMATCH, HttpStatus.CONFLICT, "Only a written exam is marked.");
		}
		requireMarker(primaryModuleId(exam), actor);
		sittings.settleParticipant(participantId);
		if (!ready) {
			participant.setReadyForReleaseAt(null);
			return view(participant, exam, moduleOf(exam));
		}
		if (!LiveExamParticipant.SUBMITTED.equals(participant.getStatus()) || markedAt(participant) == null
				|| scoreOf(participant) == null) {
			throw new ApiException(ErrorCode.EVALUATION_NOT_ALLOWED, HttpStatus.CONFLICT,
					"A draft mark is required before the paper is ready for release.");
		}
		participant.setReadyForReleaseAt(clock.instant());
		return view(participant, exam, moduleOf(exam));
	}

	/**
	 * What blocks a release and what is waiting for one. Read only: sittings are posted by the deadline job, so an open
	 * sitting can show for about a minute after its deadline.
	 */
	@Transactional(readOnly = true)
	public ReleaseStatus releaseStatus(UUID examId, CurrentActor actor) {
		if (actor.role() != Role.MASTER_ADMIN && actor.role() != Role.SYSTEM_ADMIN) {
			throw denied();
		}
		LiveExam exam = requireExam(examId);
		if (!LiveExam.WRITTEN.equals(exam.getExamType())) {
			throw new ApiException(ErrorCode.EXAM_TYPE_MISMATCH, HttpStatus.CONFLICT, "Only a written exam is published.");
		}
		int awaiting = 0;
		int open = 0;
		int drafts = 0;
		int ready = 0;
		int failed = 0;
		for (LiveExamParticipant participant : participants.findByExamIdAndDeletedAtIsNull(examId)) {
			if (LiveExamParticipant.FAILED_TO_SUBMIT.equals(participant.getStatus())) {
				failed++;
			}
			else if (LiveExamParticipant.IN_PROGRESS.equals(participant.getStatus())) {
				open++;
			}
			else if (LiveExamParticipant.SUBMITTED.equals(participant.getStatus())) {
				if (markedAt(participant) == null) {
					awaiting++;
					continue;
				}
				if (participant.getPublishedAt() == null || participant.hasPendingRevision()) {
					drafts++;
					if (participant.getReadyForReleaseAt() != null) {
						ready++;
					}
				}
			}
		}
		return new ReleaseStatus(awaiting, open, drafts, ready, failed, awaiting == 0 && open == 0);
	}

	/** The mark a staff reader works on: the correction waiting for release if there is one, else the released or draft mark. */
	private static Instant markedAt(LiveExamParticipant participant) {
		return participant.hasPendingRevision() ? participant.getPendingEvaluatedAt() : participant.getEvaluatedAt();
	}

	/** The exam's package for marking and reading: its first active membership. Throws when it is in none. */
	private UUID primaryModuleId(LiveExam exam) {
		return memberships.primaryModuleId(exam.getId()).orElseThrow(this::notFound);
	}

	private static BigDecimal scoreOf(LiveExamParticipant participant) {
		return participant.hasPendingRevision() ? participant.getPendingScore() : participant.getScore();
	}

	private static Boolean passedOf(LiveExamParticipant participant) {
		return participant.hasPendingRevision() ? participant.getPendingPassed() : participant.getPassed();
	}

	private static String commentOf(LiveExamParticipant participant) {
		return participant.hasPendingRevision() ? participant.getPendingComment() : participant.getComment();
	}

	private static UUID evaluatorOf(LiveExamParticipant participant) {
		return participant.hasPendingRevision() ? participant.getPendingEvaluatedBy() : participant.getEvaluatedBy();
	}

	private static String storedFileOf(LiveExamParticipant participant) {
		return participant.hasPendingRevision() ? participant.getPendingStoredFileName() : participant.getEvaluatedStoredFileName();
	}

	private static String originalFileOf(LiveExamParticipant participant) {
		return participant.hasPendingRevision() ? participant.getPendingOriginalFileName() : participant.getEvaluatedOriginalFileName();
	}

	/**
	 * Moves a correction waiting for release into the released columns. The PDF it replaced is deleted, unless the
	 * new PDF is the same file.
	 */
	private void release(LiveExamParticipant participant, Instant at) {
		if (participant.hasPendingRevision()) {
			String previous = participant.getEvaluatedStoredFileName();
			participant.setScore(participant.getPendingScore());
			participant.setPassed(participant.getPendingPassed());
			participant.setComment(participant.getPendingComment());
			participant.setEvaluatedBy(participant.getPendingEvaluatedBy());
			participant.setEvaluatedAt(participant.getPendingEvaluatedAt());
			participant.setEvaluatedStoredFileName(participant.getPendingStoredFileName());
			participant.setEvaluatedOriginalFileName(participant.getPendingOriginalFileName());
			participant.setEvaluatedByteSize(participant.getPendingByteSize());
			participant.setEvaluatedContentType(participant.getPendingContentType());
			participant.clearPendingRevision();
			String released = participant.getEvaluatedStoredFileName();
			if (previous != null && released != null && !previous.equals(released)) {
				files.commitReplacement(released, previous);
			}
		}
		participant.setReadyForReleaseAt(null);
		participant.setPublishedAt(at);
	}

	/** Checks the marked PDF, compresses it and stores it. The caller commits the replacement after the row is set. */
	private LocalFileStorage.StoredFile storeMarkedPdf(MultipartFile file) {
		if (file.getSize() > LiveExamSittingService.MAX_PDF_BYTES) {
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
		return files.save(file.getOriginalFilename(), "application/pdf", new ByteArrayInputStream(compressed),
				compressed.length, LiveExamSittingService.MAX_PDF_BYTES);
	}

	/** An instructor reads a paper only after its shared window has ended (liveAt plus the duration). */
	private boolean windowEnded(LiveExam exam) {
		return LiveExam.PUBLISHED.equals(exam.getStatus()) && exam.getLiveAt() != null
				&& !clock.instant().isBefore(exam.getLiveAt().plus(Duration.ofMinutes(exam.getDurationMinutes())));
	}

	private void requireMarker(UUID moduleId, CurrentActor actor) {
		if (actor.role() == Role.MASTER_ADMIN) {
			return;
		}
		if (assignedInstructor(moduleId, actor)) {
			return;
		}
		throw denied();
	}

	private void requireReader(UUID moduleId, CurrentActor actor) {
		if (!readerOf(moduleId, actor)) {
			throw denied();
		}
	}

	private boolean readerOf(UUID moduleId, CurrentActor actor) {
		return actor.role() == Role.MASTER_ADMIN || actor.role() == Role.SYSTEM_ADMIN || assignedInstructor(moduleId, actor);
	}

	private boolean assignedInstructor(UUID moduleId, CurrentActor actor) {
		if (actor.role() != Role.INSTRUCTOR) {
			return false;
		}
		LiveExamInstructor assignment = instructors.findById(moduleId).orElse(null);
		return assignment != null && actor.userId().equals(assignment.getInstructorId());
	}

	private String studentName(UUID studentId) {
		return students.findById(studentId).map(profile -> profile.getFullName()).orElse("");
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

	static boolean passed(int score, LiveExam exam, int maximum) {
		int percent = exam.getPassPercent() == null ? 0 : exam.getPassPercent();
		return (long) score * 100 >= (long) percent * maximum;
	}

	private LiveExam requireExam(UUID id) {
		return exams.findByIdAndDeletedAtIsNull(id).orElseThrow(this::notFound);
	}

	private static ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
	}

	private static ApiException denied() {
		return new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
	}

	private ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	public record Assignment(UUID moduleId, UUID instructorId) {
	}

	/** The release panel's numbers. `canRelease` is true when nothing awaits a mark and no sitting is open. */
	public record ReleaseStatus(int awaiting, int open, int drafts, int ready, int failedToSubmit, boolean canRelease) {
	}

	/** Why a release was refused: papers still waiting for a mark, and sittings still open. */
	public record MarkingIncomplete(int awaiting, int open) {
	}

	public record PublishedCount(int published) {
	}

	public record SubmissionView(UUID id, UUID examId, UUID studentId, String studentName, String status, BigDecimal score,
			int maximum, Boolean passed, String kind, String attemptKind, boolean answerPdf, Boolean evaluatedPdf, String evaluatorName,
			boolean published, String comment, String evaluatedFileName, UUID moduleId, String moduleTitleEn,
			String moduleTitleBn, String examTitleEn, String examTitleBn, Instant submittedAt, Instant evaluatedAt,
			Instant publishedAt, Boolean releasePending, Boolean readyForRelease) {
	}

	public record Leaderboard(List<LeaderboardRow> rows) {
	}

	public record LeaderboardRow(String name, int rank, BigDecimal score, int maximum, boolean passed, boolean mine) {
	}

	private record BoardRow(UUID studentId, String name, BigDecimal score, int maximum, boolean passed) {
	}

}
