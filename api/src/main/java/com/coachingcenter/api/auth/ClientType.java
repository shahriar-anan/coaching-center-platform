package com.coachingcenter.api.auth;

import org.springframework.http.HttpStatus;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

public enum ClientType {

	WEB,
	MOBILE;

	public static ClientType fromHeader(String header) {
		if (header == null || header.isBlank()) {
			throw new ApiException(ErrorCode.CLIENT_TYPE_REQUIRED, HttpStatus.BAD_REQUEST, "X-Client-Type is required.");
		}
		return switch (header.trim().toLowerCase()) {
			case "web" -> WEB;
			case "mobile" -> MOBILE;
			default -> throw new ApiException(ErrorCode.CLIENT_TYPE_REQUIRED, HttpStatus.BAD_REQUEST,
					"X-Client-Type is required.");
		};
	}

}
