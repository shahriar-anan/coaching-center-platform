package com.coachingcenter.api.exams;

import java.time.Instant;

/**
 * The state of one exam for one student, derived from the window, the server clock, and that student's own participant
 * row. It never reads another student and never writes.
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

	private LiveExamStudentStatus() {
	}

	static Result of(LiveExam exam, LiveExamParticipant participant, Instant now) {
		if (exam.getLiveAt() == null) {
			return new Result(NOT_ANNOUNCED, false);
		}
		if (now.isBefore(exam.getLiveAt())) {
			return new Result(UPCOMING, false);
		}
		boolean written = LiveExam.WRITTEN.equals(exam.getExamType());
		if (participant != null) {
			return new Result(written ? written(participant, now) : mcq(participant, now), false);
		}
		if (now.isBefore(exam.getLiveAt().plusSeconds(exam.getDurationMinutes() * 60L))) {
			return new Result(OPEN, false);
		}
		return new Result(MISSED, !written);
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

	record Result(String status, boolean retakeAvailable) {
	}

}
