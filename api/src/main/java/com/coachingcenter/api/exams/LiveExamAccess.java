package com.coachingcenter.api.exams;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * Who may read and sit an exam (NR-01, R4, A2', B7). Access comes from a roster row or a grant on a package the exam is in,
 * from a grant on the exam itself, or from a bundle or manual grant. A free exam is open to every signed-in student. When
 * several routes give access, the one that lasts longest counts, and lifetime access beats any date. A package the exam was
 * removed from keeps counting for a student who held it before the removal, while their access lasts.
 */
@Component
public class LiveExamAccess {

	static final String FREE = "FREE";

	static final String EXAM = "EXAM";

	static final String ROSTER = "ROSTER";

	static final String MODULE = "MODULE";

	static final String BUNDLE = "BUNDLE";

	static final String MANUAL = "MANUAL";

	/** Ties go to the earlier route: an exam grant, then a package, then a bundle, then a manual grant. */
	private static final List<String> PRECEDENCE = List.of(EXAM, ROSTER, MODULE, BUNDLE, MANUAL);

	private final LiveExamRosterRepository roster;

	private final LiveExamGrantRepository grants;

	private final LiveExamPackageExamRepository memberships;

	private final LiveExamParticipantRepository participants;

	private final LiveExamRepository exams;

	private final Clock clock;

	public LiveExamAccess(LiveExamRosterRepository roster, LiveExamGrantRepository grants,
			LiveExamPackageExamRepository memberships, LiveExamParticipantRepository participants, LiveExamRepository exams,
			Clock clock) {
		this.roster = roster;
		this.grants = grants;
		this.memberships = memberships;
		this.participants = participants;
		this.exams = exams;
		this.clock = clock;
	}

	/** The student's access to the exam at {@code now}. */
	public AccessState state(UUID studentId, LiveExam exam, Instant now) {
		if (free(exam)) {
			return new AccessState(true, FREE, null, null, false);
		}
		return best(candidates(studentId, exam), now);
	}

	/** Granted and not expired. */
	public boolean canSit(UUID studentId, LiveExam exam, Instant now) {
		AccessState state = state(studentId, exam, now);
		return state.granted() && !state.expired();
	}

	/**
	 * Any grant, roster row or attempt the student ever had on the exam, revoked or expired. A revoked grant counts only
	 * through an attempt. Used to let a student read their own results.
	 */
	public boolean hasEver(UUID studentId, LiveExam exam) {
		return !candidates(studentId, exam).isEmpty()
				|| participants.existsByExamIdAndStudentIdAndDeletedAtIsNull(exam.getId(), studentId);
	}

	/** Granted, ignoring expiry. Kept for callers that predate {@link #canSit}; with no access days set, the two agree. */
	@Deprecated
	public boolean hasExam(UUID studentId, LiveExam exam) {
		return state(studentId, exam, clock.instant()).granted();
	}

	/** Module-wide access: a roster row or a package grant. It does not look at expiry. */
	public boolean hasModule(UUID studentId, UUID moduleId) {
		return roster.existsByModuleIdAndStudentId(moduleId, studentId)
				|| grants.existsByStudentIdAndScopeAndModuleIdAndRevokedAtIsNull(studentId, LiveExamOrder.MODULE, moduleId);
	}

	/**
	 * The student's access to a package at {@code now}: a roster row or a package grant. It ends when the package's paid
	 * exams have all run out (see {@link #moduleEnd}).
	 */
	public AccessState moduleState(UUID studentId, UUID moduleId, Instant now) {
		List<Candidate> found = new ArrayList<>();
		roster.findById(new LiveExamRoster.Key(moduleId, studentId))
			.ifPresent(row -> found.add(new Candidate(ROSTER, row.getCreatedAt(), moduleEnd(moduleId, row.getCreatedAt()))));
		for (LiveExamGrant grant : grants.findByStudentIdAndScopeAndModuleId(studentId, LiveExamOrder.MODULE, moduleId)) {
			if (grant.getRevokedAt() == null) {
				found.add(new Candidate(moduleSource(grant), grant.getCreatedAt(), moduleEnd(moduleId, grant.getCreatedAt())));
			}
		}
		return best(found, now);
	}

	/**
	 * When access that a package gives, started at {@code grantStart}, ends: the latest end among its paid exams that the
	 * grant covers. Null (lifetime) when one of them never ends, or when there is no paid exam to end.
	 */
	Instant moduleEnd(UUID moduleId, Instant grantStart) {
		Instant latest = null;
		boolean paid = false;
		for (LiveExamPackageExam membership : memberships.findByModuleId(moduleId)) {
			if (!covers(grantStart, membership.getRemovedAt())) {
				continue;
			}
			LiveExam exam = exams.findByIdAndDeletedAtIsNull(membership.getExamId()).orElse(null);
			if (exam == null || exam.isFree()) {
				continue;
			}
			paid = true;
			Instant end = AccessWindow.end(exam, grantStart);
			if (end == null) {
				return null;
			}
			if (latest == null || end.isAfter(latest)) {
				latest = end;
			}
		}
		return paid ? latest : null;
	}

	/** A live exam grant: not revoked and not expired. Only the exam's own grants count, not packages. */
	boolean holdsExamGrant(UUID studentId, LiveExam exam, Instant now) {
		for (LiveExamGrant grant : grants.findByStudentIdAndScopeAndExamId(studentId, LiveExamOrder.EXAM, exam.getId())) {
			if (grant.getRevokedAt() == null && !expired(AccessWindow.end(exam, grant.getCreatedAt()), now)) {
				return true;
			}
		}
		return false;
	}

	/** A live package grant: not revoked and not expired. */
	boolean holdsModuleGrant(UUID studentId, UUID moduleId, Instant now) {
		for (LiveExamGrant grant : grants.findByStudentIdAndScopeAndModuleId(studentId, LiveExamOrder.MODULE, moduleId)) {
			if (grant.getRevokedAt() == null && !expired(moduleEnd(moduleId, grant.getCreatedAt()), now)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Ends the grants of one target that have run out, so that a new purchase or grant can take their place. The database
	 * allows one unrevoked grant per student and target, so an expired one must be revoked first. It is kept for the record,
	 * with the reason. Only called once the target is no longer live for the student.
	 */
	void retireExpiredGrants(UUID studentId, String scope, UUID moduleId, LiveExam exam, Instant now, String reason) {
		if (LiveExamOrder.EXAM.equals(scope)) {
			for (LiveExamGrant grant : grants.findByStudentIdAndScopeAndExamId(studentId, LiveExamOrder.EXAM, exam.getId())) {
				if (grant.getRevokedAt() == null && expired(AccessWindow.end(exam, grant.getCreatedAt()), now)) {
					retire(grant, now, reason);
				}
			}
			return;
		}
		for (LiveExamGrant grant : grants.findByStudentIdAndScopeAndModuleId(studentId, LiveExamOrder.MODULE, moduleId)) {
			if (grant.getRevokedAt() == null && expired(moduleEnd(moduleId, grant.getCreatedAt()), now)) {
				retire(grant, now, reason);
			}
		}
	}

	static boolean free(LiveExam exam) {
		return exam.isFree() && LiveExam.PUBLISHED.equals(exam.getStatus());
	}

	private void retire(LiveExamGrant grant, Instant now, String reason) {
		grant.setRevokedAt(now);
		grant.setRevokeReason(reason);
		grants.saveAndFlush(grant);
	}

	private List<Candidate> candidates(UUID studentId, LiveExam exam) {
		List<Candidate> found = new ArrayList<>();
		for (LiveExamGrant grant : grants.findByStudentIdAndScopeAndExamId(studentId, LiveExamOrder.EXAM, exam.getId())) {
			if (grant.getRevokedAt() == null) {
				found.add(new Candidate(examSource(grant), grant.getCreatedAt(),
						AccessWindow.end(exam, grant.getCreatedAt())));
			}
		}
		for (LiveExamPackageExam membership : memberships.findByExamId(exam.getId())) {
			UUID moduleId = membership.getModuleId();
			Instant removedAt = membership.getRemovedAt();
			roster.findById(new LiveExamRoster.Key(moduleId, studentId)).ifPresent(row -> {
				if (covers(row.getCreatedAt(), removedAt)) {
					found.add(new Candidate(ROSTER, row.getCreatedAt(), AccessWindow.end(exam, row.getCreatedAt())));
				}
			});
			for (LiveExamGrant grant : grants.findByStudentIdAndScopeAndModuleId(studentId, LiveExamOrder.MODULE, moduleId)) {
				if (grant.getRevokedAt() == null && covers(grant.getCreatedAt(), removedAt)) {
					found.add(new Candidate(moduleSource(grant), grant.getCreatedAt(),
							AccessWindow.end(exam, grant.getCreatedAt())));
				}
			}
		}
		found.sort(Comparator.comparingInt(candidate -> PRECEDENCE.indexOf(candidate.source())));
		return found;
	}

	/** The route that lasts longest. Lifetime beats any date, and a tie keeps the earlier route in {@link #PRECEDENCE}. */
	private static AccessState best(List<Candidate> found, Instant now) {
		Candidate best = null;
		for (Candidate candidate : found) {
			if (best == null || outlasts(candidate.endsAt(), best.endsAt())) {
				best = candidate;
			}
		}
		if (best == null) {
			return AccessState.NONE;
		}
		return new AccessState(true, best.source(), best.grantStart(), best.endsAt(), expired(best.endsAt(), now));
	}

	private static boolean outlasts(Instant candidate, Instant current) {
		return current != null && (candidate == null || candidate.isAfter(current));
	}

	private static boolean expired(Instant endsAt, Instant now) {
		return endsAt != null && !now.isBefore(endsAt);
	}

	private static String examSource(LiveExamGrant grant) {
		if (LiveExamGrant.SOURCE_BUNDLE.equals(grant.getSource())) {
			return BUNDLE;
		}
		return LiveExamGrant.SOURCE_MANUAL.equals(grant.getSource()) ? MANUAL : EXAM;
	}

	private static String moduleSource(LiveExamGrant grant) {
		return LiveExamGrant.SOURCE_MANUAL.equals(grant.getSource()) ? MANUAL : MODULE;
	}

	/** A package still covers a grant made before it was removed (R4). An active package covers every grant. */
	private static boolean covers(Instant grantStart, Instant removedAt) {
		return removedAt == null || grantStart.isBefore(removedAt);
	}

	/** One route that gives access: its source, when it started, and when it ends (null for lifetime). */
	private record Candidate(String source, Instant grantStart, Instant endsAt) {
	}

}
