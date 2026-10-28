package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * NR-01 final check, case 3: a change published in the middle of a sitting (R16, R17, D15). The window is 10:00 to 12:00
 * Dhaka (04:00 to 06:00 UTC). A sitting lasts its own start plus the duration, fixed when it starts.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, MidSittingIT.ClockConfig.class })
class MidSittingIT extends PostgresIntegrationTest {

	private static final Instant BEFORE = Instant.parse("2026-10-20T04:00:00Z");

	private static final String LIVE_AT = "2026-10-20T10:00:00+06:00";

	private static final String WINDOW_END = "\"endAt\":\"2026-10-20T12:00:00+06:00\"";

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
		clock.set(BEFORE);
		fx = new AuthoringFixture(mockMvc, dataSource, passwords, json);
	}

	@Test
	void aChangePublishedMidSittingKeepsTheDeadlineShowsTheNewWordingAndScoresTheOpenPaperAtSubmit() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID category = fx.category(staff, "Mid sitting");
		UUID exam = fx.createStandalone(staff, category, "MCQ", "Mid sitting quiz", LIVE_AT, 40, WINDOW_END);
		UUID q1 = fx.question(staff, exam, AuthoringFixture.questionJson("Stem one", 2, "Because", false, "*A", "B"));
		fx.question(staff, exam, AuthoringFixture.questionJson("Stem two", 1, "Because", false, "*A", "B"));
		fx.question(staff, exam, AuthoringFixture.questionJson("Stem three", 1, "Because", false, "*A", "B"));
		fx.publishNew(master, exam);

		UUID s1 = fx.seed("STUDENT");
		UUID s2 = fx.seed("STUDENT");
		UUID s3 = fx.seed("STUDENT");
		grant(master, s1, exam);
		grant(master, s2, exam);
		grant(master, s3, exam);
		String token1 = fx.token(s1);
		String token2 = fx.token(s2);
		String token3 = fx.token(s3);

		// S2 starts and answers A everywhere before the change: the key is A on question 1.
		clock.set(Instant.parse("2026-10-20T04:05:00Z"));
		take(token2, exam).andExpect(jsonPath("$.deadlineAt").value("2026-10-20T10:45:00+06:00"));
		JsonNode before = fx.exam(staff, exam);
		answerAll(token2, exam, before, "A", "A", "A");
		clock.set(Instant.parse("2026-10-20T04:08:00Z"));
		submit(token2, exam);

		// S1 starts at 10:10: deadline 10:50.
		clock.set(Instant.parse("2026-10-20T04:10:00Z"));
		take(token1, exam).andExpect(jsonPath("$.deadlineAt").value("2026-10-20T10:50:00+06:00"));

		// The Master Admin stages a reworded question 1, its key moving from A to B, and a 60 minute duration at 10:20.
		clock.set(Instant.parse("2026-10-20T04:20:00Z"));
		mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"durationMinutes\":60}"))
			.andExpect(status().isOk());
		mockMvc.perform(patch("/api/v1/live-exams/" + exam + "/questions/" + q1).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"stem":"Reworded stem one","marks":2,"multipleAnswer":false,
					 "options":[{"text":"A","correct":false},{"text":"B","correct":true}]}
					"""))
			.andExpect(status().isOk());
		JsonNode change = bodyOf(mockMvc.perform(get("/api/v1/live-exams/" + exam + "/pending-changes")
			.header("Authorization", "Bearer " + master)));
		String revisionId = change.get("revisionId").asText();
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"revisionId\":\"%s\"}".formatted(revisionId)))
			.andExpect(status().isOk());

		// S1 reloads: the new wording shows, the key does not, and the deadline is still 10:50.
		clock.set(Instant.parse("2026-10-20T04:25:00Z"));
		JsonNode seen = bodyOf(mockMvc.perform(get("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + token1)));
		assertThat(seen.get("questions").get(0).get("stem").asText()).isEqualTo("Reworded stem one");
		for (JsonNode question : seen.get("questions")) {
			for (JsonNode option : question.get("options")) {
				assertThat(option.path("correct").asBoolean(false)).isFalse();
			}
		}
		assertThat(seen.get("sitting").get("deadlineAt").asText()).isEqualTo("2026-10-20T10:50:00+06:00");

		// S1 answers B on question 1 (the new key), and A on the others. Submitted at 10:30 on the clock.
		JsonNode now = fx.exam(staff, exam);
		answerAll(token1, exam, now, "B", "A", "A");
		clock.set(Instant.parse("2026-10-20T04:30:00Z"));
		submit(token1, exam);
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/result").header("Authorization", "Bearer " + token1))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(4));

		// S3 starts at 10:30, after the change: the new 60 minute duration gives 11:30.
		clock.set(Instant.parse("2026-10-20T04:30:00Z"));
		take(token3, exam).andExpect(jsonPath("$.deadlineAt").value("2026-10-20T11:30:00+06:00"));

		// S2 submitted before the change with A on question 1: the publish re-scored that paper, so question 1 is 0.
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/result").header("Authorization", "Bearer " + token2))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(2));
	}

	private void grant(String master, UUID student, UUID exam) throws Exception {
		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"studentId":"%s","scope":"EXAM","moduleId":null,"examId":"%s","reason":"Mid sitting grant"}
					""".formatted(student, exam)))
			.andExpect(status().isCreated());
	}

	private org.springframework.test.web.servlet.ResultActions take(String token, UUID exam) throws Exception {
		return mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isOk());
	}

	private void submit(String token, UUID exam) throws Exception {
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/submit").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
	}

	/** Answers the three questions in order with the option whose text is given. */
	private void answerAll(String token, UUID exam, JsonNode view, String... optionTexts) throws Exception {
		StringBuilder answers = new StringBuilder();
		int index = 0;
		for (JsonNode question : view.get("questions")) {
			String wanted = optionTexts[index++];
			for (JsonNode option : question.get("options")) {
				if (wanted.equals(option.get("text").asText())) {
					if (answers.length() > 0) {
						answers.append(',');
					}
					answers.append("{\"questionId\":\"").append(question.get("id").asText()).append("\",\"optionId\":\"")
						.append(option.get("id").asText()).append("\"}");
				}
			}
		}
		mockMvc.perform(put("/api/v1/live-exams/" + exam + "/answers").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"answers\":[" + answers + "]}"))
			.andExpect(status().isNoContent());
	}

	private JsonNode bodyOf(org.springframework.test.web.servlet.ResultActions request) throws Exception {
		MvcResult result = request.andExpect(status().isOk()).andReturn();
		return json.readTree(result.getResponse().getContentAsString());
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
