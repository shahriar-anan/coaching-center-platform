package com.coachingcenter.api.exams;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

final class ExamComponentLifecycle {

	private ExamComponentLifecycle() {
	}

	static String derive(PaperComponent component) {
		if (!component.isScheduled()) {
			return "DRAFT";
		}
		if (component.getStartsAt() == null || component.getEndsAt() == null) {
			return "DRAFT";
		}
		Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
		if (now.isBefore(component.getStartsAt())) {
			return "SCHEDULED";
		}
		if (now.isBefore(component.getEndsAt())) {
			return "ACTIVE";
		}
		return "ENDED";
	}

}
