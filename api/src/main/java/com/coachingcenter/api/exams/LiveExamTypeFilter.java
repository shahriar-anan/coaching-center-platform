package com.coachingcenter.api.exams;

import java.util.Locale;

import org.springframework.http.HttpStatus;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

/**
 * The optional {@code examType} filter of the two catalog doors. {@link #ANY} keeps today's unfiltered behavior and is
 * what the repository queries compare against.
 */
final class LiveExamTypeFilter {

	static final String ANY = "";

	private LiveExamTypeFilter() {
	}

	static String parse(String value) {
		if (value == null || value.isBlank()) {
			return ANY;
		}
		String type = value.trim().toUpperCase(Locale.ROOT);
		if (!LiveExam.MCQ.equals(type) && !LiveExam.WRITTEN.equals(type)) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
		}
		return type;
	}

}
