package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import javax.sql.DataSource;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
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
import org.springframework.test.web.servlet.ResultActions;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * NR-01 final check, case 6: practice exams (C9, D5, D6). A practice exam has no window and is open from publish. Its
 * leaderboard is built from each student's first attempt; retakes get a relative position. The written practice paper is
 * readable by its instructor only once the participant's deadline has passed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, PracticeExamIT.ClockConfig.class })
class PracticeExamIT extends PostgresIntegrationTest {

	private static final Instant PUBLISHED = Instant.parse("2026-10-20T04:00:00Z");

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

	private AuthoringFixture fx;

	@BeforeEach
	void setUp() {
		clock.set(PUBLISHED);
		fx = new AuthoringFixture(mockMvc, dataSource, passwords, json);
	}

	@Test
	void theBoardHoldsFirstAttemptsAndTheRetakeGetsARelativePosition() throws Exception {
		Practice practice = publishedPractice("Board", "MCQ");
		JsonNode view = fx.exam(practice.staff(), practice.exam());
		// Five questions of two marks each: a maximum of 10. Pass mark 5.
		clock.set(Instant.parse("2026-10-20T04:10:00Z"));
		sit(practice, practice.s1(), "A", "A", "A", "A", "B");
		clock.set(Instant.parse("2026-10-20T04:15:00Z"));
		sit(practice, practice.s2(), "A", "A", "A", "B", "B");
		clock.set(Instant.parse("2026-10-20T04:20:00Z"));
		sit(practice, practice.s3(), "A", "B", "B", "B", "B");
		assertThat(view.get("questions")).hasSize(5);

		// S1 retakes at 04:25 and scores the full 10.
		clock.set(Instant.parse("2026-10-20T04:25:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + practice.exam() + "/retake").header("Authorization", "Bearer " + practice.s1Token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.attemptKind").value("RETAKE"))
			.andExpect(jsonPath("$.attemptNo").value(2));
		answerAll(practice, practice.s1Token(), "A", "A", "A", "A", "A");
		submit(practice.exam(), practice.s1Token());

		// 1. The board is each student's first attempt: S1 8, S2 6. S3 is below the pass mark and is not on it.
		clock.set(Instant.parse("2026-10-20T04:30:00Z"));
		JsonNode board = bodyOf(mockMvc.perform(get("/api/v1/live-exams/" + practice.exam() + "/leaderboard")
			.header("Authorization", "Bearer " + practice.s1Token())));
		assertThat(board.get("rows")).hasSize(2);
		assertThat(board.get("rows").get(0).get("score").asDouble()).isEqualTo(8.0);
		assertThat(board.get("rows").get(1).get("score").asDouble()).isEqualTo(6.0);

		// 2. Positions. S1's retake (10) would rank 1 of 2 among board attempts; S2's first attempt is BOARD 2 of 2;
		// S3's first attempt is below pass: no rank.
		mockMvc.perform(get("/api/v1/live-exams/" + practice.exam() + "/result").header("Authorization", "Bearer " + practice.s1Token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(10))
			.andExpect(jsonPath("$.position.type").value("RELATIVE"))
			.andExpect(jsonPath("$.position.rank").value(1))
			.andExpect(jsonPath("$.position.of").value(2));
		mockMvc.perform(get("/api/v1/live-exams/" + practice.exam() + "/result").param("attempt", "1")
			.header("Authorization", "Bearer " + practice.s2Token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.position.type").value("BOARD"))
			.andExpect(jsonPath("$.position.rank").value(2))
			.andExpect(jsonPath("$.position.of").value(2));
		mockMvc.perform(get("/api/v1/live-exams/" + practice.exam() + "/result").header("Authorization", "Bearer " + practice.s3Token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.position.belowPass").value(true))
			.andExpect(jsonPath("$.position.rank").doesNotExist());
	}

	@Test
	void theBoardIsOpenToStudentsAtOnceWithNoWindowToWaitFor() throws Exception {
		Practice practice = publishedPractice("Open board", "MCQ");
		clock.set(Instant.parse("2026-10-20T04:10:00Z"));
		sit(practice, practice.s2(), "A", "A", "A", "A", "A");
		// Right after publish, with no window, the full board is open to a student.
		JsonNode board = bodyOf(mockMvc.perform(get("/api/v1/live-exams/" + practice.exam() + "/leaderboard")
			.header("Authorization", "Bearer " + practice.s1Token())));
		assertThat(board.get("rows")).hasSize(1);
		assertThat(board.get("rows").get(0).get("score").asDouble()).isEqualTo(10.0);
	}

	@Test
	void theAssignedInstructorReadsAWrittenPracticePaperOnlyAfterTheParticipantsDeadline() throws Exception {
		Practice practice = publishedPractice("Written practice", "WRITTEN");
		Marker instructor = instructor(practice.master(), "Practice marker");
		mockMvc.perform(post("/api/v1/live-exams/" + practice.exam() + "/instructor").header("Authorization", "Bearer " + practice.master())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"instructorId":"%s"}
					""".formatted(instructor.id())))
			.andExpect(status().is2xxSuccessful());

		clock.set(Instant.parse("2026-10-20T04:35:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + practice.exam() + "/take").header("Authorization", "Bearer " + practice.s1Token())
			.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.attemptKind").value("PRACTICE"))
			.andExpect(jsonPath("$.deadlineAt").value("2026-10-20T11:15:00+06:00"));
		UUID participant = participant(practice.exam(), practice.s1());

		clock.set(Instant.parse("2026-10-20T04:40:00Z"));
		mockMvc.perform(get("/api/v1/live-exam-participants/" + participant + "/question-pdf")
			.header("Authorization", "Bearer " + instructor.token()))
			.andExpect(status().isNotFound());

		clock.set(Instant.parse("2026-10-20T05:16:00Z"));
		mockMvc.perform(get("/api/v1/live-exam-participants/" + participant + "/question-pdf")
			.header("Authorization", "Bearer " + instructor.token()))
			.andExpect(status().isOk());
	}

	// ---- set-up and helpers ---------------------------------------------------------------------------------------

	/**
	 * A published PRACTICE exam with no dates, in a package that S1, S2 and S3 hold. An MCQ has five two-mark questions
	 * (correct option A); a written one has a paper. The mode is set in SQL before publish, as AttemptsIT does.
	 */
	private Practice publishedPractice(String title, String type) throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID category = fx.category(staff, title + " category");
		UUID module = fx.moduleIn(staff, category, title + " package");
		UUID exam = fx.createExam(staff, module, type, title + " exam", null, 40, null);
		if ("MCQ".equals(type)) {
			for (int question = 1; question <= 5; question++) {
				fx.question(staff, exam, AuthoringFixture.questionJson("Stem " + question, 2, "Because", false, "*A", "B"));
			}
		}
		else {
			fx.uploadPdf(staff, exam);
		}
		new JdbcTemplate(dataSource).update("update live_exams set mode = 'PRACTICE' where id = ?", exam);
		fx.publishNew(master, exam);
		UUID s1 = fx.seed("STUDENT");
		UUID s2 = fx.seed("STUDENT");
		UUID s3 = fx.seed("STUDENT");
		fx.grantModule(module, s1);
		fx.grantModule(module, s2);
		fx.grantModule(module, s3);
		return new Practice(staff, master, exam, s1, fx.token(s1), s2, fx.token(s2), s3, fx.token(s3));
	}

	/** Takes the MCQ practice exam and answers the five questions with the given option texts, then submits. */
	private void sit(Practice practice, UUID student, String... answers) throws Exception {
		String token = student.equals(practice.s1()) ? practice.s1Token()
				: student.equals(practice.s2()) ? practice.s2Token() : practice.s3Token();
		mockMvc.perform(post("/api/v1/live-exams/" + practice.exam() + "/take").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isOk());
		answerAll(practice, token, answers);
		submit(practice.exam(), token);
	}

	private void answerAll(Practice practice, String token, String... optionTexts) throws Exception {
		JsonNode view = fx.exam(practice.staff(), practice.exam());
		List<String> answers = new ArrayList<>();
		int index = 0;
		for (JsonNode question : view.get("questions")) {
			String wanted = optionTexts[index++];
			for (JsonNode option : question.get("options")) {
				if (wanted.equals(option.get("text").asText())) {
					answers.add("{\"questionId\":\"" + question.get("id").asText() + "\",\"optionId\":\""
							+ option.get("id").asText() + "\"}");
				}
			}
		}
		mockMvc.perform(put("/api/v1/live-exams/" + practice.exam() + "/answers").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"answers\":[" + String.join(",", answers) + "]}"))
			.andExpect(status().isNoContent());
	}

	private void submit(UUID exam, String token) throws Exception {
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/submit").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
	}

	private UUID participant(UUID exam, UUID student) {
		return new JdbcTemplate(dataSource).queryForObject(
				"select id from live_exam_participants where exam_id = ? and student_id = ? and deleted_at is null",
				UUID.class, exam, student);
	}

	private JsonNode bodyOf(ResultActions request) throws Exception {
		MvcResult result = request.andExpect(status().isOk()).andReturn();
		return json.readTree(result.getResponse().getContentAsString());
	}

	/** An instructor, created and activated the way the instructor tests do it, with a token. */
	private Marker instructor(String master, String name) throws Exception {
		String phone = "+8801" + ThreadLocalRandom.current().nextInt(100000000, 999999999);
		String email = "practice-" + UUID.randomUUID() + "@example.com";
		MvcResult created = mockMvc.perform(post("/api/v1/instructors").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"%s","phone":"%s","email":"%s"}
					""".formatted(name, phone, email)))
			.andExpect(status().isCreated())
			.andReturn();
		JsonNode body = json.readTree(created.getResponse().getContentAsString());
		mockMvc.perform(post("/api/v1/auth/activate").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"phone":"%s","code":"%s","password":"password1"}
					""".formatted(phone, body.get("activationCode").asText())))
			.andExpect(status().isNoContent());
		return new Marker(UUID.fromString(body.get("id").asText()), tokenFor(phone));
	}

	private String tokenFor(String phone) throws Exception {
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

	private record Practice(String staff, String master, UUID exam, UUID s1, String s1Token, UUID s2, String s2Token,
			UUID s3, String s3Token) {
	}

	private record Marker(UUID id, String token) {
	}

	@TestConfiguration
	static class ClockConfig {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(Instant.parse("2026-10-20T04:00:00Z"));
		}

	}

}
