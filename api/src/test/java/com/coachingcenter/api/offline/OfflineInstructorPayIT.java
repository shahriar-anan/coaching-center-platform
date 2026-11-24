package com.coachingcenter.api.offline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Instructor pay made simple (24 Nov, instructor-pay-simple-plan.md): the Master Admin records a payment by hand (type, classes, amount,
 * method, reference, optional batch, description), it counts at once, it can be corrected and voided, and the class payments made before
 * show with the new ones. A System Admin is refused every pay route and cannot add instructors. Each test makes its own people, batches
 * and instructors, so the class can run again on the same database. The clock is fixed at 10:00 in Dhaka on 24 November 2026.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, OfflineInstructorPayIT.ClockConfig.class })
@TestPropertySource(properties = "app.offline-admission.enabled=true")
class OfflineInstructorPayIT extends PostgresIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-11-24T04:00:00Z");

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

	@Test
	void aPaymentRecordedByTheMasterAdminCountsAtOnce() throws Exception {
		Setup s = setup();
		UUID instructor = instructor(s.master, "Record");
		UUID batch = batch(s.master);
		BigDecimal before = expense(s.master, "2026-11-23");

		JsonNode paid = respond(post(payUrl(instructor), s.master, "{\"paidOn\":\"2026-11-23\",\"kind\":\"LECTURE\",\"classCount\":4,"
				+ "\"amount\":\"1600\",\"method\":\"BKASH\",\"reference\":\"TX-77\",\"batchId\":\"" + batch + "\"}"), 201);
		assertThat(paid.get("status").asText()).isEqualTo("APPROVED");
		assertThat(paid.get("recordKind").asText()).isEqualTo("OTHER");
		assertThat(paid.get("kind").asText()).isEqualTo("LECTURE");
		assertThat(paid.get("classCount").asInt()).isEqualTo(4);
		assertThat(paid.get("batchId").asText()).isEqualTo(batch.toString());
		assertThat(paid.hasNonNull("description")).isFalse();
		assertThat(expense(s.master, "2026-11-23")).isEqualByComparingTo(before.add(new BigDecimal("1600")));

		JsonNode history = respondTo(get(payUrl(instructor)).header("Authorization", "Bearer " + s.master), 200);
		assertThat(history.get("items")).hasSize(1);
		assertThat(history.get("total").decimalValue()).isEqualByComparingTo("1600");

		JsonNode summary = respondTo(get("/api/v1/offline-instructors/" + instructor + "/pay-summary").header("Authorization",
				"Bearer " + s.master), 200);
		assertThat(summary.get("totalPaid").decimalValue()).isEqualByComparingTo("1600");
		assertThat(summary.get("paidThisMonth").decimalValue()).isEqualByComparingTo("1600");
		assertThat(summary.get("lastPaidOn").asText()).isEqualTo("2026-11-23");
		assertThat(summary.get("payments").asLong()).isEqualTo(1L);

		// The Finance entry names the type when there is no description.
		JsonNode entries = respondTo(get("/api/v1/finance/entries?from=2026-11-23&to=2026-11-23&sources=OFFLINE_INSTRUCTORS&size=200")
			.header("Authorization", "Bearer " + s.master), 200).get("items");
		assertThat(entries.toString()).contains("Instructor pay: Lecture");
	}

	@Test
	void theFormIsCheckedAsItIsOnTheScreen() throws Exception {
		Setup s = setup();
		UUID instructor = instructor(s.master, "Checks");
		String url = payUrl(instructor);

		assertThat(fields(respond(post(url, s.master, body("OTHER", "500", "2026-11-23", null)), 400))).contains("description");
		assertThat(fields(respond(post(url, s.master, body("LECTURE", "500", "2026-11-25", null)), 400))).contains("paidOn");
		assertThat(fields(respond(post(url, s.master, body(null, "500", "2026-11-23", null)), 400))).contains("kind");
		assertThat(fields(respond(post(url, s.master, body("LECTURE", "0", "2026-11-23", null)), 400))).contains("amount");
		assertThat(fields(respond(post(url, s.master, "{\"paidOn\":\"2026-11-23\",\"kind\":\"EXAM\",\"classCount\":0,\"amount\":\"500\","
				+ "\"method\":\"CASH\"}"), 400))).contains("classCount");
		assertThat(fields(respond(post(url, s.master, "{\"paidOn\":\"2026-11-23\",\"kind\":\"EXAM\",\"amount\":\"500\",\"method\":\"CHEQUE\"}"),
				400))).contains("method");

		// A past date, no batch, no reference: fine. Other with a description: fine.
		respond(post(url, s.master, body("EXAM", "500", "2026-01-05", null)), 201);
		JsonNode other = respond(post(url, s.master, body("OTHER", "250", "2026-11-24", "Travel to the exam hall")), 201);
		assertThat(other.get("description").asText()).isEqualTo("Travel to the exam hall");
	}

	@Test
	void aCorrectionAppliesAtOnceAndIsKeptInPaymentRecords() throws Exception {
		Setup s = setup();
		UUID instructor = instructor(s.master, "Correct");
		UUID id = id(respond(post(payUrl(instructor), s.master, body("LECTURE", "1000", "2026-11-20", null)), 201));
		String url = "/api/v1/offline-instructor-payments/OTHER/" + id;

		assertThat(fields(respond(patch(url, s.master, "{\"amount\":\"900\"}"), 400))).contains("editReason");
		respond(patch(url, s.master, "{\"amount\":\"1000\",\"editReason\":\"Nothing\"}"), 400);
		JsonNode corrected = respond(patch(url, s.master, "{\"amount\":\"900\",\"kind\":\"EXAM\",\"classCount\":2,"
				+ "\"editReason\":\"Two exams, not lectures\"}"), 200);
		assertThat(corrected.get("amount").decimalValue()).isEqualByComparingTo("900");
		assertThat(corrected.get("kind").asText()).isEqualTo("EXAM");
		assertThat(corrected.get("classCount").asInt()).isEqualTo(2);
		assertThat(corrected.hasNonNull("correctedAt")).isTrue();
		assertThat(jdbc().queryForObject("select status from payment_edits where kind = 'OTHER' and payment_id = ?", String.class, id))
			.isEqualTo("FINALIZED");

		JsonNode rows = respondTo(get("/api/v1/payment-records").param("kind", "OTHER").param("instructorId", instructor.toString())
			.header("Authorization", "Bearer " + s.master), 200);
		assertThat(rows).hasSize(1);
		assertThat(rows.get(0).get("amount").decimalValue()).isEqualByComparingTo("900");
		assertThat(rows.get(0).get("itemNameEn").asText()).isEqualTo("Exam");
	}

	@Test
	void aVoidedPaymentLeavesTheTotalsAndCannotChangeAgain() throws Exception {
		Setup s = setup();
		UUID instructor = instructor(s.master, "Void");
		UUID id = id(respond(post(payUrl(instructor), s.master, body("LECTURE", "700", "2026-11-22", null)), 201));
		String url = "/api/v1/offline-instructor-payments/OTHER/" + id;

		respond(post(url + "/void", s.master, "{}"), 400);
		JsonNode voided = respond(post(url + "/void", s.master, "{\"reason\":\"Entered twice\"}"), 200);
		assertThat(voided.get("status").asText()).isEqualTo("VOIDED");
		assertThat(voided.get("voidReason").asText()).isEqualTo("Entered twice");
		JsonNode summary = respondTo(get("/api/v1/offline-instructors/" + instructor + "/pay-summary").header("Authorization",
				"Bearer " + s.master), 200);
		assertThat(summary.get("totalPaid").decimalValue()).isEqualByComparingTo("0");
		assertThat(summary.get("payments").asLong()).isZero();

		respond(post(url + "/void", s.master, "{\"reason\":\"Again\"}"), 409);
		respond(patch(url, s.master, "{\"amount\":\"100\",\"editReason\":\"Too late\"}"), 409);
	}

	@Test
	void aClassPaymentFromBeforeShowsInTheHistoryAndOnlyItsMoneyFieldsChange() throws Exception {
		Setup s = setup();
		UUID instructor = instructor(s.master, "Old");
		UUID batch = batch(s.master);
		UUID classId = oldPaidClass(batch, instructor, "EXAM", "2026-11-10");
		UUID payment = oldClassPayment(classId, instructor, "500", "2026-11-11");

		JsonNode history = respondTo(get(payUrl(instructor)).header("Authorization", "Bearer " + s.master), 200).get("items");
		assertThat(history).hasSize(1);
		JsonNode row = history.get(0);
		assertThat(row.get("recordKind").asText()).isEqualTo("CLASS");
		assertThat(row.get("kind").asText()).isEqualTo("EXAM");
		assertThat(row.get("classCount").asInt()).isEqualTo(1);
		assertThat(row.get("classOn").asText()).isEqualTo("2026-11-10");
		assertThat(row.get("batchId").asText()).isEqualTo(batch.toString());

		String url = "/api/v1/offline-instructor-payments/CLASS/" + payment;
		JsonNode corrected = respond(patch(url, s.master, "{\"amount\":\"450\",\"kind\":\"LECTURE\",\"editReason\":\"Paid less\"}"), 200);
		assertThat(corrected.get("amount").decimalValue()).isEqualByComparingTo("450");
		assertThat(corrected.get("kind").asText()).as("the type comes from the class").isEqualTo("EXAM");

		respond(post(url + "/void", s.master, "{\"reason\":\"Wrong instructor\"}"), 200);
		assertThat(jdbc().queryForObject("select status from offline_classes where id = ?", String.class, classId)).isEqualTo("SCHEDULED");
	}

	@Test
	void thePaymentsPageListsEveryInstructorAndFilters() throws Exception {
		Setup s = setup();
		UUID first = instructor(s.master, "First");
		UUID second = instructor(s.master, "Second");
		UUID batch = batch(s.master);
		respond(post(payUrl(first), s.master, "{\"paidOn\":\"2026-11-20\",\"kind\":\"LECTURE\",\"amount\":\"300\",\"method\":\"CASH\","
				+ "\"batchId\":\"" + batch + "\"}"), 201);
		respond(post(payUrl(second), s.master, body("EXAM", "200", "2026-11-21", null)), 201);
		oldClassPayment(oldPaidClass(batch, second, "LECTURE", "2026-11-02"), second, "150", "2026-11-03");

		JsonNode inBatch = respondTo(get("/api/v1/offline-instructor-payments").param("batchId", batch.toString())
			.header("Authorization", "Bearer " + s.master), 200);
		assertThat(inBatch.get("items")).hasSize(2);
		assertThat(inBatch.get("total").decimalValue()).isEqualByComparingTo("450");

		JsonNode exams = respondTo(get("/api/v1/offline-instructor-payments").param("kind", "EXAM").param("instructorId", second.toString())
			.header("Authorization", "Bearer " + s.master), 200);
		assertThat(exams.get("items")).hasSize(1);
		assertThat(exams.get("items").get(0).get("instructorName").asText()).startsWith("Second");

		JsonNode cards = respondTo(get("/api/v1/offline-instructor-payments/cards").header("Authorization", "Bearer " + s.master), 200);
		assertThat(cards.get("thisMonth").get("amount").decimalValue()).isGreaterThanOrEqualTo(new BigDecimal("650"));

		JsonNode figures = respondTo(get("/api/v1/offline-instructor-payments/figures").header("Authorization", "Bearer " + s.master), 200);
		assertThat(figures.get(second.toString()).get("paidThisMonth").decimalValue()).isEqualByComparingTo("350");
		assertThat(figures.get(second.toString()).get("batches").get(0).get("id").asText()).isEqualTo(batch.toString());
	}

	@Test
	void aSystemAdminIsRefusedEveryPayRouteButStillReadsTheInstructors() throws Exception {
		Setup s = setup();
		UUID instructor = instructor(s.master, "Refused");
		UUID id = id(respond(post(payUrl(instructor), s.master, body("LECTURE", "100", "2026-11-20", null)), 201));

		respond(post(payUrl(instructor), s.staff, body("LECTURE", "100", "2026-11-20", null)), 403);
		respondTo(get(payUrl(instructor)).header("Authorization", "Bearer " + s.staff), 403);
		respondTo(get("/api/v1/offline-instructors/" + instructor + "/pay-summary").header("Authorization", "Bearer " + s.staff), 403);
		respondTo(get("/api/v1/offline-instructor-payments").header("Authorization", "Bearer " + s.staff), 403);
		respondTo(get("/api/v1/offline-instructor-payments/cards").header("Authorization", "Bearer " + s.staff), 403);
		respondTo(get("/api/v1/offline-instructor-payments/figures").header("Authorization", "Bearer " + s.staff), 403);
		respond(patch("/api/v1/offline-instructor-payments/OTHER/" + id, s.staff, "{\"amount\":\"90\",\"editReason\":\"x\"}"), 403);
		respond(post("/api/v1/offline-instructor-payments/OTHER/" + id + "/void", s.staff, "{\"reason\":\"x\"}"), 403);

		// Instructors: a System Admin picks from the list, but only the Master Admin adds, edits or archives one.
		respondTo(get("/api/v1/offline-instructors").header("Authorization", "Bearer " + s.staff), 200);
		respond(post("/api/v1/offline-instructors", s.staff, "{\"name\":\"Staff made " + UUID.randomUUID() + "\"}"), 403);
		respond(patch("/api/v1/offline-instructors/" + instructor, s.staff, "{\"phone\":\"01712345678\"}"), 403);
		respond(post("/api/v1/offline-instructors/" + instructor + "/archive", s.staff, null), 403);

		// Payment records: a System Admin no longer sees instructor payments.
		JsonNode rows = respondTo(get("/api/v1/payment-records").param("instructorId", instructor.toString())
			.header("Authorization", "Bearer " + s.staff), 200);
		assertThat(rows).isEmpty();
	}

	// ---- helpers ----------------------------------------------------------------------------------------------------------

	private static String payUrl(UUID instructor) {
		return "/api/v1/offline-instructors/" + instructor + "/payments";
	}

	/** A payment body: the type (or none), the amount, the date and the description (or none), paid in cash. */
	private static String body(String kind, String amount, String paidOn, String description) {
		return "{\"paidOn\":\"" + paidOn + "\"" + (kind == null ? "" : ",\"kind\":\"" + kind + "\"") + ",\"amount\":\"" + amount
				+ "\",\"method\":\"CASH\"" + (description == null ? "" : ",\"description\":\"" + description + "\"") + "}";
	}

	private static List<String> fields(JsonNode refusal) {
		List<String> names = new ArrayList<>();
		JsonNode details = refusal.has("fieldErrors") ? refusal.get("fieldErrors") : refusal.path("details");
		details.forEach(detail -> names.add(detail.path("field").asText()));
		if (names.isEmpty()) {
			names.add(refusal.toString());
		}
		return names;
	}

	/** A class settled before 24 Nov: paid, of the given kind. */
	private UUID oldPaidClass(UUID batch, UUID instructor, String kind, String heldOn) {
		UUID id = UUID.randomUUID();
		jdbc().update("""
				insert into offline_classes (id, batch_id, held_on, starts_at, ends_at, subject, kind, instructor_id, amount, status, created_at,
				  updated_at)
				values (?, ?, ?, '10:00', '11:20', 'Contract Act', ?, ?, 500, 'PAID', now(), now())
				""", id, batch, java.sql.Date.valueOf(heldOn), kind, instructor);
		return id;
	}

	/** A class payment as it was recorded before 24 Nov, approved. */
	private UUID oldClassPayment(UUID classId, UUID instructor, String amount, String paidOn) {
		UUID id = UUID.randomUUID();
		jdbc().update("""
				insert into offline_class_payments (id, class_id, instructor_id, amount, method, reference, paid_on, status, recorded_by,
				  recorded_at)
				values (?, ?, ?, ?, 'CASH', 'OLD-1', ?, 'APPROVED', ?, now())
				""", id, classId, instructor, new BigDecimal(amount), java.sql.Date.valueOf(paidOn), adminOf());
		return id;
	}

	private BigDecimal expense(String token, String day) throws Exception {
		return respondTo(get("/api/v1/finance/summary?from=" + day + "&to=" + day).header("Authorization", "Bearer " + token), 200)
			.get("expense").decimalValue();
	}

	private record Setup(String master, String staff) {
	}

	private Setup setup() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		return new Setup(master, staff);
	}

	private JdbcTemplate jdbc() {
		return new JdbcTemplate(dataSource);
	}

	private ResultActions post(String url, String token, String body) throws Exception {
		MockHttpServletRequestBuilder request = MockMvcRequestBuilders.post(url).header("Authorization", "Bearer " + token);
		if (body != null) {
			request = request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mockMvc.perform(request);
	}

	private ResultActions patch(String url, String token, String body) throws Exception {
		return mockMvc.perform(MockMvcRequestBuilders.patch(url).header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private JsonNode respond(ResultActions actions, int expected) throws Exception {
		MvcResult result = actions.andExpect(status().is(expected)).andReturn();
		String text = result.getResponse().getContentAsString();
		return text.isBlank() ? json.createObjectNode() : json.readTree(text);
	}

	private JsonNode respondTo(RequestBuilder request, int expected) throws Exception {
		return respond(mockMvc.perform(request), expected);
	}

	private static UUID id(JsonNode node) {
		return UUID.fromString(node.get("id").asText());
	}

	private UUID batch(String token) throws Exception {
		JsonNode created = respond(post("/api/v1/offline-batches", token, "{\"nameEn\":\"Pay\",\"nameBn\":\"পেমেন্ট\","
				+ "\"endsOn\":\"2027-06-30\",\"courseFee\":3000}"), 201);
		return UUID.fromString(created.get("id").asText());
	}

	private UUID instructor(String token, String label) throws Exception {
		String name = label + " " + UUID.randomUUID();
		JsonNode created = respond(post("/api/v1/offline-instructors", token, "{\"name\":\"" + name + "\"}"), 201);
		return UUID.fromString(created.get("id").asText());
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

	private UUID adminOf() {
		return jdbc().queryForObject("select id from users where role = 'MASTER_ADMIN' order by created_at desc limit 1", UUID.class);
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

	@TestConfiguration
	static class ClockConfig {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(NOW);
		}

	}

}
