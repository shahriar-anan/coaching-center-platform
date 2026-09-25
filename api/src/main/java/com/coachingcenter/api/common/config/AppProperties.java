package com.coachingcenter.api.common.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Email email, Cors cors, Auth auth) {

	public record Jwt(String secret, int accessTokenMinutes) {
	}

	public record Auth(int refreshTokenDays, int resetCodeMinutes, int emailCodeMinutes, int activationCodeDays,
			int codeMaxAttempts, Lockout lockout, boolean devExposeEmailCodes) {
	}

	public record Lockout(int maxFailures, int windowMinutes) {
	}

	public record Email(String from, String host, int port, String username, String password) {
	}

	public record Cors(List<String> allowedOrigins) {
	}

}
