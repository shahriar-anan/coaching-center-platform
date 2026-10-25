package com.coachingcenter.api.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.coachingcenter.api.common.config.AppProperties;

@Component
@Profile("dev")
public class LoggingEmailSender implements EmailSender {

	private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

	private final boolean exposeCodes;

	@Autowired
	public LoggingEmailSender(AppProperties properties) {
		this(properties.auth().devExposeEmailCodes());
	}

	LoggingEmailSender(boolean exposeCodes) {
		this.exposeCodes = exposeCodes;
	}

	@Override
	public void sendEmailVerification(String to, String code) {
		log.info("Email verification message queued");
		if (exposeCodes) {
			log.info("Email verification code {}", code);
		}
	}

	@Override
	public void sendPasswordReset(String to, String code) {
		log.info("Password reset message queued");
		if (exposeCodes) {
			log.info("Password reset code {}", code);
		}
	}

	@Override
	public void sendPasswordChanged(String to) {
		log.info("Password changed message queued");
	}

	@Override
	public void sendPhoneChanged(String to) {
		log.info("Phone changed message queued");
	}

	@Override
	public void sendActivation(String to, String code) {
		log.info("Activation message queued");
		if (exposeCodes) {
			log.info("Activation code {}", code);
		}
	}

}
