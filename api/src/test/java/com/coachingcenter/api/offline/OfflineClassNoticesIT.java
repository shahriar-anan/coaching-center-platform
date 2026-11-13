package com.coachingcenter.api.offline;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The offline class notices (plan section 7, stage 4): the daily digest at 21:00 (one notice per admin, the Master Admin's with the waiting
 * line, nothing when nothing waits, and no repeat on the same day), the weekly overdue reminder (only once a held class is over a week),
 * the decision notice (one per recorder per batch), and that a student gets none of them.
 *
 * <p>
 * Every test makes its own admins and classes and uses its own dates, because the database is shared with the other classes' tests. The
 * digest and weekly runs are called with an explicit snapshot where the test needs a known count, and with the real snapshot where it checks
 * the whole path. The clock is fixed at 10:00 in Dhaka on 20 October 2026.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, OfflineClassNoticesIT.ClockConfig.class })
@TestPropertySource(properties = "app.offline-admission.enabled=true")
class OfflineClassNoticesIT extends PostgresIntegrationTest {

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

	@Autowired
	private OfflineClassDigestJob digest;

	@Autowired
	private OfflineClassService classes;

	@BeforeEach
	void fixTheClock() {
		clock.set(NOW);
	}

	@Test
	void theDigestWritesOneNoticePerAdminWhenClassesWait() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Digest Master");
		UUID system = seed("SYSTEM_ADMIN", "Digest System");
		String masterToken = token(master);
		UUID batch = batch(masterToken);
		heldClass(masterToken, batch, "2026-10-19");

		LocalDate today = LocalDate.parse("2026-10-20");
		assertThat(digest.runDigest(today)).isPositive();

		assertThat(digestCount(master, today)).isEqualTo(1L);
		assertThat(digestCount(system, today)).isEqualTo(1L);
	}

	@Test
	void nothingIsWrittenWhenNothingIsToSettle() {
		UUID admin = seed("SYSTEM_ADMIN", "Quiet System");
		LocalDate today = LocalDate.parse("2031-01-05");

		assertThat(digest.runDigestWith(today, new OfflineClassService.Snapshot(0, 0, 0))).isZero();

		assertThat(digestCount(admin, today)).isZero();
	}

	@Test
	void theMasterAdminsDigestCarriesTheWaitingLineAndTheSystemAdminsDoesNot() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Waiting Master");
		UUID system = seed("SYSTEM_ADMIN", "Waiting System");
		String masterToken = token(master);
		String staffToken = token(seed("SYSTEM_ADMIN", "Waiting Staff"));
		UUID batch = batch(masterToken);
		UUID instructor = instructor(masterToken);
		UUID held = heldClassWith(masterToken, batch, "2026-10-19", instructor);
		respond(post("/api/v1/offline-classes/" + held + "/payment", staffToken,
				"{\"amount\":400,\"method\":\"CASH\",\"paidOn\":\"2026-10-19\"}"), 200);

		LocalDate today = LocalDate.parse("2026-10-21");
		digest.runDigest(today);

		assertThat(payloadHas(master, today, "waiting")).isTrue();
		assertThat(payloadHas(system, today, "waiting")).isFalse();
		assertThat(payloadCount(master, today)).isPositive();
	}

	@Test
	void aSecondRunOnTheSameDayWritesNothingNew() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Repeat Master");
		String masterToken = token(master);
		heldClass(masterToken, batch(masterToken), "2026-10-19");
		LocalDate today = LocalDate.parse("2026-10-22");

		assertThat(digest.runDigest(today)).isPositive();
		assertThat(digest.runDigest(today)).isZero();

		assertThat(digestCount(master, today)).isEqualTo(1L);
	}

	@Test
	void theWeeklyNoticeOnlyCountsClassesHeldForMoreThanAWeek() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Weekly Master");
		String masterToken = token(master);
		UUID batch = batch(masterToken);
		heldClass(masterToken, batch, "2026-10-01");

		// The class ended on 1 October at 11:20 Dhaka. Six days later it has not waited more than a week; eight days later it has.
		long sixDays = classes.snapshot(Instant.parse("2026-10-07T12:00:00Z")).heldOverAWeek();
		long eightDays = classes.snapshot(Instant.parse("2026-10-09T12:00:00Z")).heldOverAWeek();
		assertThat(eightDays).isGreaterThan(sixDays);

		LocalDate sunday = LocalDate.parse("2031-03-09");
		assertThat(digest.runWeeklyWith(sunday, new OfflineClassService.Snapshot(0, 0, 0))).isZero();
		assertThat(overdueCount(master, sunday)).isZero();

		assertThat(digest.runWeeklyWith(sunday, new OfflineClassService.Snapshot(1, 0, 1))).isPositive();
		assertThat(overdueCount(master, sunday)).isEqualTo(1L);
		// The same ISO week again adds nothing.
		assertThat(digest.runWeeklyWith(sunday, new OfflineClassService.Snapshot(1, 0, 1))).isZero();
	}

	@Test
	void aDecisionIsOneNoticePerRecorderPerBatch() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Deciding Master");
		String masterToken = token(master);
		UUID staff = seed("SYSTEM_ADMIN", "Deciding Staff");
		String staffToken = token(staff);
		UUID batch = batch(masterToken);
		UUID instructor = instructor(masterToken);
		UUID one = heldClassWith(masterToken, batch, "2026-10-17", instructor);
		UUID two = heldClassWith(masterToken, batch, "2026-10-18", instructor);
		UUID three = heldClassWith(masterToken, batch, "2026-10-19", instructor);
		String first = paymentId(respond(post("/api/v1/offline-classes/" + one + "/payment", staffToken, payment("2026-10-17")), 200));
		String second = paymentId(respond(post("/api/v1/offline-classes/" + two + "/payment", staffToken, payment("2026-10-18")), 200));
		String third = paymentId(respond(post("/api/v1/offline-classes/" + three + "/payment", staffToken, payment("2026-10-19")), 200));

		respond(post("/api/v1/offline-class-payments/approve-many", masterToken,
				"{\"ids\":[\"" + first + "\",\"" + second + "\",\"" + third + "\"]}"), 200);

		assertThat(decidedCount(staff, "approved", "3", "rejected", "0")).isEqualTo(1L);

		UUID fourth = heldClassWith(masterToken, batch, "2026-10-16", instructor);
		String rejectMe = paymentId(respond(post("/api/v1/offline-classes/" + fourth + "/payment", staffToken, payment("2026-10-16")), 200));
		respond(post("/api/v1/offline-class-payments/" + rejectMe + "/reject", masterToken, "{\"reason\":\"Wrong figure\"}"), 200);

		assertThat(decidedCount(staff, "approved", "0", "rejected", "1")).isEqualTo(1L);
	}

	@Test
	void aStudentGetsNoStaffNotice() throws Exception {
		UUID student = seed("STUDENT", "Digest Student");
		UUID master = seed("MASTER_ADMIN", "Student Master");
		heldClass(token(master), batch(token(master)), "2026-10-19");

		digest.runDigest(LocalDate.parse("2026-10-23"));
		digest.runWeeklyWith(LocalDate.parse("2031-03-16"), new OfflineClassService.Snapshot(1, 0, 1));

		assertThat(count("select count(*) from staff_notifications where recipient_id = ?", student)).isZero();
	}

	// ---- helpers ----------------------------------------------------------------------------------------------------------

	private JdbcTemplate jdbc() {
		return new JdbcTemplate(dataSource);
	}

	private long count(String sql, Object... args) {
		Long count = jdbc().queryForObject(sql, Long.class, args);
		return count == null ? 0L : count;
	}

	private long digestCount(UUID recipient, LocalDate today) {
		return count("select count(*) from staff_notifications where recipient_id = ? and kind = 'OFFLINE_CLASSES_TO_SETTLE'"
				+ " and dedupe_key = ?", recipient, "OFFLINE_CLASSES_TO_SETTLE:" + today + ":" + recipient);
	}

	private long overdueCount(UUID recipient, LocalDate sunday) {
		String week = sunday.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR) + "-W"
				+ sunday.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
		return count("select count(*) from staff_notifications where recipient_id = ? and kind = 'OFFLINE_CLASSES_OVERDUE'"
				+ " and dedupe_key = ?", recipient, "OFFLINE_CLASSES_OVERDUE:" + week + ":" + recipient);
	}

	private boolean payloadHas(UUID recipient, LocalDate today, String key) {
		return count("select count(*) from staff_notifications where recipient_id = ? and kind = 'OFFLINE_CLASSES_TO_SETTLE'"
				+ " and dedupe_key = ? and payload->>'" + key + "' is not null", recipient, "OFFLINE_CLASSES_TO_SETTLE:" + today + ":" + recipient) == 1;
	}

	private long payloadCount(UUID recipient, LocalDate today) {
		return count("select (payload->>'count')::int from staff_notifications where recipient_id = ? and kind = 'OFFLINE_CLASSES_TO_SETTLE'"
				+ " and dedupe_key = ?", recipient, "OFFLINE_CLASSES_TO_SETTLE:" + today + ":" + recipient);
	}

	private long decidedCount(UUID recipient, String approvedKey, String approved, String rejectedKey, String rejected) {
		return count("select count(*) from staff_notifications where recipient_id = ? and kind = 'OFFLINE_ENTRIES_DECIDED'"
				+ " and payload->>? = ? and payload->>? = ?", recipient, approvedKey, approved, rejectedKey, rejected);
	}

	private static String paymentId(JsonNode classView) {
		return classView.get("payment").get("id").asText();
	}

	private static String payment(String paidOn) {
		return "{\"amount\":400,\"method\":\"CASH\",\"paidOn\":\"" + paidOn + "\"}";
	}

	private ResultActions post(String url, String token, String body) throws Exception {
		MockHttpServletRequestBuilder request = MockMvcRequestBuilders.post(url).header("Authorization", "Bearer " + token);
		if (body != null) {
			request = request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mockMvc.perform(request);
	}

	private JsonNode respond(ResultActions actions, int expected) throws Exception {
		MvcResult result = actions.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is(expected))
			.andReturn();
		String text = result.getResponse().getContentAsString();
		return text.isBlank() ? json.createObjectNode() : json.readTree(text);
	}

	private UUID batch(String token) throws Exception {
		JsonNode created = respond(post("/api/v1/offline-batches", token, "{\"nameEn\":\"Notices\",\"nameBn\":\"নোটিশ\","
				+ "\"endsOn\":\"2027-06-30\",\"defaultMonthlyFee\":3000}"), 201);
		return UUID.fromString(created.get("id").asText());
	}

	private UUID instructor(String token) throws Exception {
		JsonNode created = respond(post("/api/v1/offline-instructors", token, "{\"name\":\"Notice " + UUID.randomUUID() + "\"}"), 201);
		return UUID.fromString(created.get("id").asText());
	}

	/** A class on the date, ended at 11:20 Dhaka, so it is HELD at the fixed clock. */
	private void heldClass(String token, UUID batch, String heldOn) throws Exception {
		respond(post("/api/v1/offline-batches/" + batch + "/classes", token, "{\"heldOn\":\"" + heldOn
				+ "\",\"startsAt\":\"10:00\",\"endsAt\":\"11:20\",\"subject\":\"Contract Act\"}"), 201);
	}

	/** A held class with an instructor and a rate override, so it can be paid. Returns its id. */
	private UUID heldClassWith(String token, UUID batch, String heldOn, UUID instructor) throws Exception {
		JsonNode created = respond(post("/api/v1/offline-batches/" + batch + "/classes", token, "{\"heldOn\":\"" + heldOn
				+ "\",\"startsAt\":\"10:00\",\"endsAt\":\"11:20\",\"subject\":\"Contract Act\",\"instructorId\":\"" + instructor
				+ "\",\"rateOverride\":400}"), 201);
		return UUID.fromString(created.get("id").asText());
	}

	private UUID seed(String role, String name) {
		UUID id = UUID.randomUUID();
		jdbc().update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, "+8801" + ThreadLocalRandom.current().nextInt(300000000, 999999999), "offline-notice-" + id + "@example.com",
				passwords.encode("password1"), role);
		if ("STUDENT".equals(role)) {
			jdbc().update("INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, ?, ?)", id, name,
					"N" + id.toString().substring(0, 8));
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
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
			.andReturn();
		String body = result.getResponse().getContentAsString();
		String marker = "\"accessToken\":\"";
		int start = body.indexOf(marker) + marker.length();
		return body.substring(start, body.indexOf('"', start));
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
