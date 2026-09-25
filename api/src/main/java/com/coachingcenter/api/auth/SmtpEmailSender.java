package com.coachingcenter.api.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import com.coachingcenter.api.common.config.AppProperties;

@Component
@Profile({ "staging", "prod" })
public class SmtpEmailSender implements EmailSender {

	private final JavaMailSenderImpl mail;

	private final String from;

	public SmtpEmailSender(AppProperties properties) {
		AppProperties.Email email = properties.email();
		if (email.host() == null || email.host().isBlank() || email.from() == null || email.from().isBlank()) {
			throw new IllegalStateException("EMAIL_HOST and EMAIL_FROM are required for staging and prod.");
		}
		JavaMailSenderImpl sender = new JavaMailSenderImpl();
		sender.setHost(email.host());
		sender.setPort(email.port() > 0 ? email.port() : 587);
		if (email.username() != null && !email.username().isBlank()) {
			sender.setUsername(email.username());
			sender.setPassword(email.password());
			sender.getJavaMailProperties().put("mail.smtp.auth", "true");
			sender.getJavaMailProperties().put("mail.smtp.starttls.enable", "true");
		}
		this.mail = sender;
		this.from = email.from();
	}

	@Override
	public void sendEmailVerification(String to, String code) {
		send(to, "Verify your email", "Your verification code is " + code);
	}

	@Override
	public void sendPasswordReset(String to, String code) {
		send(to, "Reset your password", "Your password reset code is " + code);
	}

	private void send(String to, String subject, String text) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(to);
		message.setSubject(subject);
		message.setText(text);
		mail.send(message);
	}

}
