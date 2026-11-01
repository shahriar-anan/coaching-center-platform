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

	@Test
	void assignAndUnassignOnAPublishedExamAreStagedUntilPublished() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		String token = "Staged" + UUID.randomUUID().toString().substring(0, 8);
		UUID first = instructor(token + " One");
		UUID second = instructor(token + " Two");
		UUID module = fx.module(staff, token + " package");
		UUID exam = fx.createExam(staff, module, "WRITTEN", "Teacher exam " + token, NOON, 40, null);
		fx.uploadPdf(staff, exam);
		fx.publishNew(master, exam);
		assertThat(instructorOf(exam)).isNull();

		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/instructor").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON).content(instructorBody(first)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.examId").value(exam.toString()))
			.andExpect(jsonPath("$.instructorId").value(first.toString()));
		assertThat(instructorOf(exam)).as("the live row keeps its instructor until publish").isNull();

		mockMvc.perform(get("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.instructor.id").value(first.toString()))
			.andExpect(jsonPath("$.pendingChanges").value(true));
		JsonNode changes = pending(master, exam);
		assertThat(field(changes, "instructor").get("after").asText()).isEqualTo(first.toString());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/instructor").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON).content(instructorBody(second)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INSTRUCTOR_ALREADY_ASSIGNED"));

		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"revisionId\":\"" + changes.get("revisionId").asText() + "\"}"))
			.andExpect(status().isOk());
		assertThat(instructorOf(exam)).as("publishing applies the staged instructor").isEqualTo(first);

		mockMvc.perform(delete("/api/v1/live-exams/" + exam + "/instructor").header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		assertThat(instructorOf(exam)).as("the unassign is staged, the row keeps the instructor").isEqualTo(first);
		JsonNode cleared = pending(master, exam);
		assertThat(field(cleared, "instructor").hasNonNull("after")).as("a removal has no staged teacher").isFalse();
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"revisionId\":\"" + cleared.get("revisionId").asText() + "\"}"))
			.andExpect(status().isOk());
		assertThat(instructorOf(exam)).as("publishing applies the unassign").isNull();
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

	@Test
	void theTeacherOnAPublishedExamIsStagedAndTheClearIsStagedToo() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		String token = "Pkg" + UUID.randomUUID().toString().substring(0, 8);
		UUID teacher = instructor(token + " Teacher");
		UUID module = fx.module(staff, token + " package");
		UUID exam = fx.createExam(staff, module, "WRITTEN", "Package exam " + token, NOON, 40, null);
		fx.uploadPdf(staff, exam);
		fx.publishNew(master, exam);

		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/instructor").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON).content(instructorBody(teacher)))
			.andExpect(status().isCreated());
		assertThat(instructorOf(exam)).as("the teacher waits for publish on a published exam").isNull();
		JsonNode changes = pending(master, exam);
		assertThat(field(changes, "instructor").get("after").asText()).isEqualTo(teacher.toString());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"revisionId\":\"" + changes.get("revisionId").asText() + "\"}"))
			.andExpect(status().isOk());
		assertThat(instructorOf(exam)).isEqualTo(teacher);

		mockMvc.perform(delete("/api/v1/live-exams/" + exam + "/instructor").header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		assertThat(instructorOf(exam)).as("the clear waits for publish too").isEqualTo(teacher);
		assertThat(field(pending(master, exam), "instructor").hasNonNull("after")).as("a removal has no staged teacher").isFalse();
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
