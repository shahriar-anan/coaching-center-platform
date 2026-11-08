package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.error.FieldErrorDetail;
import com.coachingcenter.api.common.web.PageResponse;

/**
 * Reads the payment plans of direct enrolments for the staff Installments pages and the student's Offline purchases tab (plan
 * sections 6 and 8). Nothing is written here. Every money figure is computed from the schedule and the non-voided payments, on
 * Dhaka's date. A student's rows are read through {@code studentOwner}, so another student's plan is simply not found.
 */
@Service
public class InstallmentPlanReader {

	/** Plans per page on the staff list. */
	static final int PAGE_SIZE = 20;

	private static final Set<String> STATUSES = Set.of(InstallmentCalculator.ACTIVE, InstallmentCalculator.DUE_SOON,
			InstallmentCalculator.OVERDUE, InstallmentCalculator.COMPLETED, InstallmentCalculator.ACCESS_REVOKED);

	/** Most overdue first, then the plans that need attention soon, then the rest. */
	private static final Map<String, Integer> RANK = Map.of(InstallmentCalculator.OVERDUE, 0,
			InstallmentCalculator.DUE_SOON, 1, InstallmentCalculator.ACTIVE, 2, InstallmentCalculator.ACCESS_REVOKED, 3,
			InstallmentCalculator.COMPLETED, 4);

	private static final Comparator<PlanRow> MOST_OVERDUE_FIRST = Comparator
		.comparingInt((PlanRow row) -> RANK.getOrDefault(row.status(), 9))
		.thenComparing(PlanRow::nextDueOn, Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()))
		.thenComparing(PlanRow::createdAt, Comparator.reverseOrder());

	/** The plan, its grant, the student and the item. Titles come from the exam for an exam grant, else the package. */
	private static final String BASE_COLUMNS = """
			p.id as plan_id, p.grant_id, p.student_id, p.agreed_total, p.standard_price, p.created_at,
			g.scope, g.exam_id, g.module_id, g.revoked_at,
			sp.full_name, u.email as student_email, u.phone as student_phone,
			e.title_en as exam_title_en, e.title_bn as exam_title_bn, e.access_days,
			m.title_en as module_title_en, m.title_bn as module_title_bn
			""";

	private static final String BASE_JOINS = """
			join live_exam_grants g on g.id = p.grant_id
			join users u on u.id = p.student_id
			left join student_profiles sp on sp.user_id = u.id
			left join live_exams e on e.id = g.exam_id
			left join live_exam_modules m on m.id = g.module_id
			""";

	private static final String PLAN_FROM = "from live_exam_payment_plans p " + BASE_JOINS;

	private static final String RECEIPT_FROM = "from live_exam_direct_payments pay join live_exam_payment_plans p on p.id = pay.plan_id "
			+ BASE_JOINS;

	private static final String PAYMENT_COLUMNS = """
			id, plan_id, amount, method, reference, paid_on, note, receipt_no, paid_to_date, due_after, next_due_on,
			next_due_amount, installment_no, installment_count, created_at, voided_at, void_reason
			""";

	private final NamedParameterJdbcTemplate jdbc;

	private final Clock clock;

	public InstallmentPlanReader(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = new NamedParameterJdbcTemplate(jdbc);
		this.clock = clock;
	}

	/** A plan as the list and the detail show it. {@code paid} and {@code due} are what the schedule says at this moment. */
	public record PlanRow(UUID planId, UUID grantId, UUID studentId, String studentName, String studentEmail,
			String studentPhone, String scope, UUID examId, UUID moduleId, String itemTitleEn, String itemTitleBn,
			BigDecimal agreedTotal, BigDecimal paid, BigDecimal due, boolean accessRevoked, LocalDate nextDueOn,
			BigDecimal nextDueAmount, String status, Instant createdAt) {

		/** The same plan without the student's contact details, for the student's own view. */
		PlanRow withoutContact() {
			return new PlanRow(planId, grantId, studentId, null, null, null, scope, examId, moduleId, itemTitleEn,
					itemTitleBn, agreedTotal, paid, due, accessRevoked, nextDueOn, nextDueAmount, status, createdAt);
		}
	}

	/**
	 * One payment as its receipt and the plan show it. {@code note} and {@code voidReason} are staff text, and are left out of
	 * the student's view.
	 */
	public record PaymentView(UUID id, UUID planId, BigDecimal amount, String method, String reference, LocalDate paidOn,
			String note, String receiptNo, BigDecimal paidToDate, BigDecimal dueAfter, LocalDate nextDueOn,
			BigDecimal nextDueAmount, int installmentNo, int installmentCount, Instant createdAt, Instant voidedAt,
			String voidReason) {

		PaymentView forStudent() {
			return new PaymentView(id, planId, amount, method, reference, paidOn, null, receiptNo, paidToDate, dueAfter,
					nextDueOn, nextDueAmount, installmentNo, installmentCount, createdAt, voidedAt, null);
		}
	}

	/** A plan's schedule with each row's state, and every payment, the voided ones included. */
	public record PlanDetail(PlanRow plan, List<InstallmentCalculator.RowState> schedule, List<PaymentView> payments) {

		PlanDetail forStudent() {
			return new PlanDetail(plan.withoutContact(), schedule, payments.stream().map(PaymentView::forStudent).toList());
		}
	}

	/** The figures the receipt PDF and the receipt email need, read together with the plan they belong to. */
	public record PaymentReceipt(UUID paymentId, UUID studentId, String receiptNo, LocalDate paidOn, BigDecimal amount,
			String method, String reference, BigDecimal paidToDate, BigDecimal dueAfter, LocalDate nextDueOn,
			BigDecimal nextDueAmount, int installmentNo, int installmentCount, boolean voided, String studentName,
			String studentEmail, String studentPhone, boolean exam, String itemTitleEn, String itemTitleBn,
			Integer accessDays, BigDecimal standardPrice, BigDecimal agreedTotal) {
	}

	/** The staff totals: what is still owed, and how many plans are overdue or due soon. */
	public record Summary(BigDecimal totalDue, int overdueCount, int dueSoonCount) {
	}

	/**
	 * The plans that match the filters, most overdue first, one page at a time. {@code status} is one of ACTIVE, DUE_SOON,
	 * OVERDUE, COMPLETED or ACCESS_REVOKED, or null for all. {@code q} matches the student's name, email and phone, and the item's
	 * titles.
	 */
	@Transactional(readOnly = true)
	public PageResponse<PlanRow> list(String status, String q, UUID studentId, UUID examId, UUID moduleId, int page) {
		String wanted = status == null || status.isBlank() ? null : status.trim().toUpperCase();
		if (wanted != null && !STATUSES.contains(wanted)) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.",
					List.of(new FieldErrorDetail("status", "Choose a status from the list.")));
		}
		StringBuilder where = new StringBuilder(" where 1 = 1");
		MapSqlParameterSource params = new MapSqlParameterSource();
		if (studentId != null) {
			where.append(" and p.student_id = :studentId");
			params.addValue("studentId", studentId);
		}
		if (examId != null) {
			where.append(" and g.scope = 'EXAM' and g.exam_id = :examId");
			params.addValue("examId", examId);
		}
		if (moduleId != null) {
			where.append(" and g.scope = 'MODULE' and g.module_id = :moduleId");
			params.addValue("moduleId", moduleId);
		}
		if (q != null && !q.isBlank()) {
			where.append(" and (sp.full_name ilike :q or u.email ilike :q or u.phone ilike :q or e.title_en ilike :q"
					+ " or e.title_bn ilike :q or m.title_en ilike :q or m.title_bn ilike :q)");
			params.addValue("q", "%" + escapeLike(q.trim()) + "%");
		}
		List<PlanRow> matching = plans(where.toString(), params, today()).stream()
			.filter(row -> wanted == null || wanted.equals(row.status()))
			.sorted(MOST_OVERDUE_FIRST)
			.toList();
		int pageNumber = Math.max(page, 0);
		long from = Math.min((long) pageNumber * PAGE_SIZE, matching.size());
		long to = Math.min(from + PAGE_SIZE, matching.size());
		List<PlanRow> content = new ArrayList<>(matching.subList((int) from, (int) to));
		return PageResponse.from(new PageImpl<>(content, PageRequest.of(pageNumber, PAGE_SIZE), matching.size()));
	}

	/** The totals for the staff summary band. Completed plans add nothing to what is due. */
	@Transactional(readOnly = true)
	public Summary summary() {
		BigDecimal due = BigDecimal.ZERO;
		int overdue = 0;
		int dueSoon = 0;
		for (PlanRow row : plans(" where 1 = 1", new MapSqlParameterSource(), today())) {
			if (!InstallmentCalculator.COMPLETED.equals(row.status())) {
				due = due.add(row.due());
			}
			if (InstallmentCalculator.OVERDUE.equals(row.status())) {
				overdue++;
			}
			if (InstallmentCalculator.DUE_SOON.equals(row.status())) {
				dueSoon++;
			}
		}
		return new Summary(due, overdue, dueSoon);
	}

	/**
	 * One plan with its schedule and payments. With a {@code studentOwner}, only that student's plan is found; anyone else's is
	 * empty, which the caller answers as not found.
	 */
	@Transactional(readOnly = true)
	public Optional<PlanDetail> detail(UUID planId, UUID studentOwner) {
		MapSqlParameterSource params = new MapSqlParameterSource("planId", planId);
		String where = " where p.id = :planId" + owner(studentOwner, params);
		List<Base> found = jdbc.query("select " + BASE_COLUMNS + PLAN_FROM + where, params, (rs, number) -> base(rs));
		if (found.isEmpty()) {
			return Optional.empty();
		}
		Base base = found.get(0);
		List<InstallmentSchedule.Row> rows = schedules(List.of(planId)).getOrDefault(planId, List.of());
		BigDecimal paid = paid(List.of(planId)).getOrDefault(planId, BigDecimal.ZERO);
		InstallmentCalculator.Standing standing = InstallmentCalculator.standing(rows, base.agreedTotal(), paid,
				base.revoked(), today());
		List<PaymentView> payments = jdbc.query("select " + PAYMENT_COLUMNS
				+ " from live_exam_direct_payments where plan_id = :planId order by created_at, receipt_no",
				new MapSqlParameterSource("planId", planId), (rs, number) -> paymentRow(rs));
		return Optional.of(new PlanDetail(row(base, standing), standing.rows(), payments));
	}

	/** One payment, for the response to a void or a payment. Empty when there is no such payment. */
	@Transactional(readOnly = true)
	public Optional<PaymentView> payment(UUID paymentId) {
		List<PaymentView> found = jdbc.query("select " + PAYMENT_COLUMNS + " from live_exam_direct_payments where id = :id",
				new MapSqlParameterSource("id", paymentId), (rs, number) -> paymentRow(rs));
		return found.stream().findFirst();
	}

	/**
	 * One payment with the figures of its receipt. With a {@code studentOwner}, another student's payment is empty, which the
	 * caller answers as not found.
	 */
	@Transactional(readOnly = true)
	public Optional<PaymentReceipt> receipt(UUID paymentId, UUID studentOwner) {
		MapSqlParameterSource params = new MapSqlParameterSource("paymentId", paymentId);
		String where = " where pay.id = :paymentId" + owner(studentOwner, params);
		List<PaymentReceipt> found = jdbc.query("select pay.id as payment_id, pay.receipt_no, pay.paid_on, pay.amount,"
				+ " pay.method, pay.reference, pay.paid_to_date, pay.due_after, pay.next_due_on, pay.next_due_amount,"
				+ " pay.installment_no, pay.installment_count, pay.voided_at, " + BASE_COLUMNS + RECEIPT_FROM + where,
				params, (rs, number) -> receiptOf(rs));
		return found.stream().findFirst();
	}

	/** The plans of one student, keyed by the grant each belongs to. The Offline purchases tab reads its money from these. */
	@Transactional(readOnly = true)
	public Map<UUID, PlanRow> byGrant(UUID studentId) {
		Map<UUID, PlanRow> out = new HashMap<>();
		MapSqlParameterSource params = new MapSqlParameterSource("studentId", studentId);
		for (PlanRow row : plans(" where p.student_id = :studentId", params, today())) {
			out.put(row.grantId(), row);
		}
		return out;
	}

	/** The plans on these grants, keyed by grant. The enrolled list reads its Paid or Due figures from these, for a caller who may see money. */
	@Transactional(readOnly = true)
	public Map<UUID, PlanRow> byGrantIds(Collection<UUID> grantIds) {
		Map<UUID, PlanRow> out = new HashMap<>();
		if (grantIds.isEmpty()) {
			return out;
		}
		for (PlanRow row : plans(" where p.grant_id in (:grantIds)", new MapSqlParameterSource("grantIds", grantIds), today())) {
			out.put(row.grantId(), row);
		}
		return out;
	}

	/**
	 * One installment as a reminder sees it: its date, what is still owed on it, and whether the plan still stands (the grant is
	 * not revoked and a payment is live). {@code due} is what the plan still owes in all.
	 */
	public record DueRow(UUID installmentId, UUID planId, UUID studentId, String studentName, String itemTitleEn,
			String itemTitleBn, LocalDate dueOn, BigDecimal uncovered, BigDecimal due, boolean revoked, boolean livePayment) {
	}

	/**
	 * One installment, read at the moment a reminder is sent (plan section 7). Empty when the row no longer exists, which is what
	 * a reschedule does to the unpaid rows.
	 */
	@Transactional(readOnly = true)
	public Optional<DueRow> dueRow(UUID installmentId) {
		List<Slot> slots = jdbc.query("select plan_id, seq, due_on from live_exam_installments where id = :id",
				new MapSqlParameterSource("id", installmentId),
				(rs, number) -> new Slot(rs.getObject("plan_id", UUID.class), rs.getInt("seq"),
						rs.getDate("due_on").toLocalDate()));
		if (slots.isEmpty()) {
			return Optional.empty();
		}
		Slot slot = slots.get(0);
		PlanDetail detail = detail(slot.planId(), null).orElse(null);
		if (detail == null) {
			return Optional.empty();
		}
		InstallmentCalculator.RowState state = detail.schedule()
			.stream()
			.filter(row -> row.seq() == slot.seq())
			.findFirst()
			.orElse(null);
		if (state == null) {
			return Optional.empty();
		}
		PlanRow plan = detail.plan();
		boolean livePayment = detail.payments().stream().anyMatch(payment -> payment.voidedAt() == null);
		return Optional.of(new DueRow(installmentId, plan.planId(), plan.studentId(), plan.studentName(), plan.itemTitleEn(),
				plan.itemTitleBn(), slot.dueOn(), state.uncovered(), plan.due(), plan.accessRevoked(), livePayment));
	}

	/** The id of the installment at a position on a plan. Empty when there is none. */
	@Transactional(readOnly = true)
	public Optional<UUID> installmentId(UUID planId, int seq) {
		return jdbc.query("select id from live_exam_installments where plan_id = :planId and seq = :seq",
				new MapSqlParameterSource("planId", planId).addValue("seq", seq), (rs, number) -> rs.getObject("id", UUID.class))
			.stream()
			.findFirst();
	}

	private record Slot(UUID planId, int seq, LocalDate dueOn) {
	}

	/** The name a payment method has on a receipt. */
	public static String methodLabel(String method) {
		if (method == null) {
			return "";
		}
		return switch (method) {
			case "CASH" -> "Cash";
			case "BKASH" -> "bKash";
			case "NAGAD" -> "Nagad";
			case "ROCKET" -> "Rocket";
			case "BANK" -> "Bank";
			default -> method;
		};
	}

	/** The database's view of one plan, before its money is worked out. */
	private record Base(UUID planId, UUID grantId, UUID studentId, String studentName, String studentEmail,
			String studentPhone, String scope, UUID examId, UUID moduleId, String itemTitleEn, String itemTitleBn,
			Integer accessDays, BigDecimal agreedTotal, BigDecimal standardPrice, boolean revoked, Instant createdAt) {
	}

	private List<PlanRow> plans(String where, MapSqlParameterSource params, LocalDate today) {
		List<Base> bases = jdbc.query("select " + BASE_COLUMNS + PLAN_FROM + where + " order by p.created_at desc, p.id",
				params, (rs, number) -> base(rs));
		if (bases.isEmpty()) {
			return List.of();
		}
		List<UUID> ids = bases.stream().map(Base::planId).toList();
		Map<UUID, List<InstallmentSchedule.Row>> schedules = schedules(ids);
		Map<UUID, BigDecimal> paid = paid(ids);
		List<PlanRow> out = new ArrayList<>();
		for (Base base : bases) {
			InstallmentCalculator.Standing standing = InstallmentCalculator.standing(
					schedules.getOrDefault(base.planId(), List.of()), base.agreedTotal(),
					paid.getOrDefault(base.planId(), BigDecimal.ZERO), base.revoked(), today);
			out.add(row(base, standing));
		}
		return out;
	}

	private Map<UUID, List<InstallmentSchedule.Row>> schedules(Collection<UUID> planIds) {
		Map<UUID, List<InstallmentSchedule.Row>> out = new HashMap<>();
		if (planIds.isEmpty()) {
			return out;
		}
		jdbc.query("select plan_id, seq, amount, due_on from live_exam_installments where plan_id in (:ids)"
				+ " order by plan_id, seq", new MapSqlParameterSource("ids", planIds), (RowCallbackHandler) rs -> out
					.computeIfAbsent(rs.getObject("plan_id", UUID.class), key -> new ArrayList<>())
					.add(new InstallmentSchedule.Row(rs.getInt("seq"), rs.getBigDecimal("amount"),
							rs.getDate("due_on").toLocalDate())));
		return out;
	}

	/** What each plan has been paid so far: its payments that are not voided. */
	private Map<UUID, BigDecimal> paid(Collection<UUID> planIds) {
		Map<UUID, BigDecimal> out = new HashMap<>();
		if (planIds.isEmpty()) {
			return out;
		}
		jdbc.query("select plan_id, coalesce(sum(amount), 0) as paid from live_exam_direct_payments"
				+ " where plan_id in (:ids) and voided_at is null group by plan_id", new MapSqlParameterSource("ids", planIds),
				(RowCallbackHandler) rs -> out.put(rs.getObject("plan_id", UUID.class), rs.getBigDecimal("paid")));
		return out;
	}

	private static PlanRow row(Base base, InstallmentCalculator.Standing standing) {
		return new PlanRow(base.planId(), base.grantId(), base.studentId(), base.studentName(), base.studentEmail(),
				base.studentPhone(), base.scope(), base.examId(), base.moduleId(), base.itemTitleEn(), base.itemTitleBn(),
				base.agreedTotal(), standing.paid(), standing.due(), base.revoked(), standing.nextDueOn(),
				standing.nextDueAmount(), standing.status(), base.createdAt());
	}

	private static Base base(ResultSet rs) throws SQLException {
		boolean exam = LiveExamOrder.EXAM.equals(rs.getString("scope"));
		return new Base(rs.getObject("plan_id", UUID.class), rs.getObject("grant_id", UUID.class),
				rs.getObject("student_id", UUID.class), rs.getString("full_name"), rs.getString("student_email"),
				rs.getString("student_phone"), rs.getString("scope"), rs.getObject("exam_id", UUID.class),
				rs.getObject("module_id", UUID.class),
				exam ? rs.getString("exam_title_en") : rs.getString("module_title_en"),
				exam ? rs.getString("exam_title_bn") : rs.getString("module_title_bn"),
				exam ? rs.getObject("access_days", Integer.class) : null,
				rs.getBigDecimal("agreed_total"), rs.getBigDecimal("standard_price"), rs.getTimestamp("revoked_at") != null,
				instant(rs, "created_at"));
	}

	private static PaymentReceipt receiptOf(ResultSet rs) throws SQLException {
		Base base = base(rs);
		return new PaymentReceipt(rs.getObject("payment_id", UUID.class), base.studentId(), rs.getString("receipt_no"),
				rs.getDate("paid_on").toLocalDate(), rs.getBigDecimal("amount"), rs.getString("method"),
				rs.getString("reference"), rs.getBigDecimal("paid_to_date"), rs.getBigDecimal("due_after"),
				localDate(rs, "next_due_on"), rs.getBigDecimal("next_due_amount"), rs.getInt("installment_no"),
				rs.getInt("installment_count"), rs.getTimestamp("voided_at") != null, base.studentName(),
				base.studentEmail(), base.studentPhone(), LiveExamOrder.EXAM.equals(base.scope()), base.itemTitleEn(),
				base.itemTitleBn(), base.accessDays(), base.standardPrice(), base.agreedTotal());
	}

	private static PaymentView paymentRow(ResultSet rs) throws SQLException {
		return new PaymentView(rs.getObject("id", UUID.class), rs.getObject("plan_id", UUID.class),
				rs.getBigDecimal("amount"), rs.getString("method"), rs.getString("reference"),
				rs.getDate("paid_on").toLocalDate(), rs.getString("note"), rs.getString("receipt_no"),
				rs.getBigDecimal("paid_to_date"), rs.getBigDecimal("due_after"), localDate(rs, "next_due_on"),
				rs.getBigDecimal("next_due_amount"), rs.getInt("installment_no"), rs.getInt("installment_count"),
				instant(rs, "created_at"), instant(rs, "voided_at"), rs.getString("void_reason"));
	}

	/** The owner condition, when a student asks. Null means staff, who may read any plan. */
	private static String owner(UUID studentOwner, MapSqlParameterSource params) {
		if (studentOwner == null) {
			return "";
		}
		params.addValue("studentId", studentOwner);
		return " and p.student_id = :studentId";
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		var value = rs.getTimestamp(column);
		return value == null ? null : value.toInstant();
	}

	private static LocalDate localDate(ResultSet rs, String column) throws SQLException {
		var value = rs.getDate(column);
		return value == null ? null : value.toLocalDate();
	}

	/** Escapes the LIKE wildcards, so a search for "50%" finds that text and nothing wider. */
	private static String escapeLike(String value) {
		return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	private LocalDate today() {
		return LocalDate.now(clock.withZone(LiveExamReceipts.DHAKA));
	}

}
