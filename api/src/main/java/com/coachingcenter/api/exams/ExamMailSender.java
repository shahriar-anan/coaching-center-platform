package com.coachingcenter.api.exams;

public interface ExamMailSender {

	void send(String to, String subject, String text);

	/** A mail with HTML and its plain-text twin. A sender that cannot show HTML sends the text. */
	default void send(String to, String subject, String text, String html) {
		send(to, subject, text);
	}

}
