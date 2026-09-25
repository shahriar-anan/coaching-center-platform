package com.coachingcenter.api.auth;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

import org.springframework.http.HttpStatus;

public final class PhoneNumbers {

	private PhoneNumbers() {
	}

	public static String normalize(String raw) {
		if (raw == null) {
			throw invalid();
		}
		String trimmed = raw.trim().replace(" ", "");
		String normalized = trimmed.startsWith("01") && trimmed.length() == 11 ? "+88" + trimmed : trimmed;
		if (!normalized.matches("\\+8801\\d{9}")) {
			throw invalid();
		}
		return normalized;
	}

	private static ApiException invalid() {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Phone number is invalid.");
	}

}
