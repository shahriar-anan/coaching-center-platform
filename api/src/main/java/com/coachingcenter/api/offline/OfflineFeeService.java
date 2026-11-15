package com.coachingcenter.api.offline;

import static com.coachingcenter.api.offline.OfflineFeeLedger.notFound;
import static com.coachingcenter.api.offline.OfflineFeeLedger.state;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.error.FieldErrorDetail;
import com.coachingcenter.api.offline.OfflineFeeLedger.Admission;
import com.coachingcenter.api.offline.OfflineFeeLedger.Payment;
import com.coachingcenter.api.staffnotifications.StaffNotificationKind;
import com.coachingcenter.api.staffnotifications.StaffNotificationWriter;

/**
 * Monthly fee payments of an offline admission (plan sections 4.3, 5 and 6.1): recording, approval, rejection and voiding, and the
 * schedules and receipts the screens read. Every change first takes the admission's row lock, so two changes on one admission run
 * one after the other, and the second is judged on the balance the first left. A staff payment waits as PENDING and counts for
 * nothing until the Master Admin approves it (D15); a Master Admin's payment is approved at once.
 */
@Service
public class OfflineFeeService {

	private static final String ONBOARDED = OfflineAdmission.ONBOARDED;

	private final OfflineFeeLedger ledger;

	private final OfflineFeeNotices notices;

	private final PermissionChecker permissions;

	private final AuditRecorder audit;

	private final JdbcTemplate jdbc;

	private final Clock clock;

	public OfflineFeeService(OfflineFeeLedger ledger, OfflineFeeNotices notices, PermissionChecker permissions,
			AuditRecorder audit, JdbcTemplate jdbc, Clock clock) {
		this.ledger = ledger;
		this.notices = notices;
		this.permissions = permissions;
		this.audit = audit;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/** A payment as the desk enters it. {@code paidOn} is the Dhaka date the money came in. */
	public record RecordRequest(BigDecimal amount, String method, String reference, LocalDate paidOn, String note) {
	}

	/** The reason for a rejection or a void, required. */
	public record ReasonRequest(String reason) {
	}

	/** One due on the schedule: what it asks, what is paid, what is owed, and whether it is past its date unpaid. */
	public record DueView(UUID id, int seq, LocalDate dueOn, BigDecimal amount, BigDecimal paid, BigDecimal owed, boolean overdue,
			String state) {
	}

	/** The staff view of an admission's fees: the schedule, every payment (whatever its status), and the totals. */
	public record Schedule(UUID admissionId, String admissionStatus, String personName, BigDecimal owed, BigDecimal overdue,
			List<DueView> dues, List<Payment> payments) {
	}

	/** A payment as the student sees it: only the approved ones, with no staff details. */
	public record StudentPayment(UUID id, BigDecimal amount, String method, String reference, LocalDate paidOn, String receiptNo) {
	}

	/** The student's own schedule: the same dues, and the approved payments only. */
	public record StudentSchedule(UUID admissionId, String admissionStatus, BigDecimal owed, BigDecimal overdue, List<DueView> dues,
			List<StudentPayment> payments) {
	}

	/** One due that a receipt's payment covered. */
	public record ReceiptLine(int seq, LocalDate dueOn, BigDecimal amount) {
	}

	/** Everything a receipt shows: the payment, its batch, the dues it covered, and what the admission still owes. */
	public record Receipt(Payment payment, String batchName, List<ReceiptLine> lines, BigDecimal owedNow) {
	}

	/** A payment with its admission, read under the admission's lock. */
	private record Locked(Payment payment, Admission admission) {
	}

	/**
	 * Records a payment (plan 4.3, D15, C12). Refused unless the admission is onboarded, the amount is more than zero, and the amount
	 * is no more than what is still owed. A Master Admin's payment is approved at once; a staff payment waits as PENDING, and every
	 * Master Admin is told.
	 */
	@Transactional
	public Payment record(CurrentActor actor, UUID admissionId, RecordRequest request) {
		String method = request.method() == null ? "" : request.method().trim().toUpperCase(Locale.ROOT);
		String reference = request.reference() == null ? "" : request.reference().trim();
		String note = OfflineFeeRules.blankToNull(request.note());
		List<FieldErrorDetail> errors = new ArrayList<>();
		if (!OfflineFeeRules.METHODS.contains(method)) {
			errors.add(new FieldErrorDetail("method", "Choose a payment method."));
		}
		if (!OfflineFeeRules.REFERENCE.matcher(reference).matches()) {
			errors.add(new FieldErrorDetail("reference", "A reference is required: 1 to 100 printable characters, no accents."));
		}
		if (note != null && note.length() > OfflineFeeRules.MAX_TEXT) {
			errors.add(new FieldErrorDetail("note", "The note is at most 500 characters."));
		}
		if (request.amount() == null || request.amount().signum() <= 0 || request.amount().stripTrailingZeros().scale() > 2) {
			errors.add(new FieldErrorDetail("amount", "The amount must be more than zero, with at most two decimals."));
		}
		if (request.paidOn() == null || request.paidOn().isAfter(ledger.today())) {
			errors.add(new FieldErrorDetail("paidOn", "The payment date cannot be in the future."));
		}
		throwIfAny(errors);
		BigDecimal amount = OfflineFeeRules.money(request.amount());
		LocalDate paidOn = request.paidOn();

		Admission admission = ledger.lock(admissionId);
		if (!ONBOARDED.equals(admission.status())) {
			throw state("Payments can be recorded only once the admission is onboarded.");
		}
		BigDecimal owed = ledger.owed(admissionId);
		if (amount.compareTo(owed) > 0) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
					"The amount is more than the BDT " + owed.toPlainString() + " still owed.",
					List.of(new FieldErrorDetail("amount", "The most that can be taken is BDT " + owed.toPlainString() + ".")));
		}

		boolean approvedAtOnce = permissions.has(Permissions.OFFLINE_FEE_APPROVE);
		UUID paymentId = UUID.randomUUID();
		Instant now = clock.instant();
		String receiptNo = approvedAtOnce ? ledger.nextReceipt(paidOn) : null;
		ledger.insertPayment(paymentId, admissionId, amount, method, reference, paidOn, note,
				approvedAtOnce ? OfflineFeePayment.STATUS_APPROVED : OfflineFeePayment.STATUS_PENDING, actor.userId(), now,
				approvedAtOnce ? actor.userId() : null, approvedAtOnce ? now : null, receiptNo);
		if (approvedAtOnce) {
			settle(paymentId, admissionId, amount, admission.studentId());
		}
		else {
			notices.staff(StaffNotificationKind.OFFLINE_PAYMENT_PENDING, notices.masterAdmins(),
					payload(ledger.personName(admissionId), amount, method, null), OfflineFeeRules.FEES_PAGE,
					"OFFLINE_PAYMENT_PENDING:" + paymentId);
		}
		audit.record(OfflineFeeRules.AUDIT_RECORDED, OfflineFeeRules.ENTITY_PAYMENT, paymentId);
		return ledger.payment(paymentId).orElseThrow(OfflineFeeLedger::notFound);
	}

	/**
	 * Approves a pending payment (Master Admin only): it gets its receipt number and is matched to the dues, the student is told,
	 * and the person who recorded it is told it was approved.
	 */
	@Transactional
	public Payment approve(CurrentActor actor, UUID paymentId) {
		Locked locked = locked(paymentId);
		Payment payment = locked.payment();
		if (!OfflineFeePayment.STATUS_PENDING.equals(payment.status())) {
			throw state("Only a pending payment can be approved. This one is " + payment.status().toLowerCase(Locale.ROOT) + ".");
		}
		if (!ONBOARDED.equals(locked.admission().status())) {
			throw state("Payments can be approved only once the admission is onboarded.");
		}
		BigDecimal owed = ledger.owed(payment.admissionId());
		if (payment.amount().compareTo(owed) > 0) {
			throw state("Only BDT " + owed.toPlainString() + " is still owed, so this payment is more than the balance. "
					+ "Reject it, and record what is left.");
		}
		String receiptNo = ledger.nextReceipt(payment.paidOn());
		ledger.approve(paymentId, actor.userId(), clock.instant(), receiptNo);
		settle(paymentId, payment.admissionId(), payment.amount(), payment.studentId());
		notices.staff(StaffNotificationKind.OFFLINE_PAYMENT_APPROVED, List.of(payment.recordedBy()),
				payload(payment.personName(), payment.amount(), payment.method(), null), OfflineFeeRules.FEES_PAGE,
				"OFFLINE_PAYMENT_APPROVED:" + paymentId);
		audit.record(OfflineFeeRules.AUDIT_APPROVED, OfflineFeeRules.ENTITY_PAYMENT, paymentId);
		return ledger.payment(paymentId).orElseThrow(OfflineFeeLedger::notFound);
	}

	/** Rejects a pending payment, with a reason. It is never matched, and the person who recorded it is told. */
	@Transactional
	public Payment reject(CurrentActor actor, UUID paymentId, String reason) {
		String text = reasonText(reason);
		Locked locked = locked(paymentId);
		Payment payment = locked.payment();
		if (!OfflineFeePayment.STATUS_PENDING.equals(payment.status())) {
			throw state("Only a pending payment can be rejected. This one is " + payment.status().toLowerCase(Locale.ROOT) + ".");
		}
		ledger.reject(paymentId, actor.userId(), clock.instant(), text);
		notices.staff(StaffNotificationKind.OFFLINE_PAYMENT_REJECTED, List.of(payment.recordedBy()),
				payload(payment.personName(), payment.amount(), payment.method(), StaffNotificationWriter.fitText(text)),
				OfflineFeeRules.FEES_PAGE, "OFFLINE_PAYMENT_REJECTED:" + paymentId);
		audit.record(OfflineFeeRules.AUDIT_REJECTED, OfflineFeeRules.ENTITY_PAYMENT, paymentId);
		return ledger.payment(paymentId).orElseThrow(OfflineFeeLedger::notFound);
	}

	/**
	 * Voids an approved payment (Master Admin only). Its matching is removed, so the dues it covered are due again. The receipt
	 * number is never reused, and the student is told.
	 */
	@Transactional
	public Payment voidPayment(CurrentActor actor, UUID paymentId, String reason) {
		String text = reasonText(reason);
		Locked locked = locked(paymentId);
		Payment payment = locked.payment();
		if (!OfflineFeePayment.STATUS_APPROVED.equals(payment.status())) {
			throw state("Only an approved payment can be voided. This one is " + payment.status().toLowerCase(Locale.ROOT) + ".");
		}
		ledger.voidPayment(paymentId, actor.userId(), clock.instant(), text);
		notices.studentNotice(OfflineFeeNotices.VOIDED, paymentId, payment.studentId());
		audit.record(OfflineFeeRules.AUDIT_VOIDED, OfflineFeeRules.ENTITY_PAYMENT, paymentId);
		return ledger.payment(paymentId).orElseThrow(OfflineFeeLedger::notFound);
	}

	/**
	 * The payments for a queue. The Master Admin sees every payment with the status asked for (the approval queue); a staff member
	 * sees only the payments they recorded. Anyone else is refused.
	 */
	@Transactional(readOnly = true)
	public List<Payment> queue(CurrentActor actor, String status) {
		String filter = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
		if (filter != null && !STATUSES.contains(filter)) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.",
					List.of(new FieldErrorDetail("status", "Use PENDING, APPROVED, REJECTED or VOIDED.")));
		}
		if (permissions.has(Permissions.OFFLINE_FEE_APPROVE)) {
			return ledger.queue(filter, null);
		}
		if (permissions.has(Permissions.OFFLINE_FEE_RECORD)) {
			return ledger.queue(filter, actor.userId());
		}
		throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
	}

	/** The staff view of an admission's fees: its schedule and every payment on it. */
	@Transactional(readOnly = true)
	public Schedule schedule(UUID admissionId) {
		Admission admission = ledger.find(admissionId).orElseThrow(OfflineFeeLedger::notFound);
		LocalDate today = ledger.today();
		List<DueView> dues = dueViews(admissionId, today);
		return new Schedule(admission.id(), admission.status(), ledger.personName(admissionId), total(dues), overdue(dues),
				dues, ledger.payments(admissionId, null));
	}

	/**
	 * The student's own schedule (plan 5). A student with no admission gets 404. Before onboarding there is no schedule, so the answer
	 * is a state conflict. Only approved payments are listed.
	 */
	@Transactional(readOnly = true)
	public StudentSchedule ownSchedule(UUID studentId) {
		return studentSchedule(ledger.latestFor(studentId).orElseThrow(OfflineFeeLedger::notFound));
	}

	/** The schedule of one of the student's own admissions (V94: a student may hold several). Another student's gets 404. */
	@Transactional(readOnly = true)
	public StudentSchedule ownSchedule(UUID studentId, UUID admissionId) {
		Admission admission = ledger.find(admissionId).filter(found -> studentId.equals(found.studentId()))
			.orElseThrow(OfflineFeeLedger::notFound);
		return studentSchedule(admission);
	}

	private StudentSchedule studentSchedule(Admission admission) {
		if (OfflineFeeRules.BEFORE_ONBOARDING.contains(admission.status())) {
			throw state("Your admission is not approved yet, so there is no schedule to show.");
		}
		List<DueView> dues = dueViews(admission.id(), ledger.today());
		List<StudentPayment> payments = new ArrayList<>();
		for (Payment payment : ledger.approvedFor(admission.id())) {
			payments.add(new StudentPayment(payment.id(), payment.amount(), payment.method(), payment.reference(), payment.paidOn(),
					payment.receiptNo()));
		}
		return new StudentSchedule(admission.id(), admission.status(), total(dues), overdue(dues), dues, payments);
	}

	/**
	 * The receipt of an approved payment. With a student id it answers only for that student's own payment, and a pending, rejected
	 * or voided one is "not found" to them. Without one (staff) an unapproved payment is a state conflict.
	 */
	@Transactional(readOnly = true)
	public Receipt receipt(UUID paymentId, UUID studentId) {
		Payment payment = ledger.payment(paymentId).orElseThrow(OfflineFeeLedger::notFound);
		if (studentId != null) {
			if (!studentId.equals(payment.studentId()) || !OfflineFeePayment.STATUS_APPROVED.equals(payment.status())) {
				throw notFound();
			}
		}
		else if (!OfflineFeePayment.STATUS_APPROVED.equals(payment.status())) {
			throw state("Only an approved payment has a receipt.");
		}
		List<ReceiptLine> lines = jdbc.query("""
				select d.seq, d.due_on, a.amount from offline_fee_allocations a
				join offline_fee_dues d on d.id = a.due_id
				where a.payment_id = ? order by d.due_on, d.seq
				""", (rs, row) -> new ReceiptLine(rs.getInt("seq"), rs.getDate("due_on").toLocalDate(), rs.getBigDecimal("amount")),
				paymentId);
		return new Receipt(payment, ledger.batchName(payment.admissionId()), lines, ledger.owed(payment.admissionId()));
	}

	/** The approval step shared by a Master Admin's payment and an approval: the matching and the receipt notices. */
	private void settle(UUID paymentId, UUID admissionId, BigDecimal amount, UUID studentId) {
		ledger.match(paymentId, amount, ledger.open(admissionId));
		notices.studentNotice(OfflineFeeNotices.RECEIVED, paymentId, studentId);
		notices.studentMail(OfflineFeeNotices.MAIL_RECEIPT, paymentId, studentId);
	}

	/** Reads a payment under its admission's lock, so the status it is judged on cannot change under the caller. */
	private Locked locked(UUID paymentId) {
		UUID admissionId = jdbc.queryForList("select admission_id from offline_fee_payments where id = ?", UUID.class, paymentId)
			.stream()
			.findFirst()
			.orElseThrow(OfflineFeeLedger::notFound);
		Admission admission = ledger.lock(admissionId);
		Payment payment = ledger.payment(paymentId).orElseThrow(OfflineFeeLedger::notFound);
		return new Locked(payment, admission);
	}

	private List<DueView> dueViews(UUID admissionId, LocalDate today) {
		List<DueView> views = new ArrayList<>();
		for (OfflineFeeLedger.DueRow due : ledger.dues(admissionId)) {
			BigDecimal owed = due.owed();
			String state = owed.signum() == 0 ? "PAID" : due.paid().signum() == 0 ? "UNPAID" : "PART_PAID";
			views.add(new DueView(due.id(), due.seq(), due.dueOn(), due.amount(), due.paid(), owed,
					owed.signum() > 0 && due.dueOn().isBefore(today), state));
		}
		return views;
	}

	private static BigDecimal total(List<DueView> dues) {
		BigDecimal total = BigDecimal.ZERO;
		for (DueView due : dues) {
			total = total.add(due.owed());
		}
		return total;
	}

	private static BigDecimal overdue(List<DueView> dues) {
		BigDecimal total = BigDecimal.ZERO;
		for (DueView due : dues) {
			if (due.overdue()) {
				total = total.add(due.owed());
			}
		}
		return total;
	}

	private static Map<String, Object> payload(String personName, BigDecimal amount, String method, String reason) {
		Map<String, Object> payload = new java.util.LinkedHashMap<>();
		payload.put("personName", StaffNotificationWriter.fitText(personName));
		payload.put("amount", amount);
		payload.put("method", OfflineFeeRules.methodLabel(method));
		if (reason != null) {
			payload.put("reason", reason);
		}
		return payload;
	}

	private static String reasonText(String reason) {
		String text = reason == null ? "" : reason.trim();
		if (text.isEmpty() || text.length() > OfflineFeeRules.MAX_TEXT) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.",
					List.of(new FieldErrorDetail("reason", "Give a reason of 1 to 500 characters.")));
		}
		return text;
	}

	private static void throwIfAny(List<FieldErrorDetail> errors) {
		if (!errors.isEmpty()) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.", errors);
		}
	}

	private static final Set<String> STATUSES = Set.of(OfflineFeePayment.STATUS_PENDING, OfflineFeePayment.STATUS_APPROVED,
			OfflineFeePayment.STATUS_REJECTED, OfflineFeePayment.STATUS_VOIDED);

}
