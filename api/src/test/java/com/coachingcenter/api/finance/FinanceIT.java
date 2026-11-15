package com.coachingcenter.api.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The finance reports (plan section 6, stage 5), seeded with known rows and read back: the exact totals per source and the net, a refunded
 * order subtracted, voided and pending rows excluded, the paid date in Dhaka with the boundary day counted once, the batch and instructor
 * views, the entries that add up to the summary, the CSV, the permission, and the range checks.
 *
 * <p>
 * Every run seeds its rows in a year of its own (a random year from 2100 on), so a second run on the same database adds nothing to the
 * figures it checks. The clock is fixed at 10:00 in Dhaka on 20 October 2026.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, FinanceIT.ClockConfig.class })
@TestPropertySource(properties = "app.offline-admission.enabled=true")
class FinanceIT extends PostgresIntegrationTest {

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

	@Test
	void theSeededRowsGiveTheExactTotalsPerSourceAndTheNet() throws Exception {
		Seeded s = seed();

		JsonNode summary = api(summaryUrl(s.jan(), s.jan31()), s.master);

		assertThat(summary.get("income").decimalValue()).isEqualByComparingTo("2100");
		assertThat(summary.get("expense").decimalValue()).isEqualByComparingTo("650");
		assertThat(summary.get("net").decimalValue()).isEqualByComparingTo("1450");
		assertThat(sourceTotal(summary, "ONLINE_ORDERS")).isEqualByComparingTo("800");
		assertThat(sourceTotal(summary, "DIRECT_ENROLMENT")).isEqualByComparingTo("200");
		assertThat(sourceTotal(summary, "OFFLINE_FEES")).isEqualByComparingTo("800");
		assertThat(sourceTotal(summary, "OTHER_ONLINE")).isEqualByComparingTo("300");
		assertThat(sourceTotal(summary, "STAFF_PAY")).isEqualByComparingTo("400");
		assertThat(sourceTotal(summary, "OFFLINE_INSTRUCTORS")).isEqualByComparingTo("250");
		assertThat(summary.get("previous").get("to").asText()).isEqualTo((s.year() - 1) + "-12-31");
	}

	@Test
	void aRefundIsSubtractedAndAPendingOrVoidedRowIsLeftOut() throws Exception {
		Seeded s = seed();

		JsonNode online = api(entriesUrl(s.jan(), s.jan31()) + "&sources=ONLINE_ORDERS", s.master);
		assertThat(sumOf(online.get("items"), "ONLINE_ORDERS")).isEqualByComparingTo("800");
		assertThat(hasAmount(online.get("items"), "700")).isTrue();
		assertThat(hasAmount(online.get("items"), "70")).isFalse();

		JsonNode direct = api(entriesUrl(s.jan(), s.jan31()) + "&sources=DIRECT_ENROLMENT", s.master);
		assertThat(sumOf(direct.get("items"), "DIRECT_ENROLMENT")).isEqualByComparingTo("200");
		assertThat(hasAmount(direct.get("items"), "500")).isFalse();

		JsonNode fees = api(entriesUrl(s.jan(), s.jan31()) + "&sources=OFFLINE_FEES", s.master);
		assertThat(sumOf(fees.get("items"), "OFFLINE_FEES")).isEqualByComparingTo("800");
		assertThat(hasAmount(fees.get("items"), "999")).isFalse();
		assertThat(hasAmount(fees.get("items"), "777")).isFalse();

		JsonNode staff = api(entriesUrl(s.jan(), s.jan31()) + "&sources=STAFF_PAY", s.master);
		assertThat(sumOf(staff.get("items"), "STAFF_PAY")).isEqualByComparingTo("400");
		assertThat(hasAmount(staff.get("items"), "100")).isFalse();
	}

	@Test
	void thePaidDateInDhakaDecidesTheDayAndTheBoundaryDayIsCountedOnce() throws Exception {
		Seeded s = seed();

		// 31 January 23:30 in Dhaka is in January (100); 1 February 00:30 in Dhaka is in February (50), though it is 31 January in UTC.
		JsonNode jan = api(entriesUrl(s.jan31(), s.jan31()) + "&sources=ONLINE_ORDERS", s.master);
		assertThat(jan.get("total").asLong()).isEqualTo(1L);
		assertThat(hasAmount(jan.get("items"), "100")).isTrue();
		JsonNode feb = api(entriesUrl(s.feb1(), s.feb1()) + "&sources=ONLINE_ORDERS", s.master);
		assertThat(feb.get("total").asLong()).isEqualTo(1L);
		assertThat(hasAmount(feb.get("items"), "50")).isTrue();
	}

	@Test
	void theBatchViewGivesFeesCostNetAndMargin() throws Exception {
		Seeded s = seed();

		JsonNode batches = respondArray(get("/api/v1/finance/batches?from=" + s.jan() + "&to=" + s.jan31() + "&batchId=" + s.batch())
			.header("Authorization", "Bearer " + s.master));

		assertThat(batches).hasSize(1);
		JsonNode row = batches.get(0);
		assertThat(row.get("feesCollected").decimalValue()).isEqualByComparingTo("800");
		assertThat(row.get("instructorCost").decimalValue()).isEqualByComparingTo("250");
		assertThat(row.get("net").decimalValue()).isEqualByComparingTo("550");
		assertThat(row.get("marginPercent").decimalValue()).isEqualByComparingTo("68.8");
		// With a batch chosen, only the offline sources are in the summary.
		JsonNode summary = api(summaryUrl(s.jan(), s.jan31()) + "&batchId=" + s.batch(), s.master);
		assertThat(summary.get("income").decimalValue()).isEqualByComparingTo("800");
		assertThat(summary.get("expense").decimalValue()).isEqualByComparingTo("250");
	}

	@Test
	void theInstructorViewGivesWhatIsOwedAndWhatWaits() throws Exception {
		Seeded s = seed();

		JsonNode rows = respondArray(get("/api/v1/finance/instructors?from=2026-10-01&to=2026-10-31").header("Authorization",
				"Bearer " + s.master));

		JsonNode mine = null;
		for (JsonNode row : rows) {
			if (row.get("instructorId").asText().equals(s.instructor().toString())) {
				mine = row;
			}
		}
		assertThat(mine).isNotNull();
		// Two classes are held or settled (one held and unpaid, one paid); the held one is owed at its due amount.
		assertThat(mine.get("classes").asLong()).isEqualTo(2L);
		assertThat(mine.get("owed").decimalValue()).isEqualByComparingTo("150");
		assertThat(mine.get("paid").decimalValue()).isEqualByComparingTo("200");
		assertThat(mine.get("pending").decimalValue()).isEqualByComparingTo("60");
	}

	@Test
	void theEntriesAddUpToTheSummaryAndTheCsvHasAHeaderAndAByteOrderMark() throws Exception {
		Seeded s = seed();

		JsonNode summary = api(summaryUrl(s.jan(), s.jan31()), s.master);
		JsonNode page = api(entriesUrl(s.jan(), s.jan31()) + "&size=200", s.master);
		assertThat(page.get("income").decimalValue()).isEqualByComparingTo(summary.get("income").decimalValue());
		assertThat(page.get("expense").decimalValue()).isEqualByComparingTo(summary.get("expense").decimalValue());
		assertThat(page.get("total").asLong()).isEqualTo(7L);

		byte[] csv = bytes(get("/api/v1/finance/entries.csv?from=" + s.jan() + "&to=" + s.jan31()).header("Authorization",
				"Bearer " + s.master));
		assertThat(csv[0] & 0xFF).isEqualTo(0xEF);
		assertThat(csv[1] & 0xFF).isEqualTo(0xBB);
		assertThat(csv[2] & 0xFF).isEqualTo(0xBF);
		String text = new String(csv, java.nio.charset.StandardCharsets.UTF_8).substring(1);
		assertThat(text).startsWith("date,source,direction,description,amount,reference,id,batch_id\r\n");
		assertThat(text.lines().count()).isEqualTo(8L);
	}

	@Test
	void theEntriesSearchSortAndTotalEveryStreamInReach() throws Exception {
		Seeded s = seed();
		JsonNode all = api(entriesUrl(s.jan(), s.jan31()) + "&size=200", s.master);

		// Every stream in reach is totalled, and the totals add up to the page's income and expense.
		java.math.BigDecimal streams = java.math.BigDecimal.ZERO;
		for (JsonNode stream : all.get("bySource")) {
			streams = streams.add(stream.get("total").decimalValue());
		}
		assertThat(streams).isEqualByComparingTo(all.get("income").decimalValue().add(all.get("expense").decimalValue()));

		// Largest first, then smallest first.
		JsonNode largest = api(entriesUrl(s.jan(), s.jan31()) + "&size=200&sort=amount", s.master).get("items");
		for (int i = 1; i < largest.size(); i++) {
			assertThat(largest.get(i - 1).get("amount").decimalValue()).isGreaterThanOrEqualTo(largest.get(i).get("amount").decimalValue());
		}
		JsonNode smallest = api(entriesUrl(s.jan(), s.jan31()) + "&size=200&sort=amount_asc", s.master).get("items");
		assertThat(smallest.get(0).get("amount").decimalValue()).isEqualByComparingTo(
				largest.get(largest.size() - 1).get("amount").decimalValue());

		// A search keeps only the rows whose description or reference holds it, and still totals every stream it reaches.
		String word = all.get("items").get(0).get("description").asText().split("\\s+")[0];
		JsonNode found = api(entriesUrl(s.jan(), s.jan31()) + "&size=200&q=" + java.net.URLEncoder.encode(word.toUpperCase(),
				java.nio.charset.StandardCharsets.UTF_8), s.master);
		assertThat(found.get("total").asLong()).isPositive().isLessThanOrEqualTo(all.get("total").asLong());
		for (JsonNode item : found.get("items")) {
			String text = (item.get("description").asText() + " " + item.path("reference").asText()).toLowerCase();
			assertThat(text).contains(word.toLowerCase());
		}

		// Choosing one stream leaves the others in the stream totals, so the page can still offer them.
		String first = all.get("bySource").get(0).get("source").asText();
		JsonNode one = api(entriesUrl(s.jan(), s.jan31()) + "&size=200&sources=" + first, s.master);
		assertThat(one.get("bySource").size()).isEqualTo(all.get("bySource").size());
	}

	@Test
	void staffGetForbiddenOnEveryFinanceRoute() throws Exception {
		Seeded s = seed();
		String staff = token(seedUser("SYSTEM_ADMIN", "Finance Staff"));

		for (String url : List.of(summaryUrl(s.jan(), s.jan31()), "/api/v1/finance/monthly?from=" + s.jan() + "&to=" + s.jan31(),
				"/api/v1/finance/batches?from=" + s.jan() + "&to=" + s.jan31(),
				"/api/v1/finance/instructors?from=2026-10-01&to=2026-10-31", entriesUrl(s.jan(), s.jan31()),
				"/api/v1/finance/entries.csv?from=" + s.jan() + "&to=" + s.jan31())) {
			mockMvc.perform(get(url).header("Authorization", "Bearer " + staff)).andExpect(status().isForbidden());
		}
	}

	@Test
	void aBadRangeOrAnUnknownSourceIsRefused() throws Exception {
		Seeded s = seed();
		mockMvc.perform(get(summaryUrl(s.feb1(), s.jan())).header("Authorization", "Bearer " + s.master)).andExpect(status().isBadRequest());
		// Longer than the ten years the reports answer.
		mockMvc.perform(get(summaryUrl("2100-01-01", "2111-01-05")).header("Authorization", "Bearer " + s.master))
			.andExpect(status().isBadRequest());
		mockMvc.perform(get(summaryUrl(s.jan(), s.jan31()) + "&sources=NOPE").header("Authorization", "Bearer " + s.master))
			.andExpect(status().isBadRequest());
	}

	@Test
	void theMonthlyViewHasOneRowPerMonthWithEntries() throws Exception {
		Seeded s = seed();

		JsonNode months = respondArray(get("/api/v1/finance/monthly?from=" + s.jan() + "&to=" + s.feb1()).header("Authorization",
				"Bearer " + s.master));

		List<String> names = new ArrayList<>();
		for (JsonNode month : months) {
			names.add(month.get("month").asText());
		}
		assertThat(names).contains(s.year() + "-01", s.year() + "-02");
	}

	// ---- seeding --------------------------------------------------------------------------------------------------------

	/** The rows of one run, in a year of its own. */
	private record Seeded(int year, String master, UUID batch, UUID instructor) {

		String jan() {
			return year + "-01-01";
		}

		String jan31() {
			return year + "-01-31";
		}

		String feb1() {
			return year + "-02-01";
		}

	}

	private Seeded seed() throws Exception {
		int year = 2100 + ThreadLocalRandom.current().nextInt(400);
		UUID masterId = seedUser("MASTER_ADMIN", "Finance Master");
		String master = token(masterId);
		UUID staffUser = seedUser("SYSTEM_ADMIN", "Ledger Staff");
		UUID student = seedUser("STUDENT", "Finance Student");
		UUID batch = batch(master);
		UUID instructor = UUID.randomUUID();
		jdbc().update("insert into offline_instructors (id, name, aliases, created_by, created_at, updated_at, version)"
				+ " values (?, ?, '{}', ?, now(), now(), 0)", instructor, "Finance Instructor " + instructor, masterId);

		// Online orders: 1000 paid 5 Jan (refund 300), 100 paid 31 Jan 23:30 Dhaka, 50 paid 1 Feb 00:30 Dhaka, 70 still INITIATED.
		UUID exam = exam();
		order(exam, student, 1000, year + "-01-05T04:00:00Z", 300, year, 1);
		order(exam, student, 100, year + "-01-31T17:30:00Z", null, year, 2);
		order(exam, student, 50, year + "-01-31T18:30:00Z", null, year, 3);
		jdbc().update("insert into live_exam_orders (id, student_id, scope, exam_id, amount, currency, method, tran_id, status,"
				+ " created_at, updated_at) values (?, ?, 'EXAM', ?, 70, 'BDT', 'SSLCOMMERZ', ?, 'INITIATED', now(), now())",
				UUID.randomUUID(), student, exam, "T" + UUID.randomUUID().toString().substring(0, 20));

		// Direct: 200 paid 12 Jan; 500 paid 13 Jan and voided.
		UUID grant = manualGrant(student, exam);
		UUID plan = UUID.randomUUID();
		jdbc().update("insert into live_exam_payment_plans (id, grant_id, student_id, agreed_total, created_by, created_at)"
				+ " values (?, ?, ?, 2000, ?, now())", plan, grant, student, masterId);
		direct(plan, 200, year + "-01-12", masterId, false);
		direct(plan, 500, year + "-01-13", masterId, true);

		// Offline fees: 800 approved on 15 Jan; 999 pending and 777 voided on the same days, both left out.
		UUID admission = admission(student, masterId, batch, year);
		fee(admission, 800, year + "-01-15", "APPROVED", masterId);
		fee(admission, 999, year + "-01-15", "PENDING", masterId);
		fee(admission, 777, year + "-01-16", "VOIDED", masterId);

		// Older online payments: 300 SUCCESS on 20 Jan (Dhaka 11:00); a failed one and a deleted one are left out.
		payment(student, 300, year + "-01-20T05:00:00Z", "SUCCESS", false);
		payment(student, 400, year + "-01-21T05:00:00Z", "FAILED", false);
		payment(student, 500, year + "-01-22T05:00:00Z", "SUCCESS", true);

		// Staff pay: 400 PAYMENT on 8 Jan; a voided one of 100 is left out.
		staffPayment(staffUser, 400, year + "-01-08", null);
		staffPayment(staffUser, 100, year + "-01-09", masterId);

		// Offline instructor pay: 250 approved on 18 Jan for the batch; 75 pending on the same day is left out.
		UUID classApproved = offlineClass(batch, year + "-01-18", "PAID");
		classPayment(classApproved, instructor, 250, year + "-01-18", "APPROVED", masterId);
		UUID classPending = offlineClass(batch, year + "-01-18", "PAID_PENDING");
		classPayment(classPending, instructor, 75, year + "-01-18", "PENDING", masterId);

		// The instructor's own October figures: one held and unpaid class at 150, one paid class (200 on 18 Oct), one waiting payment (60).
		UUID held = offlineClass(batch, "2026-10-19", "SCHEDULED", instructor, 150);
		UUID paidOct = offlineClass(batch, "2026-10-18", "PAID", instructor, 200);
		classPayment(paidOct, instructor, 200, "2026-10-18", "APPROVED", masterId);
		UUID waitingOct = offlineClass(batch, "2026-10-17", "PAID_PENDING", instructor, 60);
		classPayment(waitingOct, instructor, 60, "2026-10-17", "PENDING", masterId);
		assertThat(held).isNotNull();
		return new Seeded(year, master, batch, instructor);
	}

	private void order(UUID exam, UUID student, int amount, String paidAt, Integer refund, int year, int n) {
		UUID id = UUID.randomUUID();
		jdbc().update("""
				insert into live_exam_orders (id, student_id, scope, exam_id, amount, currency, method, tran_id, status, created_at,
				  updated_at, paid_at, refunded_at, refunded_by, refund_amount, refund_reason)
				values (?, ?, 'EXAM', ?, ?, 'BDT', 'SSLCOMMERZ', ?, 'SUCCEEDED', now(), now(), ?, ?, ?, ?, ?)
				""", id, student, exam, amount, "F" + year + "-" + n + "-" + UUID.randomUUID().toString().substring(0, 8),
				Timestamp.from(Instant.parse(paidAt)), refund == null ? null : Timestamp.from(Instant.parse(paidAt)),
				refund == null ? null : adminId(), refund, refund == null ? null : "Test refund");
	}

	private UUID adminId() {
		return jdbc().queryForObject("select id from users where role = 'MASTER_ADMIN' order by created_at desc limit 1", UUID.class);
	}

	private void direct(UUID plan, int amount, String paidOn, UUID by, boolean voided) {
		jdbc().update("""
				insert into live_exam_direct_payments (id, plan_id, amount, method, reference, paid_on, receipt_no, paid_to_date,
				  due_after, installment_no, installment_count, created_by, created_at, voided_at, voided_by, void_reason)
				values (?, ?, ?, 'CASH', 'R-DIRECT', ?, ?, ?, 0, 1, 1, ?, now(), ?, ?, ?)
				""", UUID.randomUUID(), plan, amount, java.sql.Date.valueOf(paidOn), "RC" + UUID.randomUUID().toString().substring(0, 20),
				amount, by, voided ? Timestamp.from(NOW) : null, voided ? by : null, voided ? "Entered twice" : null);
	}

	private void fee(UUID admission, int amount, String paidOn, String status, UUID by) {
		jdbc().update("""
				insert into offline_fee_payments (id, admission_id, amount, method, reference, paid_on, status, recorded_by, recorded_at)
				values (?, ?, ?, 'CASH', 'FEE-REF', ?, ?, ?, now())
				""", UUID.randomUUID(), admission, amount, java.sql.Date.valueOf(paidOn), status, by);
	}

	private void payment(UUID student, int amount, String createdAt, String status, boolean deleted) {
		jdbc().update("""
				insert into payments (id, student_id, payable_type, payable_id, amount, method, transaction_ref, status, created_at,
				  updated_at, deleted_at)
				values (?, ?, 'COURSE_ENROLLMENT', ?, ?, 'BKASH', ?, ?, ?, now(), ?)
				""", UUID.randomUUID(), student, UUID.randomUUID(), amount, "P" + UUID.randomUUID().toString().substring(0, 24), status,
				Timestamp.from(Instant.parse(createdAt)), deleted ? Timestamp.from(NOW) : null);
	}

	private void staffPayment(UUID user, int amount, String paidOn, UUID voidedBy) {
		jdbc().update("""
				insert into staff_ledger (id, user_id, kind, amount, due_on, paid_on, method, created_by, created_at, voided_at, voided_by,
				  void_reason)
				values (?, ?, 'PAYMENT', ?, ?, ?, 'BANK', ?, now(), ?, ?, ?)
				""", UUID.randomUUID(), user, amount, java.sql.Date.valueOf(paidOn), java.sql.Date.valueOf(paidOn), user,
				voidedBy == null ? null : Timestamp.from(NOW), voidedBy, voidedBy == null ? null : "Entered twice");
	}

	private void classPayment(UUID classId, UUID instructor, int amount, String paidOn, String status, UUID by) {
		jdbc().update("""
				insert into offline_class_payments (id, class_id, instructor_id, amount, method, paid_on, status, recorded_by, recorded_at)
				values (?, ?, ?, ?, 'CASH', ?, ?, ?, now())
				""", UUID.randomUUID(), classId, instructor, amount, java.sql.Date.valueOf(paidOn), status, by);
	}

	private UUID offlineClass(UUID batch, String heldOn, String status) {
		return offlineClass(batch, heldOn, status, null, null);
	}

	/** A class on the date, ended at 11:20 Dhaka; an amount (the due amount) when one is given. */
	private UUID offlineClass(UUID batch, String heldOn, String status, UUID instructor, Integer amount) {
		UUID id = UUID.randomUUID();
		jdbc().update("""
				insert into offline_classes (id, batch_id, held_on, starts_at, ends_at, subject, kind, instructor_id, amount, status,
				  created_at, updated_at)
				values (?, ?, ?, '10:00', '11:20', 'Contract Act', 'LECTURE', ?, ?, ?, now(), now())
				""", id, batch, java.sql.Date.valueOf(heldOn), instructor, amount, status);
		return id;
	}

	private UUID admission(UUID student, UUID createdBy, UUID batch, int year) {
		UUID admission = UUID.randomUUID();
		jdbc().update("""
				insert into offline_admissions (id, student_id, status, created_by, created_at, submit_count, submitted_at,
				  full_name, father_name, mother_name, date_of_birth, email, gender, university, semester_batch, nid, mobile,
				  emergency_mobile, hall_mess, thana, district, present_address, permanent_address, batch_id)
				values (?, ?, 'ONBOARDED', ?, ?, 1, ?, 'Finance Student', 'Karim', 'Amena', '2004-05-10', ?, 'MALE',
				  'Dhaka University', '2024-25', '1234567890123', ?, '01912345678', 'Shahid Minar Hall', 'Dhanmondi', 'Dhaka',
				  'House 1, Road 2, Dhaka', 'Village, Thana, District', ?)
				""", admission, student, createdBy, Timestamp.from(NOW), Timestamp.from(NOW), "fin-" + admission + "@example.com",
				"017" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000)), batch);
		return admission;
	}

	private UUID batch(String token) throws Exception {
		MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/offline-batches").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"nameEn\":\"Finance\",\"nameBn\":\"ফিন্যান্স\",\"endsOn\":\"2200-06-30\",\"defaultMonthlyFee\":3000}"))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	/** A live exam, published, so an order and a grant can point at it. */
	private UUID exam() {
		UUID category = UUID.randomUUID();
		jdbc().update("insert into live_exam_categories (id, title_en, title_bn, position, created_at, updated_at)"
				+ " values (?, 'Category', 'বিভাগ', 0, now(), now())", category);
		UUID id = UUID.randomUUID();
		jdbc().update("""
				insert into live_exams (id, module_id, category_id, exam_type, title_en, title_bn, description_en, description_bn,
				  rules_en, rules_bn, paper_language, live_at, ends_at, duration_minutes, mode, status, finally_saved, pass_percent,
				  answers_changeable, is_free, access_days, published_at, created_at, updated_at)
				values (?, null, ?, 'MCQ', ?, 'পরীক্ষা', '', '', '', '', 'BN', ?, ?, 30, 'LIVE', 'PUBLISHED', true, 50, true, false, 30,
				  ?, now(), now())
				""", id, category, "Finance exam " + id, Timestamp.from(Instant.parse("2026-10-01T04:00:00Z")),
				Timestamp.from(Instant.parse("2200-12-31T04:00:00Z")), Timestamp.from(Instant.parse("2026-09-01T04:00:00Z")));
		return id;
	}

	private UUID manualGrant(UUID student, UUID exam) {
		UUID grant = UUID.randomUUID();
		jdbc().update("insert into live_exam_grants (id, student_id, scope, module_id, exam_id, order_id, created_at, source, reason)"
				+ " values (?, ?, 'EXAM', null, ?, null, ?, 'MANUAL', 'Finance test')", grant, student, exam, Timestamp.from(NOW));
		return grant;
	}

	private UUID seedUser(String role, String name) {
		UUID id = UUID.randomUUID();
		jdbc().update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, "+8801" + ThreadLocalRandom.current().nextInt(300000000, 999999999), "finance-" + id + "@example.com",
				passwords.encode("password1"), role);
		if ("STUDENT".equals(role)) {
			jdbc().update("INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, ?, ?)", id, name,
					"F" + id.toString().substring(0, 8));
		}
		else {
			jdbc().update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, ?)", id, name);
		}
		return id;
	}

	// ---- requests -------------------------------------------------------------------------------------------------------

	private JdbcTemplate jdbc() {
		return new JdbcTemplate(dataSource);
	}

	private static String summaryUrl(String from, String to) {
		return "/api/v1/finance/summary?from=" + from + "&to=" + to;
	}

	private static String entriesUrl(String from, String to) {
		return "/api/v1/finance/entries?from=" + from + "&to=" + to;
	}

	/** A GET as the signed-in user, answered with 200 and its JSON body. */
	private JsonNode api(String url, String token) throws Exception {
		return respond(get(url).header("Authorization", "Bearer " + token));
	}

	private JsonNode respond(RequestBuilder request) throws Exception {
		MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
		return json.readTree(result.getResponse().getContentAsString());
	}

	private JsonNode respondArray(RequestBuilder request) throws Exception {
		return respond(request);
	}

	private byte[] bytes(RequestBuilder request) throws Exception {
		return mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
	}

	private static BigDecimal sourceTotal(JsonNode summary, String source) {
		for (JsonNode row : summary.get("sources")) {
			if (row.get("source").asText().equals(source)) {
				return row.get("total").decimalValue();
			}
		}
		return BigDecimal.ZERO;
	}

	private static BigDecimal sumOf(JsonNode items, String source) {
		BigDecimal total = BigDecimal.ZERO;
		for (JsonNode item : items) {
			if (item.get("source").asText().equals(source)) {
				total = total.add(item.get("amount").decimalValue());
			}
		}
		return total;
	}

	private static boolean hasAmount(JsonNode items, String amount) {
		for (JsonNode item : items) {
			if (item.get("amount").decimalValue().compareTo(new BigDecimal(amount)) == 0) {
				return true;
			}
		}
		return false;
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
