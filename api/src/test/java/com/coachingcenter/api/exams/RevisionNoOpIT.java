package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * NR-01 final check, case 10 (A4): an edit to a published exam that is undone leaves no revision behind. Nothing is waiting,
 * the change list is 404, the exam is not on the changes-waiting badge, and the exam reads as it was published.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, RevisionNoOpIT.ClockConfig.class })
class RevisionNoOpIT extends PostgresIntegrationTest {

	private static final Instant TEN_AM = Instant.parse("2026-10-20T04:00:00Z");

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
	void aTitleChangedAndChangedBackLeavesNoRevisionAndNoBadgeEntry() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID module = fx.module(staff, "Undo title");
		UUID exam = fx.createExam(staff, module, "MCQ", "Original", NOON, 40, null);
		fx.question(staff, exam, "Stem", 1);
		fx.publishNew(master, exam);

		stage(staff, exam, "{\"titleEn\":\"Something else\"}");
		assertThat(revisions(exam)).isOne();
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/pending-changes").header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk());
		assertThat(badgeIds(staff)).contains(exam);

		stage(staff, exam, "{\"titleEn\":\"Original\"}");

		assertThat(revisions(exam)).isZero();
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/pending-changes").header("Authorization", "Bearer " + staff))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.titleEn").value("Original"))
			.andExpect(jsonPath("$.pendingChanges").value(false));
		assertThat(badgeIds(staff)).doesNotContain(exam);
	}

	@Test
	void aQuestionStemAndOptionChangedAndChangedBackLeavesNoRevision() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID module = fx.module(staff, "Undo question");
		UUID exam = fx.createExam(staff, module, "MCQ", "Questions", NOON, 40, null);
		UUID question = fx.question(staff, exam, AuthoringFixture.questionJson("Stem", 1, "Because", false, "*A", "B"));
		fx.publishNew(master, exam);

		stageQuestion(staff, exam, question, AuthoringFixture.questionJson("Changed stem", 1, "Because", false, "A", "*B"));
		assertThat(revisions(exam)).isOne();

		stageQuestion(staff, exam, question, AuthoringFixture.questionJson("Stem", 1, "Because", false, "*A", "B"));

		assertThat(revisions(exam)).isZero();
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/pending-changes").header("Authorization", "Bearer " + staff))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff))
			.andExpect(jsonPath("$.pendingChanges").value(false));
		assertThat(badgeIds(staff)).doesNotContain(exam);
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

	private int revisions(UUID exam) {
		return fx.jdbc().queryForObject("select count(*) from live_exam_revisions where exam_id = ?", Integer.class, exam);
	}

	/** The changes-waiting badge: the ids of every exam and package it lists. */
	private java.util.Set<UUID> badgeIds(String staff) throws Exception {
		JsonNode badge = json.readTree(mockMvc.perform(get("/api/v1/live-exam-pending-changes")
			.header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString());
		java.util.Set<UUID> ids = new java.util.HashSet<>();
		badge.get("items").forEach(item -> ids.add(UUID.fromString(item.get("id").asText())));
		return ids;
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
