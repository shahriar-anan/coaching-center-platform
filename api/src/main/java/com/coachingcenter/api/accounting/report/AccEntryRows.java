package com.coachingcenter.api.accounting.report;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

import com.coachingcenter.api.accounting.AccMoney;

/**
 * The entry rows the reports answer (contracts.md, section 2, Entry): one select with every name joined, one mapper, and the filters
 * shared by the list, the CSV and the statement. Only this package reads them; the list and the home page use the same select.
 */
final class AccEntryRows {

	/** The select of an entry with its names. Callers add a where clause (built by {@link #where}) and an order. */
	static final String SELECT = """
			select e.id, e.no, e.kind, e.status, e.group_id, g.name_en as group_name_en, g.name_bn as group_name_bn, g.direction,
			  e.payee_id, p.name as payee_name, e.type_id, t.name_en as type_name_en, t.name_bn as type_name_bn, t.kind as type_kind,
			  t.unit_en, t.unit_bn, e.entry_date, e.period, e.quantity, e.unit_price, e.amount, e.account_id, a.name as account_name,
			  e.reference, e.counterparty, e.batch_id, b.name_en as batch_name_en, b.name_bn as batch_name_bn,
			  e.live_exam_id, x.title_en as live_exam_title_en, x.title_bn as live_exam_title_bn, e.description, e.note,
			  e.paired_entry_id, e.legacy_source, coalesce(rb.full_name, '') as recorded_by_name, e.recorded_at, e.corrected_at,
			  e.voided_at, vb.full_name as voided_by_name, e.void_reason, e.version
			from acc_entries e
			join acc_groups g on g.id = e.group_id
			left join acc_payees p on p.id = e.payee_id
			left join acc_types t on t.id = e.type_id
			left join acc_accounts a on a.id = e.account_id
			left join offline_batches b on b.id = e.batch_id
			left join live_exams x on x.id = e.live_exam_id
			left join admin_profiles rb on rb.user_id = e.recorded_by
			left join admin_profiles vb on vb.user_id = e.voided_by
			""";

	/** The totals of the matching rows: the count of every matching row, and the active ones' figures (contracts.md, section 4). */
	static final String TOTALS_FROM = """
			from acc_entries e
			join acc_groups g on g.id = e.group_id
			left join acc_payees p on p.id = e.payee_id
			""";

	private AccEntryRows() {
	}

	/** One entry as the API answers it. Money and quantities are strings; dates are YYYY-MM-DD; instants ISO-8601 UTC. */
	public record EntryView(UUID id, long no, String kind, String status, UUID groupId, String groupNameEn, String groupNameBn,
			String direction, UUID payeeId, String payeeName, UUID typeId, String typeNameEn, String typeNameBn, String typeKind,
			String unitEn, String unitBn, String date, String period, String quantity, String unitPrice, String amount, UUID accountId,
			String accountName, String reference, String counterparty, UUID batchId, String batchNameEn, String batchNameBn,
			UUID liveExamId, String liveExamTitleEn, String liveExamTitleBn, String description, String note, UUID pairedEntryId,
			String legacy, String recordedByName, String recordedAt, String correctedAt, String voidedAt, String voidedByName,
			String voidReason, long version,
			/** Only in a statement line: the running balance after this entry. Null everywhere else. */
			String balanceAfter) {

		/** The same entry with its running balance (a statement line). */
		EntryView withBalanceAfter(String balance) {
			return new EntryView(id, no, kind, status, groupId, groupNameEn, groupNameBn, direction, payeeId, payeeName, typeId,
					typeNameEn, typeNameBn, typeKind, unitEn, unitBn, date, period, quantity, unitPrice, amount, accountId, accountName,
					reference, counterparty, batchId, batchNameEn, batchNameBn, liveExamId, liveExamTitleEn, liveExamTitleBn,
					description, note, pairedEntryId, legacy, recordedByName, recordedAt, correctedAt, voidedAt, voidedByName,
					voidReason, version, balance);
		}

	}

	/** The filters of the entries list and the CSV (contracts.md, section 3.2). Null means no filter. */
	record Filter(LocalDate from, LocalDate to, UUID groupId, UUID payeeId, UUID typeId, String kind, UUID accountId, UUID batchId,
			String status, String q) {
	}

	/** The rows of the select with the filters' where clause. The arguments are appended to {@code args} in order. */
	static String where(Filter filter, List<Object> args) {
		List<String> parts = new ArrayList<>();
		if (filter.from() != null) {
			parts.add("e.entry_date >= ?");
			args.add(filter.from());
		}
		if (filter.to() != null) {
			parts.add("e.entry_date <= ?");
			args.add(filter.to());
		}
		if (filter.groupId() != null) {
			parts.add("e.group_id = ?");
			args.add(filter.groupId());
		}
		if (filter.payeeId() != null) {
			parts.add("e.payee_id = ?");
			args.add(filter.payeeId());
		}
		if (filter.typeId() != null) {
			parts.add("e.type_id = ?");
			args.add(filter.typeId());
		}
		if (filter.kind() != null) {
			parts.add("e.kind = ?");
			args.add(filter.kind());
		}
		if (filter.accountId() != null) {
			parts.add("e.account_id = ?");
			args.add(filter.accountId());
		}
		if (filter.batchId() != null) {
			parts.add("e.batch_id = ?");
			args.add(filter.batchId());
		}
		if (filter.status() != null) {
			parts.add("e.status = ?");
			args.add(filter.status());
		}
		if (filter.q() != null) {
			parts.add(search(filter.q(), args));
		}
		return parts.isEmpty() ? "" : " where " + String.join(" and ", parts);
	}

	/**
	 * The search of the list: description, note, reference, paid to, the payee's name, or the entry number (1042 or #1042). The text is
	 * matched without case, and the wildcard characters in it are taken as plain text.
	 */
	private static String search(String q, List<Object> args) {
		String text = q.trim();
		String like = "%" + text.toLowerCase(java.util.Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
		List<String> parts = new ArrayList<>(List.of("lower(coalesce(e.description, '')) like ?",
				"lower(coalesce(e.note, '')) like ?", "lower(coalesce(e.reference, '')) like ?",
				"lower(coalesce(e.counterparty, '')) like ?", "lower(coalesce(p.name, '')) like ?"));
		for (int i = 0; i < 5; i++) {
			args.add(like);
		}
		String number = text.startsWith("#") ? text.substring(1) : text;
		if (number.matches("[0-9]{1,18}")) {
			parts.add("e.no = ?");
			args.add(Long.parseLong(number));
		}
		return "(" + String.join(" or ", parts) + ")";
	}

	/** The rows of the select with a where clause and an order, at most {@code limit} when it is above zero. */
	static List<EntryView> find(JdbcTemplate jdbc, String where, List<Object> args, String order, int limit) {
		String sql = SELECT + where + " order by " + order + (limit > 0 ? " limit " + limit : "");
		return jdbc.query(sql, (rs, row) -> map(rs), args.toArray());
	}

	static EntryView map(ResultSet rs) throws SQLException {
		String voided = instant(rs.getTimestamp("voided_at"));
		return new EntryView(rs.getObject("id", UUID.class), rs.getLong("no"), rs.getString("kind"), rs.getString("status"),
				rs.getObject("group_id", UUID.class), rs.getString("group_name_en"), rs.getString("group_name_bn"),
				rs.getString("direction"), rs.getObject("payee_id", UUID.class), rs.getString("payee_name"),
				rs.getObject("type_id", UUID.class), rs.getString("type_name_en"), rs.getString("type_name_bn"),
				rs.getString("type_kind"), rs.getString("unit_en"), rs.getString("unit_bn"),
				rs.getObject("entry_date", LocalDate.class).toString(), rs.getString("period"), quantity(rs.getBigDecimal("quantity")),
				money(rs.getBigDecimal("unit_price")), AccMoney.format(rs.getBigDecimal("amount")),
				rs.getObject("account_id", UUID.class), rs.getString("account_name"), rs.getString("reference"),
				rs.getString("counterparty"), rs.getObject("batch_id", UUID.class), rs.getString("batch_name_en"),
				rs.getString("batch_name_bn"), rs.getObject("live_exam_id", UUID.class), rs.getString("live_exam_title_en"),
				rs.getString("live_exam_title_bn"), rs.getString("description"), rs.getString("note"),
				rs.getObject("paired_entry_id", UUID.class), rs.getString("legacy_source"), rs.getString("recorded_by_name"),
				instant(rs.getTimestamp("recorded_at")), instant(rs.getTimestamp("corrected_at")), voided,
				voided == null ? null : rs.getString("voided_by_name"), rs.getString("void_reason"), rs.getLong("version"), null);
	}

	static String money(BigDecimal value) {
		return value == null ? null : AccMoney.format(value);
	}

	static String quantity(BigDecimal value) {
		return value == null ? null : AccMoney.formatQuantity(value);
	}

	static String instant(Timestamp value) {
		return value == null ? null : value.toInstant().toString();
	}

}
