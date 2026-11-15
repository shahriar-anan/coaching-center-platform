package com.coachingcenter.api.offline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;
import com.coachingcenter.api.auth.CapturingEmailSender;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Exam results of an offline batch (plan sections 3 to 5 and 9; results-api-contract). The clock stays at one instant, so onboarding dates,
 * closing dates and publish dates are moved with SQL where a rule depends on them. Every test makes its own batch, exam and students.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, OfflineResultsIT.ClockConfig.class })
@TestPropertySource(properties = "app.offline-admission.enabled=true")
class OfflineResultsIT extends PostgresIntegrationTest {

	/** Thirteen November, Dhaka morning. Exams used here are on the tenth, so they are in the past. */
	private static final Instant NOW = Instant.parse("2026-11-13T04:00:00Z");

	private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

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

	@Autowired
	private CapturingEmailSender emails;

	@BeforeEach
	void fixTheClock() {
		clock.set(NOW);
	}

	@Test
	void previewBlocksAnUnknownRollAndADuplicateRoll() throws Exception {
		Fixture fixture = fixture();
		String unknown = roll();
		byte[] sheet = mcqSheet(List.of(
				List.of(fixture.first().roll(), "40", "0"),
				List.of(fixture.first().roll(), "30", "0"),
				List.of(unknown, "10", "0")));

		JsonNode preview = respond(upload("/api/v1/offline-classes/" + fixture.exam() + "/results/preview", fixture.master(), sheet,
				"MCQ", null), 200);

		assertThat(preview.get("problems").toString()).contains("more than once").contains("No student of this batch has this roll number.");
		assertThat(preview.get("counts").get("rows").asInt()).isEqualTo(5);
		assertThat(preview.get("counts").get("matched").asInt()).isEqualTo(2);
		assertThat(preview.get("counts").get("absent").asInt()).isEqualTo(2);
		assertThat(jdbc().queryForObject("select count(*) from offline_result_sheets where class_id = ?", Long.class, fixture.exam()))
			.isZero();
	}

	@Test
	void saveIsRefusedWhileThereAreProblems() throws Exception {
		Fixture fixture = fixture();
		byte[] sheet = mcqSheet(List.of(List.of(roll(), "40", "0")));

		respond(upload("/api/v1/offline-classes/" + fixture.exam() + "/results", fixture.master(), sheet, "MCQ", null), 400);
		assertThat(jdbc().queryForObject("select count(*) from offline_result_sheets where class_id = ?", Long.class, fixture.exam()))
			.isZero();
	}

	@Test
	void staffUploadsButOnlyTheMasterAdminPublishes() throws Exception {
		Fixture fixture = fixture();

		JsonNode staged = respond(upload("/api/v1/offline-classes/" + fixture.exam() + "/results", fixture.staff(),
				fixture.sheet(40, 19), "MCQ", "30"), 201);
		assertThat(staged.get("staged").get("status").asText()).isEqualTo("STAGED");
		assertThat(staged.get("staged").get("rows")).hasSize(3);
		assertThat(staged.get("published").isNull()).isTrue();

		respond(post("/api/v1/offline-classes/" + fixture.exam() + "/results/publish", fixture.staff(), null), 403);
		assertThat(jdbc().queryForObject("select count(*) from student_notices where kind = 'OFFLINE_RESULT_PUBLISHED'", Long.class))
			.isNotNull();

		JsonNode exams = json.readTree(mockMvc.perform(get("/api/v1/offline-batches/" + fixture.batch() + "/results")
			.header("Authorization", "Bearer " + fixture.staff())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		assertThat(exams.get(0).get("state").asText()).isEqualTo("STAGED");

		JsonNode published = respond(post("/api/v1/offline-classes/" + fixture.exam() + "/results/publish", fixture.master(), null), 200);
		assertThat(published.get("published").get("status").asText()).isEqualTo("PUBLISHED");
		assertThat(published.get("staged").isNull()).isTrue();
		assertThat(jdbc().queryForObject("select count(*) from student_notices where user_id = ? and kind = 'OFFLINE_RESULT_PUBLISHED'",
				Long.class, fixture.first().student())).isEqualTo(1L);
		assertThat(jdbc().queryForObject("select count(*) from student_notices where user_id = ? and kind = 'OFFLINE_RESULT_PUBLISHED'",
				Long.class, fixture.absent().student())).isEqualTo(1L);

		JsonNode afterPublish = json.readTree(mockMvc.perform(get("/api/v1/offline-batches/" + fixture.batch() + "/results")
			.header("Authorization", "Bearer " + fixture.staff())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		assertThat(afterPublish.get(0).get("state").asText()).isEqualTo("PUBLISHED");
		assertThat(afterPublish.get(0).get("rowCount").asInt()).isEqualTo(3);
		respond(post("/api/v1/offline-classes/" + fixture.exam() + "/results/publish", fixture.master(), null), 409);
	}

	@Test
	void republishReplacesThePublishedSheetAndSendsAnUpdateNotice() throws Exception {
		Fixture fixture = publishedFixture(40, 19);
		UUID first = publishedSheet(fixture);

		respond(upload("/api/v1/offline-classes/" + fixture.exam() + "/results", fixture.staff(), fixture.sheet(41, 19), "MCQ", null), 201);
		JsonNode republished = respond(post("/api/v1/offline-classes/" + fixture.exam() + "/results/publish", fixture.master(), null), 200);

		assertThat(UUID.fromString(republished.get("published").get("id").asText())).isNotEqualTo(first);
		assertThat(jdbc().queryForObject("select status from offline_result_sheets where id = ?", String.class, first)).isEqualTo("REPLACED");
		assertThat(jdbc().queryForObject("select count(*) from student_notices where user_id = ? and kind = 'OFFLINE_RESULT_UPDATED'",
				Long.class, fixture.first().student())).isEqualTo(1L);
		assertThat(jdbc().queryForObject("select count(*) from student_notices where user_id = ? and kind = 'OFFLINE_RESULT_PUBLISHED'",
				Long.class, fixture.first().student())).isEqualTo(1L);
	}

	@Test
	void discardRemovesOnlyTheStagedSheet() throws Exception {
		Fixture fixture = publishedFixture(40, 19);
		UUID published = publishedSheet(fixture);
		respond(upload("/api/v1/offline-classes/" + fixture.exam() + "/results", fixture.staff(), fixture.sheet(44, 19), "MCQ", null), 201);

		respond(delete("/api/v1/offline-classes/" + fixture.exam() + "/results/staged", fixture.staff()), 204);

		assertThat(jdbc().queryForObject("select status from offline_result_sheets where id = ?", String.class, published))
			.isEqualTo("PUBLISHED");
		assertThat(jdbc().queryForObject("select count(*) from offline_result_sheets where class_id = ? and status = 'STAGED'", Long.class,
				fixture.exam())).isZero();
		respond(delete("/api/v1/offline-classes/" + fixture.exam() + "/results/staged", fixture.staff()), 404);
	}

	@Test
	void studentSeesThePublishedResultAndTheLeaderboard() throws Exception {
		Fixture fixture = publishedFixture(40, 19);
		publishedSheet(fixture);

		JsonNode results = json.readTree(mockMvc.perform(get("/api/v1/me/admissions/" + fixture.first().admission() + "/results")
			.header("Authorization", "Bearer " + token(fixture.first().student()))).andExpect(status().isOk()).andReturn().getResponse()
			.getContentAsString());
		assertThat(results.get("roll").asText()).isEqualTo(fixture.first().roll());
		JsonNode exam = results.get("exams").get(0);
		assertThat(exam.get("total").decimalValue()).isEqualByComparingTo("40");
		assertThat(exam.get("rank").asInt()).isEqualTo(1);
		assertThat(exam.get("rankOf").asInt()).isEqualTo(2);
		assertThat(exam.get("absent").asBoolean()).isFalse();
		assertThat(exam.get("written").isNull()).isTrue();

		JsonNode board = json.readTree(mockMvc.perform(get("/api/v1/me/admissions/" + fixture.first().admission() + "/results/"
				+ fixture.exam()).header("Authorization", "Bearer " + token(fixture.first().student()))).andExpect(status().isOk()).andReturn()
			.getResponse().getContentAsString());
		assertThat(board.get("myRoll").asText()).isEqualTo(fixture.first().roll());
		JsonNode rows = board.get("rows");
		assertThat(rows.get(0).get("roll").asText()).isEqualTo(fixture.first().roll());
		assertThat(rows.get(0).get("mine").asBoolean()).isTrue();
		assertThat(rows.get(1).get("roll").asText()).isEqualTo(fixture.second().roll());
		assertThat(rows.get(1).get("mine").asBoolean()).isFalse();
		assertThat(rows.get(1).get("rank").asInt()).isEqualTo(2);
		assertThat(rows.get(2).get("roll").asText()).isEqualTo(fixture.absent().roll());
		assertThat(rows.get(2).get("absent").asBoolean()).isTrue();
		assertThat(rows.get(2).get("rank").isNull()).isTrue();
	}

	@Test
	void anotherStudentGetsNotFoundForTheAdmissionAndTheLeaderboard() throws Exception {
		Fixture fixture = publishedFixture(40, 19);
		publishedSheet(fixture);

		respond(getAs("/api/v1/me/admissions/" + fixture.first().admission() + "/results", token(fixture.second().student())), 404);
		respond(getAs("/api/v1/me/admissions/" + fixture.first().admission() + "/results/" + fixture.exam(),
				token(fixture.second().student())), 404);
		respond(getAs("/api/v1/me/admissions/" + fixture.second().admission() + "/results", token(fixture.second().student())), 200);
	}

	@Test
	void anExpectedStudentWithNoRowIsAbsentAndALaterJoinerIsNotListed() throws Exception {
		Fixture fixture = publishedFixture(40, 19);
		publishedSheet(fixture);
		Student joinedLater = onboardedStudent(fixture.master(), fixture.batch(), roll(), "2026-11-12T04:00:00Z");

		JsonNode absent = json.readTree(mockMvc.perform(get("/api/v1/me/admissions/" + fixture.absent().admission() + "/results")
			.header("Authorization", "Bearer " + token(fixture.absent().student()))).andExpect(status().isOk()).andReturn().getResponse()
			.getContentAsString());
		JsonNode exam = absent.get("exams").get(0);
		assertThat(exam.get("absent").asBoolean()).isTrue();
		assertThat(exam.get("total").isNull()).isTrue();
		assertThat(exam.get("rank").isNull()).isTrue();

		JsonNode later = json.readTree(mockMvc.perform(get("/api/v1/me/admissions/" + joinedLater.admission() + "/results")
			.header("Authorization", "Bearer " + token(joinedLater.student()))).andExpect(status().isOk()).andReturn().getResponse()
			.getContentAsString());
		assertThat(later.get("exams")).isEmpty();
	}

	@Test
	void aClosedStudentKeepsTheResultsPublishedBeforeTheClose() throws Exception {
		Fixture fixture = publishedFixture(40, 19);
		UUID first = publishedSheet(fixture);
		jdbc().update("update offline_result_sheets set published_at = ? where id = ?",
				Timestamp.from(Instant.parse("2026-11-11T04:00:00Z")), first);

		respond(post("/api/v1/offline-admissions/" + fixture.first().admission() + "/close", fixture.master(),
				"{\"reason\":\"Left the course\"}"), 200);
		jdbc().update("update offline_admissions set closed_at = ? where id = ?", Timestamp.from(Instant.parse("2026-11-12T04:00:00Z")),
				fixture.first().admission());

		// After the close the office publishes a new version. The closed student still sees the one published before the close (R12),
		// and the new version's roll is matched, because the closed admission keeps its roll (A3).
		respond(upload("/api/v1/offline-classes/" + fixture.exam() + "/results", fixture.staff(), fixture.sheet(35, 21), "MCQ", null), 201);
		respond(post("/api/v1/offline-classes/" + fixture.exam() + "/results/publish", fixture.master(), null), 200);

		JsonNode closed = json.readTree(mockMvc.perform(get("/api/v1/me/admissions/" + fixture.first().admission() + "/results")
			.header("Authorization", "Bearer " + token(fixture.first().student()))).andExpect(status().isOk()).andReturn().getResponse()
			.getContentAsString());
		assertThat(closed.get("exams")).hasSize(1);
		assertThat(closed.get("exams").get(0).get("total").decimalValue()).isEqualByComparingTo("40");

		JsonNode open = json.readTree(mockMvc.perform(get("/api/v1/me/admissions/" + fixture.second().admission() + "/results")
			.header("Authorization", "Bearer " + token(fixture.second().student()))).andExpect(status().isOk()).andReturn().getResponse()
			.getContentAsString());
		assertThat(open.get("exams").get(0).get("total").decimalValue()).isEqualByComparingTo("20");
	}

	@Test
	void aFutureExamTakesNoResult() throws Exception {
		Fixture fixture = fixture();
		UUID future = examClass(fixture.staff(), fixture.batch(), "2026-11-20");

		JsonNode preview = respond(upload("/api/v1/offline-classes/" + future + "/results/preview", fixture.staff(),
				fixture.sheet(40, 19), "MCQ", null), 200);
		assertThat(preview.get("problems").toString()).contains("is on 2026-11-20");
		respond(upload("/api/v1/offline-classes/" + future + "/results", fixture.staff(), fixture.sheet(40, 19), "MCQ", null),
				400);
	}

	@Test
	void aHandCancelOfAnExamWithAResultIsRefused() throws Exception {
		Fixture fixture = publishedFixture(40, 19);
		publishedSheet(fixture);

		JsonNode refused = respond(patch("/api/v1/offline-classes/" + fixture.exam(), fixture.staff(), "{\"status\":\"CANCELLED\"}"), 409);
		assertThat(refused.get("code").asText()).isEqualTo("OFFLINE_CLASS_HAS_RESULT");
	}

	@Test
	void theUploadedFileComesBackAsItWas() throws Exception {
		Fixture fixture = fixture();
		byte[] sheet = fixture.sheet(40, 19);
		respond(upload("/api/v1/offline-classes/" + fixture.exam() + "/results", fixture.staff(), sheet, "MCQ", null), 201);
		UUID sheetId = UUID.fromString(jdbc().queryForObject("select id::text from offline_result_sheets where class_id = ?", String.class,
				fixture.exam()));

		MvcResult file = mockMvc.perform(get("/api/v1/offline-classes/" + fixture.exam() + "/results/" + sheetId + "/file")
			.header("Authorization", "Bearer " + fixture.staff())).andExpect(status().isOk()).andReturn();
		assertThat(file.getResponse().getContentAsByteArray()).isEqualTo(sheet);
	}

	@Test
	void concurrentSavesRunOneAfterTheOtherAndLeaveOneStagedSheet() throws Exception {
		Fixture fixture = fixture();
		List<Integer> statuses = concurrently(
				() -> upload("/api/v1/offline-classes/" + fixture.exam() + "/results", fixture.staff(), fixture.sheet(40, 19), "MCQ", null)
					.andReturn().getResponse().getStatus(),
				() -> upload("/api/v1/offline-classes/" + fixture.exam() + "/results", fixture.staff(), fixture.sheet(41, 18), "MCQ", null)
					.andReturn().getResponse().getStatus());

		assertThat(statuses).containsOnly(201);
		assertThat(jdbc().queryForObject("select count(*) from offline_result_sheets where class_id = ? and status = 'STAGED'", Long.class,
				fixture.exam())).isEqualTo(1L);
	}

	@Test
	void concurrentPublishesLetOnlyOneThrough() throws Exception {
		Fixture fixture = fixture();
		respond(upload("/api/v1/offline-classes/" + fixture.exam() + "/results", fixture.staff(), fixture.sheet(40, 19), "MCQ", null), 201);

		List<Integer> statuses = concurrently(
				() -> post("/api/v1/offline-classes/" + fixture.exam() + "/results/publish", fixture.master(), null).andReturn().getResponse()
					.getStatus(),
				() -> post("/api/v1/offline-classes/" + fixture.exam() + "/results/publish", fixture.master(), null).andReturn().getResponse()
					.getStatus());

		assertThat(statuses).containsExactlyInAnyOrder(200, 409);
		assertThat(jdbc().queryForObject("select count(*) from offline_result_sheets where class_id = ? and status = 'PUBLISHED'", Long.class,
				fixture.exam())).isEqualTo(1L);
	}

	@Test
	void publishTellsOnlyTheStudentsWithARowAndCountsThem() throws Exception {
		Fixture fixture = fixture();
		Student joinedLater = onboardedStudent(fixture.master(), fixture.batch(), roll(), "2026-11-12T04:00:00Z");
		JsonNode staged = respond(upload("/api/v1/offline-classes/" + fixture.exam() + "/results", fixture.staff(), fixture.sheet(40, 19),
				"MCQ", null), 201);
		assertThat(staged.get("staged").get("notifyCount").asInt()).isEqualTo(3);

		respond(post("/api/v1/offline-classes/" + fixture.exam() + "/results/publish", fixture.master(), null), 200);

		assertThat(publishedNotices(fixture.first().student())).isEqualTo(1L);
		assertThat(publishedNotices(fixture.absent().student())).isEqualTo(1L);
		assertThat(publishedNotices(joinedLater.student())).isZero();
		assertThat(getJson("/api/v1/offline-classes/" + fixture.exam() + "/results", fixture.staff()).get("published").get("notifyCount")
			.asInt()).isEqualTo(3);
	}

	@Test
	void warningsHoldOnlyWhatTheRowsCannotShowAndAZeroPaddedRollMatches() throws Exception {
		Fixture fixture = fixture();
		String plain = roll();
		Student padded = onboardedStudent(fixture.master(), fixture.batch(), "0" + plain, "2026-11-01T04:00:00Z");
		byte[] sheet = mcqSheet(List.of(List.of(plain, "40", "0")));

		JsonNode preview = respond(upload("/api/v1/offline-classes/" + fixture.exam() + "/results/preview", fixture.staff(), sheet, "MCQ",
				null), 200);

		assertThat(preview.get("problems")).isEmpty();
		assertThat(preview.get("counts").get("absent").asInt()).isEqualTo(3);
		// The absent students are shown from the rows, so only the zero-padded match is a warning.
		assertThat(preview.get("warnings")).hasSize(1);
		JsonNode warning = preview.get("warnings").get(0);
		assertThat(warning.get("code").asText()).isEqualTo("ROLL_MATCHED_ZERO_PADDED");
		assertThat(warning.get("params").get("roll").asText()).isEqualTo(plain);
		assertThat(warning.get("params").get("matched").asText()).isEqualTo("0" + plain);
		assertThat(warning.get("message").asText()).isEqualTo("Row 2: roll " + plain + " matched 0" + plain + ".");

		respond(upload("/api/v1/offline-classes/" + fixture.exam() + "/results", fixture.staff(), sheet, "MCQ", null), 201);
		assertThat(jdbc().queryForObject("select r.roll_no from offline_result_rows r join offline_result_sheets s on s.id = r.sheet_id"
				+ " where s.class_id = ? and s.status = 'STAGED' and r.admission_id = ?", String.class, fixture.exam(), padded.admission()))
			.isEqualTo("0" + plain);
	}

	@Test
	void aDateChangeOfAnExamWithAResultIsRefusedButTheTopicCanChange() throws Exception {
		Fixture fixture = publishedFixture(40, 19);

		JsonNode refused = respond(patch("/api/v1/offline-classes/" + fixture.exam(), fixture.staff(), "{\"heldOn\":\"2026-11-09\"}"), 409);
		assertThat(refused.get("code").asText()).isEqualTo("OFFLINE_CLASS_HAS_RESULT");
		assertThat(refused.get("message").asText()).isEqualTo("This exam has a result, so its date cannot change. Discard the staged result"
				+ " first, or keep the date.");
		respond(patch("/api/v1/offline-classes/" + fixture.exam(), fixture.staff(), "{\"heldOn\":\"2026-11-10\"}"), 200);

		JsonNode topic = respond(patch("/api/v1/offline-classes/" + fixture.exam(), fixture.staff(), "{\"topic\":\"Part 2\"}"), 200);
		assertThat(topic.get("topic").asText()).isEqualTo("Part 2");
		assertThat(topic.get("hasResult").asBoolean()).isTrue();
	}

	@Test
	void aNotTakenExamTakesNoResult() throws Exception {
		Fixture fixture = fixture();
		for (String status : List.of("NOT_TAKEN", "NOT_TAKEN_PENDING")) {
			jdbc().update("update offline_classes set status = ? where id = ?", status, fixture.exam());

			JsonNode preview = respond(upload("/api/v1/offline-classes/" + fixture.exam() + "/results/preview", fixture.staff(),
					fixture.sheet(40, 19), "MCQ", null), 200);
			assertThat(preview.get("problems")).hasSize(1);
			assertThat(preview.get("problems").get(0).get("code").asText()).isEqualTo("NOT_TAKEN");
			respond(upload("/api/v1/offline-classes/" + fixture.exam() + "/results", fixture.staff(), fixture.sheet(40, 19), "MCQ", null),
					400);
		}
		JsonNode exams = getJson("/api/v1/offline-batches/" + fixture.batch() + "/results", fixture.staff());
		assertThat(exams.get(0).get("status").asText()).isEqualTo("NOT_TAKEN_PENDING");
	}

	@Test
	void changedSinceSaveListsAClosedStudentAndANewRollAndPublishingStillGoesAhead() throws Exception {
		Fixture fixture = fixture();
		respond(upload("/api/v1/offline-classes/" + fixture.exam() + "/results", fixture.staff(), fixture.sheet(40, 19), "MCQ", null), 201);
		String newRoll = roll();
		jdbc().update("update offline_admissions set roll_no = ? where id = ?", newRoll, fixture.second().admission());
		respond(post("/api/v1/offline-admissions/" + fixture.first().admission() + "/close", fixture.master(), "{\"reason\":\"Left the course\"}"),
				200);

		JsonNode changed = getJson("/api/v1/offline-classes/" + fixture.exam() + "/results", fixture.staff()).get("staged")
			.get("changedSinceSave");
		assertThat(changed).hasSize(2);
		JsonNode rerolled = changedFor(changed, fixture.second().admission());
		assertThat(rerolled.get("oldRoll").asText()).isEqualTo(fixture.second().roll());
		assertThat(rerolled.get("newRoll").asText()).isEqualTo(newRoll);
		assertThat(rerolled.get("closed").asBoolean()).isFalse();
		JsonNode closed = changedFor(changed, fixture.first().admission());
		assertThat(closed.get("closed").asBoolean()).isTrue();
		assertThat(closed.get("name").asText()).isNotBlank();

		respond(post("/api/v1/offline-classes/" + fixture.exam() + "/results/publish", fixture.master(), null), 200);
	}

	@Test
	void problemsCarryTheirCodesAndTheSaveRefusalKeepsThem() throws Exception {
		Fixture fixture = fixture();
		String unknown = roll();
		byte[] sheet = mcqSheet(List.of(List.of(fixture.first().roll(), "40", "0"), List.of(fixture.first().roll(), "30", "0"),
				List.of(unknown, "10", "0")));

		JsonNode preview = respond(upload("/api/v1/offline-classes/" + fixture.exam() + "/results/preview", fixture.staff(), sheet, "MCQ",
				null), 200);
		JsonNode duplicate = preview.get("problems").get(0);
		assertThat(duplicate.get("code").asText()).isEqualTo("ROW_DUPLICATE_ROLL");
		assertThat(duplicate.get("params").get("first").asInt()).isEqualTo(2);
		JsonNode missing = preview.get("problems").get(1);
		assertThat(missing.get("code").asText()).isEqualTo("ROLL_NOT_IN_BATCH");
		assertThat(missing.get("params").get("roll").asText()).isEqualTo(unknown);

		JsonNode refused = respond(upload("/api/v1/offline-classes/" + fixture.exam() + "/results", fixture.staff(), sheet, "MCQ", null), 400);
		assertThat(refused.get("fieldErrors").get(0).get("code").asText()).isEqualTo("ROW_DUPLICATE_ROLL");
		assertThat(refused.get("fieldErrors").get(1).get("code").asText()).isEqualTo("ROLL_NOT_IN_BATCH");
	}

	@Test
	void twoOnboardingsWithTheSameRollOnTwoThreadsOneWinsAndTheOtherGetsARollError() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID batch = batch(master, "Race " + unique());
		UUID first = submittedApplication(batch, uniqueEmail(), validMobile());
		readyAdmission(first, batch, master);
		UUID second = submittedApplication(batch, uniqueEmail(), validMobile());
		readyAdmission(second, batch, master);
		String rollNo = roll();
		String body = "{\"rollNo\":\"" + rollNo + "\"}";

		List<MvcResult> answers = concurrently(
				() -> post("/api/v1/offline-admissions/" + first + "/onboard", master, body).andReturn(),
				() -> post("/api/v1/offline-admissions/" + second + "/onboard", master, body).andReturn());

		List<Integer> statuses = answers.stream().map(answer -> answer.getResponse().getStatus()).sorted().toList();
		assertThat(statuses).containsExactly(200, 400);
		MvcResult refused = answers.stream().filter(answer -> answer.getResponse().getStatus() == 400).findFirst().orElseThrow();
		JsonNode error = json.readTree(refused.getResponse().getContentAsString());
		assertThat(error.get("fieldErrors").get(0).get("field").asText()).isEqualTo("rollNo");
		assertThat(jdbc().queryForObject("select count(*) from offline_admissions where roll_no = ?", Long.class, rollNo)).isEqualTo(1L);
	}

	/** Runs two calls at the same moment, each on its own thread, and answers their results in the order given. */
	private static <T> List<T> concurrently(Callable<T> first, Callable<T> second) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			CountDownLatch start = new CountDownLatch(1);
			Future<T> a = pool.submit(() -> {
				start.await();
				return first.call();
			});
			Future<T> b = pool.submit(() -> {
				start.await();
				return second.call();
			});
			start.countDown();
			return List.of(a.get(60, TimeUnit.SECONDS), b.get(60, TimeUnit.SECONDS));
		}
		finally {
			pool.shutdownNow();
		}
	}

	private JsonNode getJson(String url, String token) throws Exception {
		return json.readTree(getAs(url, token).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}

	private long publishedNotices(UUID student) {
		return jdbc().queryForObject("select count(*) from student_notices where user_id = ? and kind = 'OFFLINE_RESULT_PUBLISHED'", Long.class,
				student);
	}

	/** The change listed for one admission. */
	private static JsonNode changedFor(JsonNode changed, UUID admission) {
		for (JsonNode item : changed) {
			if (admission.toString().equals(item.get("admissionId").asText())) {
				return item;
			}
		}
		throw new AssertionError("No change is listed for " + admission);
	}

	// ---- fixtures ---------------------------------------------------------------------------------------------------------

	/** A batch with one exam on the tenth, a master and a staff member, and three onboarded students: two sit the exam, one is absent. */
	private record Fixture(String master, String staff, UUID batch, UUID exam, Student first, Student second, Student absent) {

		/** The sheet of the three students: the first with {@code firstRight} correct answers, the second with {@code secondRight}. */
		byte[] sheet(int firstRight, int secondRight) {
			return mcqSheet(List.of(List.of(first.roll(), String.valueOf(firstRight), "0"),
					List.of(second.roll(), String.valueOf(secondRight), "4")));
		}

	}

	private record Student(UUID admission, UUID student, String roll) {
	}

	/** A batch with an exam and three students, none of whom has a result yet. */
	private Fixture fixture() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID batch = batch(master, "Results " + unique());
		UUID exam = examClass(staff, batch, "2026-11-10");
		Student first = onboardedStudent(master, batch, roll(), "2026-11-01T04:00:00Z");
		Student second = onboardedStudent(master, batch, roll(), "2026-11-01T04:00:00Z");
		Student absent = onboardedStudent(master, batch, roll(), "2026-11-01T04:00:00Z");
		return new Fixture(master, staff, batch, exam, first, second, absent);
	}

	/** The fixture, with its first sheet published. The first student's total is {@code firstRight}, the second's is what {@code secondRight} gives. */
	private Fixture publishedFixture(int firstRight, int secondRight) throws Exception {
		Fixture fixture = fixture();
		respond(upload("/api/v1/offline-classes/" + fixture.exam() + "/results", fixture.staff(), fixture.sheet(firstRight, secondRight),
				"MCQ", null), 201);
		respond(post("/api/v1/offline-classes/" + fixture.exam() + "/results/publish", fixture.master(), null), 200);
		return fixture;
	}

	private UUID publishedSheet(Fixture fixture) {
		return UUID.fromString(jdbc().queryForObject("select id::text from offline_result_sheets where class_id = ? and status = 'PUBLISHED'",
				String.class, fixture.exam()));
	}

	/** An applicant with no account, onboarded with a roll and dated before the exam, who then activates the account. */
	private Student onboardedStudent(String master, UUID batch, String roll, String onboardedOn) throws Exception {
		String email = uniqueEmail();
		UUID admission = submittedApplication(batch, email, validMobile());
		readyAdmission(admission, batch, master);
		respond(post("/api/v1/offline-admissions/" + admission + "/onboard", master, "{\"rollNo\":\"" + roll + "\"}"), 200);
		jdbc().update("update offline_admissions set onboarded_at = ? where id = ?", Timestamp.from(Instant.parse(onboardedOn)), admission);
		String code = codeFrom(email);
		respond(post("/api/v1/auth/activate", null, "{\"email\":\"" + email + "\",\"code\":\"" + code + "\",\"password\":\"password1\"}"),
				204);
		UUID student = jdbc().queryForObject("select id from users where email = ?", UUID.class, email);
		return new Student(admission, student, roll);
	}

	private UUID examClass(String staff, UUID batch, String heldOn) throws Exception {
		JsonNode created = respond(post("/api/v1/offline-batches/" + batch + "/classes", staff, "{\"heldOn\":\"" + heldOn
				+ "\",\"startsAt\":\"10:00\",\"endsAt\":\"12:00\",\"subject\":\"Constitution\",\"topic\":\"Part 1\",\"kind\":\"EXAM\"}"), 201);
		return UUID.fromString(created.get("id").asText());
	}

	/** A sheet of MCQ rows: each student is a roll, the right answers, and the wrong answers. Full marks 50, deducted 0.25. */
	private static byte[] mcqSheet(List<List<String>> students) {
		List<List<?>> rows = new ArrayList<>();
		rows.add(List.of("Roll No", "Number", "Right Answer", "Wrong Answer", "Negative(Per Q.)"));
		for (List<String> student : students) {
			rows.add(List.of(student.get(0), "50", student.get(1), student.get(2), "0.25"));
		}
		return OfflineResultFileTest.workbook(rows.toArray(new List<?>[0]));
	}

	// ---- requests ---------------------------------------------------------------------------------------------------------

	private JdbcTemplate jdbc() {
		return new JdbcTemplate(dataSource);
	}

	private ResultActions post(String url, String token, String body) throws Exception {
		var request = MockMvcRequestBuilders.post(url);
		if (token != null) {
			request = request.header("Authorization", "Bearer " + token);
		}
		if (body != null) {
			request = request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mockMvc.perform(request);
	}

	private ResultActions patch(String url, String token, String body) throws Exception {
		return mockMvc.perform(MockMvcRequestBuilders.patch(url).header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions delete(String url, String token) throws Exception {
		return mockMvc.perform(MockMvcRequestBuilders.delete(url).header("Authorization", "Bearer " + token));
	}

	private ResultActions getAs(String url, String token) throws Exception {
		return mockMvc.perform(get(url).header("Authorization", "Bearer " + token));
	}

	private ResultActions upload(String url, String token, byte[] bytes, String examType, String passMark) throws Exception {
		MockMultipartHttpServletRequestBuilder request = multipart(HttpMethod.POST, url)
			.file(new MockMultipartFile("file", "result-sheet.xlsx", XLSX, bytes))
			.param("examType", examType)
			.header("Authorization", "Bearer " + token);
		if (passMark != null) {
			request = request.param("passMark", passMark);
		}
		return mockMvc.perform(request);
	}

	private JsonNode respond(ResultActions actions, int expected) throws Exception {
		MvcResult result = actions.andExpect(status().is(expected)).andReturn();
		String text = result.getResponse().getContentAsString();
		return text.isBlank() ? json.createObjectNode() : json.readTree(text);
	}

	private JsonNode respond(org.springframework.test.web.servlet.RequestBuilder request, int expected) throws Exception {
		return respond(mockMvc.perform(request), expected);
	}

	private String codeFrom(String email) {
		String captured = emails.latestRendered(email);
		assertThat(captured).isNotNull();
		return captured.substring(0, captured.indexOf('|'));
	}

	/** An applicant with no account has sent the form, with its photo, and the office has it under review. */
	private UUID submittedApplication(UUID batch, String email, String mobile) throws Exception {
		JsonNode started = respond(post("/api/v1/public/offline-applications", null,
				"{\"batchId\":\"" + batch + "\",\"email\":\"" + email + "\",\"mobile\":\"" + mobile + "\"}"), 201);
		String token = started.get("token").asText();
		respond(put("/api/v1/public/offline-applications/" + token + "/form", fullForm(email, mobile)), 200);
		respond(multipart(HttpMethod.PUT, "/api/v1/public/offline-applications/" + token + "/photo")
			.file(new MockMultipartFile("file", "me.png", "image/png", png())), 200);
		JsonNode sent = respond(post("/api/v1/public/offline-applications/" + token + "/submit", null, null), 200);
		assertThat(sent.get("state").asText()).isEqualTo("SUBMITTED");
		return UUID.fromString(started.get("admission").get("id").asText());
	}

	/** Setup, one exam, ready: the staff steps before the Master Admin onboards. */
	private void readyAdmission(UUID admission, UUID batch, String master) throws Exception {
		respond(patch("/api/v1/offline-admissions/" + admission + "/setup", master, "{\"batchId\":\"" + batch + "\"}"), 200);
		respond(post("/api/v1/offline-admissions/" + admission + "/items", master, "{\"scope\":\"EXAM\",\"examId\":\"" + publishedExam()
				+ "\"}"), 200);
		JsonNode ready = respond(post("/api/v1/offline-admissions/" + admission + "/ready", master, null), 200);
		assertThat(ready.get("state").asText()).isEqualTo("READY");
	}

	private ResultActions put(String url, String body) throws Exception {
		return mockMvc.perform(MockMvcRequestBuilders.put(url).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private UUID batch(String token, String name) throws Exception {
		MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/offline-batches").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"nameEn":"%s","nameBn":"ব্যাচ","endsOn":"2027-04-30","courseFee":3000}
					""".formatted(name)))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private UUID publishedExam() {
		UUID id = UUID.randomUUID();
		UUID category = UUID.randomUUID();
		jdbc().update("insert into live_exam_categories (id, title_en, title_bn, position, created_at, updated_at)"
				+ " values (?, 'Category', 'বিভাগ', 0, now(), now())", category);
		jdbc().update("""
				insert into live_exams (id, module_id, category_id, exam_type, title_en, title_bn, description_en, description_bn,
				  rules_en, rules_bn, paper_language, live_at, ends_at, duration_minutes, mode, status, finally_saved, pass_percent,
				  answers_changeable, is_free, access_days, published_at, created_at, updated_at)
				values (?, null, ?, 'MCQ', ?, 'পরীক্ষা', '', '', '', '', 'BN', ?, ?, 30, 'LIVE', 'PUBLISHED', true, 50, true, false, 30, ?,
				  now(), now())
				""", id, category, "Exam " + id, Timestamp.from(Instant.parse("2026-10-01T04:00:00Z")),
				Timestamp.from(Instant.parse("2027-12-31T04:00:00Z")), Timestamp.from(Instant.parse("2026-09-01T04:00:00Z")));
		return id;
	}

	private UUID seed(String role, String name) {
		String email = uniqueEmail();
		String phone = validMobile();
		String stored = phone.startsWith("01") ? "+880" + phone.substring(1) : phone;
		UUID id = UUID.randomUUID();
		jdbc().update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, stored, email, passwords.encode("password1"), role);
		jdbc().update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, ?)", id, name);
		return id;
	}

	/** A bearer token for the user, from a login with the test password. */
	private String token(UUID userId) throws Exception {
		String phone = jdbc().queryForObject("select phone from users where id = ?", String.class, userId);
		MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login").header("X-Client-Type", "mobile")
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

	/** The form every required field of which is filled in, with the given email and mobile. */
	private static String fullForm(String email, String mobile) {
		return "{\"fullName\":\"Rahim Uddin\",\"fatherName\":\"Karim Uddin\",\"motherName\":\"Amena Begum\","
				+ "\"dateOfBirth\":\"2004-05-10\",\"email\":\"" + email + "\",\"gender\":\"MALE\",\"university\":\"Dhaka University\","
				+ "\"semesterBatch\":\"2024-25\",\"nid\":\"1234567890123\",\"mobile\":\"" + mobile + "\","
				+ "\"emergencyMobile\":\"01812345678\",\"hallMess\":\"Shahid Minar Hall\",\"presentAddress\":\"House 1, Dhaka\","
				+ "\"permanentAddress\":\"Village, District\"}";
	}

	private static String uniqueEmail() {
		return "offline-res-" + UUID.randomUUID() + "@example.com";
	}

	private static String unique() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	/** A roll number nobody has yet: nine random digits. */
	private static String roll() {
		return String.valueOf(ThreadLocalRandom.current().nextLong(100_000_000L, 999_999_999L));
	}

	/** An 11-digit Bangladesh mobile starting 017. */
	private static String validMobile() {
		return "017" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
	}

	private static byte[] png() throws java.io.IOException {
		java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(64, 36, java.awt.image.BufferedImage.TYPE_INT_RGB);
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		javax.imageio.ImageIO.write(image, "png", out);
		return out.toByteArray();
	}

	@TestConfiguration
	static class ClockConfig {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(NOW);
		}

	}

}
