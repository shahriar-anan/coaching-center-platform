package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * NR-01 final check, case 2 (R16, A4): once a student has started a published MCQ exam, the staged edits that add or
 * remove questions or options cannot be published. The published paper stays as it was and the revision stays waiting.
 * A wording-only edit still stages and publishes after a start.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, StagedPublishGuardIT.ClockConfig.class })
class StagedPublishGuardIT extends PostgresIntegrationTest {

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
	void aStagedAddedQuestionCannotBePublishedOnceAStudentHasStarted() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID module = fx.module(staff, "Added question");
		UUID exam = fx.createExam(staff, module, "MCQ", "Adds", NOON, 40, null);
		UUID stem = fx.question(staff, exam, "Original stem", 1);
		fx.publishNew(master, exam);

		UUID added = fx.question(staff, exam, AuthoringFixture.questionJson("Added", 1, null, false, "*A", "B"));
		assertThat(added).isNotNull();
		assertThat(questionCount(exam)).isEqualTo(1);

		UUID studentId = fx.seed("STUDENT");
		fx.grantModule(module, studentId);
		startSitting(studentId, exam);

		JsonNode changes = pending(staff, exam);
		assertThat(questionChanges(changes)).contains("ADDED");

		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"revisionId\":\"" + changes.get("revisionId").asText() + "\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("QUESTION_STRUCTURE_LOCKED"));

		assertThat(questionCount(exam)).isEqualTo(1);
		assertThat(fx.jdbc().queryForObject("select status from live_exams where id = ?", String.class, exam))
			.isEqualTo("PUBLISHED");
		assertThat(fx.jdbc().queryForObject("select count(*) from live_exam_revisions where exam_id = ?", Integer.class, exam))
			.isOne();
		mockMvc.perform(get("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.pendingChanges").value(true));

		discard(staff, exam);
		wordingOnlyEditPublishesAfterAStart(staff, master, exam, stem, "*A", "B");
	}

	@Test
	void aStagedRemovedOptionCannotBePublishedOnceAStudentHasStarted() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID module = fx.module(staff, "Removed option");
		UUID exam = fx.createExam(staff, module, "MCQ", "Options", NOON, 40, null);
		UUID stem = fx.question(staff, exam, AuthoringFixture.questionJson("Three options", 1, "Because", false, "*A", "B", "C"));
		fx.publishNew(master, exam);

		mockMvc.perform(patch("/api/v1/live-exams/" + exam + "/questions/" + stem).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content(AuthoringFixture.questionJson("Three options", 1, "Because", false, "*A", "B")))
			.andExpect(status().isOk());

		UUID studentId = fx.seed("STUDENT");
		fx.grantModule(module, studentId);
		startSitting(studentId, exam);

		JsonNode changes = pending(staff, exam);
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"revisionId\":\"" + changes.get("revisionId").asText() + "\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("QUESTION_STRUCTURE_LOCKED"));

		assertThat(optionCount(stem)).isEqualTo(3);
		assertThat(fx.jdbc().queryForObject("select status from live_exams where id = ?", String.class, exam))
			.isEqualTo("PUBLISHED");
		mockMvc.perform(get("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff))
			.andExpect(jsonPath("$.pendingChanges").value(true));

		discard(staff, exam);
		wordingOnlyEditPublishesAfterAStart(staff, master, exam, stem, "*A", "B", "C");
	}

	/**
	 * Step 4: after the discard, a wording-only edit stages and publishes, though students have started. The options
	 * passed are the published ones, so the edit changes nothing but the stem.
	 */
	private void wordingOnlyEditPublishesAfterAStart(String staff, String master, UUID exam, UUID stem, String... options)
			throws Exception {
		mockMvc.perform(patch("/api/v1/live-exams/" + exam + "/questions/" + stem).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content(AuthoringFixture.questionJson("Reworded stem", 1, "Because", false, options)))
			.andExpect(status().isOk());
		JsonNode changes = pending(staff, exam);
		assertThat(questionChanges(changes)).containsOnly("STEM");

		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"revisionId\":\"" + changes.get("revisionId").asText() + "\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.pendingChanges").value(false));
		assertThat(fx.jdbc().queryForObject("select stem from live_exam_questions where id = ?", String.class, stem))
			.isEqualTo("Reworded stem");
	}

	private void startSitting(UUID studentId, UUID exam) throws Exception {
		String student = fx.token(studentId);
		clock.set(IN_WINDOW);
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isOk());
	}

	private void discard(String staff, UUID exam) throws Exception {
		mockMvc.perform(delete("/api/v1/live-exams/" + exam + "/pending-changes").header("Authorization", "Bearer " + staff))
			.andExpect(status().isNoContent());
	}

	private int questionCount(UUID exam) {
		return fx.jdbc().queryForObject(
				"select count(*) from live_exam_questions where exam_id = ? and deleted_at is null", Integer.class, exam);
	}

	private int optionCount(UUID question) {
		return fx.jdbc().queryForObject(
				"select count(*) from live_exam_options where question_id = ? and deleted_at is null", Integer.class, question);
	}

	private JsonNode pending(String token, UUID exam) throws Exception {
		return json.readTree(mockMvc.perform(get("/api/v1/live-exams/" + exam + "/pending-changes")
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString());
	}

	private static java.util.Set<String> questionChanges(JsonNode changes) {
		java.util.Set<String> kinds = new java.util.HashSet<>();
		changes.get("questions").forEach(change -> kinds.add(change.get("change").asText()));
		return kinds;
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
