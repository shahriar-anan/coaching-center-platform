package com.coachingcenter.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.coachingcenter.api.common.config.AppProperties;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

@Component
public class GoogleOAuthStateCookie {

	static final String NAME = "google_oauth";

	private static final String PATH = "/api/v1/auth/google";

	static final int MAX_AGE_SECONDS = 600;

	private static final int IV_BYTES = 12;

	private final byte[] key;

	private final SecureRandom random = new SecureRandom();

	public GoogleOAuthStateCookie(AppProperties properties) {
		try {
			this.key = MessageDigest.getInstance("SHA-256")
				.digest(properties.jwt().secret().getBytes(StandardCharsets.UTF_8));
		}
		catch (java.security.NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is required.", exception);
		}
	}

	public ResponseCookie write(GoogleOAuthState state) {
		String payload = String.join("\n", state.state(), state.nonce(), state.verifier(), state.redirectUri(),
				Long.toString(state.issuedAtEpochSecond()), state.next() == null ? "" : state.next());
		return cookie(NAME, encrypt(payload), MAX_AGE_SECONDS);
	}

	public GoogleOAuthState read(HttpServletRequest request) {
		String raw = value(request);
		if (raw == null) {
			return null;
		}
		String payload = decrypt(raw);
		if (payload == null) {
			return null;
		}
		String[] parts = payload.split("\n", -1);
		if (parts.length != 6) {
			return null;
		}
		long issuedAt;
		try {
			issuedAt = Long.parseLong(parts[4]);
		}
		catch (NumberFormatException exception) {
			return null;
		}
		long age = Instant.now().getEpochSecond() - issuedAt;
		if (age < -60 || age > MAX_AGE_SECONDS) {
			return null;
		}
		return new GoogleOAuthState(parts[0], parts[1], parts[2], parts[3], issuedAt, parts[5]);
	}

	public ResponseCookie clear() {
		return cookie(NAME, "", 0);
	}

	private static ResponseCookie cookie(String name, String value, int maxAge) {
		return ResponseCookie.from(name, value)
			.httpOnly(true)
			.secure(true)
			.sameSite("Lax")
			.path(PATH)
			.maxAge(maxAge)
			.build();
	}

	private String value(HttpServletRequest request) {
		if (request.getCookies() == null) {
			return null;
		}
		for (Cookie cookie : request.getCookies()) {
			if (NAME.equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}

	private String encrypt(String payload) {
		try {
			byte[] iv = new byte[IV_BYTES];
			random.nextBytes(iv);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
			byte[] encrypted = cipher.doFinal(payload.getBytes(StandardCharsets.UTF_8));
			byte[] packed = new byte[iv.length + encrypted.length];
			System.arraycopy(iv, 0, packed, 0, iv.length);
			System.arraycopy(encrypted, 0, packed, iv.length, encrypted.length);
			return Base64.getUrlEncoder().withoutPadding().encodeToString(packed);
		}
		catch (java.security.GeneralSecurityException exception) {
			throw new IllegalStateException("Could not seal the Google sign-in state.", exception);
		}
	}

	private String decrypt(String encoded) {
		try {
			byte[] packed = Base64.getUrlDecoder().decode(encoded);
			if (packed.length <= IV_BYTES) {
				return null;
			}
			byte[] iv = Arrays.copyOfRange(packed, 0, IV_BYTES);
			byte[] encrypted = Arrays.copyOfRange(packed, IV_BYTES, packed.length);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
			return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
		}
		catch (RuntimeException | java.security.GeneralSecurityException exception) {
			return null;
		}
	}

}
