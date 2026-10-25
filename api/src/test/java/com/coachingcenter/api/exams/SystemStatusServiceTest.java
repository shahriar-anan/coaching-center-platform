package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class SystemStatusServiceTest {

	private static final String SECRET = "STORE-SECRET-VALUE";

	@Test
	void reportsYesAndNoOnlyAndNeverAValue() {
		MockEnvironment environment = new MockEnvironment();
		environment.setProperty("app.sslcommerz.store-id", "store");
		environment.setProperty("app.sslcommerz.store-password", SECRET);
		environment.setProperty("app.sslcommerz.base-url", "https://sandbox.sslcommerz.com");
		environment.setProperty("app.sslcommerz.callback-base-url", "https://example.test");
		environment.setProperty("app.email.host", "smtp.example.test");

		var checks = new SystemStatusService(environment).checks();

		assertThat(find(checks, "checkout.configured").ok()).isTrue();
		assertThat(find(checks, "mail.configured").ok()).isTrue();
		assertThat(find(checks, "google.configured").ok()).isFalse();
		assertThat(find(checks, "mail.hour-before.off").ok()).isTrue();
		assertThat(find(checks, "seed.off").ok()).isTrue();
		assertThat(checks.toString()).doesNotContain(SECRET).doesNotContain("smtp.example.test");
	}

	@Test
	void theSeedAndDevCodesAreFlaggedWhenOn() {
		MockEnvironment environment = new MockEnvironment();
		environment.setProperty("app.seed.enabled", "true");
		environment.setProperty("app.auth.dev-expose-email-codes", "true");
		environment.setProperty("app.exam-mail.hour-before.enabled", "true");

		var checks = new SystemStatusService(environment).checks();

		assertThat(find(checks, "seed.off").ok()).isFalse();
		assertThat(find(checks, "auth.dev-codes.off").ok()).isFalse();
		assertThat(find(checks, "mail.hour-before.off").ok()).isFalse();
	}

	private static SystemStatusService.Check find(java.util.List<SystemStatusService.Check> checks, String name) {
		return checks.stream().filter(check -> check.name().equals(name)).findFirst().orElseThrow();
	}
}
