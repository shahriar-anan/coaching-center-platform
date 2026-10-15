package com.coachingcenter.api.catalog;

import static org.hamcrest.Matchers.hasItem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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
class LectureProgressIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Test
	void lectureProgress() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		UUID courseId = createCourse(master, "FREEMIUM");
		UUID sectionId = createNode(master, courseId, "{\"nodeType\":\"SECTION\",\"title\":\"Section\"}");
		UUID firstId = createNode(master, courseId,
				"{\"nodeType\":\"LECTURE\",\"title\":\"First\",\"parentId\":\"" + sectionId + "\"}");
		UUID middleId = createNode(master, courseId,
				"{\"nodeType\":\"LECTURE\",\"title\":\"Middle\",\"parentId\":\"" + sectionId + "\",\"free\":true}");
		UUID lastId = createNode(master, courseId, "{\"nodeType\":\"LECTURE\",\"title\":\"Last\"}");
		publish(master, courseId);

		UUID studentId = seed("STUDENT");
		String student = token(studentId);
		int before = countProgress(studentId);
		mockMvc.perform(get("/api/v1/catalog/courses/" + courseId + "/lectures/" + middleId)
			.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.previousLectureId").value(firstId.toString()))
			.andExpect(jsonPath("$.nextLectureId").value(lastId.toString()));
		assertThat(countProgress(studentId)).isEqualTo(before);

		MvcResult opened = mockMvc
			.perform(post("/api/v1/me/courses/" + courseId + "/progress").header("Authorization", "Bearer " + student)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"lectureId\":\"" + middleId + "\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.watched").value(false))
			.andReturn();
		Instant firstOpen = Instant.parse(json.readTree(opened.getResponse().getContentAsString()).get("lastOpenedAt").asText());
		Thread.sleep(20);
		MvcResult watched = mockMvc
			.perform(post("/api/v1/me/courses/" + courseId + "/progress").header("Authorization", "Bearer " + student)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"lectureId\":\"" + middleId + "\",\"watched\":true}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.watched").value(true))
			.andReturn();
		Instant secondOpen = Instant.parse(json.readTree(watched.getResponse().getContentAsString()).get("lastOpenedAt").asText());
		assertThat(secondOpen).isAfter(firstOpen);
		mockMvc.perform(post("/api/v1/me/courses/" + courseId + "/progress").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"lectureId\":\"" + middleId + "\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.watched").value(true));

		int afterFree = countProgress(studentId);
		mockMvc.perform(post("/api/v1/me/courses/" + courseId + "/progress").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"lectureId\":\"" + firstId + "\"}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ENROLLMENT_REQUIRED"));
		assertThat(countProgress(studentId)).isEqualTo(afterFree);

		UUID freeCourse = createCourse(master, "FULL_FREE");
		UUID freeLecture = createNode(master, freeCourse, "{\"nodeType\":\"LECTURE\",\"title\":\"Open\"}");
		publish(master, freeCourse);
		mockMvc.perform(post("/api/v1/me/courses/" + freeCourse + "/progress").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"lectureId\":\"" + freeLecture + "\"}"))
			.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/courses/" + courseId + "/close").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
		int beforeClosed = countProgress(studentId);
		mockMvc.perform(post("/api/v1/me/courses/" + courseId + "/progress").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"lectureId\":\"" + middleId + "\"}"))
			.andExpect(status().isNotFound());
		assertThat(countProgress(studentId)).isEqualTo(beforeClosed);
		mockMvc.perform(post("/api/v1/courses/" + courseId + "/reopen").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/courses/" + courseId + "/admissions").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"studentId\":\"" + studentId + "\"}"))
			.andExpect(status().isCreated());
		Thread.sleep(20);
		mockMvc.perform(post("/api/v1/me/courses/" + courseId + "/progress").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"lectureId\":\"" + lastId + "\"}"))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/me/courses").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.courseId == '%s')].lastLectureId".formatted(courseId), hasItem(lastId.toString())));
		mockMvc.perform(get("/api/v1/me/courses/" + courseId + "/progress").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.lastLectureId").value(lastId.toString()))
			.andExpect(jsonPath("$.lectures[?(@.lectureId == '%s')].watched".formatted(middleId), hasItem(true)))
			.andExpect(jsonPath("$.lectures[?(@.lectureId == '%s')].lastOpenedAt".formatted(lastId)).exists());

		mockMvc.perform(get("/api/v1/catalog/courses/" + courseId + "/lectures/" + firstId)
			.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.previousLectureId").doesNotExist());
		mockMvc.perform(get("/api/v1/catalog/courses/" + courseId + "/lectures/" + lastId)
			.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.nextLectureId").doesNotExist());
	}

	private int countProgress(UUID studentId) {
		return new JdbcTemplate(dataSource).queryForObject(
				"select count(*) from lecture_progress where student_id = ? and deleted_at is null", Integer.class,
				studentId);
	}

	private UUID createCourse(String token, String accessType) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/courses").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titleEn":"Progress","titleBn":"প্রগ্রেস","contentLanguage":"EN","description":"d","deliveryMode":"ONLINE","accessType":"%s"}
					""".formatted(accessType)))
			.andExpect(status().isCreated())
			.andReturn();
		return id(result);
	}

	private void publish(String token, UUID courseId) throws Exception {
		String price = "FREEMIUM".equals(accessTypeOf(courseId)) ? "\"price\":100," : "";
		mockMvc.perform(patch("/api/v1/courses/" + courseId).header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{" + price + "\"status\":\"PUBLISHED\"}"))
			.andExpect(status().isOk());
	}

	private String accessTypeOf(UUID courseId) {
		return new JdbcTemplate(dataSource).queryForObject("select access_type from courses where id = ?", String.class,
				courseId);
	}

	private UUID createNode(String token, UUID courseId, String body) throws Exception {
		MvcResult result = mockMvc
			.perform(post("/api/v1/courses/" + courseId + "/nodes").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated())
			.andReturn();
		return id(result);
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
		return "progress-" + UUID.randomUUID() + "@example.com";
	}

}
