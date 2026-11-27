package com.coachingcenter.api.accounting.report;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.accounting.AccDates;
import com.coachingcenter.api.accounting.AccMoney;
import com.coachingcenter.api.accounting.AccRefusals;
import com.coachingcenter.api.accounting.report.AccEntryRows.EntryView;
import com.coachingcenter.api.accounting.report.AccEntryRows.Filter;

/**
 * The entries list and its CSV (contracts.md, section 3.2; plan 4.6 Entries). Every filter narrows both; the totals cover every matching
 * active row, not only the page; voided rows are listed but leave the totals.
 */
@Service
public class AccEntryListService {

	/** The most rows one CSV may hold (contract: too_many_rows above it). */
	static final int MAX_CSV_ROWS = 20_000;

	private static final String DEFAULT_SORT = "-date";

	private static final int DEFAULT_SIZE = 50;

	private static final int MAX_SIZE = 200;

	private final JdbcTemplate jdbc;

	public AccEntryListService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** One page of the list, with the totals of every matching row. */
	public record EntryPage(List<EntryView> items, int page, int size, long total, Totals totals) {
	}

	/** Charged, paid, spent and income of the matching active rows, as strings. */
	public record Totals(String charged, String paid, String spent, String income) {
	}

	/** The raw request, read as the controller gives it (strings, so each field names its own error). */
	public record Request(String from, String to, String groupId, String payeeId, String typeId, String kind, String accountId,
			String batchId, String status, String q, String sort, String page, String size) {
	}

	/** One page of entries, newest first unless the sort says otherwise. */
	@Transactional(readOnly = true)
	public EntryPage list(Request request) {
		Filter filter = filter(request);
		String order = order(request.sort());
		int page = number(request.page(), 0, "page");
		int size = Math.min(MAX_SIZE, Math.max(1, number(request.size(), DEFAULT_SIZE, "size")));
		List<Object> args = new ArrayList<>();
		String where = AccEntryRows.where(filter, args);
		Counted counted = totals(where, args);
		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(size);
		pageArgs.add((long) page * size);
		List<EntryView> items = jdbc.query(AccEntryRows.SELECT + where + " order by " + order + " limit ? offset ?",
				(rs, row) -> AccEntryRows.map(rs), pageArgs.toArray());
		return new EntryPage(items, page, size, counted.count(), counted.totals());
	}

	/** The same rows as the list, all of them, as CSV in UTF-8 with a byte order mark (at most {@link #MAX_CSV_ROWS} rows). */
	@Transactional(readOnly = true)
	public Csv csv(Request request) {
		Filter filter = filter(request);
		String order = order(request.sort());
		List<Object> args = new ArrayList<>();
		String where = AccEntryRows.where(filter, args);
		if (totals(where, args).count() > MAX_CSV_ROWS) {
			throw AccRefusals.refuse("too_many_rows");
		}
		List<EntryView> rows = jdbc.query(AccEntryRows.SELECT + where + " order by " + order, (rs, row) -> AccEntryRows.map(rs),
				args.toArray());
		StringBuilder csv = new StringBuilder("﻿");
		csv.append("No,Date,Kind,Status,Group,Payee,Type,Month,Quantity,Unit,Unit price,Amount,Account,Reference,Paid to,Batch,Exam,"
				+ "Description,Note,Recorded by,Recorded at,Voided reason\r\n");
		for (EntryView row : rows) {
			String month = row.date().substring(0, 7);
			csv.append(cells(row.no(), row.date(), row.kind(), row.status(), row.groupNameEn(), row.payeeName(), row.typeNameEn(),
					month, row.quantity(), row.unitEn(), row.unitPrice(), row.amount(), row.accountName(), row.reference(),
					row.counterparty(), row.batchNameEn(), row.liveExamTitleEn(), row.description(), row.note(), row.recordedByName(),
					row.recordedAt(), row.voidReason())).append("\r\n");
		}
		String name = "accounting-" + fileDay(request.from()) + "-" + fileDay(request.to()) + ".csv";
		return new Csv(name, csv.toString().getBytes(StandardCharsets.UTF_8));
	}

	/** The CSV file: its name and its bytes. */
	public record Csv(String fileName, byte[] bytes) {
	}

	/** The count of the matching rows and the totals of the matching active ones, in one statement. */
	private Counted totals(String where, List<Object> args) {
		return jdbc.queryForObject("""
				select count(*) as total,
				  coalesce(sum(case when e.status = 'ACTIVE' and e.kind = 'CHARGE' then e.amount end), 0) as charged,
				  coalesce(sum(case when e.status = 'ACTIVE' and e.kind = 'PAYMENT' then e.amount end), 0) as paid,
				  coalesce(sum(case when e.status = 'ACTIVE' and e.kind in ('PAYMENT', 'SPEND') and g.direction = 'EXPENSE'
				    then e.amount end), 0) as spent,
				  coalesce(sum(case when e.status = 'ACTIVE' and e.kind = 'INCOME' then e.amount end), 0) as income
				""" + AccEntryRows.TOTALS_FROM + where, (rs, row) -> new Counted(rs.getLong("total"),
						new Totals(AccMoney.format(rs.getBigDecimal("charged")), AccMoney.format(rs.getBigDecimal("paid")),
								AccMoney.format(rs.getBigDecimal("spent")), AccMoney.format(rs.getBigDecimal("income")))),
				args.toArray());
	}

	private record Counted(long count, Totals totals) {
	}

	/** The filters the request asks for. Blank means no filter; a bad value is a field error. */
	static Filter filter(Request request) {
		LocalDate from = blank(request.from()) ? null : AccDates.parseDay(request.from(), "from");
		LocalDate to = blank(request.to()) ? null : AccDates.parseDay(request.to(), "to");
		if (from != null && to != null && to.isBefore(from)) {
			throw AccRefusals.validation("to", "The end date cannot be before the start date.");
		}
		return new Filter(from, to, id(request.groupId(), "groupId"), id(request.payeeId(), "payeeId"),
				id(request.typeId(), "typeId"), choice(request.kind(), "kind", "CHARGE", "PAYMENT", "SPEND", "INCOME"),
				id(request.accountId(), "accountId"), id(request.batchId(), "batchId"),
				choice(request.status(), "status", "ACTIVE", "VOIDED"), blank(request.q()) ? null : request.q().trim());
	}

	/** The ORDER BY of a sort: date, amount or number, descending with a dash; ties by the number, newest first. */
	static String order(String sort) {
		String value = blank(sort) ? DEFAULT_SORT : sort.trim().toLowerCase(Locale.ROOT);
		return switch (value) {
			case "date" -> "e.entry_date, e.no desc";
			case "-date" -> "e.entry_date desc, e.no desc";
			case "amount" -> "e.amount, e.no desc";
			case "-amount" -> "e.amount desc, e.no desc";
			case "no" -> "e.no";
			case "-no" -> "e.no desc";
			default -> throw AccRefusals.validation("sort", "Choose one of the sorts in the list.");
		};
	}

	private static int number(String text, int fallback, String field) {
		if (blank(text)) {
			return fallback;
		}
		try {
			return Math.toIntExact(Long.parseLong(text.trim()));
		}
		catch (NumberFormatException | ArithmeticException exception) {
			throw AccRefusals.validation(field, "Enter a whole number.");
		}
	}

	private static UUID id(String text, String field) {
		if (blank(text)) {
			return null;
		}
		try {
			return UUID.fromString(text.trim());
		}
		catch (IllegalArgumentException exception) {
			throw AccRefusals.validation(field, "Choose one from the list.");
		}
	}

	private static String choice(String text, String field, String... allowed) {
		if (blank(text)) {
			return null;
		}
		String value = text.trim().toUpperCase(Locale.ROOT);
		for (String option : allowed) {
			if (option.equals(value)) {
				return value;
			}
		}
		throw AccRefusals.validation(field, "Choose one from the list.");
	}

	private static String fileDay(String text) {
		return blank(text) ? "all" : text.trim();
	}

	private static String cells(Object... values) {
		List<String> cells = new ArrayList<>();
		for (Object value : values) {
			cells.add(csvCell(value == null ? "" : value.toString()));
		}
		return String.join(",", cells);
	}

	private static String csvCell(String raw) {
		// A typed text that starts like a formula (=, +, -, @, tab, return) is opened by Excel as one; a leading ' keeps it text.
		// Numbers such as a deduction's -500.00 stay as they are.
		String value = !raw.isEmpty() && "=+-@\t\r".indexOf(raw.charAt(0)) >= 0 && !raw.matches("-?\\d+(\\.\\d+)?") ? "'" + raw : raw;
		if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
			return "\"" + value.replace("\"", "\"\"") + "\"";
		}
		return value;
	}

	private static boolean blank(String text) {
		return text == null || text.isBlank();
	}

}
