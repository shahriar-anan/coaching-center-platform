package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.error.FieldErrorDetail;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Opens a payment plan for a direct enrolment that was taken with money (plan direct-enrolment-installments, sections 4 and 6).
 * It joins the grant's transaction: the grant, the plan, its schedule, the first payment and its receipt commit together, or
 * none of them do.
 */
@Service
public class LiveExamPaymentPlanService {

	/** The methods a direct payment can use (C1). */
	static final Set<String> METHODS = Set.of("CASH", "BKASH", "NAGAD", "ROCKET", "BANK");

	/** Printable ASCII only: the receipt PDF prints Latin letters, so anything else would vanish from it. */
	static final Pattern REFERENCE = Pattern.compile("[\\x20-\\x7E]{1,100}");

	static final int MAX_NOTE = 500;

	private final JdbcTemplate jdbc;

	private final LiveExamReceipts receipts;

	private final AuditRecorder audit;

	private final Clock clock;

	private final ExamMailQueue mail;

	private final StudentNoticeWriter notices;

	private final InstallmentStaffNotices staff;

	public LiveExamPaymentPlanService(JdbcTemplate jdbc, LiveExamReceipts receipts, AuditRecorder audit, Clock clock,
			ExamMailQueue mail, StudentNoticeWriter notices, InstallmentStaffNotices staff) {
		this.jdbc = jdbc;
		this.receipts = receipts;
		this.audit = audit;
		this.clock = clock;
		this.mail = mail;
		this.notices = notices;
		this.staff = staff;
	}

	/**
	 * Writes the plan for one grant. {@code price} is the target's price when the enrolment was made: the list price, its
	 * discount, and the price after the discount. Each may be null when the target has no price.
	 *
	 * @throws ApiException with {@link ErrorCode#VALIDATION_FAILED} when the payment breaks a rule
	 */
	@Transactional
	public PlanView open(UUID grantId, UUID studentId, PriceSnapshot price, DirectPaymentRequest request, UUID actorId) {
		LocalDate today = LocalDate.now(clock.withZone(LiveExamReceipts.DHAKA));
		Instant now = clock.instant();
		List<FieldErrorDetail> errors = new ArrayList<>();
		String method = request.method() == null ? "" : request.method().trim().toUpperCase(Locale.ROOT);
		if (!METHODS.contains(method)) {
			errors.add(new FieldErrorDetail("method", "Choose a payment method."));
		}
		String reference = request.reference() == null ? "" : request.reference().trim();
		if (!REFERENCE.matcher(reference).matches()) {
			errors.add(new FieldErrorDetail("reference", "A reference is required: 1 to 100 printable characters, no accents."));
		}
		String note = request.note() == null || request.note().isBlank() ? null : request.note().trim();
		if (note != null && note.length() > MAX_NOTE) {
			errors.add(new FieldErrorDetail("note", "The note is at most 500 characters."));
		}
		if (request.schedule() == null || request.schedule().stream().anyMatch(line -> line == null)) {
			errors.add(new FieldErrorDetail("schedule", "The schedule is missing a row."));
		}
		if (!errors.isEmpty()) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.", errors);
		}
		List<InstallmentSchedule.Row> rows = new ArrayList<>();
		for (int i = 0; i < request.schedule().size(); i++) {
			ScheduleLine line = request.schedule().get(i);
			rows.add(new InstallmentSchedule.Row(i + 1, line.amount(), line.dueOn()));
		}
		InstallmentSchedule.validate(request.agreedTotal(), request.amount(), request.paidOn(), rows, today);

		UUID planId = UUID.randomUUID();
		jdbc.update("""
				insert into live_exam_payment_plans (id, grant_id, student_id, list_price, discount_percent, standard_price,
				  agreed_total, created_by, created_at)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", planId, grantId, studentId, price.listPrice(), price.discountPercent(), price.standardPrice(),
				request.agreedTotal(), actorId, Timestamp.from(now));
		for (InstallmentSchedule.Row row : rows) {
			jdbc.update("""
					insert into live_exam_installments (id, plan_id, seq, amount, due_on)
					values (?, ?, ?, ?, ?)
					""", UUID.randomUUID(), planId, row.seq(), row.amount(), Date.valueOf(row.dueOn()));
		}

		// The receipt number is drawn last, once every check has passed, so a refused plan leaves no gap (plan section 4).
		InstallmentSchedule.Position position = InstallmentSchedule.position(rows, request.agreedTotal(), BigDecimal.ZERO,
				request.amount());
		UUID paymentId = UUID.randomUUID();
		String receiptNo = receipts.next(request.paidOn().atStartOfDay(LiveExamReceipts.DHAKA).toInstant());
		jdbc.update("""
				insert into live_exam_direct_payments (id, plan_id, amount, method, reference, paid_on, note, receipt_no,
				  paid_to_date, due_after, next_due_on, next_due_amount, installment_no, installment_count, created_by, created_at)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", paymentId, planId, request.amount(), method, reference, Date.valueOf(request.paidOn()), note, receiptNo,
				position.paidToDate(), position.dueAfter(), position.nextDueOn() == null ? null : Date.valueOf(position.nextDueOn()),
				position.nextDueAmount(), position.installmentNo(), position.installmentCount(), actorId, Timestamp.from(now));

		audit.record(AuditRecorder.INSTALLMENT_PLAN_CREATED, AuditRecorder.INSTALLMENT_PLAN_ENTITY, planId);
		audit.record(AuditRecorder.INSTALLMENT_PAYMENT_RECORDED, AuditRecorder.INSTALLMENT_PAYMENT_ENTITY, paymentId);
		// The first payment is announced like every other one (plan section 7). Each call only adds a row, or queues after commit.
		notices.installmentPaymentReceived(studentId, paymentId);
		mail.enqueueInstallmentReceipt(paymentId, studentId);
		staff.paymentRecorded(planId, paymentId);
		return new PlanView(planId, paymentId, receiptNo, request.agreedTotal(), position);
	}

	/**
	 * The student's latest plan on one target that still has an unpaid balance, whether its grant is revoked or not (plan
	 * section 4, E11). Null when there is none. Voided payments do not count as paid.
	 */
	@Transactional(readOnly = true)
	public UUID unfinishedPlanId(UUID studentId, String scope, UUID moduleId, UUID examId) {
		List<UUID> found = jdbc.query("""
				select p.id
				from live_exam_payment_plans p
				join live_exam_grants g on g.id = p.grant_id
				where p.student_id = ? and g.scope = ? and (g.module_id = ? or g.exam_id = ?)
				  and p.agreed_total > coalesce((select sum(d.amount) from live_exam_direct_payments d
				    where d.plan_id = p.id and d.voided_at is null), 0)
				order by p.created_at desc
				limit 1
				""", (rs, number) -> rs.getObject("id", UUID.class), studentId, scope, moduleId, examId);
		return found.isEmpty() ? null : found.get(0);
	}

	/** The list price, the discount and the price after it, as they stood when the enrolment was made. */
	record PriceSnapshot(BigDecimal listPrice, Integer discountPercent, BigDecimal standardPrice) {
	}

	/** One installment line as the form sends it: an amount and the day it falls due. */
	public record ScheduleLine(@NotNull BigDecimal amount, @NotNull LocalDate dueOn) {
	}

	/**
	 * The {@code payment} part of an enrolment. {@code agreedTotal} is the price agreed for this enrolment (D1). {@code amount}
	 * and {@code paidOn} describe the payment taken now. {@code schedule} lists every row, the first being this payment.
	 */
	public record DirectPaymentRequest(
			@NotNull BigDecimal agreedTotal,
			@NotNull BigDecimal amount,
			@NotNull String method,
			@NotNull @Size(max = 100) String reference,
			@NotNull LocalDate paidOn,
			@Size(max = MAX_NOTE) String note,
			@NotNull @Valid List<ScheduleLine> schedule) {
	}

	/** What the enrolment returns for the payment it recorded. */
	public record PlanView(UUID planId, UUID paymentId, String receiptNo, BigDecimal agreedTotal,
			InstallmentSchedule.Position position) {
	}

}
