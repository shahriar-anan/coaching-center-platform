package com.coachingcenter.api.exams;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * Where one student stands on one exam: their attempts, their access, a retake grant and the status derived from them
 * ({@link LiveExamStudentStatus}). Reads only the requesting student's rows. Every student-facing view of an exam (the
 * exam read, the lists and the routine) takes its {@code myStatus}, {@code myStatusDetail}, {@code retakeAvailable},
 * {@code sitting} and {@code attempts} from here, so they cannot disagree.
 */
@Component
public class LiveExamStudentStanding {

	private final LiveExamParticipantRepository participants;

	private final LiveExamAccess access;

	private final LiveExamRetakeGrantRepository retakeGrants;

	LiveExamStudentStanding(LiveExamParticipantRepository participants, LiveExamAccess access,
			LiveExamRetakeGrantRepository retakeGrants) {
		this.participants = participants;
		this.access = access;
		this.retakeGrants = retakeGrants;
	}

	Standing of(UUID studentId, LiveExam exam, Instant now) {
		List<LiveExamParticipant> attempts = participants
			.findByExamIdAndStudentIdAndDeletedAtIsNullOrderByAttemptNoDesc(exam.getId(), studentId);
		return build(studentId, exam, attempts, now);
	}

	/** One standing per exam, for a list. An exam the student never sat still gets one, with no attempts. */
	Map<UUID, Standing> ofAll(UUID studentId, Collection<LiveExam> exams, Instant now) {
		Map<UUID, List<LiveExamParticipant>> byExam = new HashMap<>();
		if (!exams.isEmpty()) {
			for (LiveExamParticipant participant : participants.findByStudentIdAndExamIdInAndDeletedAtIsNull(studentId,
					exams.stream().map(LiveExam::getId).toList())) {
				byExam.computeIfAbsent(participant.getExamId(), key -> new java.util.ArrayList<>()).add(participant);
			}
		}
		Map<UUID, Standing> standings = new HashMap<>();
		for (LiveExam exam : exams) {
			List<LiveExamParticipant> attempts = byExam.getOrDefault(exam.getId(), List.of()).stream()
				.sorted(Comparator.comparingInt(LiveExamParticipant::getAttemptNo).reversed())
				.toList();
			standings.put(exam.getId(), build(studentId, exam, attempts, now));
		}
		return standings;
	}

	/** {@code attempts} newest first. */
	private Standing build(UUID studentId, LiveExam exam, List<LiveExamParticipant> attempts, Instant now) {
		AccessState state = access.state(studentId, exam, now);
		boolean canSit = state.granted() && !state.expired();
		LiveExamParticipant latest = attempts.isEmpty() ? null : attempts.get(0);
		boolean retakeGranted = LiveExam.WRITTEN.equals(exam.getExamType()) && retakeGrants.findActive(exam.getId(), studentId)
			.map(grant -> grant.getUsedParticipantId() == null)
			.orElse(false);
		return new Standing(LiveExamStudentStatus.of(exam, latest, now, canSit, retakeGranted), latest, attempts.size(),
				state, retakeGranted);
	}

	/** {@code latest} is null when the student has not started; {@code attempts} is how many they started. */
	record Standing(LiveExamStudentStatus.Result status, LiveExamParticipant latest, int attempts, AccessState access,
			boolean retakeGranted) {
	}

}
