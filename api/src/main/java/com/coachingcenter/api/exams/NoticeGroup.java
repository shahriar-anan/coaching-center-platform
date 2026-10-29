package com.coachingcenter.api.exams;

import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

/**
 * The three tabs of a student's notices (N1). This is the one place that maps a notice kind to a tab. A kind that is not a
 * result or an announcement is an exam notice, so a kind added later lands under {@link #EXAMS} until it is mapped here.
 */
public enum NoticeGroup {

	RESULTS,

	EXAMS,

	ANNOUNCEMENTS;

	private static final Set<String> RESULT_KINDS = Set.of(StudentNoticeWriter.MCQ_SCORE, StudentNoticeWriter.WRITTEN_RESULT);

	private static final Set<String> ANNOUNCEMENT_KINDS = Set.of("ANNOUNCEMENT");

	public static NoticeGroup of(String kind) {
		if (RESULT_KINDS.contains(kind)) {
			return RESULTS;
		}
		return ANNOUNCEMENT_KINDS.contains(kind) ? ANNOUNCEMENTS : EXAMS;
	}

	/**
	 * The kinds a query names for this tab. For {@link #RESULTS} and {@link #ANNOUNCEMENTS} these are the tab's own kinds.
	 * For {@link #EXAMS} they are the kinds of the other two tabs, which Exams excludes, so every other kind is Exams.
	 */
	public Set<String> kinds() {
		return switch (this) {
			case RESULTS -> RESULT_KINDS;
			case ANNOUNCEMENTS -> ANNOUNCEMENT_KINDS;
			case EXAMS -> Set.of(StudentNoticeWriter.MCQ_SCORE, StudentNoticeWriter.WRITTEN_RESULT, "ANNOUNCEMENT");
		};
	}

	/** Reads the {@code group} query value. Absent means every notice (null). An unknown value is a validation error. */
	public static NoticeGroup parse(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return valueOf(value.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException exception) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
		}
	}

}
