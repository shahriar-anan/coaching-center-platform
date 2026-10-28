package com.coachingcenter.api.exams;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.users.User;
import com.coachingcenter.api.users.UserRepository;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Access that staff give by hand, and take back (R-2, R-3). Purchases stay in {@link LiveExamOrderService}. */
@Service
public class LiveExamGrantAdminService {

	private final LiveExamGrantRepository grants;

	private final LiveExamModuleRepository modules;

	private final LiveExamRepository exams;

	private final LiveExamMemberships memberships;

	private final UserRepository users;

	private final AuditRecorder audit;

	private final Clock clock;

	private final StudentNoticeWriter notices;

	private final LiveExamAccess access;

	public LiveExamGrantAdminService(LiveExamGrantRepository grants, LiveExamModuleRepository modules,
			LiveExamRepository exams, UserRepository users, AuditRecorder audit, Clock clock, StudentNoticeWriter notices,
			LiveExamMemberships memberships, LiveExamAccess access) {
		this.access = access;
		this.memberships = memberships;
		this.grants = grants;
		this.modules = modules;
		this.exams = exams;
		this.users = users;
		this.audit = audit;
		this.clock = clock;
		this.notices = notices;
	}

	/**
	 * Grants a student access to one module or one exam, by hand, with a reason. Refused with 409 while the student
	 * already has an active grant on the same target.
	 */
	@Transactional
	public GrantView grant(CurrentActor actor, GrantRequest request) {
		String scope = request.scope().trim().toUpperCase();
		UUID moduleId;
		LiveExam exam = null;
		if (LiveExamOrder.MODULE.equals(scope)) {
			if (request.moduleId() == null || request.examId() != null) {
				throw validationFailed();
			}
			moduleId = request.moduleId();
			if (modules.findByIdAndDeletedAtIsNull(moduleId).isEmpty()) {
				throw notFound();
			}
		}
		else if (LiveExamOrder.EXAM.equals(scope)) {
			if (request.examId() == null) {
				throw validationFailed();
			}
			exam = exams.findByIdAndDeletedAtIsNull(request.examId()).orElseThrow(this::notFound);
			// An exam grant names no package (R5). A package named here must be one the exam is in now.
			if (request.moduleId() != null && !memberships.activeModuleIds(exam.getId()).contains(request.moduleId())) {
				throw validationFailed();
			}
			moduleId = null;
		}
		else {
			throw validationFailed();
		}
		User student = users.findByIdAndDeletedAtIsNull(request.studentId())
			.filter(user -> Role.STUDENT.name().equals(user.getRole()))
			.orElseThrow(this::notFound);
		Instant now = clock.instant();
		if (exam == null) {
			if (access.holdsModuleGrant(student.getId(), moduleId, now)) {
				throw grantActive();
			}
			access.retireExpiredGrants(student.getId(), scope, moduleId, null, now, LiveExamAccess.RETIRED);
		}
		else {
			if (access.holdsExamGrant(student.getId(), exam, now)) {
				throw grantActive();
			}
			access.retireExpiredGrants(student.getId(), scope, null, exam, now, LiveExamAccess.RETIRED);
		}
		LiveExamGrant grant = new LiveExamGrant();
		grant.setId(UUID.randomUUID());
		grant.setStudentId(student.getId());
		grant.setScope(scope);
		grant.setModuleId(moduleId);
		grant.setExamId(exam == null ? null : exam.getId());
		grant.setSource(LiveExamGrant.SOURCE_MANUAL);
		grant.setGrantedBy(actor.userId());
		grant.setReason(request.reason().trim());
		grant.setCreatedAt(now);
		grants.saveAndFlush(grant);
		notices.purchaseGranted(student.getId(), exam == null ? moduleId : exam.getId(), scope);
		audit.record(AuditRecorder.ACCESS_GRANTED, AuditRecorder.LIVE_EXAM_GRANT_ENTITY, grant.getId());
		return view(grant);
	}

	/**
	 * Takes back access from a purchase or a manual grant (R-2). The row stays for the audit trail, and no money moves.
	 * A grant that is already revoked is not found.
	 */
	@Transactional
	public GrantView revoke(CurrentActor actor, UUID grantId, String reason) {
		String text = reason == null ? "" : reason.trim();
		if (text.isEmpty() || text.length() > 500) {
			throw validationFailed();
		}
		LiveExamGrant grant = grants.findById(grantId)
			.filter(found -> found.getRevokedAt() == null)
			.orElseThrow(this::notFound);
		grant.setRevokedAt(clock.instant());
		grant.setRevokedBy(actor.userId());
		grant.setRevokeReason(text);
		grants.saveAndFlush(grant);
		audit.record(AuditRecorder.ACCESS_REVOKED, AuditRecorder.LIVE_EXAM_GRANT_ENTITY, grant.getId());
		return view(grant);
	}

	static GrantView view(LiveExamGrant grant) {
		return new GrantView(grant.getId(), grant.getStudentId(), grant.getScope(), grant.getModuleId(), grant.getExamId(),
				grant.getSource(), grant.getGrantedBy(), grant.getReason(), grant.getCreatedAt(), grant.getRevokedAt(),
				grant.getRevokedBy(), grant.getRevokeReason());
	}

	private static ApiException grantActive() {
		return new ApiException(ErrorCode.GRANT_ALREADY_ACTIVE, HttpStatus.CONFLICT,
				"This student already has active access to that module or exam.");
	}

	private static ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
	}

	private ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	/** {@code scope} is MODULE (with {@code moduleId}) or EXAM (with {@code examId}). */
	public record GrantRequest(@NotNull UUID studentId, @NotBlank String scope, UUID moduleId, UUID examId,
			@NotBlank @Size(max = 500) String reason) {
	}

	public record GrantView(UUID id, UUID studentId, String scope, UUID moduleId, UUID examId, String source,
			UUID grantedBy, String reason, Instant createdAt, Instant revokedAt, UUID revokedBy, String revokeReason) {
	}

}
