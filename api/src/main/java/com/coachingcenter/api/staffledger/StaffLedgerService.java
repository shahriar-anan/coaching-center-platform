package com.coachingcenter.api.staffledger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.error.FieldErrorDetail;
import com.coachingcenter.api.common.web.PageResponse;
import com.coachingcenter.api.staffledger.StaffPayCalculator.Line;
import com.coachingcenter.api.staffledger.StaffPayCalculator.Totals;
import com.coachingcenter.api.staffnotifications.StaffNotificationKind;
import com.coachingcenter.api.staffnotifications.StaffNotificationWriter;

/**
 * The staff pay ledger (staff pay plan, phase 3): balances, payments, adjustments, voids, the pay settings and the defaults.
 * The Master Admin manages everyone's pay (STAFF_PAY_MANAGE); a System Admin or instructor reads their own (STAFF_PAY_SELF).
 *
 * <p>
 * Every write runs in one transaction, locks the person's row first so two payments cannot both pass the balance check, and
 * writes one audit entry. Money is {@link BigDecimal}. The account snapshot is copied from the payout account at the time of
 * payment and never refers back to it.
 */
@Service
public class StaffLedgerService {

	static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

	static final int DEFAULT_PAGE_SIZE = 20;

	static final int MAX_PAGE_SIZE = 50;

	static final BigDecimal MAX_LINE = new BigDecimal("10000000.00");

	static final BigDecimal MAX_SHEET_RATE = new BigDecimal("10000.00");

	static final int MAX_NOTE = 500;

	static final int MAX_REFERENCE = 100;

	/** The person's own pay page: where a payment notice links. */
	static final String PAY_LINK = "/staff/my-pay";

	private static final String DENIED = "Access is denied.";

	private static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";

	private static final String INSTRUCTOR = "INSTRUCTOR";

	private static final List<String> METHODS = List.of("BANK", "BKASH", "NAGAD", "ROCKET");

	private static final DateTimeFormatter TIME = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

	private static final String LINE_COLUMNS = """
			id, kind, amount, due_on, created_at, paid_on, method, account_snapshot, reference, note, voided_at, void_reason
			""";

	private final NamedParameterJdbcTemplate jdbc;

	private final PermissionChecker permissions;

	private final AuditRecorder audit;

	private final StaffNotificationWriter notifications;

	private final Clock clock;

	private final StaffSalaryService salaries;

	public StaffLedgerService(JdbcTemplate jdbc, PermissionChecker permissions, AuditRecorder audit,
			StaffNotificationWriter notifications, Clock clock, StaffSalaryService salaries) {
		this.jdbc = new NamedParameterJdbcTemplate(jdbc);
		this.permissions = permissions;
		this.audit = audit;
		this.notifications = notifications;
		this.clock = clock;
		this.salaries = salaries;
	}

	/* ---------- Master Admin: people, person, ledger ---------- */

	/** Every System Admin and instructor, active or not, by name. {@code role} filters; {@code owingOnly} keeps balances above zero. */
	@Transactional(readOnly = true)
	public List<PersonRow> people(String role, boolean owingOnly) {
		requireManage();
		String filter = roleFilter(role);
		LocalDate today = today();
		MapSqlParameterSource params = new MapSqlParameterSource();
		String roleClause = "";
		if (filter != null) {
			roleClause = " and u.role = :role";
			params.addValue("role", filter);
		}
		List<Person> people = jdbc.query("""
				select u.id, u.role, u.status, u.deleted_at, coalesce(p.full_name, '') as full_name
				from users u
				left join admin_profiles p on p.user_id = u.id
				where u.role in ('SYSTEM_ADMIN', 'INSTRUCTOR')
				""" + roleClause + " order by coalesce(p.full_name, ''), u.id", params, (rs, number) -> person(rs));
		Map<UUID, Facts> facts = facts(people, today);
		List<PersonRow> rows = new ArrayList<>();
		for (Person person : people) {
			Facts fact = facts.get(person.id());
			if (owingOnly && fact.totals().balance().signum() <= 0) {
				continue;
			}
			rows.add(new PersonRow(person.id(), person.fullName(), person.role(), person.status(), person.deleted(),
					fact.totals().balance(), fact.totals().overdue(), fact.lastPaymentOn(), payNotSetUp(person, fact)));
		}
		return rows;
	}

	/** One person's settings, summary, next pay date and preferred payout account. */
	@Transactional(readOnly = true)
	public PersonDetail person(UUID userId) {
		requireManage();
		Person person = staffPerson(userId);
		LocalDate today = today();
		Facts fact = facts(List.of(person), today).get(person.id());
		Defaults defaults = readDefaults();
		return new PersonDetail(person.id(), person.fullName(), person.role(), person.status(), person.deleted(),
				payNotSetUp(person, fact), settingsView(person.role(), fact.settings(), defaults),
				summary(fact.totals(), fact.lastPaymentOn()), nextPayDate(person, fact.settings(), today),
				preferredAccount(userId));
	}

	/** One person's payout accounts, numbers in full. Read only: the Master Admin never writes them (plan P12). */
	@Transactional(readOnly = true)
	public List<PayoutAccountRow> payoutAccounts(UUID userId) {
		requireManage();
		staffPerson(userId);
		return jdbc.query("""
				select id, kind, account_name, account_number, bank_name, branch, routing_number, wallet_number, preferred
				from staff_payout_accounts
				where user_id = :user and deleted_at is null
				order by preferred desc, created_at, id
				""", new MapSqlParameterSource("user", userId), (rs, number) -> payoutAccountRow(rs));
	}

	/** One person's ledger, newest first. Voided lines are kept and marked. */
	@Transactional(readOnly = true)
	public PageResponse<LedgerRow> ledger(UUID userId, int page, int size) {
		requireManage();
		staffPerson(userId);
		return ledgerPage(userId, page, size);
	}

	/* ---------- Master Admin: writes ---------- */

	/**
	 * Records a payment to the person. A payment above the balance needs {@code allowAdvance}. {@code accountId} must be one of
	 * the person's live payout accounts and its method must match; its masked text is copied into the payment.
	 */
	@Transactional
	public LedgerRow recordPayment(UUID userId, PaymentForm form) {
		requireManage();
		lockPerson(userId);
		PaymentForm input = form == null ? new PaymentForm(null, null, null, null, null, null, null) : form;
		List<FieldErrorDetail> errors = new ArrayList<>();
		LocalDate today = today();
		BigDecimal amount = money("amount", input.amount(), MAX_LINE, false, errors);
		if (input.amount() == null) {
			errors.add(new FieldErrorDetail("amount", "Enter the amount paid."));
		}
		boolean givenDate = input.paidOn() != null && !input.paidOn().isBlank();
		LocalDate paidOn = givenDate ? date("paidOn", input.paidOn(), errors) : today;
		if (paidOn != null && paidOn.isAfter(today)) {
			errors.add(new FieldErrorDetail("paidOn", "A payment cannot be dated after today."));
		}
		String method = method("method", input.method(), errors);
		String reference = text("reference", input.reference(), MAX_REFERENCE, errors);
		String note = text("note", input.note(), MAX_NOTE, errors);
		boolean allowAdvance = Boolean.TRUE.equals(input.allowAdvance());
		PayoutAccount account = null;
		if (input.accountId() != null) {
			account = payoutAccount(userId, input.accountId());
			if (account == null) {
				errors.add(new FieldErrorDetail("accountId", "Choose one of this person's payout accounts."));
			}
			else if (method != null && !account.kind().equals(method)) {
				errors.add(new FieldErrorDetail("method", "The payment method must match the account's kind."));
			}
		}
		if (!errors.isEmpty()) {
			throw validation(errors);
		}

		Totals totals = StaffPayCalculator.totals(lines(userId), today);
		if (amount.compareTo(totals.balance()) > 0 && !allowAdvance) {
			throw new ApiException(ErrorCode.PAYMENT_EXCEEDS_BALANCE, HttpStatus.CONFLICT,
					"The payment is more than the balance. Tick the advance option to record it anyway.");
		}
		String snapshot = account == null ? null
				: AccountSnapshot.of(account.kind(), account.bankName(), account.accountNumber(), account.walletNumber());
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into staff_ledger (id, user_id, kind, amount, due_on, paid_on, method, account_snapshot, reference, note,
					created_by, created_at)
				values (:id, :user, 'PAYMENT', :amount, :paidOn, :paidOn, :method, :snapshot, :reference, :note, :actor, :now)
				""", new MapSqlParameterSource()
			.addValue("id", id)
			.addValue("user", userId)
			.addValue("amount", amount)
			.addValue("paidOn", paidOn)
			.addValue("method", method)
			.addValue("snapshot", snapshot, Types.VARCHAR)
			.addValue("reference", reference, Types.VARCHAR)
			.addValue("note", note, Types.VARCHAR)
			.addValue("actor", actorId())
			.addValue("now", Timestamp.from(clock.instant())));
		audit.record(AuditRecorder.STAFF_PAYMENT_RECORD, AuditRecorder.STAFF_LEDGER_ENTITY, id);
		notifications.notify(userId, StaffNotificationKind.PAYMENT_RECORDED, Map.of("amount", amount, "method", method),
				PAY_LINK, "PAYMENT_RECORDED:" + id);
		return ledgerRow(id);
	}

	/** Adds a one-off bonus, advance, deduction or part month. The amount may be negative; the note is required. */
	@Transactional
	public LedgerRow addAdjustment(UUID userId, AdjustmentForm form) {
		requireManage();
		lockPerson(userId);
		AdjustmentForm input = form == null ? new AdjustmentForm(null, null) : form;
		List<FieldErrorDetail> errors = new ArrayList<>();
		BigDecimal amount = money("amount", input.amount(), MAX_LINE, true, errors);
		if (input.amount() == null) {
			errors.add(new FieldErrorDetail("amount", "Enter the amount, plus or minus."));
		}
		String note = text("note", input.note(), MAX_NOTE, errors);
		if (note == null) {
			errors.add(new FieldErrorDetail("note", "Say why: the note is required."));
		}
		if (!errors.isEmpty()) {
			throw validation(errors);
		}
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into staff_ledger (id, user_id, kind, amount, due_on, note, created_by, created_at)
				values (:id, :user, 'ADJUSTMENT', :amount, :today, :note, :actor, :now)
				""", new MapSqlParameterSource()
			.addValue("id", id)
			.addValue("user", userId)
			.addValue("amount", amount)
			.addValue("today", today())
			.addValue("note", note)
			.addValue("actor", actorId())
			.addValue("now", Timestamp.from(clock.instant())));
		audit.record(AuditRecorder.STAFF_ADJUSTMENT_RECORD, AuditRecorder.STAFF_LEDGER_ENTITY, id);
		return ledgerRow(id);
	}

	/**
	 * Voids a payment or an adjustment with a reason. The line stays in the table, marked voided, and leaves the balance.
	 * Marking and salary lines cannot be voided. A voided payment notifies the person.
	 */
	@Transactional
	public LedgerRow voidLine(UUID ledgerId, VoidForm form) {
		requireManage();
		List<FieldErrorDetail> errors = new ArrayList<>();
		String reason = text("reason", form == null ? null : form.reason(), MAX_NOTE, errors);
		if (reason == null) {
			errors.add(new FieldErrorDetail("reason", "Say why: the reason is required."));
		}
		if (!errors.isEmpty()) {
			throw validation(errors);
		}
		List<VoidTarget> found = jdbc.query("""
				select user_id, kind, amount, method, voided_at from staff_ledger where id = :id for update
				""", new MapSqlParameterSource("id", ledgerId), (rs, number) -> new VoidTarget(
						rs.getObject("user_id", UUID.class), rs.getString("kind"), rs.getBigDecimal("amount"),
						rs.getString("method"), rs.getTimestamp("voided_at") != null));
		if (found.isEmpty()) {
			throw notFound();
		}
		VoidTarget target = found.get(0);
		if (!"PAYMENT".equals(target.kind()) && !"ADJUSTMENT".equals(target.kind())) {
			throw new ApiException(ErrorCode.LEDGER_NOT_VOIDABLE, HttpStatus.CONFLICT,
					"Only a payment or an adjustment can be voided. Offset a wrong salary or marking line with an adjustment.");
		}
		if (target.voided()) {
			throw new ApiException(ErrorCode.LEDGER_ALREADY_VOIDED, HttpStatus.CONFLICT, "This line is already voided.");
		}
		jdbc.update("""
				update staff_ledger set voided_at = :now, voided_by = :actor, void_reason = :reason where id = :id
				""", new MapSqlParameterSource()
			.addValue("now", Timestamp.from(clock.instant()))
			.addValue("actor", actorId())
			.addValue("reason", reason)
			.addValue("id", ledgerId));
		audit.record(AuditRecorder.STAFF_LEDGER_VOID, AuditRecorder.STAFF_LEDGER_ENTITY, ledgerId);
		if ("PAYMENT".equals(target.kind())) {
			notifications.notify(target.userId(), StaffNotificationKind.PAYMENT_VOIDED,
					Map.of("amount", target.amount(), "method", target.method()), PAY_LINK, "PAYMENT_VOIDED:" + ledgerId);
		}
		return ledgerRow(ledgerId);
	}

	/** The default sheet rate and the two reminder settings. */
	@Transactional(readOnly = true)
	public Defaults defaults() {
		requireManage();
		return readDefaults();
	}

	@Transactional
	public Defaults updateDefaults(DefaultsForm form) {
		requireManage();
		DefaultsForm input = form == null ? new DefaultsForm(null, null, null) : form;
		List<FieldErrorDetail> errors = new ArrayList<>();
		BigDecimal sheetRate = money("sheetRate", input.sheetRate(), MAX_SHEET_RATE, false, errors);
		Integer unpaid = days("unpaidReminderDays", input.unpaidReminderDays(), 1, 365, errors);
		Integer dueSoon = days("salaryDueSoonDays", input.salaryDueSoonDays(), 0, 30, errors);
		if (input.sheetRate() == null) {
			errors.add(new FieldErrorDetail("sheetRate", "Enter the sheet rate."));
		}
		if (input.unpaidReminderDays() == null) {
			errors.add(new FieldErrorDetail("unpaidReminderDays", "Enter the number of days."));
		}
		if (input.salaryDueSoonDays() == null) {
			errors.add(new FieldErrorDetail("salaryDueSoonDays", "Enter the number of days."));
		}
		if (!errors.isEmpty()) {
			throw validation(errors);
		}
		jdbc.update("""
				update staff_pay_defaults
				set sheet_rate = :rate, unpaid_reminder_days = :unpaid, salary_due_soon_days = :dueSoon,
					updated_by = :actor, updated_at = :now
				where id = 1
				""", new MapSqlParameterSource()
			.addValue("rate", sheetRate)
			.addValue("unpaid", unpaid)
			.addValue("dueSoon", dueSoon)
			.addValue("actor", actorId())
			.addValue("now", Timestamp.from(clock.instant())));
		audit.record(AuditRecorder.STAFF_PAY_DEFAULTS_UPDATE, AuditRecorder.STAFF_PAY_DEFAULTS_ENTITY, null);
		return readDefaults();
	}

	/**
	 * Sets one person's pay settings. A System Admin gets the salary fields; an instructor gets a rate override (null clears
	 * it). The wrong role's fields are refused. A change never alters a line already written.
	 */
	@Transactional
	public SettingsView updateSettings(UUID userId, SettingsForm form) {
		requireManage();
		Person person = lockPerson(userId);
		SettingsForm input = form == null ? new SettingsForm(null, null, null, null, null) : form;
		List<FieldErrorDetail> errors = new ArrayList<>();
		boolean systemAdmin = SYSTEM_ADMIN.equals(person.role());
		BigDecimal salary = money("monthlySalary", input.monthlySalary(), MAX_LINE, false, errors);
		Integer payDay = days("payDay", input.payDay(), 1, 28, errors);
		LocalDate start = date("salaryStart", input.salaryStart(), errors);
		LocalDate end = date("salaryEnd", input.salaryEnd(), errors);
		BigDecimal sheetRate = money("sheetRate", input.sheetRate(), MAX_SHEET_RATE, false, errors);
		boolean hasSalaryFields = salary != null || payDay != null || start != null || end != null;
		if (systemAdmin) {
			if (sheetRate != null) {
				errors.add(new FieldErrorDetail("sheetRate", "A System Admin is paid a salary, not a sheet rate."));
			}
			if (salary == null) {
				if (payDay != null || start != null || end != null) {
					errors.add(new FieldErrorDetail("monthlySalary", "Set the monthly salary with the pay day and dates."));
				}
			}
			else {
				if (payDay == null) {
					errors.add(new FieldErrorDetail("payDay", "Choose the pay day, 1 to 28."));
				}
				if (start == null) {
					errors.add(new FieldErrorDetail("salaryStart", "Choose the first month of the salary."));
				}
				if (start != null && end != null && end.isBefore(start)) {
					errors.add(new FieldErrorDetail("salaryEnd", "The end date cannot be before the start date."));
				}
			}
		}
		else if (hasSalaryFields) {
			errors.add(new FieldErrorDetail("monthlySalary", "An instructor is paid per sheet. Only a System Admin has a salary."));
		}
		if (!errors.isEmpty()) {
			throw validation(errors);
		}
		jdbc.update("""
				insert into staff_pay_settings (user_id, monthly_salary, pay_day, salary_start, salary_end, sheet_rate,
					updated_by, updated_at)
				values (:user, :salary, :payDay, :start, :end, :rate, :actor, :now)
				on conflict (user_id) do update set
					monthly_salary = excluded.monthly_salary, pay_day = excluded.pay_day,
					salary_start = excluded.salary_start, salary_end = excluded.salary_end,
					sheet_rate = excluded.sheet_rate, updated_by = excluded.updated_by, updated_at = excluded.updated_at
				""", new MapSqlParameterSource()
			.addValue("user", userId)
			.addValue("salary", salary, Types.NUMERIC)
			.addValue("payDay", payDay, Types.SMALLINT)
			.addValue("start", start == null ? null : java.sql.Date.valueOf(start), Types.DATE)
			.addValue("end", end == null ? null : java.sql.Date.valueOf(end), Types.DATE)
			.addValue("rate", sheetRate, Types.NUMERIC)
			.addValue("actor", actorId())
			.addValue("now", Timestamp.from(clock.instant())));
		audit.record(AuditRecorder.STAFF_PAY_SETTINGS_UPDATE, AuditRecorder.STAFF_PAY_SETTINGS_ENTITY, userId);
		return settingsView(person.role(), settingsRow(userId), readDefaults());
	}

	/* ---------- the signed-in person's own pay ---------- */

	/** The balance and the totals for the signed-in person. A System Admin also gets the salary and the next pay date. */
	@Transactional(readOnly = true)
	public SelfSummary selfSummary(UUID me) {
		Person person = findPerson(me).orElseThrow(StaffLedgerService::notFound);
		LocalDate today = today();
		Facts fact = facts(List.of(person), today).get(me);
		boolean systemAdmin = SYSTEM_ADMIN.equals(person.role());
		SettingsRow settings = fact.settings();
		StaffSalaryService.SalaryView salary = systemAdmin ? salaries.view(me, today) : null;
		return new SelfSummary(person.role(), summary(fact.totals(), fact.lastPaymentOn()),
				systemAdmin && settings != null ? settings.monthlySalary() : null,
				systemAdmin && settings != null ? settings.payDay() : null,
				systemAdmin ? nextPayDate(person, settings, today) : null,
				salary == null ? null : salary.nextPayAmount());
	}

	/** The signed-in person's ledger, newest first. */
	@Transactional(readOnly = true)
	public PageResponse<LedgerRow> selfLedger(UUID me, int page, int size) {
		findPerson(me).orElseThrow(StaffLedgerService::notFound);
		return ledgerPage(me, page, size);
	}

	/** The signed-in System Admin's salary: settings, next pay date with its amount, and each salary month. */
	@Transactional(readOnly = true)
	public StaffSalaryService.SalaryView selfSalary(UUID me) {
		findPerson(me).orElseThrow(StaffLedgerService::notFound);
		return salaries.view(me, today());
	}

	/** One person's salary, for the Master Admin. Only a System Admin or instructor has one to show. */
	@Transactional(readOnly = true)
	public StaffSalaryService.SalaryView salary(UUID userId) {
		requireManage();
		staffPerson(userId);
		return salaries.view(userId, today());
	}

	/* ---------- shared reads and checks ---------- */

	private PageResponse<LedgerRow> ledgerPage(UUID userId, int page, int size) {
		int pageSize = size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
		int pageNumber = Math.max(page, 0);
		MapSqlParameterSource params = new MapSqlParameterSource("user", userId)
			.addValue("limit", pageSize)
			.addValue("offset", (long) pageNumber * pageSize);
		Long total = jdbc.queryForObject("select count(*) from staff_ledger where user_id = :user", params, Long.class);
		List<LedgerRow> rows = jdbc.query("""
				select %s from staff_ledger where user_id = :user
				order by created_at desc, id desc limit :limit offset :offset
				""".formatted(LINE_COLUMNS), params, (rs, number) -> ledgerRow(rs));
		Page<LedgerRow> result = new PageImpl<>(rows, PageRequest.of(pageNumber, pageSize), total == null ? 0 : total);
		return PageResponse.from(result);
	}

	private LedgerRow ledgerRow(UUID id) {
		return jdbc.query("select %s from staff_ledger where id = :id".formatted(LINE_COLUMNS),
				new MapSqlParameterSource("id", id), (rs, number) -> ledgerRow(rs)).stream().findFirst()
			.orElseThrow(StaffLedgerService::notFound);
	}

	private static LedgerRow ledgerRow(ResultSet rs) throws SQLException {
		Timestamp voided = rs.getTimestamp("voided_at");
		return new LedgerRow(rs.getObject("id", UUID.class), rs.getString("kind"), rs.getBigDecimal("amount"),
				dateText(rs, "due_on"), format(rs.getTimestamp("created_at").toInstant()), dateText(rs, "paid_on"),
				rs.getString("method"), rs.getString("account_snapshot"), rs.getString("reference"), rs.getString("note"),
				voided != null, voided == null ? null : format(voided.toInstant()), rs.getString("void_reason"));
	}

	private List<Line> lines(UUID userId) {
		return jdbc.query("""
				select id, kind, amount, due_on, created_at, voided_at from staff_ledger where user_id = :user
				""", new MapSqlParameterSource("user", userId), (rs, number) -> line(rs));
	}

	private static Line line(ResultSet rs) throws SQLException {
		return new Line(rs.getObject("id", UUID.class), LedgerKind.valueOf(rs.getString("kind")),
				rs.getBigDecimal("amount"), rs.getObject("due_on", LocalDate.class), rs.getTimestamp("created_at").toInstant(),
				rs.getTimestamp("voided_at") != null);
	}

	/**
	 * Everything the list and the person page need, for several people in three queries. Every id in {@code people} gets an
	 * entry, even when it has no lines or settings.
	 */
	private Map<UUID, Facts> facts(List<Person> people, LocalDate today) {
		Map<UUID, Facts> result = new HashMap<>();
		if (people.isEmpty()) {
			return result;
		}
		List<UUID> ids = people.stream().map(Person::id).toList();
		Map<UUID, List<Line>> lines = new HashMap<>();
		Map<UUID, LocalDate> lastPaid = new HashMap<>();
		jdbc.query("""
				select user_id, id, kind, amount, due_on, created_at, voided_at, paid_on from staff_ledger
				where user_id in (:ids)
				""", new MapSqlParameterSource("ids", ids), (rs, number) -> {
					UUID userId = rs.getObject("user_id", UUID.class);
					Line line = line(rs);
					lines.computeIfAbsent(userId, key -> new ArrayList<>()).add(line);
					LocalDate paidOn = rs.getObject("paid_on", LocalDate.class);
					if (paidOn != null && "PAYMENT".equals(rs.getString("kind")) && !line.voided()) {
						lastPaid.merge(userId, paidOn, (a, b) -> a.isAfter(b) ? a : b);
					}
					return null;
				});
		Map<UUID, SettingsRow> settings = new HashMap<>();
		jdbc.query("""
				select user_id, monthly_salary, pay_day, salary_start, salary_end, sheet_rate
				from staff_pay_settings where user_id in (:ids)
				""", new MapSqlParameterSource("ids", ids), (rs, number) -> {
					SettingsRow row = settingsRow(rs);
					settings.put(row.userId(), row);
					return null;
				});
		Map<UUID, Long> accounts = new HashMap<>();
		jdbc.query("""
				select user_id, count(*) as live from staff_payout_accounts
				where deleted_at is null and user_id in (:ids) group by user_id
				""", new MapSqlParameterSource("ids", ids), (rs, number) -> {
					accounts.put(rs.getObject("user_id", UUID.class), rs.getLong("live"));
					return null;
				});
		for (Person person : people) {
			Totals totals = StaffPayCalculator.totals(lines.getOrDefault(person.id(), List.of()), today);
			LocalDate paid = lastPaid.get(person.id());
			result.put(person.id(), new Facts(totals, paid == null ? null : paid.toString(),
					settings.get(person.id()), accounts.getOrDefault(person.id(), 0L)));
		}
		return result;
	}

	private static boolean payNotSetUp(Person person, Facts fact) {
		boolean noSalary = SYSTEM_ADMIN.equals(person.role())
				&& (fact.settings() == null || fact.settings().monthlySalary() == null);
		return noSalary || fact.accounts() == 0;
	}

	private static String nextPayDate(Person person, SettingsRow settings, LocalDate today) {
		if (!SYSTEM_ADMIN.equals(person.role()) || settings == null || settings.monthlySalary() == null) {
			return null;
		}
		LocalDate next = SalarySchedule.nextDue(settings.salaryStart(), settings.salaryEnd(), settings.payDay(), today);
		return next == null ? null : next.toString();
	}

	private static Summary summary(Totals totals, String lastPaymentOn) {
		return new Summary(totals.charged(), totals.paid(), totals.balance(), totals.owedNow(), totals.overdue(),
				lastPaymentOn);
	}

	private static SettingsView settingsView(String role, SettingsRow settings, Defaults defaults) {
		BigDecimal override = settings == null ? null : settings.sheetRate();
		return new SettingsView(settings == null ? null : settings.monthlySalary(),
				settings == null ? null : settings.payDay(),
				settings == null || settings.salaryStart() == null ? null : settings.salaryStart().toString(),
				settings == null || settings.salaryEnd() == null ? null : settings.salaryEnd().toString(),
				override,
				INSTRUCTOR.equals(role) ? (override == null ? defaults.sheetRate() : override) : null);
	}

	private SettingsRow settingsRow(UUID userId) {
		return jdbc.query("""
				select user_id, monthly_salary, pay_day, salary_start, salary_end, sheet_rate
				from staff_pay_settings where user_id = :user
				""", new MapSqlParameterSource("user", userId), (rs, number) -> settingsRow(rs)).stream().findFirst()
			.orElse(null);
	}

	private static SettingsRow settingsRow(ResultSet rs) throws SQLException {
		return new SettingsRow(rs.getObject("user_id", UUID.class), rs.getBigDecimal("monthly_salary"),
				rs.getObject("pay_day", Integer.class), rs.getObject("salary_start", LocalDate.class),
				rs.getObject("salary_end", LocalDate.class), rs.getBigDecimal("sheet_rate"));
	}

	private PayoutAccountRow preferredAccount(UUID userId) {
		return jdbc.query("""
				select id, kind, account_name, account_number, bank_name, branch, routing_number, wallet_number, preferred
				from staff_payout_accounts
				where user_id = :user and preferred and deleted_at is null
				""", new MapSqlParameterSource("user", userId), (rs, number) -> payoutAccountRow(rs)).stream().findFirst()
			.orElse(null);
	}

	private static PayoutAccountRow payoutAccountRow(ResultSet rs) throws SQLException {
		return new PayoutAccountRow(rs.getObject("id", UUID.class), rs.getString("kind"), rs.getString("account_name"),
				rs.getString("account_number"), rs.getString("bank_name"), rs.getString("branch"),
				rs.getString("routing_number"), rs.getString("wallet_number"), rs.getBoolean("preferred"));
	}

	private PayoutAccount payoutAccount(UUID userId, UUID accountId) {
		return jdbc.query("""
				select id, kind, bank_name, account_number, wallet_number from staff_payout_accounts
				where id = :id and user_id = :user and deleted_at is null
				""", new MapSqlParameterSource().addValue("id", accountId).addValue("user", userId),
				(rs, number) -> new PayoutAccount(rs.getObject("id", UUID.class), rs.getString("kind"),
						rs.getString("bank_name"), rs.getString("account_number"), rs.getString("wallet_number")))
			.stream()
			.findFirst()
			.orElse(null);
	}

	private Defaults readDefaults() {
		return jdbc.query("""
				select sheet_rate, unpaid_reminder_days, salary_due_soon_days, updated_at from staff_pay_defaults where id = 1
				""", new MapSqlParameterSource(), (rs, number) -> new Defaults(rs.getBigDecimal("sheet_rate"),
					rs.getInt("unpaid_reminder_days"), rs.getInt("salary_due_soon_days"),
					format(rs.getTimestamp("updated_at").toInstant())))
			.stream()
			.findFirst()
			.orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
					"Staff pay defaults are missing."));
	}

	private static Person person(ResultSet rs) throws SQLException {
		return new Person(rs.getObject("id", UUID.class), rs.getString("role"), rs.getString("full_name"),
				rs.getString("status"), rs.getTimestamp("deleted_at") != null);
	}

	/** A System Admin or instructor. Anyone else, including the Master Admin, is not found: he is not paid through this. */
	private Person staffPerson(UUID userId) {
		return findPerson(userId).orElseThrow(StaffLedgerService::notFound);
	}

	private Optional<Person> findPerson(UUID userId) {
		return jdbc.query("""
				select u.id, u.role, u.status, u.deleted_at, coalesce(p.full_name, '') as full_name
				from users u left join admin_profiles p on p.user_id = u.id
				where u.id = :id and u.role in ('SYSTEM_ADMIN', 'INSTRUCTOR')
				""", new MapSqlParameterSource("id", userId), (rs, number) -> person(rs)).stream().findFirst();
	}

	/** Locks the person's user row for the rest of the transaction, so two writes for one person run one at a time. */
	private Person lockPerson(UUID userId) {
		Person person = jdbc.query("""
				select u.id, u.role, u.status, u.deleted_at, coalesce(p.full_name, '') as full_name
				from users u left join admin_profiles p on p.user_id = u.id
				where u.id = :id and u.role in ('SYSTEM_ADMIN', 'INSTRUCTOR')
				for update of u
				""", new MapSqlParameterSource("id", userId), (rs, number) -> person(rs)).stream().findFirst()
			.orElseThrow(StaffLedgerService::notFound);
		return person;
	}

	/* ---------- validation helpers ---------- */

	/** A money amount to two places. Null stays null (the caller decides if it is required). Zero is never allowed. */
	static BigDecimal money(String field, BigDecimal value, BigDecimal max, boolean mayBeNegative,
			List<FieldErrorDetail> errors) {
		if (value == null) {
			return null;
		}
		if (value.signum() == 0 || (!mayBeNegative && value.signum() < 0)) {
			errors.add(new FieldErrorDetail(field, mayBeNegative ? "Enter an amount other than zero." : "Enter an amount above zero."));
			return null;
		}
		if (value.abs().compareTo(max) > 0) {
			errors.add(new FieldErrorDetail(field, "The amount is too large."));
			return null;
		}
		if (value.stripTrailingZeros().scale() > 2) {
			errors.add(new FieldErrorDetail(field, "Use at most two decimal places."));
			return null;
		}
		return value.setScale(2, RoundingMode.UNNECESSARY);
	}

	private static Integer days(String field, Integer value, int min, int max, List<FieldErrorDetail> errors) {
		if (value == null) {
			return null;
		}
		if (value < min || value > max) {
			errors.add(new FieldErrorDetail(field, "Enter a whole number from " + min + " to " + max + "."));
			return null;
		}
		return value;
	}

	/** A date as YYYY-MM-DD. Blank is null. */
	private static LocalDate date(String field, String value, List<FieldErrorDetail> errors) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return LocalDate.parse(value.trim());
		}
		catch (DateTimeParseException exception) {
			errors.add(new FieldErrorDetail(field, "Use the date as YYYY-MM-DD."));
			return null;
		}
	}

	private static String method(String field, String value, List<FieldErrorDetail> errors) {
		String method = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
		if (method.isEmpty()) {
			errors.add(new FieldErrorDetail(field, "Choose how the money was sent."));
			return null;
		}
		if (!METHODS.contains(method)) {
			errors.add(new FieldErrorDetail(field, "Choose bank, bKash, Nagad or Rocket."));
			return null;
		}
		return method;
	}

	/** Trimmed text; blank is null; longer than {@code max} is an error. */
	private static String text(String field, String value, int max, List<FieldErrorDetail> errors) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String trimmed = value.trim();
		if (trimmed.length() > max) {
			errors.add(new FieldErrorDetail(field, "Use at most " + max + " characters."));
			return null;
		}
		return trimmed;
	}

	static String roleFilter(String role) {
		if (role == null || role.isBlank()) {
			return null;
		}
		String value = role.trim().toUpperCase(Locale.ROOT);
		if (!SYSTEM_ADMIN.equals(value) && !INSTRUCTOR.equals(value)) {
			throw validation(List.of(new FieldErrorDetail("role", "Role must be SYSTEM_ADMIN or INSTRUCTOR.")));
		}
		return value;
	}

	/* ---------- access ---------- */

	private void requireManage() {
		if (!permissions.has(Permissions.STAFF_PAY_MANAGE)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, DENIED);
		}
	}

	private static UUID actorId() {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor) {
			return actor.userId();
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

	private LocalDate today() {
		return LocalDate.now(clock.withZone(DHAKA));
	}

	private static String dateText(ResultSet rs, String column) throws SQLException {
		LocalDate value = rs.getObject(column, LocalDate.class);
		return value == null ? null : value.toString();
	}

	private static String format(Instant instant) {
		return instant.atZone(DHAKA).format(TIME);
	}

	private static ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	private static ApiException validation(List<FieldErrorDetail> errors) {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.", errors);
	}

	/* ---------- views and forms ---------- */

	/** A System Admin or instructor, read from users and admin_profiles. */
	record Person(UUID id, String role, String fullName, String status, boolean deleted) {
	}

	/** The pay facts the screens need for one person. */
	record Facts(Totals totals, String lastPaymentOn, SettingsRow settings, long accounts) {
	}

	record SettingsRow(UUID userId, BigDecimal monthlySalary, Integer payDay, LocalDate salaryStart, LocalDate salaryEnd,
			BigDecimal sheetRate) {
	}

	record PayoutAccount(UUID id, String kind, String bankName, String accountNumber, String walletNumber) {
	}

	private record VoidTarget(UUID userId, String kind, BigDecimal amount, String method, boolean voided) {
	}

	/** A person in the people table. {@code payNotSetUp} is true when a salary (System Admin) or a live payout account is missing. */
	public record PersonRow(UUID id, String fullName, String role, String status, boolean deleted, BigDecimal balance,
			BigDecimal overdue, String lastPaymentOn, boolean payNotSetUp) {
	}

	/** The person page. */
	public record PersonDetail(UUID id, String fullName, String role, String status, boolean deleted, boolean payNotSetUp,
			SettingsView settings, Summary summary, String nextPayDate, PayoutAccountRow preferredAccount) {
	}

	/**
	 * The totals. {@code charged} is what is earned or due; {@code paid} what was paid; {@code balance} charged minus paid;
	 * {@code owedNow} the balance never below zero; {@code overdue} the part of it past its due date.
	 */
	public record Summary(BigDecimal charged, BigDecimal paid, BigDecimal balance, BigDecimal owedNow, BigDecimal overdue,
			String lastPaymentOn) {
	}

	/** The pay settings. {@code effectiveSheetRate} is the instructor's rate now: the override, else the default. */
	public record SettingsView(BigDecimal monthlySalary, Integer payDay, String salaryStart, String salaryEnd,
			BigDecimal sheetRate, BigDecimal effectiveSheetRate) {
	}

	/** {@code nextPayAmount} is what is due on the next pay date (phase 5); null for an instructor. */
	public record SelfSummary(String role, Summary summary, BigDecimal monthlySalary, Integer payDay, String nextPayDate,
			BigDecimal nextPayAmount) {
	}

	public record PayoutAccountRow(UUID id, String kind, String accountName, String accountNumber, String bankName,
			String branch, String routingNumber, String walletNumber, boolean preferred) {
	}

	/** One money line. Dates are Dhaka dates; times are ISO with the Dhaka offset. */
	public record LedgerRow(UUID id, String kind, BigDecimal amount, String dueOn, String createdAt, String paidOn,
			String method, String accountSnapshot, String reference, String note, boolean voided, String voidedAt,
			String voidReason) {
	}

	public record Defaults(BigDecimal sheetRate, int unpaidReminderDays, int salaryDueSoonDays, String updatedAt) {
	}

	public record PaymentForm(BigDecimal amount, String paidOn, String method, UUID accountId, String reference, String note,
			Boolean allowAdvance) {
	}

	public record AdjustmentForm(BigDecimal amount, String note) {
	}

	public record VoidForm(String reason) {
	}

	public record DefaultsForm(BigDecimal sheetRate, Integer unpaidReminderDays, Integer salaryDueSoonDays) {
	}

	public record SettingsForm(BigDecimal monthlySalary, Integer payDay, String salaryStart, String salaryEnd,
			BigDecimal sheetRate) {
	}

}
