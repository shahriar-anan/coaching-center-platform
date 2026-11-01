package com.coachingcenter.api.exams;

import org.springframework.http.HttpStatus;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

/** What a review is about: an exam or a package. The path value is the word in the URL ({@code exam} or {@code package}). */
public enum LiveExamReviewKind {

	EXAM("exam", "exam_id"),

	PACKAGE("package", "module_id");

	private final String path;

	/** The review column that holds this kind's target id. A fixed name from this enum, never user input. */
	private final String column;

	LiveExamReviewKind(String path, String column) {
		this.path = path;
		this.column = column;
	}

	public String path() {
		return path;
	}

	String column() {
		return column;
	}

	/** The kind named by a filter value, or null for any other word. */
	public static LiveExamReviewKind of(String value) {
		for (LiveExamReviewKind kind : values()) {
			if (kind.path.equals(value)) {
				return kind;
			}
		}
		return null;
	}

	/** The kind named by a path segment. Any other word is not found (reviews plan, section 4). */
	public static LiveExamReviewKind require(String value) {
		LiveExamReviewKind kind = of(value);
		if (kind == null) {
			throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
		}
		return kind;
	}

}
