package com.coachingcenter.api.auth;

import org.springframework.http.ResponseCookie;

final class RefreshCookies {

	static final String NAME = "refresh_token";

	private static final String PATH = "/api/v1/auth";

	private RefreshCookies() {
	}

	static ResponseCookie issue(String rawToken, long maxAgeSeconds) {
		return ResponseCookie.from(NAME, rawToken)
			.httpOnly(true)
			.secure(true)
			.sameSite("Strict")
			.path(PATH)
			.maxAge(maxAgeSeconds)
			.build();
	}

	static ResponseCookie clear() {
		return ResponseCookie.from(NAME, "")
			.httpOnly(true)
			.secure(true)
			.sameSite("Strict")
			.path(PATH)
			.maxAge(0)
			.build();
	}

}
