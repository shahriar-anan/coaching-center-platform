package com.coachingcenter.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.coachingcenter.api.common.config.AppProperties;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

class SmtpEmailSenderTest {

	private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

	private final Logger logger = (Logger) LoggerFactory.getLogger(SmtpEmailSender.class);

	@AfterEach
	void detachLogs() {
		logger.detachAppender(logs);
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Test
	void fromAddressAndNameComeFromConfig() throws Exception {
		FakeOutbound mail = new FakeOutbound();
		SmtpEmailSender sender = sender(mail, "desk@symamlaw.com", "SYMAM Desk", "admissions@symamlaw.com");

		sender.sendPasswordReset("student@example.com", "K7M2Q9RX");
		MimeMessage message = mail.last;

		InternetAddress from = (InternetAddress) message.getFrom()[0];
		assertThat(from.getAddress()).isEqualTo("desk@symamlaw.com");
		assertThat(from.getPersonal()).isEqualTo("SYMAM Desk");
		assertThat(((InternetAddress) message.getReplyTo()[0]).getAddress()).isEqualTo("admissions@symamlaw.com");
		assertThat(message.getSubject()).isEqualTo("Reset your password");
		assertThat(message.getContent().toString())
			.isEqualTo("Your password reset code is K7M2Q9RX. Open /reset-password and enter this code.");
		assertThat(message.getContent().toString()).doesNotContain("/reset-password?");
	}

	@Test
	void replyToIsOmittedWhenUnsetOrBlank() throws Exception {
		FakeOutbound unset = new FakeOutbound();
		sender(unset, "no-reply@symamlaw.com", "SYMAM LAW", null).sendEmailVerification("student@example.com", "K7M2Q9RX");
		assertThat(unset.last.getHeader("Reply-To")).isNull();

		FakeOutbound blank = new FakeOutbound();
		sender(blank, "no-reply@symamlaw.com", "SYMAM LAW", "  ").sendEmailVerification("student@example.com", "K7M2Q9RX");
		assertThat(blank.last.getHeader("Reply-To")).isNull();
	}

	@Test
	void blankFromNameUsesTheDefaultDisplayName() throws Exception {
		FakeOutbound mail = new FakeOutbound();
		sender(mail, "no-reply@symamlaw.com", " ", null).sendEmailVerification("student@example.com", "K7M2Q9RX");

		InternetAddress from = (InternetAddress) mail.last.getFrom()[0];
		assertThat(from.getAddress()).isEqualTo("no-reply@symamlaw.com");
		assertThat(from.getPersonal()).isEqualTo("SYMAM LAW");
	}

	@Test
	void smtpRequiresStartTlsAndTimesOut() {
		var sender = SmtpEmailSender.configuredSender(new AppProperties.Email("no-reply@symamlaw.com", "SYMAM LAW", null,
				"smtp-relay.brevo.com", 587, "smtp-login@brevo.example", "brevo-api-key-should-not-leak"));
		assertThat(sender.getHost()).isEqualTo("smtp-relay.brevo.com");
		assertThat(sender.getPort()).isEqualTo(587);
		assertThat(sender.getJavaMailProperties())
			.containsEntry("mail.smtp.auth", "true")
			.containsEntry("mail.smtp.starttls.enable", "true")
			.containsEntry("mail.smtp.starttls.required", "true")
			.containsEntry("mail.smtp.connectiontimeout", "10000")
			.containsEntry("mail.smtp.timeout", "10000")
			.containsEntry("mail.smtp.writetimeout", "10000")
			.doesNotContainKey("mail.smtp.ssl.enable");
	}

	@Test
	void transportConnectsWithTheConfiguredHostAndCredentials() throws Exception {
		var sender = SmtpEmailSender.configuredSender(new AppProperties.Email("no-reply@symamlaw.com", "SYMAM LAW", null,
				"smtp-relay.brevo.com", 587, "smtp-login@brevo.example", "brevo-api-key-should-not-leak"));
		Transport transport = mock(Transport.class);

		SmtpEmailSender.JavaMailOutbound.connect(transport, sender);

		verify(transport).connect("smtp-relay.brevo.com", 587, "smtp-login@brevo.example",
				"brevo-api-key-should-not-leak");
	}

	@Test
	void sendFailureIsHandledAndLoggedWithoutTheCodeOrCredential() {
		attachLogs();
		FakeOutbound mail = new FakeOutbound();
		mail.failure = new CodedFailure(
				"535 5.7.8 rejected 654321 student@example.com brevo-api-key-should-not-leak");
		SmtpEmailSender sender = sender(mail, "no-reply@symamlaw.com", "SYMAM LAW", null);

		assertThatCode(() -> sender.sendPasswordReset("student@example.com", "654321")).doesNotThrowAnyException();

		mail.failure = new CodedFailure("535 5.7.8 rejected student@example.com brevo-api-key-should-not-leak");
		SmtpEmailSender.DeliveryResult result = sender.sendPlainNow("student@example.com", "Exam starts soon",
				"Your exam starts at 10:00.");
		assertThat(result.accepted()).isFalse();
		String written = logs.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (left, right) -> left + "\n" + right);
		assertThat(written).contains("recipient=s***@example.com");
		assertThat(written).contains("s***@example.com");
		assertThat(written).contains("error=CodedFailure");
		assertThat(written).contains("smtpCode=535");
		assertThat(written).doesNotContain("654321");
		assertThat(written).doesNotContain("brevo-api-key-should-not-leak");
		assertThat(written).doesNotContain("student@");
		assertThat(written).doesNotContain("smtp-login@brevo.example");
		assertThat(result.providerResponse()).doesNotContain("brevo-api-key-should-not-leak");
		assertThat(result.providerResponse()).doesNotContain("student@");
		assertThat(logs.list).allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
	}

	@Test
	void activationAndPasswordChangedNotesStayPlainText() throws Exception {
		FakeOutbound mail = new FakeOutbound();
		SmtpEmailSender sender = sender(mail, "no-reply@symamlaw.com", "SYMAM LAW", null);

		sender.sendActivation("student@example.com", "K7M2Q9RX");
		assertThat(mail.last.getSubject()).isEqualTo("Activate your account");
		assertThat(mail.last.getContent().toString()).isEqualTo(
				"Your activation code is K7M2Q9RX. Open /activate and enter this code. Your phone is required there too.");

		sender.sendPasswordChanged("student@example.com");
		assertThat(mail.last.getSubject()).isEqualTo("Your password was changed");
		String changed = mail.last.getContent().toString();
		assertThat(changed).isEqualTo("Your password was changed. If you did not change it, contact the center.");
		assertThat(changed).doesNotContain("K7M2Q9RX");
	}

	@Test
	void passwordResetWaitsUntilTheTransactionCommits() throws Exception {
		attachLogs();
		FakeOutbound mail = new FakeOutbound();
		SmtpEmailSender sender = sender(mail, "no-reply@symamlaw.com", "SYMAM LAW", null);
		TransactionSynchronizationManager.initSynchronization();

		sender.sendPasswordReset("student@example.com", "K7M2Q9RX");
		assertThat(mail.sent).isZero();

		List<TransactionSynchronization> synchronizations = new ArrayList<>(
				TransactionSynchronizationManager.getSynchronizations());
		for (TransactionSynchronization synchronization : synchronizations) {
			synchronization.afterCommit();
		}

		assertThat(mail.sent).isEqualTo(1);
		assertThat(mail.last.getContent().toString())
			.isEqualTo("Your password reset code is K7M2Q9RX. Open /reset-password and enter this code.");
		String written = logs.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (left, right) -> left + "\n" + right);
		assertThat(written).doesNotContain("K7M2Q9RX");
	}

	private void attachLogs() {
		logs.list.clear();
		logs.start();
		logger.setLevel(Level.WARN);
		logger.addAppender(logs);
	}

	private static SmtpEmailSender sender(FakeOutbound mail, String from, String fromName, String replyTo) {
		return new SmtpEmailSender(new AppProperties.Email(from, fromName, replyTo, "smtp-relay.brevo.com", 587,
				"smtp-login@brevo.example", "brevo-api-key-should-not-leak"), mail, Runnable::run);
	}

	private static final class FakeOutbound implements OutboundMail {

		private MimeMessage last;

		private Exception failure;

		private int sent;

		@Override
		public MimeMessage createMimeMessage() {
			return new MimeMessage(Session.getInstance(new Properties()));
		}

		@Override
		public String send(MimeMessage message) throws MessagingException {
			if (failure instanceof MessagingException messaging) {
				throw messaging;
			}
			if (failure instanceof RuntimeException runtime) {
				throw runtime;
			}
			this.last = message;
			sent++;
			return "250 2.0.0 OK";
		}

	}

	private static final class CodedFailure extends MessagingException {

		private CodedFailure(String message) {
			super(message);
		}

		@SuppressWarnings("unused")
		public int getReturnCode() {
			return 535;
		}

	}

}
