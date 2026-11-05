package com.coachingcenter.api.staffledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The instructor marking pay (staff pay plan, phase 4): the earnings sync rules (P1 to P3), the rate snapshot, the switch, the
 * notices, the unpaid reminder, the instructor's and the Master Admin's marking figures, and who may read them.
 *
 * <p>
 * The test base switches the scheduled jobs off, so each test calls {@link StaffEarningsSyncJob#sync()} or
 * {@link StaffEarningsSyncJob#remindUnpaid()} itself. Notices are checked per recipient, so other data in the database cannot
 * change a count.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class StaffEarningsIT extends PostgresIntegrationTest {

	private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Autowired
	private StaffEarningsSyncJob job;

	private JdbcTemplate jdbc;

	@BeforeEach
	void setUp() {
		jdbc = new JdbcTemplate(dataSource);
	}

	@Test
	void aPublishedSheetEarnsOnceAtTheDefaultRateAndBothSidesAreTold() {
		UUID master = seed("MASTER_ADMIN", "Master Earn");
		UUID instructor = seed("INSTRUCTOR", "Earn Instructor");
		UUID exam = exam(title("Muslim Law"));
		UUID sheet = sheet(exam, student(), 1, "LIVE", "SUBMITTED", instructor, minutesAgo(90), minutesAgo(30), null);
		BigDecimal rate = defaultRate();

		job.sync();

		assertThat(lineCount(sheet)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select kind from staff_ledger where participant_id = ?", String.class, sheet))
			.isEqualTo("MARKING");
		assertThat(jdbc.queryForObject("select user_id from staff_ledger where participant_id = ?", UUID.class, sheet))
			.isEqualTo(instructor);
		assertThat(jdbc.queryForObject("select amount from staff_ledger where participant_id = ?", BigDecimal.class, sheet))
			.isEqualByComparingTo(rate);
		assertThat(jdbc.queryForObject("select sheet_rate from staff_ledger where participant_id = ?", BigDecimal.class, sheet))
			.isEqualByComparingTo(rate);
		assertThat(jdbc.queryForObject("select due_on from staff_ledger where participant_id = ?", LocalDate.class, sheet))
			.isEqualTo(dhaka(minutesAgo(30)));

		assertThat(owedNotices(master, examTitle(exam))).isEqualTo(1);
		assertThat(earnedNotices(instructor, examTitle(exam))).isEqualTo(1);

		job.sync();
		assertThat(lineCount(sheet)).isEqualTo(1);
		assertThat(owedNotices(master, examTitle(exam))).isEqualTo(1);
		assertThat(earnedNotices(instructor, examTitle(exam))).isEqualTo(1);
	}

	@Test
	void aCorrectionPublishedLaterPaysNothingMore() {
		UUID instructor = seed("INSTRUCTOR", "First Marker");
		UUID other = seed("INSTRUCTOR", "Second Marker");
		UUID exam = exam(title("Correction"));
		UUID sheet = sheet(exam, student(), 1, "LIVE", "SUBMITTED", instructor, minutesAgo(90), minutesAgo(30), null);
		job.sync();
		assertThat(lineCount(sheet)).isEqualTo(1);

		jdbc.update("update live_exam_participants set evaluated_by = ?, published_at = ? where id = ?", other,
				ts(minutesAgo(5)), sheet);
		job.sync();

		assertThat(lineCount(sheet)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select user_id from staff_ledger where participant_id = ?", UUID.class, sheet))
			.isEqualTo(instructor);
		assertThat(earnedNotices(other, examTitle(exam))).isZero();
	}

	@Test
	void aSheetMarkedByTheMasterAdminOrASystemAdminEarnsNothing() {
		UUID master = seed("MASTER_ADMIN", "Master Marker");
		UUID sysAdmin = seed("SYSTEM_ADMIN", "Admin Marker");
		UUID exam = exam(title("Admin marks"));
		UUID byMaster = sheet(exam, student(), 1, "LIVE", "SUBMITTED", master, minutesAgo(90), minutesAgo(30), null);
		UUID bySysAdmin = sheet(exam, student(), 1, "LIVE", "SUBMITTED", sysAdmin, minutesAgo(90), minutesAgo(30), null);

		job.sync();

		assertThat(lineCount(byMaster)).isZero();
		assertThat(lineCount(bySysAdmin)).isZero();
	}

	@Test
	void anAutomaticZeroAnUnpublishedSheetAndAMarkWaitingForReleaseEarnNothing() {
		UUID instructor = seed("INSTRUCTOR", "Not Yet");
		UUID exam = exam(title("Not yet"));
		UUID zero = sheet(exam, student(), 1, "LIVE", "FAILED_TO_SUBMIT", instructor, minutesAgo(90), minutesAgo(30), null);
		UUID unpublished = sheet(exam, student(), 1, "LIVE", "SUBMITTED", instructor, minutesAgo(90), null, minutesAgo(10));

		job.sync();

		assertThat(lineCount(zero)).isZero();
		assertThat(lineCount(unpublished)).isZero();
	}

	@Test
	void aRetakeSheetEarnsItsOwnLine() {
		UUID instructor = seed("INSTRUCTOR", "Retake Marker");
		UUID exam = exam(title("Retake"));
		UUID learner = student();
		UUID first = sheet(exam, learner, 1, "LIVE", "SUBMITTED", instructor, minutesAgo(200), minutesAgo(180), null);
		UUID retake = sheet(exam, learner, 2, "RETAKE", "SUBMITTED", instructor, minutesAgo(100), minutesAgo(60), null);

		job.sync();

		assertThat(lineCount(first)).isEqualTo(1);
		assertThat(lineCount(retake)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from staff_ledger where user_id = ? and kind = 'MARKING'", Long.class,
				instructor)).isEqualTo(2L);
	}

	@Test
	void theInstructorsOwnRateIsCopiedOntoEachLineAndALaterChangeLeavesPastLinesAlone() {
		UUID instructor = seed("INSTRUCTOR", "Own Rate");
		jdbc.update("insert into staff_pay_settings (user_id, sheet_rate, updated_at) values (?, 60.00, now())", instructor);
		UUID exam = exam(title("Own rate"));
		UUID earlier = sheet(exam, student(), 1, "LIVE", "SUBMITTED", instructor, minutesAgo(90), minutesAgo(30), null);
		job.sync();
		assertThat(jdbc.queryForObject("select amount from staff_ledger where participant_id = ?", BigDecimal.class, earlier))
			.isEqualByComparingTo("60.00");

		jdbc.update("update staff_pay_settings set sheet_rate = 75.00 where user_id = ?", instructor);
		UUID later = sheet(exam, student(), 1, "LIVE", "SUBMITTED", instructor, minutesAgo(90), minutesAgo(20), null);
		job.sync();

		assertThat(jdbc.queryForObject("select amount from staff_ledger where participant_id = ?", BigDecimal.class, earlier))
			.isEqualByComparingTo("60.00");
		assertThat(jdbc.queryForObject("select amount from staff_ledger where participant_id = ?", BigDecimal.class, later))
			.isEqualByComparingTo("75.00");
	}

	@Test
	void theSwitchOffStopsNewLinesAndSwitchingBackOnCatchesUp() {
		UUID instructor = seed("INSTRUCTOR", "Switched");
		UUID exam = exam(title("Switch"));
		UUID sheet = sheet(exam, student(), 1, "LIVE", "SUBMITTED", instructor, minutesAgo(90), minutesAgo(30), null);

		// The test base has the scheduled jobs switched off, so the scheduled entry point does nothing here.
		job.run();
		assertThat(lineCount(sheet)).isZero();

		job.sync();
		assertThat(lineCount(sheet)).isEqualTo(1);
	}

	@Test
	void theFiguresCountMarkedWaitingReadyPublishedEarnedAndExpected() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Figures Master");
		UUID viewer = seed("INSTRUCTOR", "Figures Marker");
		UUID exam = exam(title("Figures"));
		sheet(exam, student(), 1, "LIVE", "SUBMITTED", viewer, minutesAgo(90), null, minutesAgo(10));
		sheet(exam, student(), 1, "LIVE", "SUBMITTED", viewer, minutesAgo(90), null, null);
		sheet(exam, student(), 1, "LIVE", "SUBMITTED", viewer, minutesAgo(120), minutesAgo(30), null);
		job.sync();
		BigDecimal rate = defaultRate();

		JsonNode mine = send(HttpMethod.GET, "/api/v1/me/pay/marking", token(viewer), null, 200);
		assertThat(mine.path("totals").path("marked").asInt()).isEqualTo(3);
		assertThat(mine.path("totals").path("waiting").asInt()).isEqualTo(2);
		assertThat(mine.path("totals").path("ready").asInt()).isEqualTo(1);
		assertThat(mine.path("totals").path("published").asInt()).isEqualTo(1);
		assertMoney(mine.path("totals").path("earned"), rate.toPlainString());
		assertMoney(mine.path("totals").path("expected"), rate.multiply(BigDecimal.valueOf(2)).toPlainString());
		assertMoney(mine.path("sheetRate"), rate.toPlainString());
		assertThat(mine.path("exams").size()).isEqualTo(1);
		assertThat(mine.path("exams").get(0).path("marked").asInt()).isEqualTo(3);

		JsonNode forMaster = send(HttpMethod.GET, "/api/v1/staff-pay/people/" + viewer + "/marking", token(master), null, 200);
		assertThat(forMaster.path("totals").path("waiting").asInt()).isEqualTo(2);
		assertThat(forMaster.path("totals").path("published").asInt()).isEqualTo(1);
	}

	@Test
	void theMarkingViewsAreReadForTheRightPeopleOnly() throws Exception {
		UUID master = seed("MASTER_ADMIN", "View Master");
		UUID sysAdmin = seed("SYSTEM_ADMIN", "View Admin");
		UUID instructor = seed("INSTRUCTOR", "View Instructor");

		send(HttpMethod.GET, "/api/v1/me/pay/marking", token(master), null, 403);
		send(HttpMethod.GET, "/api/v1/me/pay/marking", token(sysAdmin), null, 403);
		send(HttpMethod.GET, "/api/v1/me/pay/marking", token(instructor), null, 200);
		send(HttpMethod.GET, "/api/v1/staff-pay/people/" + instructor + "/marking", token(instructor), null, 403);
		send(HttpMethod.GET, "/api/v1/staff-pay/people/" + sysAdmin + "/marking", token(master), null, 404);
		send(HttpMethod.GET, "/api/v1/staff-pay/people/" + instructor + "/marking", token(master), null, 200);
	}

	@Test
	void theUnpaidReminderGoesOnceAWeekAndOnlyForAMarkingOlderThanTheReminderDays() {
		UUID master = seed("MASTER_ADMIN", "Reminder Master");
		String owedName = "Reminder Owed " + UUID.randomUUID().toString().substring(0, 8);
		UUID owed = seed("INSTRUCTOR", owedName);
		UUID exam = exam(title("Reminder"));
		Instant thirtyDaysAgo = Instant.now().minus(Duration.ofDays(30));
		sheet(exam, student(), 1, "LIVE", "SUBMITTED", owed, thirtyDaysAgo.minus(Duration.ofHours(1)), thirtyDaysAgo, null);
		job.sync();

		job.remindUnpaid();
		job.remindUnpaid();

		assertThat(reminders(master, owedName)).isEqualTo(1);

		String freshName = "Reminder Fresh " + UUID.randomUUID().toString().substring(0, 8);
		UUID fresh = seed("INSTRUCTOR", freshName);
		sheet(exam, student(), 1, "LIVE", "SUBMITTED", fresh, minutesAgo(90), minutesAgo(30), null);
		job.sync();
		job.remindUnpaid();

		assertThat(reminders(master, freshName)).isZero();
	}

	/* ---------- helpers ---------- */

	private long lineCount(UUID sheet) {
		return count("select count(*) from staff_ledger where participant_id = ?", sheet);
	}

	private long owedNotices(UUID master, String examTitle) {
		return count("""
				select count(*) from staff_notifications
				where recipient_id = ? and kind = 'MARKING_PUBLISHED_OWED' and payload->>'examTitleEn' = ?
				""", master, examTitle);
	}

	private long earnedNotices(UUID instructor, String examTitle) {
		return count("""
				select count(*) from staff_notifications
				where recipient_id = ? and kind = 'MARKING_PUBLISHED_EARNED' and payload->>'examTitleEn' = ?
				""", instructor, examTitle);
	}

	private long reminders(UUID master, String personName) {
		return count("""
				select count(*) from staff_notifications
				where recipient_id = ? and kind = 'MARKING_UNPAID_REMINDER' and payload->>'personName' = ?
				""", master, personName);
	}

	private String examTitle(UUID exam) {
		return jdbc.queryForObject("select title_en from live_exams where id = ?", String.class, exam);
	}

	private BigDecimal defaultRate() {
		return jdbc.queryForObject("select sheet_rate from staff_pay_defaults where id = 1", BigDecimal.class);
	}

	private UUID exam(String title) {
		UUID category = UUID.randomUUID();
		jdbc.update("""
				insert into live_exam_categories (id, title_en, title_bn, position, created_at, updated_at)
				values (?, 'Marking', 'মার্কিং', 0, now(), now())
				""", category);
		UUID exam = UUID.randomUUID();
		jdbc.update("""
				insert into live_exams (id, module_id, category_id, exam_type, title_en, title_bn, description_en,
				  description_bn, rules_en, rules_bn, paper_language, live_at, ends_at, duration_minutes, mode, status,
				  finally_saved, answers_changeable, created_at, updated_at)
				values (?, null, ?, 'WRITTEN', ?, 'খাতা', 'd', 'd', 'r', 'r', 'BN', now(), now() + make_interval(mins => 40),
				  40, 'LIVE', 'PUBLISHED', true, true, now(), now())
				""", exam, category, title);
		return exam;
	}

	/** A student who sat the paper. Students hold no pay, so they have no admin profile. */
	private UUID student() {
		return seed("STUDENT", null);
	}

	private UUID sheet(UUID exam, UUID student, int attempt, String kind, String status, UUID marker, Instant markedAt,
			Instant publishedAt, Instant readyAt) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into live_exam_participants (id, exam_id, student_id, kind, started_at, deadline_at, submitted_at,
				  status, answer_stored_file_name, created_at, updated_at, attempt_no, evaluated_by, evaluated_at,
				  published_at, ready_for_release_at, score)
				values (?, ?, ?, ?, now(), now(), now(), ?, 'answer.pdf', now(), now(), ?, ?, ?, ?, ?, ?)
				""", id, exam, student, kind, status, attempt, marker, ts(markedAt), ts(publishedAt), ts(readyAt),
				marker == null ? null : 40);
		return id;
	}

	private static Timestamp ts(Instant instant) {
		return instant == null ? null : Timestamp.from(instant);
	}

	private static Instant minutesAgo(long minutes) {
		return Instant.now().minus(Duration.ofMinutes(minutes));
	}

	private static LocalDate dhaka(Instant instant) {
		return instant.atZone(DHAKA).toLocalDate();
	}

	private static String title(String prefix) {
		return prefix + " " + UUID.randomUUID().toString().substring(0, 8);
	}

	private static void assertMoney(JsonNode node, String expected) {
		assertThat(new BigDecimal(node.asText()).compareTo(new BigDecimal(expected))).as(node.toString()).isZero();
	}

	/** Sends one request, expects {@code status}, and returns the body read as JSON (a missing body reads as an empty node). */
	private JsonNode send(HttpMethod method, String path, String token, Object body, int status) throws Exception {
		MockHttpServletRequestBuilder builder = request(method, path).header("Authorization", "Bearer " + token);
		if (body != null) {
			builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
		}
		MvcResult result = mockMvc.perform(builder).andExpect(status().is(status)).andReturn();
		String text = result.getResponse().getContentAsString();
		return text.isBlank() ? json.createObjectNode() : json.readTree(text);
	}

	private long count(String sql, Object... args) {
		Long value = jdbc.queryForObject(sql, Long.class, args);
		return value == null ? 0 : value;
	}

	private UUID seed(String role, String fullName) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, uniquePhone(), "earn-" + id + "@example.com", passwords.encode("password1"), role);
		if (fullName != null) {
			jdbc.update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, ?)", id, fullName);
		}
		return id;
	}

	private String token(UUID userId) throws Exception {
		String phone = jdbc.queryForObject("select phone from users where id = ?", String.class, userId);
		MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
			.post("/api/v1/auth/login")
			.header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content(json.writeValueAsString(Map.of("identifier", phone, "password", "password1"))))
			.andExpect(status().isOk())
			.andReturn();
		return json.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
	}

	private static String uniquePhone() {
		int suffix = java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999);
		return "+8801" + suffix;
	}

}
