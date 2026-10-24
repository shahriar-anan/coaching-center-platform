package com.coachingcenter.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class GoogleIdTokenReaderTest {

	@Test
	void acceptsEitherGoogleIssuerAndTheConfiguredAudience() {
		Jwt jwt = jwt("https://accounts.google.com", List.of("test-client-id"), "test-client-id", "subject-1");
		assertThat(GoogleIdTokenReader.issuer().validate(jwt).hasErrors()).isFalse();
		assertThat(GoogleIdTokenReader.audience("test-client-id").validate(jwt).hasErrors()).isFalse();
		assertThat(GoogleIdTokenReader.authorizedParty("test-client-id").validate(jwt).hasErrors()).isFalse();
		assertThat(GoogleIdTokenReader.subject().validate(jwt).hasErrors()).isFalse();

		Jwt legacy = jwt("accounts.google.com", List.of("test-client-id"), null, "subject-1");
		assertThat(GoogleIdTokenReader.issuer().validate(legacy).hasErrors()).isFalse();
		assertThat(GoogleIdTokenReader.authorizedParty("test-client-id").validate(legacy).hasErrors()).isFalse();
	}

	@Test
	void rejectsAForeignIssuerAudienceOrParty() {
		Jwt jwt = jwt("https://example.invalid", List.of("other-client"), "other-client", "subject-1");
		assertThat(GoogleIdTokenReader.issuer().validate(jwt).hasErrors()).isTrue();
		assertThat(GoogleIdTokenReader.audience("test-client-id").validate(jwt).hasErrors()).isTrue();
		assertThat(GoogleIdTokenReader.authorizedParty("test-client-id").validate(jwt).hasErrors()).isTrue();
		assertThat(GoogleIdTokenReader.subject().validate(jwt("https://accounts.google.com", List.of("test-client-id"), null, " ")).hasErrors())
			.isTrue();
	}

	@Test
	void multipleAudiencesRequireTheAuthorizedParty() {
		Jwt missing = jwt("https://accounts.google.com", List.of("test-client-id", "other-client"), null, "subject-1");
		assertThat(GoogleIdTokenReader.authorizedParty("test-client-id").validate(missing).hasErrors()).isTrue();
		Jwt present = jwt("https://accounts.google.com", List.of("test-client-id", "other-client"), "test-client-id", "subject-1");
		assertThat(GoogleIdTokenReader.authorizedParty("test-client-id").validate(present).hasErrors()).isFalse();
	}

	@Test
	void emailVerifiedAcceptsBooleanTrueAndTheStringTrue() {
		assertThat(GoogleIdTokenReader.emailVerified(Boolean.TRUE)).isTrue();
		assertThat(GoogleIdTokenReader.emailVerified("true")).isTrue();
		assertThat(GoogleIdTokenReader.emailVerified(Boolean.FALSE)).isFalse();
		assertThat(GoogleIdTokenReader.emailVerified("false")).isFalse();
		assertThat(GoogleIdTokenReader.emailVerified(null)).isFalse();
	}

	private static Jwt jwt(String issuer, List<String> audience, String party, String subject) {
		Jwt.Builder builder = Jwt.withTokenValue("unsigned")
			.header("alg", "none")
			.claim("iss", issuer)
			.subject(subject)
			.audience(audience)
			.issuedAt(Instant.now())
			.expiresAt(Instant.now().plusSeconds(60));
		if (party != null) {
			builder.claim("azp", party);
		}
		return builder.build();
	}

}
