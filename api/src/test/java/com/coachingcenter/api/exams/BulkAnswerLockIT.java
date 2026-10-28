package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * NR-01 final check, case 12: bulk answer save when answers cannot be changed (R9, D3). The exam has "answers can be
 * changed" off. A single-answer question is final at the first choice; a multi-answer question is held in the same
 * way. The window is 12:00 to 12:40 Dhaka (06:00 to 06:40 UTC).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, BulkAnswerLockIT.ClockConfig.class })
class BulkAnswerLockIT extends PostgresIntegrationTest {

	private static final Instant BEFORE = Instant.parse("2026-10-20T04:00:00Z");

	private static final Instant SITTING = Instant.parse("2026-10-20T06:10:00Z");

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
	void aFinalBulkAnswerCannotChangeButARepeatOrANewQuestionIsSaved() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID category = fx.category(staff, "Bulk lock");
		UUID exam = fx.createStandalone(staff, category, "MCQ", "Locked answers", AuthoringFixture.LIVE_AT, 40,
				"\"answersChangeable\":false");
		UUID single = fx.question(staff, exam, AuthoringFixture.questionJson("Single", 1, "Because", false, "*A", "B"));
		UUID multi = fx.question(staff, exam,
				AuthoringFixture.questionJson("Multi", 2, "Because", true, "*A", "*B", "C"));
		UUID third = fx.question(staff, exam, AuthoringFixture.questionJson("Third", 1, "Because", false, "*A", "B"));
		fx.publishNew(master, exam);
		UUID student = fx.seed("STUDENT");
		grant(master, student, exam);
		String token = fx.token(student);
		JsonNode view = fx.exam(staff, exam);
		String singleA = optionId(view, single, "A");
		String singleB = optionId(view, single, "B");
		String multiA = optionId(view, multi, "A");
		String multiB = optionId(view, multi, "B");
		String thirdA = optionId(view, third, "A");

		clock.set(SITTING);
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isOk());

		// 1. The first bulk save answers the single and the multi-answer question. Changing either is refused.
		bulk(token, exam, answer(single, singleA), answer(multi, multiA, multiB));
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		UUID participant = participant(exam, student);
		assertThat(optionsOf(jdbc, participant, single)).containsExactly(UUID.fromString(singleA));
		assertThat(optionsOf(jdbc, participant, multi)).containsExactlyInAnyOrder(UUID.fromString(multiA),
				UUID.fromString(multiB));

		mockMvc.perform(put("/api/v1/live-exams/" + exam + "/answers").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"answers\":[" + answer(single, singleB) + "," + answer(multi, multiA, multiB) + "]}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ANSWER_LOCKED"));
		mockMvc.perform(put("/api/v1/live-exams/" + exam + "/answers").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"answers\":[" + answer(single, singleA) + "," + answer(multi, multiA) + "]}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ANSWER_LOCKED"));
		assertThat(optionsOf(jdbc, participant, single)).containsExactly(UUID.fromString(singleA));
		assertThat(optionsOf(jdbc, participant, multi)).containsExactlyInAnyOrder(UUID.fromString(multiA),
				UUID.fromString(multiB));

		// 2. A bulk save that repeats the same answers is accepted.
		bulk(token, exam, answer(single, singleA), answer(multi, multiA, multiB));

		// 3. A bulk save that adds the third question, unanswered until now, keeps the first two and saves the third.
		bulk(token, exam, answer(single, singleA), answer(multi, multiA, multiB), answer(third, thirdA));
		assertThat(optionsOf(jdbc, participant, single)).containsExactly(UUID.fromString(singleA));
		assertThat(optionsOf(jdbc, participant, multi)).containsExactlyInAnyOrder(UUID.fromString(multiA),
				UUID.fromString(multiB));
		assertThat(optionsOf(jdbc, participant, third)).containsExactly(UUID.fromString(thirdA));
	}

	private void grant(String master, UUID student, UUID exam) throws Exception {
		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"studentId":"%s","scope":"EXAM","moduleId":null,"examId":"%s","reason":"Bulk answers grant"}
					""".formatted(student, exam)))
			.andExpect(status().isCreated());
	}

	private void bulk(String token, UUID exam, String... answers) throws Exception {
		mockMvc.perform(put("/api/v1/live-exams/" + exam + "/answers").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"answers\":[" + String.join(",", answers) + "]}"))
			.andExpect(status().isNoContent());
	}

	private static String answer(UUID question, String... optionIds) {
		StringBuilder ids = new StringBuilder();
		for (String id : optionIds) {
			if (ids.length() > 0) {
				ids.append(',');
			}
			ids.append('"').append(id).append('"');
		}
		return "{\"questionId\":\"" + question + "\",\"optionIds\":[" + ids + "]}";
	}

	private static String optionId(JsonNode view, UUID question, String text) {
		for (JsonNode row : view.get("questions")) {
			if (question.toString().equals(row.get("id").asText())) {
				for (JsonNode option : row.get("options")) {
					if (text.equals(option.get("text").asText())) {
						return option.get("id").asText();
					}
				}
			}
		}
		throw new IllegalStateException("No option " + text + " on " + question);
	}

	private UUID participant(UUID exam, UUID student) {
		return new JdbcTemplate(dataSource).queryForObject(
				"select id from live_exam_participants where exam_id = ? and student_id = ? and deleted_at is null",
				UUID.class, exam, student);
	}

	/** The option ids saved for one question of a participant. */
	private static java.util.List<UUID> optionsOf(JdbcTemplate jdbc, UUID participant, UUID question) {
		return jdbc.queryForList(
				"select option_id from live_exam_answers where participant_id = ? and question_id = ?", UUID.class,
				participant, question);
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
