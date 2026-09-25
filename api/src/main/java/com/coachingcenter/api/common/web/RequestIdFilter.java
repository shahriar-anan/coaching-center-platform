package com.coachingcenter.api.common.web;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

	private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9-]{1,64}$");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String requestId = resolve(request.getHeader(RequestIds.HEADER));
		request.setAttribute(RequestIds.ATTRIBUTE, requestId);
		response.setHeader(RequestIds.HEADER, requestId);
		MDC.put("requestId", requestId);
		try {
			filterChain.doFilter(request, response);
		}
		finally {
			log.info("request completed method={} path={} status={}", request.getMethod(), request.getRequestURI(),
					response.getStatus());
			MDC.remove("requestId");
		}
	}

	private static String resolve(String header) {
		if (header != null && SAFE_ID.matcher(header).matches()) {
			return header;
		}
		return UUID.randomUUID().toString();
	}

}
