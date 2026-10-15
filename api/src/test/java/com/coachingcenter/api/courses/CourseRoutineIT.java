package com.coachingcenter.api.courses;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class CourseRoutineIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Test
	void courseRoutine() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		UUID offline = createCourse(master, "OFFLINE");
		String slots = """
				{"slots":[
				  {"weekday":1,"startTime":"09:00","endTime":"10:00","place":"Room A"},
				  {"weekday":3,"startTime":"11:00","endTime":"12:30"}
				]}
				""";
		mockMvc.perform(put("/api/v1/courses/" + offline + "/routine").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(slots))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].weekday").value(1))
			.andExpect(jsonPath("$[0].startTime").value("09:00"))
			.andExpect(jsonPath("$[0].endTime").value("10:00"))
			.andExpect(jsonPath("$[0].place").value("Room A"))
			.andExpect(jsonPath("$[0].position").value(0))
			.andExpect(jsonPath("$[1].weekday").value(3))
			.andExpect(jsonPath("$[1].position").value(1))
			.andExpect(jsonPath("$[1].place").doesNotExist());
		mockMvc.perform(get("/api/v1/courses/" + offline + "/routine").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].place").value("Room A"))
			.andExpect(jsonPath("$[1].startTime").value("11:00"));

		mockMvc.perform(put("/api/v1/courses/" + offline + "/routine").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"slots":[{"weekday":1,"startTime":"09:00","endTime":"09:00"}]}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		mockMvc.perform(put("/api/v1/courses/" + offline + "/routine").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"slots":[
					  {"weekday":1,"startTime":"09:00","endTime":"10:30"},
					  {"weekday":1,"startTime":"10:00","endTime":"11:00"}
					]}
					"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ROUTINE_OVERLAP"));
		mockMvc.perform(get("/api/v1/courses/" + offline + "/routine").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].place").value("Room A"))
			.andExpect(jsonPath("$.length()").value(2));

		mockMvc.perform(patch("/api/v1/courses/" + offline).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"deliveryMode\":\"ONLINE\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ROUTINE_NOT_APPLICABLE"));
		mockMvc.perform(get("/api/v1/courses/" + offline).header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.deliveryMode").value("OFFLINE"));

		publish(master, offline);
		String student = token(seed("STUDENT"));
		mockMvc.perform(get("/api/v1/catalog/courses/" + offline).header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.routine[0].position").value(0))
			.andExpect(jsonPath("$.routine[1].weekday").value(3));
		mockMvc.perform(put("/api/v1/courses/" + offline + "/routine").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content(slots))
			.andExpect(status().isForbidden());

		UUID online = createCourse(master, "ONLINE");
		mockMvc.perform(put("/api/v1/courses/" + online + "/routine").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(slots))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ROUTINE_NOT_APPLICABLE"));
		int onlineSlots = new JdbcTemplate(dataSource).queryForObject(
				"select count(*) from course_routine_slots where course_id = ? and deleted_at is null", Integer.class, online);
		org.assertj.core.api.Assertions.assertThat(onlineSlots).isZero();
		publish(master, online);
		mockMvc.perform(get("/api/v1/catalog/courses/" + online).header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.routine").isEmpty());

		UUID hybrid = createCourse(master, "HYBRID");
		mockMvc.perform(put("/api/v1/courses/" + hybrid + "/routine").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"slots":[{"weekday":5,"startTime":"16:00","endTime":"17:00","place":"Lab"}]}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].place").value("Lab"));

		mockMvc.perform(put("/api/v1/courses/" + offline + "/routine").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"slots\":[]}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isEmpty());
		mockMvc.perform(get("/api/v1/courses/" + offline + "/routine").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isEmpty());

		mockMvc.perform(post("/api/v1/courses/" + offline + "/close").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/catalog/courses/" + offline).header("Authorization", "Bearer " + student))
			.andExpect(status().isNotFound());
	}

	private UUID createCourse(String token, String deliveryMode) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/courses").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titleEn":"Routine","titleBn":"রুটিন","contentLanguage":"EN","description":"d","deliveryMode":"%s","accessType":"FULL_FREE"}
					""".formatted(deliveryMode)))
			.andExpect(status().isCreated())
			.andReturn();
		return id(result);
	}

	private void publish(String token, UUID courseId) throws Exception {
		mockMvc.perform(patch("/api/v1/courses/" + courseId).header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"PUBLISHED\"}"))
			.andExpect(status().isOk());
	}

	private UUID id(MvcResult result) throws Exception {
		JsonNode body = json.readTree(result.getResponse().getContentAsString());
		return UUID.fromString(body.get("id").asText());
	}

	private UUID seed(String role) {
		UUID userId = UUID.randomUUID();
		new JdbcTemplate(dataSource).update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", userId, uniquePhone(), uniqueEmail(), passwords.encode("password1"), role);
		if ("STUDENT".equals(role)) {
			new JdbcTemplate(dataSource).update(
					"INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, 'Seed', ?)", userId,
					"S" + userId.toString().substring(0, 8));
		}
		else {
			new JdbcTemplate(dataSource).update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, 'Admin')",
					userId);
		}
		return userId;
	}

	private String token(UUID userId) throws Exception {
		String phone = new JdbcTemplate(dataSource).queryForObject("select phone from users where id = ?", String.class,
				userId);
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"identifier\":\"" + phone + "\",\"password\":\"password1\"}"))
			.andExpect(status().isOk())
			.andReturn();
		String body = result.getResponse().getContentAsString();
		String marker = "\"accessToken\":\"";
		int start = body.indexOf(marker) + marker.length();
		return body.substring(start, body.indexOf('"', start));
	}

	private static String uniquePhone() {
		int suffix = java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999);
		return "+8801" + suffix;
	}

	private static String uniqueEmail() {
		return "routine-" + UUID.randomUUID() + "@example.com";
	}

}
