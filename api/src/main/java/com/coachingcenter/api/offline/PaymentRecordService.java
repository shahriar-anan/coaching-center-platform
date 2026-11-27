package com.coachingcenter.api.offline;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import com.coachingcenter.api.exams.InstallmentPlanService;
import com.coachingcenter.api.staffnotifications.StaffNotificationKind;
import com.coachingcenter.api.staffnotifications.StaffNotificationWriter;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The payment records page (payment-records-edit-plan.md): every recorded course fee payment, direct enrolment
 * payment the caller may see, with filters, and their corrections. A System Admin's correction waits for the Master Admin, who finalizes
 * or rejects it; the Master Admin's own is final at once (P3). A finalized correction changes the payment row itself, so Finance, which
 * reads those rows, shows it (P5). Every correction keeps the values before and after.
 */
@Service
public class PaymentRecordService {

	public static final String FEE = "FEE";

	/**
	 * Retired (accounting, R3): instructor payments are corrected in Accounting now, and no longer listed here. The constant stays until
	 * the old instructor pay service is removed; nothing here lists or corrects this kind.
	 */
	public static final String CLASS = "CLASS";

	public static final String DIRECT = "DIRECT";

	/** Online purchases through the payment gateway (live_exam_orders): listed, never corrected here (16 Nov). */
	public static final String ONLINE = "ONLINE";

	/** Staff pay (staff_ledger PAYMENT rows): listed, corrected on the Staff pay page (16 Nov). */
	public static final String STAFF = "STAFF";

	/** Retired (accounting, R3): instructor payments moved to the accounting ledger. See {@link #CLASS}. */
	public static final String OTHER = "OTHER";

	/** The kinds a correction can be made to: the payments the office records by hand, fees and direct enrolment payments. */
	static final Set<String> KINDS = Set.of(FEE, DIRECT);

	/** Money in and money out, as Finance counts them (16 Nov). */
	static final Set<String> INCOME = Set.of(ONLINE, DIRECT, FEE);

	static final Set<String> EXPENSE = Set.of(STAFF);

	private static final Set<String> METHODS = Set.of("CASH", "BKASH", "NAGAD", "ROCKET", "BANK");

	private static final Pattern REFERENCE = Pattern.compile("[ -~]{1,100}");

	private static final int MAX_TEXT = 500;

	private static final String PAGE_LINK = "/staff/payment-records";

	private static final String MASTER_ADMINS = """
			select u.id from users u where u.role = 'MASTER_ADMIN' and u.status = 'ACTIVE' and u.deleted_at is null
			""";

	/**
	 * One row of the page, whatever its kind. Fields that do not apply to the kind are null. {@code direction} is INCOME or EXPENSE;
	 * {@code itemType} is what the money was for (PACKAGE, EXAM, BATCH, CLASS or STAFF), with the package or exam's id and titles;
	 * {@code editable} is true for the payments the office records by hand (16 Nov).
	 */
	public record PaymentRow(String kind, UUID id, LocalDate paidOn, BigDecimal amount, String method, String reference, String note,
			String status, String receiptNo, String personName, UUID batchId, String batchNameEn, String batchNameBn, UUID instructorId,
			String instructorName, String recordedByName, Instant recordedAt, Instant correctedAt, PendingEdit pendingEdit,
			String direction, String itemType, UUID itemId, String itemNameEn, String itemNameBn, BigDecimal refundedAmount,
			boolean editable) {
	}

	/** The values a correction changes. */
	public record Values(BigDecimal amount, LocalDate paidOn, String method, String reference, String note) {
	}

	/** The waiting correction of a row. */
	public record PendingEdit(UUID id, Values after, String reason, String requestedByName, Instant requestedAt) {
	}

	/** One correction, for the waiting list and the history. */
	public record EditView(UUID id, String kind, UUID paymentId, String status, Values before, Values after, String reason,
			String personName, String requestedByName, Instant requestedAt, String decidedByName, Instant decidedAt,
			String decisionReason) {
	}

	/** The filters of the page. Null means no filter. */
	public record Filters(LocalDate from, LocalDate to, String kind, UUID batchId, String q, String direction, String itemType,
			String method, String status, UUID categoryId) {

		/** The filters of the first version of the page: no direction, item, method or status. */
		public Filters(LocalDate from, LocalDate to, String kind, UUID batchId, String q) {
			this(from, to, kind, batchId, q, null, null, null, null, null);
		}
	}

	private final JdbcTemplate jdbc;

	private final OfflineFeeLedger ledger;

	private final InstallmentPlanService installments;

	private final StaffNotificationWriter staff;

	private final AuditRecorder audit;

	private final ObjectMapper json;

	private final Clock clock;

	public PaymentRecordService(JdbcTemplate jdbc, OfflineFeeLedger ledger, InstallmentPlanService installments,
			StaffNotificationWriter staff, AuditRecorder audit, ObjectMapper json, Clock clock) {
		this.jdbc = jdbc;
		this.ledger = ledger;
		this.installments = installments;
		this.staff = staff;
		this.audit = audit;
		this.json = json;
		this.clock = clock;
	}

	// ---- the list ----------------------------------------------------------------------------------------------------------

	private static final String FEE_ROWS = """
			select 'FEE' as kind, p.id, p.paid_on, p.amount, p.method, p.reference, p.note, p.status, p.receipt_no,
			  coalesce(a.full_name, sp.full_name, '') as person_name, a.batch_id, b.name_en as batch_name_en, b.name_bn as batch_name_bn,
			  null::uuid as instructor_id, null::varchar as instructor_name, coalesce(ap.full_name, '') as recorded_by_name,
			  p.recorded_at, p.corrected_at,
			  'BATCH' as item_type, null::uuid as item_id, null::varchar as item_en, null::varchar as item_bn, null::numeric as refunded_amount,
			  null::uuid as item_category
			from offline_fee_payments p
			join offline_admissions a on a.id = p.admission_id
			left join offline_batches b on b.id = a.batch_id
			left join student_profiles sp on sp.user_id = a.student_id
			left join admin_profiles ap on ap.user_id = p.recorded_by
			""";

	private static final String DIRECT_ROWS = """
			select 'DIRECT' as kind, p.id, p.paid_on, p.amount, p.method, p.reference, p.note,
			  case when p.voided_at is null then 'APPROVED' else 'VOIDED' end as status, p.receipt_no,
			  coalesce(sp.full_name, '') as person_name, null::uuid as batch_id, null::varchar as batch_name_en,
			  null::varchar as batch_name_bn, null::uuid as instructor_id, null::varchar as instructor_name,
			  coalesce(ap.full_name, '') as recorded_by_name, p.created_at as recorded_at, p.corrected_at,
			  case when g.scope = 'EXAM' then 'EXAM' else 'PACKAGE' end as item_type, coalesce(g.exam_id, g.module_id) as item_id,
			  coalesce(e.title_en, m.title_en) as item_en, coalesce(e.title_bn, m.title_bn) as item_bn, null::numeric as refunded_amount,
			  coalesce(e.category_id, m.category_id) as item_category
			from live_exam_direct_payments p
			join live_exam_payment_plans pl on pl.id = p.plan_id
			left join live_exam_grants g on g.id = pl.grant_id
			left join live_exam_modules m on m.id = g.module_id
			left join live_exams e on e.id = g.exam_id
			left join student_profiles sp on sp.user_id = pl.student_id
			left join admin_profiles ap on ap.user_id = p.created_by
			""";

	/** Online purchases: the gateway's paid orders, a refunded one shown as REFUNDED with what was given back (16 Nov). */
	private static final String ONLINE_ROWS = """
			select 'ONLINE' as kind, o.id, cast((coalesce(o.paid_at, o.created_at) at time zone 'Asia/Dhaka') as date) as paid_on, o.amount,
			  'ONLINE' as method, o.tran_id as reference, null::varchar as note,
			  case when o.refunded_at is not null then 'REFUNDED' else 'APPROVED' end as status, o.receipt_no,
			  coalesce(sp.full_name, '') as person_name, null::uuid as batch_id, null::varchar as batch_name_en, null::varchar as batch_name_bn,
			  null::uuid as instructor_id, null::varchar as instructor_name, '' as recorded_by_name,
			  coalesce(o.paid_at, o.created_at) as recorded_at, null::timestamptz as corrected_at,
			  case when o.exam_id is not null then 'EXAM' else 'PACKAGE' end as item_type, coalesce(o.exam_id, o.module_id) as item_id,
			  coalesce(e.title_en, m.title_en) as item_en, coalesce(e.title_bn, m.title_bn) as item_bn, o.refund_amount as refunded_amount,
			  coalesce(e.category_id, m.category_id) as item_category
			from live_exam_orders o
			left join live_exam_modules m on m.id = o.module_id
			left join live_exams e on e.id = o.exam_id
			left join student_profiles sp on sp.user_id = o.student_id
			where o.status = 'SUCCEEDED'
			""";

	/** Staff pay: the payments on the staff ledger, a voided one shown as VOIDED (16 Nov). */
	private static final String STAFF_ROWS = """
			select 'STAFF' as kind, l.id, l.paid_on, l.amount, coalesce(l.method, 'CASH') as method, l.reference, l.note,
			  case when l.voided_at is not null then 'VOIDED' else 'APPROVED' end as status, null::varchar as receipt_no,
			  coalesce(sp.full_name, '') as person_name, null::uuid as batch_id, null::varchar as batch_name_en, null::varchar as batch_name_bn,
			  null::uuid as instructor_id, null::varchar as instructor_name, coalesce(cp.full_name, '') as recorded_by_name,
			  l.created_at as recorded_at, null::timestamptz as corrected_at,
			  'STAFF' as item_type, null::uuid as item_id, null::varchar as item_en, null::varchar as item_bn, null::numeric as refunded_amount,
			  null::uuid as item_category
			from staff_ledger l
			left join admin_profiles sp on sp.user_id = l.user_id
			left join admin_profiles cp on cp.user_id = l.created_by
			where l.kind = 'PAYMENT' and l.paid_on is not null
			""";

	/**
	 * The payments the caller may see ({@code kinds}), newest paid first, with any waiting correction. At most 500 rows: the filters
	 * narrow a longer list.
	 */
	@Transactional(readOnly = true)
	public List<PaymentRow> list(Set<String> kinds, Filters filters) {
		List<String> parts = new ArrayList<>();
		for (String kind : List.of(ONLINE, DIRECT, FEE, STAFF)) {
			boolean direction = filters.direction() == null || ("INCOME".equals(filters.direction()) ? INCOME : EXPENSE).contains(kind);
			if (kinds.contains(kind) && (filters.kind() == null || filters.kind().equals(kind)) && direction) {
				parts.add(switch (kind) {
					case ONLINE -> ONLINE_ROWS;
					case DIRECT -> DIRECT_ROWS;
					case FEE -> FEE_ROWS;
					default -> STAFF_ROWS;
				});
			}
		}
		if (parts.isEmpty()) {
			return List.of();
		}
		StringBuilder sql = new StringBuilder("select x.*, pe.id as edit_id, pe.after_values, pe.reason as edit_reason,")
			.append(" coalesce(rp.full_name, '') as edit_by, pe.requested_at as edit_at from (")
			.append(String.join(" union all ", parts))
			.append(") x left join payment_edits pe on pe.kind = x.kind and pe.payment_id = x.id and pe.status = 'PENDING'")
			.append(" left join admin_profiles rp on rp.user_id = pe.requested_by where true");
		List<Object> args = new ArrayList<>();
		if (filters.from() != null) {
			sql.append(" and x.paid_on >= ?");
			args.add(Date.valueOf(filters.from()));
		}
		if (filters.to() != null) {
			sql.append(" and x.paid_on <= ?");
			args.add(Date.valueOf(filters.to()));
		}
		if (filters.batchId() != null) {
			sql.append(" and x.batch_id = ?");
			args.add(filters.batchId());
		}
		if (filters.itemType() != null) {
			sql.append(" and x.item_type = ?");
			args.add(filters.itemType());
		}
		if (filters.categoryId() != null) {
			// 17 Nov: a category takes in its sub-categories, as the catalogue nests them.
			sql.append(" and x.item_category in (with recursive tree(id) as (select ?::uuid union all")
				.append(" select c.id from live_exam_categories c join tree t on c.parent_id = t.id) select id from tree)");
			args.add(filters.categoryId());
		}
		if (filters.method() != null) {
			sql.append(" and x.method = ?");
			args.add(filters.method());
		}
		if (filters.status() != null) {
			sql.append(" and x.status = ?");
			args.add(filters.status());
		}
		if (filters.q() != null && !filters.q().isBlank()) {
			String like = "%" + filters.q().trim().toLowerCase(Locale.ROOT) + "%";
			sql.append(" and (lower(x.person_name) like ? or lower(coalesce(x.reference, '')) like ? or lower(coalesce(x.receipt_no, '')) like ?")
				.append(" or lower(coalesce(x.item_en, '')) like ? or lower(coalesce(x.item_bn, '')) like ?)");
			for (int i = 0; i < 5; i += 1) {
				args.add(like);
			}
		}
		sql.append(" order by x.paid_on desc, x.recorded_at desc limit 500");
		return jdbc.query(sql.toString(), (rs, row) -> row(rs), args.toArray());
	}

	private PaymentRow row(ResultSet rs) throws SQLException {
		UUID editId = rs.getObject("edit_id", UUID.class);
		PendingEdit pending = editId == null ? null
				: new PendingEdit(editId, values(json.readTree(rs.getString("after_values"))), rs.getString("edit_reason"),
						rs.getString("edit_by"), rs.getTimestamp("edit_at").toInstant());
		Timestamp corrected = rs.getTimestamp("corrected_at");
		return new PaymentRow(rs.getString("kind"), rs.getObject("id", UUID.class), rs.getDate("paid_on").toLocalDate(),
				rs.getBigDecimal("amount"), rs.getString("method"), rs.getString("reference"), rs.getString("note"), rs.getString("status"),
				rs.getString("receipt_no"), rs.getString("person_name"), rs.getObject("batch_id", UUID.class), rs.getString("batch_name_en"),
				rs.getString("batch_name_bn"), rs.getObject("instructor_id", UUID.class), rs.getString("instructor_name"),
				rs.getString("recorded_by_name"), rs.getTimestamp("recorded_at").toInstant(),
				corrected == null ? null : corrected.toInstant(), pending, INCOME.contains(rs.getString("kind")) ? "INCOME" : "EXPENSE",
				rs.getString("item_type"), rs.getObject("item_id", UUID.class), rs.getString("item_en"), rs.getString("item_bn"),
				rs.getBigDecimal("refunded_amount"), KINDS.contains(rs.getString("kind")));
	}

	/** The corrections in one status (PENDING for the waiting tab), newest first, for the kinds the caller may see. */
	@Transactional(readOnly = true)
	public List<EditView> edits(Set<String> kinds, String status) {
		// PENDING by default; DECIDED is the history (finalized and rejected).
		String wanted = status == null || status.isBlank() ? "PENDING" : status.trim().toUpperCase(Locale.ROOT);
		boolean decided = "DECIDED".equals(wanted);
		List<EditView> views = new ArrayList<>();
		for (Map<String, Object> row : jdbc.queryForList("""
				select pe.*, coalesce(rp.full_name, '') as requested_by_name, coalesce(dp.full_name, '') as decided_by_name
				from payment_edits pe
				left join admin_profiles rp on rp.user_id = pe.requested_by
				left join admin_profiles dp on dp.user_id = pe.decided_by
				where (? and pe.status <> 'PENDING') or pe.status = ? order by pe.requested_at desc limit 200
				""", decided, wanted)) {
			String kind = (String) row.get("kind");
			if (!kinds.contains(kind)) {
				continue;
			}
			views.add(editView(row));
		}
		return views;
	}

	private EditView editView(Map<String, Object> row) {
		String kind = (String) row.get("kind");
		UUID paymentId = (UUID) row.get("payment_id");
		Timestamp decided = (Timestamp) row.get("decided_at");
		return new EditView((UUID) row.get("id"), kind, paymentId, (String) row.get("status"), values(json.readTree(String.valueOf(row.get(
				"before_values")))), values(json.readTree(String.valueOf(row.get("after_values")))), (String) row.get("reason"),
				personName(kind, paymentId), (String) row.get("requested_by_name"), ((Timestamp) row.get("requested_at")).toInstant(),
				decided == null ? null : (String) row.get("decided_by_name"), decided == null ? null : decided.toInstant(),
				(String) row.get("decision_reason"));
	}

	// ---- correcting ----------------------------------------------------------------------------------------------------------

	/**
	 * Corrects a payment (P2, P3). {@code finalizes} is true for the Master Admin: the change applies at once. Anyone else's waits for the
	 * Master Admin, who is told. A reason is required. Refused for a voided or rejected payment, when nothing changes, and while another
	 * correction of the same payment waits.
	 */
	@Transactional
	public EditView propose(UUID actorId, boolean finalizes, Set<String> kinds, String kindText, UUID paymentId, JsonNode body) {
		String kind = kindOf(kindText, kinds);
		Values before = current(kind, paymentId);
		String status = statusOf(kind, paymentId);
		if ("VOIDED".equals(status) || "REJECTED".equals(status)) {
			throw state("A " + status.toLowerCase(Locale.ROOT) + " payment cannot be corrected.");
		}
		List<FieldErrorDetail> problems = new ArrayList<>();
		Values after = parse(body, before, problems);
		String reason = text(body, "reason");
		if (reason == null || reason.length() > MAX_TEXT) {
			problems.add(new FieldErrorDetail("reason", "Say why the payment is corrected, in up to 500 characters."));
		}
		if (!problems.isEmpty()) {
			throw validation(problems);
		}
		if (same(before, after)) {
			throw validation(List.of(new FieldErrorDetail("amount", "Nothing is changed.")));
		}
		Integer waiting = jdbc.queryForObject("select count(*) from payment_edits where kind = ? and payment_id = ? and status = 'PENDING'",
				Integer.class, kind, paymentId);
		if (waiting != null && waiting > 0) {
			throw state("A correction of this payment is already waiting for the Master Admin.");
		}
		UUID id = UUID.randomUUID();
		Instant now = clock.instant();
		if (finalizes) {
			apply(kind, paymentId, after);
			insert(id, kind, paymentId, "FINALIZED", before, after, reason, actorId, now, actorId, now);
			audit.record(AuditRecorder.PAYMENT_EDIT_FINALIZED, AuditRecorder.PAYMENT_EDIT_ENTITY, id);
		}
		else {
			insert(id, kind, paymentId, "PENDING", before, after, reason, actorId, now, null, null);
			audit.record(AuditRecorder.PAYMENT_EDIT_REQUESTED, AuditRecorder.PAYMENT_EDIT_ENTITY, id);
			Map<String, Object> payload = payload(kind, paymentId, after.amount(), null);
			for (UUID master : jdbc.queryForList(MASTER_ADMINS, UUID.class)) {
				staff.notify(master, StaffNotificationKind.PAYMENT_EDIT_PENDING, payload, PAGE_LINK + "?tab=waiting",
						"PAYMENT_EDIT_PENDING:" + id + ":" + master);
			}
		}
		return edit(id);
	}

	/** Finalizes a waiting correction (Master Admin): the payment takes the new values, and the person who asked is told. */
	@Transactional
	public EditView finalizeEdit(UUID actorId, UUID editId) {
		Map<String, Object> row = waiting(editId);
		String kind = (String) row.get("kind");
		UUID paymentId = (UUID) row.get("payment_id");
		String status = statusOf(kind, paymentId);
		if ("VOIDED".equals(status) || "REJECTED".equals(status)) {
			throw state("The payment is " + status.toLowerCase(Locale.ROOT) + " now, so the correction cannot be applied. Reject it.");
		}
		Values after = values(json.readTree(String.valueOf(row.get("after_values"))));
		apply(kind, paymentId, after);
		jdbc.update("update payment_edits set status = 'FINALIZED', decided_by = ?, decided_at = ? where id = ?", actorId,
				Timestamp.from(clock.instant()), editId);
		audit.record(AuditRecorder.PAYMENT_EDIT_FINALIZED, AuditRecorder.PAYMENT_EDIT_ENTITY, editId);
		staff.notify((UUID) row.get("requested_by"), StaffNotificationKind.PAYMENT_EDIT_FINALIZED, payload(kind, paymentId, after.amount(),
				null), PAGE_LINK, "PAYMENT_EDIT_FINALIZED:" + editId);
		return edit(editId);
	}

	/** Rejects a waiting correction (Master Admin), with a reason. The payment is unchanged, and the person who asked is told. */
	@Transactional
	public EditView rejectEdit(UUID actorId, UUID editId, String reasonText) {
		String reason = reasonText == null ? null : reasonText.trim();
		if (reason == null || reason.isEmpty() || reason.length() > MAX_TEXT) {
			throw validation(List.of(new FieldErrorDetail("reason", "Give a reason of 1 to 500 characters.")));
		}
		Map<String, Object> row = waiting(editId);
		jdbc.update("update payment_edits set status = 'REJECTED', decided_by = ?, decided_at = ?, decision_reason = ? where id = ?",
				actorId, Timestamp.from(clock.instant()), reason, editId);
		audit.record(AuditRecorder.PAYMENT_EDIT_REJECTED, AuditRecorder.PAYMENT_EDIT_ENTITY, editId);
		String kind = (String) row.get("kind");
		UUID paymentId = (UUID) row.get("payment_id");
		Values after = values(json.readTree(String.valueOf(row.get("after_values"))));
		staff.notify((UUID) row.get("requested_by"), StaffNotificationKind.PAYMENT_EDIT_REJECTED,
				payload(kind, paymentId, after.amount(), StaffNotificationWriter.fitText(reason)), PAGE_LINK, "PAYMENT_EDIT_REJECTED:" + editId);
		return edit(editId);
	}

	/** The kinds of payment an edit may touch, for the controller to check against who is asking. */
	String kindOfEdit(UUID editId) {
		return jdbc.queryForList("select kind from payment_edits where id = ?", String.class, editId).stream().findFirst()
			.orElseThrow(OfflineSupport::notFound);
	}

	// ---- applying a correction -------------------------------------------------------------------------------------------

	private void apply(String kind, UUID paymentId, Values after) {
		switch (kind) {
			case FEE -> applyFee(paymentId, after);
			case DIRECT -> installments.correctPayment(paymentId, after.amount(), after.paidOn(), after.method(), after.reference(), after.note());
			default -> throw OfflineSupport.notFound();
		}
	}

	/**
	 * A course fee payment. An approved one is matched again with its new amount: after onboarding to the open instalments, no more than
	 * is owed once its old amount is taken back; before onboarding no more than the course fee less the other approved payments. The
	 * receipt keeps its number (P4).
	 */
	private void applyFee(UUID paymentId, Values after) {
		UUID admissionId = jdbc.queryForObject("select admission_id from offline_fee_payments where id = ?", UUID.class, paymentId);
		OfflineFeeLedger.Admission admission = ledger.lock(admissionId);
		Map<String, Object> payment = jdbc.queryForMap("select status, amount from offline_fee_payments where id = ? for update", paymentId);
		if (OfflineFeePayment.STATUS_APPROVED.equals(payment.get("status"))) {
			BigDecimal oldAmount = (BigDecimal) payment.get("amount");
			boolean scheduled = OfflineAdmission.ONBOARDED.equals(admission.status()) || OfflineAdmission.CLOSED.equals(admission.status());
			if (scheduled) {
				jdbc.update("delete from offline_fee_allocations where payment_id = ?", paymentId);
				BigDecimal owed = ledger.owed(admissionId);
				if (after.amount().compareTo(owed) > 0) {
					throw tooMuch(owed);
				}
			}
			else {
				BigDecimal room = ledger.owed(admissionId).add(oldAmount);
				if (after.amount().compareTo(room) > 0) {
					throw tooMuch(room);
				}
			}
			update("offline_fee_payments", paymentId, after);
			if (scheduled) {
				ledger.match(paymentId, after.amount(), ledger.open(admissionId));
			}
			return;
		}
		update("offline_fee_payments", paymentId, after);
	}

	private void update(String table, UUID paymentId, Values after) {
		jdbc.update("update " + table + " set amount = ?, paid_on = ?, method = ?, reference = ?, note = ?, corrected_at = ? where id = ?",
				after.amount(), Date.valueOf(after.paidOn()), after.method(), after.reference(), after.note(),
				Timestamp.from(clock.instant()), paymentId);
	}

	// ---- helpers -----------------------------------------------------------------------------------------------------------

	private static String kindOf(String text, Set<String> kinds) {
		String kind = text == null ? "" : text.trim().toUpperCase(Locale.ROOT);
		if (!KINDS.contains(kind)) {
			throw OfflineSupport.notFound();
		}
		if (!kinds.contains(kind)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
		return kind;
	}

	private static String table(String kind) {
		return switch (kind) {
			case FEE -> "offline_fee_payments";
			case DIRECT -> "live_exam_direct_payments";
			default -> throw OfflineSupport.notFound();
		};
	}

	private Values current(String kind, UUID paymentId) {
		List<Values> found = jdbc.query("select amount, paid_on, method, reference, note from " + table(kind) + " where id = ?",
				(rs, row) -> new Values(rs.getBigDecimal("amount"), rs.getDate("paid_on").toLocalDate(), rs.getString("method"),
						rs.getString("reference"), rs.getString("note")), paymentId);
		if (found.isEmpty()) {
			throw OfflineSupport.notFound();
		}
		return found.get(0);
	}

	private String statusOf(String kind, UUID paymentId) {
		if (DIRECT.equals(kind)) {
			return Boolean.TRUE.equals(jdbc.queryForObject("select voided_at is not null from live_exam_direct_payments where id = ?",
					Boolean.class, paymentId)) ? "VOIDED" : "APPROVED";
		}
		return jdbc.queryForObject("select status from " + table(kind) + " where id = ?", String.class, paymentId);
	}

	private String personName(String kind, UUID paymentId) {
		String sql = switch (kind) {
			case FEE -> """
					select coalesce(a.full_name, sp.full_name, '') from offline_fee_payments p join offline_admissions a on a.id = p.admission_id
					left join student_profiles sp on sp.user_id = a.student_id where p.id = ?""";
			default -> """
					select coalesce(sp.full_name, '') from live_exam_direct_payments p join live_exam_payment_plans pl on pl.id = p.plan_id
					left join student_profiles sp on sp.user_id = pl.student_id where p.id = ?""";
		};
		return jdbc.queryForList(sql, String.class, paymentId).stream().findFirst().orElse("");
	}

	/** The new values, each defaulting to the current one when the body leaves it out. Checked as a recording is. */
	private Values parse(JsonNode body, Values before, List<FieldErrorDetail> problems) {
		BigDecimal amount = before.amount();
		if (body != null && body.hasNonNull("amount")) {
			try {
				amount = new BigDecimal(body.get("amount").asText().trim());
				if (amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2) {
					problems.add(new FieldErrorDetail("amount", "The amount must be more than zero, with at most two decimals."));
				}
				amount = amount.setScale(2, RoundingMode.HALF_UP);
			}
			catch (NumberFormatException exception) {
				problems.add(new FieldErrorDetail("amount", "Enter the amount as a number."));
			}
		}
		LocalDate paidOn = before.paidOn();
		if (body != null && body.hasNonNull("paidOn")) {
			try {
				paidOn = LocalDate.parse(body.get("paidOn").asText().trim());
				if (paidOn.isAfter(OfflineFeeSchedule.today(clock.instant()))) {
					problems.add(new FieldErrorDetail("paidOn", "The payment date cannot be in the future."));
				}
			}
			catch (java.time.format.DateTimeParseException exception) {
				problems.add(new FieldErrorDetail("paidOn", "Enter the date as yyyy-MM-dd."));
			}
		}
		String method = body != null && body.hasNonNull("method") ? body.get("method").asText().trim().toUpperCase(Locale.ROOT)
				: before.method();
		if (!METHODS.contains(method)) {
			problems.add(new FieldErrorDetail("method", "Choose a payment method."));
		}
		String reference = body != null && body.has("reference") ? text(body, "reference") : before.reference();
		if (reference == null || !REFERENCE.matcher(reference).matches()) {
			problems.add(new FieldErrorDetail("reference", "A reference is required: 1 to 100 printable characters, no accents."));
		}
		String note = body != null && body.has("note") ? text(body, "note") : before.note();
		if (note != null && note.length() > MAX_TEXT) {
			problems.add(new FieldErrorDetail("note", "The note is at most 500 characters."));
		}
		return new Values(amount, paidOn, method, reference, note);
	}

	private static boolean same(Values a, Values b) {
		return a.amount().compareTo(b.amount()) == 0 && a.paidOn().equals(b.paidOn()) && a.method().equals(b.method())
				&& java.util.Objects.equals(a.reference(), b.reference()) && java.util.Objects.equals(a.note(), b.note());
	}

	private static String text(JsonNode body, String key) {
		if (body == null || !body.hasNonNull(key)) {
			return null;
		}
		String value = body.get(key).asText().trim();
		return value.isEmpty() ? null : value;
	}

	private String valuesJson(Values values) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("amount", values.amount().toPlainString());
		map.put("paidOn", values.paidOn().toString());
		map.put("method", values.method());
		map.put("reference", values.reference());
		map.put("note", values.note());
		return json.writeValueAsString(map);
	}

	private static Values values(JsonNode node) {
		return new Values(new BigDecimal(node.get("amount").asText()), LocalDate.parse(node.get("paidOn").asText()),
				node.get("method").asText(), node.hasNonNull("reference") ? node.get("reference").asText() : null,
				node.hasNonNull("note") ? node.get("note").asText() : null);
	}

	private void insert(UUID id, String kind, UUID paymentId, String status, Values before, Values after, String reason, UUID requestedBy,
			Instant requestedAt, UUID decidedBy, Instant decidedAt) {
		jdbc.update("""
				insert into payment_edits (id, kind, payment_id, status, before_values, after_values, reason, requested_by, requested_at,
				  decided_by, decided_at) values (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?)
				""", id, kind, paymentId, status, valuesJson(before), valuesJson(after), reason, requestedBy, Timestamp.from(requestedAt),
				decidedBy, decidedAt == null ? null : Timestamp.from(decidedAt));
	}

	private Map<String, Object> waiting(UUID editId) {
		List<Map<String, Object>> rows = jdbc.queryForList("select * from payment_edits where id = ? for update", editId);
		if (rows.isEmpty()) {
			throw OfflineSupport.notFound();
		}
		if (!"PENDING".equals(rows.get(0).get("status"))) {
			throw state("This correction is already " + String.valueOf(rows.get(0).get("status")).toLowerCase(Locale.ROOT) + ".");
		}
		// An instructor payment correction (CLASS or OTHER) is no longer decided here: it is corrected in Accounting (R3).
		if (!KINDS.contains(String.valueOf(rows.get(0).get("kind")))) {
			throw state("This correction is for an instructor payment, which is now corrected in Accounting.");
		}
		return rows.get(0);
	}

	private EditView edit(UUID id) {
		Map<String, Object> row = jdbc.queryForMap("""
				select pe.*, coalesce(rp.full_name, '') as requested_by_name, coalesce(dp.full_name, '') as decided_by_name
				from payment_edits pe left join admin_profiles rp on rp.user_id = pe.requested_by
				left join admin_profiles dp on dp.user_id = pe.decided_by where pe.id = ?
				""", id);
		return editView(row);
	}

	private Map<String, Object> payload(String kind, UUID paymentId, BigDecimal amount, String reason) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("personName", personName(kind, paymentId));
		payload.put("amount", amount.toPlainString());
		if (reason != null) {
			payload.put("reason", reason);
		}
		return payload;
	}

	private static ApiException tooMuch(BigDecimal limit) {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "The amount is more than is owed.",
				List.of(new FieldErrorDetail("amount", "The most this payment can be is BDT " + limit.toPlainString() + ".")));
	}

	private static ApiException state(String message) {
		return new ApiException(ErrorCode.ADMISSION_STATE, HttpStatus.CONFLICT, message);
	}

	private static ApiException validation(List<FieldErrorDetail> problems) {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.", problems);
	}

}
