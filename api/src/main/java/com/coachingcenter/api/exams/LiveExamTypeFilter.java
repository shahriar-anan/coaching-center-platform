package com.coachingcenter.api.exams;

import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

/**
 * The optional {@code examType}, {@code mode} and {@code phase} filters of the catalog doors and the staff exam list. {@link #ANY}
 * keeps today's unfiltered behavior and is what the repository queries compare against.
 */
final class LiveExamTypeFilter {

	static final String ANY = "";

	private static final Set<String> PHASES = Set.of(ExamWindow.UNANNOUNCED, ExamWindow.UPCOMING, ExamWindow.LIVE,
			ExamWindow.ENDED, ExamWindow.PRACTICE);

	/** The category tabs (REQ-007). An unknown value is {@link #VIEW_ALL}, not an error. */
	static final String VIEW_ALL = "ALL";

	static final String VIEW_FREE = "FREE";

	static final String VIEW_LIVE = "LIVE";

	static final String VIEW_ARCHIVED = "ARCHIVED";

	private static final Set<String> VIEWS = Set.of(VIEW_ALL, VIEW_FREE, VIEW_LIVE, VIEW_ARCHIVED);

	private LiveExamTypeFilter() {
	}

	static String parse(String value) {
		if (value == null || value.isBlank()) {
			return ANY;
		}
		String type = value.trim().toUpperCase(Locale.ROOT);
		if (!LiveExam.MCQ.equals(type) && !LiveExam.WRITTEN.equals(type)) {
			throw validationFailed();
		}
		return type;
	}

	/** {@code LIVE} or {@code PRACTICE}, or {@link #ANY}. */
	static String parseMode(String value) {
		if (value == null || value.isBlank()) {
			return ANY;
		}
		String mode = value.trim().toUpperCase(Locale.ROOT);
		if (!LiveExam.MODE_LIVE.equals(mode) && !LiveExam.MODE_PRACTICE.equals(mode)) {
			throw validationFailed();
		}
		return mode;
	}

	/** One of the {@link ExamWindow} phases, or {@link #ANY}. */
	static String parsePhase(String value) {
		if (value == null || value.isBlank()) {
			return ANY;
		}
		String phase = value.trim().toUpperCase(Locale.ROOT);
		if (!PHASES.contains(phase)) {
			throw validationFailed();
		}
		return phase;
	}

	/** One of {@link #VIEW_ALL}, {@link #VIEW_FREE}, {@link #VIEW_LIVE} or {@link #VIEW_ARCHIVED}. Unknown values read as all. */
	static String parseView(String value) {
		if (value == null || value.isBlank()) {
			return VIEW_ALL;
		}
		String view = value.trim().toUpperCase(Locale.ROOT);
		return VIEWS.contains(view) ? view : VIEW_ALL;
	}

	private static ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
	}

}
