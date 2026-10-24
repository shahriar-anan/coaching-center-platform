package com.coachingcenter.api.auth;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.stereotype.Component;

@Component
public class GoogleAuthorizationRequests {

	private final GoogleOAuthClient google;

	private final SecureRandom random = new SecureRandom();

	public GoogleAuthorizationRequests(GoogleOAuthClient google) {
		this.google = google;
	}

	public Started start(String next) {
		ClientRegistration registration = google.registration();
		String state = randomKey();
		String nonce = randomKey();
		OAuth2AuthorizationRequest.Builder builder = OAuth2AuthorizationRequest.authorizationCode()
			.clientId(registration.getClientId())
			.authorizationUri(registration.getProviderDetails().getAuthorizationUri())
			.redirectUri(registration.getRedirectUri())
			.scopes(registration.getScopes())
			.state(state)
			.attributes(attributes -> attributes.put(OAuth2ParameterNames.REGISTRATION_ID, registration.getRegistrationId()));
		OAuth2AuthorizationRequestCustomizers.withPkce().accept(builder);
		builder.attributes(attributes -> attributes.put(OidcParameterNames.NONCE, nonce));
		builder.additionalParameters(parameters -> parameters.put(OidcParameterNames.NONCE, nonce));
		OAuth2AuthorizationRequest request = builder.build();
		String verifier = request.getAttribute(PkceParameterNames.CODE_VERIFIER);
		if (verifier == null || verifier.isBlank()) {
			throw new IllegalStateException("PKCE verifier was not created.");
		}
		GoogleOAuthState saved = new GoogleOAuthState(state, nonce, verifier, registration.getRedirectUri(),
				Instant.now().getEpochSecond(), next == null ? "" : next);
		return new Started(request.getAuthorizationRequestUri(), saved);
	}

	private String randomKey() {
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public record Started(String authorizationUri, GoogleOAuthState state) {
	}

}
