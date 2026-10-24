package com.coachingcenter.api.users;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

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
class InstructorDirectoryIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder passwords;

	@Test
	void theMasterAdminSearchesFiltersAndSeesAssignedModules() throws Exception {
		String master = login("+8801700000001", "bootstrap-password-1");
		String token = "Dir" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		String assignedPhone = uniquePhone();
		String assignedId = createInstructor(master, token + " Assigned", assignedPhone, token + "-a@example.com");
		String pendingId = createInstructor(master, token + " Pending", uniquePhone(), token + "-p@example.com");
		String idleId = createInstructor(master, token + " Idle", uniquePhone(), token + "-i@example.com");
		jdbc.update("update users set status = 'ACTIVE' where id in (?, ?)", UUID.fromString(assignedId),
				UUID.fromString(idleId));
		UUID module = module(token + " Module", false);
		assign(module, assignedId);
		UUID deletedModule = module(token + " Gone", true);
		assign(deletedModule, idleId);

		mockMvc.perform(get("/api/v1/instructors").param("q", token).header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(3))
			.andExpect(jsonPath("$.content[0].fullName").value(token + " Assigned"))
			.andExpect(jsonPath("$.content[1].fullName").value(token + " Idle"))
			.andExpect(jsonPath("$.content[2].fullName").value(token + " Pending"))
			.andExpect(jsonPath("$.content[0].modules[0].titleEn").value(token + " Module"))
			.andExpect(jsonPath("$.content[0].modules[0].titleBn").value("মডিউল"))
			.andExpect(jsonPath("$.content[1].modules").isEmpty())
			.andExpect(jsonPath("$.content[2].status").value("PENDING_ACTIVATION"));

		mockMvc.perform(get("/api/v1/instructors").param("q", token).param("status", "PENDING_ACTIVATION")
			.header("Authorization", "Bearer " + master))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].id").value(pendingId));
		mockMvc.perform(get("/api/v1/instructors").param("q", token).param("assigned", "true")
			.header("Authorization", "Bearer " + master))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].id").value(assignedId));
		mockMvc.perform(get("/api/v1/instructors").param("q", token).param("assigned", "false")
			.header("Authorization", "Bearer " + master))
			.andExpect(jsonPath("$.totalElements").value(2));
		mockMvc.perform(get("/api/v1/instructors").param("q", assignedPhone.substring(3)).header("Authorization", "Bearer " + master))
			.andExpect(jsonPath("$.content[0].id").value(assignedId));
		mockMvc.perform(get("/api/v1/instructors").param("q", token + "-p@example").header("Authorization", "Bearer " + master))
			.andExpect(jsonPath("$.content[0].id").value(pendingId));
		mockMvc.perform(get("/api/v1/instructors").param("q", token).param("size", "2").param("page", "1")
			.header("Authorization", "Bearer " + master))
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.totalPages").value(2));
	}

	@Test
	void anInvalidFilterIsRejectedAndOnlyInstructorsAreListed() throws Exception {
		String master = login("+8801700000001", "bootstrap-password-1");
		mockMvc.perform(get("/api/v1/instructors").param("status", "BANNED").header("Authorization", "Bearer " + master))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		mockMvc.perform(get("/api/v1/instructors").param("assigned", "maybe").header("Authorization", "Bearer " + master))
			.andExpect(status().isBadRequest());

		String token = "Only" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		mockMvc.perform(post("/api/v1/students").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"%s Student","phone":"%s","email":"%s-s@example.com"}
					""".formatted(token, uniquePhone(), token)))
			.andExpect(status().isCreated());
		String deletedId = createInstructor(master, token + " Deleted", uniquePhone(), token + "-d@example.com");
		jdbc.update("update users set deleted_at = now() where id = ?", UUID.fromString(deletedId));
		mockMvc.perform(get("/api/v1/instructors").param("q", token).header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void everyoneElseIsRefused() throws Exception {
		mockMvc.perform(get("/api/v1/instructors")).andExpect(status().isUnauthorized());
		for (String role : new String[] { "SYSTEM_ADMIN", "INSTRUCTOR", "STUDENT" }) {
			String phone = uniquePhone();
			jdbc.update("""
					insert into users (id, phone, email, password_hash, role, status, created_at, updated_at)
					values (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
					""", UUID.randomUUID(), phone, "dir-" + UUID.randomUUID() + "@example.com",
					passwords.encode("password1"), role);
			mockMvc.perform(get("/api/v1/instructors").header("Authorization", "Bearer " + login(phone, "password1")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		}
	}

	private String createInstructor(String master, String name, String phone, String email) throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/instructors").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"%s","phone":"%s","email":"%s"}
					""".formatted(name, phone, email)))
			.andExpect(status().isCreated())
			.andReturn();
		return field(created, "id");
	}

	private UUID module(String title, boolean deleted) {
		UUID category = UUID.randomUUID();
		jdbc.update("""
				insert into live_exam_categories (id, title_en, title_bn, position, created_at, updated_at)
				values (?, 'Cat', 'ক্যাট', 0, now(), now())
				""", category);
		UUID module = UUID.randomUUID();
		jdbc.update("""
				insert into live_exam_modules (id, category_id, title_en, title_bn, description_en, description_bn,
				  access_mode, status, finally_saved, position, created_at, updated_at, deleted_at)
				values (?, ?, ?, 'মডিউল', 'd', 'd', 'MANUAL', 'PUBLISHED', true, 0, now(), now(), ?)
				""", module, category, title, deleted ? java.sql.Timestamp.from(java.time.Instant.now()) : null);
		return module;
	}

	private void assign(UUID module, String instructorId) {
		jdbc.update("insert into live_exam_instructors (module_id, instructor_id, assigned_at) values (?, ?, now())",
				module, UUID.fromString(instructorId));
	}

	private String login(String identifier, String password) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"identifier":"%s","password":"%s"}
					""".formatted(identifier, password)))
			.andExpect(status().isOk())
			.andReturn();
		return field(result, "accessToken");
	}

	private static String field(MvcResult result, String name) throws Exception {
		String body = result.getResponse().getContentAsString();
		String marker = "\"" + name + "\":\"";
		int start = body.indexOf(marker) + marker.length();
		return body.substring(start, body.indexOf('"', start));
	}

	private static String uniquePhone() {
		return "+8801" + ThreadLocalRandom.current().nextInt(100000000, 999999999);
	}

}
