package com.coachingcenter.api.exams;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.springframework.core.env.Environment;

final class SslCommerzProfileGuard {

	static final String SANDBOX_HOST = "sandbox.sslcommerz.com";

	static final String LIVE_HOST = "securepay.sslcommerz.com";

	private SslCommerzProfileGuard() {
	}

	static void check(Environment environment) {
		String baseUrl = environment.getProperty("SSLCZ_BASE_URL");
		if (baseUrl == null || baseUrl.isBlank()) {
			baseUrl = environment.getProperty("app.sslcommerz.base-url");
		}
		check(Arrays.asList(environment.getActiveProfiles()), baseUrl);
	}

	static void check(List<String> profiles, String baseUrl) {
		if (baseUrl == null || baseUrl.isBlank()) {
			return;
		}
		String host = hostOf(baseUrl);
		boolean prod = profiles.stream().anyMatch(profile -> "prod".equalsIgnoreCase(profile));
		boolean staging = profiles.stream().anyMatch(profile -> "staging".equalsIgnoreCase(profile));
		if (staging && !SANDBOX_HOST.equals(host)) {
			throw new IllegalStateException("SSLCZ_BASE_URL host is not allowed on the staging profile.");
		}
		if (prod && !LIVE_HOST.equals(host)) {
			throw new IllegalStateException("SSLCZ_BASE_URL host is not allowed on the prod profile.");
		}
	}

	static String hostOf(String baseUrl) {
		String trimmed = baseUrl.trim();
		if (!trimmed.contains("://")) {
			trimmed = "https://" + trimmed;
		}
		try {
			String host = URI.create(trimmed).getHost();
			return host == null ? "" : host.toLowerCase(Locale.ROOT);
		}
		catch (IllegalArgumentException exception) {
			return "";
		}
	}

}
