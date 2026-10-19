package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, LiveExamIT.ClockConfig.class })
class LiveExamIT extends PostgresIntegrationTest {

	private static final Instant TEN_AM = Instant.parse("2026-10-20T04:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Autowired
	private MutableClock clock;

	@BeforeEach
	void setClock() {
		clock.set(TEN_AM);
	}

	@Test
	void mcqAndWrittenExamsShareOnePublishedModule() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		UUID module = publishModule(staff, "Shared");
		UUID mcq = createExam(staff, module, "MCQ", "Noon quiz", "দুপুর কুইজ", "2026-10-20T12:00:00+06:00", 30, 40, null);
		UUID written = createExam(staff, module, "WRITTEN", "Noon paper", "দুপুর খাতা", "2026-10-20T13:00:00+06:00", 30, 40,
				20);

		JsonNode list = exams(staff, module);
		assertThat(ids(list)).contains(mcq, written);
		assertThat(typeOf(list, mcq)).isEqualTo("MCQ");
		assertThat(typeOf(list, written)).isEqualTo("WRITTEN");
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		assertThat(jdbc.queryForObject("select count(*) from live_exams where id in (?, ?)", Integer.class, mcq, written))
			.isEqualTo(2);
		assertThat(jdbc.queryForObject("select count(*) from paper_components where id in (?, ?)", Integer.class, mcq,
				written)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from exam_modules where id = ?", Integer.class, module)).isZero();
	}

	@Test
	void publishRequiresPassPercentQuestionsAndAWrittenPaper() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		UUID module = publishModule(staff, "Gates");

		UUID noPercent = createExam(staff, module, "MCQ", "No percent", "শতকরা নেই", "2026-10-20T12:00:00+06:00", 30, null,
				null);
		addQuestion(staff, noPercent, "Stem", 1, "Because", "Yes", "No");
		finalSave(staff, noPercent);
		mockMvc.perform(post("/api/v1/live-exams/" + noPercent + "/publish").header("Authorization", "Bearer " + staff))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PASS_PERCENT_REQUIRED"));

		UUID emptyMcq = createExam(staff, module, "MCQ", "Empty", "খালি", "2026-10-20T15:00:00+06:00", 30, 50, null);
		finalSave(staff, emptyMcq);
		mockMvc.perform(post("/api/v1/live-exams/" + emptyMcq + "/publish").header("Authorization", "Bearer " + staff))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("QUESTIONS_REQUIRED"));

		UUID bareWritten = createExam(staff, module, "WRITTEN", "Bare", "খালি খাতা", "2026-10-20T16:00:00+06:00", 30, 50,
				null);
		finalSave(staff, bareWritten);
		mockMvc.perform(post("/api/v1/live-exams/" + bareWritten + "/publish").header("Authorization", "Bearer " + staff))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("WRITTEN_PAPER_REQUIRED"));
	}

	@Test
	void anEndedWindowCannotBePublished() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		UUID module = publishModule(staff, "Ended");
		UUID exam = createExam(staff, module, "MCQ", "Yesterday", "গতকাল", "2026-10-20T12:00:00+06:00", 40, 50, null);
		addQuestion(staff, exam, "Stem", 1, "Because", "Yes", "No");
		finalSave(staff, exam);
		clock.set(Instant.parse("2026-10-20T06:40:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + staff))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EXAM_WINDOW_ENDED"));
		assertThat(exam(staff, exam).get("status").asText()).isEqualTo("DRAFT");
	}

	@Test
	void mcqMaximumIsTheSumAndTheStudentUpcomingPayloadHidesStems() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		UUID studentId = seed("STUDENT");
		String student = token(studentId);
		UUID module = publishModule(staff, "Marks");
		new JdbcTemplate(dataSource).update(
				"insert into live_exam_roster (module_id, student_id, created_at) values (?, ?, now())", module, studentId);
		UUID exam = createExam(staff, module, "MCQ", "Sum", "যোগ", "2026-10-20T12:00:00+06:00", 40, 50, null);
		addQuestion(staff, exam, "Two plus two", 2, "Four", "4", "5");
		addQuestion(staff, exam, "Three plus three", 3, "Six", "6", "7");
		addQuestion(staff, exam, "Five plus five", 5, "Ten", "10", "11");
		finalSave(staff, exam);
		publish(staff, exam);

		JsonNode staffView = exam(staff, exam);
		assertThat(staffView.get("maximum").asInt()).isEqualTo(10);
		assertThat(staffView.get("questions")).hasSize(3);
		for (JsonNode question : staffView.get("questions")) {
			int correct = 0;
			for (JsonNode option : question.get("options")) {
				if (option.get("correct").asBoolean()) {
					correct++;
				}
			}
			assertThat(correct).isEqualTo(1);
		}

		String studentBody = mockMvc.perform(get("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.maximum").value(10))
			.andExpect(jsonPath("$.questions").doesNotExist())
			.andExpect(jsonPath("$.liveAt").value("2026-10-20T12:00:00+06:00"))
			.andExpect(jsonPath("$.endAt").value("2026-10-20T12:40:00+06:00"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(studentBody).doesNotContain("Two plus two", "Three plus three", "Five plus five");
	}

	@Test
	void fiveMinuteGapIsEnforcedInsideOneModuleOnly() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		UUID module = publishModule(staff, "Gap");
		UUID otherModule = publishModule(staff, "Other gap");
		UUID written = createExam(staff, module, "WRITTEN", "Morning paper", "সকাল খাতা", "2026-10-20T12:00:00+06:00", 40,
				40, 20);
		uploadPdf(staff, written);
		finalSave(staff, written);
		publish(staff, written);

		UUID crowded = createExam(staff, module, "MCQ", "Too soon", "খুব তাড়াতাড়ি", "2026-10-20T12:44:00+06:00", 20, 40,
				null);
		mockMvc.perform(get("/api/v1/live-exams/" + crowded).header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.liveAt").value("2026-10-20T12:44:00+06:00"));
		addQuestion(staff, crowded, "Stem", 2, null, "A", "B");
		finalSave(staff, crowded);
		mockMvc.perform(post("/api/v1/live-exams/" + crowded + "/publish").header("Authorization", "Bearer " + staff))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EXAM_WINDOW_CONFLICT"))
			.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Morning paper")))
			.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("2026-10-20T12:00:00+06:00")))
			.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("2026-10-20T12:40:00+06:00")));

		mockMvc.perform(patch("/api/v1/live-exams/" + crowded).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"liveAt\":\"2026-10-20T12:45:00+06:00\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DRAFT"));
		finalSave(staff, crowded);
		publish(staff, crowded);

		UUID elsewhere = createExam(staff, otherModule, "MCQ", "Other module", "অন্য মডিউল", "2026-10-20T12:44:00+06:00",
				20, 40, null);
		addQuestion(staff, elsewhere, "Stem", 2, null, "A", "B");
		finalSave(staff, elsewhere);
		publish(staff, elsewhere);
	}

	@Test
	void questionAndScheduleLockWhenTheWindowStartsAndTheExamStaysPublished() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		UUID module = publishModule(staff, "Lock");
		UUID exam = createExam(staff, module, "MCQ", "Locked quiz", "বন্ধ কুইজ", "2026-10-20T12:00:00+06:00", 40, 50, null);
		UUID question = addQuestion(staff, exam, "Original stem", 2, "Because", "A", "B");
		finalSave(staff, exam);
		publish(staff, exam);

		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		mockMvc.perform(patch("/api/v1/live-exams/" + exam + "/questions/" + question)
			.header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content(questionJson("Changed stem", 2, "Because", "A", "B")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONTENT_LOCKED"));
		mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"liveAt\":\"2026-10-20T12:05:00+06:00\",\"durationMinutes\":50}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONTENT_LOCKED"));

		JsonNode still = exam(staff, exam);
		assertThat(still.get("questions").get(0).get("stem").asText()).isEqualTo("Original stem");
		assertThat(still.get("liveAt").asText()).isEqualTo("2026-10-20T12:00:00+06:00");
		assertThat(still.get("durationMinutes").asInt()).isEqualTo(40);
		assertThat(still.get("status").asText()).isEqualTo("PUBLISHED");
		mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"passPercent\":10}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONTENT_LOCKED"));
		assertThat(exam(staff, exam).get("passPercent").asInt()).isEqualTo(50);

		UUID written = createExam(staff, module, "WRITTEN", "Locked paper", "বন্ধ খাতা", "2026-10-20T12:45:00+06:00", 40,
				50, 10);
		uploadPdf(staff, written);
		finalSave(staff, written);
		publish(staff, written);
		clock.set(Instant.parse("2026-10-20T06:50:00Z"));
		mockMvc.perform(patch("/api/v1/live-exams/" + written).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"maximum\":4,\"passPercent\":80}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONTENT_LOCKED"));
		JsonNode paper = exam(staff, written);
		assertThat(paper.get("maximum").asInt()).isEqualTo(10);
		assertThat(paper.get("passPercent").asInt()).isEqualTo(50);
		assertThat(paper.get("status").asText()).isEqualTo("PUBLISHED");

		clock.set(Instant.parse("2026-10-20T07:00:00Z"));
		mockMvc.perform(get("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PUBLISHED"));
	}

	private UUID publishModule(String token, String title) throws Exception {
		UUID category = createCategory(token, title + " cat", "বিভাগ");
		MvcResult created = mockMvc.perform(post("/api/v1/live-exam-modules").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"categoryId":"%s","titleEn":"%s","titleBn":"মডিউল","descriptionEn":"d","descriptionBn":"বি"}
					""".formatted(category, title)))
			.andExpect(status().isCreated())
			.andReturn();
		UUID module = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/final-save").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/publish").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		return module;
	}

	private UUID createCategory(String token, String titleEn, String titleBn) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/live-exam-categories").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titleEn":"%s","titleBn":"%s"}
					""".formatted(titleEn, titleBn)))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private UUID createExam(String token, UUID moduleId, String type, String titleEn, String titleBn, String liveAt,
			int duration, Integer passPercent, Integer maximum) throws Exception {
		String percent = passPercent == null ? "" : ",\"passPercent\":" + passPercent;
		String max = maximum == null ? "" : ",\"maximum\":" + maximum;
		MvcResult result = mockMvc
			.perform(post("/api/v1/live-exam-modules/" + moduleId + "/exams").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "examType":"%s",
						  "titleEn":"%s",
						  "titleBn":"%s",
						  "descriptionEn":"English description",
						  "descriptionBn":"বাংলা বিবরণ",
						  "rulesEn":"English rules",
						  "rulesBn":"বাংলা নিয়ম",
						  "liveAt":"%s",
						  "durationMinutes":%d%s%s
						}
						""".formatted(type, titleEn, titleBn, liveAt, duration, percent, max)))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private UUID addQuestion(String token, UUID examId, String stem, int marks, String explanation, String correct,
			String wrong) throws Exception {
		MvcResult result = mockMvc
			.perform(post("/api/v1/live-exams/" + examId + "/questions").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(questionJson(stem, marks, explanation, correct, wrong)))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private static String questionJson(String stem, int marks, String explanation, String correct, String wrong) {
		String explanationField = explanation == null ? "" : ",\"explanation\":\"%s\"".formatted(explanation);
		return """
				{"stem":"%s","marks":%d%s,"options":[{"text":"%s","correct":true},{"text":"%s","correct":false}]}
				""".formatted(stem, marks, explanationField, correct, wrong);
	}

	private void uploadPdf(String token, UUID examId) throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "paper.pdf", "application/pdf", tinyPdf());
		mockMvc.perform(multipart("/api/v1/live-exams/" + examId + "/question-pdf").file(file)
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.questionPdf").value(true));
	}

	private void finalSave(String token, UUID examId) throws Exception {
		mockMvc.perform(post("/api/v1/live-exams/" + examId + "/final-save").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.finallySaved").value(true));
	}

	private void publish(String token, UUID examId) throws Exception {
		mockMvc.perform(post("/api/v1/live-exams/" + examId + "/publish").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PUBLISHED"));
	}

	private JsonNode exams(String token, UUID moduleId) throws Exception {
		MvcResult result = mockMvc
			.perform(get("/api/v1/live-exam-modules/" + moduleId + "/exams").header("Authorization", "Bearer " + token)
				.param("size", "100"))
			.andExpect(status().isOk())
			.andReturn();
		return json.readTree(result.getResponse().getContentAsString()).get("content");
	}

	private JsonNode exam(String token, UUID examId) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/live-exams/" + examId).header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn();
		return json.readTree(result.getResponse().getContentAsString());
	}

	private static java.util.List<UUID> ids(JsonNode content) {
		java.util.List<UUID> ids = new java.util.ArrayList<>();
		for (JsonNode node : content) {
			ids.add(UUID.fromString(node.get("id").asText()));
		}
		return ids;
	}

	private static String typeOf(JsonNode content, UUID id) {
		for (JsonNode node : content) {
			if (id.toString().equals(node.get("id").asText())) {
				return node.get("examType").asText();
			}
		}
		return null;
	}

	private static byte[] tinyPdf() {
		return """
				%PDF-1.1
				1 0 obj<</Type/Catalog>>endobj
				trailer<</Root 1 0 R>>
				%%EOF
				""".getBytes(StandardCharsets.US_ASCII);
	}

	private UUID seed(String role) {
		UUID id = UUID.randomUUID();
		new JdbcTemplate(dataSource).update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, uniquePhone(), uniqueEmail(), passwords.encode("password1"), role);
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
		return "live-exam-" + UUID.randomUUID() + "@example.com";
	}

	@TestConfiguration
	static class ClockConfig {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(TEN_AM);
		}

	}

}
