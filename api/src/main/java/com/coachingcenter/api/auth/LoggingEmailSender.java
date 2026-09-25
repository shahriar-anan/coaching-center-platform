package com.coachingcenter.api.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.coachingcenter.api.common.config.AppProperties;

@Component
@Profile("dev")
@ConditionalOnMissingBean(EmailSender.class)
public class LoggingEmailSender implements EmailSender {

	private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

	private final AppProperties properties;

	public LoggingEmailSender(AppProperties properties) {
		this.properties = properties;
	}

	@Override
	public void sendEmailVerification(String to, String code) {
		log.info("Email verification message queued");
		expose("email verification", code);
	}

	@Override
	public void sendPasswordReset(String to, String code) {
		log.info("Password reset message queued");
		expose("password reset", code);
	}

	private void expose(String purpose, String code) {
		if (properties.auth().devExposeEmailCodes()) {
			log.info("Dev-only {} code {}", purpose, code);
		}
	}

}
