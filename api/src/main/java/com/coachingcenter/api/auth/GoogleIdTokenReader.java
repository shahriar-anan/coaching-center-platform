package com.coachingcenter.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import com.coachingcenter.api.common.error.ErrorCode;

@Component
public class GoogleIdTokenReader implements GoogleIdentityReader {

	private static final String HTTPS_ISSUER = "https://accounts.google.com";

	private static final String LEGACY_ISSUER = "accounts.google.com";

	private final GoogleOAuthClient google;

	private volatile NimbusJwtDecoder decoder;

	public GoogleIdTokenReader(GoogleOAuthClient google) {
		this.google = google;
	}

	@Override
	public GoogleIdentity read(String idToken, String expectedNonce) {
		try {
			Jwt jwt = decoder().decode(idToken);
			String nonce = jwt.getClaimAsString(IdTokenClaimNames.NONCE);
			if (!same(expectedNonce, nonce)) {
				throw new GoogleCallbackException(ErrorCode.GOOGLE_STATE_INVALID);
			}
			String subject = jwt.getSubject();
			if (subject == null || !subject.matches("^[\\x21-\\x7E]{1,255}$")) {
				throw new GoogleCallbackException(ErrorCode.GOOGLE_SIGN_IN_FAILED);
			}
			String email = jwt.getClaimAsString(StandardClaimNames.EMAIL);
			return new GoogleIdentity(subject, email, jwt.getClaimAsString(StandardClaimNames.NAME),
					emailVerified(jwt.getClaim(StandardClaimNames.EMAIL_VERIFIED)));
		}
		catch (GoogleCallbackException exception) {
			throw exception;
		}
		catch (JwtException exception) {
			throw new GoogleCallbackException(ErrorCode.GOOGLE_SIGN_IN_FAILED);
		}
	}

	private NimbusJwtDecoder decoder() {
		NimbusJwtDecoder existing = decoder;
		if (existing != null) {
			return existing;
		}
		synchronized (this) {
			if (decoder == null) {
				decoder = buildDecoder(google.registration().getProviderDetails().getJwkSetUri(),
						google.registration().getClientId());
			}
			return decoder;
		}
	}

	void useDecoder(NimbusJwtDecoder replacement) {
		this.decoder = replacement;
	}

	static NimbusJwtDecoder buildDecoder(String jwkSetUri, String clientId) {
		NimbusJwtDecoder created = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).jwsAlgorithm(SignatureAlgorithm.RS256).build();
		created.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
				new JwtTimestampValidator(), issuer(), audience(clientId), authorizedParty(clientId), subject()));
		return created;
	}

	static OAuth2TokenValidator<Jwt> issuer() {
		return jwt -> {
			String issuer = jwt.getClaimAsString(IdTokenClaimNames.ISS);
			if (HTTPS_ISSUER.equals(issuer) || LEGACY_ISSUER.equals(issuer)) {
				return OAuth2TokenValidatorResult.success();
			}
			return failure("invalid_issuer");
		};
	}

	static OAuth2TokenValidator<Jwt> audience(String clientId) {
		return jwt -> {
			List<String> audience = jwt.getAudience();
			if (audience != null && audience.contains(clientId)) {
				return OAuth2TokenValidatorResult.success();
			}
			return failure("invalid_audience");
		};
	}

	static OAuth2TokenValidator<Jwt> authorizedParty(String clientId) {
		return jwt -> {
			String party = jwt.getClaimAsString(IdTokenClaimNames.AZP);
			List<String> audience = jwt.getAudience();
			if (audience != null && audience.size() > 1) {
				if (clientId.equals(party)) {
					return OAuth2TokenValidatorResult.success();
				}
				return failure("invalid_authorized_party");
			}
			if (party == null || party.equals(clientId)) {
				return OAuth2TokenValidatorResult.success();
			}
			return failure("invalid_authorized_party");
		};
	}

	static OAuth2TokenValidator<Jwt> subject() {
		return jwt -> {
			String subject = jwt.getSubject();
			if (subject != null && !subject.isBlank()) {
				return OAuth2TokenValidatorResult.success();
			}
			return failure("invalid_subject");
		};
	}

	private static OAuth2TokenValidatorResult failure(String description) {
		return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", description, null));
	}

	static boolean emailVerified(Object claim) {
		return Boolean.TRUE.equals(claim) || "true".equals(claim);
	}

	private static boolean same(String left, String right) {
		if (left == null || right == null) {
			return false;
		}
		return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
	}

}
