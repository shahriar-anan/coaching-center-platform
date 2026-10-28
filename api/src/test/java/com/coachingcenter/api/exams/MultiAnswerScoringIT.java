package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * Multi-answer MCQ scoring and the answer payloads (NR-01 D3, R8 and R9). The paper has two questions of two marks each:
 * Q1 may take several options (its options A and B are correct, C is wrong), Q2 takes one (X correct, Y wrong). The
 * negative mark per exam is 0.25. The window is 06:00 to 06:40 UTC and the sittings are taken at 06:10.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, MultiAnswerScoringIT.ClockConfig.class })
class MultiAnswerScoringIT extends PostgresIntegrationTest {

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
	void aMultiAnswerExactMatchEarnsFullMarksAndAWrongSelectionLosesTheNegativeMark() throws Exception {
		Paper paper = paper("Scoring", false);
		String student = paper.studentToken();
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		take(paper, student);
		save(paper, student, "[{\"questionId\":\"%s\",\"optionIds\":[\"%s\",\"%s\"]},{\"questionId\":\"%s\",\"optionIds\":[\"%s\"]}]"
			.formatted(paper.q1(), paper.a(), paper.b(), paper.q2(), paper.y()));
		clock.set(Instant.parse("2026-10-20T06:20:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + paper.exam() + "/submit").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			// Q1 exact (+2), Q2 wrong (-0.25).
			.andExpect(jsonPath("$.score").value(1.75));
	}

	@Test
	void aSingleAnswerQuestionRejectsTwoOptionsButAMultiOneTakesThem() throws Exception {
		Paper paper = paper("Cap", false);
		String student = paper.studentToken();
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		take(paper, student);
		save(paper, student, "[{\"questionId\":\"%s\",\"optionIds\":[\"%s\",\"%s\"]}]".formatted(paper.q2(), paper.x(), paper.y()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		save(paper, student, "[{\"questionId\":\"%s\",\"optionIds\":[\"%s\",\"%s\"]}]".formatted(paper.q1(), paper.a(), paper.b()))
			.andExpect(status().isNoContent());
	}

	@Test
	void theOldSingleOptionPayloadIsStillAccepted() throws Exception {
		Paper paper = paper("Legacy", false);
		String student = paper.studentToken();
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		take(paper, student);
		save(paper, student, "[{\"questionId\":\"%s\",\"optionId\":\"%s\"}]".formatted(paper.q2(), paper.x()))
			.andDo(result -> {
				if (result.getResponse().getStatus() == 400) {
					throw new AssertionError("legacy payload refused: " + result.getResponse().getContentAsString());
				}
			})
			.andExpect(status().isNoContent());
	}

	@Test
	void aLockedSingleAnswerIsFinalAtItsFirstChoiceAndResendingItIsFine() throws Exception {
		Paper paper = paper("Locked single", true);
		String student = paper.studentToken();
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		take(paper, student);
		one(paper, student, paper.q2(), "{\"optionIds\":[\"%s\"]}".formatted(paper.x()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.confirmed").value(true));
		one(paper, student, paper.q2(), "{\"optionIds\":[\"%s\"]}".formatted(paper.x())).andExpect(status().isOk());
		one(paper, student, paper.q2(), "{\"optionIds\":[\"%s\"]}".formatted(paper.y()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ANSWER_LOCKED"));
		one(paper, student, paper.q2(), "{\"optionIds\":[]}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ANSWER_LOCKED"));
	}

	@Test
	void aMultiAnswerIsChangeableUntilTheStudentConfirmsIt() throws Exception {
		Paper paper = paper("Confirm", true);
		String student = paper.studentToken();
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		take(paper, student);
		one(paper, student, paper.q1(), "{\"optionIds\":[\"%s\"]}".formatted(paper.a()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.confirmed").value(false));
		one(paper, student, paper.q1(), "{\"optionIds\":[\"%s\",\"%s\"],\"confirm\":true}".formatted(paper.a(), paper.b()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.confirmed").value(true));
		one(paper, student, paper.q1(), "{\"optionIds\":[\"%s\"]}".formatted(paper.c()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ANSWER_LOCKED"));
		assertThat(new JdbcTemplate(dataSource).queryForObject(
				"select count(*) from live_exam_answer_confirmations where participant_id in"
						+ " (select id from live_exam_participants where exam_id = ?)",
				Integer.class, paper.exam())).isEqualTo(1);
	}

	/** One student, one exam, two questions. */
	private record Paper(UUID exam, UUID q1, UUID q2, UUID a, UUID b, UUID c, UUID x, UUID y, UUID student, String studentToken) {
	}

	/** Builds the paper, sets the scoring switches by SQL (the authoring API for them is A's slice), and puts one student on it. */
	private Paper paper(String title, boolean locked) throws Exception {
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
		JsonNode first = question(staff, exam, "Pick two", "A", "true", "B", "false", "C", "false");
		JsonNode second = question(staff, exam, "Pick one", "X", "true", "Y", "false");
		UUID q1 = UUID.fromString(first.get("id").asText());
		UUID q2 = UUID.fromString(second.get("id").asText());
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		// Until NR-01 A's multi-answer authoring is merged, the question API rejects a second correct option, so the second
		// correct option is set here by SQL. Switch to the API after A merges.
		jdbc.update("update live_exam_options set correct = true where id = ?", option(first, "B"));
		jdbc.update("update live_exam_questions set multiple_answer = true where id = ?", q1);
		jdbc.update("update live_exams set negative_marks = 0.25 where id = ?", exam);
		if (locked) {
			jdbc.update("update live_exams set answers_changeable = false where id = ?", exam);
		}
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/final-save").header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
		UUID student = seed("STUDENT");
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/roster").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"studentId":"%s"}
					""".formatted(student)))
			.andExpect(status().isCreated());
		return new Paper(exam, q1, q2, option(first, "A"), option(first, "B"), option(first, "C"), option(second, "X"),
				option(second, "Y"), student, token(student));
	}

	/** A question whose options are given as {text, correct} pairs. */
	private JsonNode question(String staff, UUID exam, String stem, String... options) throws Exception {
		List<String> items = new ArrayList<>();
		for (int index = 0; index < options.length; index += 2) {
			items.add("{\"text\":\"%s\",\"correct\":%s}".formatted(options[index], options[index + 1]));
		}
		MvcResult result = mockMvc.perform(post("/api/v1/live-exams/" + exam + "/questions")
			.header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"stem":"%s","marks":2,"explanation":"Because","options":[%s]}
					""".formatted(stem, String.join(",", items))))
			.andExpect(status().isCreated())
			.andReturn();
		return json.readTree(result.getResponse().getContentAsString());
	}
	private UUID option(JsonNode question, String text) {
		for (JsonNode option : question.get("options")) {
			if (text.equals(option.get("text").asText())) {
				return UUID.fromString(option.get("id").asText());
			}
		}
		throw new AssertionError("no option " + text);
	}

	private void take(Paper paper, String student) throws Exception {
		mockMvc.perform(post("/api/v1/live-exams/" + paper.exam() + "/take").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
	}

	private ResultActions save(Paper paper, String student, String answers) throws Exception {
		return mockMvc.perform(put("/api/v1/live-exams/" + paper.exam() + "/answers").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"answers\":" + answers + "}"));
	}

	private ResultActions one(Paper paper, String student, UUID questionId, String body) throws Exception {
		return mockMvc.perform(put("/api/v1/live-exams/" + paper.exam() + "/answers/" + questionId)
			.header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
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
		return "multi-" + UUID.randomUUID() + "@example.com";
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
