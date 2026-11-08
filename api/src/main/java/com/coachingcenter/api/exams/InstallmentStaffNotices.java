package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.coachingcenter.api.staffnotifications.StaffNotificationKind;
import com.coachingcenter.api.staffnotifications.StaffNotificationWriter;

/**
 * The Master Admin's staff notifications about direct-enrolment payments (plan sections 7 and 9.1).
 *
 * <p>
 * A recorded payment is announced after it commits, in its own transaction, and a failure there is logged and never reaches the
 * payment. {@link StaffNotificationWriter#notify} refuses a bad payload by throwing, and a throw inside the payment's own
 * transaction would mark it for rollback, so the payment path never calls it inside that transaction. A reminder is written by
 * the reminder job, one transaction per plan, from values that have already been checked.
 *
 * <p>
 * Payloads hold the student's name, the item's titles, amounts and dates. Never a reference, a phone number or an account.
 */
@Component
class InstallmentStaffNotices {

	/** The page a notification opens: the plan's detail on the staff site. */
	static final String LINK = "/staff/installments/";

	private static final Logger log = LoggerFactory.getLogger(InstallmentStaffNotices.class);

	private final JdbcTemplate jdbc;

	private final StaffNotificationWriter writer;

	private final InstallmentPlanReader reader;

	private final AfterCommit afterCommit;

	InstallmentStaffNotices(JdbcTemplate jdbc, StaffNotificationWriter writer, InstallmentPlanReader reader,
			AfterCommit afterCommit) {
		this.jdbc = jdbc;
		this.writer = writer;
		this.reader = reader;
		this.afterCommit = afterCommit;
	}

	/**
	 * A payment was recorded, the first one of a plan included. The notification is written after the caller's transaction
	 * commits. Any failure is logged, and the payment stands.
	 */
	void paymentRecorded(UUID planId, UUID paymentId) {
		afterCommit.run(() -> {
			try {
				InstallmentPlanReader.PaymentReceipt receipt = reader.receipt(paymentId, null).orElse(null);
				if (receipt == null) {
					return;
				}
				Map<String, Object> values = payload(receipt.studentName(), receipt.itemTitleEn(), receipt.itemTitleBn(),
						receipt.amount(), receipt.nextDueOn(), receipt.dueAfter());
				for (UUID master : masters()) {
					writer.notify(master, StaffNotificationKind.INSTALLMENT_PAYMENT_RECORDED, values, LINK + planId,
							"INSTALLMENT_PAYMENT_RECORDED:" + master + ":" + paymentId);
				}
			}
			catch (RuntimeException exception) {
				log.warn("installment payment notice not written paymentId={} error={}", paymentId,
						exception.getClass().getSimpleName());
			}
		});
	}

	/**
	 * A reminder for the oldest unpaid installment of a plan. Joins the reminder job's transaction for that plan. The key holds the
	 * recipient, the installment and its date, and for overdue the week, so a repeat run adds nothing.
	 */
	void reminder(StaffNotificationKind kind, InstallmentPlanReader.PlanRow plan, UUID installmentId, LocalDate dueOn,
			String week, BigDecimal uncovered) {
		Map<String, Object> values = payload(plan.studentName(), plan.itemTitleEn(), plan.itemTitleBn(), uncovered, dueOn,
				plan.due());
		for (UUID master : masters()) {
			writer.notify(master, kind, values, LINK + plan.planId(),
					kind.name() + ":" + master + ":" + installmentId + ":" + dueOn + (week == null ? "" : ":" + week));
		}
	}

	/**
	 * The payload of a payment or reminder notification. Names and titles go through {@code fitText}, so a long run of digits
	 * or an over-long title cannot make the notify call refuse the row. A null title stays null (a {@code Map.of} would throw).
	 */
	static Map<String, Object> payload(String studentName, String titleEn, String titleBn, BigDecimal amount, LocalDate dueOn,
			BigDecimal due) {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("studentName", StaffNotificationWriter.fitText(studentName));
		values.put("titleEn", StaffNotificationWriter.fitText(titleEn));
		values.put("titleBn", StaffNotificationWriter.fitText(titleBn));
		values.put("amount", amount);
		values.put("dueOn", dueOn == null ? null : dueOn.toString());
		values.put("due", due);
		return values;
	}

	/** The live Master Admins. The writer skips any that is not active. */
	private List<UUID> masters() {
		return jdbc.queryForList("select id from users where role = 'MASTER_ADMIN' and deleted_at is null", UUID.class);
	}

}
