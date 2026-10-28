package com.coachingcenter.api.exams;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * Who may read and sit an exam (NR-01, R4 and A2'). Access comes from a roster row or a module grant on a package the exam is
 * in, or from a grant on the exam itself. A package the exam was removed from keeps counting for a student who held it
 * before the removal, while their access lasts.
 */
@Component
public class LiveExamAccess {

	private final LiveExamRosterRepository roster;

	private final LiveExamGrantRepository grants;

	private final LiveExamPackageExamRepository memberships;

	private final LiveExamParticipantRepository participants;

	private final Clock clock;

	public LiveExamAccess(LiveExamRosterRepository roster, LiveExamGrantRepository grants,
			LiveExamPackageExamRepository memberships, LiveExamParticipantRepository participants, Clock clock) {
		this.roster = roster;
		this.grants = grants;
		this.memberships = memberships;
		this.participants = participants;
		this.clock = clock;
	}

	/** The student's access to the exam at {@code now}. The grant start that gives the latest end is the one that counts. */
	public AccessState state(UUID studentId, LiveExam exam, Instant now) {
		Optional<Candidate> best = candidates(studentId, exam).stream()
			.max(Comparator.comparing(Candidate::grantStart));
		if (best.isEmpty()) {
			return AccessState.NONE;
		}
		Candidate grant = best.get();
		Instant endsAt = AccessWindow.end(exam, grant.grantStart());
		return new AccessState(true, grant.source(), grant.grantStart(), endsAt,
				AccessWindow.expired(exam, grant.grantStart(), now));
	}

	/** Granted and not expired. */
	public boolean canSit(UUID studentId, LiveExam exam, Instant now) {
		AccessState state = state(studentId, exam, now);
		return state.granted() && !state.expired();
	}

	/** Any grant, roster row or attempt ever, revoked or expired. Used to let a student read their own results. */
	public boolean hasEver(UUID studentId, LiveExam exam) {
		for (UUID moduleId : packageIds(exam.getId())) {
			if (roster.existsByModuleIdAndStudentId(moduleId, studentId) || grants.existsByStudentIdAndModuleId(studentId, moduleId)) {
				return true;
			}
		}
		return grants.existsByStudentIdAndExamId(studentId, exam.getId())
				|| participants.existsByExamIdAndStudentIdAndDeletedAtIsNull(exam.getId(), studentId);
	}

	/** Granted, ignoring expiry. Kept for callers that predate {@link #canSit}; with no access days set, the two agree. */
	@Deprecated
	public boolean hasExam(UUID studentId, LiveExam exam) {
		return state(studentId, exam, clock.instant()).granted();
	}

	public boolean hasModule(UUID studentId, UUID moduleId) {
		return roster.existsByModuleIdAndStudentId(moduleId, studentId)
				|| grants.existsByStudentIdAndModuleIdAndRevokedAtIsNull(studentId, moduleId);
	}

	private List<Candidate> candidates(UUID studentId, LiveExam exam) {
		List<Candidate> found = new ArrayList<>();
		for (LiveExamPackageExam membership : memberships.findByExamId(exam.getId())) {
			UUID moduleId = membership.getModuleId();
			Instant removedAt = membership.getRemovedAt();
			roster.findById(new LiveExamRoster.Key(moduleId, studentId)).ifPresent(row -> {
				if (covers(row.getCreatedAt(), removedAt)) {
					found.add(new Candidate("ROSTER", row.getCreatedAt()));
				}
			});
			for (LiveExamGrant grant : grants.findByStudentIdAndScopeAndModuleId(studentId, LiveExamOrder.MODULE, moduleId)) {
				if (grant.getRevokedAt() == null && covers(grant.getCreatedAt(), removedAt)) {
					found.add(new Candidate("MODULE", grant.getCreatedAt()));
				}
			}
		}
		for (LiveExamGrant grant : grants.findByStudentIdAndScopeAndExamId(studentId, LiveExamOrder.EXAM, exam.getId())) {
			if (grant.getRevokedAt() == null) {
				found.add(new Candidate("EXAM", grant.getCreatedAt()));
			}
		}
		return found;
	}

	/** A package still covers a grant made before it was removed (R4). An active package covers every grant. */
	private static boolean covers(Instant grantStart, Instant removedAt) {
		return removedAt == null || grantStart.isBefore(removedAt);
	}

	private List<UUID> packageIds(UUID examId) {
		return memberships.findByExamId(examId).stream().map(LiveExamPackageExam::getModuleId).toList();
	}

	private record Candidate(String source, Instant grantStart) {
	}

}
