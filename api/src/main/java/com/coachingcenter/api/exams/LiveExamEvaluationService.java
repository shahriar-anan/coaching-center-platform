package com.coachingcenter.api.exams;

import java.io.ByteArrayInputStream;
import java.io.IOException;
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

	public LiveExamEvaluationService(LiveExamRepository exams, LiveExamModuleRepository modules,
			LiveExamParticipantRepository participants, LiveExamInstructorRepository instructors,
			LiveExamQuestionRepository questions, LiveExamService examViews, LiveExamSittingService sittings,
			UserRepository users, StudentProfileRepository students, AdminProfileRepository admins, LocalFileStorage files,
			PdfCompressor pdfs, Clock clock) {
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
	public List<SubmissionView> list(UUID moduleId, CurrentActor actor) {
		requireReader(moduleId, actor);
		List<SubmissionView> views = new ArrayList<>();
		for (LiveExam exam : exams.findByModuleIdAndStatusAndDeletedAtIsNull(moduleId, LiveExam.PUBLISHED)) {
			if (!LiveExam.WRITTEN.equals(exam.getExamType())) {
				continue;
			}
			sittings.finalizeDue(exam.getId());
			for (LiveExamParticipant participant : participants.findByExamIdAndDeletedAtIsNull(exam.getId())) {
				views.add(view(participant, exam));
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
		requireReader(exam.getModuleId(), actor);
		sittings.settleParticipant(participantId);
		return view(participant, exam);
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
		requireMarker(exam.getModuleId(), actor);
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
		if (file == null || file.getSize() > LiveExamSittingService.MAX_PDF_BYTES) {
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
		String previous = participant.getEvaluatedStoredFileName();
		LocalFileStorage.StoredFile stored = files.save(file.getOriginalFilename(), "application/pdf",
				new ByteArrayInputStream(compressed), compressed.length, LiveExamSittingService.MAX_PDF_BYTES);
		participant.setScore(marks);
		participant.setPassed(passed(marks, exam, maximum));
		participant.setComment(comment == null || comment.isBlank() ? null : comment.trim());
		participant.setEvaluatedBy(actor.userId());
		participant.setEvaluatedAt(clock.instant());
		participant.setEvaluatedStoredFileName(stored.storedFileName());
		participant.setEvaluatedOriginalFileName(stored.originalFileName());
		participant.setEvaluatedByteSize(stored.byteSize());
		participant.setEvaluatedContentType("application/pdf");
		files.commitReplacement(stored.storedFileName(), previous);
		return view(participant, exam);
	}

	@Transactional
	public Leaderboard leaderboard(UUID examId, CurrentActor actor, boolean marker) {
		LiveExam exam = requireExam(examId);
		if (marker) {
			requireMarker(exam.getModuleId(), actor);
		}
		else {
			examViews.get(examId, false, actor.userId());
		}
		Instant openAt = exam.getLiveAt().plus(Duration.ofMinutes(exam.getDurationMinutes())).plus(Duration.ofMinutes(5));
		if (clock.instant().isBefore(openAt)) {
			return new Leaderboard(List.of());
		}
		sittings.finalizeDue(examId);
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
				// A missing PDF is already scored zero.
			}
			else if (participant.getEvaluatedAt() == null || participant.getScore() == null) {
				continue;
			}
			scored.add(new BoardRow(studentName(participant.getStudentId()), participant.getScore(), maximum,
					participant.getPassed() != null && participant.getPassed()));
		}
		scored.sort(Comparator.comparingInt(BoardRow::score).reversed().thenComparing(BoardRow::name));
		List<LeaderboardRow> rows = new ArrayList<>();
		int rank = 1;
		Integer previous = null;
		for (int index = 0; index < scored.size(); index++) {
			BoardRow row = scored.get(index);
			if (previous != null && row.score() != previous) {
				rank = index + 1;
			}
			previous = row.score();
			rows.add(new LeaderboardRow(row.name(), rank, row.score(), row.maximum(), row.passed()));
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
		requireMarker(exam.getModuleId(), actor);
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
		if (participant.getEvaluatedStoredFileName() == null) {
			throw notFound();
		}
		try {
			return Files.readAllBytes(files.resolve(participant.getEvaluatedStoredFileName()));
		}
		catch (IOException exception) {
			throw notFound();
		}
	}

	private SubmissionView view(LiveExamParticipant participant, LiveExam exam) {
		String evaluatorName = null;
		if (participant.getEvaluatedBy() != null) {
			evaluatorName = admins.findById(participant.getEvaluatedBy()).map(profile -> profile.getFullName()).orElse(null);
		}
		return new SubmissionView(participant.getId(), exam.getId(), participant.getStudentId(),
				studentName(participant.getStudentId()), participant.getStatus(), participant.getScore(), maximum(exam),
				participant.getPassed(), participant.getKind(), participant.getAnswerStoredFileName() != null,
				participant.getEvaluatedStoredFileName() == null ? null : Boolean.TRUE, evaluatorName);
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
		if (actor.role() == Role.MASTER_ADMIN || actor.role() == Role.SYSTEM_ADMIN) {
			return;
		}
		if (assignedInstructor(moduleId, actor)) {
			return;
		}
		throw denied();
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

	private static boolean passed(int score, LiveExam exam, int maximum) {
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

	public record SubmissionView(UUID id, UUID examId, UUID studentId, String studentName, String status, Integer score,
			int maximum, Boolean passed, String kind, boolean answerPdf, Boolean evaluatedPdf, String evaluatorName) {
	}

	public record Leaderboard(List<LeaderboardRow> rows) {
	}

	public record LeaderboardRow(String name, int rank, int score, int maximum, boolean passed) {
	}

	private record BoardRow(String name, int score, int maximum, boolean passed) {
	}

}
