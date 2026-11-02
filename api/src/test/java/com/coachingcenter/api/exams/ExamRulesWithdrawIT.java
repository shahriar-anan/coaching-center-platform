package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.MvcResult;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.ObjectMapper;

/**
 * The rules for withdrawing and editing: a withdrawn exam takes edits (staged when it was published), withdraw is refused
 * during an open sitting, questions change after a start, rows are soft-deleted, texts are optional, a written maximum
 * cannot fall below a mark, and publishing does not re-score.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, ExamRulesWithdrawIT.ClockConfig.class })
class ExamRulesWithdrawIT extends PostgresIntegrationTest {

	private static final Instant TEN_AM = Instant.parse("2026-10-20T04:00:00Z");

	/** The window opens at 12:00 Dhaka. A student who takes at 12:10 Dhaka has a deadline at 12:50 Dhaka. */
	private static final Instant TAKEN_AT = Instant.parse("2026-10-20T06:10:00Z");

	private static final Instant AFTER_DEADLINE = Instant.parse("2026-10-20T07:00:00Z");

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
	void editsToAWithdrawnExamThatWasPublishedAreStagedUntilThePublish() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID category = fx.category(staff, "Withdrawn staged");
		UUID exam = fx.createStandalone(staff, category, "MCQ", "Old title", AuthoringFixture.LIVE_AT, 40, null);
		fx.question(staff, exam, "Stem", 1);
		fx.publishNew(master, exam);
		withdraw(master, exam);

		mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"titleEn\":\"New title\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ARCHIVED"))
			.andExpect(jsonPath("$.pendingChanges").value(true));
		assertThat(titleInRow(exam)).isEqualTo("Old title");

		// The staged revision of a withdrawn exam publishes, and the exam stays withdrawn (D6).
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ARCHIVED"))
			.andExpect(jsonPath("$.pendingChanges").value(false));
		assertThat(titleInRow(exam)).isEqualTo("New title");
	}

	@Test
	void editsToAWithdrawnExamThatWasNeverPublishedApplyDirectly() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID category = fx.category(staff, "Withdrawn draft");
		UUID exam = fx.createStandalone(staff, category, "MCQ", "Draft title", AuthoringFixture.LIVE_AT, 40, null);
		withdraw(master, exam);

		mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"titleEn\":\"Renamed draft\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ARCHIVED"))
			.andExpect(jsonPath("$.titleEn").value("Renamed draft"))
			.andExpect(jsonPath("$.pendingChanges").value(false));
		assertThat(titleInRow(exam)).isEqualTo("Renamed draft");

		// Still no publish without a restore: the withdrawn exam says so.
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + master))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EXAM_ARCHIVED"));
	}

	@Test
	void withdrawIsRefusedWhileAStudentIsSittingAndAllowedAfterTheDeadline() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID module = fx.module(staff, "Withdraw sitting");
		UUID exam = fx.createExam(staff, module, "MCQ", "Sitting now", AuthoringFixture.LIVE_AT, 40, null);
		fx.question(staff, exam, "Stem", 1);
		fx.publishNew(master, exam);
		takeExam(module, exam);

		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/archive").header("Authorization", "Bearer " + master))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EXAM_SITTING_OPEN"));

		clock.set(AFTER_DEADLINE);
		withdraw(master, exam);
	}

	@Test
	void questionsCanBeAddedAndRemovedAfterAStudentStarted() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID module = fx.module(staff, "Structure after start");
		UUID exam = fx.createExam(staff, module, "MCQ", "Structure", AuthoringFixture.LIVE_AT, 40, null);
		UUID first = fx.question(staff, exam, "Stem", 1);
		fx.publishNew(master, exam);
		takeExam(module, exam);

		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/questions").header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content(AuthoringFixture.questionJson("Added after start", 2, "Because", false, "*A", "B")))
			.andExpect(status().isCreated());
		mockMvc.perform(delete("/api/v1/live-exams/" + exam + "/questions/" + first).header("Authorization", "Bearer " + staff))
			.andExpect(status().is2xxSuccessful());
	}

	@Test
	void aChangedOptionCountKeepsTheOldOptionRows() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID category = fx.category(staff, "Soft delete");
		UUID exam = fx.createStandalone(staff, category, "MCQ", "Options", AuthoringFixture.LIVE_AT, 40, null);
		UUID question = fx.question(staff, exam, "Stem", 1);
		fx.publishNew(master, exam);
		UUID firstOption = fx.jdbc().queryForObject(
				"select id from live_exam_options where question_id = ? and position = 0", UUID.class, question);

		mockMvc.perform(patch("/api/v1/live-exams/" + exam + "/questions/" + question)
			.header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content(AuthoringFixture.questionJson("Stem", 1, "Because", false, "*A", "B", "C")))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());

		// The two replaced options are soft-deleted, not removed, so a pinned answer still points at a real row (D12).
		assertThat(fx.jdbc().queryForObject(
				"select count(*) from live_exam_options where question_id = ? and deleted_at is not null", Integer.class,
				question)).isEqualTo(2);
		assertThat(fx.jdbc().queryForObject("select count(*) from live_exam_options where id = ?", Integer.class,
				firstOption)).isEqualTo(1);
	}

	@Test
	void descriptionsAndRulesCanBeBlankOnAPackageAndAnExam() throws Exception {
		String staff = fx.staff();
		UUID category = fx.category(staff, "Blank package text");
		MvcResult created = mockMvc.perform(post("/api/v1/live-exam-modules").header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content(json.writeValueAsString(Map.of("categoryId", category, "titleEn", "Blank package", "titleBn", "মডিউল",
					"descriptionEn", "", "descriptionBn", ""))))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.descriptionEn").value(""))
			.andReturn();
		UUID module = fx.id(created);
		mockMvc.perform(patch("/api/v1/live-exam-modules/" + module).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"descriptionEn\":\"\",\"descriptionBn\":\"\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.descriptionBn").value(""));

		UUID exam = fx.createStandalone(staff, category, "MCQ", "Blank exam text", AuthoringFixture.LIVE_AT, 40, null);
		mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"descriptionEn\":\"\",\"rulesEn\":\"\",\"rulesBn\":\"\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.rulesEn").value(""));
	}

	@Test
	void publishingAChangedKeyDoesNotRescoreSubmittedPapers() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID module = fx.module(staff, "No rescore");
		UUID exam = fx.createExam(staff, module, "MCQ", "No rescore", AuthoringFixture.LIVE_AT, 40, null);
		UUID question = fx.question(staff, exam, "Stem", 1);
		fx.publishNew(master, exam);
		UUID student = takeExam(module, exam);
		fx.jdbc().update(
				"update live_exam_participants set status = 'SUBMITTED', score = 1, submitted_at = now() where exam_id = ? and student_id = ?",
				exam, student);

		mockMvc.perform(patch("/api/v1/live-exams/" + exam + "/questions/" + question).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content(AuthoringFixture.questionJson("Stem", 1, "Because", false, "A", "*B")))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());

		BigDecimal score = fx.jdbc().queryForObject("select score from live_exam_participants where exam_id = ?",
				BigDecimal.class, exam);
		assertThat(score).isEqualByComparingTo("1");
		assertThat(fx.jdbc().queryForObject("select rescored from live_exam_change_log where exam_id = ?", Integer.class,
				exam)).isZero();
	}

	@Test
	void restoringAWithdrawnExamAppliesItsStagedEditsToTheDraft() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID module = fx.module(staff, "Restore staged");
		UUID exam = fx.createExam(staff, module, "MCQ", "Before restore", AuthoringFixture.LIVE_AT, 40, null);
		fx.question(staff, exam, "Stem", 1);
		fx.publishNew(master, exam);
		withdraw(master, exam);
		mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"titleEn\":\"Staged title\"}"))
			.andExpect(status().isOk());
		assertThat(titleInRow(exam)).isEqualTo("Before restore");

		// The staged edit is not lost on restore: it becomes the draft's rows, and nothing is published (D8, D5).
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/restore").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.pendingChanges").value(false));
		assertThat(titleInRow(exam)).isEqualTo("Staged title");
		assertThat(fx.jdbc().queryForObject("select count(*) from live_exam_revisions where exam_id = ?", Integer.class, exam))
			.isZero();
	}

	@Test
	void aCategoryAndAPackageCannotBeArchivedWhileAStudentIsSitting() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID module = fx.module(staff, "Archive while sitting");
		UUID exam = fx.createExam(staff, module, "MCQ", "Sitting under archive", AuthoringFixture.LIVE_AT, 40, null);
		fx.question(staff, exam, "Stem", 1);
		fx.publishNew(master, exam);
		takeExam(module, exam);
		UUID category = fx.jdbc().queryForObject("select category_id from live_exam_modules where id = ?", UUID.class, module);

		mockMvc.perform(post("/api/v1/live-exam-categories/" + category + "/archive").header("Authorization", "Bearer " + master))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CATALOG_SITTING_OPEN"));
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/archive").header("Authorization", "Bearer " + master))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CATALOG_SITTING_OPEN"));
		mockMvc.perform(delete("/api/v1/live-exam-modules/" + module).header("Authorization", "Bearer " + master))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CATALOG_SITTING_OPEN"));

		// Once the sitting has ended the package can be archived.
		clock.set(AFTER_DEADLINE);
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/archive").header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
	}

	@Test
	void aWithdrawnExamThatWasNeverPublishedChangesModeLikeADraft() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID category = fx.category(staff, "Mode withdrawn");
		UUID exam = fx.createStandalone(staff, category, "MCQ", "Mode change", AuthoringFixture.LIVE_AT, 40, null);
		withdraw(master, exam);
		mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"mode\":\"PRACTICE\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mode").value("PRACTICE"));
	}

	@Test
	void aTimeChangeOnAWithdrawnExamIsNotAnnouncedButOnAPublishedOneIs() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID module = fx.module(staff, "Time notices");
		UUID withdrawn = fx.createExam(staff, module, "MCQ", "Withdrawn time", AuthoringFixture.LIVE_AT, 40, null);
		fx.question(staff, withdrawn, "Stem", 1);
		fx.publishNew(master, withdrawn);
		UUID live = fx.createExam(staff, module, "MCQ", "Live time", AuthoringFixture.LIVE_AT, 40, null);
		fx.question(staff, live, "Stem", 1);
		fx.publishNew(master, live);
		fx.grantModule(module, fx.seed("STUDENT"));
		withdraw(master, withdrawn);

		for (UUID exam : List.of(withdrawn, live)) {
			mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"durationMinutes\":50}"))
				.andExpect(status().isOk());
			mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + master))
				.andExpect(status().isOk());
		}

		// The control: a published exam's time change is announced; the withdrawn one's is not (students cannot see it).
		assertThat(timeNotices(live)).isPositive();
		assertThat(timeNotices(withdrawn)).isZero();
	}

	@Test
	void aWrittenResultKeepsTheMaximumItWasSatOnWhenTheExamMaximumChanges() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID module = fx.module(staff, "Written maximum kept");
		UUID exam = fx.createExam(staff, module, "WRITTEN", "Written kept", AuthoringFixture.LIVE_AT, 40, null);
		fx.uploadPdf(staff, exam);
		mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"maximum\":10}"))
			.andExpect(status().isOk());
		fx.publishNew(master, exam);
		UUID student = takeExam(module, exam);
		// Marked 8 out of 10, released (V67: the attempt keeps the maximum it was sat on).
		fx.jdbc().update("update live_exam_participants set status = 'SUBMITTED', score = 8, passed = true, submitted_at = now(),"
				+ " published_at = now() where exam_id = ? and student_id = ?", exam, student);

		// The maximum can be raised or lowered after marking, even below the mark given, and the marked paper does not move.
		for (int maximum : List.of(20, 5)) {
			mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"maximum\":" + maximum + "}"))
				.andExpect(status().isOk());
			mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + master))
				.andExpect(status().isOk());
			mockMvc.perform(get("/api/v1/live-exams/" + exam + "/leaderboard").header("Authorization", "Bearer " + master))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rows[0].score").value(8))
				.andExpect(jsonPath("$.rows[0].maximum").value(10))
				.andExpect(jsonPath("$.rows[0].percent").value(80.0));
		}
	}

	private int timeNotices(UUID exam) {
		return fx.jdbc().queryForObject(
				"select count(*) from student_notices where kind = 'EXAM_TIME_CHANGED' and exam_id = ?", Integer.class, exam);
	}

	private void withdraw(String master, UUID exam) throws Exception {
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/archive").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ARCHIVED"));
	}

	/** A student is granted the package and takes the exam at 12:10 Dhaka. Returns the student's id. */
	private UUID takeExam(UUID module, UUID exam) throws Exception {
		UUID student = fx.seed("STUDENT");
		fx.grantModule(module, student);
		clock.set(TAKEN_AT);
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take").header("Authorization", "Bearer " + fx.token(student)))
			.andExpect(status().is2xxSuccessful());
		return student;
	}

	private String titleInRow(UUID exam) {
		return fx.jdbc().queryForObject("select title_en from live_exams where id = ?", String.class, exam);
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
