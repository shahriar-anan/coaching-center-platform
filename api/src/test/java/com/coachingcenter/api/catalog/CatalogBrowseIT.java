package com.coachingcenter.api.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
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
class CatalogBrowseIT extends PostgresIntegrationTest {

	private static final String OPEN_EN = "OpenCourseEnglish";
	private static final String OPEN_BN = "খোলা কোর্স";
	private static final String CLOSED_EN = "ClosedCourseEnglish";
	private static final String DRAFT_EN = "DraftCourseEnglish";
	private static final String ARCHIVED_EN = "ArchivedCourseEnglish";
	private static final String BOOK_EN = "AvailableBookEnglish";
	private static final String BOOK_BN = "উপলব্ধ বই";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	private String master;

	private UUID openCourseId;

	private UUID closedCourseId;

	private UUID draftCourseId;

	private UUID archivedCourseId;

	private UUID availableBookId;

	private UUID unavailableBookId;

	private UUID openCoverId;

	private UUID bookCoverId;

	private UUID lectureMaterialId;

	private String student;

	@BeforeEach
	void seedCatalog() throws Exception {
		master = token(seed("MASTER_ADMIN"));
		openCourseId = createCourse(OPEN_EN, OPEN_BN);
		publish(openCourseId);
		UUID lectureId = createLecture(openCourseId);
		mockMvc.perform(patch("/api/v1/courses/" + openCourseId + "/nodes/" + lectureId).header("Authorization",
				"Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"videoUrl":"https://www.youtube.com/watch?v=dQw4w9WgXcQ","notes":"secret notes","free":true}
					"""))
			.andExpect(status().isOk());
		openCoverId = uploadCourseCover(openCourseId);
		lectureMaterialId = uploadLectureMaterial(openCourseId, lectureId);

		closedCourseId = createCourse(CLOSED_EN, "বন্ধ কোর্স");
		publish(closedCourseId);
		mockMvc.perform(post("/api/v1/courses/" + closedCourseId + "/close").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());

		draftCourseId = createCourse(DRAFT_EN, "খসড়া কোর্স");

		archivedCourseId = createCourse(ARCHIVED_EN, "আর্কাইভ কোর্স");
		publish(archivedCourseId);
		mockMvc.perform(post("/api/v1/courses/" + archivedCourseId + "/archive").header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());

		availableBookId = createBook(BOOK_EN, BOOK_BN, true);
		bookCoverId = uploadBookCover(availableBookId);
		unavailableBookId = createBook("HiddenBookEnglish", "লুকানো বই", false);

		String phone = uniquePhone();
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"Student","phone":"%s","email":"%s","password":"password1"}
					""".formatted(phone, uniqueEmail())))
			.andExpect(status().isCreated());
		student = tokenFromPhone(phone);
	}

	@Test
	void studentCatalogBrowse() throws Exception {
		mockMvc.perform(get("/api/v1/catalog/courses")).andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/v1/catalog/courses").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[?(@.titleEn=='" + OPEN_EN + "')]").exists())
			.andExpect(jsonPath("$.content[?(@.titleEn=='" + CLOSED_EN + "')]").doesNotExist())
			.andExpect(jsonPath("$.content[?(@.titleEn=='" + DRAFT_EN + "')]").doesNotExist())
			.andExpect(jsonPath("$.content[?(@.titleEn=='" + ARCHIVED_EN + "')]").doesNotExist());

		mockMvc.perform(get("/api/v1/catalog/courses").header("Authorization", "Bearer " + student).param("q", OPEN_EN))
			.andExpect(jsonPath("$.content[0].titleEn").value(OPEN_EN));
		mockMvc.perform(get("/api/v1/catalog/courses").header("Authorization", "Bearer " + student).param("q", OPEN_BN))
			.andExpect(jsonPath("$.content[0].titleBn").value(OPEN_BN));
		mockMvc.perform(get("/api/v1/catalog/courses").header("Authorization", "Bearer " + student).param("q", "secret notes"))
			.andExpect(jsonPath("$.content.length()").value(0));

		mockMvc.perform(get("/api/v1/catalog/courses/" + openCourseId).header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.contentTree[?(@.nodeType=='LECTURE')].free").isArray())
			.andExpect(jsonPath("$.videoRef").doesNotExist())
			.andExpect(jsonPath("$.notes").doesNotExist());

		mockMvc.perform(get("/api/v1/catalog/courses/" + closedCourseId).header("Authorization", "Bearer " + student))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/catalog/courses/" + draftCourseId).header("Authorization", "Bearer " + student))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/catalog/courses/" + archivedCourseId).header("Authorization", "Bearer " + student))
			.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/v1/catalog/books").header("Authorization", "Bearer " + student))
			.andExpect(jsonPath("$.content[?(@.titleEn=='" + BOOK_EN + "')]").exists())
			.andExpect(jsonPath("$.content[?(@.titleEn=='HiddenBookEnglish')]").doesNotExist());
		mockMvc.perform(get("/api/v1/catalog/books").header("Authorization", "Bearer " + student).param("q", BOOK_EN))
			.andExpect(jsonPath("$.content[0].titleEn").value(BOOK_EN));
		mockMvc.perform(get("/api/v1/catalog/books").header("Authorization", "Bearer " + student).param("q", BOOK_BN))
			.andExpect(jsonPath("$.content[0].titleBn").value(BOOK_BN));
		mockMvc.perform(get("/api/v1/catalog/books/" + availableBookId).header("Authorization", "Bearer " + student))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/files/" + openCoverId).header("Authorization", "Bearer " + student))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/files/" + bookCoverId).header("Authorization", "Bearer " + student))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/files/" + lectureMaterialId).header("Authorization", "Bearer " + student))
			.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/courses").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/v1/catalog/courses").header("Authorization", "Bearer " + master))
			.andExpect(status().isForbidden());
	}

	private UUID createCourse(String titleEn, String titleBn) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/courses").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{
					  "titleEn":"%s",
					  "titleBn":"%s",
					  "contentLanguage":"EN",
					  "description":"Course description",
					  "deliveryMode":"ONLINE",
					  "accessType":"FREEMIUM"
					}
					""".formatted(titleEn, titleBn)))
			.andExpect(status().isCreated())
			.andReturn();
		return id(result);
	}

	private void publish(UUID courseId) throws Exception {
		mockMvc.perform(patch("/api/v1/courses/" + courseId).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"price\":100}"))
			.andExpect(status().isOk());
		mockMvc.perform(patch("/api/v1/courses/" + courseId).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"PUBLISHED\"}"))
			.andExpect(status().isOk());
	}

	private UUID createLecture(UUID courseId) throws Exception {
		MvcResult result = mockMvc
			.perform(post("/api/v1/courses/" + courseId + "/nodes").header("Authorization", "Bearer " + master)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nodeType\":\"LECTURE\",\"title\":\"Lecture one\"}"))
			.andExpect(status().isCreated())
			.andReturn();
		return id(result);
	}

	private UUID uploadCourseCover(UUID courseId) throws Exception {
		MvcResult result = mockMvc
			.perform(multipart("/api/v1/courses/" + courseId + "/cover")
				.file(new MockMultipartFile("file", "cover.png", "image/png", "png".getBytes()))
				.header("Authorization", "Bearer " + master))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private UUID uploadLectureMaterial(UUID courseId, UUID lectureId) throws Exception {
		MvcResult result = mockMvc
			.perform(multipart("/api/v1/courses/" + courseId + "/materials")
				.file(new MockMultipartFile("file", "notes.pdf", "application/pdf", "pdf".getBytes()))
				.param("lectureNodeId", lectureId.toString())
				.header("Authorization", "Bearer " + master))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private UUID createBook(String titleEn, String titleBn, boolean available) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/books").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{
					  "titleEn":"%s",
					  "titleBn":"%s",
					  "author":"Author",
					  "description":"Book description"
					}
					""".formatted(titleEn, titleBn)))
			.andExpect(status().isCreated())
			.andReturn();
		UUID bookId = id(result);
		mockMvc.perform(patch("/api/v1/books/" + bookId).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"price\":50}"))
			.andExpect(status().isOk());
		if (available) {
			mockMvc.perform(patch("/api/v1/books/" + bookId).header("Authorization", "Bearer " + master)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"available\":true}"))
				.andExpect(status().isOk());
		}
		return bookId;
	}

	private UUID uploadBookCover(UUID bookId) throws Exception {
		MvcResult result = mockMvc
			.perform(multipart("/api/v1/books/" + bookId + "/cover")
				.file(new MockMultipartFile("file", "cover.png", "image/png", "png".getBytes()))
				.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("coverFileId").asText());
	}

	private UUID id(MvcResult result) throws Exception {
		JsonNode node = json.readTree(result.getResponse().getContentAsString());
		return UUID.fromString(node.get("id").asText());
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
		else {
			new JdbcTemplate(dataSource).update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, 'Admin')", id);
		}
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

	private static String uniquePhone() {
		int suffix = java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999);
		return "+8801" + suffix;
	}

	private static String uniqueEmail() {
		return "catalog-" + UUID.randomUUID() + "@example.com";
	}

}
