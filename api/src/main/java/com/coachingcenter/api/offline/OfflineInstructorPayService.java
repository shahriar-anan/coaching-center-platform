package com.coachingcenter.api.offline;

import static com.coachingcenter.api.offline.OfflineSupport.notFound;
import static com.coachingcenter.api.offline.OfflineSupport.state;
import static com.coachingcenter.api.offline.OfflineSupport.validation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.FieldErrorDetail;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Payments to offline instructors (24 Nov, instructor-pay-simple-plan.md). Pay no longer comes from the classes: the Master Admin records
 * each payment by hand, with what it was for (a lecture, an exam or something else), how many classes it covers (for the record only),
 * the amount, method, reference, an optional batch and a description. It counts at once.
 *
 * <p>New payments are rows of {@code offline_instructor_other_payments} (kind OTHER in Payment records and its corrections). The class
 * payments made before (kind CLASS, {@code offline_class_payments}) are read with them, so an instructor's history and Finance stay whole;
 * they can be corrected (date, amount, method, reference, note) and voided, never recorded anew.
 */
@Service
public class OfflineInstructorPayService {

	/** A payment of the new kind, in its own table. The same name Payment records and corrections use. */
	public static final String OTHER = PaymentRecordService.OTHER;

	/** A class payment made before 24 Nov. */
	public static final String CLASS = PaymentRecordService.CLASS;

	public static final String ENTITY = "OFFLINE_OTHER_PAYMENT";

	public static final String RECORDED = "OFFLINE_OTHER_PAYMENT_RECORDED";

	public static final String VOIDED = "OFFLINE_OTHER_PAYMENT_VOIDED";

	public static final String CLASS_PAYMENT_ENTITY = "OFFLINE_CLASS_PAYMENT";

	public static final String CLASS_PAYMENT_VOIDED = "OFFLINE_CLASS_PAYMENT_VOIDED";

	static final Set<String> KINDS = Set.of("LECTURE", "EXAM", "OTHER");

	private static final Set<String> METHODS = Set.of("CASH", "BKASH", "NAGAD", "ROCKET", "BANK");

	private static final Pattern REFERENCE = Pattern.compile("[ -~]{1,100}");

	private static final int MAX_TEXT = 500;

	private static final int MAX_CLASSES = 200;

	private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000");

	private static final int MAX_ROWS = 1000;

	/** Both tables in one shape. A class payment's type is its class's kind, one class, and the class's subject and date. */
	private static final String ROWS = """
			select 'OTHER' as record_kind, p.id, p.instructor_id, i.name as instructor_name, p.kind, p.class_count, p.batch_id,
			  b.name_en as batch_name_en, b.name_bn as batch_name_bn, p.reason as description, null::uuid as class_id, null::date as class_on,
			  p.amount, p.method, p.reference, p.paid_on, p.note, p.status, coalesce(ap.full_name, '') as recorded_by_name, p.recorded_at,
			  p.void_reason, p.corrected_at
			from offline_instructor_other_payments p
			join offline_instructors i on i.id = p.instructor_id
			left join offline_batches b on b.id = p.batch_id
			left join admin_profiles ap on ap.user_id = p.recorded_by
			union all
			select 'CLASS' as record_kind, p.id, p.instructor_id, i.name as instructor_name, c.kind, 1 as class_count, c.batch_id,
			  b.name_en as batch_name_en, b.name_bn as batch_name_bn, c.subject as description, c.id as class_id, c.held_on as class_on,
			  p.amount, p.method, p.reference, p.paid_on, p.note, p.status, coalesce(ap.full_name, '') as recorded_by_name, p.recorded_at,
			  p.void_reason, p.corrected_at
			from offline_class_payments p
			join offline_classes c on c.id = p.class_id
			join offline_instructors i on i.id = p.instructor_id
			left join offline_batches b on b.id = c.batch_id
			left join admin_profiles ap on ap.user_id = p.recorded_by
			""";

	/**
	 * One payment as the screens show it. {@code recordKind} is OTHER (recorded by hand) or CLASS (a class payment from before 24 Nov,
	 * with its class); {@code kind} is LECTURE, EXAM or OTHER; {@code description} is what the admin typed, or the class's subject.
	 */
	public record PaymentView(String recordKind, UUID id, UUID instructorId, String instructorName, String kind, Integer classCount,
			UUID batchId, String batchNameEn, String batchNameBn, String description, UUID classId, LocalDate classOn, BigDecimal amount,
			String method, String reference, LocalDate paidOn, String note, String status, String recordedByName, Instant recordedAt,
			String voidReason, Instant correctedAt) {
	}

	/** A list of payments and the total of the approved ones in it. */
	public record PaymentList(List<PaymentView> items, BigDecimal total, boolean truncated) {
	}

	/** A batch's two names. */
	public record BatchName(UUID id, String nameEn, String nameBn) {
	}

	/** The figures at the top of an instructor's page. Approved payments only. */
	public record PaySummary(BigDecimal totalPaid, BigDecimal paidThisMonth, LocalDate lastPaidOn, BigDecimal lastAmount, long payments,
			List<BatchName> batches) {
	}

	/** One summary card: the approved amount and how many payments make it. */
	public record Card(BigDecimal amount, long payments) {
	}

	/** The cards of the Instructor payments page. */
	public record Cards(Card thisMonth, Card lastMonth, Card thisYear) {
	}

	/** What a list is narrowed to. Every field may be null. */
	public record Filters(UUID instructorId, LocalDate from, LocalDate to, UUID batchId, String kind, Set<String> statuses) {
	}

	/** One instructor's figures for the instructors list: paid this month, the last payment, and the batches they teach. */
	public record ListFigures(BigDecimal paidThisMonth, LocalDate lastPaidOn, List<BatchName> batches) {
	}

	private final JdbcTemplate jdbc;

	private final AuditRecorder audit;

	private final Clock clock;

	private final ObjectMapper json;

	public OfflineInstructorPayService(JdbcTemplate jdbc, AuditRecorder audit, Clock clock, ObjectMapper json) {
		this.jdbc = jdbc;
		this.audit = audit;
		this.clock = clock;
		this.json = json;
	}

	// ---- reading -------------------------------------------------------------------------------------------------------

	/** An instructor's payments, every status, newest first (the Payment history tab). */
	@Transactional(readOnly = true)
	public PaymentList history(UUID instructorId, LocalDate from, LocalDate to, String kind) {
		requireInstructor(instructorId);
		return list(new Filters(instructorId, from, to, null, kind, null));
	}

	/** Payments across instructors (the Instructor payments page): approved and voided, newest first. */
	@Transactional(readOnly = true)
	public PaymentList all(UUID instructorId, LocalDate from, LocalDate to, UUID batchId, String kind) {
		return list(new Filters(instructorId, from, to, batchId, kind, Set.of("APPROVED", "VOIDED")));
	}

	private PaymentList list(Filters filters) {
		StringBuilder sql = new StringBuilder("select x.* from (").append(ROWS).append(") x where true");
		List<Object> args = new ArrayList<>();
		if (filters.instructorId() != null) {
			sql.append(" and x.instructor_id = ?");
			args.add(filters.instructorId());
		}
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
		String kind = filters.kind() == null || filters.kind().isBlank() ? null : filters.kind().trim().toUpperCase(Locale.ROOT);
		if (kind != null) {
			if (!KINDS.contains(kind)) {
				throw validation(List.of(new FieldErrorDetail("kind", "Choose lecture, exam or other.")));
			}
			sql.append(" and x.kind = ?");
			args.add(kind);
		}
		if (filters.statuses() != null) {
			sql.append(" and x.status in (").append(String.join(", ", filters.statuses().stream().map(status -> "?").toList())).append(")");
			args.addAll(filters.statuses());
		}
		sql.append(" order by x.paid_on desc, x.recorded_at desc limit ").append(MAX_ROWS + 1);
		List<PaymentView> rows = jdbc.query(sql.toString(), (rs, row) -> view(rs), args.toArray());
		boolean truncated = rows.size() > MAX_ROWS;
		List<PaymentView> items = truncated ? rows.subList(0, MAX_ROWS) : rows;
		BigDecimal total = items.stream().filter(item -> "APPROVED".equals(item.status())).map(PaymentView::amount)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		return new PaymentList(List.copyOf(items), total, truncated);
	}

	/** The figures at the top of an instructor's page (D14). */
	@Transactional(readOnly = true)
	public PaySummary summary(UUID instructorId) {
		requireInstructor(instructorId);
		LocalDate today = today();
		LocalDate monthStart = YearMonth.from(today).atDay(1);
		Map<String, Object> totals = jdbc.queryForMap("select coalesce(sum(x.amount), 0) as total, count(*) as payments,"
				+ " coalesce(sum(x.amount) filter (where x.paid_on >= ?), 0) as month from (" + ROWS + ") x"
				+ " where x.instructor_id = ? and x.status = 'APPROVED'", Date.valueOf(monthStart), instructorId);
		List<Map<String, Object>> last = jdbc.queryForList("select x.paid_on, x.amount from (" + ROWS + ") x"
				+ " where x.instructor_id = ? and x.status = 'APPROVED' order by x.paid_on desc, x.recorded_at desc limit 1", instructorId);
		List<BatchName> batches = jdbc.query("""
				select b.id, b.name_en, b.name_bn from offline_batches b
				where exists (select 1 from offline_classes c where c.batch_id = b.id and c.instructor_id = ? and c.status <> 'CANCELLED')
				order by b.name_en
				""", (rs, row) -> new BatchName(rs.getObject("id", UUID.class), rs.getString("name_en"), rs.getString("name_bn")),
				instructorId);
		return new PaySummary((BigDecimal) totals.get("total"), (BigDecimal) totals.get("month"),
				last.isEmpty() ? null : ((Date) last.get(0).get("paid_on")).toLocalDate(),
				last.isEmpty() ? null : (BigDecimal) last.get(0).get("amount"), ((Number) totals.get("payments")).longValue(), batches);
	}

	/** The cards of the Instructor payments page: this month, last month and this year (D13). Approved payments only. */
	@Transactional(readOnly = true)
	public Cards cards() {
		LocalDate today = today();
		YearMonth month = YearMonth.from(today);
		return new Cards(card(month.atDay(1), month.atEndOfMonth()), card(month.minusMonths(1).atDay(1), month.minusMonths(1).atEndOfMonth()),
				card(LocalDate.of(today.getYear(), 1, 1), LocalDate.of(today.getYear(), 12, 31)));
	}

	private Card card(LocalDate from, LocalDate to) {
		Map<String, Object> row = jdbc.queryForMap("select coalesce(sum(x.amount), 0) as amount, count(*) as payments from (" + ROWS
				+ ") x where x.status = 'APPROVED' and x.paid_on between ? and ?", Date.valueOf(from), Date.valueOf(to));
		return new Card((BigDecimal) row.get("amount"), ((Number) row.get("payments")).longValue());
	}

	/**
	 * Each instructor's paid-this-month, last payment date and batches (those with a class of theirs that is not cancelled), for the
	 * instructors list. An instructor with neither a payment nor a class is left out.
	 */
	@Transactional(readOnly = true)
	public Map<UUID, ListFigures> listFigures() {
		LocalDate monthStart = YearMonth.from(today()).atDay(1);
		Map<UUID, BigDecimal> month = new LinkedHashMap<>();
		Map<UUID, LocalDate> last = new LinkedHashMap<>();
		jdbc.query("select x.instructor_id, coalesce(sum(x.amount) filter (where x.paid_on >= ?), 0) as month, max(x.paid_on) as last_paid"
				+ " from (" + ROWS + ") x where x.status = 'APPROVED' group by x.instructor_id", rs -> {
					UUID id = rs.getObject("instructor_id", UUID.class);
					Date lastPaid = rs.getDate("last_paid");
					month.put(id, rs.getBigDecimal("month"));
					last.put(id, lastPaid == null ? null : lastPaid.toLocalDate());
				}, Date.valueOf(monthStart));
		Map<UUID, List<BatchName>> batches = new LinkedHashMap<>();
		jdbc.query("""
				select distinct c.instructor_id, b.id, b.name_en, b.name_bn from offline_classes c join offline_batches b on b.id = c.batch_id
				where c.instructor_id is not null and c.status <> 'CANCELLED' order by b.name_en
				""", rs -> {
					batches.computeIfAbsent(rs.getObject("instructor_id", UUID.class), key -> new ArrayList<>())
						.add(new BatchName(rs.getObject("id", UUID.class), rs.getString("name_en"), rs.getString("name_bn")));
				});
		Set<UUID> ids = new java.util.LinkedHashSet<>(month.keySet());
		ids.addAll(batches.keySet());
		Map<UUID, ListFigures> figures = new LinkedHashMap<>();
		for (UUID id : ids) {
			figures.put(id, new ListFigures(month.getOrDefault(id, BigDecimal.ZERO), last.get(id),
					List.copyOf(batches.getOrDefault(id, List.of()))));
		}
		return figures;
	}

	// ---- writing -------------------------------------------------------------------------------------------------------

	/** Records a payment to the instructor (3.1). It counts at once. */
	@Transactional
	public PaymentView record(UUID instructorId, UUID actorId, JsonNode body) {
		requireInstructor(instructorId);
		List<FieldErrorDetail> problems = new ArrayList<>();
		Fields fields = fields(body, null, problems);
		if (!problems.isEmpty()) {
			throw validation(problems);
		}
		UUID id = UUID.randomUUID();
		Instant now = clock.instant();
		jdbc.update("""
				insert into offline_instructor_other_payments (id, instructor_id, batch_id, kind, class_count, reason, amount, method,
				  reference, paid_on, note, status, recorded_by, recorded_at, decided_by, decided_at)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'APPROVED', ?, ?, ?, ?)
				""", id, instructorId, fields.batchId(), fields.kind(), fields.classCount(), fields.description(), fields.amount(),
				fields.method(), fields.reference(), Date.valueOf(fields.paidOn()), fields.note(), actorId, Timestamp.from(now), actorId,
				Timestamp.from(now));
		audit.record(RECORDED, ENTITY, id);
		return find(OTHER, id);
	}

	/**
	 * Corrects a payment (D9). It applies at once, is marked corrected, and is kept as a finalized correction in Payment records with
	 * its reason. A payment recorded by hand may change any field; a class payment from before only its date, amount, method, reference
	 * and note. Refused for a voided or rejected payment, when nothing changes, and while a correction of it waits for approval.
	 */
	@Transactional
	public PaymentView edit(String recordKindText, UUID id, UUID actorId, JsonNode body) {
		String recordKind = recordKind(recordKindText);
		PaymentView before = lockAndFind(recordKind, id);
		if (!"APPROVED".equals(before.status())) {
			throw state("Only a paid payment can be corrected. This one is " + before.status().toLowerCase(Locale.ROOT) + ".");
		}
		Integer waiting = jdbc.queryForObject("select count(*) from payment_edits where kind = ? and payment_id = ? and status = 'PENDING'",
				Integer.class, recordKind, id);
		if (waiting != null && waiting > 0) {
			throw state("A correction of this payment is waiting in Payment records. Finalize or reject it first.");
		}
		List<FieldErrorDetail> problems = new ArrayList<>();
		Fields after = fields(body, before, problems);
		String editReason = text(body, "editReason");
		if (editReason == null || editReason.length() > MAX_TEXT) {
			problems.add(new FieldErrorDetail("editReason", "Say why the payment is corrected, in up to 500 characters."));
		}
		if (!problems.isEmpty()) {
			throw validation(problems);
		}
		Fields old = Fields.of(before);
		if (CLASS.equals(recordKind)) {
			// A class payment's type, classes, batch and description come from its class.
			after = new Fields(old.kind(), old.classCount(), old.batchId(), old.description(), after.amount(), after.method(),
					after.reference(), after.paidOn(), after.note());
		}
		if (after.equals(old)) {
			throw validation(List.of(new FieldErrorDetail("amount", "Nothing is changed.")));
		}
		Instant now = clock.instant();
		if (OTHER.equals(recordKind)) {
			jdbc.update("""
					update offline_instructor_other_payments set kind = ?, class_count = ?, batch_id = ?, reason = ?, amount = ?, method = ?,
					  reference = ?, paid_on = ?, note = ?, corrected_at = ? where id = ?
					""", after.kind(), after.classCount(), after.batchId(), after.description(), after.amount(), after.method(),
					after.reference(), Date.valueOf(after.paidOn()), after.note(), Timestamp.from(now), id);
		}
		else {
			jdbc.update("""
					update offline_class_payments set amount = ?, method = ?, reference = ?, paid_on = ?, note = ?, corrected_at = ?
					where id = ?
					""", after.amount(), after.method(), after.reference(), Date.valueOf(after.paidOn()), after.note(), Timestamp.from(now), id);
		}
		UUID editId = UUID.randomUUID();
		jdbc.update("""
				insert into payment_edits (id, kind, payment_id, status, before_values, after_values, reason, requested_by, requested_at,
				  decided_by, decided_at) values (?, ?, ?, 'FINALIZED', ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?)
				""", editId, recordKind, id, valuesJson(old), valuesJson(after), editReason, actorId, Timestamp.from(now), actorId,
				Timestamp.from(now));
		audit.record(AuditRecorder.PAYMENT_EDIT_FINALIZED, AuditRecorder.PAYMENT_EDIT_ENTITY, editId);
		return find(recordKind, id);
	}

	/** Voids a paid payment made by mistake, with a reason (D16). It leaves Finance and the figures; it cannot be edited again. */
	@Transactional
	public PaymentView voidPayment(String recordKindText, UUID id, UUID actorId, JsonNode body) {
		String recordKind = recordKind(recordKindText);
		PaymentView payment = lockAndFind(recordKind, id);
		if (!"APPROVED".equals(payment.status())) {
			throw state("Only a paid payment can be voided. This one is " + payment.status().toLowerCase(Locale.ROOT) + ".");
		}
		String reason = text(body, "reason");
		if (reason == null || reason.length() > MAX_TEXT) {
			throw validation(List.of(new FieldErrorDetail("reason", "Give a reason of 1 to 500 characters.")));
		}
		Instant now = clock.instant();
		jdbc.update("update " + table(recordKind) + " set status = 'VOIDED', voided_by = ?, voided_at = ?, void_reason = ? where id = ?",
				actorId, Timestamp.from(now), reason, id);
		if (CLASS.equals(recordKind)) {
			// The class is no longer paid, so it can be changed again, as a void did before 24 Nov.
			jdbc.update("update offline_classes set status = 'SCHEDULED', status_by = ?, status_at = ?, updated_at = ? where id = ? and status = 'PAID'",
					actorId, Timestamp.from(now), Timestamp.from(now), payment.classId());
		}
		audit.record(OTHER.equals(recordKind) ? VOIDED : CLASS_PAYMENT_VOIDED, OTHER.equals(recordKind) ? ENTITY : CLASS_PAYMENT_ENTITY, id);
		return find(recordKind, id);
	}

	// ---- fields --------------------------------------------------------------------------------------------------------

	/** The values of a payment that can be recorded or corrected. */
	private record Fields(String kind, Integer classCount, UUID batchId, String description, BigDecimal amount, String method,
			String reference, LocalDate paidOn, String note) {

		static Fields of(PaymentView view) {
			return new Fields(view.kind(), view.classCount(), view.batchId(), view.description(), view.amount(), view.method(),
					view.reference(), view.paidOn(), view.note());
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof Fields that && Objects.equals(kind, that.kind) && Objects.equals(classCount, that.classCount)
					&& Objects.equals(batchId, that.batchId) && Objects.equals(description, that.description)
					&& amount.compareTo(that.amount) == 0 && Objects.equals(method, that.method) && Objects.equals(reference, that.reference)
					&& Objects.equals(paidOn, that.paidOn) && Objects.equals(note, that.note);
		}

		@Override
		public int hashCode() {
			return Objects.hash(kind, classCount, batchId, description, amount.stripTrailingZeros(), method, reference, paidOn, note);
		}

	}

	/**
	 * The fields of a recording (when {@code before} is null) or a correction (each left out keeps its value). Checked as in 3.1: a
	 * date not after today, a type, classes 1 to 200, an amount above zero, a method, and a description for OTHER.
	 */
	private Fields fields(JsonNode body, PaymentView before, List<FieldErrorDetail> problems) {
		boolean editing = before != null;
		LocalDate paidOn = editing ? before.paidOn() : null;
		if (!editing || has(body, "paidOn")) {
			String raw = text(body, "paidOn");
			paidOn = null;
			if (raw == null) {
				problems.add(new FieldErrorDetail("paidOn", "Enter the date it was paid."));
			}
			else {
				try {
					paidOn = LocalDate.parse(raw);
					if (paidOn.isAfter(today())) {
						problems.add(new FieldErrorDetail("paidOn", "The date can't be after today."));
					}
				}
				catch (DateTimeParseException invalid) {
					problems.add(new FieldErrorDetail("paidOn", "Enter the date as yyyy-MM-dd."));
				}
			}
		}
		String kind = editing ? before.kind() : null;
		if (!editing || has(body, "kind")) {
			String raw = text(body, "kind");
			kind = raw == null ? null : raw.toUpperCase(Locale.ROOT);
			if (kind == null || !KINDS.contains(kind)) {
				problems.add(new FieldErrorDetail("kind", "Choose lecture, exam or other."));
			}
		}
		Integer classCount = editing ? before.classCount() : null;
		if (!editing || has(body, "classCount")) {
			classCount = null;
			String raw = text(body, "classCount");
			if (raw != null) {
				try {
					classCount = Integer.valueOf(raw);
					if (classCount < 1 || classCount > MAX_CLASSES) {
						problems.add(new FieldErrorDetail("classCount", "Enter a number of classes from 1 to " + MAX_CLASSES + ", or leave it empty."));
					}
				}
				catch (NumberFormatException invalid) {
					problems.add(new FieldErrorDetail("classCount", "Enter the number of classes as a whole number."));
				}
			}
		}
		BigDecimal amount = editing ? before.amount() : null;
		if (!editing || has(body, "amount")) {
			amount = null;
			String raw = text(body, "amount");
			try {
				amount = raw == null ? null : new BigDecimal(raw);
			}
			catch (NumberFormatException invalid) {
				amount = null;
			}
			if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2 || amount.compareTo(MAX_AMOUNT) > 0) {
				problems.add(new FieldErrorDetail("amount", "Enter an amount above zero and up to 10,00,000, with at most two decimals."));
				amount = null;
			}
			else {
				amount = amount.setScale(2, RoundingMode.HALF_UP);
			}
		}
		String method = editing ? before.method() : null;
		if (!editing || has(body, "method")) {
			String raw = text(body, "method");
			method = raw == null ? "" : raw.toUpperCase(Locale.ROOT);
			if (!METHODS.contains(method)) {
				problems.add(new FieldErrorDetail("method", "Choose a payment method."));
			}
		}
		String reference = editing ? before.reference() : null;
		if (!editing || has(body, "reference")) {
			reference = text(body, "reference");
			if (reference != null && !REFERENCE.matcher(reference).matches()) {
				problems.add(new FieldErrorDetail("reference", "A reference is up to 100 printable characters, no accents."));
			}
		}
		UUID batchId = editing ? before.batchId() : null;
		if (!editing || has(body, "batchId")) {
			batchId = null;
			String raw = text(body, "batchId");
			if (raw != null) {
				try {
					batchId = UUID.fromString(raw);
					if (!Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from offline_batches where id = ?)", Boolean.class,
							batchId))) {
						problems.add(new FieldErrorDetail("batchId", "Choose a batch from the list."));
					}
				}
				catch (IllegalArgumentException invalid) {
					problems.add(new FieldErrorDetail("batchId", "Choose a batch from the list."));
				}
			}
		}
		String description = editing ? before.description() : null;
		if (!editing || has(body, "description")) {
			description = text(body, "description");
			if (description != null && description.length() > MAX_TEXT) {
				problems.add(new FieldErrorDetail("description", "The description is at most 500 characters."));
			}
		}
		if ("OTHER".equals(kind) && description == null && !(editing && CLASS.equals(before.recordKind()))) {
			problems.add(new FieldErrorDetail("description", "Say what the payment is for."));
		}
		String note = editing ? before.note() : null;
		if (!editing || has(body, "note")) {
			note = text(body, "note");
			if (note != null && note.length() > MAX_TEXT) {
				problems.add(new FieldErrorDetail("note", "The note is at most 500 characters."));
			}
		}
		return new Fields(kind, classCount, batchId, description, amount, method, reference, paidOn, note);
	}

	/** The values a correction keeps, as Payment records reads them (amount, paidOn, method, reference, note) and the rest after them. */
	private String valuesJson(Fields values) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("amount", values.amount().toPlainString());
		map.put("paidOn", values.paidOn().toString());
		map.put("method", values.method());
		map.put("reference", values.reference());
		map.put("note", values.note());
		map.put("kind", values.kind());
		map.put("classCount", values.classCount());
		map.put("batchId", values.batchId() == null ? null : values.batchId().toString());
		map.put("description", values.description());
		return json.writeValueAsString(map);
	}

	// ---- helpers -------------------------------------------------------------------------------------------------------

	private PaymentView find(String recordKind, UUID id) {
		return jdbc.query("select x.* from (" + ROWS + ") x where x.record_kind = ? and x.id = ?", (rs, row) -> view(rs), recordKind, id)
			.stream().findFirst().orElseThrow(OfflineSupport::notFound);
	}

	private PaymentView lockAndFind(String recordKind, UUID id) {
		if (jdbc.queryForList("select id from " + table(recordKind) + " where id = ? for update", UUID.class, id).isEmpty()) {
			throw notFound();
		}
		return find(recordKind, id);
	}

	private static String recordKind(String text) {
		String kind = text == null ? "" : text.trim().toUpperCase(Locale.ROOT);
		if (!OTHER.equals(kind) && !CLASS.equals(kind)) {
			throw notFound();
		}
		return kind;
	}

	private static String table(String recordKind) {
		return OTHER.equals(recordKind) ? "offline_instructor_other_payments" : "offline_class_payments";
	}

	private void requireInstructor(UUID instructorId) {
		if (!Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from offline_instructors where id = ?)", Boolean.class,
				instructorId))) {
			throw notFound();
		}
	}

	private LocalDate today() {
		return OfflineFeeSchedule.today(clock.instant());
	}

	private static PaymentView view(ResultSet rs) throws SQLException {
		Timestamp corrected = rs.getTimestamp("corrected_at");
		Date classOn = rs.getDate("class_on");
		Integer classCount = rs.getObject("class_count", Integer.class);
		return new PaymentView(rs.getString("record_kind"), rs.getObject("id", UUID.class), rs.getObject("instructor_id", UUID.class),
				rs.getString("instructor_name"), rs.getString("kind"), classCount, rs.getObject("batch_id", UUID.class),
				rs.getString("batch_name_en"), rs.getString("batch_name_bn"), rs.getString("description"), rs.getObject("class_id", UUID.class),
				classOn == null ? null : classOn.toLocalDate(), rs.getBigDecimal("amount"), rs.getString("method"), rs.getString("reference"),
				rs.getDate("paid_on").toLocalDate(), rs.getString("note"), rs.getString("status"), rs.getString("recorded_by_name"),
				rs.getTimestamp("recorded_at").toInstant(), rs.getString("void_reason"), corrected == null ? null : corrected.toInstant());
	}

	private static boolean has(JsonNode body, String key) {
		return body != null && body.has(key);
	}

	private static String text(JsonNode body, String key) {
		if (body == null || !body.hasNonNull(key)) {
			return null;
		}
		String value = body.get(key).asText().trim();
		return value.isEmpty() ? null : value;
	}

}
