package com.coachingcenter.api.offline;

import java.io.IOException;
import java.util.List;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.coachingcenter.api.common.error.ApiError;
import com.coachingcenter.api.common.error.ApiErrorHttpWriter;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.web.RequestIds;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Answers 404 for every offline admission route while {@link OfflineAdmissionSwitch} is off. It runs before the security chain, so a
 * signed-out caller gets the same 404 as a signed-in one, and the routes do not reveal that they exist. Runs after the request id
 * filter, so the refusal carries the request id.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class OfflineAdmissionSwitchFilter extends OncePerRequestFilter {

	private static final List<String> ROUTES = List.of("/api/v1/offline-admissions", "/api/v1/offline-batches",
			"/api/v1/offline-fee-payments", "/api/v1/offline-instructors", "/api/v1/offline-routines", "/api/v1/offline-classes", "/api/v1/offline-class-payments", "/api/v1/offline-class-decisions", "/api/v1/offline-other-payments",
			"/api/v1/me/admission", "/api/v1/me/admissions", "/api/v1/public/offline-batches", "/api/v1/public/offline-applications");

	private final OfflineAdmissionSwitch admissionSwitch;

	private final ApiErrorHttpWriter errors;

	public OfflineAdmissionSwitchFilter(OfflineAdmissionSwitch admissionSwitch, ApiErrorHttpWriter errors) {
		this.admissionSwitch = admissionSwitch;
		this.errors = errors;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (admissionSwitch.isEnabled() || !isOfflineRoute(request)) {
			chain.doFilter(request, response);
			return;
		}
		errors.write(response, HttpServletResponse.SC_NOT_FOUND,
				ApiError.of(ErrorCode.RESOURCE_NOT_FOUND, "Resource not found.", RequestIds.current(request)));
	}

	/** The route itself or anything under it. Preflight requests pass, so CORS is answered as before. */
	private static boolean isOfflineRoute(HttpServletRequest request) {
		if (HttpMethod.OPTIONS.matches(request.getMethod())) {
			return false;
		}
		String path = request.getRequestURI();
		return ROUTES.stream().anyMatch(route -> path.equals(route) || path.startsWith(route + "/"));
	}

}
