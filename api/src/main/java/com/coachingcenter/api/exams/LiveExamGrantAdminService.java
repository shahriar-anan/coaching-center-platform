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

	private final UserRepository users;

	private final AuditRecorder audit;

	private final Clock clock;

	public LiveExamGrantAdminService(LiveExamGrantRepository grants, LiveExamModuleRepository modules,
			LiveExamRepository exams, UserRepository users, AuditRecorder audit, Clock clock) {
		this.grants = grants;
		this.modules = modules;
		this.exams = exams;
		this.users = users;
		this.audit = audit;
		this.clock = clock;
	}

	/**
	 * Grants a student access to one module or one exam, by hand, with a reason. Refused with 409 while the student
	 * already has an active grant on the same target.
	 */
	@Transactional
	public GrantView grant(CurrentActor actor, GrantRequest request) {
		String scope = request.scope().trim().toUpperCase();
		UUID moduleId;
		UUID examId;
		if (LiveExamOrder.MODULE.equals(scope)) {
			if (request.moduleId() == null || request.examId() != null) {
				throw validationFailed();
			}
			moduleId = request.moduleId();
			examId = null;
			if (modules.findByIdAndDeletedAtIsNull(moduleId).isEmpty()) {
				throw notFound();
			}
		}
		else if (LiveExamOrder.EXAM.equals(scope)) {
			if (request.examId() == null) {
				throw validationFailed();
			}
			LiveExam exam = exams.findByIdAndDeletedAtIsNull(request.examId()).orElseThrow(this::notFound);
			if (request.moduleId() != null && !request.moduleId().equals(exam.getModuleId())) {
				throw validationFailed();
			}
			moduleId = exam.getModuleId();
			examId = exam.getId();
		}
		else {
			throw validationFailed();
		}
		User student = users.findByIdAndDeletedAtIsNull(request.studentId())
			.filter(user -> Role.STUDENT.name().equals(user.getRole()))
			.orElseThrow(this::notFound);
		boolean active = LiveExamOrder.MODULE.equals(scope)
				? grants.existsByStudentIdAndScopeAndModuleIdAndRevokedAtIsNull(student.getId(), scope, moduleId)
				: grants.existsByStudentIdAndScopeAndExamIdAndRevokedAtIsNull(student.getId(), scope, examId);
		if (active) {
			throw new ApiException(ErrorCode.GRANT_ALREADY_ACTIVE, HttpStatus.CONFLICT,
					"This student already has active access to that module or exam.");
		}
		LiveExamGrant grant = new LiveExamGrant();
		grant.setId(UUID.randomUUID());
		grant.setStudentId(student.getId());
		grant.setScope(scope);
		grant.setModuleId(moduleId);
		grant.setExamId(examId);
		grant.setSource(LiveExamGrant.SOURCE_MANUAL);
		grant.setGrantedBy(actor.userId());
		grant.setReason(request.reason().trim());
		grant.setCreatedAt(clock.instant());
		grants.saveAndFlush(grant);
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
