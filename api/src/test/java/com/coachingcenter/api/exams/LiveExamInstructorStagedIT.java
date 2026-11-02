package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * A teacher change on a published exam is a staged edit (NR-01, E31): the live row keeps its instructor until the Master Admin
 * publishes, and the change is listed as pending. A draft exam still takes the change straight to its row.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, LiveExamInstructorStagedIT.ClockConfig.class })
class LiveExamInstructorStagedIT extends PostgresIntegrationTest {

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

	/**
	 * A teacher change on a published exam is staged, whoever makes it: the row keeps its teacher until the Master Admin publishes the
	 * change, and the change shows in the exam's pending changes first.
	 */
	@Test
	void aMasterAdminsTeacherChangeOnAPublishedExamWaitsForPublish() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		String token = "Staged" + UUID.randomUUID().toString().substring(0, 8);
		UUID first = instructor(token + " One");
		UUID module = fx.module(staff, token + " package");
		UUID exam = fx.createExam(staff, module, "WRITTEN", "Teacher exam " + token, NOON, 40, null);
		fx.uploadPdf(staff, exam);
		fx.publishNew(master, exam);
		assertThat(instructorOf(exam)).isNull();

		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/instructor").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON).content(instructorBody(first)))
			.andExpect(status().isCreated());
		assertThat(instructorOf(exam)).as("the assign waits in the revision until publish").isNull();
		pending(master, exam);
		publishStaged(master, exam);
		assertThat(instructorOf(exam)).as("publishing applies the staged teacher").isEqualTo(first);

		mockMvc.perform(delete("/api/v1/live-exams/" + exam + "/instructor").header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		assertThat(instructorOf(exam)).as("the clear waits in the revision too").isEqualTo(first);
		pending(master, exam);
		publishStaged(master, exam);
		assertThat(instructorOf(exam)).as("publishing applies the clear").isNull();
	}

	private void publishStaged(String master, UUID exam) throws Exception {
		String revision = pending(master, exam).get("revisionId").asText();
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"revisionId\":\"" + revision + "\"}"))
			.andExpect(status().isOk());
	}

	/** Only the Master Admin assigns a teacher; a System Admin is refused and the exam keeps its teacher. */
	@Test
	void aSystemAdminCannotAssignATeacher() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		String token = "Refused" + UUID.randomUUID().toString().substring(0, 8);
		UUID teacher = instructor(token + " Teacher");
		UUID module = fx.module(staff, token + " package");
		UUID exam = fx.createExam(staff, module, "WRITTEN", "Teacher exam " + token, NOON, 40, null);
		fx.uploadPdf(staff, exam);
		fx.publishNew(master, exam);

		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/instructor").header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON).content(instructorBody(teacher)))
			.andExpect(status().isForbidden());
		assertThat(instructorOf(exam)).as("a refused assign leaves the exam without a teacher").isNull();
	}

	@Test
	void aDraftExamTakesTheTeacherChangeStraightToItsRow() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		String token = "Draft" + UUID.randomUUID().toString().substring(0, 8);
		UUID teacher = instructor(token + " Teacher");
		UUID module = fx.module(staff, token + " package");
		UUID draft = fx.createExam(staff, module, "WRITTEN", "Draft exam " + token, NOON, 40, null);

		mockMvc.perform(post("/api/v1/live-exams/" + draft + "/instructor").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON).content(instructorBody(teacher)))
			.andExpect(status().isCreated());
		assertThat(instructorOf(draft)).isEqualTo(teacher);
		mockMvc.perform(get("/api/v1/live-exams/" + draft + "/pending-changes").header("Authorization", "Bearer " + master))
			.andExpect(status().isNotFound());
	}

	/**
	 * An MCQ exam never takes an instructor: the assign is refused with 409 INSTRUCTOR_NOT_FOR_MCQ and the row stays empty. A
	 * written exam in the same package takes one as before.
	 */
	@Test
	void anMcqExamRefusesAnInstructorAndAWrittenExamTakesOne() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		String token = "Mcq" + UUID.randomUUID().toString().substring(0, 8);
		UUID teacher = instructor(token + " Teacher");
		UUID module = fx.module(staff, token + " package");
		UUID quiz = fx.createExam(staff, module, "MCQ", "Quiz " + token, NOON, 40, null);
		UUID paper = fx.createExam(staff, module, "WRITTEN", "Paper " + token, NOON, 40, null);

		mockMvc.perform(post("/api/v1/live-exams/" + quiz + "/instructor").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON).content(instructorBody(teacher)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INSTRUCTOR_NOT_FOR_MCQ"));
		assertThat(instructorOf(quiz)).as("the MCQ exam keeps no instructor").isNull();

		mockMvc.perform(post("/api/v1/live-exams/" + paper + "/instructor").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON).content(instructorBody(teacher)))
			.andExpect(status().isCreated());
		assertThat(instructorOf(paper)).isEqualTo(teacher);
	}

	private UUID instructor(String name) {
		UUID id = fx.seed("INSTRUCTOR");
		fx.jdbc().update("update admin_profiles set full_name = ? where user_id = ?", name, id);
		return id;
	}

	private static String instructorBody(UUID instructor) {
		return "{\"instructorId\":\"" + instructor + "\"}";
	}

	private UUID instructorOf(UUID exam) {
		return fx.jdbc().queryForObject("select instructor_id from live_exams where id = ?", UUID.class, exam);
	}

	private JsonNode pending(String token, UUID exam) throws Exception {
		return json.readTree(mockMvc.perform(get("/api/v1/live-exams/" + exam + "/pending-changes")
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString());
	}

	private static JsonNode field(JsonNode changes, String name) {
		for (JsonNode change : changes.get("fields")) {
			if (name.equals(change.get("field").asText())) {
				return change;
			}
		}
		throw new AssertionError("No change to " + name);
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
