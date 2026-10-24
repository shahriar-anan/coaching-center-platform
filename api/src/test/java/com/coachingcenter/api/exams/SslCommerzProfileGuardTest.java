package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class SslCommerzProfileGuardTest {

	@Test
	void stagingAllowsOnlyTheSandboxHostAndProdAllowsOnlyTheLiveHost() {
		assertThatCode(() -> SslCommerzProfileGuard.check(List.of("staging"), "https://sandbox.sslcommerz.com"))
			.doesNotThrowAnyException();
		assertThatCode(() -> SslCommerzProfileGuard.check(List.of("staging"), "https://SANDBOX.sslcommerz.com/"))
			.doesNotThrowAnyException();
		assertThatCode(() -> SslCommerzProfileGuard.check(List.of("prod"), "https://securepay.sslcommerz.com"))
			.doesNotThrowAnyException();
		assertThatThrownBy(() -> SslCommerzProfileGuard.check(List.of("staging"), "https://securepay.sslcommerz.com"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("staging")
			.hasMessageNotContaining("https://");
		assertThatThrownBy(() -> SslCommerzProfileGuard.check(List.of("staging"), "https://example.com"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("staging");
		assertThatThrownBy(() -> SslCommerzProfileGuard.check(List.of("prod"), "https://sandbox.sslcommerz.com"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("prod")
			.hasMessageNotContaining("https://");
		assertThatThrownBy(() -> SslCommerzProfileGuard.check(List.of("prod"), "https://example.com"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("prod");
		assertThatThrownBy(() -> SslCommerzProfileGuard.check(List.of("staging", "prod"), "https://sandbox.sslcommerz.com"))
			.isInstanceOf(IllegalStateException.class);
		assertThatCode(() -> SslCommerzProfileGuard.check(List.of("dev"), "https://example.com")).doesNotThrowAnyException();
		assertThatCode(() -> SslCommerzProfileGuard.check(List.of("staging"), " ")).doesNotThrowAnyException();
		assertThatCode(() -> SslCommerzProfileGuard.check(List.of("prod"), null)).doesNotThrowAnyException();
	}

}
