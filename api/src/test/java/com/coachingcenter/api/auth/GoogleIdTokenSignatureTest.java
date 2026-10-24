package com.coachingcenter.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.coachingcenter.api.common.config.AppProperties;
import com.coachingcenter.api.common.error.ErrorCode;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

class GoogleIdTokenSignatureTest {

	private static final String CLIENT_ID = "test-client-id";

	private static HttpServer server;

	private static RSAKey signingKey;

	private static RSAKey otherKey;

	private static GoogleIdTokenReader reader;

	@BeforeAll
	static void keysAndServer() throws Exception {
		signingKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
		otherKey = new RSAKeyGenerator(2048).keyID("other-key").generate();
		byte[] jwks = new JWKSet(signingKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/jwks", exchange -> {
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, jwks.length);
			exchange.getResponseBody().write(jwks);
			exchange.close();
		});
		server.start();
		String uri = "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks";
		AppProperties properties = new AppProperties(null, null, null, null, null,
				new AppProperties.Google(CLIENT_ID, "test-client-secret", "https://api.example/callback", "/login/google"),
				null);
		reader = new GoogleIdTokenReader(new GoogleOAuthClient(properties));
		reader.useDecoder(GoogleIdTokenReader.buildDecoder(uri, CLIENT_ID));
	}

	@AfterAll
	static void stop() {
		if (server != null) {
			server.stop(0);
		}
	}

	@Test
	void signedTokenIsAccepted() throws Exception {
		GoogleIdentity identity = reader.read(token(signingKey, "https://accounts.google.com", CLIENT_ID, CLIENT_ID,
				"nonce-1", Instant.now().plusSeconds(300), true), "nonce-1");
		assertThat(identity.subject()).isEqualTo("subject-1");
		assertThat(identity.email()).isEqualTo("ada@example.com");
		assertThat(identity.emailVerified()).isTrue();
	}

	@Test
	void badSignatureWrongIssuerWrongAudienceAndExpiryAreRejected() throws Exception {
		assertFailed(token(otherKey, "https://accounts.google.com", CLIENT_ID, CLIENT_ID, "nonce-1",
				Instant.now().plusSeconds(300), true));
		assertFailed(token(signingKey, "https://evil.example", CLIENT_ID, CLIENT_ID, "nonce-1",
				Instant.now().plusSeconds(300), true));
		assertFailed(token(signingKey, "https://accounts.google.com", "other-client", "other-client", "nonce-1",
				Instant.now().plusSeconds(300), true));
		assertFailed(token(signingKey, "https://accounts.google.com", CLIENT_ID, CLIENT_ID, "nonce-1",
				Instant.now().minusSeconds(300), true));
	}

	@Test
	void nonceMismatchIsRejected() throws Exception {
		String token = token(signingKey, "https://accounts.google.com", CLIENT_ID, CLIENT_ID, "other-nonce",
				Instant.now().plusSeconds(300), true);
		assertThatThrownBy(() -> reader.read(token, "nonce-1")).isInstanceOf(GoogleCallbackException.class)
			.extracting(exception -> ((GoogleCallbackException) exception).code())
			.isEqualTo(ErrorCode.GOOGLE_STATE_INVALID);
	}

	private static void assertFailed(String token) {
		assertThatThrownBy(() -> reader.read(token, "nonce-1")).isInstanceOf(GoogleCallbackException.class)
			.extracting(exception -> ((GoogleCallbackException) exception).code())
			.isEqualTo(ErrorCode.GOOGLE_SIGN_IN_FAILED);
	}

	private static String token(RSAKey key, String issuer, String audience, String party, String nonce, Instant expiresAt,
			boolean emailVerified) throws Exception {
		JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(issuer)
			.subject("subject-1")
			.audience(audience)
			.claim("azp", party)
			.claim("email", "ada@example.com")
			.claim("email_verified", emailVerified)
			.claim("name", "Ada")
			.claim("nonce", nonce)
			.issueTime(Date.from(Instant.now().minusSeconds(30)))
			.expirationTime(Date.from(expiresAt))
			.build();
		SignedJWT jwt = new SignedJWT(
				new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).type(JOSEObjectType.JWT).build(), claims);
		jwt.sign(new RSASSASigner(key));
		return jwt.serialize();
	}

}
