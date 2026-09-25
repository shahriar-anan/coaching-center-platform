package com.coachingcenter.api.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
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
import com.coachingcenter.api.auth.CapturingEmailSender;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class AuditLogIT extends PostgresIntegrationTest {

	private static final Set<String> API_OPERATIONS = Set.of(
			"POST /api/v1/auth/register",
			"POST /api/v1/auth/activate",
			"POST /api/v1/auth/login",
			"POST /api/v1/auth/refresh",
			"POST /api/v1/auth/logout",
			"POST /api/v1/auth/change-password",
			"POST /api/v1/auth/forgot-password",
			"POST /api/v1/auth/reset-password",
			"POST /api/v1/auth/verify-email",
			"POST /api/v1/auth/verify-email/resend",
			"GET /api/v1/me",
			"POST /api/v1/admins",
			"GET /api/v1/admins",
			"PATCH /api/v1/admins/{id}/status",
			"DELETE /api/v1/admins/{id}",
			"POST /api/v1/admins/{id}/activation-code",
			"POST /api/v1/students",
			"GET /api/v1/students",
			"GET /api/v1/students/{id}",
			"PATCH /api/v1/students/{id}",
			"PATCH /api/v1/students/{id}/status",
			"POST /api/v1/students/{id}/activation-code",
			"DELETE /api/v1/students/{id}",
			"GET /api/v1/audit-log");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private CapturingEmailSender emails;

	@Autowired
	private ObjectMapper json;

	@Test
	void auditedActionsRecordActorActionAndEntity() throws Exception {
		String phone = uniquePhone();
		String email = uniqueEmail();
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		UUID masterId = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, 'MASTER_ADMIN', 'ACTIVE', now(), now())
				""", masterId, phone, email, passwords.encode("password1"));
		String access = token(phone);

		MvcResult created = mockMvc.perform(post("/api/v1/students").header("Authorization", "Bearer " + access)
			.contentType(MediaType.APPLICATION_JSON)
			.content(account(uniquePhone(), uniqueEmail())))
			.andExpect(status().isCreated())
			.andReturn();
		UUID studentId = UUID.fromString(field(created, "id"));
		assertAudit(jdbc, AuditRecorder.ACTIVATION_CODE_ISSUED, masterId, studentId);

		mockMvc.perform(patch("/api/v1/students/" + studentId + "/status").header("Authorization", "Bearer " + access)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"INACTIVE\"}"))
			.andExpect(status().isNoContent());
		assertAudit(jdbc, AuditRecorder.USER_STATUS_CHANGED, masterId, studentId);

		mockMvc.perform(delete("/api/v1/students/" + studentId).header("Authorization", "Bearer " + access))
			.andExpect(status().isNoContent());
		assertAudit(jdbc, AuditRecorder.USER_DELETED, masterId, studentId);

		String resetEmail = uniqueEmail();
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"Reset User","phone":"%s","email":"%s","password":"password1"}
					""".formatted(uniquePhone(), resetEmail)))
			.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + resetEmail + "\"}")).andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"code":"%s","password":"password2"}
					""".formatted(emails.latestReset(resetEmail))))
			.andExpect(status().isNoContent());
		UUID resetUser = jdbc.queryForObject("select id from users where email = ?", UUID.class, resetEmail);
		Integer resets = jdbc.queryForObject("""
				select count(*) from audit_log
				where action = ? and entity_id = ? and actor_user_id is null
				""", Integer.class, AuditRecorder.PASSWORD_RESET, resetUser);
		assertThat(resets).isEqualTo(1);
	}

	@Test
	void openApiMatchesImplementedEndpoints() throws Exception {
		MvcResult result = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
		JsonNode paths = json.readTree(result.getResponse().getContentAsString()).get("paths");
		Set<String> operations = new java.util.HashSet<>();
		paths.properties().forEach(path -> path.getValue().properties()
			.forEach(method -> operations.add(method.getKey().toUpperCase() + " " + path.getKey())));
		assertThat(operations).isEqualTo(API_OPERATIONS);
	}

	private static void assertAudit(JdbcTemplate jdbc, String action, UUID actor, UUID entity) {
		Integer count = jdbc.queryForObject("""
				select count(*) from audit_log
				where action = ? and actor_user_id = ? and entity_id = ? and entity_type = 'USER'
				""", Integer.class, action, actor, entity);
		assertThat(count).isEqualTo(1);
	}

	private String token(String phone) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"identifier":"%s","password":"password1"}
					""".formatted(phone)))
			.andExpect(status().isOk())
			.andReturn();
		return field(result, "accessToken");
	}

	private String field(MvcResult result, String name) throws Exception {
		String body = result.getResponse().getContentAsString();
		String marker = "\"" + name + "\":\"";
		int start = body.indexOf(marker) + marker.length();
		return body.substring(start, body.indexOf('"', start));
	}

	private static String account(String phone, String email) {
		return """
				{"fullName":"Audit User","phone":"%s","email":"%s"}
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
