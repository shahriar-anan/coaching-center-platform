package com.coachingcenter.api.exams;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

/**
 * The access an offline admission gives (plan section 4.4): one {@code OFFLINE} grant per exam or package, capped at the end of the
 * course, with no per-item notice or mail (C5). The offline admission service calls this; the rules for an ordinary grant stay in
 * {@link LiveExamGrantAdminService}, which this does not replace. The package-private helpers of the exam access code are used
 * here so that the same expiry and overlap rules apply.
 */
@Component
public class OfflineAccessGrants {

	/** The reason stored on every grant an offline admission makes. */
	public static final String REASON = "Offline admission";

	private final LiveExamGrantRepository grants;

	private final LiveExamRepository exams;

	private final LiveExamModuleRepository modules;

	private final LiveExamAccess access;

	private final LiveExamOverlaps overlaps;

	public OfflineAccessGrants(LiveExamGrantRepository grants, LiveExamRepository exams, LiveExamModuleRepository modules,
			LiveExamAccess access, LiveExamOverlaps overlaps) {
		this.grants = grants;
		this.exams = exams;
		this.modules = modules;
		this.access = access;
		this.overlaps = overlaps;
	}

	/**
	 * Whether the student already holds this exam or package by a live grant (not revoked, not expired). A package also counts when
	 * the student is live on its roster. Only the same target counts: access through another package is an overlap, which is shown
	 * as a warning and never blocks (plan 4.4).
	 */
	@org.springframework.transaction.annotation.Transactional(readOnly = true)
	public boolean holds(UUID studentId, String scope, UUID moduleId, UUID examId, Instant now) {
		if (LiveExamAccess.EXAM.equals(scope)) {
			LiveExam exam = requireExam(examId);
			return access.holdsExamGrant(studentId, exam, now);
		}
		return access.holdsModuleGrant(studentId, moduleId, now) || access.rosterLive(studentId, moduleId, now);
	}

	/** The exams and packages the student already holds that overlap this item. Money is never shown here. */
	@org.springframework.transaction.annotation.Transactional(readOnly = true)
	public List<LiveExamGrantAdminService.QuoteOverlap> overlaps(UUID studentId, String scope, UUID moduleId, UUID examId,
			Instant now) {
		if (LiveExamAccess.EXAM.equals(scope)) {
			return overlaps.forExam(studentId, requireExam(examId), now, false);
		}
		return overlaps.forPackage(studentId, moduleId, now, false);
	}

	/** The title of an exam or package, English and Bangla, for a message or a list. Null when it no longer exists. */
	@org.springframework.transaction.annotation.Transactional(readOnly = true)
	public String[] titles(String scope, UUID moduleId, UUID examId) {
		if (LiveExamAccess.EXAM.equals(scope)) {
			return exams.findById(examId).map(exam -> new String[] { exam.getTitleEn(), exam.getTitleBn() }).orElse(null);
		}
		return modules.findById(moduleId).map(module -> new String[] { module.getTitleEn(), module.getTitleBn() }).orElse(null);
	}

	/**
	 * Gives the student an {@code OFFLINE} grant on this exam or package, capped at {@code accessEndsAt}. An expired grant on the same
	 * target is retired first, since the database allows one live grant per target. A refusal rolls back the caller's transaction.
	 */
	public UUID grant(UUID studentId, String scope, UUID moduleId, UUID examId, Instant accessEndsAt, UUID grantedBy, Instant now) {
		if (LiveExamAccess.EXAM.equals(scope)) {
			access.retireExpiredGrants(studentId, LiveExamAccess.EXAM, null, requireExam(examId), now, LiveExamAccess.RETIRED);
		}
		else {
			access.retireExpiredGrants(studentId, LiveExamAccess.MODULE, moduleId, null, now, LiveExamAccess.RETIRED);
		}
		LiveExamGrant grant = new LiveExamGrant();
		grant.setId(UUID.randomUUID());
		grant.setStudentId(studentId);
		grant.setScope(LiveExamAccess.EXAM.equals(scope) ? LiveExamAccess.EXAM : LiveExamAccess.MODULE);
		grant.setExamId(LiveExamAccess.EXAM.equals(scope) ? examId : null);
		grant.setModuleId(LiveExamAccess.EXAM.equals(scope) ? null : moduleId);
		grant.setSource(LiveExamGrant.SOURCE_OFFLINE);
		grant.setGrantedBy(grantedBy);
		grant.setReason(REASON);
		grant.setCreatedAt(now);
		grant.setAccessEndsAt(accessEndsAt);
		try {
			grants.saveAndFlush(grant);
		}
		catch (DataIntegrityViolationException exception) {
			throw new ApiException(ErrorCode.GRANT_ALREADY_ACTIVE, HttpStatus.CONFLICT,
					"This student already has active access to that module or exam.");
		}
		return grant.getId();
	}

	/** Moves an OFFLINE grant's end. A grant that was revoked is history and is left as it is. */
	public void capEnd(UUID grantId, Instant accessEndsAt) {
		grants.findById(grantId).filter(grant -> grant.getRevokedAt() == null).ifPresent(grant -> {
			grant.setAccessEndsAt(accessEndsAt);
			grants.saveAndFlush(grant);
		});
	}

	/** Takes back an OFFLINE grant with a reason. A grant already revoked is left as it is. */
	public void revoke(UUID grantId, UUID actorId, String reason, Instant now) {
		grants.findById(grantId).filter(grant -> grant.getRevokedAt() == null).ifPresent(grant -> {
			grant.setRevokedAt(now);
			grant.setRevokedBy(actorId);
			grant.setRevokeReason(reason);
			grants.saveAndFlush(grant);
		});
	}

	private LiveExam requireExam(UUID examId) {
		return exams.findByIdAndDeletedAtIsNull(examId)
			.orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found."));
	}

}
