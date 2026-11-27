package com.coachingcenter.api.accounting.ledger;

import static com.coachingcenter.api.accounting.ledger.LedgerSupport.actorId;
import static com.coachingcenter.api.accounting.ledger.LedgerSupport.check;
import static com.coachingcenter.api.accounting.ledger.LedgerSupport.checkVersion;
import static com.coachingcenter.api.accounting.ledger.LedgerSupport.required;
import static com.coachingcenter.api.accounting.ledger.LedgerSupport.text;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.accounting.AccDates;
import com.coachingcenter.api.accounting.AccIds;
import com.coachingcenter.api.accounting.AccMoney;
import com.coachingcenter.api.accounting.AccRefusals;
import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.FieldErrorDetail;
import com.coachingcenter.api.offline.OfflineInstructor;
import com.coachingcenter.api.offline.OfflineInstructorRepository;
import com.coachingcenter.api.offline.OfflineInstructorService;
import com.coachingcenter.api.staffledger.LedgerKind;
import com.coachingcenter.api.staffledger.StaffPayCalculator;

/**
 * Payees (plan 4.3): the people and firms of a Payees group. A payee of the Offline instructors group is the same record as its
 * offline_instructors row (same id): every create, edit, archive and restore changes both in one transaction. A payee's balance is its
 * active charges minus its active payments (AccBalances, contracts section 4).
 */
@Service
public class AccPayeeService {

	public static final String ENTITY = "ACC_PAYEE";

	public static final String CREATED = "ACC_PAYEE_CREATED";

	public static final String UPDATED = "ACC_PAYEE_UPDATED";

	public static final String ARCHIVED = "ACC_PAYEE_ARCHIVED";

	public static final String RESTORED = "ACC_PAYEE_RESTORED";

	static final int MAX_NAME = 200;

	static final int MAX_PHONE = 20;

	static final int MAX_NOTE = 500;

	/**
	 * One row per payee with its balance, this month's payments and the newest payment (contracts PayeeRow). The three lateral subqueries
	 * are per payee; the group comes from the join. Parameters: the first and last day of this month, then the filter's.
	 */
	private static final String PAYEE_ROWS = """
			select p.id, p.group_id, g.name_en as group_name_en, g.name_bn as group_name_bn, p.name, p.phone, p.archived_at,
			  coalesce(b.balance, 0) as balance, coalesce(m.paid, 0) as paid_this_month, lp.entry_date as last_on,
			  lp.amount as last_amount
			from acc_payees p
			join acc_groups g on g.id = p.group_id
			left join lateral (select sum(case e.kind when 'CHARGE' then e.amount when 'PAYMENT' then -e.amount end) as balance
			                   from acc_entries e where e.payee_id = p.id and e.status = 'ACTIVE') b on true
			left join lateral (select sum(e.amount) as paid from acc_entries e
			                   where e.payee_id = p.id and e.kind = 'PAYMENT' and e.status = 'ACTIVE'
			                     and e.entry_date between ? and ?) m on true
			left join lateral (select e.entry_date, e.amount from acc_entries e
			                   where e.payee_id = p.id and e.kind = 'PAYMENT' and e.status = 'ACTIVE'
			                   order by e.entry_date desc, e.recorded_at desc limit 1) lp on true
			""";

	private final JdbcTemplate jdbc;

	private final AuditRecorder audit;

	private final Clock clock;

	private final OfflineInstructorRepository instructors;

	public AccPayeeService(JdbcTemplate jdbc, AuditRecorder audit, Clock clock, OfflineInstructorRepository instructors) {
		this.jdbc = jdbc;
		this.audit = audit;
		this.clock = clock;
		this.instructors = instructors;
	}

	/** A payee in a list (contracts PayeeRow). Money is two decimals; the balance is negative for an advance. */
	public record PayeeRow(UUID id, UUID groupId, String groupNameEn, String groupNameBn, String name, String phone, String balance,
			String paidThisMonth, String lastPaymentOn, String lastPaymentAmount, boolean archived) {
	}

	/** A payee's full record (contracts Payee). {@code staffPayBalance} is null when no site user is linked. */
	public record Payee(UUID id, UUID groupId, String groupNameEn, String groupNameBn, String name, String phone, String balance,
			String paidThisMonth, String lastPaymentOn, String lastPaymentAmount, boolean archived, String note, List<String> aliases,
			UUID userId, String userName, String staffPayBalance, String totalCharged, String totalPaid, int paymentsCount,
			List<BatchName> batches, String createdAt, long version) {
	}

	/** A batch an instructor teaches (a class of theirs is not cancelled). */
	public record BatchName(UUID id, String nameEn, String nameBn) {
	}

	/** One type's newest active charge, with its quantity and price (contracts LastPrice). */
	public record LastPrice(UUID typeId, String quantity, String unitPrice, String amount, String date) {
	}

	/** A site user who can be linked to a payee (contracts /users/linkable). */
	public record LinkableUser(UUID id, String name, String role) {
	}

	/** Body of POST /groups/{id}/payees. {@code aliases} count only in the Offline instructors group. */
	public record CreatePayee(String name, String phone, String note, List<String> aliases, UUID userId) {
	}

	/**
	 * Body of PATCH /payees/{id}. A name left out keeps it; phone, note, aliases and userId sent null are cleared. {@code version} is
	 * required.
	 */
	public record UpdatePayee(String name, String phone, String note, List<String> aliases, UUID userId, Long version) {
	}

	/** The group row a create checks. */
	private record GroupRef(UUID id, String mode, String systemKey, boolean archived) {
	}

	/** The payee row an edit or a state change locks. */
	private record Locked(UUID id, UUID groupId, String name, UUID userId, boolean archived, long version) {
	}

	// ---- reading ----------------------------------------------------------------------------------------------------------

	/** The payees of one group, by name. {@code q} matches the name or the phone; archived ones only when asked. */
	@Transactional(readOnly = true)
	public List<PayeeRow> listForGroup(UUID groupId, String q, boolean includeArchived) {
		if (!Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from acc_groups where id = ?)", Boolean.class, groupId))) {
			throw AccRefusals.notFound("group");
		}
		String pattern = pattern(q);
		return rows("p.group_id = ? and (cast(? as boolean) or p.archived_at is null)"
				+ " and (lower(p.name) like ? escape '!' or lower(coalesce(p.phone, '')) like ? escape '!') order by p.name",
				groupId, includeArchived, pattern, pattern);
	}

	/** The picker: live payees of live groups whose name or phone matches {@code q} (at least one character), at most {@code limit}. */
	@Transactional(readOnly = true)
	public List<PayeeRow> search(String q, int limit) {
		List<FieldErrorDetail> problems = new ArrayList<>();
		if (q == null || q.isBlank()) {
			problems.add(new FieldErrorDetail("q", "Type at least one character."));
		}
		if (limit < 1 || limit > 50) {
			problems.add(new FieldErrorDetail("limit", "Choose from 1 to 50."));
		}
		check(problems);
		String pattern = pattern(q.trim());
		return rows("g.archived_at is null and p.archived_at is null"
				+ " and (lower(p.name) like ? escape '!' or lower(coalesce(p.phone, '')) like ? escape '!')"
				+ " order by p.name limit ?", pattern, pattern, limit);
	}

	/** One payee with everything the page shows. Unknown is 404. */
	@Transactional(readOnly = true)
	public Payee get(UUID id) {
		return detail(id);
	}

	/** The newest active charge of each type for this payee, with its quantity and price (the copy of the price is one click away). */
	@Transactional(readOnly = true)
	public List<LastPrice> lastPrices(UUID id) {
		requirePayee(id);
		return jdbc.query("""
				select distinct on (e.type_id) e.type_id, e.quantity, e.unit_price, e.amount, e.entry_date
				from acc_entries e
				where e.payee_id = ? and e.kind = 'CHARGE' and e.status = 'ACTIVE'
				order by e.type_id, e.entry_date desc, e.recorded_at desc
				""", (rs, number) -> new LastPrice(rs.getObject("type_id", UUID.class), quantity(rs.getBigDecimal("quantity")),
				money(rs.getBigDecimal("unit_price")), AccMoney.format(rs.getBigDecimal("amount")),
				rs.getDate("entry_date").toLocalDate().toString()), id);
	}

	/** The site users who may be linked to a payee: System Admins and Instructors, active, by name, at most 20. */
	@Transactional(readOnly = true)
	public List<LinkableUser> linkable(String q) {
		String pattern = pattern(q == null ? "" : q.trim());
		return jdbc.query("""
				select u.id, coalesce(ap.full_name, '') as name, u.role
				from users u left join admin_profiles ap on ap.user_id = u.id
				where u.role in ('SYSTEM_ADMIN', 'INSTRUCTOR') and u.status = 'ACTIVE' and u.deleted_at is null
				  and lower(coalesce(ap.full_name, '')) like ? escape '!'
				order by name, u.id limit 20
				""", (rs, number) -> new LinkableUser(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("role")),
				pattern);
	}

	// ---- writing ----------------------------------------------------------------------------------------------------------

	@Transactional
	public Payee create(UUID groupId, CreatePayee body) {
		GroupRef group = lockGroup(groupId);
		List<FieldErrorDetail> problems = new ArrayList<>();
		String name = required(body.name(), MAX_NAME, "name", problems);
		String phone = text(body.phone(), MAX_PHONE, "phone", problems);
		String note = text(body.note(), MAX_NOTE, "note", problems);
		String[] aliases = offlineGroup(group) ? OfflineInstructorService.cleanAliases(body.aliases(), problems) : new String[0];
		check(problems);
		if (!"PAYEES".equals(group.mode())) {
			throw AccRefusals.refuse("group_mode");
		}
		if (group.archived()) {
			throw AccRefusals.refuse("group_archived");
		}
		if (nameTaken(groupId, name, null)) {
			throw AccRefusals.refuse("name_taken");
		}
		if (offlineGroup(group)) {
			requireFreeNames(null, name, aliases);
		}
		if (body.userId() != null) {
			requireLinkable(body.userId(), null);
		}
		UUID id = UUID.randomUUID();
		Timestamp now = Timestamp.from(clock.instant());
		UUID actor = actorId();
		jdbc.update("""
				insert into acc_payees (id, group_id, name, phone, note, user_id, created_by, created_at, updated_at, version)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
				""", id, groupId, name, phone, note, body.userId(), actor, now, now);
		if (offlineGroup(group)) {
			// The offline_instructors row with the same id, which the routine picker reads.
			OfflineInstructor row = new OfflineInstructor();
			row.setId(id);
			row.setName(name);
			row.setPhone(phone);
			row.setNote(note);
			row.setAliases(aliases);
			row.setCreatedBy(actor);
			row.setCreatedAt(now.toInstant());
			row.setUpdatedAt(now.toInstant());
			instructors.saveAndFlush(row);
		}
		audit.record(CREATED, ENTITY, id);
		return detail(id);
	}

	@Transactional
	public Payee update(UUID id, UpdatePayee body) {
		Locked payee = lockPayee(id);
		checkVersion(body.version(), payee.version());
		GroupRef group = groupOf(payee.groupId());
		boolean offline = offlineGroup(group);
		List<FieldErrorDetail> problems = new ArrayList<>();
		String name = body.name() == null ? payee.name() : required(body.name(), MAX_NAME, "name", problems);
		String phone = text(body.phone(), MAX_PHONE, "phone", problems);
		String note = text(body.note(), MAX_NOTE, "note", problems);
		String[] aliases = offline ? OfflineInstructorService.cleanAliases(body.aliases(), problems) : new String[0];
		check(problems);
		if (nameTaken(payee.groupId(), name, id)) {
			throw AccRefusals.refuse("name_taken");
		}
		if (offline) {
			requireFreeNames(id, name, aliases);
		}
		if (body.userId() != null && !body.userId().equals(payee.userId())) {
			requireLinkable(body.userId(), id);
		}
		jdbc.update("""
				update acc_payees set name = ?, phone = ?, note = ?, user_id = ?, version = version + 1, updated_at = ? where id = ?
				""", name, phone, note, body.userId(), Timestamp.from(clock.instant()), id);
		if (offline) {
			mirror(id, name, phone, note, aliases, null);
		}
		audit.record(UPDATED, ENTITY, id);
		return detail(id);
	}

	/** Archives a payee. It leaves the group's list but can still be charged and paid (plan 4.3). */
	@Transactional
	public Payee archive(UUID id) {
		Locked payee = lockPayee(id);
		if (!payee.archived()) {
			Instant now = clock.instant();
			jdbc.update("update acc_payees set archived_at = ?, version = version + 1, updated_at = ? where id = ?",
					Timestamp.from(now), Timestamp.from(now), id);
			if (offlineGroup(groupOf(payee.groupId()))) {
				setInstructorArchived(id, now);
			}
			audit.record(ARCHIVED, ENTITY, id);
		}
		return detail(id);
	}

	@Transactional
	public Payee restore(UUID id) {
		Locked payee = lockPayee(id);
		if (payee.archived()) {
			GroupRef group = groupOf(payee.groupId());
			if (group.archived()) {
				throw AccRefusals.refuse("group_archived");
			}
			if (nameTaken(payee.groupId(), payee.name(), id)) {
				throw AccRefusals.refuse("name_taken");
			}
			boolean offline = offlineGroup(group);
			if (offline) {
				// Restore refuses a name a live instructor has as a name or an alias (plan 4.3), as name_taken.
				OfflineInstructor own = instructors.findById(id).orElse(null);
				String[] aliases = own == null || own.getAliases() == null ? new String[0] : own.getAliases();
				for (String candidate : withName(payee.name(), aliases)) {
					if (liveInstructorHas(id, candidate)) {
						throw AccRefusals.refuse("name_taken");
					}
				}
			}
			if (payee.userId() != null && linkedElsewhere(payee.userId(), id)) {
				throw AccRefusals.refuse("user_linked");
			}
			Instant now = clock.instant();
			jdbc.update("update acc_payees set archived_at = null, version = version + 1, updated_at = ? where id = ?",
					Timestamp.from(now), id);
			if (offline) {
				setInstructorArchived(id, null);
			}
			audit.record(RESTORED, ENTITY, id);
		}
		return detail(id);
	}

	// ---- rules ------------------------------------------------------------------------------------------------------------

	private static boolean offlineGroup(GroupRef group) {
		return AccIds.GROUP_OFFLINE_INSTRUCTORS.equals(group.id()) || "OFFLINE_INSTRUCTORS".equals(group.systemKey());
	}

	private GroupRef lockGroup(UUID groupId) {
		List<GroupRef> found = jdbc.query("select id, mode, system_key, archived_at from acc_groups where id = ? for update",
				(rs, number) -> new GroupRef(rs.getObject("id", UUID.class), rs.getString("mode"), rs.getString("system_key"),
						rs.getTimestamp("archived_at") != null), groupId);
		if (found.isEmpty()) {
			throw AccRefusals.notFound("group");
		}
		return found.get(0);
	}

	private GroupRef groupOf(UUID groupId) {
		return jdbc.query("select id, mode, system_key, archived_at from acc_groups where id = ?",
				(rs, number) -> new GroupRef(rs.getObject("id", UUID.class), rs.getString("mode"), rs.getString("system_key"),
						rs.getTimestamp("archived_at") != null), groupId).get(0);
	}

	private Locked lockPayee(UUID id) {
		List<Locked> found = jdbc.query("""
				select id, group_id, name, user_id, archived_at, version from acc_payees where id = ? for update
				""", (rs, number) -> new Locked(rs.getObject("id", UUID.class), rs.getObject("group_id", UUID.class),
						rs.getString("name"), rs.getObject("user_id", UUID.class), rs.getTimestamp("archived_at") != null,
						rs.getLong("version")), id);
		if (found.isEmpty()) {
			throw AccRefusals.notFound("payee");
		}
		return found.get(0);
	}

	private void requirePayee(UUID id) {
		if (!Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from acc_payees where id = ?)", Boolean.class, id))) {
			throw AccRefusals.notFound("payee");
		}
	}

	/** Whether another live payee of the group has this name, ignoring case. {@code exceptId}: the payee being changed, or null. */
	private boolean nameTaken(UUID groupId, String name, UUID exceptId) {
		return Boolean.TRUE.equals(jdbc.queryForObject("""
				select exists (select 1 from acc_payees where group_id = ? and archived_at is null and lower(name) = lower(?)
				  and (cast(? as uuid) is null or id <> cast(? as uuid)))
				""", Boolean.class, groupId, name, exceptId, exceptId));
	}

	/**
	 * A name or alias may belong to one live instructor only (plan 4.3). Checks each name against the other live instructors' names and
	 * aliases; a clash is alias_taken.
	 */
	private void requireFreeNames(UUID exceptId, String name, String[] aliases) {
		for (String candidate : withName(name, aliases)) {
			if (liveInstructorHas(exceptId, candidate)) {
				throw AccRefusals.refuse("alias_taken");
			}
		}
	}

	/** Whether a live instructor other than {@code exceptId} has this text as its name or one of its aliases, ignoring case. */
	private boolean liveInstructorHas(UUID exceptId, String text) {
		String wanted = text.toLowerCase(Locale.ROOT);
		for (OfflineInstructor other : instructors.findByArchivedAtIsNullOrderByNameAsc()) {
			if (other.getId().equals(exceptId)) {
				continue;
			}
			if (other.getName().toLowerCase(Locale.ROOT).equals(wanted)) {
				return true;
			}
			if (other.getAliases() != null) {
				for (String alias : other.getAliases()) {
					if (alias.toLowerCase(Locale.ROOT).equals(wanted)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static List<String> withName(String name, String[] aliases) {
		List<String> all = new ArrayList<>();
		all.add(name);
		all.addAll(List.of(aliases));
		return all;
	}

	/** A site user to link: a System Admin or an Instructor, active, and not linked to another live payee (else user_linked). */
	private void requireLinkable(UUID userId, UUID exceptPayeeId) {
		if (!Boolean.TRUE.equals(jdbc.queryForObject("""
				select exists (select 1 from users where id = ? and role in ('SYSTEM_ADMIN', 'INSTRUCTOR')
				  and status = 'ACTIVE' and deleted_at is null)
				""", Boolean.class, userId))) {
			throw AccRefusals.refuse("user_not_linkable");
		}
		if (linkedElsewhere(userId, exceptPayeeId)) {
			throw AccRefusals.refuse("user_linked");
		}
	}

	private boolean linkedElsewhere(UUID userId, UUID exceptPayeeId) {
		return Boolean.TRUE.equals(jdbc.queryForObject("""
				select exists (select 1 from acc_payees where user_id = ? and archived_at is null
				  and (cast(? as uuid) is null or id <> cast(? as uuid)))
				""", Boolean.class, userId, exceptPayeeId, exceptPayeeId));
	}

	/** Keeps the offline_instructors row of a group payee in step with the payee (same id). */
	private void mirror(UUID id, String name, String phone, String note, String[] aliases, UUID createdBy) {
		Instant now = clock.instant();
		OfflineInstructor row = instructors.findById(id).orElseGet(() -> {
			OfflineInstructor created = new OfflineInstructor();
			created.setId(id);
			created.setCreatedBy(createdBy == null ? actorId() : createdBy);
			created.setCreatedAt(now);
			return created;
		});
		row.setName(name);
		row.setPhone(phone);
		row.setNote(note);
		row.setAliases(aliases);
		row.setUpdatedAt(now);
		instructors.saveAndFlush(row);
	}

	private void setInstructorArchived(UUID id, Instant archivedAt) {
		instructors.findById(id).ifPresent(row -> {
			row.setArchivedAt(archivedAt);
			row.setUpdatedAt(clock.instant());
			instructors.saveAndFlush(row);
		});
	}

	// ---- views --------------------------------------------------------------------------------------------------------------

	/** The full record of one payee: the row, its totals, the note, the alias names and batches, and the linked user's staff balance. */
	private Payee detail(UUID id) {
		List<PayeeRow> found = rows("p.id = ?", id);
		if (found.isEmpty()) {
			throw AccRefusals.notFound("payee");
		}
		PayeeRow row = found.get(0);
		Extras extras = jdbc.queryForObject("""
				select p.note, p.created_at, p.version, p.user_id, ap.full_name as user_name
				from acc_payees p left join admin_profiles ap on ap.user_id = p.user_id where p.id = ?
				""", (rs, number) -> new Extras(rs.getString("note"), rs.getTimestamp("created_at").toInstant(), rs.getLong("version"),
						rs.getObject("user_id", UUID.class), rs.getString("user_name")), id);
		Totals totals = jdbc.queryForObject("""
				select coalesce(sum(case when e.kind = 'CHARGE' then e.amount end), 0) as charged,
				       coalesce(sum(case when e.kind = 'PAYMENT' then e.amount end), 0) as paid,
				       count(*) filter (where e.kind = 'PAYMENT') as payments
				from acc_entries e where e.payee_id = ? and e.status = 'ACTIVE'
				""", (rs, number) -> new Totals(rs.getBigDecimal("charged"), rs.getBigDecimal("paid"), rs.getInt("payments")), id);
		List<String> aliases = new ArrayList<>();
		List<BatchName> batches = new ArrayList<>();
		if (AccIds.GROUP_OFFLINE_INSTRUCTORS.equals(row.groupId())) {
			instructors.findById(id).ifPresent(instructor -> {
				if (instructor.getAliases() != null) {
					aliases.addAll(List.of(instructor.getAliases()));
				}
			});
			batches.addAll(jdbc.query("""
					select b.id, b.name_en, b.name_bn from offline_batches b
					where exists (select 1 from offline_classes c where c.batch_id = b.id and c.instructor_id = ? and c.status <> 'CANCELLED')
					order by b.name_en
					""", (rs, number) -> new BatchName(rs.getObject("id", UUID.class), rs.getString("name_en"),
					rs.getString("name_bn")), id));
		}
		return new Payee(row.id(), row.groupId(), row.groupNameEn(), row.groupNameBn(), row.name(), row.phone(), row.balance(),
				row.paidThisMonth(), row.lastPaymentOn(), row.lastPaymentAmount(), row.archived(), extras.note(), aliases,
				extras.userId(), extras.userId() == null ? null : extras.userName(), staffBalance(extras.userId()),
				AccMoney.format(totals.charged()), AccMoney.format(totals.paid()), totals.payments(), batches,
				extras.createdAt().toString(), extras.version());
	}

	/** The linked user's balance in the staff pay ledger, read the way StaffLedgerService reads it. Null when no user is linked. */
	private String staffBalance(UUID userId) {
		if (userId == null) {
			return null;
		}
		List<StaffPayCalculator.Line> lines = jdbc.query("""
				select id, kind, amount, due_on, created_at, voided_at from staff_ledger where user_id = ?
				""", (rs, number) -> new StaffPayCalculator.Line(rs.getObject("id", UUID.class),
				LedgerKind.valueOf(rs.getString("kind")), rs.getBigDecimal("amount"), rs.getObject("due_on", LocalDate.class),
				rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("voided_at") != null), userId);
		return AccMoney.format(StaffPayCalculator.totals(lines, AccDates.today(clock)).balance());
	}

	private List<PayeeRow> rows(String where, Object... args) {
		YearMonth month = YearMonth.from(AccDates.today(clock));
		List<Object> all = new ArrayList<>();
		all.add(month.atDay(1));
		all.add(month.atEndOfMonth());
		all.addAll(List.of(args));
		return jdbc.query(PAYEE_ROWS + " where " + where, (rs, number) -> row(rs), all.toArray());
	}

	private static PayeeRow row(ResultSet rs) throws SQLException {
		Date last = rs.getDate("last_on");
		return new PayeeRow(rs.getObject("id", UUID.class), rs.getObject("group_id", UUID.class), rs.getString("group_name_en"),
				rs.getString("group_name_bn"), rs.getString("name"), rs.getString("phone"),
				AccMoney.format(rs.getBigDecimal("balance")), AccMoney.format(rs.getBigDecimal("paid_this_month")),
				last == null ? null : last.toLocalDate().toString(), money(rs.getBigDecimal("last_amount")),
				rs.getTimestamp("archived_at") != null);
	}

	private static String money(BigDecimal value) {
		return value == null ? null : AccMoney.format(value);
	}

	private static String quantity(BigDecimal value) {
		return value == null ? null : AccMoney.formatQuantity(value);
	}

	/** The LIKE pattern of a search: the text in lower case, with the LIKE characters escaped (escape character "!"). */
	private static String pattern(String q) {
		String text = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
		return "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
	}

	private record Extras(String note, Instant createdAt, long version, UUID userId, String userName) {
	}

	private record Totals(BigDecimal charged, BigDecimal paid, int payments) {
	}

}
