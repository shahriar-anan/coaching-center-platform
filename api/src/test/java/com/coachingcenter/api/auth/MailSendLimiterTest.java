package com.coachingcenter.api.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import com.coachingcenter.api.common.error.ApiException;

class MailSendLimiterTest {

	@Test
	void resendCooldownRejectsASecondSendInsideSixtySeconds() {
		MutableClock clock = new MutableClock(Instant.parse("2026-10-24T12:00:00Z"));
		MailSendLimiter limiter = new MailSendLimiter(new MailSendLimiter.Limits(3, 10, 60), clock);

		limiter.acquire("student@example.com", "203.0.113.8", true);
		assertThatThrownBy(() -> limiter.acquire("student@example.com", "203.0.113.9", true))
			.isInstanceOf(ApiException.class)
			.extracting("status")
			.isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

		clock.now = clock.now.plusSeconds(60);
		assertThatCode(() -> limiter.acquire("student@example.com", "203.0.113.9", true)).doesNotThrowAnyException();
	}

	@Test
	void onlyALoopbackPeerMaySupplyTheProxyRealIp() {
		MockHttpServletRequest throughProxy = new MockHttpServletRequest();
		throughProxy.setRemoteAddr("127.0.0.1");
		throughProxy.addHeader("X-Forwarded-For", "198.51.100.4, 203.0.113.9");
		throughProxy.addHeader("X-Real-IP", "203.0.113.8");
		org.assertj.core.api.Assertions.assertThat(MailSendLimiter.clientAddress(throughProxy)).isEqualTo("203.0.113.8");

		MockHttpServletRequest forwardedOnly = new MockHttpServletRequest();
		forwardedOnly.setRemoteAddr("127.0.0.1");
		forwardedOnly.addHeader("X-Forwarded-For", "198.51.100.4");
		org.assertj.core.api.Assertions.assertThat(MailSendLimiter.clientAddress(forwardedOnly)).isEqualTo("127.0.0.1");

		MockHttpServletRequest direct = new MockHttpServletRequest();
		direct.setRemoteAddr("203.0.113.50");
		direct.addHeader("X-Real-IP", "198.51.100.7");
		direct.addHeader("X-Forwarded-For", "198.51.100.7");
		org.assertj.core.api.Assertions.assertThat(MailSendLimiter.clientAddress(direct)).isEqualTo("203.0.113.50");
	}

	private static final class MutableClock extends Clock {

		private Instant now;

		private MutableClock(Instant now) {
			this.now = now;
		}

		@Override
		public java.time.ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}

	}

}
