package com.coachingcenter.api.common.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Email email, Cors cors, Auth auth, Bootstrap bootstrap, Google google, Mail mail) {

	public record Jwt(String secret, int accessTokenMinutes) {
	}

	public record Auth(int refreshTokenDays, int resetCodeMinutes, int emailCodeMinutes, int activationCodeDays,
			int codeMaxAttempts, Lockout lockout, boolean devExposeEmailCodes, int resetResponseFloorMillis) {
	}

	public record Lockout(int maxFailures, int windowMinutes) {
	}

	public record Email(String fromAddress, String fromName, String replyTo, String host, int port, String username,
			String password) {
	}

	public record Cors(List<String> allowedOrigins) {
	}

	public record Bootstrap(String phone, String email, String name, String password) {
	}

	public record Google(String clientId, String clientSecret, String redirectUri, String postLoginPath) {

		public boolean enabled() {
			return present(clientId) && present(clientSecret) && present(redirectUri);
		}

		private static boolean present(String value) {
			return value != null && !value.isBlank();
		}

	}

	public record Mail(int sendsPerEmailPerHour, int sendsPerIpPerHour, int resendCooldownSeconds) {
	}

}
