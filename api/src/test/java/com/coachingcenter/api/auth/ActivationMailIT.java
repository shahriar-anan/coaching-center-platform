package com.coachingcenter.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.coachingcenter.api.PostgresIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class ActivationMailIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CapturingEmailSender emails;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder passwords;

	@Test
	void createAndReissueSendTheCodeAndReissueRequiresAPendingAccount() throws Exception {
		String access = masterAccess();
		String studentEmail = uniqueEmail();
		String studentPhone = uniquePhone();
		MvcResult created = mockMvc.perform(post("/api/v1/students").header("Authorization", "Bearer " + access)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"Pending Student","phone":"%s","email":"%s"}
					""".formatted(studentPhone, studentEmail)))
			.andExpect(status().isCreated())
			.andReturn();
		String firstCode = json(created, "activationCode");
		String studentId = json(created, "id");
		assertThat(emails.latestActivation(studentEmail)).isEqualTo(firstCode);

		MvcResult reissued = mockMvc.perform(post("/api/v1/students/" + studentId + "/activation-code")
			.header("Authorization", "Bearer " + access))
			.andExpect(status().isOk())
			.andReturn();
		String secondCode = json(reissued, "activationCode");
		assertThat(secondCode).isNotEqualTo(firstCode);
		assertThat(emails.latestActivation(studentEmail)).isEqualTo(secondCode);
		activate(studentPhone, firstCode).andExpect(status().isBadRequest());
		activate(studentPhone, secondCode).andExpect(status().isNoContent());

		mockMvc.perform(post("/api/v1/students/" + studentId + "/activation-code").header("Authorization", "Bearer " + access))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ACTIVATION_NOT_PENDING"));

		String inactiveEmail = uniqueEmail();
		String inactiveId = json(mockMvc.perform(post("/api/v1/students").header("Authorization", "Bearer " + access)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"Inactive Student","phone":"%s","email":"%s"}
					""".formatted(uniquePhone(), inactiveEmail)))
			.andExpect(status().isCreated())
			.andReturn(), "id");
		jdbc.update("update users set status = 'INACTIVE' where id = ?", UUID.fromString(inactiveId));
		mockMvc.perform(post("/api/v1/students/" + inactiveId + "/activation-code").header("Authorization", "Bearer " + access))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ACTIVATION_NOT_PENDING"));
	}

	@Test
	void instructorReissueIsMasterAdminOnlyAndOnlyForAPendingInstructor() throws Exception {
		String access = masterAccess();
		String email = uniqueEmail();
		String phone = uniquePhone();
		MvcResult created = mockMvc.perform(post("/api/v1/instructors").header("Authorization", "Bearer " + access)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"Pending Instructor","phone":"%s","email":"%s"}
					""".formatted(phone, email)))
			.andExpect(status().isCreated())
			.andReturn();
		String instructorId = json(created, "id");
		assertThat(emails.latestActivation(email)).isEqualTo(json(created, "activationCode"));

		MvcResult reissued = mockMvc.perform(post("/api/v1/instructors/" + instructorId + "/activation-code")
			.header("Authorization", "Bearer " + access))
			.andExpect(status().isOk())
			.andReturn();
		assertThat(emails.latestActivation(email)).isEqualTo(json(reissued, "activationCode"));

		String systemAccess = systemAdminAccess();
		mockMvc.perform(post("/api/v1/instructors/" + instructorId + "/activation-code")
			.header("Authorization", "Bearer " + systemAccess))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

		mockMvc.perform(post("/api/v1/instructors/" + UUID.randomUUID() + "/activation-code")
			.header("Authorization", "Bearer " + access))
			.andExpect(status().isNotFound());

		String studentId = json(mockMvc.perform(post("/api/v1/students").header("Authorization", "Bearer " + access)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"Not Instructor","phone":"%s","email":"%s"}
					""".formatted(uniquePhone(), uniqueEmail())))
			.andExpect(status().isCreated())
			.andReturn(), "id");
		mockMvc.perform(post("/api/v1/instructors/" + studentId + "/activation-code").header("Authorization", "Bearer " + access))
			.andExpect(status().isNotFound());

		jdbc.update("update users set deleted_at = now() where id = ?", UUID.fromString(instructorId));
		mockMvc.perform(post("/api/v1/instructors/" + instructorId + "/activation-code")
			.header("Authorization", "Bearer " + access))
			.andExpect(status().isNotFound());
	}

	private org.springframework.test.web.servlet.ResultActions activate(String phone, String code) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/activate").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"phone":"%s","code":"%s","password":"password1"}
					""".formatted(phone, code)));
	}

	private String systemAdminAccess() throws Exception {
		String phone = uniquePhone();
		jdbc.update("""
				insert into users (id, phone, email, password_hash, role, status, created_at, updated_at)
				values (?, ?, ?, ?, 'SYSTEM_ADMIN', 'ACTIVE', now(), now())
				""", UUID.randomUUID(), phone, uniqueEmail(), passwords.encode("password1"));
		return json(login(phone, "password1"), "accessToken");
	}

	private String masterAccess() throws Exception {
		return json(login("+8801700000001", "bootstrap-password-1"), "accessToken");
	}

	private MvcResult login(String phone, String password) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"identifier":"%s","password":"%s"}
					""".formatted(phone, password)))
			.andExpect(status().isOk())
			.andReturn();
	}

	private static String json(MvcResult result, String field) throws Exception {
		String body = result.getResponse().getContentAsString();
		String marker = "\"" + field + "\":\"";
		int start = body.indexOf(marker) + marker.length();
		return body.substring(start, body.indexOf('"', start));
	}

	private static String uniquePhone() {
		int suffix = java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999);
		return "+8801" + suffix;
	}

	private static String uniqueEmail() {
		return "activation-" + UUID.randomUUID() + "@example.com";
	}

}
