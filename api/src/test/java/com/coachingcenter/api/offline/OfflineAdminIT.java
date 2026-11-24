package com.coachingcenter.api.offline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import javax.imageio.ImageIO;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.context.TestPropertySource;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The staff and Master Admin side of an offline admission (plan sections 4.2 to 4.4, 5 and 10.2, Gate 3): the queue and detail, the
 * setup, exams and packages, sending back and ready, onboarding and its grants and schedule, the terms after onboarding, closing, the
 * signed copy and the batches. Every test makes its own students, exams, packages and batches with random ids, so the class can run
 * again on the same database. The clock is fixed at 10:00 in Dhaka on 20 October 2026 and every test starts from that instant.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, OfflineAdminIT.ClockConfig.class })
@TestPropertySource(properties = "app.offline-admission.enabled=true")
class OfflineAdminIT extends PostgresIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-10-20T04:00:00Z");

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
	void fixTheClock() {
		clock.set(NOW);
	}

	// ---- roles and states ---------------------------------------------------------------------------------------------------

	@Test
	void staffSetsUpMarksReadyAndSendsBackButCannotOnboardChangeTermsOrClose() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Master");
		UUID staff = seed("SYSTEM_ADMIN", "Staff");
		String staffToken = token(staff);
		String masterToken = token(master);
		UUID studentId = seed("STUDENT", "Rahim");
		UUID admission = admission(studentId, master, "SUBMITTED");
		UUID batch = batch(masterToken, "2027-02-15", "3000");
		UUID exam = publishedExam();

		respond(patch("/api/v1/offline-admissions/" + admission + "/setup", staffToken, "{\"batchId\":\"" + batch + "\"}"), 200);
		JsonNode item = respond(post("/api/v1/offline-admissions/" + admission + "/items", staffToken,
				"{\"scope\":\"EXAM\",\"examId\":\"" + exam + "\"}"), 200);
		assertThat(item.get("alreadyHeld").asBoolean()).isFalse();
		JsonNode ready = respond(post("/api/v1/offline-admissions/" + admission + "/ready", staffToken, null), 200);
		assertThat(ready.get("state").asText()).isEqualTo("READY");
		assertThat(notificationCount("ADMISSION_READY", master)).isEqualTo(1L);

		// The setup is locked while the admission is ready (C16).
		JsonNode locked = respond(patch("/api/v1/offline-admissions/" + admission + "/setup", staffToken, "{\"courseFee\":\"4000\"}"),
				409);
		assertThat(locked.get("code").asText()).isEqualTo("ADMISSION_STATE");

		// Staff cannot onboard, change the terms or close (plan 4.6).
		respond(post("/api/v1/offline-admissions/" + admission + "/onboard", staffToken, rollBody()), 403);
		respond(patch("/api/v1/offline-admissions/" + admission + "/terms", staffToken, "{\"courseFee\":\"3500\"}"), 403);
		respond(post("/api/v1/offline-admissions/" + admission + "/close", staffToken, "{\"reason\":\"No\"}"), 403);

		// The Master Admin takes it back to review, and the person who marked it ready is told (C16).
		respond(post("/api/v1/offline-admissions/" + admission + "/back-to-review", masterToken, null), 200);
		assertThat(notificationCount("ADMISSION_RETURNED", staff)).isEqualTo(1L);

		// Staff send it back with a reason; the student gets one notice and one mail carrying the reason.
		JsonNode sent = respond(post("/api/v1/offline-admissions/" + admission + "/request-changes", staffToken,
				"{\"reason\":\"NID is short a digit\"}"), 200);
		assertThat(sent.get("state").asText()).isEqualTo("CHANGES_REQUESTED");
		assertThat(sent.get("changesReason").asText()).isEqualTo("NID is short a digit");
		assertThat(studentNoticeCount(studentId, "ADMISSION_CHANGES_REQUESTED")).isEqualTo(1L);
		assertThat(mailCount(studentId, "ADMISSION_CHANGES_REQUESTED")).isEqualTo(1L);
		assertThat(jdbc().queryForObject("select count(*) from offline_admission_submissions where admission_id = ?"
				+ " and returned_reason = ?", Long.class, admission, "NID is short a digit")).isEqualTo(1L);
	}

	@Test
	void instructorIsRefusedOnEveryAdmissionRoute() throws Exception {
		String instructor = token(seed("INSTRUCTOR", "Instructor"));
		UUID admission = admission(seed("STUDENT", "Some Student"), seed("MASTER_ADMIN", "Desk"), "SUBMITTED");
		String base = "/api/v1/offline-admissions/" + admission;

		mockMvc.perform(get("/api/v1/offline-admissions").header("Authorization", "Bearer " + instructor))
			.andExpect(status().isForbidden());
		mockMvc.perform(get(base).header("Authorization", "Bearer " + instructor)).andExpect(status().isForbidden());
		respond(patch(base + "/setup", instructor, "{}"), 403);
		respond(post(base + "/items", instructor, "{\"scope\":\"EXAM\"}"), 403);
		respond(post(base + "/ready", instructor, null), 403);
		respond(post(base + "/back-to-review", instructor, null), 403);
		respond(post(base + "/request-changes", instructor, "{\"reason\":\"x\"}"), 403);
		respond(post(base + "/onboard", instructor, rollBody()), 403);
		respond(patch(base + "/terms", instructor, "{\"courseFee\":\"1\"}"), 403);
		respond(post(base + "/close", instructor, "{\"reason\":\"x\"}"), 403);
	}

	@Test
	void studentIsRefusedOnStaffRoutes() throws Exception {
		String student = token(seed("STUDENT", "Plain Student"));

		mockMvc.perform(get("/api/v1/offline-admissions").header("Authorization", "Bearer " + student))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/offline-batches").header("Authorization", "Bearer " + student))
			.andExpect(status().isForbidden());
	}

	// ---- queue and detail ---------------------------------------------------------------------------------------------------

	@Test
	void queueFindsTheAdmissionByNameAndTheDetailShowsTheNid() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Master");
		String name = "Queue Test " + UUID.randomUUID();
		UUID admission = admission(seed("STUDENT", name), master, "SUBMITTED");
		jdbc().update("update offline_admissions set full_name = ? where id = ?", name, admission);
		String token = token(master);

		mockMvc.perform(get("/api/v1/offline-admissions").param("status", "SUBMITTED").param("q", name)
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].id").value(admission.toString()))
			.andExpect(jsonPath("$.content[0].flags").isEmpty());

		mockMvc.perform(get("/api/v1/offline-admissions/" + admission).header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.form.nid").value("1234567890123"))
			.andExpect(jsonPath("$.form.dateOfBirth").value("2004-05-10"));
	}

	// ---- ready ---------------------------------------------------------------------------------------------------------------

	@Test
	void readyNamesEachMissingPartAndChangesNothing() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Master");
		String token = token(master);
		UUID bare = admission(seed("STUDENT", "Bare"), master, "SUBMITTED");

		JsonNode refusal = respond(post("/api/v1/offline-admissions/" + bare + "/ready", token, null), 400);
		assertThat(refusal.get("code").asText()).isEqualTo("VALIDATION_FAILED");
		assertThat(fieldNames(refusal)).contains("batchId", "courseFee", "installments", "courseEndsOn");
		assertThat(fieldNames(refusal)).doesNotContain("items");
		assertThat(jdbc().queryForObject("select status from offline_admissions where id = ?", String.class, bare))
			.isEqualTo("SUBMITTED");

		UUID noNid = admission(seed("STUDENT", "No Nid"), master, "SUBMITTED");
		jdbc().update("update offline_admissions set nid = null where id = ?", noNid);
		// 16 Nov: the NID is optional, so a missing one is not among the parts Ready asks for.
		JsonNode missingNid = respond(post("/api/v1/offline-admissions/" + noNid + "/ready", token, null), 400);
		assertThat(fieldNames(missingNid)).doesNotContain("nid");
	}

	// ---- setup ---------------------------------------------------------------------------------------------------------------

	@Test
	void setupFillsTheFeeAndTheEndFromTheBatchAndTheFirstDueOneMonthAfterAdmission() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Master");
		String token = token(master);
		UUID batch = batch(token, "2027-02-15", "3000");
		UUID admission = admission(seed("STUDENT", "Setup Student"), master, "SUBMITTED");

		JsonNode setup = respond(patch("/api/v1/offline-admissions/" + admission + "/setup", token,
				"{\"batchId\":\"" + batch + "\"}"), 200).get("setup");
		assertThat(setup.get("admittedOn").asText()).isEqualTo("2026-10-20");
		assertThat(setup.get("firstDueOn").asText()).isEqualTo("2026-11-20");
		assertThat(setup.get("courseFee").decimalValue()).isEqualByComparingTo("3000");
		assertThat(setup.get("courseEndsOn").asText()).isEqualTo("2027-02-15");
		// The batch's four months (20 October to 15 February), cut to the three that fit from 20 November: 1,000 each.
		assertThat(setup.get("installments").asInt()).isEqualTo(3);
		assertThat(setup.get("installmentPlan")).hasSize(3);
		assertThat(setup.get("installmentPlan").get(2).get("dueOn").asText()).isEqualTo("2027-01-20");
		assertThat(setup.get("installmentPlan").get(2).get("amount").decimalValue()).isEqualByComparingTo("1000");

		// Admitted on 31 January: one month later is the last day of February, not 3 March (plan side effect 18).
		UUID longerBatch = batch(token, "2027-06-30", "3000");
		UUID other = admission(seed("STUDENT", "January Student"), master, "SUBMITTED");
		JsonNode january = respond(patch("/api/v1/offline-admissions/" + other + "/setup", token,
				"{\"batchId\":\"" + longerBatch + "\",\"admittedOn\":\"2027-01-31\"}"), 200).get("setup");
		assertThat(january.get("firstDueOn").asText()).isEqualTo("2027-02-28");
	}

	@Test
	void anAdmittedDateSentEmptyIsLeftForTheAdminAndNotGuessed() throws Exception {
		// 18 Nov: the setup screen sends the admitted date empty until the admin enters it. Then no date is filled in, nor the first due
		// date, and the admission cannot be marked ready; once the date is entered, the first due date follows a month later.
		UUID master = seed("MASTER_ADMIN", "Master");
		String token = token(master);
		UUID batch = batch(token, "2027-02-15", "3000");
		UUID admission = admission(seed("STUDENT", "Empty Date"), master, "SUBMITTED");
		String url = "/api/v1/offline-admissions/" + admission + "/setup";

		JsonNode setup = respond(patch(url, token, "{\"batchId\":\"" + batch + "\",\"admittedOn\":null,\"firstDueOn\":null}"), 200).get("setup");
		assertThat(setup.hasNonNull("admittedOn")).isFalse();
		assertThat(setup.hasNonNull("firstDueOn")).isFalse();
		assertThat(setup.get("courseFee").decimalValue()).isEqualByComparingTo("3000");
		JsonNode refused = respond(post("/api/v1/offline-admissions/" + admission + "/ready", token, null), 400);
		assertThat(fieldNames(refused)).contains("admittedOn");

		JsonNode entered = respond(patch(url, token, "{\"admittedOn\":\"2026-10-25\",\"firstDueOn\":null}"), 200).get("setup");
		assertThat(entered.get("admittedOn").asText()).isEqualTo("2026-10-25");
		assertThat(entered.get("firstDueOn").asText()).isEqualTo("2026-11-25");
	}

	@Test
	void aBatchGivesNoExamsOrPackagesAtSetupAndOnlyTheStudentsOwnItemsCount() throws Exception {
		// P17: the batch carries no default items, so setting it copies nothing onto the admission. Exams and packages are optional
		// for the ready check; the student's own items, when chosen here, still count.
		UUID master = seed("MASTER_ADMIN", "Master");
		String token = token(master);
		UUID batch = batch(token, "2027-02-15", "3000");
		UUID exam = publishedExam();
		UUID admission = admission(seed("STUDENT", "Own Items"), master, "SUBMITTED");

		respond(patch("/api/v1/offline-admissions/" + admission + "/setup", token, "{\"batchId\":\"" + batch + "\"}"), 200);
		assertThat(jdbc().queryForObject("select count(*) from offline_admission_items where admission_id = ?", Long.class, admission))
			.isZero();
		JsonNode added = respond(post("/api/v1/offline-admissions/" + admission + "/items", token,
				"{\"scope\":\"EXAM\",\"examId\":\"" + exam + "\"}"), 200);
		assertThat(added.get("admission").get("items")).hasSize(1);
		JsonNode ready = respond(post("/api/v1/offline-admissions/" + admission + "/ready", token, null), 200);
		assertThat(ready.get("state").asText()).isEqualTo("READY");
	}

	@Test
	void setupRefusesAnEndBeforeTheFirstDueAndAnArchivedBatch() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Master");
		String token = token(master);
		UUID admission = admission(seed("STUDENT", "Dates Student"), master, "SUBMITTED");

		JsonNode badDates = respond(patch("/api/v1/offline-admissions/" + admission + "/setup", token,
				"{\"admittedOn\":\"2026-11-01\",\"firstDueOn\":\"2026-12-01\",\"courseEndsOn\":\"2026-11-15\"}"), 400);
		assertThat(fieldNames(badDates)).contains("courseEndsOn");

		UUID batch = batch(token, "2027-02-15", "3000");
		respond(post("/api/v1/offline-batches/" + batch + "/archive", token, null), 200);
		JsonNode archived = respond(patch("/api/v1/offline-admissions/" + admission + "/setup", token,
				"{\"batchId\":\"" + batch + "\"}"), 400);
		assertThat(fieldNames(archived)).contains("batchId");
	}

	// ---- exams and packages ----------------------------------------------------------------------------------------------------

	@Test
	void anItemTheStudentAlreadyHoldsIsFlaggedAndAnUnpublishedExamIsRefused() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Master");
		String token = token(master);
		UUID student = seed("STUDENT", "Overlap Student");
		UUID admission = admission(student, master, "SUBMITTED");
		UUID held = publishedExam();
		manualExamGrant(student, held);

		JsonNode added = respond(post("/api/v1/offline-admissions/" + admission + "/items", token,
				"{\"scope\":\"EXAM\",\"examId\":\"" + held + "\"}"), 200);
		assertThat(added.get("alreadyHeld").asBoolean()).isTrue();
		assertThat(added.get("admission").get("items")).hasSize(1);

		JsonNode refusal = respond(post("/api/v1/offline-admissions/" + admission + "/items", token,
				"{\"scope\":\"EXAM\",\"examId\":\"" + unpublishedExam() + "\"}"), 400);
		assertThat(fieldNames(refusal)).contains("examId");
	}

	// ---- onboarding -----------------------------------------------------------------------------------------------------------

	@Test
	void onboardingMakesOneCappedGrantPerItemAndTheFeeScheduleWithOneNoticeAndOneMail() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Master");
		UUID staff = seed("SYSTEM_ADMIN", "Staff");
		String masterToken = token(master);
		UUID student = seed("STUDENT", "Onboard Student");
		UUID batch = batch(masterToken, "2027-04-30", "12000");
		UUID admission = admission(student, master, "SUBMITTED");
		UUID exam = publishedExam();
		UUID module = publishedModule();
		respond(patch("/api/v1/offline-admissions/" + admission + "/setup", masterToken,
				"{\"batchId\":\"" + batch + "\",\"admittedOn\":\"2026-12-31\"}"), 200);
		respond(post("/api/v1/offline-admissions/" + admission + "/items", masterToken, "{\"scope\":\"EXAM\",\"examId\":\"" + exam + "\"}"),
				200);
		respond(post("/api/v1/offline-admissions/" + admission + "/items", masterToken,
				"{\"scope\":\"MODULE\",\"moduleId\":\"" + module + "\"}"), 200);
		// Staff mark it ready, so the Master Admin onboards it and staff are told.
		respond(post("/api/v1/offline-admissions/" + admission + "/ready", token(staff), null), 200);

		JsonNode onboarded = respond(post("/api/v1/offline-admissions/" + admission + "/onboard", masterToken, rollBody()), 200);
		assertThat(onboarded.get("state").asText()).isEqualTo("ONBOARDED");

		// One OFFLINE grant per item, capped at the end of 30 April in Dhaka: 18:00 UTC on 30 April.
		Instant cap = Instant.parse("2027-04-30T18:00:00Z");
		assertThat(jdbc().queryForObject("select count(*) from live_exam_grants where student_id = ? and source = 'OFFLINE'",
				Long.class, student)).isEqualTo(2L);
		List<Timestamp> caps = jdbc().queryForList("select access_ends_at from live_exam_grants where student_id = ?"
				+ " and source = 'OFFLINE'", Timestamp.class, student);
		assertThat(caps).hasSize(2).allSatisfy(ends -> assertThat(ends.toInstant()).isEqualTo(cap));
		assertThat(jdbc().queryForObject("select count(*) from offline_admission_items where admission_id = ?"
				+ " and grant_id is not null", Long.class, admission)).isEqualTo(2L);

		// The schedule: the 31st falls on the last day of February 2027 (side effect 18).
		List<String> dues = jdbc().queryForList("select due_on::text from offline_fee_dues where admission_id = ? order by seq",
				String.class, admission);
		assertThat(dues).containsExactly("2027-01-31", "2027-02-28", "2027-03-31", "2027-04-30");
		assertThat(jdbc().queryForObject("select amount from offline_fee_dues where admission_id = ? and seq = 1", BigDecimal.class,
				admission)).isEqualByComparingTo("3000");

		// One approval notice and one mail; no per-item access notice (C5).
		assertThat(studentNoticeCount(student, "ADMISSION_APPROVED")).isEqualTo(1L);
		assertThat(studentNoticeCount(student, "ACCESS_GRANTED")).isZero();
		assertThat(mailCount(student, "ADMISSION_APPROVED")).isEqualTo(1L);
		assertThat(jdbc().queryForObject("select payload from exam_outbound_mail where user_id = ? and kind = 'ADMISSION_APPROVED'",
				String.class, student)).contains("2027-04-30");
		assertThat(jdbc().queryForObject("select full_name from student_profiles where user_id = ?", String.class, student))
			.isEqualTo("Rahim Uddin");
		assertThat(notificationCount("ADMISSION_ONBOARDED", staff)).isEqualTo(1L);
	}

	@Test
	void onboardingRefusesAnItemTheStudentAlreadyHoldsNamingItAndLeavesNoRowsChanged() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Master");
		String masterToken = token(master);
		UUID student = seed("STUDENT", "Held Student");
		UUID admission = admission(student, master, "SUBMITTED");
		UUID exam = publishedExam();
		manualExamGrant(student, exam);
		respond(post("/api/v1/offline-admissions/" + admission + "/items", masterToken, "{\"scope\":\"MODULE\",\"moduleId\":\""
				+ publishedModule() + "\"}"), 200);
		respond(post("/api/v1/offline-admissions/" + admission + "/items", masterToken, "{\"scope\":\"EXAM\",\"examId\":\"" + exam + "\"}"),
				200);
		respond(patch("/api/v1/offline-admissions/" + admission + "/setup", masterToken,
				"{\"batchId\":\"" + batch(masterToken, "2027-04-30", "3000") + "\"}"), 200);
		respond(post("/api/v1/offline-admissions/" + admission + "/ready", masterToken, null), 200);

		JsonNode refusal = respond(post("/api/v1/offline-admissions/" + admission + "/onboard", masterToken, rollBody()), 409);
		assertThat(refusal.get("code").asText()).isEqualTo("GRANT_ALREADY_ACTIVE");
		assertThat(refusal.get("message").asText()).contains(titleOf("live_exams", exam));

		// Nothing was written: the admission is still ready, and the package grant made before the refusal was rolled back.
		assertThat(jdbc().queryForObject("select status from offline_admissions where id = ?", String.class, admission))
			.isEqualTo("READY");
		assertThat(jdbc().queryForObject("select count(*) from live_exam_grants where student_id = ? and source = 'OFFLINE'",
				Long.class, student)).isZero();
		assertThat(jdbc().queryForObject("select count(*) from offline_fee_dues where admission_id = ?", Long.class, admission))
			.isZero();
		assertThat(jdbc().queryForObject("select count(*) from offline_admission_items where admission_id = ?"
				+ " and grant_id is not null", Long.class, admission)).isZero();
		assertThat(studentNoticeCount(student, "ADMISSION_APPROVED")).isZero();
	}

	@Test
	void theFeeScheduleFallsOnTheLastDayOfShortMonthsAndTheCapIsTheEndOfTheDhakaDay() {
		assertThat(OfflineFeeSchedule.dueDates(LocalDate.parse("2027-01-31"), LocalDate.parse("2027-04-30")))
			.containsExactly(LocalDate.parse("2027-01-31"), LocalDate.parse("2027-02-28"), LocalDate.parse("2027-03-31"),
					LocalDate.parse("2027-04-30"));
		assertThat(OfflineFeeSchedule.dueDates(LocalDate.parse("2028-01-31"), LocalDate.parse("2028-03-31")))
			.containsExactly(LocalDate.parse("2028-01-31"), LocalDate.parse("2028-02-29"), LocalDate.parse("2028-03-31"));
		assertThat(OfflineFeeSchedule.dueDates(LocalDate.parse("2027-01-15"), LocalDate.parse("2027-01-14"))).isEmpty();
		assertThat(OfflineFeeSchedule.endOfDay(LocalDate.parse("2027-04-30"))).isEqualTo(Instant.parse("2027-04-30T18:00:00Z"));
	}

	@Test
	void theCourseFeeIsSplitIntoWholeTakaWithTheRestOnTheLastInstalment() {
		assertThat(OfflineFeeSchedule.split(new BigDecimal("7000"), 6)).extracting(BigDecimal::toPlainString)
			.containsExactly("1166.00", "1166.00", "1166.00", "1166.00", "1166.00", "1170.00");
		assertThat(OfflineFeeSchedule.split(new BigDecimal("9000"), 7)).extracting(BigDecimal::toPlainString)
			.containsExactly("1285.00", "1285.00", "1285.00", "1285.00", "1285.00", "1285.00", "1290.00");
		assertThat(OfflineFeeSchedule.split(new BigDecimal("1000.50"), 3)).extracting(BigDecimal::toPlainString)
			.containsExactly("333.00", "333.00", "334.50");
		assertThat(OfflineFeeSchedule.split(new BigDecimal("9000"), 1)).extracting(BigDecimal::toPlainString).containsExactly("9000.00");
		assertThat(OfflineFeeSchedule.installmentDates(LocalDate.parse("2027-01-31"), 3))
			.containsExactly(LocalDate.parse("2027-01-31"), LocalDate.parse("2027-02-28"), LocalDate.parse("2027-03-31"));
		// The batch's default: as many months as fit, at least one.
		assertThat(OfflineFeeSchedule.fittingInstallments(LocalDate.parse("2026-11-01"), LocalDate.parse("2027-05-31"))).isEqualTo(7);
		assertThat(OfflineFeeSchedule.fittingInstallments(LocalDate.parse("2027-06-01"), LocalDate.parse("2027-05-31"))).isEqualTo(1);
	}

	// ---- instalments set one by one, and payments before onboarding (instalment-plan-and-early-payments.md) --------------------

	private static String plan(String... rows) {
		StringBuilder body = new StringBuilder("{\"installmentPlan\":[");
		for (int index = 0; index < rows.length; index += 2) {
			body.append(index == 0 ? "" : ",").append("{\"dueOn\":\"").append(rows[index]).append("\",\"amount\":\"").append(rows[index + 1])
				.append("\"}");
		}
		return body.append("]}").toString();
	}

	@Test
	void theAdminSetsEachInstalmentAndTheCourseFeeIsTheirSum() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Master");
		String token = token(master);
		UUID batch = batch(token, "2027-04-30", "12000");
		UUID admission = admission(seed("STUDENT", "Plan Student"), master, "SUBMITTED");
		String setup = "/api/v1/offline-admissions/" + admission + "/setup";
		respond(patch(setup, token, "{\"batchId\":\"" + batch + "\",\"admittedOn\":\"2026-12-31\"}"), 200);

		// E1, E2: any dates and amounts; the course fee is their sum, the count their number, the first due date the first row's.
		JsonNode own = respond(patch(setup, token, plan("2027-01-10", "4000", "2027-02-01", "2000", "2027-03-15", "1000")), 200).get("setup");
		assertThat(own.get("customPlan").asBoolean()).isTrue();
		assertThat(own.get("courseFee").decimalValue()).isEqualByComparingTo("7000");
		assertThat(own.get("installments").asInt()).isEqualTo(3);
		assertThat(own.get("firstDueOn").asText()).isEqualTo("2027-01-10");
		assertThat(own.get("installmentPlan").get(1).get("amount").decimalValue()).isEqualByComparingTo("2000");

		// Out of order, after the course end, or below one taka: refused on the plan, and the saved plan stays.
		assertThat(fieldNames(respond(patch(setup, token, plan("2027-02-01", "4000", "2027-01-10", "3000")), 400)))
			.contains("installmentPlan");
		assertThat(fieldNames(respond(patch(setup, token, plan("2027-01-10", "4000", "2027-05-01", "3000")), 400)))
			.contains("installmentPlan");
		assertThat(fieldNames(respond(patch(setup, token, plan("2027-01-10", "0")), 400))).contains("installmentPlan");

		// A fee and a count without rows is the even split again (Spread evenly).
		JsonNode even = respond(patch(setup, token, "{\"courseFee\":\"9000\",\"installments\":3}"), 200).get("setup");
		assertThat(even.get("customPlan").asBoolean()).isFalse();
		assertThat(even.get("installmentPlan").get(2).get("amount").decimalValue()).isEqualByComparingTo("3000");

		// Back to the admin's rows; onboarding writes exactly those dues.
		respond(patch(setup, token, plan("2027-01-10", "4000", "2027-02-01", "2000", "2027-03-15", "1000")), 200);
		respond(post("/api/v1/offline-admissions/" + admission + "/items", token, "{\"scope\":\"EXAM\",\"examId\":\"" + publishedExam() + "\"}"),
				200);
		respond(post("/api/v1/offline-admissions/" + admission + "/ready", token, null), 200);
		respond(post("/api/v1/offline-admissions/" + admission + "/onboard", token, rollBody()), 200);
		assertThat(unpaidDueDates(admission)).containsExactly("2027-01-10", "2027-02-01", "2027-03-15");
		assertThat(unpaidDueAmounts(admission)).containsExactly("4000.00", "2000.00", "1000.00");
	}

	@Test
	void aPaymentCanBeTakenBeforeOnboardingAndIsMatchedToTheDuesAtOnboarding() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Master");
		String token = token(master);
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID batch = batch(token, "2027-04-30", "12000");
		UUID admission = admission(seed("STUDENT", "Early Payer"), master, "SUBMITTED");
		String base = "/api/v1/offline-admissions/" + admission;

		// No batch or plan yet: nothing to pay against.
		respond(post(base + "/payments", token, payment("1000", "EARLY-0")), 409);

		respond(patch(base + "/setup", token, "{\"batchId\":\"" + batch + "\",\"admittedOn\":\"2026-10-20\"}"), 200);
		respond(patch(base + "/setup", token, plan("2026-11-20", "4000", "2026-12-20", "2000", "2027-01-20", "1000")), 200);

		// E3: the Master Admin's payment counts at once and gets its receipt (E4); a staff payment waits.
		JsonNode paid = respond(post(base + "/payments", token, payment("4500", "EARLY-1")), 201);
		assertThat(paid.get("status").asText()).isEqualTo("APPROVED");
		assertThat(paid.hasNonNull("receiptNo")).isTrue();
		MvcResult pdf = mockMvc.perform(get("/api/v1/offline-fee-payments/" + paid.get("id").asText() + "/receipt.pdf")
			.header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn();
		assertThat(pdf.getResponse().getContentAsByteArray().length).isGreaterThan(1000);
		JsonNode waiting = respond(post(base + "/payments", staff, payment("500", "EARLY-2")), 201);
		assertThat(waiting.get("status").asText()).isEqualTo("PENDING");

		// What is owed is the plan's total less the approved payments; more than that is refused.
		JsonNode fees = respondTo(get(base + "/fees").header("Authorization", "Bearer " + token), 200);
		assertThat(fees.get("owed").decimalValue()).isEqualByComparingTo("2500");
		assertThat(fees.get("dues")).isEmpty();
		respond(post(base + "/payments", token, payment("2600", "EARLY-3")), 400);

		// The plan cannot go below what is paid or waiting (5,000).
		assertThat(fieldNames(respond(patch(base + "/setup", token, plan("2026-11-20", "4000")), 400))).contains("installmentPlan");

		respond(post("/api/v1/offline-fee-payments/" + waiting.get("id").asText() + "/approve", token, null), 200);
		assertThat(respondTo(get(base + "/fees").header("Authorization", "Bearer " + token), 200).get("owed").decimalValue())
			.isEqualByComparingTo("2000");

		// Onboarding writes the dues and matches the 5,000 to them in order: the first 4,000 in full, 1,000 of the second.
		respond(post(base + "/items", token, "{\"scope\":\"EXAM\",\"examId\":\"" + publishedExam() + "\"}"), 200);
		respond(post(base + "/ready", token, null), 200);
		respond(post(base + "/onboard", token, rollBody()), 200);
		JsonNode after = respondTo(get(base + "/fees").header("Authorization", "Bearer " + token), 200);
		assertThat(after.get("owed").decimalValue()).isEqualByComparingTo("2000");
		assertThat(after.get("dues").get(0).get("paid").decimalValue()).isEqualByComparingTo("4000");
		assertThat(after.get("dues").get(1).get("paid").decimalValue()).isEqualByComparingTo("1000");
		assertThat(after.get("dues").get(2).get("paid").decimalValue()).isEqualByComparingTo("0");
	}

	@Test
	void aRecordedFeePaymentIsCorrectedTheMasterAdminFinalizesItAndFinanceShowsIt() throws Exception {
		// payment-records-edit-plan.md: a System Admin's correction waits; the Master Admin finalizes or rejects it.
		UUID master = seed("MASTER_ADMIN", "Master");
		String token = token(master);
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID admission = onboarded(seed("STUDENT", "Corrected Payer"), master);
		JsonNode paid = respond(post("/api/v1/offline-admissions/" + admission + "/payments", token, payment("3000", "FIX-1")), 201);
		String id = paid.get("id").asText();
		BigDecimal before = income(token);

		JsonNode waiting = respond(post("/api/v1/payment-records/FEE/" + id + "/edits", staff,
				"{\"amount\":\"3500\",\"reason\":\"The student paid 3,500\"}"), 200);
		assertThat(waiting.get("status").asText()).isEqualTo("PENDING");
		String editId = waiting.get("id").asText();
		// While it waits nothing changes, Finance included; a second correction of the same payment waits its turn.
		assertThat(income(token)).isEqualByComparingTo(before);
		respond(post("/api/v1/payment-records/FEE/" + id + "/edits", staff, "{\"amount\":\"3600\",\"reason\":\"Again\"}"), 409);
		respond(post("/api/v1/payment-edits/" + editId + "/finalize", staff, null), 403);
		JsonNode rows = respondTo(get("/api/v1/payment-records?kind=FEE&from=2026-10-20&to=2026-10-20").header("Authorization",
				"Bearer " + token), 200);
		boolean listed = false;
		for (JsonNode row : rows) {
			if (id.equals(row.get("id").asText())) {
				listed = true;
				assertThat(row.get("pendingEdit").get("after").get("amount").decimalValue()).isEqualByComparingTo("3500");
			}
		}
		assertThat(listed).isTrue();
		assertThat(respondTo(get("/api/v1/payment-records/edits").header("Authorization", "Bearer " + token), 200).toString())
			.contains(editId);

		// Finalized: the payment is 3,500, matched again (3,000 to the first instalment, 500 to the second), and Finance shows it.
		assertThat(respond(post("/api/v1/payment-edits/" + editId + "/finalize", token, null), 200).get("status").asText())
			.isEqualTo("FINALIZED");
		JsonNode fees = respondTo(get("/api/v1/offline-admissions/" + admission + "/fees").header("Authorization", "Bearer " + token), 200);
		assertThat(fees.get("owed").decimalValue()).isEqualByComparingTo("8500");
		assertThat(fees.get("dues").get(0).get("paid").decimalValue()).isEqualByComparingTo("3000");
		assertThat(fees.get("dues").get(1).get("paid").decimalValue()).isEqualByComparingTo("500");
		assertThat(income(token).subtract(before)).isEqualByComparingTo("500");
		assertThat(jdbc().queryForObject("select receipt_no from offline_fee_payments where id = ?", String.class, UUID.fromString(id)))
			.isEqualTo(paid.get("receiptNo").asText());
		mockMvc.perform(get("/api/v1/offline-fee-payments/" + id + "/receipt.pdf").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());

		// The Master Admin's own correction is final at once, and cannot take more than is owed.
		respond(post("/api/v1/payment-records/FEE/" + id + "/edits", token, "{\"amount\":\"13000\",\"reason\":\"Too much\"}"), 400);
		JsonNode own = respond(post("/api/v1/payment-records/FEE/" + id + "/edits", token,
				"{\"reference\":\"FIX-1-B\",\"reason\":\"Receipt book number\"}"), 200);
		assertThat(own.get("status").asText()).isEqualTo("FINALIZED");

		// A rejected correction leaves the payment as it was.
		String rejected = respond(post("/api/v1/payment-records/FEE/" + id + "/edits", staff,
				"{\"paidOn\":\"2026-10-19\",\"reason\":\"Paid the day before\"}"), 200).get("id").asText();
		respond(post("/api/v1/payment-edits/" + rejected + "/reject", token, "{\"reason\":\"The receipt book says 20 October\"}"), 200);
		assertThat(jdbc().queryForObject("select paid_on::text from offline_fee_payments where id = ?", String.class, UUID.fromString(id)))
			.isEqualTo("2026-10-20");
		JsonNode history = respondTo(get("/api/v1/payment-records/edits?status=DECIDED").header("Authorization", "Bearer " + token), 200);
		assertThat(history.toString()).contains(rejected).contains("The receipt book says 20 October").doesNotContain("\"PENDING\"");
		assertThat(respondTo(get("/api/v1/payment-records/edits").header("Authorization", "Bearer " + token), 200).toString())
			.doesNotContain(rejected);
	}

	/** Income in Finance on the test day, as the Master Admin sees it. */
	private BigDecimal income(String token) throws Exception {
		return respondTo(get("/api/v1/finance/summary?from=2026-10-20&to=2026-10-20").header("Authorization", "Bearer " + token), 200)
			.get("income").decimalValue();
	}

	private static String payment(String amount, String reference) {
		return "{\"amount\":\"" + amount + "\",\"method\":\"CASH\",\"reference\":\"" + reference + "\",\"paidOn\":\"2026-10-20\"}";
	}

	// ---- terms and items after onboarding ----------------------------------------------------------------------------------

	@Test
	void termsSpreadWhatIsLeftOfTheCourseFeeOverTheOpenInstalmentsAndMoveTheCaps() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Master");
		UUID student = seed("STUDENT", "Terms Student");
		UUID admission = onboarded(student, master);
		String token = token(master);
		assertThat(unpaidDueAmounts(admission)).containsExactly("3000.00", "3000.00", "3000.00", "3000.00");

		respond(patch("/api/v1/offline-admissions/" + admission + "/terms", token(seed("SYSTEM_ADMIN", "Staff")), "{\"courseFee\":\"1\"}"),
				403);

		// Before any instalment is due, a new fee is spread over all four.
		JsonNode raised = respond(patch("/api/v1/offline-admissions/" + admission + "/terms", token, "{\"courseFee\":\"14000\"}"), 200);
		assertThat(raised.get("setup").get("courseFee").decimalValue()).isEqualByComparingTo("14000");
		assertThat(unpaidDueAmounts(admission)).containsExactly("3500.00", "3500.00", "3500.00", "3500.00");
		assertThat(studentNoticeCount(student, "ADMISSION_UPDATED")).isEqualTo(1L);
		assertThat(mailCount(student, "ADMISSION_UPDATED")).isEqualTo(1L);

		// Later in the course: January has come and keeps its amount; what is left is spread over February to April.
		clock.set(Instant.parse("2027-02-10T04:00:00Z"));
		respond(patch("/api/v1/offline-admissions/" + admission + "/terms", token, "{\"courseFee\":\"15500\"}"), 200);
		assertThat(unpaidDueAmounts(admission)).containsExactly("3500.00", "4000.00", "4000.00", "4000.00");

		// A fifth instalment needs a later end: the rest is spread over four, and the cap of every live OFFLINE grant moves.
		clock.set(Instant.parse("2027-02-11T04:00:00Z"));
		JsonNode tooMany = respond(patch("/api/v1/offline-admissions/" + admission + "/terms", token, "{\"installments\":5}"), 400);
		assertThat(fieldNames(tooMany)).contains("installments");
		respond(patch("/api/v1/offline-admissions/" + admission + "/terms", token,
				"{\"installments\":5,\"courseEndsOn\":\"2027-06-15\"}"), 200);
		assertThat(unpaidDueDates(admission)).containsExactly("2027-01-31", "2027-02-28", "2027-03-31", "2027-04-30", "2027-05-31");
		assertThat(unpaidDueAmounts(admission)).containsExactly("3500.00", "3000.00", "3000.00", "3000.00", "3000.00");
		assertThat(liveCaps(student)).hasSize(2).allSatisfy(cap -> assertThat(cap).isEqualTo(Instant.parse("2027-06-15T18:00:00Z")));

		// Fewer instalments and an earlier end: the ones beyond are cancelled, the rest is spread with the remainder on the last.
		clock.set(Instant.parse("2027-02-12T04:00:00Z"));
		respond(patch("/api/v1/offline-admissions/" + admission + "/terms", token,
				"{\"installments\":3,\"courseEndsOn\":\"2027-03-31\",\"courseFee\":\"15501\"}"), 200);
		assertThat(unpaidDueDates(admission)).containsExactly("2027-01-31", "2027-02-28", "2027-03-31");
		assertThat(unpaidDueAmounts(admission)).containsExactly("3500.00", "6000.00", "6001.00");
		assertThat(liveCaps(student)).hasSize(2).allSatisfy(cap -> assertThat(cap).isEqualTo(Instant.parse("2027-03-31T18:00:00Z")));

		// A fee below what is already due is refused.
		JsonNode tooLow = respond(patch("/api/v1/offline-admissions/" + admission + "/terms", token, "{\"courseFee\":\"3000\"}"), 400);
		assertThat(fieldNames(tooLow)).contains("courseFee");
	}

	@Test
	void aDuePaidByAnApprovedPaymentIsNeverCancelled() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Master");
		UUID student = seed("STUDENT", "Paid Student");
		UUID admission = onboarded(student, master);
		UUID lastDue = jdbc().queryForObject("select id from offline_fee_dues where admission_id = ? and seq = 4", UUID.class, admission);
		// No Stage 4 API exists yet, so the approved payment is written with JDBC, allocated to the last due.
		UUID payment = UUID.randomUUID();
		jdbc().update("""
				insert into offline_fee_payments (id, admission_id, amount, method, reference, paid_on, status, recorded_by, recorded_at)
				values (?, ?, 3000, 'CASH', 'DESK-1', '2027-04-01', 'APPROVED', ?, now())
				""", payment, admission, master);
		jdbc().update("insert into offline_fee_allocations (id, payment_id, due_id, amount) values (?, ?, ?, 3000)",
				UUID.randomUUID(), payment, lastDue);

		JsonNode refusal = respond(patch("/api/v1/offline-admissions/" + admission + "/terms", token(master),
				"{\"installments\":3,\"courseEndsOn\":\"2027-03-31\"}"), 400);
		assertThat(fieldNames(refusal)).contains("installments");
		assertThat(refusal.get("fieldErrors").get(0).get("message").asText()).contains("already paid or due");
		assertThat(jdbc().queryForObject("select cancelled_at is null from offline_fee_dues where id = ?", Boolean.class, lastDue))
			.isTrue();
	}

	@Test
	void anItemAddedAfterOnboardingIsMasterAdminOnlyGrantsAtOnceAndRevokesWithAReason() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Master");
		String masterToken = token(master);
		UUID student = seed("STUDENT", "Late Item Student");
		UUID admission = onboarded(student, master);
		UUID extra = publishedExam();

		respond(post("/api/v1/offline-admissions/" + admission + "/items", token(seed("SYSTEM_ADMIN", "Staff")),
				"{\"scope\":\"EXAM\",\"examId\":\"" + extra + "\"}"), 403);

		JsonNode added = respond(post("/api/v1/offline-admissions/" + admission + "/items", masterToken,
				"{\"scope\":\"EXAM\",\"examId\":\"" + extra + "\"}"), 200);
		String itemId = null;
		for (JsonNode item : added.get("admission").get("items")) {
			if (item.hasNonNull("examId") && extra.toString().equals(item.get("examId").asText())) {
				itemId = item.get("id").asText();
			}
		}
		assertThat(itemId).isNotNull();
		assertThat(jdbc().queryForObject("select count(*) from live_exam_grants where student_id = ? and exam_id = ?"
				+ " and source = 'OFFLINE' and revoked_at is null", Long.class, student, extra)).isEqualTo(1L);
		assertThat(studentNoticeCount(student, "ADMISSION_UPDATED")).isEqualTo(1L);

		mockMvc.perform(delete("/api/v1/offline-admissions/" + admission + "/items/" + itemId).header("Authorization", "Bearer " + masterToken))
			.andExpect(status().isOk());
		assertThat(jdbc().queryForObject("select revoke_reason from live_exam_grants where student_id = ? and exam_id = ?"
				+ " and source = 'OFFLINE'", String.class, student, extra)).isEqualTo("Removed from offline admission");
	}

	@Test
	void closeRevokesTheGrantsCancelsTheUnpaidDuesAndNotifiesOnce() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Master");
		String masterToken = token(master);
		UUID student = seed("STUDENT", "Closing Student");
		UUID admission = onboarded(student, master);

		respond(post("/api/v1/offline-admissions/" + admission + "/close", token(seed("SYSTEM_ADMIN", "Staff")), "{\"reason\":\"No\"}"),
				403);
		respond(post("/api/v1/offline-admissions/" + admission + "/close", masterToken, "{\"reason\":\"   \"}"), 400);

		JsonNode closed = respond(post("/api/v1/offline-admissions/" + admission + "/close", masterToken,
				"{\"reason\":\"Left the course\"}"), 200);
		assertThat(closed.get("state").asText()).isEqualTo("CLOSED");
		assertThat(jdbc().queryForObject("select count(*) from live_exam_grants where student_id = ? and source = 'OFFLINE'"
				+ " and revoked_at is not null and revoke_reason = 'Admission closed'", Long.class, student)).isEqualTo(2L);
		assertThat(jdbc().queryForObject("select count(*) from offline_fee_dues where admission_id = ? and cancelled_at is not null",
				Long.class, admission)).isEqualTo(4L);
		assertThat(studentNoticeCount(student, "ADMISSION_CLOSED")).isEqualTo(1L);
		assertThat(mailCount(student, "ADMISSION_CLOSED")).isEqualTo(1L);
	}

	// ---- signed copy --------------------------------------------------------------------------------------------------------

	@Test
	void signedCopyAcceptsAPdfAndAPhotoAndRefusesOtherTypes() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Master");
		UUID admission = admission(seed("STUDENT", "Signed Student"), master, "SUBMITTED");
		String staffToken = token(seed("SYSTEM_ADMIN", "Staff"));
		byte[] pdf = "%PDF-1.4\n%test\n".getBytes();

		mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/offline-admissions/" + admission + "/signed-form")
			.file(new MockMultipartFile("file", "signed.pdf", "application/pdf", pdf))
			.header("Authorization", "Bearer " + staffToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.signedCopy.fileName").value("signed.pdf"));
		mockMvc.perform(get("/api/v1/offline-admissions/" + admission + "/signed-form").header("Authorization", "Bearer " + staffToken))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", containsString("application/pdf")));

		JsonNode refused = respondTo(multipart(HttpMethod.PUT, "/api/v1/offline-admissions/" + admission + "/signed-form")
			.file(new MockMultipartFile("file", "notes.txt", "text/plain", "not a form".getBytes()))
			.header("Authorization", "Bearer " + staffToken), 400);
		assertThat(refused.get("code").asText()).isEqualTo("VALIDATION_FAILED");

		mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/offline-admissions/" + admission + "/signed-form")
			.file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpeg()))
			.header("Authorization", "Bearer " + staffToken))
			.andExpect(status().isOk());

		mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/offline-admissions/" + admission + "/signed-form")
			.file(new MockMultipartFile("file", "signed.pdf", "application/pdf", pdf))
			.header("Authorization", "Bearer " + token(seed("INSTRUCTOR", "Instructor"))))
			.andExpect(status().isForbidden());
	}

	// ---- batches --------------------------------------------------------------------------------------------------------------

	@Test
	void aBatchWithOnboardedStudentsCannotBeArchivedAndARoutineNoticesEachOnboardedStudent() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Master");
		String masterToken = token(master);
		String staffToken = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID student = seed("STUDENT", "Batch Student");
		UUID admission = onboarded(student, master);
		UUID batch = jdbc().queryForObject("select batch_id from offline_admissions where id = ?", UUID.class, admission);

		JsonNode refusal = respond(post("/api/v1/offline-batches/" + batch + "/archive", masterToken, null), 409);
		assertThat(refusal.get("message").asText()).contains("1 onboarded student");
		respond(post("/api/v1/offline-batches/" + batch + "/archive", staffToken, null), 403);

		mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/offline-batches/" + batch + "/routine-pdf")
			.file(new MockMultipartFile("file", "routine.pdf", "application/pdf", "%PDF-1.4\nroutine\n".getBytes()))
			.header("Authorization", "Bearer " + staffToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.routineUploaded").value(true));
		assertThat(studentNoticeCount(student, "BATCH_ROUTINE_UPDATED")).isEqualTo(1L);
		mockMvc.perform(get("/api/v1/offline-batches/" + batch + "/routine.pdf").header("Authorization", "Bearer " + staffToken))
			.andExpect(status().isOk());

		mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/offline-batches/" + batch + "/routine-pdf")
			.file(new MockMultipartFile("file", "routine.txt", "text/plain", "plain".getBytes()))
			.header("Authorization", "Bearer " + staffToken))
			.andExpect(status().isBadRequest());
	}

	@Test
	void theMasterAdminManagesBatchesAndAnEmptyBatchCanBeArchived() throws Exception {
		String masterToken = token(seed("MASTER_ADMIN", "Master"));
		UUID batch = batch(masterToken, "2027-05-31", "2500");

		// V103: a published batch's form is locked; the Master Admin moves it to draft to edit it. Staff can do neither.
		JsonNode locked = respond(patch("/api/v1/offline-batches/" + batch, masterToken, "{\"courseFee\":\"2700\"}"), 409);
		assertThat(locked.get("message").asText()).contains("Move it to draft");
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		respond(post("/api/v1/offline-batches/" + batch + "/unpublish", staff, null), 403);
		JsonNode draft = respond(post("/api/v1/offline-batches/" + batch + "/unpublish", masterToken, null), 200);
		assertThat(draft.get("published").asBoolean()).isFalse();
		// A draft is hidden from students: the public card and list leave it out.
		mockMvc.perform(get("/api/v1/public/offline-batches/" + batch)).andExpect(status().isNotFound());

		JsonNode changed = respond(patch("/api/v1/offline-batches/" + batch, masterToken, "{\"courseFee\":\"2700\",\"defaultInstallments\":3}"),
				200);
		assertThat(changed.get("courseFee").decimalValue()).isEqualByComparingTo("2700");
		assertThat(changed.get("defaultInstallments").asInt()).isEqualTo(3);
		assertThat(jdbc().queryForObject("select default_monthly_fee from offline_batches where id = ?", BigDecimal.class, batch))
			.isEqualByComparingTo("900");
		respond(patch("/api/v1/offline-batches/" + batch, masterToken, "{\"defaultInstallments\":25}"), 400);
		JsonNode published = respond(post("/api/v1/offline-batches/" + batch + "/publish", masterToken, null), 200);
		assertThat(published.get("published").asBoolean()).isTrue();
		mockMvc.perform(get("/api/v1/public/offline-batches/" + batch)).andExpect(status().isOk());
		JsonNode archived = respond(post("/api/v1/offline-batches/" + batch + "/archive", masterToken, null), 200);
		assertThat(archived.get("status").asText()).isEqualTo("ARCHIVED");
		respond(patch("/api/v1/offline-batches/" + batch, masterToken, "{\"nameEn\":\"Changed\"}"), 409);
	}

	// ---- helpers ----------------------------------------------------------------------------------------------------------------

	private JdbcTemplate jdbc() {
		return new JdbcTemplate(dataSource);
	}

	private ResultActions patch(String url, String token, String body) throws Exception {
		return mockMvc.perform(MockMvcRequestBuilders.patch(url).header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body == null ? "{}" : body));
	}

	private ResultActions post(String url, String token, String body) throws Exception {
		var request = MockMvcRequestBuilders.post(url).header("Authorization", "Bearer " + token);
		if (body != null) {
			request = request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mockMvc.perform(request);
	}

	/** A request answered with the expected status; returns its JSON body. */
	private JsonNode respond(ResultActions actions, int expected) throws Exception {
		MvcResult result = actions.andExpect(status().is(expected)).andReturn();
		String text = result.getResponse().getContentAsString();
		return text.isBlank() ? json.createObjectNode() : json.readTree(text);
	}

	/** A request built by the caller (a multipart upload, say), answered with the expected status. */
	private JsonNode respondTo(RequestBuilder request, int expected) throws Exception {
		return respond(mockMvc.perform(request), expected);
	}

	private static Set<String> fieldNames(JsonNode refusal) {
		Set<String> fields = new HashSet<>();
		refusal.get("fieldErrors").forEach(error -> fields.add(error.get("field").asText()));
		return fields;
	}

	private long notificationCount(String kind, UUID recipient) {
		Long count = jdbc().queryForObject("select count(*) from staff_notifications where kind = ? and recipient_id = ?", Long.class,
				kind, recipient);
		return count == null ? 0L : count;
	}

	private long studentNoticeCount(UUID student, String kind) {
		Long count = jdbc().queryForObject("select count(*) from student_notices where user_id = ? and kind = ?", Long.class, student,
				kind);
		return count == null ? 0L : count;
	}

	private long mailCount(UUID student, String kind) {
		Long count = jdbc().queryForObject("select count(*) from exam_outbound_mail where user_id = ? and kind = ?", Long.class, student,
				kind);
		return count == null ? 0L : count;
	}

	private List<String> unpaidDueAmounts(UUID admission) {
		return jdbc().queryForList("select amount::text from offline_fee_dues where admission_id = ? and cancelled_at is null"
				+ " order by seq", String.class, admission);
	}

	private List<String> unpaidDueDates(UUID admission) {
		return jdbc().queryForList("select due_on::text from offline_fee_dues where admission_id = ? and cancelled_at is null"
				+ " order by seq", String.class, admission);
	}

	private List<Instant> liveCaps(UUID student) {
		return jdbc().queryForList("select access_ends_at from live_exam_grants where student_id = ? and source = 'OFFLINE'"
				+ " and revoked_at is null", Timestamp.class, student).stream().map(Timestamp::toInstant).toList();
	}

	private String titleOf(String table, UUID id) {
		return jdbc().queryForObject("select title_en from " + table + " where id = ?", String.class, id);
	}

	/**
	 * A student's admission as the desk and the form leave it: every form field filled, a photo on file, one submission row, and the
	 * given state. The admission is created at the test clock's instant.
	 */
	private UUID admission(UUID studentId, UUID createdBy, String status) {
		UUID id = UUID.randomUUID();
		jdbc().update("""
				insert into offline_admissions (id, student_id, status, created_by, created_at, submit_count, submitted_at,
				  full_name, father_name, mother_name, date_of_birth, email, gender, university, semester_batch, nid, mobile,
				  emergency_mobile, hall_mess, thana, district, present_address, permanent_address)
				values (?, ?, ?, ?, ?, 1, ?, 'Rahim Uddin', 'Karim Uddin', 'Amena Begum', '2004-05-10', ?, 'MALE', 'Dhaka University',
				  '2024-25', '1234567890123', ?, '01912345678', 'Shahid Minar Hall', 'Dhanmondi', 'Dhaka',
				  'House 1, Road 2, Dhaka', 'Village, Thana, District')
				""", id, studentId, status, createdBy, Timestamp.from(NOW), Timestamp.from(NOW), uniqueEmail(), validMobile());
		jdbc().update("insert into offline_admission_submissions (id, admission_id, submitted_at, submitted_by, form_json)"
				+ " values (?, ?, ?, ?, '{}')", UUID.randomUUID(), id, Timestamp.from(NOW), studentId);
		jdbc().update("insert into student_photos (user_id, stored_name, content_type, byte_size, updated_at) values (?, ?, ?, ?, now())",
				studentId, "photo-" + UUID.randomUUID() + ".jpg", "image/jpeg", 100L);
		return id;
	}

	/**
	 * An onboarded admission: a batch ending 30 April 2027 with a 12,000 course fee, admitted on 31 December, so four instalments of
	 * 3,000 from 31 January; one exam and one package; and the Master Admin's onboarding.
	 */
	private UUID onboarded(UUID student, UUID master) throws Exception {
		String token = token(master);
		UUID batch = batch(token, "2027-04-30", "12000");
		UUID admission = admission(student, master, "SUBMITTED");
		respond(patch("/api/v1/offline-admissions/" + admission + "/setup", token,
				"{\"batchId\":\"" + batch + "\",\"admittedOn\":\"2026-12-31\"}"), 200);
		respond(post("/api/v1/offline-admissions/" + admission + "/items", token, "{\"scope\":\"EXAM\",\"examId\":\"" + publishedExam()
				+ "\"}"), 200);
		respond(post("/api/v1/offline-admissions/" + admission + "/items", token,
				"{\"scope\":\"MODULE\",\"moduleId\":\"" + publishedModule() + "\"}"), 200);
		respond(post("/api/v1/offline-admissions/" + admission + "/ready", token, null), 200);
		respond(post("/api/v1/offline-admissions/" + admission + "/onboard", token, rollBody()), 200);
		return admission;
	}

	/** A batch made through the API, as the Master Admin makes it. */
	private UUID batch(String token, String endsOn, String fee) throws Exception {
		MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/offline-batches").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"nameEn\":\"BJS Evening\",\"nameBn\":\"বিজেএস সন্ধ্যা\",\"endsOn\":\"" + endsOn
					+ "\",\"courseFee\":" + fee + "}"))
			.andExpect(status().isCreated())
			.andReturn();
		UUID id = UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
		// A batch starts as a draft (V103); the helper publishes it, as the Master Admin does before students apply.
		mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/offline-batches/" + id + "/publish").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		return id;
	}

	private UUID publishedExam() {
		return exam("PUBLISHED");
	}

	private UUID unpublishedExam() {
		return exam("DRAFT");
	}

	/** An exam in the given status, live from 1 October 2026 to the end of 2027, with 30 days of access. */
	private UUID exam(String status) {
		UUID id = UUID.randomUUID();
		jdbc().update("""
				insert into live_exams (id, module_id, category_id, exam_type, title_en, title_bn, description_en, description_bn,
				  rules_en, rules_bn, paper_language, live_at, ends_at, duration_minutes, mode, status, finally_saved, pass_percent,
				  answers_changeable, is_free, access_days, published_at, created_at, updated_at)
				values (?, null, ?, 'MCQ', ?, 'পরীক্ষা', '', '', '', '', 'BN', ?, ?, 30, 'LIVE', ?, true, 50, true, false, 30, ?,
				  now(), now())
				""", id, category(), "Exam " + id, Timestamp.from(Instant.parse("2026-10-01T04:00:00Z")),
				Timestamp.from(Instant.parse("2027-12-31T04:00:00Z")), status, Timestamp.from(Instant.parse("2026-09-01T04:00:00Z")));
		return id;
	}

	private UUID publishedModule() {
		UUID id = UUID.randomUUID();
		jdbc().update("""
				insert into live_exam_modules (id, category_id, title_en, title_bn, description_en, description_bn, access_mode, status,
				  finally_saved, position, created_at, updated_at)
				values (?, ?, ?, 'প্যাকেজ', '', '', 'MANUAL', 'PUBLISHED', true, 0, now(), now())
				""", id, category(), "Package " + id);
		return id;
	}

	private UUID category() {
		UUID id = UUID.randomUUID();
		jdbc().update("insert into live_exam_categories (id, title_en, title_bn, position, created_at, updated_at)"
				+ " values (?, 'Category', 'বিভাগ', 0, now(), now())", id);
		return id;
	}

	/** A live manual grant on an exam, made at the test clock's instant, as a direct enrolment would make it. */
	private void manualExamGrant(UUID student, UUID exam) {
		jdbc().update("insert into live_exam_grants (id, student_id, scope, module_id, exam_id, order_id, created_at, source, reason)"
				+ " values (?, ?, 'EXAM', null, ?, null, ?, 'MANUAL', 'Held before')", UUID.randomUUID(), student, exam,
				Timestamp.from(NOW));
	}

	private UUID seed(String role, String name) {
		UUID id = UUID.randomUUID();
		jdbc().update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, "+8801" + ThreadLocalRandom.current().nextInt(300000000, 999999999), "offline-admin-" + id + "@example.com",
				passwords.encode("password1"), role);
		if ("STUDENT".equals(role)) {
			jdbc().update("INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, ?, ?)", id, name,
					"A" + id.toString().substring(0, 8));
		}
		else {
			jdbc().update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, ?)", id, name);
		}
		return id;
	}

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

	private static String uniqueEmail() {
		return "offline-admin-form-" + UUID.randomUUID() + "@example.com";
	}

	/** An 11-digit Bangladesh mobile starting 017. */
	private static String validMobile() {
		return "017" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
	}

	/** A small real JPEG, made in memory. */
	private static byte[] jpeg() throws IOException {
		BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "jpg", out);
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


	/** A request body with a roll number nobody has yet: onboarding requires one. */
	private static String rollBody() {
		return "{\"rollNo\":\"" + java.util.concurrent.ThreadLocalRandom.current().nextLong(100_000_000L, 999_999_999L) + "\"}";
	}
}
