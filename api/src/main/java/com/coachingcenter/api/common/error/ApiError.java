package com.coachingcenter.api.common.error;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

public record ApiError(
		String code,
		String message,
		List<FieldErrorDetail> fieldErrors,
		Instant timestamp,
		String requestId,
		UUID orderId,
		String tranId,
		/** A structured payload for the few conflicts that name another record. Absent from every other error. */
		@JsonInclude(JsonInclude.Include.NON_NULL) Object details) {

	public static ApiError of(ErrorCode code, String message, String requestId) {
		return new ApiError(code.name(), message, null, Instant.now(), requestId, null, null, null);
	}

	public static ApiError of(ErrorCode code, String message, List<FieldErrorDetail> fieldErrors, String requestId) {
		return new ApiError(code.name(), message, fieldErrors, Instant.now(), requestId, null, null, null);
	}

	public static ApiError of(ErrorCode code, String message, String requestId, UUID orderId, String tranId) {
		return new ApiError(code.name(), message, null, Instant.now(), requestId, orderId, tranId, null);
	}

}
