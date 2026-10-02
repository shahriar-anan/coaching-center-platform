package com.coachingcenter.api.users;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import javax.sql.DataSource;

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
import com.coachingcenter.api.auth.CapturingEmailConfig;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class AuthorizationIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Test
	void systemAdminCannotDeleteManageAdminsOrReadAudit() throws Exception {
		String access = token(seed("SYSTEM_ADMIN"));
		UUID student = seed("STUDENT");
		UUID admin = seed("SYSTEM_ADMIN");
		mockMvc.perform(delete("/api/v1/students/" + student).header("Authorization", "Bearer " + access))
			.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/v1/admins/" + admin).header("Authorization", "Bearer " + access))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/admins").header("Authorization", "Bearer " + access)
			.contentType(MediaType.APPLICATION_JSON)
			.content(accountJson()))
			.andExpect(status().isForbidden());
		mockMvc.perform(patch("/api/v1/admins/" + admin + "/status").header("Authorization", "Bearer " + access)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"INACTIVE\"}"))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/audit-log").header("Authorization", "Bearer " + access))
			.andExpect(status().isForbidden());
	}

	@Test
	void masterAdminCanSoftDeleteAStudentAndASystemAdmin() throws Exception {
		String access = token(seed("MASTER_ADMIN"));
		UUID student = seed("STUDENT");
		UUID admin = seed("SYSTEM_ADMIN");
		mockMvc.perform(delete("/api/v1/students/" + student).header("Authorization", "Bearer " + access))
			.andExpect(status().isNoContent());
		mockMvc.perform(delete("/api/v1/admins/" + admin).header("Authorization", "Bearer " + access))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/students/" + student).header("Authorization", "Bearer " + access))
			.andExpect(status().isNotFound());
	}

	@Test
	void studentCannotReadAnotherStudentOrCallAdminEndpoints() throws Exception {
		String phone = uniquePhone();
		register(phone);
		String access = tokenFromLogin(phone);
		UUID other = seed("STUDENT");
		mockMvc.perform(get("/api/v1/students/" + other).header("Authorization", "Bearer " + access))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/admins").header("Authorization", "Bearer " + access)).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/students").header("Authorization", "Bearer " + access)).andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/students").header("Authorization", "Bearer " + access)
			.contentType(MediaType.APPLICATION_JSON)
			.content(accountJson()))
			.andExpect(status().isForbidden());
	}

	@Test
	void noEndpointCanMutateAMasterAdmin() throws Exception {
		String access = token(seed("MASTER_ADMIN"));
		UUID master = seed("MASTER_ADMIN");
		mockMvc.perform(delete("/api/v1/admins/" + master).header("Authorization", "Bearer " + access))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("MASTER_ADMIN_PROTECTED"));
		mockMvc.perform(patch("/api/v1/admins/" + master + "/status").header("Authorization", "Bearer " + access)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"INACTIVE\"}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("MASTER_ADMIN_PROTECTED"));
		mockMvc.perform(post("/api/v1/admins/" + master + "/activation-code").header("Authorization", "Bearer " + access))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("MASTER_ADMIN_PROTECTED"));
		mockMvc.perform(post("/api/v1/admins").header("Authorization", "Bearer " + access)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"Second Master","phone":"%s","email":"%s","role":"MASTER_ADMIN"}
					""".formatted(uniquePhone(), uniqueEmail())))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("MASTER_ADMIN_PROTECTED"));
		Integer stillActive = new JdbcTemplate(dataSource).queryForObject(
				"select count(*) from users where id = ? and status = 'ACTIVE' and deleted_at is null", Integer.class, master);
		org.assertj.core.api.Assertions.assertThat(stillActive).isEqualTo(1);
	}

	private UUID seed(String role) {
		UUID id = UUID.randomUUID();
		String phone = uniquePhone();
		new JdbcTemplate(dataSource).update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, phone, uniqueEmail(), passwords.encode("password1"), role);
		if ("STUDENT".equals(role)) {
			new JdbcTemplate(dataSource).update(
					"INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, 'Seed', ?)", id,
					"S" + id.toString().substring(0, 8));
		}
		return id;
	}

	private String token(UUID userId) throws Exception {
		String phone = new JdbcTemplate(dataSource).queryForObject("select phone from users where id = ?", String.class,
				userId);
		return tokenFromLogin(phone);
	}

	private String tokenFromLogin(String phone) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"identifier":"%s","password":"password1"}
					""".formatted(phone)))
			.andExpect(status().isOk())
			.andReturn();
		String body = result.getResponse().getContentAsString();
		String marker = "\"accessToken\":\"";
		int start = body.indexOf(marker) + marker.length();
		return body.substring(start, body.indexOf('"', start));
	}

	private void register(String phone) throws Exception {
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"Student","phone":"%s","email":"%s","password":"password1"}
					""".formatted(phone, uniqueEmail())))
			.andExpect(status().isCreated());
	}

	private static String accountJson() {
		return accountJson(uniquePhone(), uniqueEmail());
	}

	private static String accountJson(String phone, String email) {
		return """
				{"fullName":"Created User","phone":"%s","email":"%s"}
				""".formatted(phone, email);
	}

	private static String uniquePhone() {
		int suffix = java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999);
		return "+8801" + suffix;
	}

	private static String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@example.com";
	}

}
