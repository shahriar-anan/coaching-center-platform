package com.coachingcenter.api.exams;

import java.time.Instant;

/**
 * The state of one exam for one student, derived from the window, the server clock, that student's access and their own
 * latest attempt. It never reads another student and never writes.
 *
 * <p>{@code status} is the vocabulary the current web knows (contracts §7.8). The three states the new rules add
 * ({@link #ARCHIVED_OPEN}, {@link #PRACTICE_OPEN}, {@link #EXPIRED}) go out as {@code detail} and map to a legacy value in
 * {@code status}, so an unknown value never reaches the web's status pill.
 */
final class LiveExamStudentStatus {

	static final String NOT_ANNOUNCED = "NOT_ANNOUNCED";

	static final String UPCOMING = "UPCOMING";

	static final String OPEN = "OPEN";

	static final String IN_PROGRESS = "IN_PROGRESS";

	static final String SUBMITTED = "SUBMITTED";

	static final String AWAITING_RESULT = "AWAITING_RESULT";

	static final String RESULT_READY = "RESULT_READY";

	static final String FAILED_TO_SUBMIT = "FAILED_TO_SUBMIT";

	static final String MISSED = "MISSED";

	/** The window has ended, the student has no attempt and may still sit. Legacy: {@link #MISSED}. */
	static final String ARCHIVED_OPEN = "ARCHIVED_OPEN";

	/** A practice exam with no attempt yet, open from publish. Legacy: {@link #OPEN}. */
	static final String PRACTICE_OPEN = "PRACTICE_OPEN";

	/** The student's access has ended, so they cannot start an attempt. Legacy: {@link #MISSED}. */
	static final String EXPIRED = "EXPIRED";

	private LiveExamStudentStatus() {
	}

	/** For callers that do not know the student's access: the student is taken to hold it, and no written retake is granted. */
	static Result of(LiveExam exam, LiveExamParticipant participant, Instant now) {
		return of(exam, participant, now, true, false);
	}

	/**
	 * @param latest the student's latest attempt, or null
	 * @param canSit access holds and has not expired ({@code LiveExamAccess.canSit})
	 * @param retakeGranted the student holds an unused written retake grant
	 */
	static Result of(LiveExam exam, LiveExamParticipant latest, Instant now, boolean canSit, boolean retakeGranted) {
		String phase = ExamWindow.phase(exam, now);
		if (ExamWindow.UNANNOUNCED.equals(phase)) {
			return new Result(NOT_ANNOUNCED, false, NOT_ANNOUNCED);
		}
		if (ExamWindow.UPCOMING.equals(phase)) {
			return new Result(UPCOMING, false, UPCOMING);
		}
		boolean written = LiveExam.WRITTEN.equals(exam.getExamType());
		if (latest != null) {
			String status = written ? written(latest, now) : mcq(latest, now);
			boolean finished = finished(latest, now);
			boolean retake = canSit && finished && (written ? retakeGranted : true);
			return new Result(status, retake, status);
		}
		if (!canSit) {
			return new Result(MISSED, false, EXPIRED);
		}
		if (ExamWindow.PRACTICE.equals(phase)) {
			return new Result(OPEN, false, PRACTICE_OPEN);
		}
		if (ExamWindow.LIVE.equals(phase)) {
			return new Result(OPEN, false, OPEN);
		}
		return new Result(MISSED, !written, ARCHIVED_OPEN);
	}

	/** An attempt is finished once it is posted or past its own deadline: a student may start another MCQ attempt. */
	private static boolean finished(LiveExamParticipant participant, Instant now) {
		return participant.getSubmittedAt() != null || !now.isBefore(participant.getDeadlineAt());
	}

	private static String mcq(LiveExamParticipant participant, Instant now) {
		if (now.isBefore(participant.getDeadlineAt())) {
			return participant.getSubmittedAt() == null ? IN_PROGRESS : SUBMITTED;
		}
		return RESULT_READY;
	}

	private static String written(LiveExamParticipant participant, Instant now) {
		boolean stored = participant.getAnswerStoredFileName() != null;
		boolean past = !now.isBefore(participant.getDeadlineAt());
		if (LiveExamParticipant.FAILED_TO_SUBMIT.equals(participant.getStatus()) || (past && !stored)) {
			return FAILED_TO_SUBMIT;
		}
		if (participant.getPublishedAt() != null) {
			return RESULT_READY;
		}
		if (past) {
			return AWAITING_RESULT;
		}
		return stored ? SUBMITTED : IN_PROGRESS;
	}

	/** {@code status} is the legacy value, {@code detail} the full one ({@code myStatusDetail}). */
	record Result(String status, boolean retakeAvailable, String detail) {
	}

}
