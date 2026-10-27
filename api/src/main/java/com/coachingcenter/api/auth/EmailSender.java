package com.coachingcenter.api.auth;

import com.coachingcenter.api.common.mail.MailRecipient;

public interface EmailSender {

	void sendEmailVerification(String to, String code);

	void sendPasswordReset(String to, String code);

	void sendPasswordChanged(String to);

	void sendPhoneChanged(String to);

	void sendActivation(String to, String code);

	/*
	 * The same five mails with the person's name and role, so a mail can greet them and link to the right site. A sender that only
	 * knows the address (the dev logger, the test capture) keeps working through these defaults.
	 */

	default void sendEmailVerification(MailRecipient to, String code) {
		sendEmailVerification(to.email(), code);
	}

	default void sendPasswordReset(MailRecipient to, String code) {
		sendPasswordReset(to.email(), code);
	}

	default void sendPasswordChanged(MailRecipient to) {
		sendPasswordChanged(to.email());
	}

	default void sendPhoneChanged(MailRecipient to) {
		sendPhoneChanged(to.email());
	}

	default void sendActivation(MailRecipient to, String code) {
		sendActivation(to.email(), code);
	}

}
