package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;

/**
 * The installment reminder job, run day by day through {@code runOn} (plan section 6.2). The schedule is the same in every test:
 * 100 paid today, then 100 due on 20 November and 100 due on 20 December 2026, so the reminder is about 20 November.
 * Due soon starts three days before (17 November). Overdue starts the day after (21 November), then once a week.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, InstallmentTestSupport.ClockConfig.class })
class InstallmentReminderIT extends InstallmentTestSupport {

	private static final String DUE_SOON = "INSTALLMENT_DUE_SOON";

	private static final String OVERDUE = "INSTALLMENT_OVERDUE";

	@Autowired
	private InstallmentReminderJob job;

	@Test
	void aDueSoonReminderReachesTheStudentAndTheMasterOnceEach() throws Exception {
		UUID masterId = seed("MASTER_ADMIN", "Reminder Master");
		String master = token(masterId);
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID student = seed("STUDENT", "Reminded Student");
		String plan = plan(master, system, student, "Reminded");

		job.runOn(LocalDate.parse("2026-11-01"));
		assertThat(studentNotices(student, DUE_SOON)).isZero();

		job.runOn(LocalDate.parse("2026-11-17"));
		assertThat(studentNotices(student, DUE_SOON)).isEqualTo(1);
		assertThat(staffNotices(masterId, DUE_SOON, plan)).isEqualTo(1);

		// A repeat run on the same day adds nothing, to the student or the Master Admin.
		job.runOn(LocalDate.parse("2026-11-17"));
		assertThat(studentNotices(student, DUE_SOON)).isEqualTo(1);
		assertThat(staffNotices(masterId, DUE_SOON, plan)).isEqualTo(1);
	}

	@Test
	void anOverdueReminderIsSentOnceAWeekAndARepeatInTheSameWeekAddsNothing() throws Exception {
		UUID masterId = seed("MASTER_ADMIN", "Overdue Master");
		String master = token(masterId);
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID student = seed("STUDENT", "Overdue Student");
		String plan = plan(master, system, student, "Overdue");

		job.runOn(LocalDate.parse("2026-11-21"));
		assertThat(studentNotices(student, OVERDUE)).isEqualTo(1);
		assertThat(staffNotices(masterId, OVERDUE, plan)).isEqualTo(1);

		// The day after is still the same week, so nothing new.
		job.runOn(LocalDate.parse("2026-11-22"));
		assertThat(studentNotices(student, OVERDUE)).isEqualTo(1);

		// Eight days after the due date starts the second week.
		job.runOn(LocalDate.parse("2026-11-28"));
		assertThat(studentNotices(student, OVERDUE)).isEqualTo(2);
		assertThat(staffNotices(masterId, OVERDUE, plan)).isEqualTo(2);
	}

	@Test
	void aPlanPaidInFullGetsNoReminder() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Paid Master"));
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID student = seed("STUDENT", "Paid Student");
		UUID module = module(system, "Paid off");
		String plan = enrol(master, student, module, "300", "100", "TXN-P1", row("100", TODAY), row("100", "2026-11-20"),
				row("100", "2026-12-20")).get("paymentPlanId").asText();
		pay(master, plan, "100", "TXN-P2");
		pay(master, plan, "100", "TXN-P3");

		job.runOn(LocalDate.parse("2026-11-17"));
		job.runOn(LocalDate.parse("2026-11-21"));
		assertThat(studentNotices(student, DUE_SOON)).isZero();
		assertThat(studentNotices(student, OVERDUE)).isZero();
	}

	@Test
	void aRevokedPlanGetsNoReminder() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Revoke Master"));
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID student = seed("STUDENT", "Revoked Reminder Student");
		UUID module = module(system, "Revoked reminder");
		JsonNode grant = enrol(master, student, module, "300", "100", "TXN-V1", row("100", TODAY), row("100", "2026-11-20"),
				row("100", "2026-12-20"));
		mockMvc.perform(delete("/api/v1/live-exam-grants/" + grant.get("id").asText()).param("reason", "Did not pay")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());

		job.runOn(LocalDate.parse("2026-11-17"));
		assertThat(studentNotices(student, DUE_SOON)).isZero();
	}

	@Test
	void aStudentEmailIsQueuedOnlyWhenTheAddressIsVerified() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Email Master"));
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID module = module(system, "Email rule");
		UUID unverified = seed("STUDENT", "Unverified Student");
		UUID verified = seed("STUDENT", "Verified Student");
		enrol(master, unverified, module, "300", "100", "TXN-E1", row("100", TODAY), row("100", "2026-11-20"),
				row("100", "2026-12-20"));
		enrol(master, verified, module, "300", "100", "TXN-E2", row("100", TODAY), row("100", "2026-11-20"),
				row("100", "2026-12-20"));
		jdbc().update("update users set email_verified_at = now() where id = ?", verified);

		job.runOn(LocalDate.parse("2026-11-17"));

		// Both get the in-app notice. Only the verified address gets the email.
		assertThat(studentNotices(unverified, DUE_SOON)).isEqualTo(1);
		assertThat(studentNotices(verified, DUE_SOON)).isEqualTo(1);
		assertThat(mail(unverified, DUE_SOON)).isZero();
		assertThat(mail(verified, DUE_SOON)).isEqualTo(1);
	}

	@Test
	void aSuspendedStudentGetsNothingButTheMasterAdminIsStillTold() throws Exception {
		UUID masterId = seed("MASTER_ADMIN", "Suspended Master");
		String master = token(masterId);
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID student = seed("STUDENT", "Suspended Student");
		String plan = plan(master, system, student, "Suspended");
		jdbc().update("update users set status = 'INACTIVE' where id = ?", student);

		job.runOn(LocalDate.parse("2026-11-17"));

		assertThat(studentNotices(student, DUE_SOON)).isZero();
		assertThat(mail(student, DUE_SOON)).isZero();
		assertThat(staffNotices(masterId, DUE_SOON, plan)).isEqualTo(1);
	}

	/** A plan with 100 paid today and two installments still to come, for a student who is active and has no verified email. */
	private String plan(String master, String system, UUID student, String title) throws Exception {
		UUID module = module(system, title);
		return enrol(master, student, module, "300", "100", "TXN-" + title.hashCode(), row("100", TODAY),
				row("100", "2026-11-20"), row("100", "2026-12-20")).get("paymentPlanId").asText();
	}

	/** Student notices of one kind that belong to an installment (not to an exam). */
	private long studentNotices(UUID student, String kind) {
		Long count = jdbc().queryForObject(
				"select count(*) from student_notices where user_id = ? and kind = ? and installment_id is not null", Long.class,
				student, kind);
		return count == null ? 0 : count;
	}

	/** Staff notifications of one kind to one Master Admin, for one plan (the link names the plan). */
	private long staffNotices(UUID master, String kind, String planId) {
		Long count = jdbc().queryForObject(
				"select count(*) from staff_notifications where recipient_id = ? and kind = ? and link = ?", Long.class, master,
				kind, "/staff/installments/" + planId);
		return count == null ? 0 : count;
	}

	/** Email rows of one kind to one user. */
	private long mail(UUID user, String kind) {
		Long count = jdbc().queryForObject("select count(*) from exam_outbound_mail where user_id = ? and kind = ?", Long.class,
				user, kind);
		return count == null ? 0 : count;
	}

}
