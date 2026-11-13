package com.coachingcenter.api.offline;

import static org.assertj.core.api.Assertions.assertThat;


import java.math.BigDecimal;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The monthly fee of an offline admission (plan sections 4.3, 5, 6.1 and 7): payments, approval, rejection and voiding, matching to
 * the dues, receipts, the student's own view, and the reminder job. The admissions are inserted directly, as the desk and onboarding
 * flows would leave them, so the fee code is tested on its own.
 *
 * The clock is fixed at 10:00 in Dhaka on 10 December 2026. Each test makes its own students and admissions, so the tests do not depend
 * on each other or on the order they run in.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, OfflineFeeIT.ClockConfig.class })
class OfflineFeeIT extends PostgresIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-12-10T04:00:00Z");

	private static final LocalDate DUE_1 = LocalDate.of(2026, 12, 15);

	private static final LocalDate DUE_2 = LocalDate.of(2027, 1, 15);

	private static final LocalDate DUE_3 = LocalDate.of(2027, 2, 15);

	private static final String PASSWORD = "password1";

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
	private OfflineFeeReminderJob reminders;

	@Autowired
	private OfflineFeeMails mails;

	/** The Master Admin who owns every admission row a test writes. JUnit makes a new instance per test method. */
	private UUID master;

	@BeforeEach
	void fixClock() {
		clock.set(NOW);
		master = seed("MASTER_ADMIN", "Fee Master");
	}

	// ---- recording and approval -----------------------------------------------------------------------------------------

	@Test
	void staffPaymentIsPendingAndCountsForNothing() throws Exception {
		UUID staff = seed("SYSTEM_ADMIN", "Fee Staff");
		UUID student = student("Pending Payer");
		UUID admission = onboarded(student, "ONBOARDED");
		MvcResult recorded = post(token(staff), "/api/v1/offline-admissions/" + admission + "/payments",
				payBody("3000", "CASH", "DESK-1", "2026-12-10"));
		assertThat(recorded.getResponse().getStatus()).isEqualTo(201);
		JsonNode payment = json.readTree(recorded.getResponse().getContentAsString());
		assertThat(payment.get("status").asText()).isEqualTo("PENDING");
		assertThat(payment.hasNonNull("receiptNo")).isFalse();
		assertThat(allocations(payment.get("id").asText())).isZero();
		assertThat(schedule(master, admission).get("owed").decimalValue()).isEqualByComparingTo("9000");
		assertThat(notifications("OFFLINE_PAYMENT_PENDING", master)).isEqualTo(1L);
		assertThat(notifications("OFFLINE_PAYMENT_PENDING", staff)).isZero();
	}

	@Test
	void masterPaymentIsApprovedAtOnceNumberedMatchedAndNotifiesStudent() throws Exception {
		UUID student = student("Approved Payer");
		UUID admission = onboarded(student, "ONBOARDED");
		JsonNode payment = recordAs(master, admission, "3000", "BKASH", "TRX-100", "2026-12-10");
		String id = payment.get("id").asText();
		assertThat(payment.get("status").asText()).isEqualTo("APPROVED");
		assertThat(payment.get("receiptNo").asText()).startsWith("RCPT-2026-");
		assertThat(allocations(id)).isEqualTo(1L);
		assertThat(dueOwed(admission, DUE_1)).isEqualByComparingTo("0");
		assertThat(studentNotices("OFFLINE_FEE_RECEIVED", student, id)).isEqualTo(1L);
		assertThat(mailRows("OFFLINE_FEE_RECEIPT", student, id)).isEqualTo(1L);
	}

	@Test
	void approvingAPendingPaymentNumbersAndMatchesIt() throws Exception {
		UUID staff = seed("SYSTEM_ADMIN", "Approve Staff");
		UUID student = student("Approved Later");
		UUID admission = onboarded(student, "ONBOARDED");
		String id = recordAs(staff, admission, "3000", "NAGAD", "NG-200", "2026-12-10").get("id").asText();
		assertThat(allocations(id)).isZero();
		MvcResult approved = post(token(master), "/api/v1/offline-fee-payments/" + id + "/approve", "");
		assertThat(approved.getResponse().getStatus()).isEqualTo(200);
		JsonNode view = json.readTree(approved.getResponse().getContentAsString());
		assertThat(view.get("status").asText()).isEqualTo("APPROVED");
		assertThat(view.get("receiptNo").asText()).startsWith("RCPT-2026-");
		assertThat(dueOwed(admission, DUE_1)).isEqualByComparingTo("0");
		assertThat(studentNotices("OFFLINE_FEE_RECEIVED", student, id)).isEqualTo(1L);
		assertThat(mailRows("OFFLINE_FEE_RECEIPT", student, id)).isEqualTo(1L);
		assertThat(notifications("OFFLINE_PAYMENT_APPROVED", staff)).isEqualTo(1L);
	}

	@Test
	void rejectionNotifiesTheRecorderWithTheReasonAndMatchesNothing() throws Exception {
		UUID staff = seed("SYSTEM_ADMIN", "Reject Staff");
		UUID student = student("Rejected Payer");
		UUID admission = onboarded(student, "ONBOARDED");
		String id = recordAs(staff, admission, "3000", "BANK", "BK-300", "2026-12-10").get("id").asText();
		MvcResult rejected = post(token(master), "/api/v1/offline-fee-payments/" + id + "/reject",
				"{\"reason\":\"Slip does not match the bank statement\"}");
		assertThat(rejected.getResponse().getStatus()).isEqualTo(200);
		assertThat(json.readTree(rejected.getResponse().getContentAsString()).get("status").asText()).isEqualTo("REJECTED");
		assertThat(allocations(id)).isZero();
		assertThat(notifications("OFFLINE_PAYMENT_REJECTED", staff)).isEqualTo(1L);
		assertThat(jdbcString("select payload->>'reason' from staff_notifications where kind = 'OFFLINE_PAYMENT_REJECTED'"
				+ " and recipient_id = ?", staff)).isEqualTo("Slip does not match the bank statement");
		assertThat(post(token(master), "/api/v1/offline-fee-payments/" + id + "/approve", "").getResponse().getStatus())
			.isEqualTo(409);
		assertThat(post(token(master), "/api/v1/offline-fee-payments/" + id + "/reject", "{\"reason\":\"again\"}")
			.getResponse()
			.getStatus()).isEqualTo(409);
	}

	@Test
	void voidRestoresTheDueAndNotifiesTheStudent() throws Exception {
		UUID student = student("Voided Payer");
		UUID admission = onboarded(student, "ONBOARDED");
		String id = recordAs(master, admission, "3000", "CASH", "DESK-9", "2026-12-10").get("id").asText();
		assertThat(dueOwed(admission, DUE_1)).isEqualByComparingTo("0");
		MvcResult voided = post(token(master), "/api/v1/offline-fee-payments/" + id + "/void",
				"{\"reason\":\"Entered on the wrong student\"}");
		assertThat(voided.getResponse().getStatus()).isEqualTo(200);
		JsonNode view = json.readTree(voided.getResponse().getContentAsString());
		assertThat(view.get("status").asText()).isEqualTo("VOIDED");
		assertThat(view.get("voidReason").asText()).isEqualTo("Entered on the wrong student");
		assertThat(allocations(id)).isZero();
		assertThat(dueOwed(admission, DUE_1)).isEqualByComparingTo("3000");
		assertThat(studentNotices("OFFLINE_FEE_VOIDED", student, id)).isEqualTo(1L);
		assertThat(post(token(master), "/api/v1/offline-fee-payments/" + id + "/void", "{\"reason\":\"again\"}").getResponse()
			.getStatus()).isEqualTo(409);
		assertThat(post(token(master), "/api/v1/offline-fee-payments/" + id + "/approve", "").getResponse().getStatus())
			.isEqualTo(409);
	}

	// ---- matching ---------------------------------------------------------------------------------------------------------

	@Test
	void partialPaymentMatchesTheOldestDueFirstAndLeavesTheRest() throws Exception {
		UUID admission = onboarded(student("Partial Payer"), "ONBOARDED");
		recordAs(master, admission, "2000", "CASH", "P-1", "2026-12-10");
		JsonNode schedule = schedule(master, admission);
		assertThat(due(schedule, 1).get("owed").decimalValue()).isEqualByComparingTo("1000");
		assertThat(due(schedule, 1).get("state").asText()).isEqualTo("PART_PAID");
		assertThat(due(schedule, 2).get("owed").decimalValue()).isEqualByComparingTo("3000");
		assertThat(due(schedule, 2).get("state").asText()).isEqualTo("UNPAID");
	}

	@Test
	void aPaymentLargerThanTheOldestDueCarriesOverToTheNext() throws Exception {
		UUID admission = onboarded(student("Carry Payer"), "ONBOARDED");
		recordAs(master, admission, "2000", "CASH", "C-1", "2026-12-10");
		recordAs(master, admission, "2500", "ROCKET", "C-2", "2026-12-10");
		JsonNode schedule = schedule(master, admission);
		assertThat(due(schedule, 1).get("owed").decimalValue()).isEqualByComparingTo("0");
		assertThat(due(schedule, 1).get("state").asText()).isEqualTo("PAID");
		assertThat(due(schedule, 2).get("owed").decimalValue()).isEqualByComparingTo("1500");
		assertThat(due(schedule, 2).get("paid").decimalValue()).isEqualByComparingTo("1500");
	}

	@Test
	void overPaymentIsRefusedWithTheAmountStillOwed() throws Exception {
		UUID admission = onboarded(student("Over Payer"), "ONBOARDED");
		MvcResult refused = post(token(master), "/api/v1/offline-admissions/" + admission + "/payments",
				payBody("9001", "CASH", "OVER-1", "2026-12-10"));
		assertThat(refused.getResponse().getStatus()).isEqualTo(400);
		JsonNode body = json.readTree(refused.getResponse().getContentAsString());
		assertThat(body.get("code").asText()).isEqualTo("VALIDATION_FAILED");
		assertThat(body.get("message").asText()).contains("9000.00");
		assertThat(jdbcCount("select count(*) from offline_fee_payments where admission_id = ?", admission)).isZero();
	}

	@Test
	void paymentOnANonOnboardedAdmissionIsRefusedWithAdmissionState() throws Exception {
		UUID admission = onboarded(student("Not Yet Onboarded"), "SUBMITTED");
		MvcResult refused = post(token(master), "/api/v1/offline-admissions/" + admission + "/payments",
				payBody("1000", "CASH", "EARLY-1", "2026-12-10"));
		assertThat(refused.getResponse().getStatus()).isEqualTo(409);
		assertThat(json.readTree(refused.getResponse().getContentAsString()).get("code").asText()).isEqualTo("ADMISSION_STATE");
	}

	@Test
	void receiptNumbersAreUniqueAcrossFeesAndDirectPayments() throws Exception {
		UUID admission = onboarded(student("Receipt Payer"), "ONBOARDED");
		String first = recordAs(master, admission, "1000", "CASH", "R-1", "2026-12-10").get("receiptNo").asText();
		String second = recordAs(master, admission, "1000", "CASH", "R-2", "2026-12-10").get("receiptNo").asText();
		assertThat(second).isNotEqualTo(first);
		assertThat(jdbcCount("""
				select count(*) - count(distinct r) from (
				  select receipt_no as r from offline_fee_payments where receipt_no is not null
				  union all select receipt_no from live_exam_direct_payments
				  union all select receipt_no from live_exam_orders where receipt_no is not null) numbers
				""")).isZero();
	}

	// ---- the student's own view and receipts --------------------------------------------------------------------------------

	@Test
	void aStudentReadsOnlyTheirOwnScheduleAndNeverAPendingPayment() throws Exception {
		UUID staff = seed("SYSTEM_ADMIN", "View Staff");
		UUID studentA = student("Student Alpha");
		UUID admissionA = onboarded(studentA, "ONBOARDED");
		UUID studentB = student("Student Beta");
		onboarded(studentB, "ONBOARDED");
		String approved = recordAs(master, admissionA, "1000", "CASH", "V-1", "2026-12-10").get("id").asText();
		String pending = recordAs(staff, admissionA, "500", "BKASH", "V-2", "2026-12-10").get("id").asText();

		MvcResult own = get(token(studentA), "/api/v1/me/admission/fees");
		assertThat(own.getResponse().getStatus()).isEqualTo(200);
		JsonNode schedule = json.readTree(own.getResponse().getContentAsString());
		assertThat(schedule.get("payments")).hasSize(1);
		assertThat(schedule.get("payments").get(0).get("id").asText()).isEqualTo(approved);
		assertThat(schedule.get("owed").decimalValue()).isEqualByComparingTo("8000");
		assertThat(schedule.get("dues")).hasSize(3);

		JsonNode otherSchedule = json.readTree(get(token(studentB), "/api/v1/me/admission/fees").getResponse().getContentAsString());
		assertThat(otherSchedule.get("payments")).isEmpty();
		assertThat(otherSchedule.get("owed").decimalValue()).isEqualByComparingTo("9000");

		assertThat(get(token(studentB), "/api/v1/me/admission/payments/" + approved + "/receipt.pdf").getResponse().getStatus())
			.isEqualTo(404);
		assertThat(get(token(studentA), "/api/v1/me/admission/payments/" + pending + "/receipt.pdf").getResponse().getStatus())
			.isEqualTo(404);
		assertThat(get(token(studentA), "/api/v1/offline-admissions/" + admissionA + "/fees").getResponse().getStatus())
			.isEqualTo(403);
		assertThat(post(token(studentA), "/api/v1/offline-admissions/" + admissionA + "/payments",
				payBody("100", "CASH", "NOPE", "2026-12-10")).getResponse().getStatus()).isEqualTo(403);
	}

	@Test
	void theApprovedReceiptIsAPdfAndTheMailTextCarriesItsNumber() throws Exception {
		UUID student = student("Receipt Reader");
		UUID admission = onboarded(student, "ONBOARDED");
		JsonNode payment = recordAs(master, admission, "3000", "NAGAD", "RCPT-READ", "2026-12-10");
		String id = payment.get("id").asText();
		MvcResult pdf = get(token(master), "/api/v1/offline-fee-payments/" + id + "/receipt.pdf");
		assertThat(pdf.getResponse().getStatus()).isEqualTo(200);
		assertThat(pdf.getResponse().getContentType()).isEqualTo(MediaType.APPLICATION_PDF_VALUE);
		assertThat(new String(pdf.getResponse().getContentAsByteArray(), 0, 4)).isEqualTo("%PDF");

		OfflineFeeMails.Mail mail = mails.render("OFFLINE_FEE_RECEIPT", id, student);
		assertThat(mail).isNotNull();
		assertThat(mail.to()).isEqualTo(jdbcString("select email from users where id = ?", student));
		assertThat(mail.subject()).contains(payment.get("receiptNo").asText());
		assertThat(mail.text()).contains(payment.get("receiptNo").asText());
	}

	@Test
	void theApprovalQueueShowsEveryPendingPaymentToTheMasterAndOwnToStaff() throws Exception {
		UUID staffOne = seed("SYSTEM_ADMIN", "Queue Staff One");
		UUID staffTwo = seed("SYSTEM_ADMIN", "Queue Staff Two");
		UUID admission = onboarded(student("Queue Payer"), "ONBOARDED");
		String mine = recordAs(staffOne, admission, "100", "CASH", "Q-1", "2026-12-10").get("id").asText();
		String theirs = recordAs(staffTwo, admission, "200", "CASH", "Q-2", "2026-12-10").get("id").asText();

		JsonNode forMaster = json.readTree(get(token(master), "/api/v1/offline-fee-payments?status=PENDING")
			.getResponse()
			.getContentAsString());
		assertThat(ids(forMaster)).contains(mine, theirs);
		JsonNode forStaff = json.readTree(get(token(staffOne), "/api/v1/offline-fee-payments?status=PENDING")
			.getResponse()
			.getContentAsString());
		assertThat(ids(forStaff)).contains(mine).doesNotContain(theirs);
		assertThat(get(token(seed("INSTRUCTOR", "Queue Instructor")), "/api/v1/offline-fee-payments").getResponse().getStatus())
			.isEqualTo(403);
	}

	// ---- reminders ----------------------------------------------------------------------------------------------------------

	@Test
	void reminderJobSendsOneReminderPerWindowAndAddsNothingOnARepeatRun() throws Exception {
		UUID student = student("Reminded Student");
		UUID admission = onboarded(student, "ONBOARDED");
		UUID dueOne = dueId(admission, DUE_1);
		reminders.runOn(LocalDate.of(2026, 12, 12));
		assertThat(studentNotices("OFFLINE_FEE_DUE_SOON", student, dueOne.toString())).isEqualTo(1L);
		assertThat(mailRows("OFFLINE_FEE_DUE_SOON", student, dueOne.toString())).isEqualTo(1L);

		assertThat(reminders.runOn(LocalDate.of(2026, 12, 12))).isZero();
		assertThat(studentNotices("OFFLINE_FEE_DUE_SOON", student, dueOne.toString())).isEqualTo(1L);

		reminders.runOn(DUE_1);
		assertThat(studentNotices("OFFLINE_FEE_DUE_TODAY", student, dueOne.toString())).isEqualTo(1L);
		assertThat(mailRows("OFFLINE_FEE_DUE_TODAY", student, dueOne.toString())).isEqualTo(1L);

		reminders.runOn(DUE_1.plusDays(3));
		assertThat(studentNotices("OFFLINE_FEE_OVERDUE", student, dueOne.toString())).isEqualTo(1L);
		assertThat(mailRows("OFFLINE_FEE_OVERDUE", student, dueOne.toString())).isEqualTo(1L);
		assertThat(overdueStaffNotices(master, dueOne)).isEqualTo(1L);
		assertThat(reminders.runOn(DUE_1.plusDays(3))).isZero();
		assertThat(overdueStaffNotices(master, dueOne)).isEqualTo(1L);

		reminders.runOn(DUE_1.plusDays(10));
		assertThat(overdueStaffNotices(master, dueOne)).isEqualTo(2L);
		assertThat(studentNotices("OFFLINE_FEE_OVERDUE", student, dueOne.toString())).isEqualTo(1L);
	}

	@Test
	void aSuspendedStudentGetsNoNoticeButTheAdminsAreStillTold() throws Exception {
		UUID admins = seed("SYSTEM_ADMIN", "Overdue Staff");
		UUID student = student("Suspended Student");
		UUID admission = onboarded(student, "ONBOARDED");
		jdbc().update("update users set status = 'INACTIVE' where id = ?", student);
		UUID dueOne = dueId(admission, DUE_1);
		reminders.runOn(DUE_1.plusDays(3));
		assertThat(studentNotices("OFFLINE_FEE_OVERDUE", student, dueOne.toString())).isZero();
		assertThat(mailRows("OFFLINE_FEE_OVERDUE", student, dueOne.toString())).isZero();
		assertThat(overdueStaffNotices(master, dueOne)).isEqualTo(1L);
		assertThat(overdueStaffNotices(admins, dueOne)).isEqualTo(1L);
	}

	// ---- helpers ------------------------------------------------------------------------------------------------------------

	private JdbcTemplate jdbc() {
		return new JdbcTemplate(dataSource);
	}

	private long jdbcCount(String sql, Object... args) {
		Long count = jdbc().queryForObject(sql, Long.class, args);
		return count == null ? 0L : count;
	}

	private String jdbcString(String sql, Object... args) {
		return jdbc().queryForObject(sql, String.class, args);
	}

	/** A payment body: the amount as written, the method, the reference, and the paid-on date. */
	private static String payBody(String amount, String method, String reference, String paidOn) {
		return """
				{"amount":%s,"method":"%s","reference":"%s","paidOn":"%s","note":"test"}
				""".formatted(amount, method, reference, paidOn);
	}

	/** Records a payment as the given user and asserts it was created. */
	private JsonNode recordAs(UUID actor, UUID admission, String amount, String method, String reference, String paidOn)
			throws Exception {
		MvcResult result = post(token(actor), "/api/v1/offline-admissions/" + admission + "/payments",
				payBody(amount, method, reference, paidOn));
		assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(201);
		return json.readTree(result.getResponse().getContentAsString());
	}

	private JsonNode schedule(UUID actor, UUID admission) throws Exception {
		MvcResult result = get(token(actor), "/api/v1/offline-admissions/" + admission + "/fees");
		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		return json.readTree(result.getResponse().getContentAsString());
	}

	/** The due with this month number on a schedule. */
	private static JsonNode due(JsonNode schedule, int seq) {
		for (JsonNode due : schedule.get("dues")) {
			if (due.get("seq").asInt() == seq) {
				return due;
			}
		}
		throw new AssertionError("no due " + seq);
	}

	private static java.util.List<String> ids(JsonNode list) {
		java.util.List<String> ids = new java.util.ArrayList<>();
		list.forEach(item -> ids.add(item.get("id").asText()));
		return ids;
	}

	private BigDecimal dueOwed(UUID admission, LocalDate dueOn) {
		BigDecimal owed = jdbc().queryForObject("""
				select d.amount - coalesce(sum(a.amount) filter (where p.status = 'APPROVED'), 0)
				from offline_fee_dues d
				left join offline_fee_allocations a on a.due_id = d.id
				left join offline_fee_payments p on p.id = a.payment_id
				where d.admission_id = ? and d.due_on = ? group by d.id, d.amount
				""", BigDecimal.class, admission, dueOn);
		return owed;
	}

	private UUID dueId(UUID admission, LocalDate dueOn) {
		return jdbc().queryForObject("select id from offline_fee_dues where admission_id = ? and due_on = ?", UUID.class, admission,
				dueOn);
	}

	private long allocations(String paymentId) {
		return jdbcCount("select count(*) from offline_fee_allocations where payment_id = ?", UUID.fromString(paymentId));
	}

	private long notifications(String kind, UUID recipient) {
		return jdbcCount("select count(*) from staff_notifications where kind = ? and recipient_id = ?", kind, recipient);
	}

	private long overdueStaffNotices(UUID recipient, UUID dueId) {
		return jdbcCount("select count(*) from staff_notifications where kind = 'OFFLINE_FEE_OVERDUE' and recipient_id = ?"
				+ " and dedupe_key like ?", recipient, "OFFLINE_FEE_OVERDUE:" + dueId + ":%");
	}

	/** The student notices of one kind for one key (a payment or a due), so a repeat is visible as a count above one. */
	private long studentNotices(String kind, UUID student, String key) {
		return jdbcCount("select count(*) from student_notices where user_id = ? and kind = ? and message_key = ?", student, kind,
				key);
	}

	private long studentNotices(String kind, UUID student, UUID key) {
		return studentNotices(kind, student, key.toString());
	}

	private long mailRows(String kind, UUID student, String key) {
		return jdbcCount("select count(*) from exam_outbound_mail where user_id = ? and kind = ? and message_key = ?", student, kind,
				key);
	}

	private long mailRows(String kind, UUID student, UUID key) {
		return mailRows(kind, student, key.toString());
	}

	private MvcResult post(String token, String url, String body) throws Exception {
		return mockMvc.perform(MockMvcRequestBuilders.post(url).header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body.isEmpty() ? "{}" : body)).andReturn();
	}

	private MvcResult get(String token, String url) throws Exception {
		return mockMvc.perform(MockMvcRequestBuilders.get(url).header("Authorization", "Bearer " + token)).andReturn();
	}

	/** A student with a verified email, so the mail rows are written for them. */
	private UUID student(String name) {
		UUID id = seed("STUDENT", name);
		jdbc().update("update users set email_verified_at = now() where id = ?", id);
		return id;
	}

	/**
	 * An admission with three monthly dues of 3,000: 15 December 2026, 15 January and 15 February 2027. The admission is inserted in the
	 * given state, as the onboarding flow leaves it.
	 */
	private UUID onboarded(UUID student, String status) {
		UUID admission = UUID.randomUUID();
		jdbc().update("""
				insert into offline_admissions (id, student_id, status, created_by, created_at, submit_count, full_name,
				  monthly_fee, admitted_on, first_due_on, course_ends_on)
				values (?, ?, ?, ?, now(), 1, ?, 3000, '2026-11-15', ?, '2027-02-20')
				""", admission, student, status, master, "Admission of " + student.toString().substring(0, 8), DUE_1);
		if (OfflineAdmission.ONBOARDED.equals(status)) {
			LocalDate[] dues = { DUE_1, DUE_2, DUE_3 };
			for (int i = 0; i < dues.length; i++) {
				jdbc().update("""
						insert into offline_fee_dues (id, admission_id, seq, due_on, amount, created_at)
						values (?, ?, ?, ?, 3000, now())
						""", UUID.randomUUID(), admission, (short) (i + 1), dues[i]);
			}
		}
		return admission;
	}

	private UUID seed(String role, String name) {
		UUID id = UUID.randomUUID();
		String phone = "+8801" + ThreadLocalRandom.current().nextInt(300000000, 999999999);
		jdbc().update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, phone, "offline-fee-" + id + "@example.com", passwords.encode(PASSWORD), role);
		if ("STUDENT".equals(role)) {
			jdbc().update("INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, ?, ?)", id, name,
					"F" + id.toString().substring(0, 8));
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
					{"identifier":"%s","password":"%s"}
					""".formatted(phone, PASSWORD)))
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
