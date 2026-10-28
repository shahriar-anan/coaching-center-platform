package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** A2: multi-answer questions, their key rules, and the structure lock once a student has started (R8, R16, C14). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, ExamQuestionMultiAnswerIT.ClockConfig.class })
class ExamQuestionMultiAnswerIT extends PostgresIntegrationTest {

	private static final Instant TEN_AM = Instant.parse("2026-10-20T04:00:00Z");

	/** Inside the window of the exams below (12:00 to 12:40 in Dhaka). */
	private static final Instant IN_WINDOW = Instant.parse("2026-10-20T06:05:00Z");

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
	void aSingleAnswerQuestionHasExactlyOneCorrectOptionAndAtLeastTwoOptions() throws Exception {
		String staff = fx.staff();
		UUID module = fx.module(staff, "Single key");
		UUID exam = fx.createExam(staff, module, "MCQ", "Single", NOON, 40, null);

		postQuestion(staff, exam, AuthoringFixture.questionJson("Two keys", 1, null, false, "*A", "*B", "C"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		postQuestion(staff, exam, AuthoringFixture.questionJson("No key", 1, null, false, "A", "B"))
			.andExpect(status().isBadRequest());
		postQuestion(staff, exam, AuthoringFixture.questionJson("One option", 1, null, false, "*A"))
			.andExpect(status().isBadRequest());
		postQuestion(staff, exam, AuthoringFixture.questionJson("Fine", 1, null, false, "*A", "B", "C"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.multipleAnswer").value(false));
	}

	@Test
	void aMultiAnswerQuestionNeedsAtLeastOneCorrectOptionAndTwoOptions() throws Exception {
		String staff = fx.staff();
		UUID module = fx.module(staff, "Multi key");
		UUID exam = fx.createExam(staff, module, "MCQ", "Multi", NOON, 40, null);

		postQuestion(staff, exam, AuthoringFixture.questionJson("Nothing right", 2, null, true, "A", "B"))
			.andExpect(status().isBadRequest());
		postQuestion(staff, exam, AuthoringFixture.questionJson("Alone", 2, null, true, "*A"))
			.andExpect(status().isBadRequest());
		UUID question = fx.id(postQuestion(staff, exam,
				AuthoringFixture.questionJson("Select all that apply", 2, null, true, "*A", "*B", "C"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.multipleAnswer").value(true))
			.andReturn());

		JsonNode view = fx.exam(staff, exam);
		assertThat(view.get("questions").get(0).get("id").asText()).isEqualTo(question.toString());
		assertThat(view.get("questions").get(0).get("multipleAnswer").asBoolean()).isTrue();
	}

	@Test
	void turningMultiAnswerOffNeedsExactlyOneCorrectOption() throws Exception {
		String staff = fx.staff();
		UUID module = fx.module(staff, "Turn off");
		UUID exam = fx.createExam(staff, module, "MCQ", "Turn off", NOON, 40, null);
		UUID question = fx.question(staff, exam, AuthoringFixture.questionJson("Pick two", 1, null, true, "*A", "*B", "C"));

		patchQuestion(staff, exam, question, AuthoringFixture.questionJson("Pick two", 1, null, false, "*A", "*B", "C"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		patchQuestion(staff, exam, question, AuthoringFixture.questionJson("Pick one", 1, null, false, "*A", "B", "C"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.multipleAnswer").value(false));
		assertThat(fx.jdbc().queryForObject("select multiple_answer from live_exam_questions where id = ?", Boolean.class,
				question)).isFalse();
	}

	@Test
	void aStartedExamKeepsItsQuestionAndOptionCountButTakesEveryKeyAndWordingEdit() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID module = fx.module(staff, "Locked");
		UUID exam = fx.createExam(staff, module, "MCQ", "Started", NOON, 40, null);
		UUID kept = fx.question(staff, exam, AuthoringFixture.questionJson("Keep", 1, "Why", false, "*A", "B", "C"));
		UUID dropped = fx.question(staff, exam, AuthoringFixture.questionJson("Drop", 1, null, false, "*A", "B"));
		fx.publishNew(master, exam);

		UUID studentId = fx.seed("STUDENT");
		String student = fx.token(studentId);
		fx.grantModule(module, studentId);
		clock.set(IN_WINDOW);
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isOk());

		postQuestion(staff, exam, AuthoringFixture.questionJson("Late", 1, null, false, "*A", "B"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("QUESTION_STRUCTURE_LOCKED"));
		mockMvc.perform(delete("/api/v1/live-exams/" + exam + "/questions/" + dropped).header("Authorization", "Bearer " + staff))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("QUESTION_STRUCTURE_LOCKED"));
		patchQuestion(staff, exam, kept, AuthoringFixture.questionJson("Keep", 1, "Why", false, "*A", "B", "C", "D"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("QUESTION_STRUCTURE_LOCKED"));

		// Wording, key, marks, explanation and the multi-answer setting still change, and the row keeps its id.
		patchQuestion(staff, exam, kept, AuthoringFixture.questionJson("Reworded", 3, "Because", true, "A", "*B", "*C"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.stem").value("Reworded"))
			.andExpect(jsonPath("$.marks").value(3))
			.andExpect(jsonPath("$.multipleAnswer").value(true))
			.andExpect(jsonPath("$.id").value(kept.toString()))
			.andExpect(jsonPath("$.options", org.hamcrest.Matchers.hasSize(3)))
			.andExpect(jsonPath("$.options[1].correct").value(true))
			.andExpect(jsonPath("$.options[2].correct").value(true));
		// The exam is published, so the edit is staged (E31): the published rows keep their wording until publish.
		assertThat(fx.jdbc().queryForObject("select stem from live_exam_questions where id = ?", String.class, kept))
			.isEqualTo("Keep");

		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/submit").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk());
	}

	private ResultActions postQuestion(String token, UUID exam, String body) throws Exception {
		return mockMvc.perform(post("/api/v1/live-exams/" + exam + "/questions").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private ResultActions patchQuestion(String token, UUID exam, UUID question, String body) throws Exception {
		return mockMvc.perform(patch("/api/v1/live-exams/" + exam + "/questions/" + question)
			.header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
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
