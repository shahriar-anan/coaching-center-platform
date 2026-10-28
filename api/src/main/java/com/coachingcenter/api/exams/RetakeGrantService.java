package com.coachingcenter.api.exams;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.users.StudentProfileRepository;

/**
 * A written retake, granted on a student's result by the Master Admin or a System Admin (NR-01, D19 and R14). A written exam
 * has at most one retake: once the retake a grant allows has started, no further grant is made.
 */
@Service
public class RetakeGrantService {

	private static final int MAX_REASON = 500;

	private final LiveExamRepository exams;

	private final LiveExamParticipantRepository participants;

	private final LiveExamRetakeGrantRepository grants;

	private final StudentProfileRepository students;

	private final LiveExamSittingService sittings;

	private final StudentNoticeWriter notices;

	private final AuditRecorder audit;

	private final Clock clock;

	public RetakeGrantService(LiveExamRepository exams, LiveExamParticipantRepository participants,
			LiveExamRetakeGrantRepository grants, StudentProfileRepository students, LiveExamSittingService sittings,
			StudentNoticeWriter notices, AuditRecorder audit, Clock clock) {
		this.exams = exams;
		this.participants = participants;
		this.grants = grants;
		this.students = students;
		this.sittings = sittings;
		this.notices = notices;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional
	public GrantView grant(UUID examId, UUID studentId, String reason, UUID grantedBy) {
		LiveExam exam = exams.findByIdAndDeletedAtIsNull(examId).orElseThrow(this::notFound);
		if (!LiveExam.WRITTEN.equals(exam.getExamType())) {
			throw new ApiException(ErrorCode.EXAM_TYPE_MISMATCH, HttpStatus.CONFLICT, "Only a written exam has a retake grant.");
		}
		if (studentId == null || !students.existsById(studentId)) {
			throw notFound();
		}
		String note = reason == null || reason.isBlank() ? null : reason.trim();
		if (note != null && note.length() > MAX_REASON) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
		}
		List<LiveExamParticipant> attempts = participants
			.findByExamIdAndStudentIdAndDeletedAtIsNullOrderByAttemptNoDesc(examId, studentId);
		// A sitting past its deadline is posted first, so a student whose time has just run out can be granted a retake.
		attempts.forEach(attempt -> sittings.settleParticipant(attempt.getId()));
		LiveExamParticipant latest = attempts.stream()
			.filter(attempt -> !LiveExamParticipant.IN_PROGRESS.equals(attempt.getStatus()))
			.findFirst()
			.orElseThrow(() -> retakeNotAllowed("The student has no finished attempt to retake."));
		LiveExamRetakeGrant active = grants.findActive(examId, studentId).orElse(null);
		if ((active != null && active.getUsedParticipantId() != null) || attempts.size() > 1) {
			throw retakeNotAllowed("A written exam has one retake, and it was already used.");
		}
		if (active != null) {
			throw new ApiException(ErrorCode.GRANT_ALREADY_ACTIVE, HttpStatus.CONFLICT, "A retake is already granted.");
		}
		LiveExamRetakeGrant grant = new LiveExamRetakeGrant();
		grant.setExamId(examId);
		grant.setStudentId(studentId);
		grant.setGrantedBy(grantedBy);
		grant.setReason(note);
		grant.setCreatedAt(clock.instant());
		grant = grants.save(grant);
		notices.retakeAvailable(examId, studentId);
		audit.record(AuditRecorder.LIVE_EXAM_RETAKE_GRANT, AuditRecorder.LIVE_EXAM_PARTICIPANT_ENTITY, latest.getId());
		return view(grant);
	}

	/** Revokes a grant that has not been used. A used one stays: the retake it allowed is on the student's record. */
	@Transactional
	public void revoke(UUID examId, UUID grantId, UUID revokedBy) {
		LiveExamRetakeGrant grant = grants.findById(grantId).orElseThrow(this::notFound);
		if (!examId.equals(grant.getExamId()) || grant.getRevokedAt() != null) {
			throw notFound();
		}
		if (grant.getUsedParticipantId() != null) {
			throw retakeNotAllowed("The retake was already used.");
		}
		grant.setRevokedAt(clock.instant());
		grant.setRevokedBy(revokedBy);
		UUID entity = participants.findByExamIdAndStudentIdAndDeletedAtIsNullOrderByAttemptNoDesc(examId, grant.getStudentId())
			.stream()
			.findFirst()
			.map(LiveExamParticipant::getId)
			.orElse(grantId);
		audit.record(AuditRecorder.LIVE_EXAM_RETAKE_REVOKE, AuditRecorder.LIVE_EXAM_PARTICIPANT_ENTITY, entity);
	}

	private static GrantView view(LiveExamRetakeGrant grant) {
		return new GrantView(grant.getId(), grant.getExamId(), grant.getStudentId(), grant.getReason(), grant.getGrantedBy(),
				grant.getCreatedAt(), grant.getUsedParticipantId() != null);
	}

	private static ApiException retakeNotAllowed(String message) {
		return new ApiException(ErrorCode.RETAKE_NOT_ALLOWED, HttpStatus.CONFLICT, message);
	}

	private ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	public record GrantView(UUID id, UUID examId, UUID studentId, String reason, UUID grantedBy, Instant createdAt,
			boolean used) {
	}

}
