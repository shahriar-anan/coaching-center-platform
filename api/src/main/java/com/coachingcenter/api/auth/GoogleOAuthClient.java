package com.coachingcenter.api.auth;

import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.stereotype.Component;

import com.coachingcenter.api.common.config.AppProperties;

@Component
public class GoogleOAuthClient {

	private final AppProperties properties;

	private final ClientRegistration registration;

	public GoogleOAuthClient(AppProperties properties) {
		this.properties = properties;
		AppProperties.Google google = properties.google();
		if (google != null && google.enabled()) {
			this.registration = ClientRegistration.withRegistrationId("google")
				.clientId(google.clientId())
				.clientSecret(google.clientSecret())
				.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
				.authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
				.redirectUri(google.redirectUri())
				.scope(OidcScopes.OPENID, OidcScopes.EMAIL, OidcScopes.PROFILE)
				.authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
				.tokenUri("https://oauth2.googleapis.com/token")
				.jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
				.issuerUri("https://accounts.google.com")
				.userNameAttributeName("sub")
				.build();
		}
		else {
			this.registration = null;
		}
	}

	public boolean enabled() {
		return registration != null;
	}

	public ClientRegistration registration() {
		if (registration == null) {
			throw new GoogleCallbackException(com.coachingcenter.api.common.error.ErrorCode.GOOGLE_SIGN_IN_UNAVAILABLE);
		}
		return registration;
	}

	public String postLoginPath() {
		String path = properties.google() == null ? null : properties.google().postLoginPath();
		if (path == null || !path.startsWith("/") || path.startsWith("//") || path.contains("\\") || path.contains("..")) {
			return "/login/google";
		}
		return path;
	}

}
