package com.coachingcenter.api.courses;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;
import com.coachingcenter.api.files.LocalFileStorage;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class CourseInstructorIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Autowired
	private LocalFileStorage files;

	@Test
	void courseInstructors() throws Exception {
		UUID masterId = seed("MASTER_ADMIN");
		String master = token(masterId);
		String system = token(seed("SYSTEM_ADMIN"));
		String student = token(seed("STUDENT"));
		UUID courseId = createCourse(master);
		UUID sectionId = createNode(master, courseId, "{\"nodeType\":\"SECTION\",\"title\":\"Section\"}");
		UUID lectureId = createNode(master, courseId,
				"{\"nodeType\":\"LECTURE\",\"title\":\"Lecture\",\"parentId\":\"" + sectionId + "\"}");

		mockMvc.perform(post("/api/v1/courses/" + courseId + "/instructors").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		UUID firstId = id(mockMvc
			.perform(post("/api/v1/courses/" + courseId + "/instructors").header("Authorization", "Bearer " + master)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Ada\",\"description\":\"Leads the class\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("Ada"))
			.andExpect(jsonPath("$.description").value("Leads the class"))
			.andExpect(jsonPath("$.position").value(0))
			.andExpect(jsonPath("$.userId").doesNotExist())
			.andExpect(jsonPath("$.role").doesNotExist())
			.andReturn());
		UUID secondId = id(mockMvc
			.perform(post("/api/v1/courses/" + courseId + "/instructors").header("Authorization", "Bearer " + master)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Grace\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.position").value(1))
			.andReturn());

		mockMvc.perform(patch("/api/v1/courses/" + courseId + "/instructors/" + firstId)
			.header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Ada Lovelace\",\"description\":null,\"position\":1}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Ada Lovelace"))
			.andExpect(jsonPath("$.description").doesNotExist())
			.andExpect(jsonPath("$.position").value(1));
		mockMvc.perform(get("/api/v1/courses/" + courseId + "/instructors").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(secondId.toString()))
			.andExpect(jsonPath("$[0].position").value(0))
			.andExpect(jsonPath("$[1].id").value(firstId.toString()));

		mockMvc.perform(multipart("/api/v1/courses/" + courseId + "/instructors/" + secondId + "/photo")
			.file(file("notes.pdf", "application/pdf", new byte[] { 1, 2 }))
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		mockMvc.perform(get("/api/v1/courses/" + courseId + "/instructors").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == '%s')].photoFileId".formatted(secondId)).doesNotExist());

		UUID photoId = photoId(mockMvc
			.perform(multipart("/api/v1/courses/" + courseId + "/instructors/" + secondId + "/photo")
				.file(file("ada.jpg", "image/jpeg", new byte[] { 9, 8, 7 }))
				.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andReturn());
		String stored = storedName(photoId);
		assertThat(Files.exists(files.resolve(stored))).isTrue();

		mockMvc.perform(multipart("/api/v1/courses/" + courseId + "/instructors/" + secondId + "/photo")
			.file(file("big.jpg", "image/jpeg", new byte[1100]))
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isPayloadTooLarge())
			.andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
		assertThat(Files.exists(files.resolve(stored))).isTrue();
		mockMvc.perform(get("/api/v1/courses/" + courseId + "/instructors").header("Authorization", "Bearer " + master))
			.andExpect(jsonPath("$[?(@.id == '%s')].photoFileId".formatted(secondId), hasItem(photoId.toString())));

		UUID replacedId = photoId(mockMvc
			.perform(multipart("/api/v1/courses/" + courseId + "/instructors/" + secondId + "/photo")
				.file(file("ada-2.jpg", "image/jpeg", new byte[] { 4, 5, 6 }))
				.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andReturn());
		assertThat(replacedId).isNotEqualTo(photoId);
		assertThat(Files.exists(files.resolve(stored))).isFalse();
		assertThat(Files.exists(files.resolve(storedName(replacedId)))).isTrue();
		Integer hidden = new JdbcTemplate(dataSource).queryForObject(
				"select count(*) from materials where id = ? and deleted_at is not null", Integer.class, photoId);
		assertThat(hidden).isEqualTo(1);

		mockMvc.perform(multipart("/api/v1/courses/" + courseId + "/materials")
			.file(file("handout.pdf", "application/pdf", new byte[] { 1 }))
			.param("title", "Handout")
			.param("kind", "HANDOUT")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isCreated());
		mockMvc.perform(multipart("/api/v1/courses/" + courseId + "/materials")
			.file(file("section.pdf", "application/pdf", new byte[] { 2 }))
			.param("sectionNodeId", sectionId.toString())
			.param("title", "Section file")
			.param("kind", "HANDOUT")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isCreated());
		mockMvc.perform(multipart("/api/v1/courses/" + courseId + "/materials")
			.file(file("lecture.pdf", "application/pdf", new byte[] { 3 }))
			.param("lectureNodeId", lectureId.toString())
			.param("title", "Lecture file")
			.param("kind", "HANDOUT")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isCreated());
		publish(master, courseId);

		mockMvc.perform(get("/api/v1/catalog/courses/" + courseId).header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.instructors[?(@.id == '%s')].name".formatted(secondId), hasItem("Grace")))
			.andExpect(jsonPath("$.instructors[?(@.id == '%s')].photoFileId".formatted(secondId), hasItem(replacedId.toString())))
			.andExpect(jsonPath("$.instructors[?(@.id == '%s')].position".formatted(firstId), hasItem(1)))
			.andExpect(jsonPath("$.instructors[0].userId").doesNotExist())
			.andExpect(jsonPath("$.instructors[0].role").doesNotExist())
			.andExpect(jsonPath("$.courseMaterials[*].id", not(hasItem(replacedId.toString()))))
			.andExpect(jsonPath("$.sectionMaterials[*].id", not(hasItem(replacedId.toString()))));
		mockMvc.perform(get("/api/v1/catalog/courses/" + courseId + "/lectures/" + lectureId)
			.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.materials[*].id", not(hasItem(replacedId.toString()))));
		mockMvc.perform(get("/api/v1/files/" + replacedId).header("Authorization", "Bearer " + student))
			.andExpect(status().isOk());

		UUID emptyCourse = createCourse(master);
		publish(master, emptyCourse);
		mockMvc.perform(get("/api/v1/catalog/courses/" + emptyCourse).header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.instructors").isEmpty());

		mockMvc.perform(post("/api/v1/courses/" + courseId + "/close").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/catalog/courses/" + courseId).header("Authorization", "Bearer " + student))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/files/" + replacedId).header("Authorization", "Bearer " + student))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/files/" + replacedId).header("Authorization", "Bearer " + system))
			.andExpect(status().isOk());

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
			.delete("/api/v1/courses/" + courseId + "/instructors/" + secondId)
			.header("Authorization", "Bearer " + system))
			.andExpect(status().isForbidden());
		String kept = storedName(replacedId);
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
			.delete("/api/v1/courses/" + courseId + "/instructors/" + secondId)
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/courses/" + courseId + "/instructors").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].id", not(hasItem(secondId.toString()))));
		Integer audits = new JdbcTemplate(dataSource).queryForObject("""
				select count(*) from audit_log
				where action = 'COURSE_INSTRUCTOR_DELETE' and entity_type = 'COURSE_INSTRUCTOR'
				  and entity_id = ? and actor_user_id = ?
				""", Integer.class, secondId, masterId);
		assertThat(audits).isEqualTo(1);
		assertThat(Files.exists(files.resolve(kept))).isFalse();
	}

	private UUID photoId(MvcResult result) throws Exception {
		JsonNode body = json.readTree(result.getResponse().getContentAsString());
		return UUID.fromString(body.get("photoFileId").asText());
	}

	private String storedName(UUID materialId) {
		return new JdbcTemplate(dataSource).queryForObject("select stored_file_name from materials where id = ?",
				String.class, materialId);
	}

	private static MockMultipartFile file(String name, String type, byte[] bytes) {
		return new MockMultipartFile("file", name, type, bytes);
	}

	private UUID createCourse(String token) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/courses").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titleEn":"Instructors","titleBn":"শিক্ষক","contentLanguage":"EN","description":"d","deliveryMode":"ONLINE","accessType":"FULL_FREE"}
					"""))
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

	private UUID createNode(String token, UUID courseId, String body) throws Exception {
		return id(mockMvc.perform(post("/api/v1/courses/" + courseId + "/nodes").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body))
			.andExpect(status().isCreated())
			.andReturn());
	}

	private UUID id(MvcResult result) throws Exception {
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
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
		return "instructor-" + UUID.randomUUID() + "@example.com";
	}

}
