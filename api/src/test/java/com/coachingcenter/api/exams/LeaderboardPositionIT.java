package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Where a result stands on the board (NR-01 D4 and D5, R11 to R13). The paper has three questions worth 2, 4 and 4 marks,
 * a pass mark of 50 per cent (5 of 10), so the possible scores are 10, 8, 6, 4 and 2 (4 and 2 fail).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, LeaderboardPositionIT.ClockConfig.class })
class LeaderboardPositionIT extends PostgresIntegrationTest {

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
	void aLiveSubmitSeesItsCurrentRankAndAnArchivedOneSeesARelativeRank() throws Exception {
		Paper paper = paper("Positions");
		List<UUID> s = paper.students();
		UUID a = s.get(0), b = s.get(1), c = s.get(2), d = s.get(3), e = s.get(4);
		String ta = token(a), tb = token(b), tc = token(c), td = token(d), te = token(e);

		// A scores 10 and B scores 6, both posted before C sits.
		live(paper, ta, "2026-10-20T06:05:00Z", "2026-10-20T06:10:00Z", paper.q1(), paper.q2(), paper.q3());
		live(paper, tb, "2026-10-20T06:06:00Z", "2026-10-20T06:12:00Z", paper.q1(), paper.q2());

		// C scores 8 at 06:20: ranked among the board so far (A 10, C 8, B 6), so rank 2 of 3 (D4, CURRENT).
		clock.set(Instant.parse("2026-10-20T06:15:00Z"));
		sit(paper, tc);
		save(paper, tc, paper.q2(), paper.q3());
		clock.set(Instant.parse("2026-10-20T06:20:00Z"));
		submit(paper, tc)
			.andExpect(jsonPath("$.score").value(8))
			.andExpect(jsonPath("$.position.type").value("CURRENT"))
			.andExpect(jsonPath("$.position.rank").value(2))
			.andExpect(jsonPath("$.position.of").value(3))
			.andExpect(jsonPath("$.position.belowPass").value(false));

		// D scores 4, below the pass mark: no rank (R13).
		clock.set(Instant.parse("2026-10-20T06:25:00Z"));
		sit(paper, td);
		save(paper, td, paper.q2());
		clock.set(Instant.parse("2026-10-20T06:30:00Z"));
		submit(paper, td)
			.andExpect(jsonPath("$.position.belowPass").value(true))
			.andExpect(jsonPath("$.position.rank").doesNotExist());

		// E starts after the window (06:45) and scores 10, equal to A: an archived score is RELATIVE, and a tie takes
		// the better rank, so E is 1 of 3. E is not on the board.
		clock.set(Instant.parse("2026-10-20T06:45:00Z"));
		sit(paper, te);
		save(paper, te, paper.q1(), paper.q2(), paper.q3());
		clock.set(Instant.parse("2026-10-20T06:46:00Z"));
		submit(paper, te)
			.andExpect(jsonPath("$.position.type").value("RELATIVE"))
			.andExpect(jsonPath("$.position.rank").value(1))
			.andExpect(jsonPath("$.position.of").value(3));

		// The board opens at the window end (D12): three rows, highest first, E absent.
		clock.set(Instant.parse("2026-10-20T06:50:00Z"));
		String board = mockMvc.perform(get("/api/v1/live-exams/" + paper.exam() + "/leaderboard")
			.header("Authorization", "Bearer " + te))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		JsonNode rows = json.readTree(board).get("rows");
		assertThat(rows).hasSize(3);
		assertThat(rows.get(0).get("score").asDouble()).isEqualTo(10.0);
		assertThat(rows.get(1).get("score").asDouble()).isEqualTo(8.0);
		assertThat(rows.get(2).get("score").asDouble()).isEqualTo(6.0);
		assertThat(json.readTree(board).get("me").get("type").asText()).isEqualTo("RELATIVE");
	}

	@Test
	void aRetakeIsNeverOnTheBoardAndGetsARelativePosition() throws Exception {
		Paper paper = paper("Retake position");
		List<UUID> s = paper.students();
		UUID a = s.get(0), b = s.get(1), c = s.get(2);
		String ta = token(a), tb = token(b), tc = token(c);
		live(paper, ta, "2026-10-20T06:05:00Z", "2026-10-20T06:10:00Z", paper.q1(), paper.q2(), paper.q3());
		live(paper, tb, "2026-10-20T06:06:00Z", "2026-10-20T06:12:00Z", paper.q1(), paper.q2());
		live(paper, tc, "2026-10-20T06:07:00Z", "2026-10-20T06:14:00Z", paper.q2(), paper.q3());

		// A's retake after the window scores 6: the board holds 10, 8 and 6, so two rows beat it and it is rank 3 of 3.
		clock.set(Instant.parse("2026-10-20T06:50:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + paper.exam() + "/retake").header("Authorization", "Bearer " + ta))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.attemptKind").value("RETAKE"));
		// The board before the retake is posted: staff see every row, so the scores the retake is ranked against are visible.
		mockMvc.perform(get("/api/v1/live-exams/" + paper.exam() + "/leaderboard").header("Authorization", "Bearer " + paper.master()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.rows.length()").value(3))
			.andExpect(jsonPath("$.rows[0].score").value(10.0))
			.andExpect(jsonPath("$.rows[1].score").value(8.0))
			.andExpect(jsonPath("$.rows[2].score").value(6.0));
		save(paper, ta, paper.q1(), paper.q2());
		submit(paper, ta)
			.andExpect(jsonPath("$.attemptNo").value(2))
			.andExpect(jsonPath("$.score").value(6.0))
			.andExpect(jsonPath("$.position.type").value("RELATIVE"))
			.andExpect(jsonPath("$.position.rank").value(3))
			.andExpect(jsonPath("$.position.of").value(3));

		// The retake is not one of the rows: still three rows.
		String board = mockMvc.perform(get("/api/v1/live-exams/" + paper.exam() + "/leaderboard")
			.header("Authorization", "Bearer " + ta))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(json.readTree(board).get("rows")).hasSize(3);
	}

	/** One exam: three questions worth 2, 4 and 4 marks, each with a correct option and a wrong one; the window is 06:00 to 06:40. */
	private record Paper(UUID exam, UUID q1, UUID q2, UUID q3, List<UUID> students, List<UUID> correct, List<UUID> wrong, String master) {

		UUID student() {
			return students.get(0);
		}

	}

	private Paper paper(String title) throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		UUID module = publishModule(staff, title);
		MvcResult created = mockMvc
			.perform(post("/api/v1/live-exam-modules/" + module + "/exams").header("Authorization", "Bearer " + staff)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "examType":"MCQ","paperLanguage":"BN","titleEn":"%s quiz","titleBn":"কুইজ",
						  "descriptionEn":"d","descriptionBn":"বি","rulesEn":"English rules","rulesBn":"নিয়ম",
						  "liveAt":"2026-10-20T12:00:00+06:00","durationMinutes":40,"passPercent":50
						}
						""".formatted(title)))
			.andExpect(status().isCreated())
			.andReturn();
		UUID exam = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
		List<UUID> questions = new ArrayList<>();
		List<UUID> correct = new ArrayList<>();
		List<UUID> wrong = new ArrayList<>();
		int[] marks = { 2, 4, 4 };
		for (int index = 0; index < marks.length; index++) {
			MvcResult added = mockMvc.perform(post("/api/v1/live-exams/" + exam + "/questions")
				.header("Authorization", "Bearer " + staff)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"stem":"Q%d","marks":%d,"explanation":"Because","options":[{"text":"Right","correct":true},{"text":"Wrong","correct":false}]}
						""".formatted(index + 1, marks[index])))
				.andExpect(status().isCreated())
				.andReturn();
			JsonNode body = json.readTree(added.getResponse().getContentAsString());
			questions.add(UUID.fromString(body.get("id").asText()));
			for (JsonNode option : body.get("options")) {
				if (option.get("correct").asBoolean()) {
					correct.add(UUID.fromString(option.get("id").asText()));
				}
				else {
					wrong.add(UUID.fromString(option.get("id").asText()));
				}
			}
		}
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/final-save").header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
		List<UUID> students = new ArrayList<>();
		for (int index = 0; index < 5; index++) {
			UUID student = seed("STUDENT");
			mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/roster").header("Authorization", "Bearer " + master)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"studentId":"%s"}
						""".formatted(student)))
				.andExpect(status().isCreated());
			students.add(student);
		}
		return new Paper(exam, questions.get(0), questions.get(1), questions.get(2), students, correct, wrong, master);
	}

	/** Negative marks (R8): two students with the same number of right answers. The one who picked a wrong option loses a mark. */
	@Test
	void negativeMarksChangeTheOrderOfTwoStudentsWithTheSameCorrectCount() throws Exception {
		Paper paper = paper("Negative marks");
		new JdbcTemplate(dataSource).update("update live_exams set negative_marks = 0.25 where id = ?", paper.exam());
		List<UUID> s = paper.students();
		String wrongPicker = token(s.get(0));
		String blank = token(s.get(1));
		// The first student is right on q1 and q2 and picks a wrong option on q3: 6 - 0.25 = 5.75. The second is right on
		// q1 and q2 and leaves q3 blank: 6. The same correct count, but the wrong pick costs the negative mark.
		clock.set(Instant.parse("2026-10-20T06:05:00Z"));
		sit(paper, wrongPicker);
		answer(paper, wrongPicker, new UUID[] { paper.q1(), paper.q2(), paper.q3() },
				new UUID[] { correctOption(paper, paper.q1()), correctOption(paper, paper.q2()), wrongOption(paper, paper.q3()) });
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		submit(paper, wrongPicker).andExpect(jsonPath("$.score").value(5.75));
		live(paper, blank, "2026-10-20T06:06:00Z", "2026-10-20T06:12:00Z", paper.q1(), paper.q2());

		clock.set(Instant.parse("2026-10-20T06:50:00Z"));
		JsonNode rows = json.readTree(mockMvc.perform(get("/api/v1/live-exams/" + paper.exam() + "/leaderboard")
			.header("Authorization", "Bearer " + blank))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString()).get("rows");
		assertThat(rows).hasSize(2);
		assertThat(rows.get(0).get("score").asDouble()).isEqualTo(6.0);
		assertThat(rows.get(0).get("rank").asInt()).isEqualTo(1);
		assertThat(rows.get(1).get("score").asDouble()).isEqualTo(5.75);
		assertThat(rows.get(1).get("rank").asInt()).isEqualTo(2);
	}

	/** A written score has no position until that attempt is released (D4 and R13). */
	@Test
	void aWrittenPositionAppearsOnlyAfterTheAttemptIsReleased() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		UUID module = publishModule(staff, "Written position");
		UUID exam = writtenExam(staff, master, module);
		UUID studentId = seed("STUDENT");
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/roster").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"studentId":"%s"}
					""".formatted(studentId)))
			.andExpect(status().isCreated());
		String student = token(studentId);
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
		mockMvc.perform(multipart("/api/v1/live-exams/" + exam + "/answer-pdf")
			.file(new MockMultipartFile("file", "answer.pdf", "application/pdf", compressiblePdf()))
			.header("Authorization", "Bearer " + student))
			.andExpect(status().isNoContent());

		// Past the deadline (06:50) and not marked: the attempt is posted but not released, so there is no position.
		clock.set(Instant.parse("2026-10-20T06:55:00Z"));
		assertThat(absentPosition(resultBody(exam, studentId))).isTrue();

		UUID participant = new JdbcTemplate(dataSource).queryForObject(
				"select id from live_exam_participants where exam_id = ? and student_id = ?", UUID.class, exam, studentId);
		mockMvc.perform(multipart("/api/v1/live-exam-participants/" + participant + "/evaluation")
			.file(new MockMultipartFile("file", "mark.pdf", "application/pdf", compressiblePdf()))
			.param("marks", "6")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
		assertThat(absentPosition(resultBody(exam, studentId))).isTrue();

		mockMvc.perform(post("/api/v1/live-exam-participants/" + participant + "/publish").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
		JsonNode position = json.readTree(resultBody(exam, studentId)).get("position");
		assertThat(position.get("type").asText()).isEqualTo("BOARD");
		assertThat(position.get("rank").asInt()).isEqualTo(1);
		assertThat(position.get("of").asInt()).isEqualTo(1);
		assertThat(position.get("belowPass").asBoolean()).isFalse();
	}

	/** A null position is left out of the JSON, so it is either missing or null. */
	private boolean absentPosition(String body) throws Exception {
		JsonNode position = json.readTree(body).path("position");
		return position.isMissingNode() || position.isNull();
	}

	private String resultBody(UUID exam, UUID student) throws Exception {
		return mockMvc.perform(get("/api/v1/live-exams/" + exam + "/result").header("Authorization", "Bearer " + token(student)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
	}

	/** Answers the given questions in one save, with the given options (one each). */
	private void answer(Paper paper, String token, UUID[] questions, UUID[] options) throws Exception {
		List<String> items = new ArrayList<>();
		for (int index = 0; index < questions.length; index++) {
			items.add("{\"questionId\":\"%s\",\"optionIds\":[\"%s\"]}".formatted(questions[index], options[index]));
		}
		mockMvc.perform(put("/api/v1/live-exams/" + paper.exam() + "/answers").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"answers\":[" + String.join(",", items) + "]}"))
			.andExpect(status().isNoContent());
	}

	private static UUID wrongOption(Paper paper, UUID question) {
		return paper.wrong().get(List.of(paper.q1(), paper.q2(), paper.q3()).indexOf(question));
	}

	/** A published written exam with a paper, window 06:00 to 06:40 UTC, maximum 10, pass at 5. */
	private UUID writtenExam(String staff, String master, UUID module) throws Exception {
		MvcResult created = mockMvc
			.perform(post("/api/v1/live-exam-modules/" + module + "/exams").header("Authorization", "Bearer " + staff)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "examType":"WRITTEN","paperLanguage":"BN","titleEn":"Noon paper","titleBn":"খাতা",
						  "descriptionEn":"d","descriptionBn":"বি","rulesEn":"English rules","rulesBn":"নিয়ম",
						  "liveAt":"2026-10-20T12:00:00+06:00","durationMinutes":40,"passPercent":50,"maximum":10
						}
						"""))
			.andExpect(status().isCreated())
			.andReturn();
		UUID exam = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
		mockMvc.perform(multipart("/api/v1/live-exams/" + exam + "/question-pdf")
			.file(new MockMultipartFile("file", "paper.pdf", "application/pdf", tinyPdf()))
			.header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/final-save").header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
		return exam;
	}

	private static byte[] tinyPdf() {
		return "%PDF-1.1\n1 0 obj<</Type/Catalog>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
	}

	private static byte[] compressiblePdf() throws Exception {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			PDPage page = new PDPage();
			document.addPage(page);
			try (PDPageContentStream stream = new PDPageContentStream(document, page, AppendMode.OVERWRITE, false)) {
				stream.beginText();
				stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
				stream.newLineAtOffset(40, 700);
				stream.showText("A".repeat(400));
				stream.endText();
			}
			document.save(output);
			return output.toByteArray();
		}
	}
	/** Sits a paper, answers the given questions correctly, and submits at {@code submitAt}. */
	private void live(Paper paper, String token, String startAt, String submitAt, UUID... answered) throws Exception {
		clock.set(Instant.parse(startAt));
		mockMvc.perform(post("/api/v1/live-exams/" + paper.exam() + "/take").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
		// One save with every answer: a bulk save replaces the whole selection, so saving one question at a time keeps only the last.
		save(paper, token, answered);
		clock.set(Instant.parse(submitAt));
		mockMvc.perform(post("/api/v1/live-exams/" + paper.exam() + "/submit").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
	}

	/** Starts a sitting at the current clock time. */
	private void sit(Paper paper, String token) throws Exception {
		mockMvc.perform(post("/api/v1/live-exams/" + paper.exam() + "/take").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
	}

	private void save(Paper paper, String token, UUID... questions) throws Exception {
		List<String> items = new ArrayList<>();
		for (UUID question : questions) {
			items.add("{\"questionId\":\"%s\",\"optionIds\":[\"%s\"]}".formatted(question, correctOption(paper, question)));
		}
		mockMvc.perform(put("/api/v1/live-exams/" + paper.exam() + "/answers").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"answers\":[" + String.join(",", items) + "]}"))
			.andExpect(status().isNoContent());
	}

	private org.springframework.test.web.servlet.ResultActions submit(Paper paper, String token) throws Exception {
		return mockMvc.perform(post("/api/v1/live-exams/" + paper.exam() + "/submit").header("Authorization", "Bearer " + token));
	}

	private static UUID correctOption(Paper paper, UUID question) {
		int index = List.of(paper.q1(), paper.q2(), paper.q3()).indexOf(question);
		return paper.correct().get(index);
	}

	private UUID publishModule(String token, String title) throws Exception {
		MvcResult category = mockMvc.perform(post("/api/v1/live-exam-categories").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titleEn":"%s cat","titleBn":"বিভাগ"}
					""".formatted(title)))
			.andExpect(status().isCreated())
			.andReturn();
		UUID categoryId = UUID.fromString(json.readTree(category.getResponse().getContentAsString()).get("id").asText());
		MvcResult created = mockMvc.perform(post("/api/v1/live-exam-modules").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"categoryId":"%s","titleEn":"%s","titleBn":"মডিউল","descriptionEn":"d","descriptionBn":"বি"}
					""".formatted(categoryId, title)))
			.andExpect(status().isCreated())
			.andReturn();
		UUID module = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/final-save").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/publish").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		return module;
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
		return "board-" + UUID.randomUUID() + "@example.com";
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
