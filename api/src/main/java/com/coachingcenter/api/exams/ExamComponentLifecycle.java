package com.coachingcenter.api.exams;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

final class ExamComponentLifecycle {

	private ExamComponentLifecycle() {
	}

	/** The lifecycle at {@code at}, which the caller takes from the application clock. */
	static String derive(PaperComponent component, Instant at) {
		if (!component.isScheduled()) {
			return "DRAFT";
		}
		if (component.getStartsAt() == null || component.getEndsAt() == null) {
			return "DRAFT";
		}
		Instant now = at.truncatedTo(ChronoUnit.MILLIS);
		if (now.isBefore(component.getStartsAt())) {
			return "SCHEDULED";
		}
		if (now.isBefore(component.getEndsAt())) {
			return "ACTIVE";
		}
		return "ENDED";
	}

}
