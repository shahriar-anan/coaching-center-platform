package com.coachingcenter.api.accounting;

import java.util.List;

import org.springframework.http.HttpStatus;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.error.FieldErrorDetail;

/**
 * The errors of the accounting API (contracts.md, section 1 and 5). A refusal is 409 ACC_REFUSED with the message key
 * "accounting.errors.<key>"; the web writes the words. Stale versions and missing rows use the same codes as the rest of the API.
 */
public final class AccRefusals {

	private AccRefusals() {
	}

	/** The change is not allowed now. The key is one of the refusal keys in contracts.md, section 5. */
	public static ApiException refuse(String key) {
		return new ApiException(ErrorCode.ACC_REFUSED, HttpStatus.CONFLICT, "accounting.errors." + key);
	}

	/** Someone else changed the same row first (optimistic lock). The client reloads and tries again. */
	public static ApiException stale() {
		return new ApiException(ErrorCode.STALE_VERSION, HttpStatus.CONFLICT,
				"Someone else changed this first. Reload the page and try again.");
	}

	/** An unknown id. The argument names what was looked for, for the code that reads the log; the answer is the same for all. */
	public static ApiException notFound(String what) {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	/** One field is wrong: 400 with the field named, as the rest of the API answers. */
	public static ApiException validation(String field, String message) {
		return validation(List.of(new FieldErrorDetail(field, message)));
	}

	public static ApiException validation(List<FieldErrorDetail> problems) {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.", problems);
	}

}
