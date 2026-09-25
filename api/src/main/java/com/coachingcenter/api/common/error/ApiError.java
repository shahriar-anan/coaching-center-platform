package com.coachingcenter.api.common.error;

import java.time.Instant;
import java.util.List;

public record ApiError(
		String code,
		String message,
		List<FieldErrorDetail> fieldErrors,
		Instant timestamp,
		String requestId) {

	public static ApiError of(ErrorCode code, String message, String requestId) {
		return new ApiError(code.name(), message, null, Instant.now(), requestId);
	}

	public static ApiError of(ErrorCode code, String message, List<FieldErrorDetail> fieldErrors, String requestId) {
		return new ApiError(code.name(), message, fieldErrors, Instant.now(), requestId);
	}

}
