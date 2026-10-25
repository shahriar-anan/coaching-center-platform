package com.coachingcenter.api.common.error;

import java.util.UUID;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

	private final ErrorCode code;

	private final HttpStatus status;

	private final UUID orderId;

	private final String tranId;

	private final Object details;

	public ApiException(ErrorCode code, HttpStatus status, String message) {
		this(code, status, message, null, null, null);
	}

	public ApiException(ErrorCode code, HttpStatus status, String message, UUID orderId, String tranId) {
		this(code, status, message, orderId, tranId, null);
	}

	public ApiException(ErrorCode code, HttpStatus status, String message, Object details) {
		this(code, status, message, null, null, details);
	}

	private ApiException(ErrorCode code, HttpStatus status, String message, UUID orderId, String tranId, Object details) {
		super(message);
		this.code = code;
		this.status = status;
		this.orderId = orderId;
		this.tranId = tranId;
		this.details = details;
	}

	/** The named record for a conflict, or null. Written to the error body as {@code details}. */
	public Object details() {
		return details;
	}

	public ErrorCode code() {
		return code;
	}

	public HttpStatus status() {
		return status;
	}

	public UUID orderId() {
		return orderId;
	}

	public String tranId() {
		return tranId;
	}

}
