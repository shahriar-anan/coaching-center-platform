package com.coachingcenter.api.auth;

public interface EmailSender {

	void sendEmailVerification(String to, String code);

	void sendPasswordReset(String to, String code);

	void sendPasswordChanged(String to);

	void sendPhoneChanged(String to);

	void sendActivation(String to, String code);

}
