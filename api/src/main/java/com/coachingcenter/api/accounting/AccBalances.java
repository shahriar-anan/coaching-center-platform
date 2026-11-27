package com.coachingcenter.api.accounting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

/**
 * The sums of the ledger (contracts.md, section 4). Only active entries count; a voided entry leaves every figure. A payee's balance is
 * its active charges (a deduction is stored negative) minus its active payments: positive is owed, negative is an advance. Used by L for
 * the payee rows and by R for the reports and the home page.
 */
@Component
public class AccBalances {

	/** Balances of several payees: what each is owed (positive) or has in advance (negative). */
	public record Owed(BigDecimal owed, BigDecimal advance) {
	}

	/** The figures of a range: charged, paid, spent (payments and spending in expense groups) and income. */
	public record Totals(BigDecimal charged, BigDecimal paid, BigDecimal spent, BigDecimal income) {
	}

	private static final String BALANCE_OF_ENTRY = "case e.kind when 'CHARGE' then e.amount when 'PAYMENT' then -e.amount else 0 end";

	private final JdbcTemplate jdbc;

	public AccBalances(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** The balance of one payee. Zero when it has no entries. */
	public BigDecimal balance(UUID payeeId) {
		return jdbc.queryForObject("select coalesce(sum(" + BALANCE_OF_ENTRY
				+ "), 0) from acc_entries e where e.payee_id = ? and e.status = 'ACTIVE'", BigDecimal.class, payeeId);
	}

	/** The balance of every payee of one group, archived ones too. A payee with no entries maps to zero. */
	public Map<UUID, BigDecimal> balances(UUID groupId) {
		Map<UUID, BigDecimal> balances = new LinkedHashMap<>();
		RowCallbackHandler row = rs -> balances.put(rs.getObject("id", UUID.class), rs.getBigDecimal("balance"));
		jdbc.query("select p.id, coalesce(sum(" + BALANCE_OF_ENTRY + "), 0) as balance from acc_payees p"
				+ " left join acc_entries e on e.payee_id = p.id and e.status = 'ACTIVE'"
				+ " where p.group_id = ? group by p.id order by p.id", row, groupId);
		return balances;
	}

	/** Owed and advance over all payees, or over one group when groupId is given (every payee, archived ones too). */
	public Owed owedAndAdvance(UUID groupId) {
		return jdbc.queryForObject("""
				select coalesce(sum(case when b > 0 then b end), 0) as owed,
				       coalesce(sum(case when b < 0 then -b end), 0) as advance
				from (select p.id, coalesce(sum(%s), 0) as b
				      from acc_payees p left join acc_entries e on e.payee_id = p.id and e.status = 'ACTIVE'
				      where (cast(? as uuid) is null or p.group_id = cast(? as uuid))
				      group by p.id) per_payee
				""".formatted(BALANCE_OF_ENTRY), (rs, rowNum) -> new Owed(rs.getBigDecimal("owed"), rs.getBigDecimal("advance")),
				groupId, groupId);
	}

	/** The payments made to one payee with a date in the range (both days included). */
	public BigDecimal paidInRange(UUID payeeId, LocalDate from, LocalDate to) {
		return jdbc.queryForObject("""
				select coalesce(sum(amount), 0) from acc_entries
				where payee_id = ? and kind = 'PAYMENT' and status = 'ACTIVE' and entry_date between ? and ?
				""", BigDecimal.class, payeeId, from, to);
	}

	/**
	 * The figures of a range, by entry date, both days included. Optionally one group. Spent is payments and spending in expense groups;
	 * income is income entries. Finance filters by its own switch; these totals do not.
	 */
	public Totals totals(LocalDate from, LocalDate to, UUID groupId) {
		return jdbc.queryForObject("""
				select coalesce(sum(case when e.kind = 'CHARGE' then e.amount end), 0) as charged,
				       coalesce(sum(case when e.kind = 'PAYMENT' then e.amount end), 0) as paid,
				       coalesce(sum(case when e.kind in ('PAYMENT', 'SPEND') and g.direction = 'EXPENSE' then e.amount end), 0) as spent,
				       coalesce(sum(case when e.kind = 'INCOME' then e.amount end), 0) as income
				from acc_entries e join acc_groups g on g.id = e.group_id
				where e.status = 'ACTIVE' and e.entry_date between ? and ?
				  and (cast(? as uuid) is null or e.group_id = cast(? as uuid))
				""", (rs, rowNum) -> new Totals(rs.getBigDecimal("charged"), rs.getBigDecimal("paid"), rs.getBigDecimal("spent"),
						rs.getBigDecimal("income")), from, to, groupId, groupId);
	}

}
