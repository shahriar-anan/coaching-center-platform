package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
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
class ExamAccessGateIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	private String master;

	private String system;

	private String studentEnrolled;

	private String studentFree;

	private UUID freemiumCourse;

	private UUID fullFreeCourse;

	private UUID openModule;

	private String openModuleTitle;

	private UUID enrolledFreemiumModule;

	private UUID enrolledFullFreeModule;

	private UUID purchaseModule;

	private UUID mcqComponent;

	private UUID writtenComponent;

	@BeforeEach
	void seed() throws Exception {
		master = token(seed("MASTER_ADMIN"));
		system = token(seed("SYSTEM_ADMIN"));
		freemiumCourse = createCourse("FREEMIUM");
		fullFreeCourse = createCourse("FULL_FREE");

		UUID enrolledStudentId = seed("STUDENT");
		studentEnrolled = token(enrolledStudentId);
		mockMvc.perform(post("/api/v1/courses/" + freemiumCourse + "/admissions").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"studentId\":\"" + enrolledStudentId + "\"}"))
			.andExpect(status().isCreated());

		String phone = uniquePhone();
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"Free","phone":"%s","email":"%s","password":"password1"}
					""".formatted(phone, uniqueEmail())))
			.andExpect(status().isCreated());
		studentFree = tokenFromPhone(phone);

		openModuleTitle = "Open Exam " + UUID.randomUUID();
		openModule = publishModule(openModuleTitle, "খোলা", "OPEN", null, null);
		enrolledFreemiumModule = publishModule("Freemium Exam", "ফ্রিম", "ENROLLED_ONLY", freemiumCourse, null);
		enrolledFullFreeModule = publishModule("Full free Exam", "ফুল", "ENROLLED_ONLY", fullFreeCourse, null);
		purchaseModule = publishModule("Paid Exam", "কেনা", "PURCHASE_REQUIRED", null, 300);

		UUID paper = createPaper(openModule);
		mcqComponent = createComponent(paper, "MCQ");
		writtenComponent = createComponent(paper, "WRITTEN");
		addMcq(mcqComponent);
		addWritten(writtenComponent);
		patchMcqRules(mcqComponent);
	}

	@Test
	void studentAccessGate() throws Exception {
		mockMvc.perform(get("/api/v1/catalog/exam-modules")).andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/v1/catalog/exam-modules").header("Authorization", "Bearer " + studentFree)
			.param("q", openModuleTitle))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].id").value(openModule.toString()));

		mockMvc.perform(get("/api/v1/catalog/exam-modules").header("Authorization", "Bearer " + studentFree)
			.param("q", "খোলা"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].titleBn").value("খোলা"));

		mockMvc.perform(get("/api/v1/catalog/exam-modules/" + openModule).header("Authorization", "Bearer " + studentFree))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessGranted").value(true))
			.andExpect(jsonPath("$.descriptionEn").isNotEmpty())
			.andExpect(jsonPath("$.nodes[0].titleEn").isNotEmpty())
			.andExpect(jsonPath("$.stem").doesNotExist());

		UUID draft = createModule("Draft", "ড্রাফট");
		mockMvc.perform(get("/api/v1/catalog/exam-modules/" + draft).header("Authorization", "Bearer " + studentFree))
			.andExpect(status().isNotFound());

		UUID noMode = createModule("No mode", "মোড");
		mockMvc.perform(patch("/api/v1/exam-modules/" + noMode).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"PUBLISHED\"}"))
			.andExpect(status().isOk());
		assertThat(moduleIds(json.readTree(mockMvc
			.perform(get("/api/v1/catalog/exam-modules").header("Authorization", "Bearer " + studentFree))
			.andReturn()
			.getResponse()
			.getContentAsString()).get("content"))).doesNotContain(noMode.toString());

		mockMvc.perform(get("/api/v1/catalog/exam-modules/" + enrolledFullFreeModule).header("Authorization",
				"Bearer " + studentFree))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessGranted").value(true));

		mockMvc.perform(get("/api/v1/catalog/exam-modules/" + enrolledFreemiumModule).header("Authorization",
				"Bearer " + studentEnrolled))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessGranted").value(true));
		mockMvc.perform(get("/api/v1/catalog/exam-modules/" + enrolledFreemiumModule).header("Authorization",
				"Bearer " + studentFree))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessGranted").value(false));

		mockMvc.perform(get("/api/v1/catalog/exam-modules/" + purchaseModule).header("Authorization", "Bearer " + studentFree))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessGranted").value(false));

		mockMvc.perform(post("/api/v1/courses/" + fullFreeCourse + "/close").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/catalog/exam-modules/" + enrolledFullFreeModule).header("Authorization",
				"Bearer " + studentFree))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessGranted").value(false));
		mockMvc.perform(post("/api/v1/courses/" + fullFreeCourse + "/reopen").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());

		String awaiting = registerStudent();
		mockMvc.perform(post("/api/v1/courses/" + freemiumCourse + "/purchases").header("Authorization", "Bearer " + awaiting)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"method\":\"BKASH\",\"transactionRef\":\"trx-" + UUID.randomUUID() + "\"}"))
			.andExpect(status().isCreated());
		mockMvc.perform(get("/api/v1/catalog/exam-modules/" + enrolledFreemiumModule).header("Authorization", "Bearer " + awaiting))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessGranted").value(false));

		mockMvc.perform(post("/api/v1/courses/" + freemiumCourse + "/close").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/catalog/exam-modules/" + enrolledFreemiumModule).header("Authorization",
				"Bearer " + studentEnrolled))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessGranted").value(false));
		mockMvc.perform(post("/api/v1/courses/" + freemiumCourse + "/reopen").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());

		UUID oldOpeningStudent = seed("STUDENT");
		String oldOpeningToken = token(oldOpeningStudent);
		mockMvc.perform(post("/api/v1/courses/" + freemiumCourse + "/admissions").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"studentId\":\"" + oldOpeningStudent + "\"}"))
			.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/courses/" + freemiumCourse + "/close").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/courses/" + freemiumCourse + "/reopen").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/catalog/exam-modules/" + enrolledFreemiumModule).header("Authorization",
				"Bearer " + oldOpeningToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessGranted").value(false));

		mockMvc.perform(get("/api/v1/catalog/exam-modules/" + openModule + "/components/" + mcqComponent)
			.header("Authorization", "Bearer " + studentFree))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.descriptionEn").value("MCQ rules EN"))
			.andExpect(jsonPath("$.questionCount").value(1))
			.andExpect(jsonPath("$.maxMarks").value(1))
			.andExpect(jsonPath("$.questions").isEmpty());

		mockMvc.perform(get("/api/v1/catalog/exam-modules/" + openModule + "/components/" + writtenComponent)
			.header("Authorization", "Bearer " + studentFree))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.questions[0].stem").value("Written Q"))
			.andExpect(jsonPath("$.questions[0].explanation").doesNotExist());

		UUID lockedPaper = createPaper(enrolledFreemiumModule);
		UUID lockedMcq = createComponent(lockedPaper, "MCQ");
		mockMvc.perform(get("/api/v1/catalog/exam-modules/" + enrolledFreemiumModule + "/components/" + lockedMcq)
			.header("Authorization", "Bearer " + studentFree))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("EXAM_ACCESS_REQUIRED"));

		UUID archived = publishModule("Archived", "আর্কাইভ", "OPEN", null, null);
		UUID archivedPaper = createPaper(archived);
		UUID archivedMcq = createComponent(archivedPaper, "MCQ");
		Instant activeStart = Instant.now().minusSeconds(60);
		Instant activeEnd = Instant.now().plusSeconds(3600);
		new JdbcTemplate(dataSource).update("""
				UPDATE paper_components SET scheduled = true, starts_at = ?, ends_at = ?, duration_seconds = 60,
				description_en = 'EN', description_bn = 'BN' WHERE id = ?
				""", Timestamp.from(activeStart), Timestamp.from(activeEnd), archivedMcq);
		mockMvc.perform(post("/api/v1/exam-modules/" + archived + "/archive").header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/catalog/exam-modules/" + archived).header("Authorization", "Bearer " + studentFree))
			.andExpect(status().isNotFound());
		assertThat(moduleIds(json.readTree(mockMvc
			.perform(get("/api/v1/catalog/exam-modules").header("Authorization", "Bearer " + studentFree))
			.andReturn()
			.getResponse()
			.getContentAsString()).get("content"))).doesNotContain(archived.toString());
		mockMvc.perform(get("/api/v1/exam-components/" + archivedMcq).header("Authorization", "Bearer " + system))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.lifecycle").value("ACTIVE"));
		new JdbcTemplate(dataSource).update("update paper_components set ends_at = ? where id = ?",
				Timestamp.from(Instant.now().minusSeconds(30)), archivedMcq);
		mockMvc.perform(get("/api/v1/exam-components/" + archivedMcq).header("Authorization", "Bearer " + system))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.lifecycle").value("ENDED"));
	}

	private UUID publishModule(String titleEn, String titleBn, String mode, UUID courseId, Integer price)
			throws Exception {
		UUID module = createModule(titleEn, titleBn);
		if (price != null) {
			mockMvc.perform(patch("/api/v1/exam-modules/" + module).header("Authorization", "Bearer " + master)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"price\":" + price + "}"))
				.andExpect(status().isOk());
		}
		String courseJson = courseId == null ? "null" : "\"" + courseId + "\"";
		mockMvc.perform(patch("/api/v1/exam-modules/" + module + "/access").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"accessMode\":\"" + mode + "\",\"courseId\":" + courseJson + "}"))
			.andExpect(status().isOk());
		mockMvc.perform(patch("/api/v1/exam-modules/" + module).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"PUBLISHED\"}"))
			.andExpect(status().isOk());
		return module;
	}

	private UUID createModule(String titleEn, String titleBn) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/exam-modules").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titleEn":"%s","titleBn":"%s","descriptionEn":"root en","descriptionBn":"root bn"}
					""".formatted(titleEn, titleBn)))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private UUID createPaper(UUID moduleId) throws Exception {
		MvcResult result = mockMvc
			.perform(post("/api/v1/exam-modules/" + moduleId + "/nodes").header("Authorization", "Bearer " + system)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nodeType\":\"PAPER\",\"titleEn\":\"Paper\",\"titleBn\":\"পেপার\"}"))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private UUID createComponent(UUID paperId, String type) throws Exception {
		MvcResult result = mockMvc
			.perform(post("/api/v1/exam-papers/" + paperId + "/components").header("Authorization", "Bearer " + system)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"type\":\"" + type + "\"}"))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private void addMcq(UUID componentId) throws Exception {
		mockMvc.perform(post("/api/v1/exam-components/" + componentId + "/questions").header("Authorization",
				"Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"stem":"secret","marks":1,"subject":"S","options":[{"text":"a","correct":true},{"text":"b","correct":false}]}
					"""))
			.andExpect(status().isCreated());
	}

	private void addWritten(UUID componentId) throws Exception {
		mockMvc.perform(post("/api/v1/exam-components/" + componentId + "/questions").header("Authorization",
				"Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"stem\":\"Written Q\",\"marks\":5,\"subject\":\"S\",\"explanation\":\"hidden\"}"))
			.andExpect(status().isCreated());
	}

	private void patchMcqRules(UUID componentId) throws Exception {
		UUID paperId = paperForComponent(componentId);
		mockMvc.perform(patch("/api/v1/exam-papers/" + paperId).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"passPercent\":50}"))
			.andExpect(status().isOk());
		mockMvc.perform(patch("/api/v1/exam-components/" + componentId).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{
					  "descriptionEn":"MCQ rules EN",
					  "descriptionBn":"MCQ rules BN",
					  "startsAt":"2030-01-01T10:00:00Z",
					  "endsAt":"2030-01-01T12:00:00Z",
					  "durationSeconds":3600
					}
					"""))
			.andExpect(status().isOk());
	}

	private UUID paperForComponent(UUID componentId) {
		return new JdbcTemplate(dataSource).queryForObject(
				"select paper_id from paper_components where id = ?", UUID.class, componentId);
	}

	private UUID createCourse(String accessType) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/courses").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{
					  "titleEn":"Course-%s",
					  "titleBn":"কোর্স",
					  "contentLanguage":"EN",
					  "description":"d",
					  "deliveryMode":"ONLINE",
					  "accessType":"%s"
					}
					""".formatted(UUID.randomUUID().toString().substring(0, 6), accessType)))
			.andExpect(status().isCreated())
			.andReturn();
		UUID id = UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
		if ("FREEMIUM".equals(accessType)) {
			mockMvc.perform(patch("/api/v1/courses/" + id).header("Authorization", "Bearer " + master)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"price\":100,\"status\":\"PUBLISHED\"}"))
				.andExpect(status().isOk());
		}
		else {
			mockMvc.perform(patch("/api/v1/courses/" + id).header("Authorization", "Bearer " + master)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"PUBLISHED\"}"))
				.andExpect(status().isOk());
		}
		return id;
	}

	private String registerStudent() throws Exception {
		String phone = uniquePhone();
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"S","phone":"%s","email":"%s","password":"password1"}
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
		if ("SYSTEM_ADMIN".equals(role) || "MASTER_ADMIN".equals(role)) {
			new JdbcTemplate(dataSource).update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, 'Admin')", id);
		}
		return id;
	}

	private String token(UUID userId) throws Exception {
		String phone = new JdbcTemplate(dataSource).queryForObject("select phone from users where id = ?", String.class,
				userId);
		return tokenFromLogin(phone, "password1");
	}

	private String tokenFromPhone(String phone) throws Exception {
		return tokenFromLogin(phone, "password1");
	}

	private String tokenFromLogin(String phone, String password) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"identifier":"%s","password":"%s"}
					""".formatted(phone, password)))
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
		return "exam-gate-" + UUID.randomUUID() + "@example.com";
	}

	private static java.util.List<String> moduleIds(JsonNode content) {
		java.util.List<String> ids = new java.util.ArrayList<>();
		for (JsonNode node : content) {
			ids.add(node.get("id").asText());
		}
		return ids;
	}

}
