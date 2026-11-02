package com.coachingcenter.api.exams;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.users.StudentProfileRepository;

/**
 * A student asks for another written retake. The request waits for the Master Admin or a System Admin, who approves it (which
 * makes the grant through {@link RetakeGrantService}) or declines it with a note the student sees. A written exam still has one
 * retake in all, and an MCQ exam is not part of this.
 */
@Service
public class RetakeRequestService {

	private static final int MAX_TEXT = 500;

	private final LiveExamRepository exams;

	private final LiveExamParticipantRepository participants;

	private final LiveExamRetakeGrantRepository grants;

	private final LiveExamRetakeRequestRepository requests;

	private final StudentProfileRepository students;

	private final LiveExamSittingService sittings;

	private final RetakeGrantService retakeGrants;

	private final StudentNoticeWriter notices;

	private final AuditRecorder audit;

	private final Clock clock;

	public RetakeRequestService(LiveExamRepository exams, LiveExamParticipantRepository participants,
			LiveExamRetakeGrantRepository grants, LiveExamRetakeRequestRepository requests, StudentProfileRepository students,
			LiveExamSittingService sittings, RetakeGrantService retakeGrants, StudentNoticeWriter notices, AuditRecorder audit,
			Clock clock) {
		this.exams = exams;
		this.participants = participants;
		this.grants = grants;
		this.requests = requests;
		this.students = students;
		this.sittings = sittings;
		this.retakeGrants = retakeGrants;
		this.notices = notices;
		this.audit = audit;
		this.clock = clock;
	}

	/** The student's latest request, and whether they can ask now. {@code blockedReason} says why not, as a code. */
	@Transactional
	public MyRequestView mine(UUID examId, UUID studentId) {
		LiveExam exam = exams.findByIdAndDeletedAtIsNull(examId).orElseThrow(this::notFound);
		RequestView latest = requests.findTopByExamIdAndStudentIdOrderByRequestedAtDesc(examId, studentId)
			.map(RetakeRequestService::view).orElse(null);
		String blocked = blockedReason(exam, studentId);
		return new MyRequestView(latest, blocked == null, blocked);
	}

	/** The student asks for a retake. The reason is optional. */
	@Transactional
	public RequestView request(UUID examId, UUID studentId, String reason) {
		LiveExam exam = exams.findByIdAndDeletedAtIsNull(examId).orElseThrow(this::notFound);
		String blocked = blockedReason(exam, studentId);
		if (blocked != null) {
			throw new ApiException(ErrorCode.RETAKE_NOT_ALLOWED, HttpStatus.CONFLICT, "A retake request is not allowed: " + blocked + ".");
		}
		LiveExamRetakeRequest request = new LiveExamRetakeRequest();
		request.setExamId(examId);
		request.setStudentId(studentId);
		request.setStatus(LiveExamRetakeRequest.PENDING);
		request.setReason(text(reason));
		request.setRequestedAt(clock.instant());
		request = requests.save(request);
		audit.record(AuditRecorder.LIVE_EXAM_RETAKE_REQUEST, AuditRecorder.LIVE_EXAM_ENTITY, examId);
		return view(request);
	}

	/** Every request with a status (PENDING by default), for the staff who decide them. */
	@Transactional(readOnly = true)
	public List<StaffRequestView> list(String status) {
		String wanted = status == null || status.isBlank() ? LiveExamRetakeRequest.PENDING : status;
		return requests.findByStatusOrderByRequestedAtAsc(wanted).stream().map(this::staffView).toList();
	}

	/** Approves a request: the grant is made, and the student is told a retake is available. */
	@Transactional
	public RequestView approve(UUID requestId, UUID actorId) {
		LiveExamRetakeRequest request = waiting(requestId);
		UUID grantId = retakeGrants.grant(request.getExamId(), request.getStudentId(), request.getReason(), actorId).id();
		request.setStatus(LiveExamRetakeRequest.APPROVED);
		request.setGrantId(grantId);
		request.setDecidedAt(clock.instant());
		request.setDecidedBy(actorId);
		return view(request);
	}

	/** Declines a request with an optional note, which the student sees on the exam's result page. */
	@Transactional
	public RequestView decline(UUID requestId, String note, UUID actorId) {
		LiveExamRetakeRequest request = waiting(requestId);
		request.setStatus(LiveExamRetakeRequest.DECLINED);
		request.setDecisionNote(text(note));
		request.setDecidedAt(clock.instant());
		request.setDecidedBy(actorId);
		notices.retakeDeclined(request.getExamId(), request.getStudentId());
		audit.record(AuditRecorder.LIVE_EXAM_RETAKE_DECLINE, AuditRecorder.LIVE_EXAM_ENTITY, request.getExamId());
		return view(request);
	}

	/**
	 * Why a student cannot ask now, or null when they can. A written exam only, after a finished attempt, with no retake
	 * already granted or used and no request waiting.
	 */
	private String blockedReason(LiveExam exam, UUID studentId) {
		if (!LiveExam.WRITTEN.equals(exam.getExamType()) || !LiveExam.PUBLISHED.equals(exam.getStatus())) {
			return "NOT_WRITTEN";
		}
		List<LiveExamParticipant> attempts = participants
			.findByExamIdAndStudentIdAndDeletedAtIsNullOrderByAttemptNoDesc(exam.getId(), studentId);
		attempts.forEach(attempt -> sittings.settleParticipant(attempt.getId()));
		LiveExamParticipant latest = attempts.stream()
			.filter(attempt -> !LiveExamParticipant.IN_PROGRESS.equals(attempt.getStatus()))
			.findFirst()
			.orElse(null);
		if (latest == null) {
			return "NO_FINISHED_ATTEMPT";
		}
		// A retake is asked for once the result is out, not while the script is still being marked.
		if (!RetakeGrantService.resultOut(latest)) {
			return "RESULT_NOT_PUBLISHED";
		}
		if (attempts.size() > 1) {
			return "RETAKE_USED";
		}
		Optional<LiveExamRetakeGrant> active = grants.findActive(exam.getId(), studentId);
		if (active.isPresent()) {
			return active.get().getUsedParticipantId() != null ? "RETAKE_USED" : "GRANT_ACTIVE";
		}
		if (requests.existsByExamIdAndStudentIdAndStatus(exam.getId(), studentId, LiveExamRetakeRequest.PENDING)) {
			return "PENDING";
		}
		return null;
	}

	private LiveExamRetakeRequest waiting(UUID requestId) {
		LiveExamRetakeRequest request = requests.findById(requestId).orElseThrow(this::notFound);
		if (!LiveExamRetakeRequest.PENDING.equals(request.getStatus())) {
			throw new ApiException(ErrorCode.RETAKE_NOT_ALLOWED, HttpStatus.CONFLICT, "This request was already decided.");
		}
		return request;
	}

	private StaffRequestView staffView(LiveExamRetakeRequest request) {
		LiveExam exam = exams.findById(request.getExamId()).orElse(null);
		String studentName = students.findById(request.getStudentId()).map(profile -> profile.getFullName()).orElse("");
		return new StaffRequestView(request.getId(), request.getExamId(), exam == null ? "" : exam.getTitleEn(),
				exam == null ? "" : exam.getTitleBn(), request.getStudentId(), studentName, request.getStatus(), request.getReason(),
				request.getDecisionNote(), request.getRequestedAt(), request.getDecidedAt());
	}

	private static RequestView view(LiveExamRetakeRequest request) {
		return new RequestView(request.getId(), request.getExamId(), request.getStudentId(), request.getStatus(), request.getReason(),
				request.getDecisionNote(), request.getRequestedAt(), request.getDecidedAt());
	}

	private static String text(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String trimmed = value.trim();
		if (trimmed.length() > MAX_TEXT) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
		}
		return trimmed;
	}

	private ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	public record RequestView(UUID id, UUID examId, UUID studentId, String status, String reason, String decisionNote,
			Instant requestedAt, Instant decidedAt) {
	}

	/** The student's side: the latest request, whether a new one can be made, and why not when it cannot. */
	public record MyRequestView(RequestView request, boolean canRequest, String blockedReason) {
	}

	public record StaffRequestView(UUID id, UUID examId, String examTitleEn, String examTitleBn, UUID studentId, String studentName,
			String status, String reason, String decisionNote, Instant requestedAt, Instant decidedAt) {
	}

}
