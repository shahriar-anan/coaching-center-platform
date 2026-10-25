package com.coachingcenter.api.auth;

import java.io.UnsupportedEncodingException;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.coachingcenter.api.common.config.AppProperties;

import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.Transport;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

@Component
@Profile({ "staging", "prod" })
public class SmtpEmailSender implements EmailSender, DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

	private static final String VERIFY_SUBJECT = "Verify your email";

	private static final String RESET_SUBJECT = "Reset your password";

	private static final String CHANGED_SUBJECT = "Your password was changed";

	private static final String ACTIVATION_SUBJECT = "Activate your account";

	private static final int TIMEOUT_MS = 10_000;

	private final MailSettings settings;

	private final OutboundMail mail;

	private final Executor executor;

	private final ExecutorService owned;

	@Autowired
	public SmtpEmailSender(AppProperties properties) {
		this.settings = normalize(require(properties.email()));
		this.mail = new JavaMailOutbound(javaMail(this.settings));
		this.owned = newPool();
		this.executor = this.owned;
	}

	SmtpEmailSender(AppProperties.Email email, OutboundMail mail, Executor executor) {
		this.settings = normalize(require(email));
		this.mail = mail;
		this.executor = executor;
		this.owned = null;
	}

	@Override
	public void sendEmailVerification(String to, String code) {
		dispatch(to, VERIFY_SUBJECT, "Your verification code is " + code, code);
	}

	@Override
	public void sendPasswordReset(String to, String code) {
		dispatch(to, RESET_SUBJECT, passwordResetBody(code), code);
	}

	@Override
	public void sendPasswordChanged(String to) {
		dispatch(to, CHANGED_SUBJECT, passwordChangedBody(), "");
	}

	@Override
	public void sendActivation(String to, String code) {
		dispatch(to, ACTIVATION_SUBJECT, activationBody(code), code);
	}

	public DeliveryResult sendPlainNow(String to, String subject, String text) {
		return deliverNow(to, subject, text, "");
	}

	@Override
	public void destroy() {
		if (owned != null) {
			owned.shutdown();
		}
	}

	private void dispatch(String to, String subject, String text, String secret) {
		Runnable job = () -> deliverNow(to, subject, text, secret);
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					submit(to, secret, job);
				}
			});
			return;
		}
		submit(to, secret, job);
	}

	private void submit(String to, String secret, Runnable job) {
		try {
			executor.execute(job);
		}
		catch (RejectedExecutionException exception) {
			logFailure(to, secret, exception);
		}
	}

	private DeliveryResult deliverNow(String to, String subject, String text, String secret) {
		try {
			MimeMessage message = compose(to, subject, text);
			String response = sanitize(mail.send(message), secret);
			return new DeliveryResult(true, response.isBlank() ? "accepted" : response);
		}
		catch (Exception exception) {
			logFailure(to, secret, exception);
			return new DeliveryResult(false, failureSummary(secret, exception));
		}
	}

	private MimeMessage compose(String to, String subject, String text) throws MessagingException {
		MimeMessage message = mail.createMimeMessage();
		MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
		try {
			helper.setFrom(settings.fromAddress(), settings.fromName());
		}
		catch (UnsupportedEncodingException exception) {
			throw new MessagingException("From address could not be encoded.", exception);
		}
		helper.setTo(to);
		helper.setSubject(subject);
		helper.setText(text, false);
		if (settings.replyTo() != null) {
			helper.setReplyTo(settings.replyTo());
		}
		return message;
	}

	private void logFailure(String to, String secret, Exception exception) {
		log.warn("Email send failed recipient={} error={} smtpCode={} providerResponse={}", maskAddress(to),
				exception.getClass().getSimpleName(), smtpCode(exception), failureSummary(secret, exception));
	}

	private String failureSummary(String secret, Exception exception) {
		String message = exception.getMessage();
		if ((message == null || message.isBlank()) && exception.getCause() != null) {
			message = exception.getCause().getMessage();
		}
		String summary = exception.getClass().getSimpleName() + " smtpCode=" + smtpCode(exception);
		String detail = sanitize(message, secret);
		if (!detail.isBlank()) {
			summary = summary + " " + detail;
		}
		return summary;
	}

	private String sanitize(String raw, String secret) {
		String value = raw == null ? "" : raw.replace("\r", " ").replace("\n", " ").trim();
		value = redact(value, settings.password());
		value = redact(value, settings.username());
		value = redact(value, secret);
		value = maskAddresses(value);
		if (value.length() > 300) {
			value = value.substring(0, 300);
		}
		return value;
	}

	private static String passwordResetBody(String code) {
		return "Your password reset code is " + code + ". Open /reset-password and enter this code.";
	}

	private static String activationBody(String code) {
		return "Your activation code is " + code + ". Open /activate and enter this code. Your phone is required there too.";
	}

	private static String passwordChangedBody() {
		return "Your password was changed. If you did not change it, contact the center.";
	}

	private static String redact(String value, String secret) {
		if (value.isEmpty() || secret == null || secret.isBlank()) {
			return value;
		}
		return value.replace(secret, "***");
	}

	static String maskAddress(String email) {
		if (email == null) {
			return "unknown";
		}
		int at = email.lastIndexOf('@');
		if (at <= 0 || at == email.length() - 1) {
			return "unknown";
		}
		return email.charAt(0) + "***" + email.substring(at);
	}

	private static String maskAddresses(String value) {
		return value.replaceAll("([A-Za-z0-9])[A-Za-z0-9._%+-]*@", "$1***@");
	}

	static String smtpCode(Throwable error) {
		for (Throwable current = error; current != null; current = current.getCause()) {
			try {
				Object value = current.getClass().getMethod("getReturnCode").invoke(current);
				if (value instanceof Integer code && code > 0) {
					return Integer.toString(code);
				}
			}
			catch (ReflectiveOperationException ignored) {
				// This exception type does not carry an SMTP status.
			}
		}
		return "none";
	}

	private static AppProperties.Email require(AppProperties.Email email) {
		if (email == null || blank(email.host()) || blank(email.username()) || blank(email.password())
				|| blank(email.fromAddress())) {
			throw new IllegalStateException(
					"MAIL_HOST, MAIL_USERNAME, MAIL_PASSWORD (or BREVO_API_KEY), and MAIL_FROM_ADDRESS are required for staging and prod.");
		}
		try {
			new InternetAddress(email.fromAddress().trim()).validate();
			if (!blank(email.replyTo())) {
				new InternetAddress(email.replyTo().trim()).validate();
			}
		}
		catch (AddressException exception) {
			throw new IllegalStateException("MAIL_FROM_ADDRESS or MAIL_REPLY_TO is not a valid email address.");
		}
		return email;
	}

	private static MailSettings normalize(AppProperties.Email email) {
		String replyTo = blank(email.replyTo()) ? null : email.replyTo().trim();
		String fromName = blank(email.fromName()) ? "SYMAM LAW" : email.fromName().trim();
		int port = email.port() > 0 ? email.port() : 587;
		return new MailSettings(email.fromAddress().trim(), fromName, replyTo, email.host().trim(), port,
				email.username().trim(), email.password());
	}

	static JavaMailSenderImpl configuredSender(AppProperties.Email email) {
		return javaMail(normalize(require(email)));
	}

	private static JavaMailSenderImpl javaMail(MailSettings settings) {
		JavaMailSenderImpl sender = new JavaMailSenderImpl();
		sender.setHost(settings.host());
		sender.setPort(settings.port());
		sender.setUsername(settings.username());
		sender.setPassword(settings.password());
		sender.setDefaultEncoding("UTF-8");
		Properties properties = sender.getJavaMailProperties();
		properties.put("mail.transport.protocol", "smtp");
		properties.put("mail.smtp.auth", "true");
		properties.put("mail.smtp.starttls.enable", "true");
		properties.put("mail.smtp.starttls.required", "true");
		properties.put("mail.smtp.ssl.checkserveridentity", "true");
		properties.put("mail.smtp.connectiontimeout", Integer.toString(TIMEOUT_MS));
		properties.put("mail.smtp.timeout", Integer.toString(TIMEOUT_MS));
		properties.put("mail.smtp.writetimeout", Integer.toString(TIMEOUT_MS));
		return sender;
	}

	private static ExecutorService newPool() {
		return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(50), runnable -> {
			Thread thread = new Thread(runnable, "mail-sender");
			thread.setDaemon(true);
			return thread;
		});
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}

	private record MailSettings(String fromAddress, String fromName, String replyTo, String host, int port,
			String username, String password) {
	}

	static final class JavaMailOutbound implements OutboundMail {

		private final JavaMailSenderImpl sender;

		private JavaMailOutbound(JavaMailSenderImpl sender) {
			this.sender = sender;
		}

		@Override
		public MimeMessage createMimeMessage() {
			return sender.createMimeMessage();
		}

		@Override
		public String send(MimeMessage message) throws MessagingException {
			Transport transport = sender.getSession().getTransport("smtp");
			try {
				connect(transport, sender);
				Address[] recipients = message.getAllRecipients();
				transport.sendMessage(message, recipients);
				return lastResponse(transport);
			}
			finally {
				close(transport);
			}
		}

		// The session does not hold the host or the credentials. JavaMailSenderImpl keeps them itself.
		static void connect(Transport transport, JavaMailSenderImpl sender) throws MessagingException {
			transport.connect(sender.getHost(), sender.getPort(), sender.getUsername(), sender.getPassword());
		}

		private static String lastResponse(Transport transport) {
			try {
				Object value = transport.getClass().getMethod("getLastServerResponse").invoke(transport);
				return value == null ? "accepted" : value.toString().trim();
			}
			catch (ReflectiveOperationException exception) {
				return "accepted";
			}
		}

		private static void close(Transport transport) {
			try {
				transport.close();
			}
			catch (MessagingException ignored) {
				// The send result is already decided.
			}
		}

	}

	public record DeliveryResult(boolean accepted, String providerResponse) {
	}

}
