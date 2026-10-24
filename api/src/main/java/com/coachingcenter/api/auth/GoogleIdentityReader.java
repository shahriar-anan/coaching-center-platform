package com.coachingcenter.api.auth;

public interface GoogleIdentityReader {

	GoogleIdentity read(String idToken, String expectedNonce);

}
