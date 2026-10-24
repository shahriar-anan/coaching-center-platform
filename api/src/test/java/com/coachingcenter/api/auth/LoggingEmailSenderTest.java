package com.coachingcenter.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class LoggingEmailSenderTest {

	private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

	private final Logger logger = (Logger) LoggerFactory.getLogger(LoggingEmailSender.class);

	@AfterEach
	void detachLogs() {
		logger.detachAppender(logs);
	}

	@Test
	void queuedLinesDoNotContainTheCodeOrAddress() {
		logs.list.clear();
		logs.start();
		logger.setLevel(Level.INFO);
		logger.addAppender(logs);
		LoggingEmailSender sender = new LoggingEmailSender(false);

		sender.sendEmailVerification("student@example.com", "K7M2Q9RX");
		sender.sendPasswordReset("student@example.com", "K7M2Q9RX");
		sender.sendActivation("student@example.com", "K7M2Q9RX");
		sender.sendPasswordChanged("student@example.com");

		String written = logs.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (left, right) -> left + "\n" + right);
		assertThat(written).contains("Email verification message queued");
		assertThat(written).contains("Password reset message queued");
		assertThat(written).contains("Activation message queued");
		assertThat(written).contains("Password changed message queued");
		assertThat(written).doesNotContain("K7M2Q9RX");
		assertThat(written).doesNotContain("student@example.com");
	}

	@Test
	void exposeFlagLogsVerificationResetAndActivationCodes() {
		logs.list.clear();
		logs.start();
		logger.setLevel(Level.INFO);
		logger.addAppender(logs);
		LoggingEmailSender sender = new LoggingEmailSender(true);

		sender.sendEmailVerification("student@example.com", "K7M2Q9RX");
		sender.sendPasswordReset("student@example.com", "P4R8W2YN");
		sender.sendActivation("student@example.com", "A9C3E7HJ");
		sender.sendPasswordChanged("student@example.com");

		String written = logs.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (left, right) -> left + "\n" + right);
		assertThat(written).contains("Email verification code K7M2Q9RX");
		assertThat(written).contains("Password reset code P4R8W2YN");
		assertThat(written).contains("Activation code A9C3E7HJ");
		assertThat(written).contains("Password changed message queued");
		assertThat(written).doesNotContain("student@example.com");
	}

}
