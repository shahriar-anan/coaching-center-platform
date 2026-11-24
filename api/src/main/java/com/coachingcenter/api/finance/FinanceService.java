package com.coachingcenter.api.finance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.error.FieldErrorDetail;
import com.coachingcenter.api.offline.OfflineAdmissionSwitch;

/**
 * The finance reports (plan section 6, stage 5): every money row of the centre as an entry, dated on the day it was paid in Dhaka (cash
 * basis), and the views built from those entries. Read only: nothing here writes.
 *
 * <p>
 * Each source reads its own table and paid date, named in {@link #PARTS}. Income is ONLINE_ORDERS, DIRECT_ENROLMENT, OFFLINE_FEES and
 * OTHER_ONLINE; expense is STAFF_PAY and OFFLINE_INSTRUCTORS. OFFLINE_FEES and OFFLINE_INSTRUCTORS are left out while the offline
 * switch is off (plan 9, side effect 6). OTHER_ONLINE is shown only when the older {@code payments} table holds a live row (plan G7).
 * A batch filter keeps the offline sources only, since the online sources have no batch.
 */
@Service
public class FinanceService {

	public static final String ONLINE_ORDERS = "ONLINE_ORDERS";

	public static final String DIRECT_ENROLMENT = "DIRECT_ENROLMENT";

	public static final String OFFLINE_FEES = "OFFLINE_FEES";

	public static final String OTHER_ONLINE = "OTHER_ONLINE";

	public static final String STAFF_PAY = "STAFF_PAY";

	public static final String OFFLINE_INSTRUCTORS = "OFFLINE_INSTRUCTORS";

	public static final List<String> ALL = List.of(ONLINE_ORDERS, DIRECT_ENROLMENT, OFFLINE_FEES, OTHER_ONLINE, STAFF_PAY,
			OFFLINE_INSTRUCTORS);

	static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

	static final List<String> OFFLINE = List.of(OFFLINE_FEES, OFFLINE_INSTRUCTORS);

	/** The longest range the finance reports answer: ten years, which covers "All time" from the first year of records. */
	static final long MAX_DAYS = 3653;

	static final int MAX_PAGE_SIZE = 200;

	static final int DEFAULT_PAGE_SIZE = 50;

	private static final String INCOME = "INCOME";

	private static final String EXPENSE = "EXPENSE";

	/**
	 * One select per source. Every select has the same columns (source, paid_on, description, amount, direction, reference, id, batch_id)
	 * and ends with the date predicate; a batch filter is appended to the offline ones. The paid date is:
	 * ONLINE_ORDERS: paid_at (created_at when paid_at is empty), in Dhaka. DIRECT_ENROLMENT: paid_on. OFFLINE_FEES: paid_on, approved only.
	 * OTHER_ONLINE: created_at in Dhaka (the older table keeps no paid date). STAFF_PAY: paid_on, PAYMENT rows not voided.
	 * OFFLINE_INSTRUCTORS: paid_on, approved payments only.
	 */
	private static final Map<String, String> PARTS = Map.of(
			ONLINE_ORDERS, """
					select 'ONLINE_ORDERS' as source,
					  cast((coalesce(o.paid_at, o.created_at) at time zone 'Asia/Dhaka') as date) as paid_on,
					  coalesce(m.title_en, e.title_en, 'Live exam order') as description,
					  o.amount - coalesce(o.refund_amount, 0) as amount, 'INCOME' as direction,
					  coalesce(o.receipt_no, o.tran_id) as reference, o.id as id, cast(null as uuid) as batch_id
					from live_exam_orders o
					left join live_exam_modules m on m.id = o.module_id
					left join live_exams e on e.id = o.exam_id
					where o.status = 'SUCCEEDED' and o.amount - coalesce(o.refund_amount, 0) > 0
					  and cast((coalesce(o.paid_at, o.created_at) at time zone 'Asia/Dhaka') as date) between ? and ?
					""",
			DIRECT_ENROLMENT, """
					select 'DIRECT_ENROLMENT' as source, p.paid_on as paid_on,
					  'Installment ' || p.installment_no || ' of ' || p.installment_count as description,
					  p.amount as amount, 'INCOME' as direction, p.receipt_no as reference, p.id as id, cast(null as uuid) as batch_id
					from live_exam_direct_payments p
					where p.voided_at is null and p.paid_on between ? and ?
					""",
			OFFLINE_FEES, """
					select 'OFFLINE_FEES' as source, f.paid_on as paid_on, 'Admission fee: ' || a.full_name as description,
					  f.amount as amount, 'INCOME' as direction, coalesce(f.receipt_no, f.reference) as reference, f.id as id,
					  a.batch_id as batch_id
					from offline_fee_payments f
					join offline_admissions a on a.id = f.admission_id
					where f.status = 'APPROVED' and f.paid_on between ? and ?
					""",
			OTHER_ONLINE, """
					select 'OTHER_ONLINE' as source, cast((p.created_at at time zone 'Asia/Dhaka') as date) as paid_on,
					  'Online payment (' || p.payable_type || ')' as description, p.amount as amount, 'INCOME' as direction,
					  p.transaction_ref as reference, p.id as id, cast(null as uuid) as batch_id
					from payments p
					where p.status = 'SUCCESS' and p.deleted_at is null
					  and cast((p.created_at at time zone 'Asia/Dhaka') as date) between ? and ?
					""",
			STAFF_PAY, """
					select 'STAFF_PAY' as source, l.paid_on as paid_on, 'Staff payment' as description, l.amount as amount,
					  'EXPENSE' as direction, l.reference as reference, l.id as id, cast(null as uuid) as batch_id
					from staff_ledger l
					where l.kind = 'PAYMENT' and l.voided_at is null and l.paid_on between ? and ?
					""",
			OFFLINE_INSTRUCTORS, """
					select 'OFFLINE_INSTRUCTORS' as source, x.paid_on as paid_on, x.description as description, x.amount as amount,
					  'EXPENSE' as direction, x.reference as reference, x.id as id, x.batch_id as batch_id
					from (
					  select p.paid_on, 'Instructor pay: ' || c.subject as description, p.amount, p.reference, p.id, c.batch_id
					  from offline_class_payments p
					  join offline_classes c on c.id = p.class_id
					  where p.status = 'APPROVED'
					  union all
					  select o.paid_on, 'Instructor pay: ' || coalesce(o.reason, initcap(o.kind)) as description, o.amount, o.reference, o.id,
					    o.batch_id
					  from offline_instructor_other_payments o
					  where o.status = 'APPROVED'
					) x
					where x.paid_on between ? and ?
					""");

	/** The column each offline source filters by when a batch is chosen. */
	private static final Map<String, String> BATCH_COLUMN = Map.of(OFFLINE_FEES, "a.batch_id", OFFLINE_INSTRUCTORS, "x.batch_id");

	private final JdbcTemplate jdbc;

	private final OfflineAdmissionSwitch offlineSwitch;

	private final Clock clock;

	public FinanceService(JdbcTemplate jdbc, OfflineAdmissionSwitch offlineSwitch, Clock clock) {
		this.jdbc = jdbc;
		this.offlineSwitch = offlineSwitch;
		this.clock = clock;
	}

	// ---- the reports --------------------------------------------------------------------------------------------------

	/** Totals for the period, per source, and the same figures for the period of the same length before it. */
	public Summary summary(String from, String to, String sources, String batch) {
		Query query = query(from, to, sources, batch);
		List<Entry> entries = entries(query);
		Totals now = totals(entries);
		long days = ChronoUnit.DAYS.between(query.from(), query.to()) + 1;
		LocalDate previousTo = query.from().minusDays(1);
		LocalDate previousFrom = previousTo.minusDays(days - 1);
		Totals before = totals(entries(new Query(previousFrom, previousTo, query.sources(), query.batchId())));
		List<SourceTotal> bySource = new ArrayList<>();
		for (String source : query.sources()) {
			List<Entry> rows = entries.stream().filter(entry -> entry.source().equals(source)).toList();
			BigDecimal total = sum(rows);
			bySource.add(new SourceTotal(source, direction(source), total, rows.size()));
		}
		return new Summary(query.from(), query.to(), now.income(), now.expense(), now.net(), bySource,
				new Previous(previousFrom, previousTo, before.income(), before.expense(), before.net()),
				new Change(percent(now.income(), before.income()), percent(now.expense(), before.expense()),
						percent(now.net(), before.net())));
	}

	/** Per month: income, expense, net and the total per source (the chart's data). Months with no entries are left out. */
	public List<Month> monthly(String from, String to, String sources, String batch) {
		Query query = query(from, to, sources, batch);
		Map<YearMonth, List<Entry>> byMonth = new TreeMap<>();
		for (Entry entry : entries(query)) {
			byMonth.computeIfAbsent(YearMonth.from(entry.date()), key -> new ArrayList<>()).add(entry);
		}
		List<Month> months = new ArrayList<>();
		for (Map.Entry<YearMonth, List<Entry>> month : byMonth.entrySet()) {
			Totals totals = totals(month.getValue());
			Map<String, BigDecimal> bySource = new LinkedHashMap<>();
			for (String source : ALL) {
				List<Entry> rows = month.getValue().stream().filter(entry -> entry.source().equals(source)).toList();
				if (!rows.isEmpty()) {
					bySource.put(source, sum(rows));
				}
			}
			months.add(new Month(month.getKey().toString(), totals.income(), totals.expense(), totals.net(), bySource));
		}
		return months;
	}

	/**
	 * Per offline batch: the fees collected, the instructor cost, the net (fees less cost) and the margin (net as a percentage of the fees,
	 * null when there are no fees). The batch filter of the summary applies here too.
	 */
	public List<BatchRow> batches(String from, String to, String batch) {
		Query query = query(from, to, null, batch);
		Map<UUID, List<Entry>> byBatch = new HashMap<>();
		for (Entry entry : entries(query)) {
			if (entry.batchId() != null) {
				byBatch.computeIfAbsent(entry.batchId(), key -> new ArrayList<>()).add(entry);
			}
		}
		Map<UUID, String> names = batchNames();
		List<BatchRow> rows = new ArrayList<>();
		for (Map.Entry<UUID, List<Entry>> found : byBatch.entrySet()) {
			BigDecimal fees = sum(found.getValue().stream().filter(entry -> OFFLINE_FEES.equals(entry.source())).toList());
			BigDecimal cost = sum(found.getValue().stream().filter(entry -> OFFLINE_INSTRUCTORS.equals(entry.source())).toList());
			BigDecimal net = fees.subtract(cost);
			BigDecimal margin = fees.signum() == 0 ? null : net.multiply(BigDecimal.valueOf(100)).divide(fees, 1, RoundingMode.HALF_UP);
			rows.add(new BatchRow(found.getKey(), names.getOrDefault(found.getKey(), ""), fees, cost, net, margin));
		}
		rows.sort(Comparator.comparing(BatchRow::name));
		return rows;
	}

	/**
	 * Per offline instructor, what was paid in the range (approved payments, recorded by hand or the class payments from before 24 Nov) and
	 * how many payments make it. Pay no longer comes from the classes, so nothing is owed or waiting (instructor-pay-simple-plan.md).
	 */
	public List<InstructorRow> instructors(String from, String to) {
		LocalDate start = day(from, "from");
		LocalDate end = day(to, "to");
		checkRange(start, end);
		Map<UUID, InstructorRow.Builder> rows = new HashMap<>();
		addPayments(rows, start, end);
		Map<UUID, String> names = instructorNames();
		List<InstructorRow> found = new ArrayList<>();
		for (Map.Entry<UUID, InstructorRow.Builder> row : rows.entrySet()) {
			InstructorRow.Builder value = row.getValue();
			found.add(new InstructorRow(row.getKey(), names.getOrDefault(row.getKey(), ""), value.payments, value.paid));
		}
		found.sort(Comparator.comparing(InstructorRow::name));
		return found;
	}

	/**
	 * The rows behind the figures, one page at a time (the Entries page). {@code q} keeps the rows whose description or reference holds
	 * it, ignoring case: a student's or an instructor's name, a receipt or a transaction reference. {@code sort} is {@code date} (newest
	 * first, the default), {@code date_asc}, {@code amount} (largest first) or {@code amount_asc}. {@code bySource} totals every stream
	 * in reach for the same dates, batch and search, the streams left out of {@code sources} included, so the page can offer them.
	 */
	public EntryPage entries(String from, String to, String sources, String batch, String q, String sort, int page, int size) {
		Query query = query(from, to, sources, batch);
		List<Entry> all = sorted(matching(entries(query), q), sort);
		int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
		int safePage = Math.max(0, page);
		int start = Math.min(all.size(), safePage * safeSize);
		int end = Math.min(all.size(), start + safeSize);
		Totals totals = totals(all);
		List<Entry> everyStream = query.sources().size() == available(query.batchId()).size() ? all
				: matching(entries(query(from, to, null, batch)), q);
		return new EntryPage(all.subList(start, end), safePage, safeSize, all.size(), totals.income(), totals.expense(),
				streamTotals(everyStream));
	}

	/** The same rows as {@link #entries}, all of them, as CSV in UTF-8 with a byte order mark. */
	public byte[] entriesCsv(String from, String to, String sources, String batch, String q, String sort) {
		Query query = query(from, to, sources, batch);
		StringBuilder csv = new StringBuilder("﻿");
		csv.append("date,source,direction,description,amount,reference,id,batch_id\r\n");
		for (Entry entry : sorted(matching(entries(query), q), sort)) {
			csv.append(csvCell(entry.date().toString())).append(',')
				.append(csvCell(entry.source())).append(',')
				.append(csvCell(entry.direction())).append(',')
				.append(csvCell(entry.description())).append(',')
				.append(csvCell(entry.amount().toPlainString())).append(',')
				.append(csvCell(entry.reference())).append(',')
				.append(csvCell(entry.id() == null ? "" : entry.id().toString())).append(',')
				.append(csvCell(entry.batchId() == null ? "" : entry.batchId().toString())).append("\r\n");
		}
		return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
	}

	// ---- the entries -------------------------------------------------------------------------------------------------

	/** Every entry of the query's sources in its range, read from the tables. */
	List<Entry> entries(Query query) {
		if (query.sources().isEmpty()) {
			return List.of();
		}
		List<String> parts = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		for (String source : query.sources()) {
			String sql = PARTS.get(source);
			parts.add(query.batchId() == null ? sql : sql + " and " + BATCH_COLUMN.get(source) + " = ?");
			args.add(query.from());
			args.add(query.to());
			if (query.batchId() != null) {
				args.add(query.batchId());
			}
		}
		return jdbc.query(String.join(" union all ", parts), (rs, row) -> new Entry(rs.getString("source"),
				rs.getObject("paid_on", LocalDate.class), rs.getString("description"), rs.getBigDecimal("amount"),
				rs.getString("direction"), rs.getString("reference"), rs.getObject("id", UUID.class),
				rs.getObject("batch_id", UUID.class)), args.toArray());
	}

	/**
	 * The query the request asks for. Dates are required and in year-month-day form; the range runs from no later than the end and at most
	 * ten years long. Named sources must be known; the list is narrowed to the sources that apply (offline sources when the switch is on,
	 * the batch's own sources when a batch is chosen, and OTHER_ONLINE only when the older table has rows).
	 */
	Query query(String from, String to, String sources, String batch) {
		LocalDate start = day(from, "from");
		LocalDate end = day(to, "to");
		checkRange(start, end);
		UUID batchId = batch == null || batch.isBlank() ? null : batchId(batch);
		List<String> available = available(batchId);
		List<String> chosen = new ArrayList<>();
		if (sources == null || sources.isBlank()) {
			chosen.addAll(available);
		}
		else {
			List<FieldErrorDetail> problems = new ArrayList<>();
			for (String token : sources.split(",")) {
				String name = token.trim().toUpperCase(Locale.ROOT);
				if (name.isEmpty()) {
					continue;
				}
				if (!ALL.contains(name)) {
					problems.add(new FieldErrorDetail("sources", "Unknown source: " + token.trim() + "."));
				}
				else if (available.contains(name) && !chosen.contains(name)) {
					chosen.add(name);
				}
			}
			if (!problems.isEmpty()) {
				throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.", problems);
			}
		}
		return new Query(start, end, chosen, batchId);
	}

	private List<String> available(UUID batchId) {
		List<String> available = new ArrayList<>();
		for (String source : ALL) {
			if (batchId != null && !OFFLINE.contains(source)) {
				continue;
			}
			if (OFFLINE.contains(source) && !offlineSwitch.isEnabled()) {
				continue;
			}
			if (OTHER_ONLINE.equals(source) && !hasOtherOnlineRows()) {
				continue;
			}
			available.add(source);
		}
		return available;
	}

	/** Whether the older {@code payments} table holds a live row (plan G7): only then is OTHER_ONLINE shown. */
	boolean hasOtherOnlineRows() {
		Boolean exists = jdbc.queryForObject("select exists (select 1 from payments where status = 'SUCCESS' and deleted_at is null)",
				Boolean.class);
		return Boolean.TRUE.equals(exists);
	}

	// ---- helpers ------------------------------------------------------------------------------------------------------

	private void addPayments(Map<UUID, InstructorRow.Builder> rows, LocalDate start, LocalDate end) {
		jdbc.query("""
				select p.instructor_id, sum(p.amount) as total, count(*) as payments
				from (
				  select instructor_id, amount, status, paid_on from offline_class_payments
				  union all
				  select instructor_id, amount, status, paid_on from offline_instructor_other_payments
				) p
				where p.status = 'APPROVED' and p.paid_on between ? and ?
				group by p.instructor_id
				""", (rs) -> {
			InstructorRow.Builder row = rows.computeIfAbsent(rs.getObject("instructor_id", UUID.class), key -> new InstructorRow.Builder());
			row.paid = row.paid.add(rs.getBigDecimal("total"));
			row.payments += rs.getLong("payments");
		}, start, end);
	}

	private Map<UUID, String> batchNames() {
		Map<UUID, String> names = new HashMap<>();
		jdbc.query("select id, name_en from offline_batches", (rs) -> {
			names.put(rs.getObject("id", UUID.class), rs.getString("name_en"));
		});
		return names;
	}

	private Map<UUID, String> instructorNames() {
		Map<UUID, String> names = new HashMap<>();
		jdbc.query("select id, name from offline_instructors", (rs) -> {
			names.put(rs.getObject("id", UUID.class), rs.getString("name"));
		});
		return names;
	}

	private static UUID batchId(String text) {
		try {
			return UUID.fromString(text.trim());
		}
		catch (IllegalArgumentException exception) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.",
					List.of(new FieldErrorDetail("batchId", "Choose one batch from the list.")));
		}
	}

	private static LocalDate day(String text, String field) {
		if (text == null || text.isBlank()) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.",
					List.of(new FieldErrorDetail(field, "Enter the date as year-month-day.")));
		}
		try {
			return LocalDate.parse(text.trim());
		}
		catch (DateTimeParseException exception) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.",
					List.of(new FieldErrorDetail(field, "Enter the date as year-month-day.")));
		}
	}

	private static void checkRange(LocalDate start, LocalDate end) {
		if (end.isBefore(start)) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "The end date cannot be before the start date.",
					List.of(new FieldErrorDetail("to", "The end date cannot be before the start date.")));
		}
		if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "The range can be at most 10 years.",
					List.of(new FieldErrorDetail("to", "The range can be at most 10 years.")));
		}
	}

	private static String direction(String source) {
		return STAFF_PAY.equals(source) || OFFLINE_INSTRUCTORS.equals(source) ? EXPENSE : INCOME;
	}

	private static Totals totals(List<Entry> entries) {
		BigDecimal income = sum(entries.stream().filter(entry -> INCOME.equals(entry.direction())).toList());
		BigDecimal expense = sum(entries.stream().filter(entry -> EXPENSE.equals(entry.direction())).toList());
		return new Totals(income, expense, income.subtract(expense));
	}

	private static BigDecimal sum(List<Entry> entries) {
		BigDecimal total = BigDecimal.ZERO;
		for (Entry entry : entries) {
			total = total.add(entry.amount());
		}
		return total.setScale(2, RoundingMode.HALF_UP);
	}

	private static BigDecimal percent(BigDecimal now, BigDecimal before) {
		if (before.signum() == 0) {
			return null;
		}
		return now.subtract(before).multiply(BigDecimal.valueOf(100)).divide(before.abs(), 1, RoundingMode.HALF_UP);
	}

	private static List<Entry> sorted(List<Entry> entries) {
		return sorted(entries, null);
	}

	/** The entries in the order asked for (see {@link #entries}); ties fall back to the newest first, then the source and the id. */
	private static List<Entry> sorted(List<Entry> entries, String sort) {
		Comparator<Entry> newest = Comparator.comparing(Entry::date).reversed().thenComparing(Entry::source)
			.thenComparing(entry -> entry.id() == null ? "" : entry.id().toString());
		Comparator<Entry> order = switch (sort == null ? "" : sort.trim().toLowerCase(Locale.ROOT)) {
			case "date_asc" -> Comparator.comparing(Entry::date).thenComparing(newest);
			case "amount" -> Comparator.comparing(Entry::amount).reversed().thenComparing(newest);
			case "amount_asc" -> Comparator.comparing(Entry::amount).thenComparing(newest);
			default -> newest;
		};
		List<Entry> sorted = new ArrayList<>(entries);
		sorted.sort(order);
		return sorted;
	}

	/** The entries whose description or reference holds {@code q}, ignoring case; all of them when {@code q} is blank. */
	private static List<Entry> matching(List<Entry> entries, String q) {
		if (q == null || q.isBlank()) {
			return entries;
		}
		String needle = q.trim().toLowerCase(Locale.ROOT);
		List<Entry> found = new ArrayList<>();
		for (Entry entry : entries) {
			String description = entry.description() == null ? "" : entry.description().toLowerCase(Locale.ROOT);
			String reference = entry.reference() == null ? "" : entry.reference().toLowerCase(Locale.ROOT);
			if (description.contains(needle) || reference.contains(needle)) {
				found.add(entry);
			}
		}
		return found;
	}

	/** Each stream's total and count among the entries, in the order the streams first appear. */
	private static List<SourceTotal> streamTotals(List<Entry> entries) {
		Map<String, SourceTotal> totals = new java.util.LinkedHashMap<>();
		for (Entry entry : entries) {
			SourceTotal before = totals.get(entry.source());
			totals.put(entry.source(), before == null ? new SourceTotal(entry.source(), entry.direction(), entry.amount(), 1)
					: new SourceTotal(entry.source(), entry.direction(), before.total().add(entry.amount()), before.count() + 1));
		}
		return List.copyOf(totals.values());
	}

	private static String csvCell(String value) {
		if (value == null) {
			return "";
		}
		if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
			return "\"" + value.replace("\"", "\"\"") + "\"";
		}
		return value;
	}

	// ---- records ------------------------------------------------------------------------------------------------------

	/** The checked request: the range, the sources that apply, and the batch if one is chosen. */
	record Query(LocalDate from, LocalDate to, List<String> sources, UUID batchId) {
	}

	/** One row behind the figures: where it came from, when it was paid, and which row it is. */
	public record Entry(String source, LocalDate date, String description, BigDecimal amount, String direction, String reference, UUID id,
			UUID batchId) {
	}

	record Totals(BigDecimal income, BigDecimal expense, BigDecimal net) {
	}

	/** Totals of one source over the period. */
	public record SourceTotal(String source, String direction, BigDecimal total, int count) {
	}

	/** The period before, of the same length, and the change from it in percent (null where the earlier figure is zero). */
	public record Previous(LocalDate from, LocalDate to, BigDecimal income, BigDecimal expense, BigDecimal net) {
	}

	public record Change(BigDecimal income, BigDecimal expense, BigDecimal net) {
	}

	public record Summary(LocalDate from, LocalDate to, BigDecimal income, BigDecimal expense, BigDecimal net, List<SourceTotal> sources,
			Previous previous, Change change) {
	}

	public record Month(String month, BigDecimal income, BigDecimal expense, BigDecimal net, Map<String, BigDecimal> bySource) {
	}

	public record BatchRow(UUID batchId, String name, BigDecimal feesCollected, BigDecimal instructorCost, BigDecimal net,
			BigDecimal marginPercent) {
	}

	/** One instructor's approved payments in the range: how many, and how much. */
	public record InstructorRow(UUID instructorId, String name, long payments, BigDecimal paid) {

		/** The figures of one instructor while they are being read. */
		static final class Builder {

			long payments;

			BigDecimal paid = BigDecimal.ZERO;

		}

	}

	/** One page of entries, the totals of all the matching ones, and each stream's total in reach (the streams left out included). */
	public record EntryPage(List<Entry> items, int page, int size, long total, BigDecimal income, BigDecimal expense,
			List<SourceTotal> bySource) {
	}

}
