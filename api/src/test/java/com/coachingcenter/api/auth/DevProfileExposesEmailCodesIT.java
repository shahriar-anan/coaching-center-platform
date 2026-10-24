package com.coachingcenter.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.coachingcenter.api.PostgresIntegrationTest;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

@SpringBootTest(properties = {
		"spring.profiles.active=dev",
		"DEV_EXPOSE_EMAIL_CODES=true",
		"app.auth.dev-expose-email-codes=true"
})
@AutoConfigureMockMvc
class DevProfileExposesEmailCodesIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

	private final Logger logger = (Logger) LoggerFactory.getLogger(LoggingEmailSender.class);

	@BeforeEach
	void captureLogs() {
		logs.list.clear();
		logs.start();
		logger.setLevel(Level.INFO);
		logger.addAppender(logs);
	}

	@AfterEach
	void detachLogs() {
		logger.detachAppender(logs);
	}

	@Test
	void devProfileLogsVerificationAndResetCodesAndKeepsThemOutOfTheJson() throws Exception {
		String email = "expose-" + java.util.UUID.randomUUID() + "@example.com";
		String body = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"Expose User","phone":"%s","email":"%s","password":"password1"}
					""".formatted(uniquePhone(), email)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String verification = codeAfter("Email verification code ");
		assertThat(verification).isNotBlank();
		assertThat(body).doesNotContain(verification);

		String forgot = mockMvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\"}"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String reset = codeAfter("Password reset code ");
		assertThat(reset).isNotBlank();
		assertThat(forgot).doesNotContain(reset);
		assertThat(forgot).doesNotContain(verification);
	}

	private String codeAfter(String prefix) {
		return logs.list.stream()
			.map(ILoggingEvent::getFormattedMessage)
			.filter(line -> line.startsWith(prefix))
			.map(line -> line.substring(prefix.length()).trim())
			.findFirst()
			.orElse("");
	}

	private static String uniquePhone() {
		int suffix = java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999);
		return "+8801" + suffix;
	}

}
