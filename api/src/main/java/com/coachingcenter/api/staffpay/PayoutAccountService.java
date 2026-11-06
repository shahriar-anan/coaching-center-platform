package com.coachingcenter.api.staffpay;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.staffnotifications.StaffNotificationKind;
import com.coachingcenter.api.staffnotifications.StaffNotificationWriter;

/**
 * Payout accounts of System Admins and instructors (staff pay plan, phase 2). Each write acts only on the given person's own
 * accounts, locks that person's row so the five-account limit and the one preferred account hold under concurrent calls,
 * writes one audit row holding the account id only, and tells every active Master Admin with a notification that names the
 * person and the change, never a number.
 *
 * <p>
 * The service does not check permissions. The owner routes check STAFF_PAY_SELF; a Master Admin read route (staff pay, phase
 * 3) calls {@link #list} after checking STAFF_PAY_MANAGE. There is no path that writes another person's accounts.
 */
@Service
public class PayoutAccountService {

	private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

	private static final DateTimeFormatter TIME = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

	private static final String COLUMNS = """
			select a.id, a.kind, a.account_name, a.account_number, a.bank_name, a.branch, a.routing_number,
			       a.wallet_number, a.account_number_enc, a.routing_number_enc, a.wallet_number_enc, a.key_version,
			       a.preferred, a.created_at, a.updated_at
			from staff_payout_accounts a
			where a.user_id = :user and a.deleted_at is null
			""";

	/* The plain columns are written too until the phase 9 verification has passed; the sealed columns are filled when a key is set. */
	private static final String INSERT = """
			insert into staff_payout_accounts (id, user_id, kind, account_name, account_number, bank_name, branch,
			       routing_number, wallet_number, account_number_enc, routing_number_enc, wallet_number_enc, key_version,
			       preferred, created_at, updated_at)
			values (:id, :user, :kind, :name, :number, :bank, :branch, :routing, :wallet, :numberEnc, :routingEnc, :walletEnc,
			       :keyVersion, :preferred, :now, :now)
			""";

	private static final String UPDATE = """
			update staff_payout_accounts
			set kind = :kind, account_name = :name, account_number = :number, bank_name = :bank, branch = :branch,
			    routing_number = :routing, wallet_number = :wallet, account_number_enc = :numberEnc,
			    routing_number_enc = :routingEnc, wallet_number_enc = :walletEnc, key_version = :keyVersion,
			    updated_at = :now
			where id = :id and user_id = :user and deleted_at is null
			""";

	private final NamedParameterJdbcTemplate jdbc;

	private final StaffNotificationWriter notifications;

	private final AuditRecorder audit;

	private final Clock clock;

	private final PayoutNumberCipher cipher;

	public PayoutAccountService(JdbcTemplate jdbc, StaffNotificationWriter notifications, AuditRecorder audit, Clock clock,
			PayoutNumberCipher cipher) {
		this.jdbc = new NamedParameterJdbcTemplate(jdbc);
		this.notifications = notifications;
		this.audit = audit;
		this.clock = clock;
		this.cipher = cipher;
	}

	/**
	 * The person's live accounts, preferred first, then in the order they were added, with the numbers in full. The owner's
	 * list uses it; so does the Master Admin's read-only view.
	 */
	@Transactional(readOnly = true)
	public List<PayoutAccountView> list(UUID userId) {
		return jdbc.query(COLUMNS + " order by a.preferred desc, a.created_at, a.id", new MapSqlParameterSource("user", userId),
				(rs, number) -> view(rs));
	}

	/** Adds an account. The first live account becomes the preferred one. Refused past five live accounts. */
	@Transactional
	public PayoutAccountView add(UUID userId, PayoutAccountRequest request) {
		PayoutAccountRules.Fields fields = PayoutAccountRules.validate(request);
		lockOwner(userId);
		long live = liveCount(userId);
		if (live >= PayoutAccountRules.MAX_ACCOUNTS) {
			throw new ApiException(ErrorCode.PAYOUT_ACCOUNT_LIMIT, HttpStatus.CONFLICT,
					"A person can keep at most 5 payout accounts.");
		}
		UUID id = UUID.randomUUID();
		Timestamp now = Timestamp.from(clock.instant());
		jdbc.update(INSERT, fields(userId, fields, id, now).addValue("preferred", live == 0));
		audit.record(AuditRecorder.PAYOUT_ACCOUNT_ADDED, AuditRecorder.PAYOUT_ACCOUNT_ENTITY, id);
		notifyMasterAdmins(userId, "ADDED");
		return find(userId, id);
	}

	/** Changes one of the person's live accounts. Its kind may change; its preferred flag does not. */
	@Transactional
	public PayoutAccountView update(UUID userId, UUID id, PayoutAccountRequest request) {
		PayoutAccountRules.Fields fields = PayoutAccountRules.validate(request);
		lockOwner(userId);
		int changed = jdbc.update(UPDATE, fields(userId, fields, id, Timestamp.from(clock.instant())));
		if (changed == 0) {
			throw notFound();
		}
		audit.record(AuditRecorder.PAYOUT_ACCOUNT_UPDATED, AuditRecorder.PAYOUT_ACCOUNT_ENTITY, id);
		notifyMasterAdmins(userId, "UPDATED");
		return find(userId, id);
	}

	/**
	 * Soft deletes one of the person's accounts. If it was the preferred one, the earliest-added live account becomes
	 * preferred. The row stays, so a payment that copied it keeps its record.
	 */
	@Transactional
	public void remove(UUID userId, UUID id) {
		lockOwner(userId);
		boolean wasPreferred = preferred(userId, id);
		Timestamp now = Timestamp.from(clock.instant());
		jdbc.update("""
				update staff_payout_accounts set deleted_at = :now, preferred = false, updated_at = :now
				where id = :id and user_id = :user and deleted_at is null
				""", new MapSqlParameterSource("now", now).addValue("id", id).addValue("user", userId));
		if (wasPreferred) {
			jdbc.update("""
					update staff_payout_accounts set preferred = true, updated_at = :now
					where id = (select s.id from staff_payout_accounts s where s.user_id = :user and s.deleted_at is null
					            order by s.created_at, s.id limit 1)
					""", new MapSqlParameterSource("now", now).addValue("user", userId));
		}
		audit.record(AuditRecorder.PAYOUT_ACCOUNT_REMOVED, AuditRecorder.PAYOUT_ACCOUNT_ENTITY, id);
		notifyMasterAdmins(userId, "REMOVED");
	}

	/** Makes one live account the preferred one. Clears the flag on the others first: the index allows one at a time. */
	@Transactional
	public PayoutAccountView makePreferred(UUID userId, UUID id) {
		lockOwner(userId);
		if (!preferred(userId, id)) {
			Timestamp now = Timestamp.from(clock.instant());
			jdbc.update("""
					update staff_payout_accounts set preferred = false, updated_at = :now
					where user_id = :user and preferred and deleted_at is null
					""", new MapSqlParameterSource("now", now).addValue("user", userId));
			jdbc.update("""
					update staff_payout_accounts set preferred = true, updated_at = :now
					where id = :id and user_id = :user and deleted_at is null
					""", new MapSqlParameterSource("now", now).addValue("id", id).addValue("user", userId));
			audit.record(AuditRecorder.PAYOUT_ACCOUNT_PREFERRED, AuditRecorder.PAYOUT_ACCOUNT_ENTITY, id);
			notifyMasterAdmins(userId, "PREFERRED");
		}
		return find(userId, id);
	}

	/** Locks the person's row for the rest of the transaction, so two writes for one person run in turn. */
	private void lockOwner(UUID userId) {
		jdbc.queryForList("select id from users where id = :user for update", new MapSqlParameterSource("user", userId));
	}

	private long liveCount(UUID userId) {
		Long count = jdbc.queryForObject(
				"select count(*) from staff_payout_accounts where user_id = :user and deleted_at is null",
				new MapSqlParameterSource("user", userId), Long.class);
		return count == null ? 0 : count;
	}

	/** Whether the live account belongs to the person and is preferred. A row that is not theirs is not found. */
	private boolean preferred(UUID userId, UUID id) {
		List<Boolean> flags = jdbc.query(
				"select preferred from staff_payout_accounts where id = :id and user_id = :user and deleted_at is null",
				new MapSqlParameterSource("id", id).addValue("user", userId), (rs, number) -> rs.getBoolean("preferred"));
		if (flags.isEmpty()) {
			throw notFound();
		}
		return flags.get(0);
	}

	private PayoutAccountView find(UUID userId, UUID id) {
		List<PayoutAccountView> rows = jdbc.query(COLUMNS + " and a.id = :id",
				new MapSqlParameterSource("user", userId).addValue("id", id), (rs, number) -> view(rs));
		if (rows.isEmpty()) {
			throw notFound();
		}
		return rows.get(0);
	}

	/**
	 * Tells each active Master Admin that the person's account changed. The text needs only the person's name and the kind
	 * of change. The link is the person's list page, which exists before the pay pages do.
	 */
	private void notifyMasterAdmins(UUID userId, String change) {
		Map<String, Object> person = jdbc.queryForMap("""
				select coalesce(p.full_name, 'Staff member') as name, u.role
				from users u left join admin_profiles p on p.user_id = u.id
				where u.id = :user
				""", new MapSqlParameterSource("user", userId));
		String name = StaffNotificationWriter.fitText(String.valueOf(person.get("name")));
		String link = "INSTRUCTOR".equals(String.valueOf(person.get("role"))) ? "/staff/instructors" : "/staff/admins";
		List<UUID> masters = jdbc.query("""
				select id from users where role = 'MASTER_ADMIN' and status = 'ACTIVE' and deleted_at is null
				""", new MapSqlParameterSource(), (rs, number) -> rs.getObject("id", UUID.class));
		for (UUID master : masters) {
			notifications.notify(master, StaffNotificationKind.PAYOUT_ACCOUNT_CHANGED, Map.of("personName", name, "change", change),
					link, null);
		}
	}

	/** The row's values, with the numbers sealed too when a key is set (sealed columns and key version stay empty without one). */
	private MapSqlParameterSource fields(UUID userId, PayoutAccountRules.Fields fields, UUID id, Timestamp now) {
		return new MapSqlParameterSource().addValue("id", id).addValue("user", userId)
			.addValue("kind", fields.kind().name()).addValue("name", fields.accountName())
			.addValue("number", fields.accountNumber()).addValue("bank", fields.bankName())
			.addValue("branch", fields.branch()).addValue("routing", fields.routingNumber())
			.addValue("wallet", fields.walletNumber())
			.addValue("numberEnc", cipher.seal(PayoutNumberCipher.ACCOUNT, id, fields.accountNumber()), Types.BINARY)
			.addValue("routingEnc", cipher.seal(PayoutNumberCipher.ROUTING, id, fields.routingNumber()), Types.BINARY)
			.addValue("walletEnc", cipher.seal(PayoutNumberCipher.WALLET, id, fields.walletNumber()), Types.BINARY)
			.addValue("keyVersion", cipher.enabled() ? cipher.keyVersion() : null, Types.SMALLINT)
			.addValue("now", now);
	}

	private static ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	private PayoutAccountView view(ResultSet rs) throws SQLException {
		PayoutNumberCipher.PayoutNumbers numbers = cipher.read(rs);
		String account = numbers.account();
		String wallet = numbers.wallet();
		return new PayoutAccountView(rs.getObject("id", UUID.class), rs.getString("kind"), rs.getString("account_name"), account,
				rs.getString("bank_name"), rs.getString("branch"), numbers.routing(), wallet,
				PayoutAccountRules.mask(wallet != null ? wallet : account), rs.getBoolean("preferred"),
				format(rs.getTimestamp("created_at").toInstant()), format(rs.getTimestamp("updated_at").toInstant()));
	}

	private static String format(Instant instant) {
		return instant.atZone(DHAKA).format(TIME);
	}

	/**
	 * One payout account. {@code accountNumber} and {@code walletNumber} hold the full number and are only ever sent to the
	 * owner or the Master Admin's read view; {@code maskedNumber} is the form a list shows.
	 */
	public record PayoutAccountView(UUID id, String kind, String accountName, String accountNumber, String bankName, String branch,
			String routingNumber, String walletNumber, String maskedNumber, boolean preferred, String createdAt, String updatedAt) {
	}

	/**
	 * One entry as the owner sends it. {@code kind} is BANK, BKASH, NAGAD or ROCKET. The bank fields are read for a bank and
	 * the wallet number for a wallet; the other fields are ignored.
	 */
	public record PayoutAccountRequest(String kind, String accountName, String accountNumber, String bankName, String branch,
			String routingNumber, String walletNumber) {
	}

}
