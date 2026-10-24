package com.coachingcenter.api.auth;

import com.coachingcenter.api.common.error.ErrorCode;

public class GoogleCallbackException extends RuntimeException {

	private final ErrorCode code;

	public GoogleCallbackException(ErrorCode code) {
		super(code.name());
		this.code = code;
	}

	public ErrorCode code() {
		return code;
	}

}
