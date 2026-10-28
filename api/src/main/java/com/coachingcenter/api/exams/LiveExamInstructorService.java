package com.coachingcenter.api.exams;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.users.User;
import com.coachingcenter.api.users.UserRepository;

/**
 * The instructor on an exam (NR-01, R3). Marking, the submission list and the written paper all read
 * {@code live_exams.instructor_id}; the package keeps only a default that a new exam inherits.
 */
@Service
public class LiveExamInstructorService {

	private final LiveExamRepository exams;

	private final LiveExamPackageExamRepository memberships;

	private final UserRepository users;

	private final AuditRecorder audit;

	public LiveExamInstructorService(LiveExamRepository exams, LiveExamPackageExamRepository memberships,
			UserRepository users, AuditRecorder audit) {
		this.exams = exams;
		this.memberships = memberships;
		this.users = users;
		this.audit = audit;
	}

	@Transactional
	public Assignment assign(UUID examId, UUID instructorId) {
		LiveExam exam = exams.findByIdAndDeletedAtIsNull(examId).orElseThrow(this::notFound);
		if (exam.getInstructorId() != null) {
			throw new ApiException(ErrorCode.INSTRUCTOR_ALREADY_ASSIGNED, HttpStatus.CONFLICT,
					"This exam already has an instructor.");
		}
		requireInstructor(instructorId);
		exam.setInstructorId(instructorId);
		audit.record(AuditRecorder.LIVE_EXAM_INSTRUCTOR_ASSIGN, AuditRecorder.LIVE_EXAM_ENTITY, examId);
		return new Assignment(examId, instructorId);
	}

	@Transactional
	public void unassign(UUID examId) {
		LiveExam exam = exams.findByIdAndDeletedAtIsNull(examId).orElseThrow(this::notFound);
		if (exam.getInstructorId() == null) {
			throw notFound();
		}
		exam.setInstructorId(null);
		audit.record(AuditRecorder.LIVE_EXAM_INSTRUCTOR_UNASSIGN, AuditRecorder.LIVE_EXAM_ENTITY, examId);
	}

	/** Sets the instructor of every exam now in the package, replacing any it had. Used by the package route's {@code applyToExams}. */
	@Transactional
	public int setForPackageExams(UUID moduleId, UUID instructorId) {
		int changed = 0;
		for (LiveExamPackageExam membership : memberships.findActiveByModuleId(moduleId)) {
			LiveExam exam = exams.findByIdAndDeletedAtIsNull(membership.getExamId()).orElse(null);
			if (exam != null && !instructorId.equals(exam.getInstructorId())) {
				exam.setInstructorId(instructorId);
				audit.record(AuditRecorder.LIVE_EXAM_INSTRUCTOR_ASSIGN, AuditRecorder.LIVE_EXAM_ENTITY, exam.getId());
				changed++;
			}
		}
		return changed;
	}

	/** Clears the instructor from the package's exams that hold the given one, the exams that inherited the package default. */
	@Transactional
	public int clearForPackageExams(UUID moduleId, UUID instructorId) {
		int changed = 0;
		for (LiveExamPackageExam membership : memberships.findActiveByModuleId(moduleId)) {
			LiveExam exam = exams.findByIdAndDeletedAtIsNull(membership.getExamId()).orElse(null);
			if (exam != null && instructorId.equals(exam.getInstructorId())) {
				exam.setInstructorId(null);
				audit.record(AuditRecorder.LIVE_EXAM_INSTRUCTOR_UNASSIGN, AuditRecorder.LIVE_EXAM_ENTITY, exam.getId());
				changed++;
			}
		}
		return changed;
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
