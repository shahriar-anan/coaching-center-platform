package com.coachingcenter.api.auth;

import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.stereotype.Component;

import com.coachingcenter.api.common.error.ErrorCode;

@Component
public class GoogleCodeExchanger implements GoogleAuthorizationCodeClient {

	private final GoogleOAuthClient google;

	private final RestClientAuthorizationCodeTokenResponseClient tokens = new RestClientAuthorizationCodeTokenResponseClient();

	public GoogleCodeExchanger(GoogleOAuthClient google) {
		this.google = google;
	}

	@Override
	public String exchangeForIdToken(String code, String verifier, String redirectUri) {
		if (code == null || code.isBlank()) {
			throw new GoogleCallbackException(ErrorCode.GOOGLE_SIGN_IN_FAILED);
		}
		ClientRegistration registration = google.registration();
		if (!registration.getRedirectUri().equals(redirectUri)) {
			throw new GoogleCallbackException(ErrorCode.GOOGLE_STATE_INVALID);
		}
		OAuth2AuthorizationRequest authorization = OAuth2AuthorizationRequest.authorizationCode()
			.clientId(registration.getClientId())
			.authorizationUri(registration.getProviderDetails().getAuthorizationUri())
			.redirectUri(redirectUri)
			.scopes(registration.getScopes())
			.state("stored")
			.attributes(attributes -> attributes.put(PkceParameterNames.CODE_VERIFIER, verifier))
			.build();
		OAuth2AuthorizationResponse response = OAuth2AuthorizationResponse.success(code)
			.redirectUri(redirectUri)
			.state("stored")
			.build();
		try {
			OAuth2AccessTokenResponse token = tokens
				.getTokenResponse(new OAuth2AuthorizationCodeGrantRequest(registration,
						new OAuth2AuthorizationExchange(authorization, response)));
			Object idToken = token.getAdditionalParameters().get(OidcParameterNames.ID_TOKEN);
			if (!(idToken instanceof String value) || value.isBlank()) {
				throw new GoogleCallbackException(ErrorCode.GOOGLE_SIGN_IN_FAILED);
			}
			return value;
		}
		catch (OAuth2AuthorizationException exception) {
			throw new GoogleCallbackException(ErrorCode.GOOGLE_SIGN_IN_FAILED);
		}
	}

}
