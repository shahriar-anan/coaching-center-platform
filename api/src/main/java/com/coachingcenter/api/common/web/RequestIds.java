package com.coachingcenter.api.common.web;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestIds {

	public static final String HEADER = "X-Request-Id";

	public static final String ATTRIBUTE = "com.coachingcenter.api.requestId";

	private RequestIds() {
	}

	public static String current(HttpServletRequest request) {
		Object value = request.getAttribute(ATTRIBUTE);
		return value instanceof String id ? id : null;
	}

}
