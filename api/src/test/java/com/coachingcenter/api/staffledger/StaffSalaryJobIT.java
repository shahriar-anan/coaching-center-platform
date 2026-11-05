package com.coachingcenter.api.staffledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The System Admin salary job (staff pay plan, phase 5): the SALARY lines and their catch-up, the due-soon and overdue reminders
 * and their dedupe, a payment clearing a reminder, the end date, the instructor who is never paid through it, the switch, and
 * the two salary routes. The job is driven by {@code runOn} with fixed dates; the scheduled tick is off in tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class StaffSalaryJobIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Autowired
	private StaffSalaryJob job;

	private JdbcTemplate jdbc;

	@BeforeEach
	void setUp() {
		jdbc = new JdbcTemplate(dataSource);
	}

	@Test
	void theJobWritesEachFinishedMonthOnceAndCatchesUpAfterAMissedRun() {
		UUID person = seedSystemAdmin();
		salary(person, "20000", 5, "2026-08-01", null);

		job.runOn(LocalDate.of(2026, 11, 1));
		// August, September and October have finished; November is still in progress.
		assertThat(salaryLines(person)).isEqualTo(3);
		assertThat(jdbc.queryForObject("select due_on from staff_ledger where user_id = ? and period = '2026-10'", LocalDate.class,
				person)).isEqualTo(LocalDate.of(2026, 11, 5));
		assertThat(jdbc.queryForObject("select amount from staff_ledger where user_id = ? and period = '2026-10'", BigDecimal.class,
				person)).isEqualByComparingTo("20000");

		job.runOn(LocalDate.of(2026, 11, 1));
		assertThat(salaryLines(person)).isEqualTo(3);

		// The job did not run during November: on 1 December the November line is written as well.
		job.runOn(LocalDate.of(2026, 12, 1));
		assertThat(salaryLines(person)).isEqualTo(4);
		assertThat(jdbc.queryForObject("select due_on from staff_ledger where user_id = ? and period = '2026-11'", LocalDate.class,
				person)).isEqualTo(LocalDate.of(2026, 12, 5));
	}

	@Test
	void theMasterAdminIsRemindedDueSoonAndOncePerWeekWhileOverdueUntilAPaymentClearsIt() {
		UUID master = seed("MASTER_ADMIN");
		UUID person = seedSystemAdmin();
		salary(person, "20000", 5, "2026-10-01", null);
		int original = jdbc.queryForObject("select salary_due_soon_days from staff_pay_defaults where id = 1", Integer.class);
		jdbc.update("update staff_pay_defaults set salary_due_soon_days = 3 where id = 1");
		try {
			// October is written on 1 November, due 5 November. The due-soon window opens on 2 November.
			job.runOn(LocalDate.of(2026, 11, 1));
			assertThat(notices(dueSoonKey(master, person, "2026-10"))).isZero();

			job.runOn(LocalDate.of(2026, 11, 2));
			job.runOn(LocalDate.of(2026, 11, 3));
			assertThat(notices(dueSoonKey(master, person, "2026-10"))).isEqualTo(1);

			// The day after the due date, then weekly (6 November is in ISO week 45, 13 November in week 46).
			job.runOn(LocalDate.of(2026, 11, 6));
			assertThat(notices(overdueKey(master, person, "2026-10", "2026-W45"))).isEqualTo(1);
			job.runOn(LocalDate.of(2026, 11, 7));
			assertThat(notices(overdueKey(master, person, "2026-10", "2026-W45"))).isEqualTo(1);
			job.runOn(LocalDate.of(2026, 11, 13));
			assertThat(notices(overdueKey(master, person, "2026-10", "2026-W46"))).isEqualTo(1);

			assertThat(count("""
					select count(*) from staff_notifications
					where recipient_id = ? and kind = 'SALARY_OVERDUE' and dedupe_key like ?
					  and payload->>'dueOn' = '2026-11-05'
					""", master, "SALARY_OVERDUE:" + master + ":" + person + ":%")).isEqualTo(2);

			// A payment of the full salary clears it: the next week brings no reminder.
			pay(person, "20000", LocalDate.of(2026, 11, 13));
			job.runOn(LocalDate.of(2026, 11, 20));
			assertThat(notices(overdueKey(master, person, "2026-10", "2026-W47"))).isZero();
		}
		finally {
			jdbc.update("update staff_pay_defaults set salary_due_soon_days = ? where id = 1", original);
		}
	}

	@Test
	void anEndDateStopsNewMonthsAndAnInstructorIsNeverPaidThroughTheJob() {
		UUID person = seedSystemAdmin();
		salary(person, "15000", 28, "2026-08-01", "2026-09-30");
		UUID instructor = seed("INSTRUCTOR");
		jdbc.update("insert into staff_pay_settings (user_id, sheet_rate, updated_at) values (?, 60, now())", instructor);

		job.runOn(LocalDate.of(2026, 11, 1));

		// August and September only: the end date is in September, so October gets no line.
		assertThat(salaryLines(person)).isEqualTo(2);
		assertThat(jdbc.queryForObject("select due_on from staff_ledger where user_id = ? and period = '2026-09'", LocalDate.class,
				person)).isEqualTo(LocalDate.of(2026, 10, 28));
		assertThat(salaryLines(instructor)).isZero();
	}

	@Test
	void theJobDoesNothingWhileItIsSwitchedOff() {
		UUID person = seedSystemAdmin();
		salary(person, "1000", 5, "2020-01-01", null);

		ReflectionTestUtils.setField(job, "enabled", false);
		try {
			job.run();
			assertThat(salaryLines(person)).isZero();
		}
		finally {
			ReflectionTestUtils.setField(job, "enabled", true);
		}
	}

	@Test
	void theSelfAndMasterRoutesShowTheSalaryMonthsAndTheDueAmount() throws Exception {
		UUID master = seed("MASTER_ADMIN");
		UUID person = seedSystemAdmin();
		UUID instructor = seed("INSTRUCTOR");
		salary(person, "20000", 5, "2026-10-01", null);
		job.runOn(LocalDate.of(2026, 11, 1));
		pay(person, "20000", LocalDate.of(2026, 11, 2));
		String self = token(person);
		String masterToken = token(master);

		JsonNode summary = send(HttpMethod.GET, "/api/v1/me/pay/summary", self, null, 200);
		assertThat(summary.has("nextPayAmount")).isTrue();
		assertThat(summary.path("nextPayDate").isNull()).isFalse();
		assertThat(summary.path("nextPayAmount").isNull()).isFalse();

		JsonNode mine = send(HttpMethod.GET, "/api/v1/me/pay/salary", self, null, 200);
		assertMoney(mine.path("monthlySalary"), "20000");
		assertThat(mine.path("months")).hasSize(1);
		assertThat(mine.path("months").get(0).path("period").asText()).isEqualTo("2026-10");
		assertThat(mine.path("months").get(0).path("dueOn").asText()).isEqualTo("2026-11-05");
		assertThat(mine.path("months").get(0).path("status").asText()).isEqualTo("PAID");

		JsonNode theirs = send(HttpMethod.GET, "/api/v1/staff-pay/people/" + person + "/salary", masterToken, null, 200);
		assertThat(theirs.path("months")).hasSize(1);
		assertThat(theirs.path("salaryStart").asText()).isEqualTo("2026-10-01");

		send(HttpMethod.GET, "/api/v1/staff-pay/people/" + person + "/salary", self, null, 403);
		send(HttpMethod.GET, "/api/v1/me/pay/salary", masterToken, null, 403);

		JsonNode instructorSummary = send(HttpMethod.GET, "/api/v1/me/pay/summary", token(instructor), null, 200);
		// Null fields are left out of the JSON (non_null inclusion), so an instructor has no nextPayAmount key.
		assertThat(instructorSummary.path("nextPayAmount").isMissingNode() || instructorSummary.path("nextPayAmount").isNull()).isTrue();
		assertThat(instructorSummary.path("role").asText()).isEqualTo("INSTRUCTOR");
	}

	private long salaryLines(UUID person) {
		return count("select count(*) from staff_ledger where user_id = ? and kind = 'SALARY'", person);
	}

	/** How many notifications carry this dedupe key: 0 or 1 when the dedupe rule holds. */
	private long notices(String key) {
		return count("select count(*) from staff_notifications where dedupe_key = ?", key);
	}

	private static String dueSoonKey(UUID master, UUID person, String period) {
		return "SALARY_DUE_SOON:" + master + ":" + person + ":" + period;
	}

	private static String overdueKey(UUID master, UUID person, String period, String week) {
		return "SALARY_OVERDUE:" + master + ":" + person + ":" + period + ":" + week;
	}

	private void salary(UUID person, String amount, int payDay, String start, String end) {
		if (end == null) {
			jdbc.update("""
					insert into staff_pay_settings (user_id, monthly_salary, pay_day, salary_start, salary_end, updated_at)
					values (?, ?, ?, ?, null, now())
					""", person, new BigDecimal(amount), payDay, LocalDate.parse(start));
		}
		else {
			jdbc.update("""
					insert into staff_pay_settings (user_id, monthly_salary, pay_day, salary_start, salary_end, updated_at)
					values (?, ?, ?, ?, ?, now())
					""", person, new BigDecimal(amount), payDay, LocalDate.parse(start), LocalDate.parse(end));
		}
	}

	private void pay(UUID person, String amount, LocalDate on) {
		jdbc.update("""
				insert into staff_ledger (id, user_id, kind, amount, due_on, paid_on, method, created_at)
				values (?, ?, 'PAYMENT', ?, ?, ?, 'BANK', now())
				""", UUID.randomUUID(), person, new BigDecimal(amount), on, on);
	}

	private static void assertMoney(JsonNode node, String expected) {
		assertThat(new BigDecimal(node.asText()).compareTo(new BigDecimal(expected))).as(node.toString()).isZero();
	}

	/** Sends one request, expects {@code status}, and returns the body read as JSON (a missing body reads as an empty node). */
	private JsonNode send(HttpMethod method, String path, String token, Object body, int expected) throws Exception {
		MockHttpServletRequestBuilder builder = request(method, path).header("Authorization", "Bearer " + token);
		if (body != null) {
			builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
		}
		MvcResult result = mockMvc.perform(builder).andExpect(status().is(expected)).andReturn();
		String text = result.getResponse().getContentAsString();
		return text.isBlank() ? json.createObjectNode() : json.readTree(text);
	}

	private long count(String sql, Object... args) {
		Long value = jdbc.queryForObject(sql, Long.class, args);
		return value == null ? 0 : value;
	}

	private UUID seedSystemAdmin() {
		UUID id = seed("SYSTEM_ADMIN");
		jdbc.update("update admin_profiles set full_name = 'Salary Test' where user_id = ?", id);
		return id;
	}

	private UUID seed(String role) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, uniquePhone(), "salary-" + id + "@example.com", passwords.encode("password1"), role);
		jdbc.update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, 'Pay Test')", id);
		return id;
	}

	private String token(UUID userId) throws Exception {
		String phone = jdbc.queryForObject("select phone from users where id = ?", String.class, userId);
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
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
