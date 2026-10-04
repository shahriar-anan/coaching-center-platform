package com.coachingcenter.api.catalog;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import org.springframework.mock.web.MockMultipartFile;
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
class CatalogCourseReadIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Test
	void studentCourseReadPayload() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		UUID main = createCategory(master, "Main Category", "মেইন");
		UUID sub = createCategory(master, "Sub Category", "সাব", main);
		UUID courseId = createPublishedCourse(master);
		placeCourse(master, courseId, sub, 1);
		UUID sectionId = createSection(master, courseId);
		UUID lectureId = createLecture(master, courseId, sectionId, false);
		uploadCourseFile(master, courseId, "course-file.pdf", false);
		uploadLectureFile(master, courseId, lectureId, "lecture-file.pdf");

		String student = tokenFromPhone(registerStudentPhone());

		mockMvc.perform(get("/api/v1/catalog/courses/" + courseId).header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.categoryPath[0].titleEn").value("Main Category"))
			.andExpect(jsonPath("$.categoryPath[1].titleEn").value("Sub Category"))
			.andExpect(jsonPath("$.courseMaterials[0].originalFileName").value("course-file.pdf"))
			.andExpect(jsonPath("$.courseMaterials[0].contentType").isNotEmpty());

		mockMvc.perform(get("/api/v1/catalog/courses/" + courseId + "/lectures/" + lectureId)
			.header("Authorization", "Bearer " + student))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ENROLLMENT_REQUIRED"));

		mockMvc.perform(get("/api/v1/course-categories").header("Authorization", "Bearer " + student))
			.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/v1/catalog/course-categories").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == '%s')].titleEn".formatted(main), hasItem("Main Category")));
	}

	private UUID createCategory(String token, String titleEn, String titleBn) throws Exception {
		return createCategory(token, titleEn, titleBn, null);
	}

	private UUID createCategory(String token, String titleEn, String titleBn, UUID parentId) throws Exception {
		String parent = parentId == null ? "null" : "\"" + parentId + "\"";
		MvcResult result = mockMvc.perform(post("/api/v1/course-categories").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titleEn":"%s","titleBn":"%s","parentId":%s}
					""".formatted(titleEn, titleBn, parent)))
			.andExpect(status().isCreated())
			.andReturn();
		return id(result);
	}

	private UUID createPublishedCourse(String token) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/courses").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titleEn":"Read Course","titleBn":"রিড","description":"d","contentLanguage":"EN","price":100,"deliveryMode":"ONLINE","accessType":"FREEMIUM"}
					"""))
			.andExpect(status().isCreated())
			.andReturn();
		UUID courseId = id(result);
		mockMvc.perform(patch("/api/v1/courses/" + courseId).header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"PUBLISHED\"}"))
			.andExpect(status().isOk());
		return courseId;
	}

	private void placeCourse(String token, UUID courseId, UUID categoryId, int position) throws Exception {
		mockMvc.perform(patch("/api/v1/courses/" + courseId).header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"categoryId":"%s","categoryPosition":%d}
					""".formatted(categoryId, position)))
			.andExpect(status().isOk());
	}

	private UUID createSection(String token, UUID courseId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/courses/" + courseId + "/nodes").header("Authorization",
				"Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"nodeType\":\"SECTION\",\"title\":\"Section 1\"}"))
			.andExpect(status().isCreated())
			.andReturn();
		return id(result);
	}

	private UUID createLecture(String token, UUID courseId, UUID parentId, boolean free) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/courses/" + courseId + "/nodes").header("Authorization",
				"Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"nodeType":"LECTURE","title":"Lecture 1","parentId":"%s","free":%s}
					""".formatted(parentId, free)))
			.andExpect(status().isCreated())
			.andReturn();
		return id(result);
	}

	private void uploadCourseFile(String token, UUID courseId, String name, boolean free) throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", name, "application/pdf", new byte[] { 1, 2, 3 });
		mockMvc.perform(multipart("/api/v1/courses/" + courseId + "/materials").file(file)
			.param("free", String.valueOf(free))
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isCreated());
	}

	private void uploadLectureFile(String token, UUID courseId, UUID lectureId, String name) throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", name, "application/pdf", new byte[] { 1, 2, 3 });
		mockMvc.perform(multipart("/api/v1/courses/" + courseId + "/materials").file(file)
			.param("lectureNodeId", lectureId.toString())
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isCreated());
	}

	private String registerStudentPhone() throws Exception {
		String phone = uniquePhone();
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"Reader","phone":"%s","email":"%s","password":"password1"}
					""".formatted(phone, uniqueEmail())))
			.andExpect(status().isCreated());
		return phone;
	}

	private UUID seed(String role) {
		UUID id = UUID.randomUUID();
		String phone = uniquePhone();
		new JdbcTemplate(dataSource).update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, phone, uniqueEmail(), passwords.encode("password1"), role);
		new JdbcTemplate(dataSource).update(
				"INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, 'Seed', ?)", id,
				"S" + id.toString().substring(0, 8));
		return id;
	}

	private String token(UUID userId) throws Exception {
		String phone = new JdbcTemplate(dataSource).queryForObject("select phone from users where id = ?", String.class,
				userId);
		return tokenFromPhone(phone);
	}

	private String tokenFromPhone(String phone) throws Exception {
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

	private UUID id(MvcResult result) throws Exception {
		JsonNode body = json.readTree(result.getResponse().getContentAsString());
		return UUID.fromString(body.get("id").asText());
	}

	private static String uniquePhone() {
		int suffix = java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999);
		return "+8801" + suffix;
	}

	private static String uniqueEmail() {
		return "read-" + UUID.randomUUID() + "@example.com";
	}

}
