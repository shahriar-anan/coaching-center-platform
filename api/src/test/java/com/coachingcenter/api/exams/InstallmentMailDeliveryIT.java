package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.auth.CapturingEmailConfig;

/**
 * The last step of the installment emails: a queued row is built into a real message and handed to the sender (plan section 7).
 * The other installment tests stop at the queue. Each case runs the same scan the five-minute job runs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, InstallmentTestSupport.ClockConfig.class, InstallmentMailDeliveryIT.MailConfig.class })
class InstallmentMailDeliveryIT extends InstallmentTestSupport {

	private static final String RECEIPT = "INSTALLMENT_RECEIPT";

	@Autowired
	private ExamMailService examMail;

	@Autowired
	private CapturingExamMailSender sender;

	@Autowired
	private InstallmentReminderJob job;

	@Autowired
	private MutableClock clock;

	@BeforeEach
	void reset() {
		sender.clear();
		clock.set(Instant.parse("2026-10-20T04:00:00Z"));
	}

	@Test
	void aPaymentsReceiptIsBuiltAndSentToAVerifiedStudent() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Receipt Master"));
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID student = seed("STUDENT", "Receipt Student");
		verify(student);
		String plan = plan(master, system, student, "Delivered receipt");

		examMail.scan();

		String paymentId = onlyPayment(plan);
		String receiptNo = jdbc().queryForObject("select receipt_no from live_exam_direct_payments where id = ?::uuid", String.class,
				paymentId);
		assertThat(mailStatus(RECEIPT, paymentId)).isEqualTo("SENT");
		List<CapturingExamMailSender.Sent> sent = sender.sentTo(email(student));
		assertThat(sent).hasSize(1);
		CapturingExamMailSender.Sent mail = sent.get(0);
		assertThat(mail.subject()).isNotBlank();
		assertThat(mail.text()).contains(receiptNo).contains("100").contains("200").contains("Delivered receipt");
	}

	@Test
	void anUnverifiedStudentGetsNoReceiptEmail() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Unverified Master"));
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID student = seed("STUDENT", "Unverified Student");
		String plan = plan(master, system, student, "Unverified receipt");

		examMail.scan();

		assertThat(rows(RECEIPT, onlyPayment(plan))).isZero();
		assertThat(sender.sentTo(email(student))).isEmpty();
	}

	@Test
	void aPaymentVoidedBeforeTheScanSendsNoReceipt() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Void Master"));
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID student = seed("STUDENT", "Void Student");
		verify(student);
		String plan = plan(master, system, student, "Voided receipt");
		String first = onlyPayment(plan);
		pay(master, plan, "100", "TXN-SECOND");
		String second = jdbc().queryForObject(
				"select id::text from live_exam_direct_payments where plan_id = ?::uuid and id <> ?::uuid", String.class, plan, first);
		mockMvc.perform(post("/api/v1/installment-payments/" + second + "/void").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\":\"Typed in by mistake\"}"))
			.andExpect(status().isOk());

		examMail.scan();

		assertThat(mailStatus(RECEIPT, first)).isEqualTo("SENT");
		assertThat(mailStatus(RECEIPT, second)).isEqualTo("SKIPPED");
		assertThat(sender.sentTo(email(student))).hasSize(1);
	}

	@Test
	void theDueSoonReminderIsBuiltAndSentToTheStudentAndTheMaster() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Due Soon Master"));
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID student = seed("STUDENT", "Due Soon Student");
		verify(student);
		verifyMasters();
		String plan = plan(master, system, student, "Delivered due soon");
		examMail.scan();
		sender.clear();

		clock.set(Instant.parse("2026-11-17T04:00:00Z"));
		job.runOn(LocalDate.parse("2026-11-17"));
		examMail.scan();

		String installment = secondInstallment(plan);
		assertThat(sentRows("INSTALLMENT_DUE_SOON", installment)).isGreaterThanOrEqualTo(2);
		List<CapturingExamMailSender.Sent> sent = sender.sentTo(email(student));
		assertThat(sent).hasSize(1);
		assertThat(sent.get(0).subject()).isNotBlank();
		assertThat(sent.get(0).text()).contains("Delivered due soon");
	}

	@Test
	void theOverdueReminderIsBuiltAndSentAndStopsOnceTheInstallmentIsPaid() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Overdue Mail Master"));
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID student = seed("STUDENT", "Overdue Mail Student");
		verify(student);
		verifyMasters();
		String plan = plan(master, system, student, "Delivered overdue");
		examMail.scan();
		sender.clear();

		clock.set(Instant.parse("2026-11-21T04:00:00Z"));
		job.runOn(LocalDate.parse("2026-11-21"));
		examMail.scan();

		String installment = secondInstallment(plan);
		assertThat(sentRows("INSTALLMENT_OVERDUE", installment)).isGreaterThanOrEqualTo(2);
		assertThat(sender.sentTo(email(student))).hasSize(1);

		// The student pays before the next week's run: a reminder queued for a paid row is not sent.
		sender.clear();
		clock.set(Instant.parse("2026-11-28T04:00:00Z"));
		job.runOn(LocalDate.parse("2026-11-28"));
		pay(master, plan, "100", "TXN-LATE");
		examMail.scan();
		List<CapturingExamMailSender.Sent> afterPaying = sender.sentTo(email(student));
		assertThat(afterPaying).hasSize(1);
		assertThat(afterPaying.get(0).text()).contains("Installment payment receipt");
		assertThat(sentRows("INSTALLMENT_OVERDUE", installment)).isGreaterThanOrEqualTo(2);
		Long skipped = jdbc().queryForObject(
				"select count(*) from exam_outbound_mail where kind = 'INSTALLMENT_OVERDUE' and status = 'SKIPPED' and message_key like ?",
				Long.class, "%" + installment + "%");
		assertThat(skipped).isGreaterThanOrEqualTo(2);
	}

	private String plan(String master, String system, UUID student, String title) throws Exception {
		UUID module = module(system, title);
		return enrol(master, student, module, "300", "100", "TXN-" + Math.abs(title.hashCode()), row("100", TODAY),
				row("100", "2026-11-20"), row("100", "2026-12-20")).get("paymentPlanId").asText();
	}

	private void verify(UUID user) {
		jdbc().update("update users set email_verified_at = now() where id = ?", user);
	}

	private void verifyMasters() {
		jdbc().update("update users set email_verified_at = now() where role = 'MASTER_ADMIN'");
	}

	private String email(UUID user) {
		return jdbc().queryForObject("select email from users where id = ?", String.class, user);
	}

	private String onlyPayment(String plan) {
		return jdbc().queryForObject("select id::text from live_exam_direct_payments where plan_id = ?::uuid", String.class, plan);
	}

	private String secondInstallment(String plan) {
		return jdbc().queryForObject("select id::text from live_exam_installments where plan_id = ?::uuid and seq = 2", String.class,
				plan);
	}

	private String mailStatus(String kind, String key) {
		return jdbc().queryForObject("select status from exam_outbound_mail where kind = ? and message_key = ?", String.class, kind,
				key);
	}

	private long rows(String kind, String key) {
		Long count = jdbc().queryForObject("select count(*) from exam_outbound_mail where kind = ? and message_key = ?", Long.class,
				kind, key);
		return count == null ? 0 : count;
	}

	private long sentRows(String kind, String installment) {
		Long count = jdbc().queryForObject(
				"select count(*) from exam_outbound_mail where kind = ? and status = 'SENT' and message_key like ?", Long.class, kind,
				"%" + installment + "%");
		return count == null ? 0 : count;
	}

	@TestConfiguration
	static class MailConfig {

		@Bean
		@Primary
		CapturingExamMailSender capturingExamMailSender() {
			return new CapturingExamMailSender();
		}

	}

}
