package com.coachingcenter.api.staffnotifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

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
import org.springframework.test.web.servlet.MvcResult;

import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The staff notification centre (staff pay plan, phase 1): the writer's insert and dedupe rule, each person seeing only their
 * own rows, the bell's count, marking read, and the routes refusing a student.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class StaffNotificationIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Autowired
	private StaffNotificationWriter writer;

	private JdbcTemplate jdbc;

	@BeforeEach
	void setUp() {
		jdbc = new JdbcTemplate(dataSource);
	}

	@Test
	void aNotificationReachesItsStaffRecipientOnlyAndTheWriterSkipsAStudent() throws Exception {
		UUID master = seed("MASTER_ADMIN");
		UUID other = seed("INSTRUCTOR");
		UUID student = seed("STUDENT");

		assertThat(writer.notify(master, StaffNotificationKind.PAYOUT_ACCOUNT_CHANGED,
				Map.of("personName", "Rahim", "change", "UPDATED"), "/staff/pay", null)).isTrue();
		assertThat(writer.notify(student, StaffNotificationKind.PAYMENT_RECORDED, Map.of("amount", "৳500", "method", "BKASH"),
				"/staff/my-pay", null)).isFalse();

		assertThat(count("select count(*) from staff_notifications where recipient_id = ?", student)).isZero();
		assertThat(count("select count(*) from staff_notifications where recipient_id = ?", other)).isZero();

		String masterToken = token(master);
		JsonNode page = json.readTree(mockMvc.perform(get("/api/v1/me/staff-notifications")
			.header("Authorization", "Bearer " + masterToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andReturn()
			.getResponse()
			.getContentAsString());
		JsonNode row = page.get("content").get(0);
		assertThat(row.get("kind").asText()).isEqualTo("PAYOUT_ACCOUNT_CHANGED");
		assertThat(row.get("link").asText()).isEqualTo("/staff/pay");
		assertThat(row.get("payload").get("personName").asText()).isEqualTo("Rahim");
		// The API leaves null fields out of its JSON (non_null inclusion), so an unread row has no readAt key at all.
		assertThat(row.path("readAt").isMissingNode() || row.path("readAt").isNull()).isTrue();

		mockMvc.perform(get("/api/v1/me/staff-notifications").header("Authorization", "Bearer " + token(other)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void aRepeatedDedupeKeyAddsNothing() {
		UUID instructor = seed("INSTRUCTOR");
		String key = "MARKING_PUBLISHED_EARNED:" + instructor + ":exam-1";

		boolean first = writer.notify(instructor, StaffNotificationKind.MARKING_PUBLISHED_EARNED,
				Map.of("sheets", 3, "amount", "৳150"), "/staff/my-pay", key);
		boolean second = writer.notify(instructor, StaffNotificationKind.MARKING_PUBLISHED_EARNED,
				Map.of("sheets", 3, "amount", "৳150"), "/staff/my-pay", key);

		assertThat(first).isTrue();
		assertThat(second).isFalse();
		assertThat(count("select count(*) from staff_notifications where dedupe_key = ?", key)).isEqualTo(1);
	}

	@Test
	void aDedupeKeyIsUniqueAcrossPeopleSoAProducerPutsTheRecipientInIt() {
		UUID first = seed("SYSTEM_ADMIN");
		UUID second = seed("SYSTEM_ADMIN");
		// Unique per run: the test database keeps its rows between runs, so a fixed key would already exist on a second run.
		String shared = "SALARY_DUE_SOON:" + UUID.randomUUID();

		assertThat(writer.notify(first, StaffNotificationKind.SALARY_DUE_SOON, Map.of("period", "2026-11"), "/staff/pay", shared))
			.isTrue();
		assertThat(writer.notify(second, StaffNotificationKind.SALARY_DUE_SOON, Map.of("period", "2026-11"), "/staff/pay", shared))
			.isFalse();
	}

	@Test
	void theUnreadCountMarkingOneReadAndMarkingAllRead() throws Exception {
		UUID admin = seed("SYSTEM_ADMIN");
		for (int i = 0; i < 3; i++) {
			writer.notify(admin, StaffNotificationKind.SALARY_OVERDUE, Map.of("period", "2026-0" + (i + 1)), "/staff/my-pay",
					"overdue:" + admin + ":" + i);
		}
		String token = token(admin);

		mockMvc.perform(get("/api/v1/me/staff-notifications/unread-count").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.count").value(3));

		UUID one = jdbc.queryForObject("select id from staff_notifications where recipient_id = ? limit 1", UUID.class, admin);
		mockMvc.perform(post("/api/v1/me/staff-notifications/" + one + "/read").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.readAt").isNotEmpty());
		Instant firstRead = readAt(one);

		mockMvc.perform(post("/api/v1/me/staff-notifications/" + one + "/read").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isOk());
		assertThat(readAt(one)).isEqualTo(firstRead);

		mockMvc.perform(get("/api/v1/me/staff-notifications/unread-count").header("Authorization", "Bearer " + token))
			.andExpect(jsonPath("$.count").value(2));
		mockMvc.perform(get("/api/v1/me/staff-notifications").param("unread", "true").header("Authorization", "Bearer " + token))
			.andExpect(jsonPath("$.totalElements").value(2));

		mockMvc.perform(post("/api/v1/me/staff-notifications/read-all").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.updated").value(2));
		mockMvc.perform(get("/api/v1/me/staff-notifications/unread-count").header("Authorization", "Bearer " + token))
			.andExpect(jsonPath("$.count").value(0));
	}

	@Test
	void aPersonCannotReadOrMarkAnotherPersonsNotification() throws Exception {
		UUID owner = seed("INSTRUCTOR");
		UUID stranger = seed("MASTER_ADMIN");
		writer.notify(owner, StaffNotificationKind.PAYMENT_VOIDED, Map.of("amount", "৳200"), "/staff/my-pay", null);
		UUID row = jdbc.queryForObject("select id from staff_notifications where recipient_id = ?", UUID.class, owner);

		mockMvc.perform(post("/api/v1/me/staff-notifications/" + row + "/read").header("Authorization", "Bearer " + token(stranger))
			.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isNotFound());
		assertThat(readAt(row)).isNull();
	}

	@Test
	void aStudentIsRefusedEveryRouteAndASignedOutCallerIsNotAuthenticated() throws Exception {
		UUID student = seed("STUDENT");
		String token = token(student);

		mockMvc.perform(get("/api/v1/me/staff-notifications").header("Authorization", "Bearer " + token))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/me/staff-notifications/unread-count").header("Authorization", "Bearer " + token))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/me/staff-notifications/read-all").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/me/staff-notifications")).andExpect(status().isUnauthorized());
	}

	@Test
	void aDeletedStaffMemberGetsNothingAndABadLinkIsRefused() {
		UUID gone = seed("INSTRUCTOR");
		jdbc.update("update users set deleted_at = now() where id = ?", gone);

		assertThat(writer.notify(gone, StaffNotificationKind.MARKING_UNPAID_REMINDER, Map.of("days", 20), "/staff/pay", null))
			.isFalse();
		assertThatThrownBy(() -> writer.notify(gone, StaffNotificationKind.MARKING_UNPAID_REMINDER, Map.of("days", 20),
				"https://elsewhere.example/", null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> writer.notify(gone, StaffNotificationKind.MARKING_UNPAID_REMINDER,
				Map.of("accountNumber", "1234"), "/staff/pay", null)).isInstanceOf(IllegalArgumentException.class);
	}

	private Instant readAt(UUID id) {
		Timestamp at = jdbc.queryForObject("select read_at from staff_notifications where id = ?", Timestamp.class, id);
		return at == null ? null : at.toInstant();
	}

	private long count(String sql, Object... args) {
		Long total = jdbc.queryForObject(sql, Long.class, args);
		return total == null ? 0 : total;
	}

	private UUID seed(String role) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, uniquePhone(), "notice-" + id + "@example.com", passwords.encode("password1"), role);
		if ("STUDENT".equals(role)) {
			jdbc.update("INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, 'Seed', ?)", id,
					"S" + id.toString().substring(0, 8));
		}
		else {
			jdbc.update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, 'Staff')", id);
		}
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
