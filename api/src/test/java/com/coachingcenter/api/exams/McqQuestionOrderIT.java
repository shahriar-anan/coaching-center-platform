package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The question order of an MCQ paper. A draft takes a new order at once; a published paper stages it until the Master Admin
 * publishes. A student having started does not stop a reorder, because no question or option is added or removed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, ArchivedAndRetakeMarkingIT.ClockConfig.class })
class McqQuestionOrderIT extends PostgresIntegrationTest {

	private static final Instant BEFORE = Instant.parse("2026-10-20T04:00:00Z");

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
	void aDraftPaperTakesTheNewOrderAtOnce() throws Exception {
		String staff = fx.staff();
		UUID exam = mcqWithThreeQuestions(staff);
		UUID first = questionId(exam, "First stem");
		UUID second = questionId(exam, "Second stem");
		UUID third = questionId(exam, "Third stem");

		reorder(staff, exam, third, first, second).andExpect(status().isNoContent());
		assertThat(stems(staff, exam)).containsExactly("Third stem", "First stem", "Second stem");
	}

	@Test
	void anOrderThatDoesNotNameEveryQuestionOnceIsRefused() throws Exception {
		String staff = fx.staff();
		UUID exam = mcqWithThreeQuestions(staff);
		UUID first = questionId(exam, "First stem");
		UUID second = questionId(exam, "Second stem");

		reorder(staff, exam, first).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		reorder(staff, exam, first, first, second).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		reorder(staff, exam, first, second, UUID.randomUUID()).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/questions/reorder").header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isBadRequest());
		assertThat(stems(staff, exam)).containsExactly("First stem", "Second stem", "Third stem");
	}

	@Test
	void aPublishedPaperStagesTheOrderAndAStartedStudentDoesNotStopIt() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID exam = mcqWithThreeQuestions(staff);
		fx.publishNew(master, exam);
		UUID first = questionId(exam, "First stem");
		UUID second = questionId(exam, "Second stem");
		UUID third = questionId(exam, "Third stem");

		UUID studentId = fx.seed("STUDENT");
		grant(master, studentId, exam);
		String student = fx.token(studentId);
		assertThat(fx.exam(staff, exam).get("started").asBoolean()).isFalse();
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		take(student, exam);
		assertThat(fx.exam(staff, exam).get("started").asBoolean()).isTrue();
		mockMvc.perform(get("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.started").doesNotExist());

		reorder(staff, exam, third, first, second).andExpect(status().isNoContent());
		assertThat(stems(staff, exam)).as("the staff view shows the staged order").containsExactly("Third stem", "First stem", "Second stem");
		assertThat(liveStems(exam)).as("the live paper keeps its order until publish").containsExactly("First stem", "Second stem", "Third stem");
		assertThat(fx.exam(staff, exam).get("pendingChanges").asBoolean()).isTrue();

		publishStaged(master, exam);
		assertThat(liveStems(exam)).containsExactly("Third stem", "First stem", "Second stem");
	}

	private UUID mcqWithThreeQuestions(String staff) throws Exception {
		UUID category = fx.category(staff, "Order " + UUID.randomUUID().toString().substring(0, 8));
		UUID exam = fx.createStandalone(staff, category, "MCQ", "Order exam", AuthoringFixture.LIVE_AT, 40, null);
		fx.question(staff, exam, "First stem", 1);
		fx.question(staff, exam, "Second stem", 1);
		fx.question(staff, exam, "Third stem", 1);
		return exam;
	}

	private UUID questionId(UUID exam, String stem) {
		return fx.jdbc().queryForObject("select id from live_exam_questions where exam_id = ? and stem = ? and deleted_at is null",
				UUID.class, exam, stem);
	}

	private ResultActions reorder(String token, UUID exam, UUID... ids) throws Exception {
		String list = Arrays.stream(ids).map(id -> "\"" + id + "\"").collect(Collectors.joining(","));
		return mockMvc.perform(post("/api/v1/live-exams/" + exam + "/questions/reorder").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"questionIds\":[" + list + "]}"));
	}

	/** The stems in the order the staff view shows them (the working copy). */
	private List<String> stems(String token, UUID exam) throws Exception {
		JsonNode questions = fx.exam(token, exam).get("questions");
		return StreamSupport.stream(questions.spliterator(), false).map(question -> question.get("stem").asText())
			.toList();
	}

	/** The stems in the order the live paper holds them. */
	private List<String> liveStems(UUID exam) {
		return new JdbcTemplate(dataSource).queryForList(
				"select stem from live_exam_questions where exam_id = ? and deleted_at is null order by position", String.class, exam);
	}

	private void grant(String master, UUID student, UUID exam) throws Exception {
		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"studentId":"%s","scope":"EXAM","moduleId":null,"examId":"%s","reason":"Order test"}
					""".formatted(student, exam)))
			.andExpect(status().isCreated());
	}

	private void take(String token, UUID exam) throws Exception {
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isOk());
	}

	/** Publishes what is staged, as the Master Admin does after reading the change list. */
	private void publishStaged(String master, UUID exam) throws Exception {
		String revision = json.readTree(mockMvc.perform(get("/api/v1/live-exams/" + exam + "/pending-changes")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString()).get("revisionId").asText();
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"revisionId\":\"" + revision + "\"}"))
			.andExpect(status().isOk());
	}
}
