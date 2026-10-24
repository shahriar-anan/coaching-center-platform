package com.coachingcenter.api.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.coachingcenter.api.common.config.AppProperties;
import com.coachingcenter.api.common.error.ErrorCode;

class GoogleCodeExchangerTest {

	@Test
	void mismatchedRedirectUriIsRejectedBeforeGoogleIsCalled() {
		AppProperties properties = new AppProperties(null, null, null, null, null, new AppProperties.Google(
				"test-client-id", "test-client-secret", "https://api.symamlaw.com/api/v1/auth/google/callback",
				"/login/google"), null);
		GoogleCodeExchanger exchanger = new GoogleCodeExchanger(new GoogleOAuthClient(properties));

		assertThatThrownBy(() -> exchanger.exchangeForIdToken("auth-code", "verifier", "https://evil.example/callback"))
			.isInstanceOf(GoogleCallbackException.class)
			.extracting(exception -> ((GoogleCallbackException) exception).code())
			.isEqualTo(ErrorCode.GOOGLE_STATE_INVALID);
	}

}
