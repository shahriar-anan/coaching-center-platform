package com.coachingcenter.api.accounting.ledger;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.accounting.AccRefusals;
import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.FieldErrorDetail;

/**
 * Money accounts (plan 5.2): where a payment, spending or income came from or went to. A name is unique among the live accounts,
 * ignoring case. Only a live account can be chosen for a new entry; an old entry keeps its account (plan 4.4).
 */
@Service
public class AccAccountService {

	public static final String ENTITY = "ACC_ACCOUNT";

	public static final String CREATED = "ACC_ACCOUNT_CREATED";

	public static final String UPDATED = "ACC_ACCOUNT_UPDATED";

	public static final String ARCHIVED = "ACC_ACCOUNT_ARCHIVED";

	public static final String RESTORED = "ACC_ACCOUNT_RESTORED";

	static final int MAX_NAME = 100;

	static final int MAX_NOTE = 500;

	private static final Set<String> KINDS = Set.of("CASH", "BKASH", "NAGAD", "ROCKET", "BANK");

	private static final String ACCOUNT_ROWS = """
			select id, name, kind, note, position, archived_at, version from acc_accounts
			""";

	private final JdbcTemplate jdbc;

	private final AuditRecorder audit;

	private final Clock clock;

	public AccAccountService(JdbcTemplate jdbc, AuditRecorder audit, Clock clock) {
		this.jdbc = jdbc;
		this.audit = audit;
		this.clock = clock;
	}

	/** A money account as the API answers it. */
	public record AccountView(UUID id, String name, String kind, String note, int position, boolean archived, long version) {
	}

	/** Body of POST /accounts. */
	public record CreateAccount(String name, String kind, String note) {
	}

	/** Body of PATCH /accounts/{id}. A name or kind left out keeps it; a note sent null clears it. {@code version} is required. */
	public record UpdateAccount(String name, String kind, String note, Long version) {
	}

	/** Body of POST /accounts/order: every live account's id, in the new order. */
	public record IdOrder(List<UUID> ids) {
	}

	/** The account row, locked for an edit. */
	private record Locked(UUID id, String name, String kind, boolean archived, long version) {
	}

	@Transactional(readOnly = true)
	public List<AccountView> list(boolean includeArchived) {
		return jdbc.query(ACCOUNT_ROWS + " where (cast(? as boolean) or archived_at is null) order by position, name",
				(rs, number) -> view(rs), includeArchived);
	}

	@Transactional
	public AccountView create(CreateAccount body) {
		List<FieldErrorDetail> problems = new ArrayList<>();
		String name = required(body.name(), MAX_NAME, "name", problems);
		String kind = choice(body.kind(), KINDS, "kind", problems);
		String note = text(body.note(), MAX_NOTE, "note", problems);
		check(problems);
		if (nameTaken(name, null)) {
			throw AccRefusals.refuse("name_taken");
		}
		UUID id = UUID.randomUUID();
		Timestamp now = Timestamp.from(clock.instant());
		int position = jdbc.queryForObject("select coalesce(max(position), 0) + 1 from acc_accounts", Integer.class);
		jdbc.update("""
				insert into acc_accounts (id, name, kind, note, position, created_at, updated_at, version)
				values (?, ?, ?, ?, ?, ?, ?, 0)
				""", id, name, kind, note, position, now, now);
		audit.record(CREATED, ENTITY, id);
		return one(id);
	}

	@Transactional
	public AccountView update(UUID id, UpdateAccount body) {
		Locked account = lock(id);
		if (account.archived()) {
			throw AccRefusals.refuse("account_archived");
		}
		checkVersion(body.version(), account.version());
		List<FieldErrorDetail> problems = new ArrayList<>();
		String name = body.name() == null ? account.name() : required(body.name(), MAX_NAME, "name", problems);
		String kind = body.kind() == null ? account.kind() : choice(body.kind(), KINDS, "kind", problems);
		String note = text(body.note(), MAX_NOTE, "note", problems);
		check(problems);
		if (nameTaken(name, id)) {
			throw AccRefusals.refuse("name_taken");
		}
		jdbc.update("update acc_accounts set name = ?, kind = ?, note = ?, version = version + 1, updated_at = ? where id = ?",
				name, kind, note, Timestamp.from(clock.instant()), id);
		audit.record(UPDATED, ENTITY, id);
		return one(id);
	}

	@Transactional
	public AccountView archive(UUID id) {
		Locked account = lock(id);
		if (!account.archived()) {
			Timestamp now = Timestamp.from(clock.instant());
			jdbc.update("update acc_accounts set archived_at = ?, version = version + 1, updated_at = ? where id = ?", now, now, id);
			audit.record(ARCHIVED, ENTITY, id);
		}
		return one(id);
	}

	@Transactional
	public AccountView restore(UUID id) {
		Locked account = lock(id);
		if (account.archived()) {
			if (nameTaken(account.name(), id)) {
				throw AccRefusals.refuse("name_taken");
			}
			jdbc.update("update acc_accounts set archived_at = null, version = version + 1, updated_at = ? where id = ?",
					Timestamp.from(clock.instant()), id);
			audit.record(RESTORED, ENTITY, id);
		}
		return one(id);
	}

	/** Puts the live accounts in the order sent. The ids must be exactly the live accounts (else 400). */
	@Transactional
	public List<AccountView> order(List<UUID> ids) {
		List<UUID> live = jdbc.queryForList("select id from acc_accounts where archived_at is null order by position, name", UUID.class);
		checkOrder(ids, live);
		Timestamp now = Timestamp.from(clock.instant());
		for (int i = 0; i < ids.size(); i++) {
			int changed = jdbc.update("update acc_accounts set position = ?, updated_at = ? where id = ? and position <> ?", i + 1, now,
					ids.get(i), i + 1);
			if (changed > 0) {
				audit.record(UPDATED, ENTITY, ids.get(i));
			}
		}
		return list(false);
	}

	private Locked lock(UUID id) {
		List<Locked> found = jdbc.query("select id, name, kind, archived_at, version from acc_accounts where id = ? for update",
				(rs, number) -> locked(rs), id);
		if (found.isEmpty()) {
			throw AccRefusals.notFound("account");
		}
		return found.get(0);
	}

	private static Locked locked(ResultSet rs) throws SQLException {
		return new Locked(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("kind"),
				rs.getTimestamp("archived_at") != null, rs.getLong("version"));
	}

	/** Whether another live account has this name, ignoring case. {@code exceptId} is the account being changed, or null. */
	private boolean nameTaken(String name, UUID exceptId) {
		return Boolean.TRUE.equals(jdbc.queryForObject("""
				select exists (select 1 from acc_accounts where archived_at is null and lower(name) = lower(?)
				  and (cast(? as uuid) is null or id <> cast(? as uuid)))
				""", Boolean.class, name, exceptId, exceptId));
	}

	private AccountView one(UUID id) {
		return jdbc.query(ACCOUNT_ROWS + " where id = ?", (rs, number) -> view(rs), id).get(0);
	}

	private static AccountView view(ResultSet rs) throws SQLException {
		return new AccountView(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("kind"), rs.getString("note"),
				rs.getInt("position"), rs.getTimestamp("archived_at") != null, rs.getLong("version"));
	}

}
