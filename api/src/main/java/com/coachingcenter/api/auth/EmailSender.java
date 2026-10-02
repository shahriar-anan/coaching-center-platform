package com.coachingcenter.api.auth;

public interface EmailSender {

	void sendEmailVerification(String to, String code);

	void sendPasswordReset(String to, String code);

}
