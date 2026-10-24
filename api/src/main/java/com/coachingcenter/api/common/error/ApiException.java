package com.coachingcenter.api.common.error;

import java.util.UUID;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

	private final ErrorCode code;

	private final HttpStatus status;

	private final UUID orderId;

	private final String tranId;

	public ApiException(ErrorCode code, HttpStatus status, String message) {
		this(code, status, message, null, null);
	}

	public ApiException(ErrorCode code, HttpStatus status, String message, UUID orderId, String tranId) {
		super(message);
		this.code = code;
		this.status = status;
		this.orderId = orderId;
		this.tranId = tranId;
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
