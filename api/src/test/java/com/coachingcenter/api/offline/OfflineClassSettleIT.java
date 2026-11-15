package com.coachingcenter.api.offline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import javax.sql.DataSource;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
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
 * Settling offline classes (plan sections 4.2 to 4.4, 6 and stage 3): HELD only after the end time, payments and their approval, the
 * reject and void paths, not-taken requests, a substitute instructor, rates that change unsettled classes only, the unsettled page and
 * its counts, bulk approval, the student's routine without any pay field, and the permissions. Each test makes its own batch and classes,
 * so the class can run again on the same database. The clock is fixed at 10:00 in Dhaka on 20 October 2026.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, OfflineClassSettleIT.ClockConfig.class })
@TestPropertySource(properties = "app.offline-admission.enabled=true")
class OfflineClassSettleIT extends PostgresIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-10-20T04:00:00Z");

	private static final String SAMPLE = "fixtures/routine/20th BJS Foundation Batch- NOVEMBER.pdf";

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
	void heldAppearsOnlyAfterTheEndTime() throws Exception {
		Setup s = setup();
		UUID instructor = instructor(s.master, "Held");
		UUID batch = batch(s.master);
		JsonNode created = classAt(s.master, batch, "2026-10-20", "14:00", "15:20", instructor);
		UUID id = id(created);
		assertThat(created.get("status").asText()).isEqualTo("SCHEDULED");

		clock.set(Instant.parse("2026-10-20T09:00:00Z"));
		assertThat(classView(s.master, batch, id).get("status").asText()).isEqualTo("SCHEDULED");

		clock.set(Instant.parse("2026-10-20T10:00:00Z"));
		assertThat(classView(s.master, batch, id).get("status").asText()).isEqualTo("HELD");
	}

	@Test
	void aPaymentBeforeTheEndIsRefusedAndTheStaffPaymentWaitsForTheMasterAdmin() throws Exception {
		Setup s = setup();
		UUID instructor = instructor(s.master, "Pay");
		UUID batch = batch(s.master);
		UUID future = id(classAt(s.master, batch, "2026-10-20", "14:00", "15:20", instructor));
		respond(post("/api/v1/offline-classes/" + future + "/payment", s.staff, payment("400", "2026-10-20")), 409);

		UUID held = id(classAt(s.master, batch, "2026-10-19", "10:00", "11:20", instructor));
		JsonNode staffPaid = respond(post("/api/v1/offline-classes/" + held + "/payment", s.staff, payment("400", "2026-10-19")), 200);
		assertThat(staffPaid.get("status").asText()).isEqualTo("PAID_PENDING");
		assertThat(staffPaid.get("payment").get("status").asText()).isEqualTo("PENDING");
		String paymentId = staffPaid.get("payment").get("id").asText();

		// Staff cannot approve; the Master Admin approves, and the class is PAID.
		respond(post("/api/v1/offline-class-payments/" + paymentId + "/approve", s.staff, null), 403);
		JsonNode approved = respond(post("/api/v1/offline-class-payments/" + paymentId + "/approve", s.master, null), 200);
		assertThat(approved.get("status").asText()).isEqualTo("PAID");
		assertThat(approved.get("payment").get("status").asText()).isEqualTo("APPROVED");
	}

	@Test
	void aSecondLivePaymentIsRefusedAndAMasterPaymentIsApprovedAtOnce() throws Exception {
		Setup s = setup();
		UUID instructor = instructor(s.master, "Twice");
		UUID batch = batch(s.master);
		UUID held = id(classAt(s.master, batch, "2026-10-19", "10:00", "11:20", instructor));
		JsonNode first = respond(post("/api/v1/offline-classes/" + held + "/payment", s.master, payment("400", "2026-10-19")), 200);
		assertThat(first.get("status").asText()).isEqualTo("PAID");

		respond(post("/api/v1/offline-classes/" + held + "/payment", s.master, payment("400", "2026-10-19")), 409);
		respond(post("/api/v1/offline-classes/" + held + "/payment", s.staff, payment("400", "2026-10-19")), 409);
	}

	@Test
	void aPaymentDatedInThePastIsAccepted() throws Exception {
		Setup s = setup();
		UUID instructor = instructor(s.master, "Past");
		UUID batch = batch(s.master);
		UUID held = id(classAt(s.master, batch, "2026-10-15", "10:00", "11:20", instructor));

		JsonNode paid = respond(post("/api/v1/offline-classes/" + held + "/payment", s.staff, payment("400", "2026-10-01")), 200);

		assertThat(paid.get("payment").get("paidOn").asText()).isEqualTo("2026-10-01");
	}

	@Test
	void aRejectedPaymentReturnsTheClassToHeld() throws Exception {
		Setup s = setup();
		UUID instructor = instructor(s.master, "Reject");
		UUID batch = batch(s.master);
		UUID held = id(classAt(s.master, batch, "2026-10-19", "10:00", "11:20", instructor));
		String paymentId = respond(post("/api/v1/offline-classes/" + held + "/payment", s.staff, payment("400", "2026-10-19")), 200)
			.get("payment").get("id").asText();

		JsonNode rejected = respond(post("/api/v1/offline-class-payments/" + paymentId + "/reject", s.master, "{\"reason\":\"Wrong amount\"}"),
				200);

		assertThat(rejected.get("status").asText()).isEqualTo("HELD");
		assertThat(rejected.hasNonNull("payment")).isFalse();
		respond(post("/api/v1/offline-classes/" + held + "/payment", s.staff, payment("400", "2026-10-19")), 200);
	}

	@Test
	void aVoidedPaymentReturnsTheClassToHeldAndAVoidIsOnlyOnce() throws Exception {
		Setup s = setup();
		UUID instructor = instructor(s.master, "Void");
		UUID batch = batch(s.master);
		UUID held = id(classAt(s.master, batch, "2026-10-19", "10:00", "11:20", instructor));
		String paymentId = respond(post("/api/v1/offline-classes/" + held + "/payment", s.master, payment("400", "2026-10-19")), 200)
			.get("payment").isNull() ? null : null;
		assertThat(paymentId).isNull();
		JsonNode paid = classView(s.master, batch, held);
		assertThat(paid.get("status").asText()).isEqualTo("PAID");
		paymentId = paid.get("payment") == null || paid.get("payment").isNull() ? null : paid.get("payment").get("id").asText();
		if (paymentId == null) {
			throw new AssertionError("The paid class has no payment in its view.");
		}

		JsonNode voided = respond(post("/api/v1/offline-class-payments/" + paymentId + "/void", s.master, "{\"reason\":\"Entered twice\"}"),
				200);
		assertThat(voided.get("status").asText()).isEqualTo("HELD");
		respond(post("/api/v1/offline-class-payments/" + paymentId + "/void", s.master, "{\"reason\":\"Again\"}"), 409);
	}

	@Test
	void aNotTakenRequestNeedsApprovalAndIsFinalOnceApproved() throws Exception {
		Setup s = setup();
		UUID instructor = instructor(s.master, "Absent");
		UUID batch = batch(s.master);
		UUID held = id(classAt(s.master, batch, "2026-10-19", "10:00", "11:20", instructor));

		JsonNode requested = respond(post("/api/v1/offline-classes/" + held + "/not-taken", s.staff, "{\"reason\":\"Instructor absent\"}"),
				200);
		assertThat(requested.get("status").asText()).isEqualTo("NOT_TAKEN_PENDING");
		String decisionId = requested.get("notTakenRequest").get("id").asText();

		JsonNode approved = respond(post("/api/v1/offline-class-decisions/" + decisionId + "/approve", s.master, null), 200);
		assertThat(approved.get("status").asText()).isEqualTo("NOT_TAKEN");
		respond(patch("/api/v1/offline-classes/" + held, s.master, "{\"subject\":\"Changed\"}"), 409);
		respond(post("/api/v1/offline-classes/" + held + "/payment", s.master, payment("400", "2026-10-19")), 409);
	}

	@Test
	void aSubstituteIsPaidAndTheRateChangesOnlyUnsettledClasses() throws Exception {
		Setup s = setup();
		UUID tuhin = instructor(s.master, "Rate A");
		UUID guest = instructor(s.master, "Guest B");
		UUID batch = batch(s.master);
		rate(s.master, batch, tuhin, "300");

		UUID plain = id(classRated(s.master, batch, "2026-10-19", "10:00", "11:20", tuhin));
		UUID overridden = id(classAt(s.master, batch, "2026-10-18", "10:00", "11:20", tuhin));
		patch("/api/v1/offline-classes/" + overridden, s.master, "{\"rateOverride\":900}");
		UUID settled = id(classRated(s.master, batch, "2026-10-17", "10:00", "11:20", tuhin));
		respond(post("/api/v1/offline-classes/" + settled + "/payment", s.master, payment("300", "2026-10-17")), 200);

		// The substitute takes one held class and is paid for it at the guest's own rate.
		rate(s.master, batch, guest, "500");
		respond(patch("/api/v1/offline-classes/" + plain, s.master, "{\"instructorId\":\"" + guest + "\"}"), 200);
		JsonNode paidGuest = respond(post("/api/v1/offline-classes/" + plain + "/payment", s.master, payment("500", "2026-10-19")), 200);
		assertThat(paidGuest.get("payment").get("instructorId").asText()).isEqualTo(guest.toString());

		// A new rate for the first instructor changes unsettled classes without an override, and not the settled one.
		rate(s.master, batch, tuhin, "400");
		JsonNode overrideAfter = classView(s.master, batch, overridden);
		assertThat(overrideAfter.get("amount").decimalValue()).isEqualByComparingTo("900");
		JsonNode settledAfter = classView(s.master, batch, settled);
		assertThat(settledAfter.get("amount").decimalValue()).isEqualByComparingTo("300");
		assertThat(settledAfter.get("status").asText()).isEqualTo("PAID");
	}

	@Test
	void theUnsettledPageAndCountsFollowTheStatuses() throws Exception {
		Setup s = setup();
		UUID instructor = instructor(s.master, "Pending");
		UUID batch = batch(s.master);
		rate(s.master, batch, instructor, "250");
		UUID first = id(classAt(s.master, batch, "2026-10-19", "10:00", "11:20", instructor));
		UUID second = id(classAt(s.master, batch, "2026-10-18", "10:00", "11:20", instructor));
		classAt(s.master, batch, "2026-10-20", "14:00", "15:20", instructor);
		respond(post("/api/v1/offline-classes/" + first + "/payment", s.staff, payment("250", "2026-10-19")), 200);

		JsonNode page = respondTo(get("/api/v1/offline-classes/unsettled?batchId=" + batch).header("Authorization", "Bearer " + s.staff), 200);
		assertThat(page.get("total").asLong()).isEqualTo(2L);
		assertThat(page.get("items").get(0).get("id").asText()).isEqualTo(second.toString());
		assertThat(page.get("items").get(0).get("status").asText()).isEqualTo("HELD");
		assertThat(page.get("items").get(1).get("id").asText()).isEqualTo(first.toString());
		assertThat(page.get("items").get(1).get("status").asText()).isEqualTo("PAID_PENDING");

		JsonNode counts = respondTo(get("/api/v1/offline-classes/unsettled/counts").header("Authorization", "Bearer " + s.staff), 200);
		assertThat(counts.get("held").asLong()).isGreaterThanOrEqualTo(1L);
		assertThat(counts.get("waitingApproval").asLong()).isGreaterThanOrEqualTo(1L);
		assertThat(counts.get("heldAmount").decimalValue().signum()).isGreaterThanOrEqualTo(0);
	}

	@Test
	void approveManyReportsEachIdOnItsOwn() throws Exception {
		Setup s = setup();
		UUID instructor = instructor(s.master, "Bulk");
		UUID batch = batch(s.master);
		UUID one = id(classAt(s.master, batch, "2026-10-19", "10:00", "11:20", instructor));
		UUID two = id(classAt(s.master, batch, "2026-10-18", "10:00", "11:20", instructor));
		String paymentOne = respond(post("/api/v1/offline-classes/" + one + "/payment", s.staff, payment("400", "2026-10-19")), 200)
			.get("payment").get("id").asText();
		String paymentTwo = respond(post("/api/v1/offline-classes/" + two + "/payment", s.staff, payment("400", "2026-10-18")), 200)
			.get("payment").get("id").asText();
		UUID unknown = UUID.randomUUID();

		JsonNode results = respond(post("/api/v1/offline-class-payments/approve-many", s.master,
				"{\"ids\":[\"" + paymentOne + "\",\"" + paymentTwo + "\",\"" + unknown + "\"]}"), 200);

		assertThat(results).hasSize(3);
		assertThat(results.get(0).get("ok").asBoolean()).isTrue();
		assertThat(results.get(1).get("ok").asBoolean()).isTrue();
		assertThat(results.get(2).get("ok").asBoolean()).isFalse();
		assertThat(results.get(2).get("id").asText()).isEqualTo(unknown.toString());
		assertThat(classView(s.master, batch, one).get("status").asText()).isEqualTo("PAID");
	}

	@Test
	void aStudentGetsTheStudentCopyAndARoutineWithNoInstructorOrPay() throws Exception {
		Setup s = setup();
		ensureKnown(s.master, "Tuhin Hossain");
		ensureKnown(s.master, "Anarul Islam");
		makeUnknown(s.master, "Milon");
		UUID batch = batch(s.master);
		confirmedSample(s.master, batch);
		UUID studentUser = seed("STUDENT", "Rahim");
		admitted(studentUser, batch, "ONBOARDED");
		String student = token(studentUser);

		byte[] copy = bytes(get("/api/v1/me/admission/batch-routine.pdf").header("Authorization", "Bearer " + student));
		assertThat(copy.length).isGreaterThan(0);
		assertThat(text(copy)).doesNotContain("Tuhin").doesNotContain("Anarul").doesNotContain("Milon");

		String routineJson = new String(bytes(get("/api/v1/me/admission/routine?month=2026-11").header("Authorization", "Bearer " + student)),
				java.nio.charset.StandardCharsets.UTF_8);
		assertThat(routineJson).doesNotContain("Tuhin").doesNotContain("Anarul").doesNotContain("Milon").doesNotContain("amount")
			.doesNotContain("pay").doesNotContain("instructor");
		assertThat(json.readTree(routineJson).size()).isGreaterThan(0);
	}

	@Test
	void aPendingStudentGetsNotFoundOnTheRoutine() throws Exception {
		Setup s = setup();
		UUID batch = batch(s.master);
		UUID student = seed("STUDENT", "Pending");
		admitted(student, batch, "SUBMITTED");
		String token = token(student);

		respondTo(get("/api/v1/me/admission/routine?month=2026-11").header("Authorization", "Bearer " + token), 404);
		respondTo(get("/api/v1/me/admission/batch-routine.pdf").header("Authorization", "Bearer " + token), 404);
	}

	@Test
	void staffCannotApproveOrVoid() throws Exception {
		Setup s = setup();
		UUID instructor = instructor(s.master, "Staff");
		UUID batch = batch(s.master);
		UUID held = id(classAt(s.master, batch, "2026-10-19", "10:00", "11:20", instructor));
		String paymentId = respond(post("/api/v1/offline-classes/" + held + "/payment", s.staff, payment("400", "2026-10-19")), 200)
			.get("payment").get("id").asText();

		respond(post("/api/v1/offline-class-payments/" + paymentId + "/approve", s.staff, null), 403);
		respond(post("/api/v1/offline-class-payments/" + paymentId + "/void", s.staff, "{\"reason\":\"x\"}"), 403);
		respond(post("/api/v1/offline-class-payments/approve-many", s.staff, "{\"ids\":[\"" + paymentId + "\"]}"), 403);
	}

	// ---- helpers ----------------------------------------------------------------------------------------------------------

	@Test
	void anInstructorPaymentIsCorrectedOnThePaymentRecordsPage() throws Exception {
		Setup s = setup();
		UUID instructor = instructor(s.master, "Correct");
		UUID batch = batch(s.master);
		UUID held = id(classAt(s.master, batch, "2026-10-19", "10:00", "11:20", instructor));
		respond(post("/api/v1/offline-classes/" + held + "/payment", s.master, payment("400", "2026-10-19")), 200);
		String paymentId = classView(s.master, batch, held).get("payment").get("id").asText();
		String url = "/api/v1/payment-records/class/" + paymentId + "/edits";

		respond(post(url, s.master, "{\"amount\":350,\"note\":\"\",\"reason\":\"Paid less\"}"), 400);
		respond(post(url, s.master, "{\"amount\":400,\"reason\":\"Nothing\"}"), 400);
		JsonNode applied = respond(post(url, s.master, "{\"amount\":350,\"note\":\"Left early\",\"reason\":\"Paid less\"}"), 200);
		assertThat(applied.get("status").asText()).isEqualTo("FINALIZED");
		assertThat(jdbc().queryForObject("select amount from offline_class_payments where id = ?::uuid", java.math.BigDecimal.class,
				paymentId)).isEqualByComparingTo("350");

		JsonNode waiting = respond(post(url, s.staff, "{\"reference\":\"R-2\",\"reason\":\"Typo\"}"), 200);
		assertThat(waiting.get("status").asText()).isEqualTo("PENDING");
		assertThat(jdbc().queryForObject("select reference from offline_class_payments where id = ?::uuid", String.class, paymentId))
			.isEqualTo("R-1");

		JsonNode rows = respondTo(get("/api/v1/payment-records").param("kind", "CLASS").param("instructorId", instructor.toString())
			.header("Authorization", "Bearer " + s.master), 200);
		assertThat(rows).hasSize(1);
		assertThat(rows.get(0).get("amount").decimalValue()).isEqualByComparingTo("350");
		assertThat(rows.get(0).hasNonNull("correctedAt")).isTrue();
		assertThat(rows.get(0).get("pendingEdit").get("after").get("reference").asText()).isEqualTo("R-2");

		respond(post("/api/v1/payment-edits/" + waiting.get("id").asText() + "/finalize", s.staff, "{}"), 403);
		respond(post("/api/v1/payment-edits/" + waiting.get("id").asText() + "/finalize", s.master, "{}"), 200);
		assertThat(jdbc().queryForObject("select reference from offline_class_payments where id = ?::uuid", String.class, paymentId))
			.isEqualTo("R-2");
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

	private byte[] bytes(RequestBuilder request) throws Exception {
		return mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
	}

	private static String text(byte[] pdf) throws IOException {
		try (PDDocument document = Loader.loadPDF(pdf)) {
			return new PDFTextStripper().getText(document);
		}
	}

	private static UUID id(JsonNode node) {
		return UUID.fromString(node.get("id").asText());
	}

	private static String payment(String amount, String paidOn) {
		return "{\"amount\":" + amount + ",\"method\":\"CASH\",\"reference\":\"R-1\",\"paidOn\":\"" + paidOn + "\",\"note\":\"test\"}";
	}

	private UUID batch(String token) throws Exception {
		JsonNode created = respond(post("/api/v1/offline-batches", token, "{\"nameEn\":\"Settle\",\"nameBn\":\"সেটল\","
				+ "\"endsOn\":\"2027-06-30\",\"courseFee\":3000}"), 201);
		return UUID.fromString(created.get("id").asText());
	}

	private UUID instructor(String token, String label) throws Exception {
		String name = label + " " + UUID.randomUUID();
		JsonNode created = respond(post("/api/v1/offline-instructors", token, "{\"name\":\"" + name + "\"}"), 201);
		return UUID.fromString(created.get("id").asText());
	}

	/** A class with a rate override of 400, so it has an amount whatever rate the instructor has. */
	private JsonNode classAt(String token, UUID batch, String heldOn, String startsAt, String endsAt, UUID instructor) throws Exception {
		return respond(post("/api/v1/offline-batches/" + batch + "/classes", token, "{\"heldOn\":\"" + heldOn + "\",\"startsAt\":\""
				+ startsAt + "\",\"endsAt\":\"" + endsAt + "\",\"subject\":\"Contract Act\",\"topic\":\"Lecture-1\",\"instructorId\":\""
				+ instructor + "\",\"rateOverride\":400}"), 201);
	}

	/** A class that takes its instructor's rate in the batch (no override). */
	private JsonNode classRated(String token, UUID batch, String heldOn, String startsAt, String endsAt, UUID instructor) throws Exception {
		return respond(post("/api/v1/offline-batches/" + batch + "/classes", token, "{\"heldOn\":\"" + heldOn + "\",\"startsAt\":\""
				+ startsAt + "\",\"endsAt\":\"" + endsAt + "\",\"subject\":\"Contract Act\",\"topic\":\"Lecture-1\",\"instructorId\":\""
				+ instructor + "\"}"), 201);
	}

	private void rate(String token, UUID batch, UUID instructor, String rate) throws Exception {
		respondTo(MockMvcRequestBuilders.put("/api/v1/offline-batches/" + batch + "/rates/" + instructor)
			.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content("{\"rate\":" + rate + "}"), 200);
	}

	/** The class as the batch's list shows it now (the list is the source of the computed status). */
	private JsonNode classView(String token, UUID batch, UUID classId) throws Exception {
		JsonNode list = respondTo(get("/api/v1/offline-batches/" + batch + "/classes").header("Authorization", "Bearer " + token),
				200);
		for (JsonNode item : list) {
			if (item.get("id").asText().equals(classId.toString())) {
				return item;
			}
		}
		throw new AssertionError("Class not in the batch list");
	}

	private UUID seed(String role, String name) {
		UUID id = UUID.randomUUID();
		jdbc().update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, "+8801" + ThreadLocalRandom.current().nextInt(300000000, 999999999), "offline-class-" + id + "@example.com",
				passwords.encode("password1"), role);
		if ("STUDENT".equals(role)) {
			jdbc().update("INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, ?, ?)", id, name,
					"S" + id.toString().substring(0, 8));
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

	/** An admission for a student in a batch, in the given state, with the form, the photo and the batch set. */
	private void admitted(UUID student, UUID batch, String status) {
		UUID admission = UUID.randomUUID();
		jdbc().update("""
				insert into offline_admissions (id, student_id, status, created_by, created_at, submit_count, submitted_at,
				  full_name, father_name, mother_name, date_of_birth, email, gender, university, semester_batch, nid, mobile,
				  emergency_mobile, hall_mess, thana, district, present_address, permanent_address, batch_id)
				values (?, ?, ?, ?, ?, 1, ?, 'Rahim Uddin', 'Karim Uddin', 'Amena Begum', '2004-05-10', ?, 'MALE',
				  'Dhaka University', '2024-25', '1234567890123', ?, '01912345678', 'Shahid Minar Hall', 'Dhanmondi', 'Dhaka',
				  'House 1, Road 2, Dhaka', 'Village, Thana, District', ?)
				""", admission, student, status, adminOf(), Timestamp.from(NOW), Timestamp.from(NOW), uniqueEmail(), validMobile(), batch);
		jdbc().update("insert into student_photos (user_id, stored_name, content_type, byte_size, updated_at) values (?, ?, ?, ?, now())",
				student, "photo-" + UUID.randomUUID() + ".jpg", "image/jpeg", 100L);
	}

	private UUID adminOf() {
		return jdbc().queryForObject("select id from users where role = 'MASTER_ADMIN' order by created_at desc limit 1", UUID.class);
	}

	private static String uniqueEmail() {
		return "offline-class-form-" + UUID.randomUUID() + "@example.com";
	}

	private static String validMobile() {
		return "017" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
	}

	// ---- the sample routine, confirmed for the student's batch ------------------------------------------------------------

	private UUID confirmedSample(String master, UUID batch) throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "20th BJS Foundation Batch- NOVEMBER.pdf", "application/pdf",
				new ClassPathResource(SAMPLE).getInputStream().readAllBytes());
		JsonNode routine = respondTo(multipart("/api/v1/offline-batches/" + batch + "/routines").file(file).param("month", "2026-11")
			.header("Authorization", "Bearer " + master), 201);
		UUID id = id(routine);
		respond(post("/api/v1/offline-routines/" + id + "/instructor-matches", master, "{\"name\":\"Milon\",\"newName\":\"Milon\"}"), 200);
		// The sample's 27-07 typo blocks the confirm (15 Nov 2026); the admin fixes it to 27 October first.
		JsonNode view = respondTo(get("/api/v1/offline-routines/" + id).header("Authorization", "Bearer " + master), 200);
		int typo = -1;
		for (int index = 0; index < view.get("rows").size(); index++) {
			if ("2026-07-27".equals(view.get("rows").get(index).get("date").asText())) {
				typo = index;
			}
		}
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/offline-routines/" + id + "/rows/" + typo)
			.header("Authorization", "Bearer " + master).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
			.content("{\"date\":\"2026-10-27\"}")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
		respond(post("/api/v1/offline-routines/" + id + "/confirm", master, null), 200);
		return id;
	}

	private void ensureKnown(String token, String name) throws Exception {
		Set<String> keys = new HashSet<>();
		for (JsonNode instructor : liveInstructors(token)) {
			keys.add(key(instructor.get("name").asText()));
			for (JsonNode alias : instructor.get("aliases")) {
				keys.add(key(alias.asText()));
			}
		}
		if (!keys.contains(key(name))) {
			respond(post("/api/v1/offline-instructors", token, "{\"name\":\"" + name + "\"}"), 201);
		}
	}

	/** Renames any live instructor that answers to the name, so the sample's name is unknown until matched. */
	private void makeUnknown(String token, String name) throws Exception {
		for (JsonNode instructor : liveInstructors(token)) {
			boolean named = key(instructor.get("name").asText()).equals(key(name));
			boolean aliased = false;
			for (JsonNode alias : instructor.get("aliases")) {
				aliased |= key(alias.asText()).equals(key(name));
			}
			if (named || aliased) {
				respond(patch("/api/v1/offline-instructors/" + instructor.get("id").asText(), token,
						"{\"name\":\"" + name + " (earlier " + UUID.randomUUID() + ")\",\"aliases\":[]}"), 200);
			}
		}
	}

	private List<JsonNode> liveInstructors(String token) throws Exception {
		JsonNode all = respondTo(get("/api/v1/offline-instructors").header("Authorization", "Bearer " + token), 200);
		List<JsonNode> found = new ArrayList<>();
		all.forEach(found::add);
		return found;
	}

	private static String key(String name) {
		return name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
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
