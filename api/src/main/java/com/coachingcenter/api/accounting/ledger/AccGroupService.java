package com.coachingcenter.api.accounting.ledger;

import static com.coachingcenter.api.accounting.ledger.LedgerSupport.actorId;
import static com.coachingcenter.api.accounting.ledger.LedgerSupport.check;
import static com.coachingcenter.api.accounting.ledger.LedgerSupport.checkOrder;
import static com.coachingcenter.api.accounting.ledger.LedgerSupport.checkVersion;
import static com.coachingcenter.api.accounting.ledger.LedgerSupport.choice;
import static com.coachingcenter.api.accounting.ledger.LedgerSupport.required;
import static com.coachingcenter.api.accounting.ledger.LedgerSupport.text;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.accounting.AccDates;
import com.coachingcenter.api.accounting.AccMoney;
import com.coachingcenter.api.accounting.AccRefusals;
import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.FieldErrorDetail;

/**
 * Groups (plan 4.1): the places money goes out to or comes in from. Mode and direction are fixed when made; the Offline instructors
 * group cannot be archived. The list figures (payees, owed, advance, this month) come from one query for all groups, not one per group.
 */
@Service
public class AccGroupService {

	public static final String ENTITY = "ACC_GROUP";

	public static final String CREATED = "ACC_GROUP_CREATED";

	public static final String UPDATED = "ACC_GROUP_UPDATED";

	public static final String ARCHIVED = "ACC_GROUP_ARCHIVED";

	public static final String RESTORED = "ACC_GROUP_RESTORED";

	static final int MAX_NAME = 100;

	private static final String OFFLINE_INSTRUCTORS = "OFFLINE_INSTRUCTORS";

	/**
	 * One row per group. The figures come from three grouped subqueries joined on the group id. Owed and advance include archived payees
	 * (plan 4.3); the payee count is the live payees only.
	 */
	private static final String GROUP_ROWS = """
			select g.id, g.name_en, g.name_bn, g.mode, g.direction, g.system_key, g.in_finance, g.position, g.archived_at, g.version,
			  coalesce(pc.payees, 0) as payee_count, coalesce(ow.owed, 0) as owed, coalesce(ow.advance, 0) as advance,
			  coalesce(tm.total, 0) as this_month
			from acc_groups g
			left join (select group_id, count(*) as payees from acc_payees where archived_at is null group by group_id) pc
			  on pc.group_id = g.id
			left join (select group_id, sum(case when b > 0 then b end) as owed, sum(case when b < 0 then -b end) as advance
			           from (select p.id, p.group_id,
			                   coalesce(sum(case e.kind when 'CHARGE' then e.amount when 'PAYMENT' then -e.amount end), 0) as b
			                 from acc_payees p left join acc_entries e on e.payee_id = p.id and e.status = 'ACTIVE'
			                 group by p.id, p.group_id) per_payee
			           group by group_id) ow on ow.group_id = g.id
			left join (select group_id, sum(amount) as total from acc_entries
			           where status = 'ACTIVE' and kind in ('PAYMENT', 'SPEND', 'INCOME') and entry_date between ? and ?
			           group by group_id) tm on tm.group_id = g.id
			where (cast(? as boolean) or g.archived_at is null)
			  and (cast(? as uuid) is null or g.id = cast(? as uuid))
			order by g.position, g.name_en
			""";

	private final JdbcTemplate jdbc;

	private final AuditRecorder audit;

	private final Clock clock;

	public AccGroupService(JdbcTemplate jdbc, AuditRecorder audit, Clock clock) {
		this.jdbc = jdbc;
		this.audit = audit;
		this.clock = clock;
	}

	/** A group as the API answers it. Money is two decimals; {@code version} is what a later edit sends back. */
	public record GroupView(UUID id, String nameEn, String nameBn, String mode, String direction, String systemKey, boolean inFinance,
			int position, boolean archived, long version, int payeeCount, String owed, String advance, String thisMonth) {
	}

	/** Body of POST /groups. {@code inFinance} left out means on. */
	public record CreateGroup(String nameEn, String nameBn, String mode, String direction, Boolean inFinance) {
	}

	/**
	 * Body of PATCH /groups/{id}. A required name left out keeps the current one; a nameBn sent null clears it; inFinance left out keeps
	 * it. {@code version} is required.
	 */
	public record UpdateGroup(String nameEn, String nameBn, Boolean inFinance, Long version) {
	}

	/** Body of an order route: every live row's id, in the new order. */
	public record IdOrder(List<UUID> ids) {
	}

	/** The group row, locked for an edit, with what the checks need. */
	private record Locked(UUID id, String nameEn, String systemKey, boolean inFinance, boolean archived, long version) {
	}

	@Transactional(readOnly = true)
	public List<GroupView> list(boolean includeArchived) {
		return rows(includeArchived, null);
	}

	@Transactional
	public GroupView create(CreateGroup body) {
		List<FieldErrorDetail> problems = new ArrayList<>();
		String nameEn = required(body.nameEn(), MAX_NAME, "nameEn", problems);
		String nameBn = text(body.nameBn(), MAX_NAME, "nameBn", problems);
		String mode = choice(body.mode(), Set.of("PAYEES", "LOG"), "mode", problems);
		String direction = choice(body.direction(), Set.of("EXPENSE", "INCOME"), "direction", problems);
		check(problems);
		// An income group is always a Log (plan 4.1): Payees with income is refused.
		if ("INCOME".equals(direction) && "PAYEES".equals(mode)) {
			throw AccRefusals.refuse("group_mode");
		}
		if (nameTaken(nameEn, null)) {
			throw AccRefusals.refuse("name_taken");
		}
		UUID id = UUID.randomUUID();
		Timestamp now = Timestamp.from(clock.instant());
		int position = jdbc.queryForObject("select coalesce(max(position), 0) + 1 from acc_groups", Integer.class);
		jdbc.update("""
				insert into acc_groups (id, name_en, name_bn, mode, direction, system_key, in_finance, position, created_by,
				  created_at, updated_at, version)
				values (?, ?, ?, ?, ?, null, ?, ?, ?, ?, ?, 0)
				""", id, nameEn, nameBn, mode, direction, body.inFinance() == null || body.inFinance(), position, actorId(), now, now);
		audit.record(CREATED, ENTITY, id);
		return rows(true, id).get(0);
	}

	@Transactional
	public GroupView update(UUID id, UpdateGroup body) {
		Locked group = lock(id);
		if (group.archived()) {
			throw AccRefusals.refuse("group_archived");
		}
		checkVersion(body.version(), group.version());
		List<FieldErrorDetail> problems = new ArrayList<>();
		String nameEn = body.nameEn() == null ? group.nameEn() : required(body.nameEn(), MAX_NAME, "nameEn", problems);
		String nameBn = text(body.nameBn(), MAX_NAME, "nameBn", problems);
		boolean inFinance = body.inFinance() == null ? group.inFinance() : body.inFinance();
		check(problems);
		if (nameTaken(nameEn, id)) {
			throw AccRefusals.refuse("name_taken");
		}
		jdbc.update("update acc_groups set name_en = ?, name_bn = ?, in_finance = ?, version = version + 1, updated_at = ? where id = ?",
				nameEn, nameBn, inFinance, Timestamp.from(clock.instant()), id);
		audit.record(UPDATED, ENTITY, id);
		return rows(true, id).get(0);
	}

	/** Archives a group: it leaves the menus and takes no new entries or payees. The Offline instructors group cannot be archived. */
	@Transactional
	public GroupView archive(UUID id) {
		Locked group = lock(id);
		if (group.archived()) {
			return rows(true, id).get(0);
		}
		if (OFFLINE_INSTRUCTORS.equals(group.systemKey())) {
			throw AccRefusals.refuse("group_locked");
		}
		Timestamp now = Timestamp.from(clock.instant());
		jdbc.update("update acc_groups set archived_at = ?, version = version + 1, updated_at = ? where id = ?", now, now, id);
		audit.record(ARCHIVED, ENTITY, id);
		return rows(true, id).get(0);
	}

	@Transactional
	public GroupView restore(UUID id) {
		Locked group = lock(id);
		if (!group.archived()) {
			return rows(true, id).get(0);
		}
		if (nameTaken(group.nameEn(), id)) {
			throw AccRefusals.refuse("name_taken");
		}
		jdbc.update("update acc_groups set archived_at = null, version = version + 1, updated_at = ? where id = ?",
				Timestamp.from(clock.instant()), id);
		audit.record(RESTORED, ENTITY, id);
		return rows(true, id).get(0);
	}

	/** Puts the live groups in the order sent. The ids must be exactly the live groups (else 400). */
	@Transactional
	public List<GroupView> order(List<UUID> ids) {
		List<UUID> live = jdbc.queryForList("select id from acc_groups where archived_at is null order by position, name_en", UUID.class);
		checkOrder(ids, live);
		Timestamp now = Timestamp.from(clock.instant());
		for (int i = 0; i < ids.size(); i++) {
			int changed = jdbc.update("update acc_groups set position = ?, updated_at = ? where id = ? and position <> ?", i + 1, now,
					ids.get(i), i + 1);
			if (changed > 0) {
				audit.record(UPDATED, ENTITY, ids.get(i));
			}
		}
		return rows(false, null);
	}

	/** The group row, locked for the edit. An unknown id is 404. */
	private Locked lock(UUID id) {
		List<Locked> found = jdbc.query("""
				select id, name_en, system_key, in_finance, archived_at, version from acc_groups where id = ? for update
				""", (rs, number) -> locked(rs), id);
		if (found.isEmpty()) {
			throw AccRefusals.notFound("group");
		}
		return found.get(0);
	}

	private static Locked locked(ResultSet rs) throws SQLException {
		return new Locked(rs.getObject("id", UUID.class), rs.getString("name_en"), rs.getString("system_key"),
				rs.getBoolean("in_finance"), rs.getTimestamp("archived_at") != null, rs.getLong("version"));
	}

	/** Whether another live group has this name, ignoring case. {@code exceptId} is the group being changed, or null for a new one. */
	private boolean nameTaken(String name, UUID exceptId) {
		return Boolean.TRUE.equals(jdbc.queryForObject("""
				select exists (select 1 from acc_groups where archived_at is null and lower(name_en) = lower(?)
				  and (cast(? as uuid) is null or id <> cast(? as uuid)))
				""", Boolean.class, name, exceptId, exceptId));
	}

	/** The groups, one or all, with their figures for this Dhaka month. */
	private List<GroupView> rows(boolean includeArchived, UUID id) {
		YearMonth month = YearMonth.from(AccDates.today(clock));
		return jdbc.query(GROUP_ROWS, (rs, number) -> view(rs), month.atDay(1), month.atEndOfMonth(), includeArchived, id, id);
	}

	private static GroupView view(ResultSet rs) throws SQLException {
		return new GroupView(rs.getObject("id", UUID.class), rs.getString("name_en"), rs.getString("name_bn"), rs.getString("mode"),
				rs.getString("direction"), rs.getString("system_key"), rs.getBoolean("in_finance"), rs.getInt("position"),
				rs.getTimestamp("archived_at") != null, rs.getLong("version"), rs.getInt("payee_count"),
				AccMoney.format(rs.getBigDecimal("owed")), AccMoney.format(rs.getBigDecimal("advance")),
				AccMoney.format(rs.getBigDecimal("this_month")));
	}

}
