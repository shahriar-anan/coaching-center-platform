package com.coachingcenter.api.accounting.report;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

import com.coachingcenter.api.accounting.AccMoney;

/**
 * The payee rows the reports answer (contracts.md, section 2, PayeeRow): the balance of every payee in one pass, then the filter the
 * caller gives. Archived payees are included; the caller decides what to hide.
 */
final class AccPayeeRows {

	/** The balance of every payee and what it paid in the month (the two dates of the month, both days included). */
	private static final String FROM = """
			with bal as (
			  select e.payee_id,
			    coalesce(sum(case e.kind when 'CHARGE' then e.amount when 'PAYMENT' then -e.amount else 0 end), 0) as balance,
			    coalesce(sum(case when e.kind = 'PAYMENT' and e.entry_date between ? and ? then e.amount else 0 end), 0) as paid_month
			  from acc_entries e
			  where e.status = 'ACTIVE' and e.payee_id is not null
			  group by e.payee_id)
			select p.id, p.group_id, g.name_en as group_name_en, g.name_bn as group_name_bn, p.name, p.phone,
			  p.archived_at is not null as archived, coalesce(b.balance, 0) as balance, coalesce(b.paid_month, 0) as paid_month,
			  (select e.entry_date from acc_entries e where e.payee_id = p.id and e.kind = 'PAYMENT' and e.status = 'ACTIVE'
			    order by e.entry_date desc, e.no desc limit 1) as last_on,
			  (select e.amount from acc_entries e where e.payee_id = p.id and e.kind = 'PAYMENT' and e.status = 'ACTIVE'
			    order by e.entry_date desc, e.no desc limit 1) as last_amount
			from acc_payees p
			join acc_groups g on g.id = p.group_id
			left join bal b on b.payee_id = p.id
			""";

	private AccPayeeRows() {
	}

	/** One payee with its balance (positive owed, negative advance). */
	public record PayeeRow(UUID id, UUID groupId, String groupNameEn, String groupNameBn, String name, String phone, String balance,
			String paidThisMonth, String lastPaymentOn, String lastPaymentAmount, boolean archived) {
	}

	/**
	 * The payees matching {@code where} (SQL over the aliases p, g and b), in {@code order}, at most {@code limit} when it is above zero.
	 * {@code monthFrom} and {@code monthTo} are the month the paid-this-month figure is taken over.
	 */
	static List<PayeeRow> find(JdbcTemplate jdbc, LocalDate monthFrom, LocalDate monthTo, String where, List<Object> args, String order,
			int limit) {
		List<Object> all = new java.util.ArrayList<>(List.of(monthFrom, monthTo));
		all.addAll(args);
		String sql = FROM + " where " + where + " order by " + order + (limit > 0 ? " limit " + limit : "");
		return jdbc.query(sql, (rs, row) -> new PayeeRow(rs.getObject("id", UUID.class), rs.getObject("group_id", UUID.class),
				rs.getString("group_name_en"), rs.getString("group_name_bn"), rs.getString("name"), rs.getString("phone"),
				AccMoney.format(rs.getBigDecimal("balance")), AccMoney.format(rs.getBigDecimal("paid_month")),
				rs.getObject("last_on", LocalDate.class) == null ? null : rs.getObject("last_on", LocalDate.class).toString(),
				rs.getBigDecimal("last_amount") == null ? null : AccMoney.format(rs.getBigDecimal("last_amount")),
				rs.getBoolean("archived")), all.toArray());
	}

}
