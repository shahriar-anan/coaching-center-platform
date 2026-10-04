package com.coachingcenter.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;
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
class ContentAccessIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	private String master;

	private String studentA;

	private String studentB;

	private UUID studentAId;

	private UUID freemiumCourse;

	private UUID fullFreeCourse;

	private UUID freeLecture;

	private UUID paidLecture;

	private UUID freeCourseMaterial;

	private UUID paidCourseMaterial;

	private UUID freeLectureMaterial;

	private UUID paidLectureMaterial;

	@BeforeEach
	void seed() throws Exception {
		master = token(seed("MASTER_ADMIN"));
		freemiumCourse = createFreemiumCourse();
		freeLecture = createLecture(freemiumCourse, "Free lecture", true);
		paidLecture = createLecture(freemiumCourse, "Paid lecture", false);
		freeCourseMaterial = uploadCourseMaterial(freemiumCourse, true);
		paidCourseMaterial = uploadCourseMaterial(freemiumCourse, false);
		freeLectureMaterial = uploadLectureMaterial(freemiumCourse, freeLecture);
		paidLectureMaterial = uploadLectureMaterial(freemiumCourse, paidLecture);
		fullFreeCourse = createFullFreeCourse();
		createLecture(fullFreeCourse, "Full free lecture", false);

		studentAId = seed("STUDENT");
		studentA = token(studentAId);
		mockMvc.perform(post("/api/v1/courses/" + freemiumCourse + "/admissions").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"studentId":"%s"}
					""".formatted(studentAId)))
			.andExpect(status().isCreated());

		String phone = uniquePhone();
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"StudentB","phone":"%s","email":"%s","password":"password1"}
					""".formatted(phone, uniqueEmail())))
			.andExpect(status().isCreated());
		studentB = tokenFromPhone(phone);
	}

	@Test
	void contentAccessRules() throws Exception {
		mockMvc.perform(get("/api/v1/catalog/courses/" + freemiumCourse + "/lectures/" + freeLecture)
			.header("Authorization", "Bearer " + studentB))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.videoRef").isNotEmpty())
			.andExpect(jsonPath("$.notes").isNotEmpty());

		mockMvc.perform(get("/api/v1/files/" + freeLectureMaterial).header("Authorization", "Bearer " + studentB))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/catalog/courses/" + freemiumCourse + "/materials/" + freeCourseMaterial)
			.header("Authorization", "Bearer " + studentB))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/catalog/courses/" + freemiumCourse + "/lectures/" + paidLecture)
			.header("Authorization", "Bearer " + studentB))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ENROLLMENT_REQUIRED"));
		mockMvc.perform(get("/api/v1/catalog/courses/" + freemiumCourse + "/materials/" + paidCourseMaterial)
			.header("Authorization", "Bearer " + studentB))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ENROLLMENT_REQUIRED"));
		mockMvc.perform(get("/api/v1/files/" + paidLectureMaterial).header("Authorization", "Bearer " + studentB))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("MATERIAL_FORBIDDEN"));

		mockMvc.perform(get("/api/v1/catalog/courses/" + freemiumCourse + "/lectures/" + paidLecture)
			.header("Authorization", "Bearer " + studentA))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.videoRef").isNotEmpty());
		mockMvc.perform(get("/api/v1/catalog/courses/" + freemiumCourse + "/materials/" + paidCourseMaterial)
			.header("Authorization", "Bearer " + studentA))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/files/" + paidLectureMaterial).header("Authorization", "Bearer " + studentA))
			.andExpect(status().isOk());

		UUID fullFreeLecture = new JdbcTemplate(dataSource).queryForObject(
				"select id from content_nodes where course_id = ? and node_type = 'LECTURE' limit 1", UUID.class,
				fullFreeCourse);
		mockMvc.perform(get("/api/v1/catalog/courses/" + fullFreeCourse + "/lectures/" + fullFreeLecture)
			.header("Authorization", "Bearer " + studentB))
			.andExpect(status().isOk());

		String awaitingStudent = registerStudent();
		String trxAwait = "trx-await-" + UUID.randomUUID();
		mockMvc.perform(post("/api/v1/courses/" + freemiumCourse + "/purchases").header("Authorization", "Bearer " + awaitingStudent)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"method":"BKASH","transactionRef":"%s"}
					""".formatted(trxAwait)))
			.andExpect(status().isCreated());
		mockMvc.perform(get("/api/v1/catalog/courses/" + freemiumCourse + "/lectures/" + paidLecture)
			.header("Authorization", "Bearer " + awaitingStudent))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ENROLLMENT_REQUIRED"));

		String failedStudent = registerStudent();
		MvcResult failedPurchase = mockMvc
			.perform(post("/api/v1/courses/" + freemiumCourse + "/purchases").header("Authorization", "Bearer " + failedStudent)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"method":"BKASH","transactionRef":"trx-fail-%s"}
						""".formatted(UUID.randomUUID())))
			.andExpect(status().isCreated())
			.andReturn();
		UUID failedPayment = UUID
			.fromString(json.readTree(failedPurchase.getResponse().getContentAsString()).get("paymentId").asText());
		mockMvc.perform(post("/api/v1/payments/" + failedPayment + "/verify").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"result\":\"FAILED\"}"))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/catalog/courses/" + freemiumCourse + "/lectures/" + paidLecture)
			.header("Authorization", "Bearer " + failedStudent))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ENROLLMENT_REQUIRED"));

		mockMvc.perform(get("/api/v1/me/courses").header("Authorization", "Bearer " + studentA))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].courseId").value(freemiumCourse.toString()));

		mockMvc.perform(post("/api/v1/courses/" + freemiumCourse + "/close").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/catalog/courses/" + freemiumCourse).header("Authorization", "Bearer " + studentA))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/catalog/courses/" + freemiumCourse + "/lectures/" + freeLecture)
			.header("Authorization", "Bearer " + studentA))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/files/" + paidLectureMaterial).header("Authorization", "Bearer " + studentA))
			.andExpect(status().isNotFound());

		mockMvc.perform(post("/api/v1/courses/" + freemiumCourse + "/reopen").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/catalog/courses/" + freemiumCourse + "/lectures/" + paidLecture)
			.header("Authorization", "Bearer " + studentA))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ENROLLMENT_REQUIRED"));
		mockMvc.perform(get("/api/v1/me/courses").header("Authorization", "Bearer " + studentA))
			.andExpect(jsonPath("$.length()").value(0));

		mockMvc.perform(post("/api/v1/courses/" + freemiumCourse + "/admissions").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"studentId":"%s"}
					""".formatted(studentAId)))
			.andExpect(status().isCreated());
		mockMvc.perform(get("/api/v1/catalog/courses/" + freemiumCourse + "/lectures/" + paidLecture)
			.header("Authorization", "Bearer " + studentA))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/me/courses").header("Authorization", "Bearer " + studentA))
			.andExpect(jsonPath("$.length()").value(1));

		String docs = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse()
			.getContentAsString();
		assertThat(docs).doesNotContain("\"EXAM\"");
	}

	private UUID createFreemiumCourse() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/courses").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{
					  "titleEn":"FreemiumAccess",
					  "titleBn":"ফ্রিমিয়াম",
					  "contentLanguage":"EN",
					  "description":"d",
					  "deliveryMode":"ONLINE",
					  "accessType":"FREEMIUM"
					}
					"""))
			.andExpect(status().isCreated())
			.andReturn();
		UUID id = id(result);
		mockMvc.perform(patch("/api/v1/courses/" + id).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"price\":100,\"status\":\"PUBLISHED\"}"))
			.andExpect(status().isOk());
		return id;
	}

	private UUID createFullFreeCourse() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/courses").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{
					  "titleEn":"FullFreeAccess",
					  "titleBn":"ফুল ফ্রি",
					  "contentLanguage":"EN",
					  "description":"d",
					  "deliveryMode":"ONLINE",
					  "accessType":"FULL_FREE"
					}
					"""))
			.andExpect(status().isCreated())
			.andReturn();
		UUID id = id(result);
		mockMvc.perform(patch("/api/v1/courses/" + id).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"PUBLISHED\"}"))
			.andExpect(status().isOk());
		return id;
	}

	private UUID createLecture(UUID courseId, String title, boolean free) throws Exception {
		MvcResult created = mockMvc
			.perform(post("/api/v1/courses/" + courseId + "/nodes").header("Authorization", "Bearer " + master)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"nodeType":"LECTURE","title":"%s"}
						""".formatted(title)))
			.andExpect(status().isCreated())
			.andReturn();
		UUID lectureId = id(created);
		mockMvc.perform(patch("/api/v1/courses/" + courseId + "/nodes/" + lectureId).header("Authorization",
				"Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"videoUrl":"https://www.youtube.com/watch?v=dQw4w9WgXcQ","notes":"notes-%s","free":%s}
					""".formatted(title, free)))
			.andExpect(status().isOk());
		return lectureId;
	}

	private UUID uploadCourseMaterial(UUID courseId, boolean free) throws Exception {
		MvcResult uploaded = mockMvc
			.perform(multipart("/api/v1/courses/" + courseId + "/materials")
				.file(new MockMultipartFile("file", "course-" + free + ".pdf", "application/pdf", "pdf".getBytes()))
				.param("free", String.valueOf(free))
				.header("Authorization", "Bearer " + master))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(uploaded.getResponse().getContentAsString()).get("id").asText());
	}

	private UUID uploadLectureMaterial(UUID courseId, UUID lectureId) throws Exception {
		MvcResult uploaded = mockMvc
			.perform(multipart("/api/v1/courses/" + courseId + "/materials")
				.file(new MockMultipartFile("file", "lecture.pdf", "application/pdf", "pdf".getBytes()))
				.param("lectureNodeId", lectureId.toString())
				.header("Authorization", "Bearer " + master))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(uploaded.getResponse().getContentAsString()).get("id").asText());
	}

	private String registerStudent() throws Exception {
		String phone = uniquePhone();
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"Student","phone":"%s","email":"%s","password":"password1"}
					""".formatted(phone, uniqueEmail())))
			.andExpect(status().isCreated());
		return tokenFromPhone(phone);
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
		return "content-" + UUID.randomUUID() + "@example.com";
	}

}
