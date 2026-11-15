package com.coachingcenter.api.offline;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;

import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.error.FieldErrorDetail;

import tools.jackson.databind.JsonNode;

/**
 * What the offline controllers and services share: the signed-in user, the refusal for a missing permission (403, as the other
 * routes), and the reading of request fields. A field the body leaves out is absent, not null: a partial update keeps it.
 */
final class OfflineSupport {

	private OfflineSupport() {
	}

	static UUID actorId() {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor) {
			return actor.userId();
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

	static void require(PermissionChecker permissions, String permission) {
		if (!permissions.has(permission)) {
			throw denied();
		}
	}

	static ApiException denied() {
		return new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
	}

	static ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	static ApiException validation(List<FieldErrorDetail> problems) {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.", problems);
	}

	/** A conflict with the admission's state: the change is not allowed now. */
	static ApiException state(String message) {
		return new ApiException(ErrorCode.ADMISSION_STATE, HttpStatus.CONFLICT, message);
	}

	static boolean has(JsonNode body, String key) {
		return body != null && body.has(key) && !body.get(key).isNull() && !body.get(key).asText().isBlank();
	}

	/** A date as year-month-day. Absent or blank is null; a value that is not a date is a problem for that field. */
	static LocalDate day(JsonNode body, String key, List<FieldErrorDetail> problems) {
		if (!has(body, key)) {
			return null;
		}
		try {
			return LocalDate.parse(body.get(key).asText().trim());
		}
		catch (DateTimeParseException exception) {
			problems.add(new FieldErrorDetail(key, "Enter the date as year-month-day."));
			return null;
		}
	}

	/** A money amount with two decimals. Absent is null; a negative or non-number is a problem for that field. */
	static BigDecimal money(JsonNode body, String key, List<FieldErrorDetail> problems) {
		if (!has(body, key)) {
			return null;
		}
		try {
			BigDecimal amount = new BigDecimal(body.get(key).asText().trim());
			if (amount.signum() < 0) {
				problems.add(new FieldErrorDetail(key, "The amount cannot be negative."));
				return null;
			}
			return amount.setScale(2, RoundingMode.HALF_UP);
		}
		catch (NumberFormatException exception) {
			problems.add(new FieldErrorDetail(key, "Enter the amount as a number."));
			return null;
		}
	}

	/**
	 * A number of monthly instalments, 1 to {@link OfflineFeeSchedule#MAX_INSTALLMENTS}, or a problem for that field. Null when the body
	 * does not name it.
	 */
	static Integer installments(JsonNode body, String key, List<FieldErrorDetail> problems) {
		if (!has(body, key)) {
			return null;
		}
		String text = body.get(key).asText().trim();
		int count;
		try {
			count = Integer.parseInt(text);
		}
		catch (NumberFormatException exception) {
			problems.add(new FieldErrorDetail(key, "Enter the number of instalments as a whole number."));
			return null;
		}
		if (count < 1 || count > OfflineFeeSchedule.MAX_INSTALLMENTS) {
			problems.add(new FieldErrorDetail(key, "Choose from 1 to " + OfflineFeeSchedule.MAX_INSTALLMENTS + " instalments."));
			return null;
		}
		return count;
	}

	/** An id, or a problem for that field when the text is not a UUID. */
	static UUID uuid(JsonNode body, String key, List<FieldErrorDetail> problems) {
		if (!has(body, key)) {
			return null;
		}
		try {
			return UUID.fromString(body.get(key).asText().trim());
		}
		catch (IllegalArgumentException exception) {
			problems.add(new FieldErrorDetail(key, "Choose one from the list."));
			return null;
		}
	}

	/** A short free-text reason: required, trimmed, at most 500 characters (the column's length). */
	static String reason(String raw, String field, List<FieldErrorDetail> problems) {
		String text = raw == null ? "" : raw.trim();
		if (text.isEmpty()) {
			problems.add(new FieldErrorDetail(field, "Enter a reason."));
			return null;
		}
		if (text.length() > 500) {
			problems.add(new FieldErrorDetail(field, "This is longer than 500 characters."));
			return null;
		}
		return text;
	}

}
