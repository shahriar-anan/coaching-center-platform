package com.coachingcenter.api.exams;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.users.User;
import com.coachingcenter.api.users.UserRepository;

/**
 * The instructor on an exam (NR-01, R3). Marking, the submission list and the written paper all read
 * {@code live_exams.instructor_id}. A package has no instructor of its own; it counts for a teacher through its exams.
 *
 * <p>
 * A published exam takes an instructor change as a staged edit (NR-01, E31): the live row keeps its instructor until the
 * change is published, and the change shows in the exam's change list and the package's staged summary. A draft is written
 * straight to the row.
 */
@Service
public class LiveExamInstructorService {

	private final LiveExamRepository exams;

	private final UserRepository users;

	private final AuditRecorder audit;

	private final LiveExamRevisionService staging;

	public LiveExamInstructorService(LiveExamRepository exams,
			UserRepository users, AuditRecorder audit, LiveExamRevisionService staging) {
		this.exams = exams;
		this.users = users;
		this.audit = audit;
		this.staging = staging;
	}

	@Transactional
	public Assignment assign(UUID examId, UUID instructorId) {
		LiveExam exam = exams.findByIdAndDeletedAtIsNull(examId).orElseThrow(this::notFound);
		requireWritten(exam);
		if (LiveExam.PUBLISHED.equals(exam.getStatus())) {
			ExamState working = staging.working(exam);
			requireUnassigned(working.instructorId);
			requireInstructor(instructorId);
			working.instructorId = instructorId;
			staging.stage(exam, working, currentActorId());
			return new Assignment(examId, instructorId);
		}
		requireUnassigned(exam.getInstructorId());
		requireInstructor(instructorId);
		exam.setInstructorId(instructorId);
		audit.record(AuditRecorder.LIVE_EXAM_INSTRUCTOR_ASSIGN, AuditRecorder.LIVE_EXAM_ENTITY, examId);
		return new Assignment(examId, instructorId);
	}

	@Transactional
	public void unassign(UUID examId) {
		LiveExam exam = exams.findByIdAndDeletedAtIsNull(examId).orElseThrow(this::notFound);
		if (LiveExam.PUBLISHED.equals(exam.getStatus())) {
			ExamState working = staging.working(exam);
			if (working.instructorId == null) {
				throw notFound();
			}
			working.instructorId = null;
			staging.stage(exam, working, currentActorId());
			return;
		}
		if (exam.getInstructorId() == null) {
			throw notFound();
		}
		exam.setInstructorId(null);
		audit.record(AuditRecorder.LIVE_EXAM_INSTRUCTOR_UNASSIGN, AuditRecorder.LIVE_EXAM_ENTITY, examId);
	}

	/** The 409 rule: an MCQ exam is scored by the system and never holds an instructor. */
	private static void requireWritten(LiveExam exam) {
		if (LiveExam.MCQ.equals(exam.getExamType())) {
			throw new ApiException(ErrorCode.INSTRUCTOR_NOT_FOR_MCQ, HttpStatus.CONFLICT, "An MCQ exam has no instructor.");
		}
	}

	/** The 409 rule: an exam holds one instructor. For a published exam the check runs against its working copy. */
	private static void requireUnassigned(UUID current) {
		if (current != null) {
			throw new ApiException(ErrorCode.INSTRUCTOR_ALREADY_ASSIGNED, HttpStatus.CONFLICT,
					"This exam already has an instructor.");
		}
	}

	private static UUID currentActorId() {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor) {
			return actor.userId();
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

	private void requireInstructor(UUID instructorId) {
		User instructor = users.findByIdAndDeletedAtIsNull(instructorId).orElseThrow(this::notFound);
		if (!Role.INSTRUCTOR.name().equals(instructor.getRole())) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
		}
	}

	private ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	public record Assignment(UUID examId, UUID instructorId) {
	}

}
