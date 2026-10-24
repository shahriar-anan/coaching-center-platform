package com.coachingcenter.api.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.coachingcenter.api.PostgresIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class GoogleSignInDisabledIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void startIsUnavailableWhenGoogleIsNotConfigured() throws Exception {
		mockMvc.perform(get("/api/v1/auth/google"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("GOOGLE_SIGN_IN_UNAVAILABLE"));
	}

}
