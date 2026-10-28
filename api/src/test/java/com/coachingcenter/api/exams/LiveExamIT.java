package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, LiveExamIT.ClockConfig.class })
class LiveExamIT extends PostgresIntegrationTest {

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
	void mcqAndWrittenExamsShareOnePublishedModule() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		UUID module = publishModule(staff, "Shared");
		UUID mcq = createExam(staff, module, "MCQ", "Noon quiz", "দুপুর কুইজ", "2026-10-20T12:00:00+06:00", 30, 40, null);
		UUID written = createExam(staff, module, "WRITTEN", "Noon paper", "দুপুর খাতা", "2026-10-20T13:00:00+06:00", 30, 40,
				20);

		JsonNode list = exams(staff, module);
		assertThat(ids(list)).contains(mcq, written);
		assertThat(typeOf(list, mcq)).isEqualTo("MCQ");
		assertThat(typeOf(list, written)).isEqualTo("WRITTEN");
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		assertThat(jdbc.queryForObject("select count(*) from live_exams where id in (?, ?)", Integer.class, mcq, written))
			.isEqualTo(2);
		assertThat(jdbc.queryForObject("select count(*) from paper_components where id in (?, ?)", Integer.class, mcq,
				written)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from exam_modules where id = ?", Integer.class, module)).isZero();
	}

	@Test
	void publishRequiresPassPercentQuestionsAndAWrittenPaper() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		UUID module = publishModule(staff, "Gates");

		UUID noPercent = createExam(staff, module, "MCQ", "No percent", "শতকরা নেই", "2026-10-20T12:00:00+06:00", 30, null,
				null);
		addQuestion(staff, noPercent, "Stem", 1, "Because", "Yes", "No");
		finalSave(staff, noPercent);
		mockMvc.perform(post("/api/v1/live-exams/" + noPercent + "/publish").header("Authorization", "Bearer " + masterToken()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PASS_PERCENT_REQUIRED"));

		UUID emptyMcq = createExam(staff, module, "MCQ", "Empty", "খালি", "2026-10-20T15:00:00+06:00", 30, 50, null);
		finalSave(staff, emptyMcq);
		mockMvc.perform(post("/api/v1/live-exams/" + emptyMcq + "/publish").header("Authorization", "Bearer " + masterToken()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("QUESTIONS_REQUIRED"));

		UUID bareWritten = createExam(staff, module, "WRITTEN", "Bare", "খালি খাতা", "2026-10-20T16:00:00+06:00", 30, 50,
				null);
		finalSave(staff, bareWritten);
		mockMvc.perform(post("/api/v1/live-exams/" + bareWritten + "/publish").header("Authorization", "Bearer " + masterToken()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("WRITTEN_PAPER_REQUIRED"));
	}

	@Test
	void anEndedWindowCanStillBePublishedBecauseArchivedParticipantsSitAfterIt() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		UUID module = publishModule(staff, "Ended");
		UUID exam = createExam(staff, module, "MCQ", "Yesterday", "গতকাল", "2026-10-20T12:00:00+06:00", 40, 50, null);
		addQuestion(staff, exam, "Stem", 1, "Because", "Yes", "No");
		finalSave(staff, exam);
		clock.set(Instant.parse("2026-10-20T06:40:00Z"));
		publish(staff, exam);
		assertThat(exam(staff, exam).get("status").asText()).isEqualTo("PUBLISHED");
		assertThat(exam(staff, exam).get("phase").asText()).isEqualTo("ENDED");
	}

	@Test
	void onlyTheMasterAdminDeletesAnUnsatUnboughtExamAndEveryReadForgetsIt() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		String staff = token(seed("SYSTEM_ADMIN"));
		UUID studentId = seed("STUDENT");
		String student = token(studentId);
		UUID module = publishModule(staff, "Delete exam");
		new JdbcTemplate(dataSource).update(
				"insert into live_exam_roster (module_id, student_id, created_at) values (?, ?, now())", module, studentId);
		UUID exam = createExam(staff, module, "MCQ", "Doomed", "বাতিল", "2026-10-20T12:00:00+06:00", 40, 50, null);
		addQuestion(staff, exam, "One", 1, null, "a", "b");
		finalSave(staff, exam);
		publish(staff, exam);

		mockMvc.perform(delete("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff))
			.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + student))
			.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/v1/live-exams/" + exam)).andExpect(status().isUnauthorized());

		mockMvc.perform(delete("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		assertThat(jdbc.queryForObject("select deleted_at is not null from live_exams where id = ?", Boolean.class, exam))
			.isTrue();
		assertThat(jdbc.queryForObject(
				"select count(*) from audit_log where action = 'LIVE_EXAM_DELETE' and entity_id = ?", Integer.class, exam))
			.isEqualTo(1);
		mockMvc.perform(get("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + student))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/public/live-exams/" + exam)).andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + master))
			.andExpect(status().isNotFound());
		assertThat(ids(exams(staff, module))).doesNotContain(exam);
	}

	@Test
	void anExamThatWasSatOrBoughtCannotBeDeleted() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		String staff = token(seed("SYSTEM_ADMIN"));
		UUID studentId = seed("STUDENT");
		UUID module = publishModule(staff, "Keep exam");
		UUID sat = createExam(staff, module, "MCQ", "Sat", "বসা", "2026-10-20T12:00:00+06:00", 40, 50, null);
		addQuestion(staff, sat, "One", 1, null, "a", "b");
		finalSave(staff, sat);
		publish(staff, sat);
		UUID bought = createExam(staff, module, "MCQ", "Bought", "কেনা", "2026-10-20T14:00:00+06:00", 40, 50, null);
		addQuestion(staff, bought, "One", 1, null, "a", "b");
		finalSave(staff, bought);
		publish(staff, bought);
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		jdbc.update("""
				insert into live_exam_participants (id, exam_id, student_id, kind, started_at, deadline_at, status,
				  created_at, updated_at)
				values (?, ?, ?, 'LIVE', now(), now(), 'IN_PROGRESS', now(), now())
				""", UUID.randomUUID(), sat, studentId);
		jdbc.update("""
				insert into live_exam_orders (id, student_id, scope, module_id, exam_id, amount, currency, method, tran_id,
				  status, created_at, updated_at)
				values (?, ?, 'EXAM', ?, ?, 10.00, 'BDT', 'SSLCOMMERZ', ?, 'SUCCEEDED', now(), now())
				""", UUID.randomUUID(), studentId, module, bought, UUID.randomUUID().toString().replace("-", "").substring(0, 30));

		mockMvc.perform(delete("/api/v1/live-exams/" + sat).header("Authorization", "Bearer " + master))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EXAM_HAS_PARTICIPANTS"));
		mockMvc.perform(delete("/api/v1/live-exams/" + bought).header("Authorization", "Bearer " + master))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EXAM_HAS_PURCHASES"));
		assertThat(jdbc.queryForObject("select count(*) from live_exams where id in (?, ?) and deleted_at is null",
				Integer.class, sat, bought)).isEqualTo(2);
	}

	@Test
	void deletingAQuestionClosesTheGapAndLowersTheMaximumWhileTheExamStaysPublished() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		String student = token(seed("STUDENT"));
		UUID module = publishModule(staff, "Delete question");
		UUID exam = createExam(staff, module, "MCQ", "Fewer", "কম", "2026-10-20T12:00:00+06:00", 40, 50, null);
		UUID first = addQuestion(staff, exam, "First", 2, null, "a", "b");
		UUID second = addQuestion(staff, exam, "Second", 3, null, "a", "b");
		UUID third = addQuestion(staff, exam, "Third", 5, null, "a", "b");
		finalSave(staff, exam);
		publish(staff, exam);

		mockMvc.perform(delete("/api/v1/live-exams/" + exam + "/questions/" + second).header("Authorization", "Bearer " + student))
			.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/v1/live-exams/" + exam + "/questions/" + second).header("Authorization", "Bearer " + staff))
			.andExpect(status().isNoContent());

		// Editing a published exam never changes its status (E27); the edit is the working copy until it is published.
		JsonNode view = exam(staff, exam);
		assertThat(view.get("status").asText()).isEqualTo("PUBLISHED");
		assertThat(view.get("maximum").asInt()).isEqualTo(7);
		assertThat(view.get("questions")).hasSize(2);
		assertThat(view.get("questions").get(0).get("id").asText()).isEqualTo(first.toString());
		assertThat(view.get("questions").get(0).get("position").asInt()).isZero();
		assertThat(view.get("questions").get(1).get("id").asText()).isEqualTo(third.toString());
		assertThat(view.get("questions").get(1).get("position").asInt()).isEqualTo(1);
		// The delete is staged (E31): the rows keep the question and no audit row is written until the publish.
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		assertThat(jdbc.queryForObject(
				"select count(*) from live_exam_options where question_id = ? and deleted_at is null", Integer.class, second))
			.isEqualTo(2);
		assertThat(jdbc.queryForObject(
				"select count(*) from audit_log where action = 'LIVE_EXAM_QUESTION_DELETE' and entity_id = ?",
				Integer.class, exam)).isZero();
		mockMvc.perform(delete("/api/v1/live-exams/" + exam + "/questions/" + second).header("Authorization", "Bearer " + staff))
			.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/v1/live-exams/" + exam + "/questions/" + UUID.randomUUID())
			.header("Authorization", "Bearer " + staff))
			.andExpect(status().isNotFound());
	}

	@Test
	void aQuestionDeleteIsAllowedInTheWindowRefusedOnWrittenAndCannotEmptyAPublishedExam() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		UUID module = publishModule(staff, "Question rules");
		UUID mcq = createExam(staff, module, "MCQ", "Locked", "তালা", "2026-10-20T12:00:00+06:00", 40, 50, null);
		UUID only = addQuestion(staff, mcq, "Only", 1, null, "a", "b");
		finalSave(staff, mcq);
		publish(staff, mcq);
		UUID written = createExam(staff, module, "WRITTEN", "Paper", "খাতা", "2026-10-20T14:00:00+06:00", 40, 50, 10);
		uploadPdf(staff, written);

		mockMvc.perform(delete("/api/v1/live-exams/" + written + "/questions/" + UUID.randomUUID())
			.header("Authorization", "Bearer " + staff))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EXAM_TYPE_MISMATCH"));

		mockMvc.perform(delete("/api/v1/live-exams/" + mcq + "/questions/" + only).header("Authorization", "Bearer " + staff))
			.andExpect(status().isNoContent());
		finalSave(staff, mcq);
		mockMvc.perform(post("/api/v1/live-exams/" + mcq + "/publish").header("Authorization", "Bearer " + masterToken()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("QUESTIONS_REQUIRED"));

		UUID late = createExam(staff, module, "MCQ", "Late", "দেরি", "2026-10-20T16:00:00+06:00", 40, 50, null);
		UUID lateQuestion = addQuestion(staff, late, "One", 1, null, "a", "b");
		finalSave(staff, late);
		publish(staff, late);
		// There is no lock during the window (E27); only a student having started stops a question being removed (R16).
		clock.set(Instant.parse("2026-10-20T10:30:00Z"));
		mockMvc.perform(delete("/api/v1/live-exams/" + late + "/questions/" + lateQuestion)
			.header("Authorization", "Bearer " + staff))
			.andExpect(status().isNoContent());
	}

	@Test
	void mcqMaximumIsTheSumAndTheStudentUpcomingPayloadHidesStems() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		UUID studentId = seed("STUDENT");
		String student = token(studentId);
		UUID module = publishModule(staff, "Marks");
		new JdbcTemplate(dataSource).update(
				"insert into live_exam_roster (module_id, student_id, created_at) values (?, ?, now())", module, studentId);
		UUID exam = createExam(staff, module, "MCQ", "Sum", "যোগ", "2026-10-20T12:00:00+06:00", 40, 50, null);
		addQuestion(staff, exam, "Two plus two", 2, "Four", "4", "5");
		addQuestion(staff, exam, "Three plus three", 3, "Six", "6", "7");
		addQuestion(staff, exam, "Five plus five", 5, "Ten", "10", "11");
		finalSave(staff, exam);
		publish(staff, exam);

		JsonNode staffView = exam(staff, exam);
		assertThat(staffView.get("maximum").asInt()).isEqualTo(10);
		assertThat(staffView.get("questions")).hasSize(3);
		for (JsonNode question : staffView.get("questions")) {
			int correct = 0;
			for (JsonNode option : question.get("options")) {
				if (option.get("correct").asBoolean()) {
					correct++;
				}
			}
			assertThat(correct).isEqualTo(1);
		}

		String studentBody = mockMvc.perform(get("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.maximum").value(10))
			.andExpect(jsonPath("$.questions").doesNotExist())
			.andExpect(jsonPath("$.liveAt").value("2026-10-20T12:00:00+06:00"))
			.andExpect(jsonPath("$.endAt").value("2026-10-20T12:40:00+06:00"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(studentBody).doesNotContain("Two plus two", "Three plus three", "Five plus five");
	}

	@Test
	void windowsInOnePackageMayOverlapAndNothingIsEnforcedBetweenThem() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		UUID module = publishModule(staff, "Overlap");
		UUID written = createExam(staff, module, "WRITTEN", "Morning paper", "সকাল খাতা", "2026-10-20T12:00:00+06:00", 40,
				40, 20);
		uploadPdf(staff, written);
		finalSave(staff, written);
		publish(staff, written);

		// D17: the five-minute clash rule is gone, so a window that overlaps the first publishes.
		UUID crowded = createExam(staff, module, "MCQ", "Too soon", "খুব তাড়াতাড়ি", "2026-10-20T12:10:00+06:00", 20, 40,
				null);
		addQuestion(staff, crowded, "Stem", 2, null, "A", "B");
		finalSave(staff, crowded);
		publish(staff, crowded);
	}

	@Test
	void theScheduleScoringAndTextOfAPublishedExamTakeEditsDuringTheWindowAndTheExamStaysPublished() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		UUID module = publishModule(staff, "Lock");
		UUID exam = createExam(staff, module, "MCQ", "Locked quiz", "বন্ধ কুইজ", "2026-10-20T12:00:00+06:00", 40, 50, null);
		UUID question = addQuestion(staff, exam, "Original stem", 2, "Because", "A", "B");
		finalSave(staff, exam);
		publish(staff, exam);

		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		mockMvc.perform(patch("/api/v1/live-exams/" + exam + "/questions/" + question)
			.header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content(questionJson("Changed stem", 2, "Because", "A", "B")))
			.andExpect(status().isOk());
		mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"liveAt\":\"2026-10-20T12:05:00+06:00\",\"durationMinutes\":50}"))
			.andExpect(status().isOk());
		mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"passPercent\":10}"))
			.andExpect(status().isOk());

		JsonNode edited = exam(staff, exam);
		assertThat(edited.get("questions").get(0).get("stem").asText()).isEqualTo("Changed stem");
		assertThat(edited.get("liveAt").asText()).isEqualTo("2026-10-20T12:05:00+06:00");
		assertThat(edited.get("durationMinutes").asInt()).isEqualTo(50);
		assertThat(edited.get("passPercent").asInt()).isEqualTo(10);
		assertThat(edited.get("status").asText()).isEqualTo("PUBLISHED");

		UUID written = createExam(staff, module, "WRITTEN", "Locked paper", "বন্ধ খাতা", "2026-10-20T12:45:00+06:00", 40,
				50, 10);
		uploadPdf(staff, written);
		finalSave(staff, written);
		publish(staff, written);
		clock.set(Instant.parse("2026-10-20T06:50:00Z"));
		mockMvc.perform(patch("/api/v1/live-exams/" + written).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"maximum\":4,\"passPercent\":80}"))
			.andExpect(status().isOk());
		JsonNode paper = exam(staff, written);
		assertThat(paper.get("maximum").asInt()).isEqualTo(4);
		assertThat(paper.get("passPercent").asInt()).isEqualTo(80);
		assertThat(paper.get("status").asText()).isEqualTo("PUBLISHED");
	}

	@Test
	void anUndatedExamPublishesAndAnEditedScheduleKeepsTheExamPublished() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		UUID module = publishModule(staff, "Routine");
		UUID otherModule = publishModule(staff, "Other routine");
		UUID omitted = postExam(staff, module, null);
		assertThat(exam(staff, omitted).has("liveAt")).isFalse();
		assertThat(exam(staff, omitted).has("endAt")).isFalse();
		addQuestion(staff, omitted, "Stem", 2, null, "A", "B");
		finalSave(staff, omitted);
		publish(staff, omitted);

		UUID explicit = postExam(staff, module, "null");
		addQuestion(staff, explicit, "Stem", 2, null, "A", "B");
		finalSave(staff, explicit);
		publish(staff, explicit);

		JsonNode published = json.readTree(mockMvc.perform(get("/api/v1/public/live-exams/" + omitted))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString());
		assertThat(published.has("liveAt")).isFalse();
		assertThat(published.has("endAt")).isFalse();
		assertThat(published.has("questions")).isFalse();

		UUID morning = createExam(staff, module, "MCQ", "Morning", "সকাল", "2026-10-20T12:00:00+06:00", 40, 50, null);
		addQuestion(staff, morning, "Stem", 2, null, "A", "B");
		finalSave(staff, morning);
		publish(staff, morning);
		UUID crowded = createExam(staff, module, "MCQ", "Too soon", "কাছাকাছি", "2026-10-20T12:44:00+06:00", 20, 50, null);
		addQuestion(staff, crowded, "Stem", 2, null, "A", "B");
		finalSave(staff, crowded);
		publish(staff, crowded);

		UUID elsewhere = createExam(staff, otherModule, "MCQ", "Same clock", "একই সময়", "2026-10-20T12:00:00+06:00", 40,
				50, null);
		addQuestion(staff, elsewhere, "Stem", 2, null, "A", "B");
		finalSave(staff, elsewhere);
		publish(staff, elsewhere);

		JsonNode list = json.readTree(mockMvc.perform(get("/api/v1/public/live-exam-modules/" + module + "/exams")
			.param("size", "20"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString())
			.get("content");
		assertThat(indexOf(list, morning)).isLessThan(indexOf(list, crowded));
		assertThat(indexOf(list, crowded)).isLessThan(indexOf(list, omitted));
		assertThat(indexOf(list, crowded)).isLessThan(indexOf(list, explicit));

		// A date set on a published exam is the working copy (the web sends only liveAt, so the end follows it).
		mockMvc.perform(patch("/api/v1/live-exams/" + omitted).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"liveAt\":\"2026-10-20T15:00:00+06:00\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PUBLISHED"))
			.andExpect(jsonPath("$.liveAt").value("2026-10-20T15:00:00+06:00"))
			.andExpect(jsonPath("$.endAt").value("2026-10-20T15:40:00+06:00"));
		mockMvc.perform(patch("/api/v1/live-exams/" + crowded).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"liveAt\":null}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PUBLISHED"));
		assertThat(exam(staff, crowded).has("liveAt")).isFalse();

		clock.set(Instant.parse("2026-10-20T06:00:00Z"));
		mockMvc.perform(patch("/api/v1/live-exams/" + morning).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"liveAt\":\"2026-10-20T16:00:00+06:00\"}"))
			.andExpect(status().isOk());
		assertThat(exam(staff, morning).get("liveAt").asText()).isEqualTo("2026-10-20T16:00:00+06:00");
		assertThat(exam(staff, morning).get("status").asText()).isEqualTo("PUBLISHED");
	}

	private UUID postExam(String token, UUID moduleId, String liveAtJson) throws Exception {
		String liveAt = liveAtJson == null ? "" : ",\"liveAt\":" + liveAtJson;
		MvcResult result = mockMvc
			.perform(post("/api/v1/live-exam-modules/" + moduleId + "/exams").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "examType":"MCQ","paperLanguage":"BN","titleEn":"Open paper","titleBn":"খাতা",
						  "descriptionEn":"English description","descriptionBn":"বাংলা বিবরণ",
						  "rulesEn":"English rules","rulesBn":"বাংলা নিয়ম",
						  "durationMinutes":40,"passPercent":50%s
						}
						""".formatted(liveAt)))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private static int indexOf(JsonNode content, UUID id) {
		for (int index = 0; index < content.size(); index++) {
			if (id.toString().equals(content.get(index).get("id").asText())) {
				return index;
			}
		}
		throw new AssertionError("Missing " + id);
	}

	private UUID publishModule(String token, String title) throws Exception {
		UUID category = createCategory(token, title + " cat", "বিভাগ");
		MvcResult created = mockMvc.perform(post("/api/v1/live-exam-modules").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"categoryId":"%s","titleEn":"%s","titleBn":"মডিউল","descriptionEn":"d","descriptionBn":"বি"}
					""".formatted(category, title)))
			.andExpect(status().isCreated())
			.andReturn();
		UUID module = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/final-save").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/publish").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		return module;
	}

	private UUID createCategory(String token, String titleEn, String titleBn) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/live-exam-categories").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titleEn":"%s","titleBn":"%s"}
					""".formatted(titleEn, titleBn)))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private UUID createExam(String token, UUID moduleId, String type, String titleEn, String titleBn, String liveAt,
			int duration, Integer passPercent, Integer maximum) throws Exception {
		String percent = passPercent == null ? "" : ",\"passPercent\":" + passPercent;
		String max = maximum == null ? "" : ",\"maximum\":" + maximum;
		MvcResult result = mockMvc
			.perform(post("/api/v1/live-exam-modules/" + moduleId + "/exams").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "examType":"%s",
						  "paperLanguage":"BN",
						  "titleEn":"%s",
						  "titleBn":"%s",
						  "descriptionEn":"English description",
						  "descriptionBn":"বাংলা বিবরণ",
						  "rulesEn":"English rules",
						  "rulesBn":"বাংলা নিয়ম",
						  "liveAt":"%s",
						  "durationMinutes":%d%s%s
						}
						""".formatted(type, titleEn, titleBn, liveAt, duration, percent, max)))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private UUID addQuestion(String token, UUID examId, String stem, int marks, String explanation, String correct,
			String wrong) throws Exception {
		MvcResult result = mockMvc
			.perform(post("/api/v1/live-exams/" + examId + "/questions").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(questionJson(stem, marks, explanation, correct, wrong)))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private static String questionJson(String stem, int marks, String explanation, String correct, String wrong) {
		String explanationField = explanation == null ? "" : ",\"explanation\":\"%s\"".formatted(explanation);
		return """
				{"stem":"%s","marks":%d%s,"options":[{"text":"%s","correct":true},{"text":"%s","correct":false}]}
				""".formatted(stem, marks, explanationField, correct, wrong);
	}

	private void uploadPdf(String token, UUID examId) throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "paper.pdf", "application/pdf", tinyPdf());
		mockMvc.perform(multipart("/api/v1/live-exams/" + examId + "/question-pdf").file(file)
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.questionPdf").value(true));
	}

	private void finalSave(String token, UUID examId) throws Exception {
		mockMvc.perform(post("/api/v1/live-exams/" + examId + "/final-save").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.finallySaved").value(true));
	}

	private void publish(String token, UUID examId) throws Exception {
		// Only the Master Admin publishes an exam (decision of 2026-10-26); the caller's token is for the setup only.
		String master = token(seed("MASTER_ADMIN"));
		mockMvc.perform(post("/api/v1/live-exams/" + examId + "/publish").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PUBLISHED"));
	}

	private JsonNode exams(String token, UUID moduleId) throws Exception {
		MvcResult result = mockMvc
			.perform(get("/api/v1/live-exam-modules/" + moduleId + "/exams").header("Authorization", "Bearer " + token)
				.param("size", "100"))
			.andExpect(status().isOk())
			.andReturn();
		return json.readTree(result.getResponse().getContentAsString()).get("content");
	}

	private JsonNode exam(String token, UUID examId) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/live-exams/" + examId).header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn();
		return json.readTree(result.getResponse().getContentAsString());
	}

	private static java.util.List<UUID> ids(JsonNode content) {
		java.util.List<UUID> ids = new java.util.ArrayList<>();
		for (JsonNode node : content) {
			ids.add(UUID.fromString(node.get("id").asText()));
		}
		return ids;
	}

	private static String typeOf(JsonNode content, UUID id) {
		for (JsonNode node : content) {
			if (id.toString().equals(node.get("id").asText())) {
				return node.get("examType").asText();
			}
		}
		return null;
	}

	private static byte[] tinyPdf() {
		return """
				%PDF-1.1
				1 0 obj<</Type/Catalog>>endobj
				trailer<</Root 1 0 R>>
				%%EOF
				""".getBytes(StandardCharsets.US_ASCII);
	}

	private String masterToken() throws Exception {
		return token(seed("MASTER_ADMIN"));
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
		return "live-exam-" + UUID.randomUUID() + "@example.com";
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
