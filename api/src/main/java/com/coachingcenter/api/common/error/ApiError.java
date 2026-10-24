package com.coachingcenter.api.common.error;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ApiError(
		String code,
		String message,
		List<FieldErrorDetail> fieldErrors,
		Instant timestamp,
		String requestId,
		UUID orderId,
		String tranId) {

	public static ApiError of(ErrorCode code, String message, String requestId) {
		return new ApiError(code.name(), message, null, Instant.now(), requestId, null, null);
	}

	public static ApiError of(ErrorCode code, String message, List<FieldErrorDetail> fieldErrors, String requestId) {
		return new ApiError(code.name(), message, fieldErrors, Instant.now(), requestId, null, null);
	}

	public static ApiError of(ErrorCode code, String message, String requestId, UUID orderId, String tranId) {
		return new ApiError(code.name(), message, null, Instant.now(), requestId, orderId, tranId);
	}

}
