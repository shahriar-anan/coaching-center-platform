package com.coachingcenter.api.offline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.context.TestPropertySource;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The staff lists of offline admissions (plan sections 5, 6.1 and 6.3): the queue's money flags, owed total and next due, the flag filter,
 * the counts, the overdue list, the admission history, and the routine fields of a batch after an upload. Every test makes its own students,
 * admissions, exams, packages and batches with random ids, and tags its names, so the class can run again on the same database and the
 * lists are read only for the rows the test made. The clock is fixed at 10:00 in Dhaka on 20 October 2026 and each test moves it forward
 * only for the money it judges.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, OfflineAdminListsIT.ClockConfig.class })
@TestPropertySource(properties = "app.offline-admission.enabled=true")
class OfflineAdminListsIT extends PostgresIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-10-20T04:00:00Z");

	/** 10 February 2027 in Dhaka: the 31 January due is past and unpaid, the 28 February one is not yet due. */
	private static final Instant FEBRUARY = Instant.parse("2027-02-10T04:00:00Z");

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

	// ---- queue: flags, owed total and next due -----------------------------------------------------------------------------

	@Test
	void aQueueRowCarriesItsFlagsOwedTotalAndNextDueForAnOnboardedAdmissionWithAnOverdueDueAndAPendingPayment() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Master");
		String staffToken = token(seed("SYSTEM_ADMIN", "Staff"));
		String masterToken = token(master);
		String tag = tag();
		UUID admission = onboarded(seed("STUDENT", "Money " + tag), master, "2026-12-31", "Money " + tag);

		// The desk takes 3,000 in February; a staff payment waits for the Master Admin.
		clock.set(FEBRUARY);
		respond(post("/api/v1/offline-admissions/" + admission + "/payments", staffToken,
				"{\"amount\":3000,\"method\":\"CASH\",\"reference\":\"DESK-1\",\"paidOn\":\"2027-02-10\"}"), 201);

		JsonNode page = read("/api/v1/offline-admissions?q=" + tag, masterToken);
		assertThat(page.get("totalElements").asLong()).isEqualTo(1L);
		JsonNode row = page.get("content").get(0);
		assertThat(row.get("id").asText()).isEqualTo(admission.toString());
		assertThat(strings(row.get("flags"))).containsExactly("NO_PAYMENT", "OVERDUE", "PENDING_APPROVAL");
		assertThat(row.get("owedTotal").decimalValue()).isEqualByComparingTo("12000");
		assertThat(row.get("nextDueOn").asText()).isEqualTo("2027-01-31");
	}

	@Test
	void theFlagFilterReturnsOnlyTheRowsThatMatchIt() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Master");
		String masterToken = token(master);
		String staffToken = token(seed("SYSTEM_ADMIN", "Staff"));
		String tag = tag();
		// A: overdue, no approved payment, and a staff payment waiting.
		UUID overdueWaiting = onboarded(seed("STUDENT", "A " + tag), master, "2026-12-31", "A " + tag);
		// B: overdue, no payment at all.
		UUID overdueUnpaid = onboarded(seed("STUDENT", "B " + tag), master, "2026-12-31", "B " + tag);
		// E: unpaid but not yet due (the first due is 5 March 2027).
		UUID notYetDue = onboarded(seed("STUDENT", "E " + tag), master, "2027-02-05", "E " + tag);
		// D: paid in full by the Master Admin, so no flag applies.
		UUID paid = onboarded(seed("STUDENT", "D " + tag), master, "2026-12-31", "D " + tag);
		// C: a form still under review, which has no dues and no flags.
		UUID submitted = admission(seed("STUDENT", "C " + tag), master, "SUBMITTED");
		jdbc().update("update offline_admissions set full_name = ? where id = ?", "C " + tag, submitted);

		clock.set(FEBRUARY);
		respond(post("/api/v1/offline-admissions/" + overdueWaiting + "/payments", staffToken,
				"{\"amount\":3000,\"method\":\"CASH\",\"reference\":\"DESK-2\",\"paidOn\":\"2027-02-10\"}"), 201);
		respond(post("/api/v1/offline-admissions/" + paid + "/payments", masterToken,
				"{\"amount\":12000,\"method\":\"BKASH\",\"reference\":\"TRX-PAID\",\"paidOn\":\"2027-02-10\"}"), 201);

		assertThat(queueIds(masterToken, tag, null)).containsExactlyInAnyOrder(str(overdueWaiting), str(overdueUnpaid),
				str(notYetDue), str(paid), str(submitted));
		assertThat(queueIds(masterToken, tag, "overdue")).containsExactlyInAnyOrder(str(overdueWaiting), str(overdueUnpaid));
		assertThat(queueIds(masterToken, tag, "pending-approval")).containsExactly(str(overdueWaiting));
		assertThat(queueIds(masterToken, tag, "unpaid")).containsExactlyInAnyOrder(str(overdueWaiting), str(overdueUnpaid),
				str(notYetDue));

		JsonNode refusal = respond(mockMvc.perform(get("/api/v1/offline-admissions?flag=sometimes")
			.header("Authorization", "Bearer " + masterToken)), 400);
		assertThat(fieldNames(refusal)).contains("flag");
	}

	// ---- counts ------------------------------------------------------------------------------------------------------------------

	@Test
	void countsMatchTheStatesSeeded() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Master");
		String staffToken = token(seed("SYSTEM_ADMIN", "Staff"));
		clock.set(FEBRUARY);
		// The database is shared between runs, so each count is read before and after the seeding and only the change is checked.
		JsonNode before = read("/api/v1/offline-admissions/counts", staffToken);

		admission(seed("STUDENT", "Count submitted"), master, "SUBMITTED");
		admission(seed("STUDENT", "Count changes"), master, "CHANGES_REQUESTED");
		admission(seed("STUDENT", "Count ready"), master, "READY");
		admission(seed("STUDENT", "Count closed"), master, "CLOSED");
		UUID onboarded = onboarded(seed("STUDENT", "Count onboarded"), master, "2026-12-31", "Count onboarded");
		respond(post("/api/v1/offline-admissions/" + onboarded + "/payments", staffToken,
				"{\"amount\":3000,\"method\":\"CASH\",\"reference\":\"DESK-3\",\"paidOn\":\"2027-02-10\"}"), 201);

		JsonNode after = read("/api/v1/offline-admissions/counts", staffToken);
		assertThat(after.get("submitted").asLong() - before.get("submitted").asLong()).isEqualTo(1L);
		assertThat(after.get("changesRequested").asLong() - before.get("changesRequested").asLong()).isEqualTo(1L);
		assertThat(after.get("ready").asLong() - before.get("ready").asLong()).isEqualTo(1L);
		assertThat(after.get("onboarded").asLong() - before.get("onboarded").asLong()).isEqualTo(1L);
		assertThat(after.get("closed").asLong() - before.get("closed").asLong()).isEqualTo(1L);
		assertThat(after.get("paymentsPending").asLong() - before.get("paymentsPending").asLong()).isEqualTo(1L);
		assertThat(after.get("overdue").asLong() - before.get("overdue").asLong()).isEqualTo(1L);

		// The Master Admin reads the same counts.
		JsonNode master2 = read("/api/v1/offline-admissions/counts", token(master));
		assertThat(master2.get("overdue").asLong()).isEqualTo(after.get("overdue").asLong());
	}

	// ---- overdue list ------------------------------------------------------------------------------------------------------------

	@Test
	void theOverdueListIsSortedByDaysLateWithTheOldestOverdueDuePerAdmission() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Master");
		String staffToken = token(seed("SYSTEM_ADMIN", "Staff"));
		String tag = tag();
		// A's first due is 31 January 2027; B's is 15 February 2027.
		UUID older = onboarded(seed("STUDENT", "Older " + tag), master, "2026-12-31", "Older " + tag);
		UUID newer = onboarded(seed("STUDENT", "Newer " + tag), master, "2027-01-15", "Newer " + tag);

		clock.set(Instant.parse("2027-03-10T04:00:00Z"));
		List<JsonNode> rows = overdueRows(staffToken);

		// Days late never rise down the list, and each admission appears once.
		Set<String> ids = new HashSet<>();
		for (int i = 0; i < rows.size(); i++) {
			assertThat(ids.add(rows.get(i).get("admissionId").asText())).isTrue();
			if (i > 0) {
				assertThat(rows.get(i - 1).get("daysLate").asLong()).isGreaterThanOrEqualTo(rows.get(i).get("daysLate").asLong());
			}
		}

		JsonNode olderRow = find(rows, older);
		JsonNode newerRow = find(rows, newer);
		assertThat(olderRow.get("dueOn").asText()).isEqualTo("2027-01-31");
		assertThat(olderRow.get("seq").asInt()).isEqualTo(1);
		assertThat(olderRow.get("amount").decimalValue()).isEqualByComparingTo("3000");
		assertThat(olderRow.get("daysLate").asLong()).isEqualTo(38L);
		assertThat(olderRow.get("owedTotal").decimalValue()).isEqualByComparingTo("12000");
		assertThat(olderRow.get("name").asText()).isEqualTo("Older " + tag);
		assertThat(olderRow.get("batchNameEn").asText()).isEqualTo("BJS Evening");
		assertThat(newerRow.get("dueOn").asText()).isEqualTo("2027-02-15");
		assertThat(newerRow.get("daysLate").asLong()).isEqualTo(23L);
		assertThat(newerRow.get("owedTotal").decimalValue()).isEqualByComparingTo("9000");
		assertThat(rows.indexOf(olderRow)).isLessThan(rows.indexOf(newerRow));
	}

	// ---- history ---------------------------------------------------------------------------------------------------------------------

	@Test
	void theHistoryListsTheSubmissionsAndThePaymentEventsNewestFirst() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Master");
		String masterToken = token(master);
		String staffToken = token(seed("SYSTEM_ADMIN", "Staff"));
		String tag = tag();
		UUID student = seed("STUDENT", "History " + tag);
		UUID admission = admission(student, master, "SUBMITTED");

		// The audit log stamps its rows with the server's own clock, not the test clock. This test therefore moves the test clock along
		// the real one, so its audit rows and its payment and submission rows sort in the order the steps ran.
		// Staff send the form back, and the student submits it again (the second submission row, as the student's submit writes it).
		clock.set(Instant.now());
		respond(post("/api/v1/offline-admissions/" + admission + "/request-changes", staffToken, "{\"reason\":\"Photo unclear\"}"), 200);
		clock.set(Instant.now());
		jdbc().update("insert into offline_admission_submissions (id, admission_id, submitted_at, submitted_by, form_json)"
				+ " values (?, ?, ?, ?, '{}')", UUID.randomUUID(), admission, Timestamp.from(clock.instant()), student);
		jdbc().update("update offline_admissions set status = 'SUBMITTED' where id = ?", admission);

		// Onboarding, then a desk payment and its approval.
		clock.set(Instant.now());
		UUID batch = batch(masterToken, "2027-04-30", "3000");
		onboardExisting(admission, batch, master, "2026-12-31");
		clock.set(Instant.now());
		JsonNode payment = respond(post("/api/v1/offline-admissions/" + admission + "/payments", staffToken,
				"{\"amount\":3000,\"method\":\"CASH\",\"reference\":\"DESK-9\",\"paidOn\":\"2026-10-20\"}"), 201);
		clock.set(Instant.now());
		respond(post("/api/v1/offline-fee-payments/" + payment.get("id").asText() + "/approve", masterToken, null), 200);

		JsonNode history = read("/api/v1/offline-admissions/" + admission + "/history", masterToken);
		List<String> actions = new ArrayList<>();
		List<Instant> times = new ArrayList<>();
		for (JsonNode event : history) {
			actions.add(event.get("action").asText());
			times.add(Instant.parse(event.get("at").asText()));
		}
		for (int i = 1; i < times.size(); i++) {
			assertThat(times.get(i - 1)).isAfterOrEqualTo(times.get(i));
		}

		// The newest event is the approval, by the Master Admin; the oldest is the first submission, by the student.
		assertThat(actions.get(0)).isEqualTo("OFFLINE_FEE_PAYMENT_APPROVED");
		assertThat(history.get(0).get("actorName").asText()).isEqualTo("Master");
		assertThat(actions.get(actions.size() - 1)).isEqualTo("OFFLINE_ADMISSION_SUBMITTED");
		// Onboarding copies the form's name into the student's profile, so the name to expect is the profile's current one.
		String studentName = jdbc().queryForObject("select full_name from student_profiles where user_id = ?", String.class, student);
		assertThat(history.get(actions.size() - 1).get("actorName").asText()).isEqualTo(studentName);
		assertThat(actions).contains("OFFLINE_ADMISSION_RESUBMITTED", "OFFLINE_ADMISSION_CHANGES_REQUESTED",
				"OFFLINE_FEE_PAYMENT_RECORDED", "OFFLINE_ADMISSION_ONBOARDED");

		// The payment's recording is older than its approval, and was made by staff.
		int recorded = actions.indexOf("OFFLINE_FEE_PAYMENT_RECORDED");
		assertThat(recorded).isGreaterThan(0);
		assertThat(history.get(recorded).get("actorName").asText()).isEqualTo("Staff");
		assertThat(history.get(recorded).get("detail").asText()).contains("DESK-9");

		// The return carries its reason, and an audit row for the submission is not listed twice.
		int returned = actions.indexOf("OFFLINE_ADMISSION_CHANGES_REQUESTED");
		assertThat(history.get(returned).get("detail").asText()).isEqualTo("Photo unclear");
		assertThat(history.get(returned).get("actorName").asText()).isEqualTo("Staff");
		assertThat(actions.stream().filter("OFFLINE_ADMISSION_CHANGES_REQUESTED"::equals)).hasSize(1);
	}

	// ---- routine fields ----------------------------------------------------------------------------------------------------------

	@Test
	void theRoutineFieldsAppearAfterAnUploadAndCountTheStudentsNotified() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Master");
		String masterToken = token(master);
		String staffToken = token(seed("SYSTEM_ADMIN", "Staff"));
		String tag = tag();
		UUID batch = batch(masterToken, "2027-04-30", "3000");
		onboardExisting(admission(seed("STUDENT", "Routine one " + tag), master, "SUBMITTED"), batch, master, "2026-12-31");

		JsonNode before = read("/api/v1/offline-batches/" + batch, staffToken);
		assertThat(before.get("routineUploaded").asBoolean()).isFalse();
		assertThat(before.get("routineUploadedBy").isNull()).isTrue();
		assertThat(before.get("studentsNotified").isNull()).isTrue();

		JsonNode first = upload(batch, staffToken);
		assertThat(first.get("routineUploadedBy").asText()).isEqualTo("Staff");
		assertThat(first.get("routineUploadedAt").isNull()).isFalse();
		assertThat(first.get("studentsNotified").asInt()).isEqualTo(1);

		// A second student is onboarded into the batch, and the Master Admin uploads the routine again.
		onboardExisting(admission(seed("STUDENT", "Routine two " + tag), master, "SUBMITTED"), batch, master, "2026-12-31");
		JsonNode second = upload(batch, masterToken);
		assertThat(second.get("routineUploadedBy").asText()).isEqualTo("Master");
		assertThat(second.get("studentsNotified").asInt()).isEqualTo(2);

		JsonNode fetched = read("/api/v1/offline-batches/" + batch, staffToken);
		assertThat(fetched.get("routineUploadedBy").asText()).isEqualTo("Master");
		assertThat(fetched.get("studentsNotified").asInt()).isEqualTo(2);

		JsonNode listed = null;
		for (JsonNode candidate : read("/api/v1/offline-batches?status=ACTIVE", staffToken)) {
			if (batch.toString().equals(candidate.get("id").asText())) {
				listed = candidate;
			}
		}
		assertThat(listed).isNotNull();
		assertThat(listed.get("routineUploadedBy").asText()).isEqualTo("Master");
		assertThat(listed.get("studentsNotified").asInt()).isEqualTo(2);
	}

	// ---- helpers ------------------------------------------------------------------------------------------------------------------

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

	/** A GET with a bearer token, answered 200; returns its JSON body. */
	private JsonNode read(String url, String token) throws Exception {
		return respond(mockMvc.perform(get(url).header("Authorization", "Bearer " + token)), 200);
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

	private static List<String> strings(JsonNode array) {
		List<String> values = new ArrayList<>();
		array.forEach(value -> values.add(value.asText()));
		return values;
	}

	private static String str(UUID id) {
		return id.toString();
	}

	/** The ids of the queue rows that match the tag and the flag (or every flag when none is given). */
	private Set<String> queueIds(String token, String tag, String flag) throws Exception {
		String url = "/api/v1/offline-admissions?size=100&q=" + tag + (flag == null ? "" : "&flag=" + flag);
		Set<String> ids = new HashSet<>();
		read(url, token).get("content").forEach(row -> ids.add(row.get("id").asText()));
		return ids;
	}

	/** Every row of the overdue list, read page by page. */
	private List<JsonNode> overdueRows(String token) throws Exception {
		List<JsonNode> rows = new ArrayList<>();
		JsonNode first = read("/api/v1/offline-admissions/overdue?size=100&page=0", token);
		first.get("content").forEach(rows::add);
		for (int page = 1; page < first.get("totalPages").asInt(); page++) {
			read("/api/v1/offline-admissions/overdue?size=100&page=" + page, token).get("content").forEach(rows::add);
		}
		return rows;
	}

	private static JsonNode find(List<JsonNode> rows, UUID admission) {
		for (JsonNode row : rows) {
			if (admission.toString().equals(row.get("admissionId").asText())) {
				return row;
			}
		}
		throw new AssertionError("The overdue list has no row for " + admission);
	}

	/** Uploads a routine PDF to the batch as the given user, and returns the batch as the upload answers it. */
	private JsonNode upload(UUID batch, String token) throws Exception {
		MvcResult result = mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/offline-batches/" + batch + "/routine-pdf")
			.file(new MockMultipartFile("file", "routine.pdf", "application/pdf", "%PDF-1.4\nroutine\n".getBytes()))
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn();
		return json.readTree(result.getResponse().getContentAsString());
	}

	private static String tag() {
		return "Tag" + UUID.randomUUID().toString().substring(0, 8);
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
				""", id, studentId, status, createdBy, Timestamp.from(clock.instant()), Timestamp.from(clock.instant()), uniqueEmail(),
				validMobile());
		jdbc().update("insert into offline_admission_submissions (id, admission_id, submitted_at, submitted_by, form_json)"
				+ " values (?, ?, ?, ?, '{}')", UUID.randomUUID(), id, Timestamp.from(clock.instant()), studentId);
		jdbc().update("insert into student_photos (user_id, stored_name, content_type, byte_size, updated_at) values (?, ?, ?, ?, now())",
				studentId, "photo-" + UUID.randomUUID() + ".jpg", "image/jpeg", 100L);
		return id;
	}

	/**
	 * An onboarded admission in a new batch that ends 30 April 2027 at 3,000 a month, with one exam and one package, onboarded by the
	 * Master Admin at the test clock's instant. The admitted-on date is given, so the dues follow from it. The name is set to the given one.
	 */
	private UUID onboarded(UUID student, UUID master, String admittedOn, String name) throws Exception {
		UUID admission = admission(student, master, "SUBMITTED");
		jdbc().update("update offline_admissions set full_name = ? where id = ?", name, admission);
		onboardExisting(admission, batch(token(master), "2027-04-30", "3000"), master, admittedOn);
		return admission;
	}

	/**
	 * Sets up, adds an exam and a package, marks ready and onboards an admission the Master Admin owns. The course fee is 3,000 for each
	 * instalment that fits to the course end, so every instalment is 3,000.
	 */
	private void onboardExisting(UUID admission, UUID batch, UUID master, String admittedOn) throws Exception {
		String token = token(master);
		JsonNode setup = respond(patch("/api/v1/offline-admissions/" + admission + "/setup", token,
				"{\"batchId\":\"" + batch + "\",\"admittedOn\":\"" + admittedOn + "\"}"), 200).get("setup");
		int count = OfflineFeeSchedule.fittingInstallments(java.time.LocalDate.parse(setup.get("firstDueOn").asText()),
				java.time.LocalDate.parse(setup.get("courseEndsOn").asText()));
		respond(patch("/api/v1/offline-admissions/" + admission + "/setup", token,
				"{\"installments\":" + count + ",\"courseFee\":" + 3000 * count + "}"), 200);
		respond(post("/api/v1/offline-admissions/" + admission + "/items", token,
				"{\"scope\":\"EXAM\",\"examId\":\"" + publishedExam() + "\"}"), 200);
		respond(post("/api/v1/offline-admissions/" + admission + "/items", token,
				"{\"scope\":\"MODULE\",\"moduleId\":\"" + publishedModule() + "\"}"), 200);
		respond(post("/api/v1/offline-admissions/" + admission + "/ready", token, null), 200);
		respond(post("/api/v1/offline-admissions/" + admission + "/onboard", token, rollBody()), 200);
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
		return "offline-admin-lists-" + UUID.randomUUID() + "@example.com";
	}

	/** An 11-digit Bangladesh mobile starting 017. */
	private static String validMobile() {
		return "017" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
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
