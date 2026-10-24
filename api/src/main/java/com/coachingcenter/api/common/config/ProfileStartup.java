package com.coachingcenter.api.common.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.core.env.Environment;

public final class ProfileStartup {

	private ProfileStartup() {
	}

	public static void check(Environment environment) {
		List<String> profiles = Arrays.asList(environment.getActiveProfiles());
		requireWebOrigin(profiles, origin(environment));
		refuseSeedOnProd(profiles, environment.getProperty("SEED_ENABLED"));
	}

	static void requireWebOrigin(List<String> profiles, String origin) {
		boolean restricted = profiles.stream()
			.anyMatch(profile -> "staging".equalsIgnoreCase(profile) || "prod".equalsIgnoreCase(profile));
		if (restricted && (origin == null || origin.isBlank())) {
			throw new IllegalStateException("WEB_ORIGIN is required on the staging and prod profiles.");
		}
	}

	static void refuseSeedOnProd(List<String> profiles, String seedEnabled) {
		boolean prod = profiles.stream().anyMatch(profile -> "prod".equalsIgnoreCase(profile));
		if (prod && seedEnabled != null && "true".equalsIgnoreCase(seedEnabled.trim())) {
			throw new IllegalStateException("SEED_ENABLED cannot be true on the prod profile.");
		}
	}

	private static String origin(Environment environment) {
		String origin = environment.getProperty("WEB_ORIGIN");
		if (origin == null || origin.isBlank()) {
			return environment.getProperty("app.cors.allowed-origins");
		}
		return origin;
	}

}
