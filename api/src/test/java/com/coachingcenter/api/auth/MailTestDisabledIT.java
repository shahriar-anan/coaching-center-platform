package com.coachingcenter.api.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.coachingcenter.api.PostgresIntegrationTest;

@SpringBootTest(properties = {
		"spring.profiles.active=staging",
		"MAIL_TEST_ENABLED=false",
		"MAIL_HOST=smtp-relay.brevo.com",
		"MAIL_PORT=587",
		"MAIL_USERNAME=mail-test-user",
		"MAIL_PASSWORD=mail-test-not-a-real-key",
		"MAIL_FROM_ADDRESS=no-reply@symamlaw.com"
})
@AutoConfigureMockMvc
class MailTestDisabledIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void mailTestIsNotFoundWhenTheFlagIsNotTrue() throws Exception {
		mockMvc.perform(post("/api/v1/admin/mail-test").contentType(MediaType.APPLICATION_JSON)
			.content("{\"to\":\"admin@example.com\"}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
	}

}
