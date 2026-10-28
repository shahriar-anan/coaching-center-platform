package com.coachingcenter.api.exams;

import java.time.Duration;
import java.time.Instant;

import org.springframework.http.HttpStatus;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

/**
 * The live window and the duration, kept apart (NR-01, D15). The window is when a student may start: {@code liveAt}
 * inclusive, {@code endsAt} exclusive. The duration is how long a sitting lasts from its own start, and it is not capped
 * at the window end.
 */
final class ExamWindow {

	static final String UNANNOUNCED = "UNANNOUNCED";

	static final String UPCOMING = "UPCOMING";

	static final String LIVE = "LIVE";

	static final String ENDED = "ENDED";

	static final String PRACTICE = "PRACTICE";

	private ExamWindow() {
	}

	/** Where the exam stands at {@code now}. The student UI calls {@code ENDED} "archived exams". */
	static String phase(LiveExam exam, Instant now) {
		if (LiveExam.MODE_PRACTICE.equals(exam.getMode())) {
			return PRACTICE;
		}
		if (exam.getLiveAt() == null) {
			return UNANNOUNCED;
		}
		if (now.isBefore(exam.getLiveAt())) {
			return UPCOMING;
		}
		return now.isBefore(exam.getEndsAt()) ? LIVE : ENDED;
	}

	/** A student may start a first attempt once the window has opened, or at any time for a published practice exam. */
	static boolean canStartFirstAttempt(LiveExam exam, Instant now) {
		if (LiveExam.MODE_PRACTICE.equals(exam.getMode())) {
			return exam.getPublishedAt() != null;
		}
		return exam.getLiveAt() != null && !now.isBefore(exam.getLiveAt());
	}

	/** {@code LIVE} when the attempt started inside the window, {@code ARCHIVED} when it started after. Set once, at start. */
	static String firstAttemptKind(LiveExam exam, Instant startedAt) {
		if (LiveExam.MODE_PRACTICE.equals(exam.getMode())) {
			return LiveExamParticipant.PRACTICE;
		}
		if (exam.getEndsAt() == null) {
			throw new IllegalStateException("A live exam without a window has no first-attempt kind.");
		}
		return startedAt.isBefore(exam.getEndsAt()) ? LiveExamParticipant.LIVE : LiveExamParticipant.ARCHIVED;
	}

	/** A sitting's own deadline: its start plus the duration. The window end never caps it (D15). */
	static Instant deadline(LiveExam exam, Instant startedAt) {
		return startedAt.plus(Duration.ofMinutes(exam.getDurationMinutes()));
	}

	/** The latest a sitting that started inside the window can run to, or null when there is no window. */
	static Instant lastLiveDeadline(LiveExam exam) {
		if (exam.getEndsAt() == null) {
			return null;
		}
		return exam.getEndsAt().plus(Duration.ofMinutes(exam.getDurationMinutes()));
	}

	/**
	 * Rejects a window that cannot hold: a practice exam with dates, a window with only one of its two dates, or a window
	 * shorter than the duration (D16). A live exam with no dates yet is allowed (D18).
	 */
	static void validate(String mode, Instant liveAt, Instant endsAt, int durationMinutes) {
		if (LiveExam.MODE_PRACTICE.equals(mode)) {
			if (liveAt != null || endsAt != null) {
				throw invalid("A practice exam has no window.");
			}
			return;
		}
		if ((liveAt == null) != (endsAt == null)) {
			throw invalid("A window needs both its start and its end, or neither.");
		}
		if (liveAt != null && endsAt.isBefore(liveAt.plus(Duration.ofMinutes(durationMinutes)))) {
			throw invalid("The window must be at least as long as the exam duration.");
		}
	}

	private static ApiException invalid(String message) {
		return new ApiException(ErrorCode.EXAM_WINDOW_INVALID, HttpStatus.BAD_REQUEST, message);
	}

}
