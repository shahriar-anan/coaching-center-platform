package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * NR-01 final check, case 9 (E28, A4): a change to the marks, negative marks, pass percent or multi-answer flag of a
 * published MCQ exam re-scores the papers already submitted. Each change is checked on its own exam, so the expected scores
 * come from one change only. The change list counts every submitted paper, and the change log counts the scores that moved.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, RescoreVariantsIT.ClockConfig.class })
class RescoreVariantsIT extends PostgresIntegrationTest {

	private static final Instant TEN_AM = Instant.parse("2026-10-20T04:00:00Z");

	/** Inside the window of the exams below (12:00 to 12:40 in Dhaka). */
	private static final Instant IN_WINDOW = Instant.parse("2026-10-20T06:05:00Z");

	/** After the window, when students see the full board. */
	private static final Instant AFTER_WINDOW = Instant.parse("2026-10-20T06:50:00Z");

	private static final String NOON = "2026-10-20T12:00:00+06:00";

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
		clock.set(TEN_AM);
		fx = new AuthoringFixture(mockMvc, dataSource, passwords, json);
	}

	@Test
	void aChangeToTheMarksOfAQuestionChangesTheScoresAndFollowsThroughToPassing() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		Quiz quiz = publishedQuiz(staff, master, "Marks");
		Paper p1 = sit(quiz, option(quiz.single(), 0), option(quiz.multi(), 0), option(quiz.multi(), 1));
		Paper p2 = sit(quiz, option(quiz.single(), 1), option(quiz.multi(), 0), option(quiz.multi(), 1));
		Paper p3 = sit(quiz, option(quiz.single(), 0), option(quiz.multi(), 2));
		Paper p4 = sit(quiz);
		assertThat(score(p1)).isEqualByComparingTo("2");
		assertThat(score(p2)).isEqualByComparingTo("1");
		assertThat(score(p3)).isEqualByComparingTo("1");
		assertThat(passed(p2)).isTrue();

		// The single question is worth 3 marks: the maximum rises from 2 to 4, so a score of 1 now falls short.
		stageQuestion(staff, quiz.exam(), quiz.single(), AuthoringFixture.questionJson("Single", 3, "Because", false, "*A", "B", "C"));
		JsonNode changes = pending(staff, quiz.exam());
		assertThat(changes.get("impact").get("papersToRescore").asInt()).isEqualTo(4);
		publish(master, quiz.exam(), changes);

		assertThat(score(p1)).isEqualByComparingTo("4");
		assertThat(score(p2)).isEqualByComparingTo("1");
		assertThat(score(p3)).isEqualByComparingTo("3");
		assertThat(score(p4)).isEqualByComparingTo("0");
		assertThat(passed(p1)).isTrue();
		assertThat(passed(p2)).as("1 of 4 is below the pass mark now").isFalse();
		assertThat(passed(p3)).isTrue();
		assertThat(passed(p4)).isFalse();
		assertThat(rescored(quiz.exam())).as("only the papers whose score moved").isEqualTo(2);
	}

	@Test
	void aNegativeMarkValueMakesWrongSelectionsLoseMarksAndScoresCanGoNegative() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		Quiz quiz = publishedQuiz(staff, master, "Negative");
		Paper p1 = sit(quiz, option(quiz.single(), 0), option(quiz.multi(), 0), option(quiz.multi(), 1));
		Paper p2 = sit(quiz, option(quiz.single(), 1), option(quiz.multi(), 0), option(quiz.multi(), 1));
		Paper p3 = sit(quiz, option(quiz.single(), 0), option(quiz.multi(), 2));
		Paper p4 = sit(quiz, option(quiz.single(), 1), option(quiz.multi(), 2));
		Paper p5 = sit(quiz);
		// No negative marking yet: a wrong selection scores 0.
		assertThat(score(p2)).isEqualByComparingTo("1");
		assertThat(score(p4)).isEqualByComparingTo("0");

		stage(staff, quiz.exam(), "{\"negativeMarks\":0.5}");
		JsonNode changes = pending(staff, quiz.exam());
		assertThat(changes.get("impact").get("papersToRescore").asInt()).isEqualTo(5);
		publish(master, quiz.exam(), changes);

		assertThat(score(p1)).as("no wrong selection, unchanged").isEqualByComparingTo("2");
		assertThat(score(p2)).isEqualByComparingTo("0.5");
		assertThat(score(p3)).isEqualByComparingTo("0.5");
		assertThat(score(p4)).isEqualByComparingTo("-1");
		assertThat(score(p5)).as("a blank paper scores 0").isEqualByComparingTo("0");
		assertThat(passed(p4)).isFalse();
		assertThat(rescored(quiz.exam())).isEqualTo(3);
	}

	@Test
	void aHigherPassPercentFlipsThePassResultAndDropsThePaperFromTheBoardButKeepsTheScores() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		Quiz quiz = publishedQuiz(staff, master, "Pass");
		Paper p1 = sit(quiz, option(quiz.single(), 0), option(quiz.multi(), 0), option(quiz.multi(), 1));
		Paper p2 = sit(quiz, option(quiz.single(), 1), option(quiz.multi(), 0), option(quiz.multi(), 1));
		Paper p3 = sit(quiz, option(quiz.single(), 1), option(quiz.multi(), 2));
		assertThat(passed(p1)).isTrue();
		assertThat(passed(p2)).as("1 of 2 passes at 50 percent").isTrue();
		assertThat(passed(p3)).isFalse();

		clock.set(AFTER_WINDOW);
		assertThat(boardRows(p1.token(), quiz.exam())).isEqualTo(2);

		stage(staff, quiz.exam(), "{\"passPercent\":70}");
		JsonNode changes = pending(staff, quiz.exam());
		assertThat(changes.get("impact").get("papersToRescore").asInt()).isEqualTo(3);
		publish(master, quiz.exam(), changes);

		assertThat(score(p1)).isEqualByComparingTo("2");
		assertThat(score(p2)).isEqualByComparingTo("1");
		assertThat(score(p3)).isEqualByComparingTo("0");
		assertThat(passed(p1)).isTrue();
		assertThat(passed(p2)).as("1 of 2 is below 70 percent now").isFalse();
		assertThat(passed(p3)).isFalse();
		assertThat(rescored(quiz.exam())).as("the scores did not move").isZero();
		assertThat(boardRows(p1.token(), quiz.exam())).as("only the paper that still passes is on the board").isEqualTo(1);
	}

	@Test
	void turningMultiAnswerOffMakesTheOneRemainingCorrectOptionScoreFullMarks() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		Quiz quiz = publishedQuiz(staff, master, "Multi");
		Paper p1 = sit(quiz, option(quiz.single(), 0), option(quiz.multi(), 0), option(quiz.multi(), 1));
		Paper p2 = sit(quiz, option(quiz.single(), 0), option(quiz.multi(), 0));
		Paper p3 = sit(quiz, option(quiz.single(), 1), option(quiz.multi(), 0));
		Paper p4 = sit(quiz, option(quiz.single(), 0));
		assertThat(score(p1)).isEqualByComparingTo("2");
		assertThat(score(p2)).as("A alone is not the exact set of two").isEqualByComparingTo("1");
		assertThat(score(p3)).isEqualByComparingTo("0");

		// Two correct options become one: B stops being correct, and the question is single-answer.
		stageQuestion(staff, quiz.exam(), quiz.multi(), AuthoringFixture.questionJson("Multi", 1, "Because", false, "*A", "B", "C"));
		JsonNode changes = pending(staff, quiz.exam());
		assertThat(changes.get("impact").get("papersToRescore").asInt()).isEqualTo(4);
		publish(master, quiz.exam(), changes);

		assertThat(score(p1)).as("chose A and B, which is no longer the key").isEqualByComparingTo("1");
		assertThat(score(p2)).as("chose only the remaining correct option: full marks").isEqualByComparingTo("2");
		assertThat(score(p3)).isEqualByComparingTo("1");
		assertThat(score(p4)).isEqualByComparingTo("1");
		assertThat(passed(p2)).isTrue();
		assertThat(rescored(quiz.exam())).isEqualTo(3);
	}

	/** A published MCQ exam in a new package: a single-answer question (A correct, marks 1) and a multi-answer one (A and B). */
	private Quiz publishedQuiz(String staff, String master, String title) throws Exception {
		UUID module = fx.module(staff, title);
		UUID exam = fx.createExam(staff, module, "MCQ", title, NOON, 40, null);
		UUID single = fx.question(staff, exam, AuthoringFixture.questionJson("Single", 1, "Because", false, "*A", "B", "C"));
		UUID multi = fx.question(staff, exam, AuthoringFixture.questionJson("Multi", 1, "Because", true, "*A", "*B", "C"));
		fx.publishNew(master, exam);
		return new Quiz(module, exam, single, multi);
	}

	/** A student sits the exam inside the window, answers with the given options and submits. */
	private Paper sit(Quiz quiz, UUID... chosen) throws Exception {
		UUID studentId = fx.seed("STUDENT");
		String student = fx.token(studentId);
		fx.grantModule(quiz.module(), studentId);
		clock.set(IN_WINDOW);
		mockMvc.perform(post("/api/v1/live-exams/" + quiz.exam() + "/take").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isOk());
		UUID participant = fx.jdbc().queryForObject(
				"select id from live_exam_participants where exam_id = ? and student_id = ?", UUID.class, quiz.exam(), studentId);
		for (UUID option : chosen) {
			fx.jdbc().update("insert into live_exam_answers (participant_id, question_id, option_id) values (?, "
					+ "(select question_id from live_exam_options where id = ?), ?)", participant, option, option);
		}
		mockMvc.perform(post("/api/v1/live-exams/" + quiz.exam() + "/submit").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk());
		return new Paper(participant, student);
	}

	private UUID option(UUID question, int position) {
		return fx.jdbc().queryForObject("select id from live_exam_options where question_id = ? and position = ? and deleted_at is null",
				UUID.class, question, position);
	}

	private void stage(String staff, UUID exam, String body) throws Exception {
		mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk());
	}

	private void stageQuestion(String staff, UUID exam, UUID question, String body) throws Exception {
		mockMvc.perform(patch("/api/v1/live-exams/" + exam + "/questions/" + question).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk());
	}

	private void publish(String master, UUID exam, JsonNode changes) throws Exception {
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"revisionId\":\"" + changes.get("revisionId").asText() + "\"}"))
			.andExpect(status().isOk());
	}

	private JsonNode pending(String token, UUID exam) throws Exception {
		return json.readTree(mockMvc.perform(get("/api/v1/live-exams/" + exam + "/pending-changes")
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString());
	}

	private int boardRows(String token, UUID exam) throws Exception {
		return json.readTree(mockMvc.perform(get("/api/v1/live-exams/" + exam + "/leaderboard")
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString()).get("rows").size();
	}

	private BigDecimal score(Paper paper) {
		return fx.jdbc().queryForObject("select score from live_exam_participants where id = ?", BigDecimal.class, paper.participant());
	}

	private Boolean passed(Paper paper) {
		return fx.jdbc().queryForObject("select passed from live_exam_participants where id = ?", Boolean.class, paper.participant());
	}

	private int rescored(UUID exam) {
		return fx.jdbc().queryForObject(
				"select rescored from live_exam_change_log where exam_id = ? order by published_at desc limit 1", Integer.class, exam);
	}

	private record Quiz(UUID module, UUID exam, UUID single, UUID multi) {
	}

	private record Paper(UUID participant, String token) {
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
