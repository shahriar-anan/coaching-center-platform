package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.test.web.servlet.ResultActions;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * An MCQ attempt is sat and scored on the question set it started with (NR-01 D9 to D13). The paper has two single-answer
 * questions of two marks each, Q1 (A right, B wrong) and Q2 (X right, Y wrong). The window is 06:00 to 06:40 UTC. Edits go
 * through the staged revision and the Master Admin's publish, as they would for a live exam.
 *
 * <p>
 * Two tests need Agent B's slice: the option-count change (B makes option deletes soft) and the added question (B lifts the
 * structure lock, R16). They are written against the target behaviour and fail until that slice is merged.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, LiveExamAttemptSnapshotIT.ClockConfig.class })
class LiveExamAttemptSnapshotIT extends PostgresIntegrationTest {

	private static final Instant TEN_AM = Instant.parse("2026-10-20T04:00:00Z");

	private static final Instant SITTING = Instant.parse("2026-10-20T06:10:00Z");

	private static final Instant SUBMIT = Instant.parse("2026-10-20T06:20:00Z");

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
	void aRunningAttemptKeepsTheWordingItStartedWith() throws Exception {
		Paper paper = paper("Reload");
		Student student = enrol(paper);
		clock.set(SITTING);
		take(paper, student);
		edit(paper, paper.q1(), "{\"stem\":\"Second wording\",\"marks\":2,\"explanation\":\"Because\",\"options\":"
				+ "[{\"text\":\"A\",\"correct\":true},{\"text\":\"B\",\"correct\":false}]}");
		publishRevision(paper);
		assertThat(new JdbcTemplate(dataSource).queryForObject("select stem from live_exam_questions where id = ?", String.class,
				paper.q1())).isEqualTo("Second wording");
		mockMvc.perform(get("/api/v1/live-exams/" + paper.exam()).header("Authorization", "Bearer " + student.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.questions[0].stem").value("First wording"));
	}

	@Test
	void aKeyChangeAfterSubmitDoesNotChangeTheScore() throws Exception {
		Paper paper = paper("Key");
		Student student = enrol(paper);
		clock.set(SITTING);
		take(paper, student);
		save(paper, student, "[{\"questionId\":\"%s\",\"optionId\":\"%s\"},{\"questionId\":\"%s\",\"optionId\":\"%s\"}]"
			.formatted(paper.q1(), paper.a(), paper.q2(), paper.x()));
		clock.set(SUBMIT);
		submit(paper, student).andExpect(jsonPath("$.score").value(4));
		// Under the new key, Q1 would be wrong for the answer A, so a re-score would give 2.
		clock.set(Instant.parse("2026-10-20T06:30:00Z"));
		edit(paper, paper.q1(), "{\"stem\":\"First wording\",\"marks\":2,\"explanation\":\"Because\",\"options\":"
				+ "[{\"text\":\"A\",\"correct\":false},{\"text\":\"B\",\"correct\":true}]}");
		publishRevision(paper);
		assertThat(new JdbcTemplate(dataSource).queryForObject("select correct from live_exam_options where id = ?", Boolean.class,
				paper.b())).isTrue();
		mockMvc.perform(get("/api/v1/live-exams/" + paper.exam() + "/result").header("Authorization", "Bearer " + student.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(4));
	}

	@Test
	void anOptionCountChangeDoesNotMoveAnAnswerOffItsOwnKey() throws Exception {
		Paper paper = paper("Options");
		Student student = enrol(paper);
		clock.set(SITTING);
		take(paper, student);
		save(paper, student, "[{\"questionId\":\"%s\",\"optionId\":\"%s\"}]".formatted(paper.q1(), paper.a()))
			.andExpect(status().isNoContent());
		edit(paper, paper.q1(), "{\"stem\":\"First wording\",\"marks\":2,\"explanation\":\"Because\",\"options\":"
				+ "[{\"text\":\"A\",\"correct\":true},{\"text\":\"B\",\"correct\":false},{\"text\":\"C\",\"correct\":false}]}");
		publishRevision(paper);
		clock.set(SUBMIT);
		// The answer was given on two options; the attempt's own key still has A right, so Q1 scores its two marks.
		submit(paper, student).andExpect(jsonPath("$.score").value(2));
		Student later = enrol(paper);
		clock.set(Instant.parse("2026-10-20T06:25:00Z"));
		take(paper, later);
		mockMvc.perform(get("/api/v1/live-exams/" + paper.exam()).header("Authorization", "Bearer " + later.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.questions[0].options.length()").value(3));
	}

	@Test
	void anAddedQuestionIsNotInAnOldAttemptButIsInANewOne() throws Exception {
		Paper paper = paper("Added");
		Student student = enrol(paper);
		clock.set(SITTING);
		take(paper, student);
		mockMvc.perform(post("/api/v1/live-exams/" + paper.exam() + "/questions").header("Authorization", "Bearer " + paper.staff())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"stem\":\"Added question\",\"marks\":2,\"explanation\":\"Because\",\"options\":"
					+ "[{\"text\":\"P\",\"correct\":true},{\"text\":\"Q\",\"correct\":false}]}"))
			.andExpect(status().isCreated());
		publishRevision(paper);
		mockMvc.perform(get("/api/v1/live-exams/" + paper.exam()).header("Authorization", "Bearer " + student.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.questions.length()").value(2));
		Student later = enrol(paper);
		clock.set(Instant.parse("2026-10-20T06:25:00Z"));
		take(paper, later);
		mockMvc.perform(get("/api/v1/live-exams/" + paper.exam()).header("Authorization", "Bearer " + later.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.questions.length()").value(3));
	}

	@Test
	void aRetakeGetsTheCurrentQuestionSet() throws Exception {
		Paper paper = paper("Retake");
		Student student = enrol(paper);
		clock.set(SITTING);
		take(paper, student);
		clock.set(SUBMIT);
		submit(paper, student).andExpect(status().isOk());
		clock.set(Instant.parse("2026-10-20T06:22:00Z"));
		edit(paper, paper.q1(), "{\"stem\":\"Second wording\",\"marks\":2,\"explanation\":\"Because\",\"options\":"
				+ "[{\"text\":\"A\",\"correct\":true},{\"text\":\"B\",\"correct\":false}]}");
		publishRevision(paper);
		clock.set(Instant.parse("2026-10-20T06:25:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + paper.exam() + "/retake").header("Authorization", "Bearer " + student.token())
			.contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
		assertThat(snapshotOf(paper, student, 1)).contains("First wording").doesNotContain("Second wording");
		assertThat(snapshotOf(paper, student, 2)).contains("Second wording").doesNotContain("First wording");
	}

	@Test
	void anAttemptWithNoSnapshotReadsTheLiveRows() throws Exception {
		Paper paper = paper("Legacy");
		Student student = enrol(paper);
		clock.set(SITTING);
		take(paper, student);
		// An attempt started before snapshots existed has no copy; it reads the live rows, as it always did.
		new JdbcTemplate(dataSource).update("update live_exam_participants set question_snapshot = null where exam_id = ?",
				paper.exam());
		edit(paper, paper.q1(), "{\"stem\":\"Second wording\",\"marks\":2,\"explanation\":\"Because\",\"options\":"
				+ "[{\"text\":\"A\",\"correct\":true},{\"text\":\"B\",\"correct\":false}]}");
		publishRevision(paper);
		mockMvc.perform(get("/api/v1/live-exams/" + paper.exam()).header("Authorization", "Bearer " + student.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.questions[0].stem").value("Second wording"));
	}

	@Test
	void theLeaderboardRanksByRawMarksAcrossTwoVersions() throws Exception {
		Paper paper = paper("Board");
		// Version 1 is worth 4 marks: the first student gets all of them, 100 percent.
		Student first = enrol(paper);
		clock.set(SITTING);
		take(paper, first);
		save(paper, first, "[{\"questionId\":\"%s\",\"optionId\":\"%s\"},{\"questionId\":\"%s\",\"optionId\":\"%s\"}]"
			.formatted(paper.q1(), paper.a(), paper.q2(), paper.x()));
		clock.set(SUBMIT);
		submit(paper, first).andExpect(jsonPath("$.score").value(4));
		// Version 2 is worth 8 marks: Q2 is now worth 6. The second student gets Q2 and misses Q1, so 6 of 8, 75 percent.
		clock.set(Instant.parse("2026-10-20T06:22:00Z"));
		edit(paper, paper.q2(), "{\"stem\":\"Second question\",\"marks\":6,\"explanation\":\"Because\",\"options\":"
				+ "[{\"text\":\"X\",\"correct\":true},{\"text\":\"Y\",\"correct\":false}]}");
		publishRevision(paper);
		Student second = enrol(paper);
		clock.set(Instant.parse("2026-10-20T06:25:00Z"));
		take(paper, second);
		save(paper, second, "[{\"questionId\":\"%s\",\"optionId\":\"%s\"},{\"questionId\":\"%s\",\"optionId\":\"%s\"}]"
			.formatted(paper.q1(), paper.b(), paper.q2(), paper.x()));
		clock.set(Instant.parse("2026-10-20T06:35:00Z"));
		submit(paper, second).andExpect(jsonPath("$.score").value(6));
		// Ranked by raw marks: the second student is first (6 over 4), though the first student has the higher percent. Each row keeps its own maximum.
		mockMvc.perform(get("/api/v1/live-exams/" + paper.exam() + "/leaderboard").header("Authorization", "Bearer " + paper.master()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.rows[0].rank").value(1))
			.andExpect(jsonPath("$.rows[0].score").value(6))
			.andExpect(jsonPath("$.rows[0].maximum").value(8))
			.andExpect(jsonPath("$.rows[0].percent").value(75.0))
			.andExpect(jsonPath("$.rows[1].rank").value(2))
			.andExpect(jsonPath("$.rows[1].score").value(4))
			.andExpect(jsonPath("$.rows[1].maximum").value(4))
			.andExpect(jsonPath("$.rows[1].percent").value(100.0));
	}

	@Test
	void theStaffResultsShowEachAttemptsOwnMaximum() throws Exception {
		Paper paper = paper("Results");
		// Version 1 is worth 4 marks, and the first student scores all of it.
		Student first = enrol(paper);
		clock.set(SITTING);
		take(paper, first);
		save(paper, first, "[{\"questionId\":\"%s\",\"optionId\":\"%s\"},{\"questionId\":\"%s\",\"optionId\":\"%s\"}]"
			.formatted(paper.q1(), paper.a(), paper.q2(), paper.x()));
		clock.set(SUBMIT);
		submit(paper, first).andExpect(jsonPath("$.score").value(4));
		// Version 2 is worth 8 marks, and the second student submits under it.
		clock.set(Instant.parse("2026-10-20T06:22:00Z"));
		edit(paper, paper.q2(), "{\"stem\":\"Second question\",\"marks\":6,\"explanation\":\"Because\",\"options\":"
				+ "[{\"text\":\"X\",\"correct\":true},{\"text\":\"Y\",\"correct\":false}]}");
		publishRevision(paper);
		Student second = enrol(paper);
		clock.set(Instant.parse("2026-10-20T06:25:00Z"));
		take(paper, second);
		save(paper, second, "[{\"questionId\":\"%s\",\"optionId\":\"%s\"},{\"questionId\":\"%s\",\"optionId\":\"%s\"}]"
			.formatted(paper.q1(), paper.b(), paper.q2(), paper.x()));
		clock.set(Instant.parse("2026-10-20T06:35:00Z"));
		submit(paper, second).andExpect(jsonPath("$.score").value(6));
		// Newest submission first: the second student's row is out of 8, the first student's row out of 4 (D9, D15).
		mockMvc.perform(get("/api/v1/live-exams/" + paper.exam() + "/results").header("Authorization", "Bearer " + paper.master()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.rows.content[0].score").value(6))
			.andExpect(jsonPath("$.rows.content[0].maximum").value(8))
			.andExpect(jsonPath("$.rows.content[1].score").value(4))
			.andExpect(jsonPath("$.rows.content[1].maximum").value(4))
			// The summary stays in raw marks, as students see them: 4 and 6, so the average is 5 and the range 4 to 6.
			.andExpect(jsonPath("$.summary.averageScore").value(5.0))
			.andExpect(jsonPath("$.summary.highestScore").value(6))
			.andExpect(jsonPath("$.summary.lowestScore").value(4));
	}

	@Test
	void aRunningAttemptIsMarkedUnderTheNegativeMarksItStartedWith() throws Exception {
		Paper paper = paper("Negative marks");
		Student early = enrol(paper);
		clock.set(SITTING);
		take(paper, early);
		// Staff turn on one mark off per wrong answer after the first student has started (a published change).
		clock.set(Instant.parse("2026-10-20T06:12:00Z"));
		mockMvc.perform(patch("/api/v1/live-exams/" + paper.exam()).header("Authorization", "Bearer " + paper.staff())
			.contentType(MediaType.APPLICATION_JSON).content("{\"negativeMarks\":1,\"passPercent\":95}"))
			.andExpect(status().isOk());
		publishRevision(paper);
		Student late = enrol(paper);
		clock.set(Instant.parse("2026-10-20T06:14:00Z"));
		take(paper, late);
		// Both answer Q1 wrongly. The first started under no negative marks, so 0; the second under one mark off, so -1 (D9).
		clock.set(Instant.parse("2026-10-20T06:15:00Z"));
		save(paper, early, "[{\"questionId\":\"%s\",\"optionId\":\"%s\"}]".formatted(paper.q1(), paper.b()));
		save(paper, late, "[{\"questionId\":\"%s\",\"optionId\":\"%s\"}]".formatted(paper.q1(), paper.b()));
		clock.set(Instant.parse("2026-10-20T06:16:00Z"));
		submit(paper, early).andExpect(jsonPath("$.score").value(0));
		submit(paper, late).andExpect(jsonPath("$.score").value(-1));
	}

	@Test
	void aStudentPaperShowsTheFullMarksOfTheAttemptNotTheCurrentPaper() throws Exception {
		Paper paper = paper("Paper marks");
		Student first = enrol(paper);
		clock.set(SITTING);
		take(paper, first);
		clock.set(Instant.parse("2026-10-20T06:22:00Z"));
		edit(paper, paper.q2(), "{\"stem\":\"Second question\",\"marks\":6,\"explanation\":\"Because\",\"options\":"
				+ "[{\"text\":\"X\",\"correct\":true},{\"text\":\"Y\",\"correct\":false}]}");
		publishRevision(paper);
		// The first student's paper was 4 marks when they started, so it still says 4, not the current 8 (D9).
		mockMvc.perform(get("/api/v1/live-exams/" + paper.exam()).header("Authorization", "Bearer " + first.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.maximum").value(4));
	}

	@Test
	void aStudentPaperShowsTheNegativeMarkAndPassPercentOfTheAttempt() throws Exception {
		Paper paper = paper("Paper rules");
		Student early = enrol(paper);
		clock.set(SITTING);
		take(paper, early);
		int passAtStart = json.readTree(mockMvc.perform(get("/api/v1/live-exams/" + paper.exam())
			.header("Authorization", "Bearer " + early.token())).andReturn().getResponse().getContentAsString()).get("passPercent").asInt();
		clock.set(Instant.parse("2026-10-20T06:12:00Z"));
		mockMvc.perform(patch("/api/v1/live-exams/" + paper.exam()).header("Authorization", "Bearer " + paper.staff())
			.contentType(MediaType.APPLICATION_JSON).content("{\"negativeMarks\":1,\"passPercent\":95}"))
			.andExpect(status().isOk());
		publishRevision(paper);
		Student late = enrol(paper);
		clock.set(Instant.parse("2026-10-20T06:14:00Z"));
		take(paper, late);

		JsonNode earlyView = json.readTree(mockMvc.perform(get("/api/v1/live-exams/" + paper.exam())
			.header("Authorization", "Bearer " + early.token())).andReturn().getResponse().getContentAsString());
		assertThat(earlyView.get("passPercent").asInt()).isEqualTo(passAtStart).isNotEqualTo(95);
		assertThat(earlyView.path("negativeMarks").asDouble(0)).isZero();
		mockMvc.perform(get("/api/v1/live-exams/" + paper.exam()).header("Authorization", "Bearer " + late.token()))
			.andExpect(jsonPath("$.passPercent").value(95))
			.andExpect(jsonPath("$.negativeMarks").value(1));
	}

	@Test
	void aScoreNoticeKeepsTheMaximumOfTheAttempt() throws Exception {
		Paper paper = paper("Notice marks");
		Student first = enrol(paper);
		clock.set(SITTING);
		take(paper, first);
		clock.set(SUBMIT);
		submit(paper, first).andExpect(status().isOk());
		clock.set(Instant.parse("2026-10-20T06:22:00Z"));
		edit(paper, paper.q2(), "{\"stem\":\"Second question\",\"marks\":6,\"explanation\":\"Because\",\"options\":"
				+ "[{\"text\":\"X\",\"correct\":true},{\"text\":\"Y\",\"correct\":false}]}");
		publishRevision(paper);
		// The paper was out of 4 when it was sat. The notice says 4, not the current 8.
		mockMvc.perform(get("/api/v1/me/notices").param("size", "50").header("Authorization", "Bearer " + first.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[?(@.kind == 'MCQ_SCORE')].maximum").value(org.hamcrest.Matchers.contains(4)));
	}

	private record Paper(UUID exam, UUID module, UUID q1, UUID q2, UUID a, UUID b, UUID x, UUID y, String staff, String master) {
	}

	/** A student on the paper: their id and their token. */
	private record Student(UUID id, String token) {
	}

	/** One exam with two questions, Q1 (A right, B wrong) and Q2 (X right, Y wrong), each worth two marks, published. */
	private Paper paper(String title) throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		UUID module = publishModule(staff, title);
		MvcResult created = mockMvc
			.perform(post("/api/v1/live-exam-modules/" + module + "/exams").header("Authorization", "Bearer " + staff)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "examType":"MCQ","paperLanguage":"BN","titleEn":"%s quiz","titleBn":"কুইজ",
						  "descriptionEn":"d","descriptionBn":"বি","rulesEn":"English rules","rulesBn":"নিয়ম",
						  "liveAt":"2026-10-20T12:00:00+06:00","durationMinutes":40,"passPercent":50
						}
						""".formatted(title)))
			.andExpect(status().isCreated())
			.andReturn();
		UUID exam = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
		JsonNode first = question(staff, exam, "First wording", "A", "true", "B", "false");
		JsonNode second = question(staff, exam, "Second question", "X", "true", "Y", "false");
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/final-save").header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
		return new Paper(exam, module, UUID.fromString(first.get("id").asText()), UUID.fromString(second.get("id").asText()),
				option(first, "A"), option(first, "B"), option(second, "X"), option(second, "Y"), staff, master);
	}

	/** Puts a new student on the paper's package and returns them with a token. */
	private Student enrol(Paper paper) throws Exception {
		UUID student = seed("STUDENT");
		mockMvc.perform(post("/api/v1/live-exam-modules/" + paper.module() + "/roster")
			.header("Authorization", "Bearer " + paper.master())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"studentId":"%s"}
					""".formatted(student)))
			.andExpect(status().isCreated());
		return new Student(student, token(student));
	}

	/** A question whose options are given as {text, correct} pairs. Marks are two. */
	private JsonNode question(String staff, UUID exam, String stem, String... options) throws Exception {
		List<String> items = new ArrayList<>();
		for (int index = 0; index < options.length; index += 2) {
			items.add("{\"text\":\"%s\",\"correct\":%s}".formatted(options[index], options[index + 1]));
		}
		MvcResult result = mockMvc.perform(post("/api/v1/live-exams/" + exam + "/questions")
			.header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"stem":"%s","marks":2,"explanation":"Because","options":[%s]}
					""".formatted(stem, String.join(",", items))))
			.andExpect(status().isCreated())
			.andReturn();
		return json.readTree(result.getResponse().getContentAsString());
	}

	private UUID option(JsonNode question, String text) {
		for (JsonNode option : question.get("options")) {
			if (text.equals(option.get("text").asText())) {
				return UUID.fromString(option.get("id").asText());
			}
		}
		throw new AssertionError("no option " + text);
	}

	private void edit(Paper paper, UUID questionId, String body) throws Exception {
		mockMvc.perform(patch("/api/v1/live-exams/" + paper.exam() + "/questions/" + questionId)
			.header("Authorization", "Bearer " + paper.staff())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body))
			.andExpect(status().isOk());
	}

	/** Publishes the staged edits of a published exam, as the Master Admin does. */
	private void publishRevision(Paper paper) throws Exception {
		mockMvc.perform(post("/api/v1/live-exams/" + paper.exam() + "/publish").header("Authorization", "Bearer " + paper.master()))
			.andExpect(status().isOk());
	}

	private void take(Paper paper, Student student) throws Exception {
		mockMvc.perform(post("/api/v1/live-exams/" + paper.exam() + "/take").header("Authorization", "Bearer " + student.token())
			.contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
	}

	private ResultActions submit(Paper paper, Student student) throws Exception {
		return mockMvc.perform(post("/api/v1/live-exams/" + paper.exam() + "/submit").header("Authorization", "Bearer " + student.token()));
	}

	private ResultActions save(Paper paper, Student student, String answers) throws Exception {
		return mockMvc.perform(put("/api/v1/live-exams/" + paper.exam() + "/answers").header("Authorization", "Bearer " + student.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"answers\":" + answers + "}"));
	}

	/** The question set stored on the student's attempt with this number, as text. */
	private String snapshotOf(Paper paper, Student student, int attemptNo) {
		String snapshot = new JdbcTemplate(dataSource).queryForObject("""
				select question_snapshot from live_exam_participants
				where exam_id = ? and student_id = ? and attempt_no = ?
				""", String.class, paper.exam(), student.id(), attemptNo);
		assertThat(snapshot).isNotNull();
		return snapshot;
	}

	private UUID publishModule(String token, String title) throws Exception {
		MvcResult category = mockMvc.perform(post("/api/v1/live-exam-categories").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titleEn":"%s cat","titleBn":"বিভাগ"}
					""".formatted(title)))
			.andExpect(status().isCreated())
			.andReturn();
		UUID categoryId = UUID.fromString(json.readTree(category.getResponse().getContentAsString()).get("id").asText());
		MvcResult created = mockMvc.perform(post("/api/v1/live-exam-modules").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"categoryId":"%s","titleEn":"%s","titleBn":"মডিউল","descriptionEn":"d","descriptionBn":"বি"}
					""".formatted(categoryId, title)))
			.andExpect(status().isCreated())
			.andReturn();
		UUID module = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/final-save").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/publish").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		return module;
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
		return "snapshot-" + UUID.randomUUID() + "@example.com";
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
