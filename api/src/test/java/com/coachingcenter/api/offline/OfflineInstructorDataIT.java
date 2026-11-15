package com.coachingcenter.api.offline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The data layer of offline instructor pay (plan sections 4.1 to 4.3, 4.4, side effects 2 and 3, Gate 1): the new tables, the case-insensitive
 * instructor names, the archive guard, the rates and how a rate change reaches the classes, the staff and Master Admin roles, the old routine
 * copied into the routines table, and one live payment per class. Every test makes its own rows with random names and ids, so the class
 * can run again on the same database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, OfflineAdminIT.ClockConfig.class })
@TestPropertySource(properties = "app.offline-admission.enabled=true")
class OfflineInstructorDataIT extends PostgresIntegrationTest {

	private static final String MIGRATION = "db/migration/V89__offline_routines_and_classes.sql";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Test
	void migrationsCreateTheNewTables() {
		for (String table : new String[] { "offline_instructors", "offline_batch_instructor_rates", "offline_batch_routines",
				"offline_classes", "offline_class_payments", "offline_class_decisions" }) {
			Long count = jdbc().queryForObject("select count(*) from information_schema.tables where table_name = ?", Long.class, table);
			assertThat(count).as(table).isEqualTo(1L);
		}
	}

	@Test
	void instructorNameIsUniqueIgnoringCaseAmongLiveInstructors() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Master");
		UUID staff = seed("SYSTEM_ADMIN", "Staff");
		String masterToken = token(master);
		String staffToken = token(staff);
		String name = "Tuhin " + suffix();

		JsonNode created = respond(post("/api/v1/offline-instructors", masterToken, "{\"name\":\"" + name + "\",\"phone\":\"01711111111\"}"),
				201);
		assertThat(created.get("name").asText()).isEqualTo(name);
		assertThat(created.get("archived").asBoolean()).isFalse();

		JsonNode duplicate = respond(post("/api/v1/offline-instructors", masterToken, "{\"name\":\"" + name.toUpperCase() + "\"}"), 400);
		assertThat(fieldNames(duplicate)).contains("name");
		JsonNode staffDuplicate = respond(post("/api/v1/offline-instructors", staffToken, "{\"name\":\"" + name.toLowerCase() + "\"}"), 400);
		assertThat(fieldNames(staffDuplicate)).contains("name");

		// A rename onto a live name is refused too, with the field error.
		String other = "Anarul " + suffix();
		JsonNode second = respond(post("/api/v1/offline-instructors", masterToken, "{\"name\":\"" + other + "\"}"), 201);
		JsonNode renamed = respond(patch("/api/v1/offline-instructors/" + second.get("id").asText(), masterToken,
				"{\"name\":\"" + name.toLowerCase() + "\"}"), 400);
		assertThat(fieldNames(renamed)).contains("name");

		// Archived names are free again.
		respond(post("/api/v1/offline-instructors/" + created.get("id").asText() + "/archive", masterToken, null), 200);
		respond(post("/api/v1/offline-instructors", masterToken, "{\"name\":\"" + name.toLowerCase() + "\"}"), 201);
	}

	@Test
	void archiveIsRefusedWhileALiveClassReferencesTheInstructor() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Master");
		String masterToken = token(master);
		UUID instructor = instructor(masterToken, "Milon " + suffix());
		UUID batch = batch(masterToken);
		UUID classId = insertClass(batch, instructor, "SCHEDULED", null, null);

		JsonNode refused = respond(post("/api/v1/offline-instructors/" + instructor + "/archive", masterToken, null), 409);
		assertThat(refused.get("message").asText()).contains("1 offline class still uses this instructor").contains("cannot be archived");

		// A cancelled class no longer counts; the archive then goes through.
		jdbc().update("update offline_classes set status = 'CANCELLED', cancelled_at = now() where id = ?", classId);
		JsonNode archived = respond(post("/api/v1/offline-instructors/" + instructor + "/archive", masterToken, null), 200);
		assertThat(archived.get("archived").asBoolean()).isTrue();
	}

	@Test
	void rateChangeUpdatesUnsettledClassesWithoutOverrideOnly() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Master");
		String masterToken = token(master);
		UUID instructor = instructor(masterToken, "Rafiq " + suffix());
		UUID batch = batch(masterToken);

		JsonNode set = respond(put("/api/v1/offline-batches/" + batch + "/rates/" + instructor, masterToken, "{\"rate\":400}"), 200);
		assertThat(set.get("rate").decimalValue()).isEqualByComparingTo("400");

		UUID unsettled = insertClass(batch, instructor, "SCHEDULED", new BigDecimal("400"), null);
		UUID overridden = insertClass(batch, instructor, "SCHEDULED", new BigDecimal("700"), new BigDecimal("700"));
		UUID paid = insertClass(batch, instructor, "PAID", new BigDecimal("400"), null);
		UUID notTaken = insertClass(batch, instructor, "NOT_TAKEN", new BigDecimal("400"), null);

		respond(put("/api/v1/offline-batches/" + batch + "/rates/" + instructor, masterToken, "{\"rate\":500}"), 200);

		assertThat(amount(unsettled)).isEqualByComparingTo("500");
		assertThat(version(unsettled)).isEqualTo(1L);
		assertThat(amount(overridden)).as("an override keeps its amount").isEqualByComparingTo("700");
		assertThat(amount(paid)).as("a paid class keeps its amount").isEqualByComparingTo("400");
		assertThat(amount(notTaken)).as("a class marked not taken keeps its amount").isEqualByComparingTo("400");

		JsonNode listed = respond(get("/api/v1/offline-batches/" + batch + "/rates", masterToken), 200);
		assertThat(listed.size()).isEqualTo(1);
		assertThat(listed.get(0).get("instructorId").asText()).isEqualTo(instructor.toString());
		assertThat(listed.get(0).get("rate").decimalValue()).isEqualByComparingTo("500");

		// Removing the rate changes no class.
		mockMvc.perform(delete("/api/v1/offline-batches/" + batch + "/rates/" + instructor).header("Authorization", "Bearer " + masterToken))
			.andExpect(status().isNoContent());
		assertThat(amount(unsettled)).isEqualByComparingTo("500");
		assertThat(jdbc().queryForObject("select count(*) from offline_batch_instructor_rates where batch_id = ?", Long.class, batch))
			.isEqualTo(0L);
	}

	@Test
	void ratesAreRefusedWhenNegativeAndStaffCanSetThem() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Master");
		UUID staff = seed("SYSTEM_ADMIN", "Staff");
		String masterToken = token(master);
		String staffToken = token(staff);
		UUID instructor = instructor(masterToken, "Anarul " + suffix());
		UUID batch = batch(masterToken);

		JsonNode negative = respond(put("/api/v1/offline-batches/" + batch + "/rates/" + instructor, masterToken, "{\"rate\":-1}"), 400);
		assertThat(fieldNames(negative)).contains("rate");

		respond(put("/api/v1/offline-batches/" + batch + "/rates/" + instructor, staffToken, "{\"rate\":350}"), 200);
		assertThat(jdbc().queryForObject("select rate from offline_batch_instructor_rates where batch_id = ? and instructor_id = ?",
				BigDecimal.class, batch, instructor)).isEqualByComparingTo("350");
	}

	@Test
	void staffCreateAndRenameInstructorsButCannotArchive() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Master");
		UUID staff = seed("SYSTEM_ADMIN", "Staff");
		String masterToken = token(master);
		String staffToken = token(staff);

		JsonNode created = respond(post("/api/v1/offline-instructors", staffToken, "{\"name\":\"Staff Made " + suffix() + "\"}"), 201);
		String id = created.get("id").asText();
		respond(patch("/api/v1/offline-instructors/" + id, staffToken, "{\"phone\":\"01822222222\",\"aliases\":[\"Tuhin\", \"tuhin\", \" \"]}"),
				200);
		respond(post("/api/v1/offline-instructors/" + id + "/archive", staffToken, null), 403);
		JsonNode archived = respond(post("/api/v1/offline-instructors/" + id + "/archive", masterToken, null), 200);
		assertThat(archived.get("archived").asBoolean()).isTrue();

		// The list hides archived instructors unless asked, and finds a live one by an alias.
		UUID live = instructor(masterToken, "Alias Holder " + suffix());
		respond(patch("/api/v1/offline-instructors/" + live, masterToken, "{\"aliases\":[\"Milon Hossain\"]}"), 200);
		JsonNode found = respond(get("/api/v1/offline-instructors?q=milon hossain", staffToken), 200);
		assertThat(found.toString()).contains(live.toString());

		String archivedName = "Retired " + suffix();
		UUID retired = instructor(masterToken, archivedName);
		respond(post("/api/v1/offline-instructors/" + retired + "/archive", masterToken, null), 200);
		JsonNode liveOnly = respond(get("/api/v1/offline-instructors?q=" + archivedName, staffToken), 200);
		assertThat(liveOnly.toString()).doesNotContain(retired.toString());
		JsonNode withArchived = respond(get("/api/v1/offline-instructors?q=" + archivedName + "&includeArchived=true", staffToken), 200);
		assertThat(withArchived.toString()).contains(retired.toString());
	}

	@Test
	void oldRoutineMigratesAsAConfirmedStaffOnlyRoutine() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Master");
		String masterToken = token(master);
		UUID batch = batch(masterToken);
		String stored = "legacy-" + UUID.randomUUID() + ".pdf";
		// 31 October 20:00 UTC is 1 November 02:00 in Dhaka, so the routine belongs to November.
		jdbc().update("""
				update offline_batches set routine_stored_name = ?, routine_original_name = ?, routine_byte_size = ?,
				  routine_content_type = 'application/pdf', routine_uploaded_by = ?, routine_uploaded_at = ? where id = ?
				""", stored, "20th BJS November.pdf", 12345L, master, Timestamp.from(Instant.parse("2026-10-31T20:00:00Z")), batch);

		runLegacyRoutineCopy();
		runLegacyRoutineCopy();

		assertThat(jdbc().queryForObject("select count(*) from offline_batch_routines where batch_id = ?", Long.class, batch)).isEqualTo(1L);
		var row = jdbc().queryForMap("select * from offline_batch_routines where batch_id = ?", batch);
		assertThat(row.get("status")).isEqualTo("CONFIRMED");
		assertThat(row.get("legacy_original")).isEqualTo(true);
		assertThat(row.get("student_file")).isNull();
		assertThat(row.get("original_file")).isEqualTo(stored);
		assertThat(row.get("original_name")).isEqualTo("20th BJS November.pdf");
		assertThat(row.get("original_byte_size")).isEqualTo(12345L);
		assertThat(row.get("version")).isEqualTo(1);
		assertThat(((Date) row.get("month")).toLocalDate()).isEqualTo(LocalDate.of(2026, 11, 1));
		assertThat(jdbc().queryForObject("select routine_stored_name from offline_batches where id = ?", String.class, batch))
			.as("the old routine columns stay").isEqualTo(stored);

		// The table holds one live (confirmed) routine per batch.
		assertThatThrownBy(() -> jdbc().update("""
				insert into offline_batch_routines (id, batch_id, month, effective_from, version, status, uploaded_at)
				values (?, ?, '2026-11-01', '2026-11-13', 2, 'CONFIRMED', now())
				""", UUID.randomUUID(), batch)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void partialUniqueIndexAllowsOneLivePaymentPerClass() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Master");
		String masterToken = token(master);
		UUID instructor = instructor(masterToken, "Payee " + suffix());
		UUID batch = batch(masterToken);
		UUID classId = insertClass(batch, instructor, "PAID_PENDING", new BigDecimal("400"), null);

		UUID first = insertPayment(classId, instructor, master, "PENDING");
		assertThatThrownBy(() -> insertPayment(classId, instructor, master, "PENDING"))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertPayment(classId, instructor, master, "APPROVED"))
			.isInstanceOf(DataIntegrityViolationException.class);

		// A rejected or voided payment does not count, so a new one may be recorded.
		jdbc().update("update offline_class_payments set status = 'REJECTED' where id = ?", first);
		UUID second = insertPayment(classId, instructor, master, "APPROVED");
		assertThatCode(() -> insertPayment(classId, instructor, master, "VOIDED")).doesNotThrowAnyException();
		assertThat(second).isNotEqualTo(first);
	}

	// ---- helpers --------------------------------------------------------------------------------------------------------------

	private JdbcTemplate jdbc() {
		return new JdbcTemplate(dataSource);
	}

	/** Runs the copy of the old routine from V89, the statement between its two markers, exactly as the migration does. */
	private void runLegacyRoutineCopy() throws IOException {
		String script = new String(new ClassPathResource(MIGRATION).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		int begin = script.indexOf("-- LEGACY_ROUTINE_COPY_BEGIN");
		int end = script.indexOf("-- LEGACY_ROUTINE_COPY_END");
		assertThat(begin).isGreaterThanOrEqualTo(0);
		assertThat(end).isGreaterThan(begin);
		// V92 made effective_from required, and this copy is the V89 statement, which predates it. For the replay only, the column
		// takes the routine's month as its default, then the default is removed again.
		jdbc().execute("alter table offline_batch_routines alter column effective_from set default date '2026-11-01'");
		try {
			jdbc().execute(script.substring(begin, end));
		}
		finally {
			jdbc().execute("alter table offline_batch_routines alter column effective_from drop default");
		}
	}

	private UUID insertClass(UUID batch, UUID instructor, String status, BigDecimal amount, BigDecimal override) {
		UUID id = UUID.randomUUID();
		jdbc().update("""
				insert into offline_classes (id, batch_id, held_on, subject, kind, instructor_id, instructor_text, amount, rate_override,
				  status, created_at, updated_at, version)
				values (?, ?, ?, 'Evidence Act', 'LECTURE', ?, 'Test', ?, ?, ?, now(), now(), 0)
				""", id, batch, Date.valueOf(LocalDate.of(2026, 11, 3)), instructor, amount, override, status);
		return id;
	}

	private UUID insertPayment(UUID classId, UUID instructor, UUID recordedBy, String status) {
		UUID id = UUID.randomUUID();
		jdbc().update("""
				insert into offline_class_payments (id, class_id, instructor_id, amount, method, paid_on, status, recorded_by, recorded_at,
				  version)
				values (?, ?, ?, 400, 'CASH', ?, ?, ?, now(), 0)
				""", id, classId, instructor, Date.valueOf(LocalDate.of(2026, 11, 4)), status, recordedBy);
		return id;
	}

	private BigDecimal amount(UUID classId) {
		return jdbc().queryForObject("select amount from offline_classes where id = ?", BigDecimal.class, classId);
	}

	private long version(UUID classId) {
		Long version = jdbc().queryForObject("select version from offline_classes where id = ?", Long.class, classId);
		return version == null ? -1L : version;
	}

	private UUID instructor(String token, String name) throws Exception {
		JsonNode created = respond(post("/api/v1/offline-instructors", token, "{\"name\":\"" + name + "\"}"), 201);
		return UUID.fromString(created.get("id").asText());
	}

	private UUID batch(String token) throws Exception {
		MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/offline-batches").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"nameEn\":\"BJS Evening\",\"nameBn\":\"বিজেএস সন্ধ্যা\",\"endsOn\":\"2027-04-30\",\"courseFee\":3000}"))
			.andExpect(status().isCreated())
			.andReturn();
		UUID id = UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
		// A batch starts as a draft (V103); the helper publishes it, as the Master Admin does before students apply.
		mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/offline-batches/" + id + "/publish").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		return id;
	}

	private ResultActions post(String url, String token, String body) throws Exception {
		var request = MockMvcRequestBuilders.post(url).header("Authorization", "Bearer " + token);
		if (body != null) {
			request = request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mockMvc.perform(request);
	}

	private ResultActions patch(String url, String token, String body) throws Exception {
		return mockMvc.perform(MockMvcRequestBuilders.patch(url).header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body == null ? "{}" : body));
	}

	private ResultActions put(String url, String token, String body) throws Exception {
		return mockMvc.perform(MockMvcRequestBuilders.put(url).header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private ResultActions get(String url, String token) throws Exception {
		return mockMvc.perform(MockMvcRequestBuilders.get(url).header("Authorization", "Bearer " + token));
	}

	/** A request answered with the expected status; returns its JSON body. */
	private JsonNode respond(ResultActions actions, int expected) throws Exception {
		MvcResult result = actions.andExpect(status().is(expected)).andReturn();
		String text = result.getResponse().getContentAsString();
		return text.isBlank() ? json.createObjectNode() : json.readTree(text);
	}

	private static Set<String> fieldNames(JsonNode refusal) {
		Set<String> fields = new HashSet<>();
		refusal.get("fieldErrors").forEach(error -> fields.add(error.get("field").asText()));
		return fields;
	}

	private UUID seed(String role, String name) {
		UUID id = UUID.randomUUID();
		jdbc().update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, "+8801" + ThreadLocalRandom.current().nextInt(300000000, 999999999), "offline-pay-" + id + "@example.com",
				passwords.encode("password1"), role);
		jdbc().update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, ?)", id, name);
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

	private static String suffix() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

}
