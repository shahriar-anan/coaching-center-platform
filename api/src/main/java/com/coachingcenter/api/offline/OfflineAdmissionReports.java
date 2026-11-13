package com.coachingcenter.api.offline;

import static com.coachingcenter.api.offline.OfflineSupport.notFound;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.web.PageResponse;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The read-only lists the admin pages use for offline admissions (plan 5, 6.1, 6.3): the counts for the tabs and the dashboard, the
 * overdue list of the fee page, and an admission's history. Money is judged by {@link OfflineAdmissionMoney}, so these lists and the
 * queue flags never disagree.
 */
@Service
public class OfflineAdmissionReports {

	/** The most history events one admission answers with, newest first. */
	static final int HISTORY_LIMIT = 200;

	private static final int MAX_PAGE_SIZE = 100;

	/** A name looked up for a user id: the staff profile, else the student profile. Null when neither exists. */
	private static final String NAME_OF = "(select coalesce(ap.full_name, sp.full_name) from users u"
			+ " left join admin_profiles ap on ap.user_id = u.id left join student_profiles sp on sp.user_id = u.id where u.id = %s)";

	private final JdbcTemplate jdbc;

	private final OfflineAdmissionMoney money;

	public OfflineAdmissionReports(JdbcTemplate jdbc, OfflineAdmissionMoney money) {
		this.jdbc = jdbc;
		this.money = money;
	}

	/**
	 * How many admissions are in each state the tabs show, how many payments wait for approval, and how many admissions have an overdue
	 * unpaid due (plan 5, 6.1).
	 */
	public record Counts(long submitted, long changesRequested, long ready, long onboarded, long closed, long paymentsPending,
			long overdue) {
	}

	/**
	 * One admission's oldest overdue unpaid due. {@code amount} is what is still owed on that due; {@code owedTotal} is all of it.
	 * {@code batchNameEn} is null when the admission has no batch; it is sent as null, not left out.
	 */
	public record OverdueRow(UUID admissionId, UUID studentId, String name, @JsonInclude(JsonInclude.Include.ALWAYS) String batchNameEn,
			LocalDate dueOn, int seq, BigDecimal amount, long daysLate, BigDecimal owedTotal) {
	}

	/**
	 * One line of an admission's history. {@code actorName} is null when the actor's name is not known, and {@code detail} when the event
	 * has no detail. Both are sent as null, not left out.
	 */
	public record HistoryRow(Instant at, String action, @JsonInclude(JsonInclude.Include.ALWAYS) String actorName,
			@JsonInclude(JsonInclude.Include.ALWAYS) String detail) {
	}

	@Transactional(readOnly = true)
	public Counts counts() {
		return jdbc.queryForObject("""
				select
				  (select count(*) from offline_admissions where status = 'SUBMITTED') as submitted,
				  (select count(*) from offline_admissions where status = 'CHANGES_REQUESTED') as changes_requested,
				  (select count(*) from offline_admissions where status = 'READY') as ready,
				  (select count(*) from offline_admissions where status = 'ONBOARDED') as onboarded,
				  (select count(*) from offline_admissions where status = 'CLOSED') as closed,
				  (select count(*) from offline_fee_payments where status = 'PENDING') as payments_pending,
				  (select count(*) from offline_admissions a where """ + " " + OfflineAdmissionMoney.unpaidDue(true) + ") as overdue",
				(rs, row) -> new Counts(rs.getLong("submitted"), rs.getLong("changes_requested"), rs.getLong("ready"),
						rs.getLong("onboarded"), rs.getLong("closed"), rs.getLong("payments_pending"), rs.getLong("overdue")),
				sqlDate(money.today()));
	}

	/**
	 * The admissions with an overdue unpaid due, one row each, with the oldest such due, sorted by days late, most first (plan 6.3).
	 */
	@Transactional(readOnly = true)
	public PageResponse<OverdueRow> overdue(int page, int size) {
		LocalDate today = money.today();
		int pageSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
		int pageNumber = Math.max(0, page);
		Long total = jdbc.queryForObject("select count(distinct x.admission_id) from (" + OfflineAdmissionMoney.DUE_OWED
				+ ") x where x.owed > 0 and x.due_on < ?", Long.class, sqlDate(today));
		long count = total == null ? 0L : total;
		List<OverdueRow> rows = jdbc.query("""
				with overdue as (
				  select distinct on (x.admission_id) x.admission_id, x.seq, x.due_on, x.owed
				  from (""" + OfflineAdmissionMoney.DUE_OWED + """
				  ) x
				  where x.owed > 0 and x.due_on < ?
				  order by x.admission_id, x.due_on, x.seq
				), owed_total as (
				  select x.admission_id, sum(x.owed) as owed_total
				  from (""" + OfflineAdmissionMoney.DUE_OWED + """
				  ) x
				  where x.owed > 0
				  group by x.admission_id
				)
				select o.admission_id, a.student_id, a.full_name, b.name_en, o.due_on, o.seq, o.owed,
				  (cast(? as date) - o.due_on) as days_late, t.owed_total
				from overdue o
				join offline_admissions a on a.id = o.admission_id
				left join offline_batches b on b.id = a.batch_id
				join owed_total t on t.admission_id = o.admission_id
				order by days_late desc, o.admission_id
				limit ? offset ?
				""", (rs, row) -> new OverdueRow(rs.getObject("admission_id", UUID.class), rs.getObject("student_id", UUID.class),
				rs.getString("full_name"), rs.getString("name_en"), rs.getDate("due_on").toLocalDate(), rs.getInt("seq"),
				rs.getBigDecimal("owed"), rs.getLong("days_late"), rs.getBigDecimal("owed_total")),
				sqlDate(today), sqlDate(today), pageSize, (long) pageNumber * pageSize);
		int totalPages = (int) ((count + pageSize - 1) / pageSize);
		return new PageResponse<>(rows, pageNumber, pageSize, count, totalPages);
	}

	/**
	 * The history of one admission, newest first, at most {@link #HISTORY_LIMIT} events (plan 6.3). It is built from three queries: the
	 * audit log's rows for the admission (the submission and return rows are left out, since the submissions give them with their reason),
	 * the admission's payments (recorded, approved or rejected, and voided), and its submissions (submitted, and returned with the reason).
	 */
	@Transactional(readOnly = true)
	public List<HistoryRow> history(UUID admissionId) {
		Long exists = jdbc.queryForObject("select count(*) from offline_admissions where id = ?", Long.class, admissionId);
		if (exists == null || exists == 0L) {
			throw notFound();
		}
		List<HistoryRow> events = new ArrayList<>(auditEvents(admissionId));
		events.addAll(paymentEvents(admissionId));
		events.addAll(submissionEvents(admissionId));
		events.sort(Comparator.comparing(HistoryRow::at).reversed());
		return events.size() > HISTORY_LIMIT ? new ArrayList<>(events.subList(0, HISTORY_LIMIT)) : events;
	}

	private List<HistoryRow> auditEvents(UUID admissionId) {
		return jdbc.query("""
				select l.action, l.created_at, coalesce(ap.full_name, sp.full_name) as actor_name
				from audit_log l
				left join admin_profiles ap on ap.user_id = l.actor_user_id
				left join student_profiles sp on sp.user_id = l.actor_user_id
				where l.entity_type = 'OFFLINE_ADMISSION' and l.entity_id = ?
				  and l.action not in ('OFFLINE_ADMISSION_SUBMITTED', 'OFFLINE_ADMISSION_RESUBMITTED', 'OFFLINE_ADMISSION_CHANGES_REQUESTED')
				order by l.created_at desc
				limit ?
				""", (rs, row) -> new HistoryRow(instant(rs.getTimestamp("created_at")), rs.getString("action"),
				rs.getString("actor_name"), null), admissionId, HISTORY_LIMIT);
	}

	private List<HistoryRow> paymentEvents(UUID admissionId) {
		List<HistoryRow> events = new ArrayList<>();
		List<PaymentRow> payments = jdbc.query("""
				select p.amount, p.method, p.reference, p.status, p.receipt_no, p.recorded_at, p.decided_at, p.decision_reason,
				  p.voided_at, p.void_reason, %s as recorded_name, %s as decided_name, %s as voided_name
				from offline_fee_payments p
				where p.admission_id = ?
				order by coalesce(p.voided_at, p.decided_at, p.recorded_at) desc
				limit ?
				""".formatted(NAME_OF.formatted("p.recorded_by"), NAME_OF.formatted("p.decided_by"), NAME_OF.formatted("p.voided_by")),
				(rs, row) -> new PaymentRow(rs.getBigDecimal("amount"), rs.getString("method"), rs.getString("reference"),
						rs.getString("status"), rs.getString("receipt_no"), instant(rs.getTimestamp("recorded_at")),
						instant(rs.getTimestamp("decided_at")), rs.getString("decision_reason"),
						instant(rs.getTimestamp("voided_at")), rs.getString("void_reason"), rs.getString("recorded_name"),
						rs.getString("decided_name"), rs.getString("voided_name")),
				admissionId, HISTORY_LIMIT);
		for (PaymentRow payment : payments) {
			String amount = "BDT " + payment.amount().toPlainString();
			events.add(new HistoryRow(payment.recordedAt(), "OFFLINE_FEE_PAYMENT_RECORDED", payment.recordedName(),
					amount + " " + payment.method() + " " + payment.reference()));
			if ("APPROVED".equals(payment.status()) || "VOIDED".equals(payment.status())) {
				if (payment.decidedAt() != null) {
					events.add(new HistoryRow(payment.decidedAt(), "OFFLINE_FEE_PAYMENT_APPROVED", payment.decidedName(),
							amount + (payment.receiptNo() == null ? "" : " " + payment.receiptNo())));
				}
			}
			else if ("REJECTED".equals(payment.status()) && payment.decidedAt() != null) {
				events.add(new HistoryRow(payment.decidedAt(), "OFFLINE_FEE_PAYMENT_REJECTED", payment.decidedName(),
						payment.decisionReason()));
			}
			if (payment.voidedAt() != null) {
				events.add(new HistoryRow(payment.voidedAt(), "OFFLINE_FEE_PAYMENT_VOIDED", payment.voidedName(),
						payment.voidReason()));
			}
		}
		return events;
	}

	private List<HistoryRow> submissionEvents(UUID admissionId) {
		List<HistoryRow> events = new ArrayList<>();
		List<SubmissionRow> submissions = jdbc.query("""
				select s.submitted_at, %s as submitted_name, s.returned_reason, s.returned_at, %s as returned_name,
				  row_number() over (order by s.submitted_at, s.id) as submission_no
				from offline_admission_submissions s
				where s.admission_id = ?
				order by s.submitted_at desc
				limit ?
				""".formatted(NAME_OF.formatted("s.submitted_by"), NAME_OF.formatted("s.returned_by")),
				(rs, row) -> new SubmissionRow(instant(rs.getTimestamp("submitted_at")), rs.getString("submitted_name"),
						rs.getString("returned_reason"), instant(rs.getTimestamp("returned_at")), rs.getString("returned_name"),
						rs.getLong("submission_no")),
				admissionId, HISTORY_LIMIT);
		for (SubmissionRow submission : submissions) {
			events.add(new HistoryRow(submission.submittedAt(),
					submission.number() == 1 ? "OFFLINE_ADMISSION_SUBMITTED" : "OFFLINE_ADMISSION_RESUBMITTED",
					submission.submittedName(), null));
			if (submission.returnedAt() != null) {
				events.add(new HistoryRow(submission.returnedAt(), "OFFLINE_ADMISSION_CHANGES_REQUESTED", submission.returnedName(),
						submission.returnedReason()));
			}
		}
		return events;
	}

	/** One payment as the history reads it: its amounts and decisions, with the names of who recorded, decided and voided it. */
	private record PaymentRow(BigDecimal amount, String method, String reference, String status, String receiptNo, Instant recordedAt,
			Instant decidedAt, String decisionReason, Instant voidedAt, String voidReason, String recordedName, String decidedName,
			String voidedName) {
	}

	/** One submission of the form, with its return if staff sent it back. {@code number} is 1 for the first submission. */
	private record SubmissionRow(Instant submittedAt, String submittedName, String returnedReason, Instant returnedAt,
			String returnedName, long number) {
	}

	private static java.sql.Date sqlDate(LocalDate day) {
		return java.sql.Date.valueOf(day);
	}

	private static Instant instant(Timestamp value) {
		return value == null ? null : value.toInstant();
	}

}
