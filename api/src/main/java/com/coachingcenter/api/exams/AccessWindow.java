package com.coachingcenter.api.exams;

import java.time.Duration;
import java.time.Instant;

/**
 * When a student's access to an exam ends (NR-01, A2' and R18). Access is counted in days from the later of the grant and
 * the window end (a live exam) or the publish time (a practice exam). Lifetime access has no end, and so does a free exam.
 */
final class AccessWindow {

	private AccessWindow() {
	}

	/**
	 * The instant access ends, or null when it never does. An undated live exam has not started counting, so it has no end
	 * yet. The end is exclusive: at the instant it is reached, access is over.
	 */
	static Instant end(LiveExam exam, Instant grantStart) {
		if (exam.isFree() || exam.getAccessDays() == null) {
			return null;
		}
		Instant anchor;
		if (LiveExam.MODE_PRACTICE.equals(exam.getMode())) {
			if (exam.getPublishedAt() == null) {
				return null;
			}
			anchor = later(grantStart, exam.getPublishedAt());
		}
		else {
			if (exam.getEndsAt() == null) {
				return null;
			}
			anchor = later(grantStart, exam.getEndsAt());
		}
		return anchor.plus(Duration.ofDays(exam.getAccessDays()));
	}

	static boolean expired(LiveExam exam, Instant grantStart, Instant now) {
		Instant end = end(exam, grantStart);
		return end != null && !now.isBefore(end);
	}

	private static Instant later(Instant grantStart, Instant other) {
		return grantStart.isAfter(other) ? grantStart : other;
	}

}
