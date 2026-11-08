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
import java.util.Objects;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.error.FieldErrorDetail;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The Master Admin's changes to a payment plan (plan sections 4 and 6): a payment, a void, a schedule change and restoring
 * access. Each runs in one transaction that first locks the plan's row, so two requests on one plan run one after the other and
 * the second is judged on the balance the first left. The receipt number is drawn last, after every check has passed.
 */
@Service
public class InstallmentPlanService {

	/** The most a void reason or a restore note may hold. */
	private static final int MAX_REASON = 500;

	/** The reason on the grant that restoring access writes. The revoked grant keeps its own reason. */
	static final String RESTORED_REASON = "Access restored on a payment plan.";

	private final JdbcTemplate jdbc;

	private final LiveExamReceipts receipts;

	private final AuditRecorder audit;

	private final Clock clock;

	private final ExamMailQueue mail;

	private final LiveExamGrantRepository grants;

	private final LiveExamRepository exams;

	private final LiveExamModuleRepository modules;

	private final LiveExamAccess access;

	private final InstallmentPlanReader reader;

	private final StudentNoticeWriter notices;

	private final InstallmentStaffNotices staff;

	public InstallmentPlanService(JdbcTemplate jdbc, LiveExamReceipts receipts, AuditRecorder audit, Clock clock,
			ExamMailQueue mail, LiveExamGrantRepository grants, LiveExamRepository exams, LiveExamModuleRepository modules,
			LiveExamAccess access, InstallmentPlanReader reader, StudentNoticeWriter notices, InstallmentStaffNotices staff) {
		this.jdbc = jdbc;
		this.receipts = receipts;
		this.audit = audit;
		this.clock = clock;
		this.mail = mail;
		this.grants = grants;
		this.exams = exams;
		this.modules = modules;
		this.access = access;
		this.reader = reader;
		this.notices = notices;
		this.staff = staff;
	}

	/**
	 * Records a payment against a plan (D6, plan section 4). Refused when it is more than the amount due, when its reference is
	 * already on this plan, or when its date is in the future. It does not change access. The receipt email is queued in the same
	 * transaction, and the scan sends it after commit.
	 */
	@Transactional
	public InstallmentPlanReader.PaymentView recordPayment(CurrentActor actor, UUID planId, RecordPaymentRequest request) {
		PlanHead plan = lockPlan(planId);
		LocalDate today = today();
		List<FieldErrorDetail> errors = new ArrayList<>();
		String method = request.method() == null ? "" : request.method().trim().toUpperCase(Locale.ROOT);
		if (!LiveExamPaymentPlanService.METHODS.contains(method)) {
			errors.add(new FieldErrorDetail("method", "Choose a payment method."));
		}
		String reference = request.reference() == null ? "" : request.reference().trim();
		if (!LiveExamPaymentPlanService.REFERENCE.matcher(reference).matches()) {
			errors.add(new FieldErrorDetail("reference", "A reference is required: 1 to 100 printable characters, no accents."));
		}
		String note = blankToNull(request.note());
		if (note != null && note.length() > LiveExamPaymentPlanService.MAX_NOTE) {
			errors.add(new FieldErrorDetail("note", "The note is at most 500 characters."));
		}
		if (request.amount() == null || !InstallmentSchedule.isMoney(request.amount()) || request.amount().signum() <= 0) {
			errors.add(new FieldErrorDetail("amount", "The amount must be more than zero, with at most two decimals."));
		}
		if (request.paidOn() == null || request.paidOn().isAfter(today)) {
			errors.add(new FieldErrorDetail("paidOn", "The payment date cannot be in the future."));
		}
		throwIfAny(errors);

		List<InstallmentSchedule.Row> rows = rows(planId);
		BigDecimal paidBefore = paidTotal(planId);
		if (request.amount().compareTo(plan.agreedTotal().subtract(paidBefore)) > 0) {
			throw new ApiException(ErrorCode.INSTALLMENT_OVERPAYMENT, HttpStatus.CONFLICT,
					"The payment is more than the amount still due.");
		}
		if (referenceTaken(planId, reference)) {
			throw new ApiException(ErrorCode.INSTALLMENT_DUPLICATE_REFERENCE, HttpStatus.CONFLICT,
					"This reference is already recorded on this plan.");
		}
		Instant now = clock.instant();
		InstallmentSchedule.Position position = InstallmentSchedule.position(rows, plan.agreedTotal(), paidBefore,
				request.amount());
		UUID paymentId = UUID.randomUUID();
		String receiptNo = receipts.next(request.paidOn().atStartOfDay(LiveExamReceipts.DHAKA).toInstant());
		jdbc.update("""
				insert into live_exam_direct_payments (id, plan_id, amount, method, reference, paid_on, note, receipt_no,
				  paid_to_date, due_after, next_due_on, next_due_amount, installment_no, installment_count, created_by, created_at)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", paymentId, planId, request.amount(), method, reference, Date.valueOf(request.paidOn()), note, receiptNo,
				position.paidToDate(), position.dueAfter(), position.nextDueOn() == null ? null : Date.valueOf(position.nextDueOn()),
				position.nextDueAmount(), position.installmentNo(), position.installmentCount(), actor.userId(),
				Timestamp.from(now));
		mail.enqueueInstallmentReceipt(paymentId, plan.studentId());
		audit.record(AuditRecorder.INSTALLMENT_PAYMENT_RECORDED, AuditRecorder.INSTALLMENT_PAYMENT_ENTITY, paymentId);
		notices.installmentPaymentReceived(plan.studentId(), paymentId);
		staff.paymentRecorded(planId, paymentId);
		return new InstallmentPlanReader.PaymentView(paymentId, planId, request.amount(), method, reference, request.paidOn(),
				note, receiptNo, position.paidToDate(), position.dueAfter(), position.nextDueOn(), position.nextDueAmount(),
				position.installmentNo(), position.installmentCount(), now, null, null);
	}

	/**
	 * Voids a payment (D16). Only the latest payment that is not voided can be voided, so no later receipt is ever wrong. The row
	 * stays, and its receipt number is never reused. Access is not changed (D5).
	 */
	@Transactional
	public InstallmentPlanReader.PaymentView voidPayment(CurrentActor actor, UUID paymentId, String reason) {
		String text = reason == null ? "" : reason.trim();
		if (text.isEmpty() || text.length() > MAX_REASON) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.",
					List.of(new FieldErrorDetail("reason", "Give a reason of 1 to 500 characters.")));
		}
		List<UUID> planIds = jdbc.queryForList("select plan_id from live_exam_direct_payments where id = ?", UUID.class,
				paymentId);
		if (planIds.isEmpty()) {
			throw notFound();
		}
		PlanHead plan = lockPlan(planIds.get(0));
		List<UUID> latest = jdbc.queryForList("""
				select id from live_exam_direct_payments
				where plan_id = ? and voided_at is null
				order by created_at desc, receipt_no desc
				limit 1
				""", UUID.class, plan.id());
		if (latest.isEmpty() || !latest.get(0).equals(paymentId)) {
			throw new ApiException(ErrorCode.INSTALLMENT_VOID_NOT_LATEST, HttpStatus.CONFLICT,
					"Only the latest payment that is not voided can be voided.");
		}
		jdbc.update("update live_exam_direct_payments set voided_at = ?, voided_by = ?, void_reason = ? where id = ?",
				Timestamp.from(clock.instant()), actor.userId(), text, paymentId);
		audit.record(AuditRecorder.INSTALLMENT_PAYMENT_VOIDED, AuditRecorder.INSTALLMENT_PAYMENT_ENTITY, paymentId);
		notices.installmentPaymentVoided(plan.studentId(), paymentId);
		return reader.payment(paymentId).orElseThrow(InstallmentPlanService::notFound);
	}

	/**
	 * Replaces the unpaid rows of a schedule (plan section 4). {@code schedule} is the whole schedule after the change: its first
	 * rows, the ones the payments already cover, must be exactly as they are, and the rest must add up to what is left.
	 */
	@Transactional
	public InstallmentPlanReader.PlanDetail changeSchedule(CurrentActor actor, UUID planId, ScheduleChangeRequest request) {
		PlanHead plan = lockPlan(planId);
		LocalDate today = today();
		List<InstallmentSchedule.Row> current = rows(planId);
		int frozen = InstallmentSchedule.coveredCount(current, paidTotal(planId));
		if (frozen >= current.size()) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.",
					List.of(new FieldErrorDetail("schedule", "Every installment is paid, so there is nothing to change.")));
		}
		if (request == null || request.schedule() == null || request.schedule().stream().anyMatch(Objects::isNull)) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.",
					List.of(new FieldErrorDetail("schedule", "The schedule is missing a row.")));
		}
		List<InstallmentSchedule.Row> proposed = new ArrayList<>();
		for (int i = 0; i < request.schedule().size(); i++) {
			LiveExamPaymentPlanService.ScheduleLine line = request.schedule().get(i);
			proposed.add(new InstallmentSchedule.Row(i + 1, line.amount(), line.dueOn()));
		}
		if (proposed.size() < frozen || !sameRows(current.subList(0, frozen), proposed.subList(0, frozen))) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.",
					List.of(new FieldErrorDetail("schedule", "A row that is already paid cannot change.")));
		}
		InstallmentSchedule.validateChange(plan.agreedTotal(), proposed, frozen, today);

		// A row that keeps its amount and date keeps its id, so the notices and reminders that point at it still hold. A
		// changed row is updated in place, a new row is added, and rows past the new end are removed.
		for (InstallmentSchedule.Row row : proposed.subList(frozen, proposed.size())) {
			if (row.seq() <= current.size()) {
				InstallmentSchedule.Row old = current.get(row.seq() - 1);
				if (old.amount().compareTo(row.amount()) != 0 || !old.dueOn().equals(row.dueOn())) {
					jdbc.update("update live_exam_installments set amount = ?, due_on = ? where plan_id = ? and seq = ?",
							row.amount(), Date.valueOf(row.dueOn()), planId, row.seq());
				}
			}
			else {
				jdbc.update("insert into live_exam_installments (id, plan_id, seq, amount, due_on) values (?, ?, ?, ?, ?)",
						UUID.randomUUID(), planId, row.seq(), row.amount(), Date.valueOf(row.dueOn()));
			}
		}
		if (proposed.size() < current.size()) {
			jdbc.update("delete from live_exam_installments where plan_id = ? and seq > ?", planId, proposed.size());
		}
		audit.record(AuditRecorder.INSTALLMENT_SCHEDULE_CHANGED, AuditRecorder.INSTALLMENT_PLAN_ENTITY, planId);
		notices.installmentScheduleChanged(plan.studentId(), planId, clock.instant());
		return reader.detail(planId, null).orElseThrow(InstallmentPlanService::notFound);
	}

	/**
	 * Gives back access that was revoked on this plan (plan section 4, C5). A new manual grant takes the old one's start, so the
	 * original access period still applies, and the plan moves to it. The revoked grant stays. Refused while the student has access
	 * again, when the exam or package is no longer published, or when the period has run out.
	 */
	@Transactional
	public LiveExamGrantAdminService.GrantView restoreAccess(CurrentActor actor, UUID planId) {
		PlanHead plan = lockPlan(planId);
		LiveExamGrant old = grants.findById(plan.grantId()).orElseThrow(InstallmentPlanService::notFound);
		if (old.getRevokedAt() == null) {
			throw refused("The access on this plan is not revoked, so there is nothing to restore.");
		}
		Instant now = clock.instant();
		if (LiveExamOrder.EXAM.equals(old.getScope())) {
			LiveExam exam = exams.findByIdAndDeletedAtIsNull(old.getExamId())
				.filter(found -> LiveExam.PUBLISHED.equals(found.getStatus()))
				.orElseThrow(() -> refused("The exam is no longer published."));
			if (access.canSit(plan.studentId(), exam, now)) {
				throw refused("The student already has access to this exam.");
			}
			if (AccessWindow.expired(exam, old.getCreatedAt(), now)) {
				throw refused("The access period has already ended.");
			}
		}
		else if (LiveExamOrder.MODULE.equals(old.getScope())) {
			LiveExamModule module = modules.findByIdAndDeletedAtIsNull(old.getModuleId())
				.filter(found -> LiveExamModule.PUBLISHED.equals(found.getStatus()))
				.orElseThrow(() -> refused("The package is no longer published."));
			AccessState state = access.moduleState(plan.studentId(), module.getId(), now);
			if (state.granted() && !state.expired()) {
				throw refused("The student already has access to this package.");
			}
			if (LiveExamAccess.expired(access.moduleEnd(module.getId(), old.getCreatedAt()), now)) {
				throw refused("The access period has already ended.");
			}
		}
		else {
			throw refused("This plan's access cannot be restored.");
		}

		LiveExamGrant grant = new LiveExamGrant();
		grant.setId(UUID.randomUUID());
		grant.setStudentId(plan.studentId());
		grant.setScope(old.getScope());
		grant.setModuleId(old.getModuleId());
		grant.setExamId(old.getExamId());
		grant.setSource(LiveExamGrant.SOURCE_MANUAL);
		grant.setGrantedBy(actor.userId());
		grant.setReason(RESTORED_REASON);
		grant.setCreatedAt(old.getCreatedAt());
		try {
			grants.saveAndFlush(grant);
		}
		catch (DataIntegrityViolationException exception) {
			// Another grant for this student and target was made at the same moment (plan section 6).
			throw new ApiException(ErrorCode.GRANT_ALREADY_ACTIVE, HttpStatus.CONFLICT,
					"This student already has active access to that module or exam.");
		}
		jdbc.update("update live_exam_payment_plans set grant_id = ? where id = ?", grant.getId(), planId);
		audit.record(AuditRecorder.INSTALLMENT_ACCESS_RESTORED, AuditRecorder.INSTALLMENT_PLAN_ENTITY, planId);
		return LiveExamGrantAdminService.view(grant);
	}

	/** The fields of a plan that a write needs, read under its lock. */
	private record PlanHead(UUID id, UUID grantId, UUID studentId, BigDecimal agreedTotal) {
	}

	/** Takes the plan's row lock. Every write to a plan's money goes through here first. */
	private PlanHead lockPlan(UUID planId) {
		List<PlanHead> found = jdbc.query("""
				select id, grant_id, student_id, agreed_total from live_exam_payment_plans where id = ? for update
				""", (rs, number) -> new PlanHead(rs.getObject("id", UUID.class), rs.getObject("grant_id", UUID.class),
						rs.getObject("student_id", UUID.class), rs.getBigDecimal("agreed_total")), planId);
		if (found.isEmpty()) {
			throw notFound();
		}
		return found.get(0);
	}

	private List<InstallmentSchedule.Row> rows(UUID planId) {
		return jdbc.query("select seq, amount, due_on from live_exam_installments where plan_id = ? order by seq",
				(rs, number) -> new InstallmentSchedule.Row(rs.getInt("seq"), rs.getBigDecimal("amount"),
						rs.getDate("due_on").toLocalDate()), planId);
	}

	/** What the plan has been paid so far: its payments that are not voided. */
	private BigDecimal paidTotal(UUID planId) {
		BigDecimal total = jdbc.queryForObject(
				"select coalesce(sum(amount), 0) from live_exam_direct_payments where plan_id = ? and voided_at is null",
				BigDecimal.class, planId);
		return total == null ? BigDecimal.ZERO : total;
	}

	/** Whether a live payment on this plan already uses the reference, ignoring case and outer spaces. */
	private boolean referenceTaken(UUID planId, String reference) {
		Integer count = jdbc.queryForObject("""
				select count(*) from live_exam_direct_payments
				where plan_id = ? and voided_at is null and upper(btrim(reference)) = upper(?)
				""", Integer.class, planId, reference);
		return count != null && count > 0;
	}

	/** Two rows match when their amounts are equal (not their scale) and their dates are the same day. */
	private static boolean sameRows(List<InstallmentSchedule.Row> stored, List<InstallmentSchedule.Row> proposed) {
		for (int i = 0; i < stored.size(); i++) {
			InstallmentSchedule.Row a = stored.get(i);
			InstallmentSchedule.Row b = proposed.get(i);
			if (b.amount() == null || a.amount().compareTo(b.amount()) != 0 || !a.dueOn().equals(b.dueOn())) {
				return false;
			}
		}
		return true;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private LocalDate today() {
		return LocalDate.now(clock.withZone(LiveExamReceipts.DHAKA));
	}

	private static void throwIfAny(List<FieldErrorDetail> errors) {
		if (!errors.isEmpty()) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.", errors);
		}
	}

	private static ApiException refused(String message) {
		return new ApiException(ErrorCode.INSTALLMENT_RESTORE_REFUSED, HttpStatus.CONFLICT, message);
	}

	private static ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	/** A payment the Master Admin records. {@code paidOn} is the Dhaka date the money came in. */
	public record RecordPaymentRequest(
			@NotNull BigDecimal amount,
			@NotNull String method,
			@NotNull @Size(max = 100) String reference,
			@NotNull LocalDate paidOn,
			@Size(max = LiveExamPaymentPlanService.MAX_NOTE) String note) {
	}

	/** The whole schedule after a change: the paid rows as they are, then the unpaid rows the Master Admin wants. */
	public record ScheduleChangeRequest(@NotNull @Valid List<LiveExamPaymentPlanService.ScheduleLine> schedule) {
	}

	/** The reason for a void, 1 to 500 characters. */
	public record VoidRequest(@Size(max = MAX_REASON) String reason) {
	}

}
