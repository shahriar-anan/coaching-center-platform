package com.coachingcenter.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.common.mail.MailRecipient;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

@SpringBootTest(properties = {
		"spring.profiles.active=staging",
		"DEV_EXPOSE_EMAIL_CODES=true",
		"MAIL_HOST=smtp-relay.brevo.com",
		"MAIL_PORT=587",
		"MAIL_USERNAME=mail-test-user",
		"MAIL_PASSWORD=mail-test-not-a-real-key",
		"MAIL_FROM_ADDRESS=no-reply@symamlaw.com"
})
@AutoConfigureMockMvc
class DevExposeEmailCodesIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ApplicationContext context;

	@MockitoBean
	private SmtpEmailSender mail;

	private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

	private final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

	@BeforeEach
	void captureLogs() {
		logs.list.clear();
		logs.start();
		root.setLevel(Level.INFO);
		root.addAppender(logs);
	}

	@AfterEach
	void detachLogs() {
		root.detachAppender(logs);
	}

	@Test
	void theExposeFlagDoesNotPutACodeInAStagingResponseOrLog() throws Exception {
		assertThat(context.getBeansOfType(LoggingEmailSender.class)).isEmpty();
		String email = "expose-" + java.util.UUID.randomUUID() + "@example.com";
		int suffix = java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999);
		String body = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"Expose User","phone":"+8801%d","email":"%s","password":"password1"}
					""".formatted(suffix, email)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();

		ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
		verify(mail).sendEmailVerification(any(MailRecipient.class), code.capture());
		assertThat(code.getValue()).isNotBlank();
		assertThat(body).doesNotContain(code.getValue());
		String written = logs.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (left, right) -> left + "\n" + right);
		assertThat(written).doesNotContain(code.getValue());
	}

}
