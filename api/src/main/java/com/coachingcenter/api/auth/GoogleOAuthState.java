package com.coachingcenter.api.auth;

public record GoogleOAuthState(String state, String nonce, String verifier, String redirectUri,
		long issuedAtEpochSecond, String next) {
}
