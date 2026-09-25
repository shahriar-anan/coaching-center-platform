package com.coachingcenter.api.auth;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.common.config.AppProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

@Service
public class JwtService {

	private final byte[] secret;

	private final int accessTokenMinutes;

	public JwtService(AppProperties properties) {
		this.secret = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
		this.accessTokenMinutes = properties.jwt().accessTokenMinutes();
		if (this.secret.length < 32) {
			throw new IllegalStateException("JWT secret must be at least 32 bytes.");
		}
	}

	public String issue(UUID userId, Role role) {
		Instant expiresAt = Instant.now().plusSeconds(accessTokenMinutes * 60L);
		return issue(userId, role, expiresAt);
	}

	public String issue(UUID userId, Role role, Instant expiresAt) {
		try {
			JWTClaimsSet claims = new JWTClaimsSet.Builder().subject(userId.toString())
				.claim("role", role.name())
				.expirationTime(Date.from(expiresAt))
				.build();
			SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
			jwt.sign(new MACSigner(secret));
			return jwt.serialize();
		}
		catch (JOSEException exception) {
			throw new IllegalStateException("Could not sign access token.", exception);
		}
	}

	public int accessTokenSeconds() {
		return accessTokenMinutes * 60;
	}

	public CurrentActor parse(String token) {
		try {
			SignedJWT jwt = SignedJWT.parse(token);
			if (!jwt.verify(new MACVerifier(secret))) {
				return null;
			}
			Date expiration = jwt.getJWTClaimsSet().getExpirationTime();
			if (expiration == null || expiration.toInstant().isBefore(Instant.now())) {
				return null;
			}
			UUID userId = UUID.fromString(jwt.getJWTClaimsSet().getSubject());
			Role role = Role.valueOf(jwt.getJWTClaimsSet().getStringClaim("role"));
			return new CurrentActor(userId, role);
		}
		catch (ParseException | JOSEException | IllegalArgumentException exception) {
			return null;
		}
	}

}
