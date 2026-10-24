package com.coachingcenter.api.exams;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.coachingcenter.api.auth.SmtpEmailSender;

@Component
@Profile({ "staging", "prod" })
public class SmtpExamMailSender implements ExamMailSender {

	private final SmtpEmailSender smtp;

	public SmtpExamMailSender(SmtpEmailSender smtp) {
		this.smtp = smtp;
	}

	@Override
	public void send(String to, String subject, String text) {
		SmtpEmailSender.DeliveryResult result = smtp.sendPlainNow(to, subject, text);
		if (!result.accepted()) {
			throw new IllegalStateException("Email was not accepted.");
		}
	}

}
