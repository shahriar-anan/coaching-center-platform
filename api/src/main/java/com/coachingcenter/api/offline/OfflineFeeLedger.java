package com.coachingcenter.api.offline;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The reads and the shared writes of the monthly fee ledger (plan 4.3): the schedule, the payments, the open balance of each due,
 * the row lock that serialises every money change on one admission, the receipt numbers, and the allocation of a payment to dues.
 * The service, the reminder job and the mail renderer all read the ledger through here, so they agree on what is owed.
 */
@Component
class OfflineFeeLedger {

	/** A due as the schedule shows it: what it asks for and what approved payments have covered so far. */
	record DueRow(UUID id, int seq, LocalDate dueOn, BigDecimal amount, BigDecimal paid) {

		BigDecimal owed() {
			return amount.subtract(paid).max(BigDecimal.ZERO);
		}

	}

	/**
	 * One payment, with the student's name for the screens and the receipt. {@code name} is the same student name as {@code personName}
	 * (the approval queue reads it under that name too); {@code batchNameEn} is the admission's batch in English, or null.
	 */
	record Payment(UUID id, UUID admissionId, UUID studentId, String personName, BigDecimal amount, String method,
			String reference, LocalDate paidOn, String note, String status, String receiptNo, UUID recordedBy,
			String recordedByName, Instant recordedAt, Instant decidedAt, String decisionReason, Instant voidedAt,
			String voidReason, String name, @JsonInclude(JsonInclude.Include.ALWAYS) String batchNameEn) {
	}

	/** An admission as the fee code needs it: who it belongs to and where it stands. */
	record Admission(UUID id, UUID studentId, String status) {
	}

	private static final String PAYMENT_SELECT = """
			select p.id, p.admission_id, a.student_id, coalesce(a.full_name, sp.full_name, '') as person_name,
			  p.amount, p.method, p.reference, p.paid_on, p.note, p.status, p.receipt_no, p.recorded_by,
			  coalesce(ap.full_name, '') as recorded_by_name, p.recorded_at, p.decided_at, p.decision_reason,
			  p.voided_at, p.void_reason, b.name_en as batch_name_en
			from offline_fee_payments p
			join offline_admissions a on a.id = p.admission_id
			left join offline_batches b on b.id = a.batch_id
			left join student_profiles sp on sp.user_id = a.student_id
			left join admin_profiles ap on ap.user_id = p.recorded_by
			""";

	private static final String DUE_SELECT = """
			select d.id, d.seq, d.due_on, d.amount,
			  coalesce(sum(a.amount) filter (where p.status = 'APPROVED'), 0) as paid
			from offline_fee_dues d
			left join offline_fee_allocations a on a.due_id = d.id
			left join offline_fee_payments p on p.id = a.payment_id
			where d.admission_id = ? and d.cancelled_at is null
			group by d.id, d.seq, d.due_on, d.amount
			order by d.due_on, d.seq
			""";

	private final JdbcTemplate jdbc;

	private final Clock clock;

	OfflineFeeLedger(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/** Takes the admission's row lock. Every money change on one admission goes through here first, so they run one at a time. */
	Admission lock(UUID admissionId) {
		return find(admissionId, true).orElseThrow(OfflineFeeLedger::notFound);
	}

	Optional<Admission> find(UUID admissionId) {
		return find(admissionId, false);
	}

	private Optional<Admission> find(UUID admissionId, boolean forUpdate) {
		String sql = "select id, student_id, status from offline_admissions where id = ?" + (forUpdate ? " for update" : "");
		return jdbc.query(sql, (rs, row) -> admission(rs), admissionId).stream().findFirst();
	}

	/** The student's most recent admission, whatever its state: a closed one stays as history. */
	Optional<Admission> latestFor(UUID studentId) {
		return jdbc.query("""
				select id, student_id, status from offline_admissions where student_id = ?
				order by created_at desc, id desc limit 1
				""", (rs, row) -> admission(rs), studentId).stream().findFirst();
	}

	/** The admission's lock-free view of its owing dues, oldest first. Cancelled dues are left out. */
	List<DueRow> dues(UUID admissionId) {
		return jdbc.query(DUE_SELECT, (rs, row) -> new DueRow(rs.getObject("id", UUID.class), rs.getInt("seq"),
				rs.getDate("due_on").toLocalDate(), rs.getBigDecimal("amount"), rs.getBigDecimal("paid")), admissionId);
	}

	/** The dues that still owe money, oldest first: the order a payment is matched in (C7, D15). */
	List<OfflineFeeRules.Open> open(UUID admissionId) {
		List<OfflineFeeRules.Open> open = new ArrayList<>();
		for (DueRow due : dues(admissionId)) {
			if (due.owed().signum() > 0) {
				open.add(new OfflineFeeRules.Open(due.id(), due.dueOn(), due.seq(), due.owed()));
			}
		}
		return open;
	}

	/**
	 * What the admission still owes in all. After onboarding: across every open due. Before onboarding there are no dues yet, so it is
	 * the course fee (the instalment plan's total) less the approved payments (instalment-plan-and-early-payments.md, E3).
	 */
	BigDecimal owed(UUID admissionId) {
		String status = jdbc.queryForObject("select status from offline_admissions where id = ?", String.class, admissionId);
		if (OfflineFeeRules.BEFORE_ONBOARDING.contains(status)) {
			BigDecimal left = jdbc.queryForObject("""
					select coalesce(a.course_fee, 0) - coalesce((select sum(p.amount) from offline_fee_payments p
					  where p.admission_id = a.id and p.status = 'APPROVED'), 0)
					from offline_admissions a where a.id = ?
					""", BigDecimal.class, admissionId);
			return left == null ? BigDecimal.ZERO.setScale(2) : left.max(BigDecimal.ZERO).setScale(2);
		}
		return OfflineFeeRules.total(open(admissionId));
	}

	/**
	 * True when a payment may be taken before onboarding (E3): the form is in (submitted, sent back or ready) and the setup has a batch
	 * and an instalment plan with a total above zero.
	 */
	boolean payableBeforeOnboarding(UUID admissionId) {
		return Boolean.TRUE.equals(jdbc.queryForObject("""
				select status in ('SUBMITTED', 'CHANGES_REQUESTED', 'READY') and batch_id is not null and coalesce(course_fee, 0) > 0
				from offline_admissions where id = ?
				""", Boolean.class, admissionId));
	}

	/** The approved and pending payments of an admission, together: what a new plan total may not go below. */
	BigDecimal paidOrWaiting(UUID admissionId) {
		BigDecimal sum = jdbc.queryForObject("""
				select coalesce(sum(amount), 0) from offline_fee_payments where admission_id = ? and status in ('APPROVED', 'PENDING')
				""", BigDecimal.class, admissionId);
		return sum == null ? BigDecimal.ZERO : sum;
	}

	/**
	 * Matches the approved payments taken before onboarding to the dues the onboarding has just written, in the order they were paid,
	 * as if each had been recorded after onboarding (E3). A payment already matched is left alone.
	 */
	void matchEarlyPayments(UUID admissionId) {
		List<Object[]> early = jdbc.query("""
				select p.id, p.amount from offline_fee_payments p
				where p.admission_id = ? and p.status = 'APPROVED'
				  and not exists (select 1 from offline_fee_allocations al where al.payment_id = p.id)
				order by p.paid_on, p.recorded_at, p.id
				""", (rs, row) -> new Object[] { rs.getObject("id", UUID.class), rs.getBigDecimal("amount") }, admissionId);
		for (Object[] payment : early) {
			match((UUID) payment[0], (BigDecimal) payment[1], open(admissionId));
		}
	}

	/** Each due that is past its date and still owes money, with what it owes. */
	BigDecimal overdue(UUID admissionId, LocalDate today) {
		BigDecimal total = BigDecimal.ZERO;
		for (DueRow due : dues(admissionId)) {
			if (due.dueOn().isBefore(today) && due.owed().signum() > 0) {
				total = total.add(due.owed());
			}
		}
		return total;
	}

	Optional<Payment> payment(UUID paymentId) {
		return jdbc.query(PAYMENT_SELECT + " where p.id = ?", (rs, row) -> payment(rs), paymentId).stream().findFirst();
	}

	/** The payments of an admission, newest first, optionally only those with one status. */
	List<Payment> payments(UUID admissionId, String status) {
		if (status == null) {
			return jdbc.query(PAYMENT_SELECT + " where p.admission_id = ? order by p.recorded_at desc, p.id", (rs, row) -> payment(rs),
					admissionId);
		}
		return jdbc.query(PAYMENT_SELECT + " where p.admission_id = ? and p.status = ? order by p.recorded_at desc, p.id",
				(rs, row) -> payment(rs), admissionId, status);
	}

	/** The payments waiting in the approval queue, or every payment with a status, filtered by who recorded it when asked. */
	List<Payment> queue(String status, UUID recordedBy) {
		String sql = PAYMENT_SELECT + " where (cast(? as varchar) is null or p.status = ?)"
				+ " and (cast(? as uuid) is null or p.recorded_by = ?)"
				+ " order by p.recorded_at desc, p.id limit 500";
		return jdbc.query(sql, (rs, row) -> payment(rs), status, status, recordedBy, recordedBy);
	}

	/** The student's approved payments only (the student's own list never shows a pending or a voided one). */
	List<Payment> approvedFor(UUID admissionId) {
		return payments(admissionId, OfflineFeePayment.STATUS_APPROVED);
	}

	/** Writes one payment row. Its receipt number is set only when it is approved at once or later. */
	void insertPayment(UUID id, UUID admissionId, BigDecimal amount, String method, String reference, LocalDate paidOn, String note,
			String status, UUID recordedBy, Instant recordedAt, UUID decidedBy, Instant decidedAt, String receiptNo) {
		jdbc.update("""
				insert into offline_fee_payments (id, admission_id, amount, method, reference, paid_on, note, status, recorded_by,
				  recorded_at, decided_by, decided_at, receipt_no, version)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
				""", id, admissionId, amount, method, reference, java.sql.Date.valueOf(paidOn), note, status, recordedBy,
				Timestamp.from(recordedAt), decidedBy, decidedAt == null ? null : Timestamp.from(decidedAt), receiptNo);
	}

	/** Records the approval of a pending payment: who decided it, when, and its receipt number. */
	void approve(UUID paymentId, UUID decidedBy, Instant decidedAt, String receiptNo) {
		jdbc.update("""
				update offline_fee_payments set status = 'APPROVED', decided_by = ?, decided_at = ?, receipt_no = ?, version = version + 1
				where id = ? and status = 'PENDING'
				""", decidedBy, Timestamp.from(decidedAt), receiptNo, paymentId);
	}

	void reject(UUID paymentId, UUID decidedBy, Instant decidedAt, String reason) {
		jdbc.update("""
				update offline_fee_payments set status = 'REJECTED', decided_by = ?, decided_at = ?, decision_reason = ?,
				  version = version + 1
				where id = ? and status = 'PENDING'
				""", decidedBy, Timestamp.from(decidedAt), reason, paymentId);
	}

	void voidPayment(UUID paymentId, UUID voidedBy, Instant voidedAt, String reason) {
		jdbc.update("delete from offline_fee_allocations where payment_id = ?", paymentId);
		jdbc.update("""
				update offline_fee_payments set status = 'VOIDED', voided_by = ?, voided_at = ?, void_reason = ?, version = version + 1
				where id = ? and status = 'APPROVED'
				""", voidedBy, Timestamp.from(voidedAt), reason, paymentId);
	}

	/**
	 * Applies an approved payment to the open dues, oldest first, and writes one allocation row per due it touches (C7, D15).
	 * Refused when the shares do not add up to the amount, which can only happen when the amount is more than is owed.
	 */
	void match(UUID paymentId, BigDecimal amount, List<OfflineFeeRules.Open> open) {
		List<OfflineFeeRules.Share> shares = OfflineFeeRules.allocate(amount, open);
		BigDecimal covered = BigDecimal.ZERO;
		for (OfflineFeeRules.Share share : shares) {
			covered = covered.add(share.amount());
			jdbc.update("insert into offline_fee_allocations (id, payment_id, due_id, amount) values (?, ?, ?, ?)",
					UUID.randomUUID(), paymentId, share.dueId(), share.amount());
		}
		if (covered.compareTo(amount) != 0) {
			throw new ApiException(ErrorCode.ADMISSION_STATE, HttpStatus.CONFLICT,
					"The payment is more than the amount still owed.");
		}
	}

	/** The next receipt number from the sequence the direct payments use (V52), so no two receipts share a number. */
	String nextReceipt(LocalDate paidOn) {
		Long number = jdbc.queryForObject("select nextval('live_exam_receipt_seq')", Long.class);
		return "RCPT-" + paidOn.getYear() + "-" + String.format(Locale.ROOT, "%06d", number == null ? 0L : number);
	}

	LocalDate today() {
		return LocalDate.now(clock.withZone(OfflineFeeRules.DHAKA));
	}

	/** The student's active name for the screens: the form's name, else the profile's. */
	String personName(UUID admissionId) {
		return jdbc.queryForList("""
				select coalesce(a.full_name, sp.full_name, '') from offline_admissions a
				left join student_profiles sp on sp.user_id = a.student_id where a.id = ?
				""", String.class, admissionId).stream().findFirst().orElse("");
	}

	/** The batch name in English, for the receipt. Null when the admission has no batch yet. */
	String batchName(UUID admissionId) {
		return jdbc.queryForList("""
				select b.name_en from offline_admissions a join offline_batches b on b.id = a.batch_id where a.id = ?
				""", String.class, admissionId).stream().findFirst().orElse(null);
	}

	private static Admission admission(ResultSet rs) throws SQLException {
		return new Admission(rs.getObject("id", UUID.class), rs.getObject("student_id", UUID.class), rs.getString("status"));
	}

	private static Payment payment(ResultSet rs) throws SQLException {
		return new Payment(rs.getObject("id", UUID.class), rs.getObject("admission_id", UUID.class),
				rs.getObject("student_id", UUID.class), rs.getString("person_name"), rs.getBigDecimal("amount"),
				rs.getString("method"), rs.getString("reference"), rs.getDate("paid_on").toLocalDate(), rs.getString("note"),
				rs.getString("status"), rs.getString("receipt_no"), rs.getObject("recorded_by", UUID.class),
				rs.getString("recorded_by_name"), instant(rs, "recorded_at"), instant(rs, "decided_at"),
				rs.getString("decision_reason"), instant(rs, "voided_at"), rs.getString("void_reason"),
				rs.getString("person_name"), rs.getString("batch_name_en"));
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		Timestamp value = rs.getTimestamp(column);
		return value == null ? null : value.toInstant();
	}

	static ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	static ApiException state(String message) {
		return new ApiException(ErrorCode.ADMISSION_STATE, HttpStatus.CONFLICT, message);
	}

}
