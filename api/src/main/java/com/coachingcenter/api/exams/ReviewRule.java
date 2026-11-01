package com.coachingcenter.api.exams;

import java.time.Instant;

/**
 * When a student may see the answer key and the explanations of an MCQ attempt: the review. One definition, used by every read
 * that can return them.
 *
 * <p>The review opens at submit, for every exam. Once a student submits an MCQ attempt, the answers and the explanations are
 * shown to that student at once, whatever the live window, the attempt's own deadline or the release switch
 * ({@code exam.answers_released_at} or the catalog {@code results_published}). A running attempt never shows the key: the rule
 * applies only to a submitted attempt. The score follows its own rules, which this class does not decide.
 *
 * <p>This replaces the earlier rule, which kept the key back until the attempt's deadline, the end of the live run or a release.
 * The owner decided the change on 1 November 2026, because the answers are shown at submit. Written exams are not covered here.
 *
 * <p>The older catalog path ({@link McqAttempt} on a {@link PaperComponent}) still lists its result only when the component
 * shows results (immediate or published), so its key is shown wherever that result is shown.
 */
final class ReviewRule {

	private ReviewRule() {
	}

	/** Whether the review of a submitted attempt on a live exam is open: it is, at once, once the attempt is submitted. */
	static boolean open(LiveExam exam, LiveExamParticipant participant, Instant now) {
		return participant.getSubmittedAt() != null;
	}

	/** Whether the review of a submitted attempt on the older catalog path is open: it is, at once, once the attempt is submitted. */
	static boolean open(PaperComponent component, McqAttempt attempt, Instant now) {
		return "SUBMITTED".equals(attempt.getStatus());
	}

	/** The older catalog attempt's deadline: its start plus its duration, never past the component's window end. */
	static Instant attemptDeadline(McqAttempt attempt, PaperComponent component) {
		Instant timerEnd = attempt.getStartedAt().plusSeconds(attempt.getDurationSeconds());
		Instant windowEnd = component.getEndsAt();
		if (windowEnd == null) {
			return timerEnd;
		}
		return timerEnd.isBefore(windowEnd) ? timerEnd : windowEnd;
	}

}
