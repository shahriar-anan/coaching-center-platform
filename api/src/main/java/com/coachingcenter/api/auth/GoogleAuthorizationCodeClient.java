package com.coachingcenter.api.auth;

public interface GoogleAuthorizationCodeClient {

	String exchangeForIdToken(String code, String verifier, String redirectUri);

}
