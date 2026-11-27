package com.coachingcenter.api.accounting.ledger;

import static com.coachingcenter.api.accounting.ledger.LedgerSupport.actorId;
import static com.coachingcenter.api.accounting.ledger.LedgerSupport.check;
import static com.coachingcenter.api.accounting.ledger.LedgerSupport.text;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.accounting.AccDates;
import com.coachingcenter.api.accounting.AccMoney;
import com.coachingcenter.api.accounting.AccRefusals;
import com.coachingcenter.api.accounting.AccRules;
import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.FieldErrorDetail;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Entries of the ledger (plan 4.4): a charge, a payment, a spending and an income. Every input goes through AccRules; the amount is
 * worked out for a counted type; "paid now" adds the payment in the same save, pointing at its charge. Nothing is deleted.
 */
@Service
public class AccEntryService {

	public static final String ENTITY = "ACC_ENTRY";

	public static final String CREATED = "ACC_ENTRY_CREATED";

	public static final String MANY_CREATED = "ACC_ENTRIES_CREATED";

	/** Most lines one "many" save may hold (plan 4.4). */
	static final int MAX_LINES = 200;

	/** A copy source row: the charge or spending, with what the copy needs. */
	private static final String ENTRY_ROWS = """
			select e.id, e.no, e.kind, e.status, e.group_id, g.name_en as group_name_en, g.name_bn as group_name_bn, g.direction,
			  e.payee_id, p.name as payee_name, e.type_id, t.name_en as type_name_en, t.name_bn as type_name_bn, t.kind as type_kind,
			  t.unit_en, t.unit_bn, e.entry_date, e.period, e.quantity, e.unit_price, e.amount, e.account_id, a.name as account_name,
			  e.reference, e.counterparty, e.batch_id, b.name_en as batch_name_en, b.name_bn as batch_name_bn, e.live_exam_id,
			  x.title_en as live_exam_title_en, x.title_bn as live_exam_title_bn, e.description, e.note, e.paired_entry_id,
			  e.legacy_source, coalesce(rp.full_name, '') as recorded_by_name, e.recorded_at, e.corrected_at, e.voided_at,
			  vp.full_name as voided_by_name, e.void_reason, e.version
			from acc_entries e
			join acc_groups g on g.id = e.group_id
			left join acc_payees p on p.id = e.payee_id
			left join acc_types t on t.id = e.type_id
			left join acc_accounts a on a.id = e.account_id
			left join offline_batches b on b.id = e.batch_id
			left join live_exams x on x.id = e.live_exam_id
			left join admin_profiles rp on rp.user_id = e.recorded_by
			left join admin_profiles vp on vp.user_id = e.voided_by
			""";

	private final JdbcTemplate jdbc;

	private final AuditRecorder audit;

	private final Clock clock;

	private final ObjectMapper json;

	public AccEntryService(JdbcTemplate jdbc, AuditRecorder audit, Clock clock, ObjectMapper json) {
		this.jdbc = jdbc;
		this.audit = audit;
		this.clock = clock;
		this.json = json;
	}

	/**
	 * One entry as sent. Money and quantity are a JSON string or number (AccMoney reads both); the date and month are YYYY-MM-DD and
	 * YYYY-MM. {@code paidNow} is for a charge only.
	 */
	public record EntryInput(String kind, UUID groupId, UUID payeeId, UUID typeId, String date, String period, Object quantity,
			Object unitPrice, Object amount, UUID accountId, String reference, String counterparty, UUID batchId, UUID liveExamId,
			String description, String note, PaidNow paidNow, UUID sourceEntryId) {
	}

	/** The payment made together with a charge (A6). The amount starts as the charge's when left out. */
	public record PaidNow(UUID accountId, Object amount, String reference) {
	}

	/** Body of POST /entries/many: one to 200 lines, saved together or not at all. */
	public record ManyInput(List<EntryInput> entries) {
	}

	/** One entry as the API answers it (contracts Entry). Money is two decimals, quantities without trailing zeros. */
	public record Entry(UUID id, long no, String kind, String status, UUID groupId, String groupNameEn, String groupNameBn,
			String direction, UUID payeeId, String payeeName, UUID typeId, String typeNameEn, String typeNameBn, String typeKind,
			String unitEn, String unitBn, String date, String period, String quantity, String unitPrice, String amount,
			UUID accountId, String accountName, String reference, String counterparty, UUID batchId, String batchNameEn,
			String batchNameBn, UUID liveExamId, String liveExamTitleEn, String liveExamTitleBn, String description, String note,
			UUID pairedEntryId, String legacy, String recordedByName, String recordedAt, String correctedAt, String voidedAt,
			String voidedByName, String voidReason, long version) {
	}

	/** An entry with its paired entry and its corrections (contracts EntryDetail: the entry's fields at the top level). */
	public record EntryDetail(@JsonUnwrapped Entry entry, Entry paired, List<EntryEdit> edits) {
	}

	/** One correction: when, by whom, why, and the changed fields before and after (contracts EntryEdit). */
	public record EntryEdit(String at, String byName, String reason, Map<String, Object> before, Map<String, Object> after) {
	}

	/**
	 * One draft from "Copy last month" (contracts CopyDraft): the entry's fields dated today, the month moved on, the source's id, and
	 * whether the same entry is already in this month. Money is a string; the draft is saved as an ordinary entry.
	 */
	public record CopyDraft(String kind, UUID groupId, UUID payeeId, UUID typeId, String date, String period, String quantity,
			String unitPrice, String amount, UUID accountId, String counterparty, UUID batchId, UUID liveExamId, String description,
			String note, UUID sourceEntryId, boolean alreadyThisMonth) {
	}

	/** A group as an entry needs it. */
	private record GroupRow(UUID id, String mode, String direction, boolean archived) {
	}

	/**
	 * One row to save: the checked values, the fields the kind keeps, and whether it is paired with the charge saved just before it.
	 */
	record Plan(UUID groupId, AccRules.Checked row, String reference, String counterparty, UUID batchId, UUID liveExamId,
			String description, String note, boolean paired) {
	}

	// ---- reading ----------------------------------------------------------------------------------------------------------

	/** One entry with its paired entry and its corrections. Unknown is 404. */
	@Transactional(readOnly = true)
	public EntryDetail get(UUID id) {
		Entry entry = view(id);
		UUID pairedId = jdbc.query("""
				select id from acc_entries where id = (select paired_entry_id from acc_entries where id = ?)
				   or paired_entry_id = ? order by no limit 1
				""", (rs, number) -> rs.getObject("id", UUID.class), id, id).stream().findFirst().orElse(null);
		Entry paired = pairedId == null ? null : view(pairedId);
		List<EntryEdit> edits = new ArrayList<>();
		jdbc.query("""
				select x.edited_at, x.reason, x.before_values, x.after_values, coalesce(p.full_name, '') as by_name
				from acc_entry_edits x left join admin_profiles p on p.user_id = x.edited_by
				where x.entry_id = ? order by x.edited_at
				""", rs -> {
					edits.add(new EntryEdit(rs.getTimestamp("edited_at").toInstant().toString(), rs.getString("by_name"),
							rs.getString("reason"), values(rs.getString("before_values")), values(rs.getString("after_values"))));
				}, id);
		return new EntryDetail(entry, paired, edits);
	}

	/**
	 * The drafts of "Copy last month": the group's active charges (Payees) or spending (Log expense) in the month (default last month),
	 * dated today, with the period moved one month on. An income group offers nothing.
	 */
	@Transactional(readOnly = true)
	public List<CopyDraft> copySource(UUID groupId, String monthText) {
		GroupRow group = groupRow(groupId);
		LocalDate today = AccDates.today(clock);
		YearMonth source = monthText == null || monthText.isBlank() ? YearMonth.from(today).minusMonths(1)
				: AccDates.parseMonth(monthText, "month");
		String kind = "PAYEES".equals(group.mode()) ? AccRules.CHARGE
				: "EXPENSE".equals(group.direction()) ? AccRules.SPEND : null;
		if (kind == null) {
			return List.of();
		}
		List<CopyDraft> drafts = new ArrayList<>();
		Set<String> thisMonth = new HashSet<>();
		jdbc.query("""
				select payee_id, type_id, period from acc_entries
				where group_id = ? and status = 'ACTIVE' and entry_date between ? and ?
				""", rs -> {
					thisMonth.add(key(rs.getObject("payee_id", UUID.class), rs.getObject("type_id", UUID.class), rs.getString("period")));
				}, groupId, YearMonth.from(today).atDay(1), YearMonth.from(today).atEndOfMonth());
		jdbc.query("""
				select e.id, e.kind, e.payee_id, e.type_id, e.period, e.quantity, e.unit_price, e.amount, e.account_id, e.counterparty,
				  e.batch_id, e.live_exam_id, e.description, e.note, e.legacy_source
				from acc_entries e
				where e.group_id = ? and e.kind = ? and e.status = 'ACTIVE' and e.entry_date between ? and ?
				order by e.entry_date, e.no
				""", rs -> {
					boolean legacy = rs.getString("legacy_source") != null;
					UUID payee = rs.getObject("payee_id", UUID.class);
					UUID type = rs.getObject("type_id", UUID.class);
					String period = rs.getString("period") == null ? null : YearMonth.parse(rs.getString("period")).plusMonths(1).toString();
					// A moved-over charge has a quantity but no price; it is copied as a plain amount, as the old pages typed it.
					String quantity = legacy ? null : quantityText(rs.getBigDecimal("quantity"));
					String unitPrice = legacy ? null : moneyText(rs.getBigDecimal("unit_price"));
					BigDecimal amount = rs.getBigDecimal("amount").abs();
					drafts.add(new CopyDraft(kind, groupId, payee, type, today.toString(), period, quantity, unitPrice,
							AccMoney.format(amount), AccRules.SPEND.equals(kind) ? rs.getObject("account_id", UUID.class) : null,
							AccRules.SPEND.equals(kind) ? rs.getString("counterparty") : null, rs.getObject("batch_id", UUID.class),
							AccRules.CHARGE.equals(kind) ? rs.getObject("live_exam_id", UUID.class) : null,
							rs.getString("description"), rs.getString("note"), rs.getObject("id", UUID.class),
							thisMonth.contains(key(payee, type, period))));
				}, groupId, kind, source.atDay(1), source.atEndOfMonth());
		return drafts;
	}

	// ---- writing ----------------------------------------------------------------------------------------------------------

	/** Saves one entry, or a charge with its payment when "paid now" is given. Returns the saved rows, the charge first. */
	@Transactional
	public List<Entry> create(EntryInput input) {
		List<Plan> plans = plan(input, false);
		List<UUID> ids = save(plans, CREATED);
		return ids.stream().map(this::view).toList();
	}

	/** Saves 1 to 200 charges, spending or income lines together or not at all (plan 4.4). A bad line refuses the whole save. */
	@Transactional
	public List<Entry> createMany(ManyInput body) {
		List<EntryInput> lines = body == null || body.entries() == null ? List.of() : body.entries();
		if (lines.isEmpty()) {
			throw AccRefusals.validation("entries", "Add at least one line.");
		}
		if (lines.size() > MAX_LINES) {
			throw AccRefusals.refuse("too_many_lines");
		}
		List<Plan> plans = new ArrayList<>();
		for (int i = 0; i < lines.size(); i++) {
			try {
				plans.addAll(plan(lines.get(i), true));
			}
			catch (ApiException exception) {
				throw prefixed(exception, i);
			}
		}
		List<UUID> ids = save(plans, MANY_CREATED);
		return ids.stream().map(this::view).toList();
	}

	// ---- rules ------------------------------------------------------------------------------------------------------------

	/** Checks one input and makes its rows (one, or two with "paid now"). Nothing is written here. */
	List<Plan> plan(EntryInput in, boolean many) {
		String kind = in.kind();
		boolean charge = AccRules.CHARGE.equals(kind);
		if (in.groupId() == null) {
			throw AccRefusals.validation("groupId", "Choose a group.");
		}
		LocalDate date = AccDates.parseDay(in.date(), "date");
		List<FieldErrorDetail> problems = new ArrayList<>();
		String reference = text(in.reference(), 100, "reference", problems);
		String counterparty = text(in.counterparty(), 200, "counterparty", problems);
		String description = text(in.description(), 500, "description", problems);
		String note = text(in.note(), 500, "note", problems);
		check(problems);
		if (many && in.paidNow() != null) {
			throw AccRefusals.validation("paidNow", "Paid now is not offered for several lines.");
		}
		if (!charge && in.paidNow() != null) {
			throw AccRefusals.validation("paidNow", "Paid now is only for a charge.");
		}
		GroupRow group = groupRow(in.groupId());
		AccRules.GroupRef groupRef = new AccRules.GroupRef(group.id(), group.mode(), group.direction(), group.archived());
		boolean payeeKind = charge || AccRules.PAYMENT.equals(kind);

		AccRules.PayeeRef payee = null;
		if (payeeKind && in.payeeId() != null) {
			payee = payeeRef(in.payeeId());
			if (payee == null) {
				throw AccRefusals.notFound("payee");
			}
		}
		AccRules.TypeRef type = null;
		if (!AccRules.PAYMENT.equals(kind) && in.typeId() != null) {
			type = typeRef(in.typeId());
			if (type == null) {
				throw AccRefusals.notFound("type");
			}
		}
		UUID accountId = charge ? null : in.accountId();
		AccRules.AccountRef account = null;
		if (accountId != null) {
			account = accountRef(accountId);
			if (account == null) {
				throw AccRefusals.notFound("account");
			}
		}
		UUID batchId = in.batchId();
		if (batchId != null && !exists("select exists (select 1 from offline_batches where id = ?)", batchId)) {
			throw AccRefusals.notFound("batch");
		}
		UUID liveExamId = charge ? in.liveExamId() : null;
		if (liveExamId != null && !exists("select exists (select 1 from live_exams where id = ? and deleted_at is null)", liveExamId)) {
			throw AccRefusals.notFound("exam");
		}

		boolean counted = type != null && "COUNTED".equals(type.kind());
		BigDecimal quantity = AccMoney.parseQuantity(in.quantity(), "quantity");
		BigDecimal unitPrice = AccMoney.parseMoney(in.unitPrice(), "unitPrice");
		// A counted type's amount is quantity times price; a typed amount is ignored for it.
		BigDecimal amount = counted ? null : AccMoney.parseMoney(in.amount(), "amount");
		AccRules.Draft draft = new AccRules.Draft(kind, in.payeeId(), AccRules.PAYMENT.equals(kind) ? null : in.typeId(), accountId, date,
				in.period(), quantity, unitPrice, amount, description);
		AccRules.Checked checked = AccRules.check(draft, groupRef, type, payee, account, null, false, AccDates.today(clock));

		List<Plan> plans = new ArrayList<>();
		String chargeReference = charge ? null : reference;
		String chargeCounterparty = AccRules.SPEND.equals(kind) || AccRules.INCOME.equals(kind) ? counterparty : null;
		plans.add(new Plan(group.id(), checked, chargeReference, chargeCounterparty, batchId, liveExamId, description, note, false));
		if (in.paidNow() != null) {
			PaidNow paidNow = in.paidNow();
			List<FieldErrorDetail> paidProblems = new ArrayList<>();
			String paidReference = text(paidNow.reference(), 100, "reference", paidProblems);
			check(paidProblems);
			AccRules.AccountRef payAccount = null;
			if (paidNow.accountId() != null) {
				payAccount = accountRef(paidNow.accountId());
				if (payAccount == null) {
					throw AccRefusals.notFound("account");
				}
			}
			BigDecimal paid = paidNow.amount() == null ? checked.amount() : AccMoney.parseMoney(paidNow.amount(), "paidAmount");
			AccRules.Checked payment = AccRules.paidNow(checked, paid, payAccount, null);
			plans.add(new Plan(group.id(), payment, paidReference, null, batchId, null, null, null, true));
		}
		return plans;
	}

	/** Writes the rows, the charge first, and audits each one. Returns the ids in order. */
	private List<UUID> save(List<Plan> plans, String action) {
		UUID actor = actorId();
		Timestamp now = Timestamp.from(clock.instant());
		List<UUID> ids = new ArrayList<>();
		UUID charge = null;
		for (Plan plan : plans) {
			UUID id = insert(plan, plan.paired() ? charge : null, actor, now);
			if (!plan.paired()) {
				charge = id;
			}
			ids.add(id);
			audit.record(action, ENTITY, id);
		}
		return ids;
	}

	private UUID insert(Plan plan, UUID pairedId, UUID actor, Timestamp now) {
		UUID id = UUID.randomUUID();
		AccRules.Checked c = plan.row();
		jdbc.update("""
				insert into acc_entries (id, kind, status, group_id, payee_id, type_id, entry_date, period, quantity, unit_price, amount,
				  account_id, reference, counterparty, batch_id, live_exam_id, description, note, paired_entry_id, recorded_by,
				  recorded_at, created_at, updated_at, version)
				values (?, ?, 'ACTIVE', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
				""", id, c.kind(), plan.groupId(), c.payeeId(), c.typeId(), c.date(), c.period(), c.quantity(), c.unitPrice(),
				c.amount(), c.accountId(), plan.reference(), plan.counterparty(), plan.batchId(), plan.liveExamId(),
				plan.description(), plan.note(), pairedId, actor, now, now, now);
		return id;
	}

	/** An error of one line of a "many" save, with its field named as entries[i].field (so the web can show the line). */
	private static ApiException prefixed(ApiException exception, int line) {
		if (!(exception.details() instanceof List<?> problems) || problems.isEmpty()) {
			return exception;
		}
		List<FieldErrorDetail> named = new ArrayList<>();
		for (Object problem : problems) {
			if (problem instanceof FieldErrorDetail detail) {
				named.add(new FieldErrorDetail("entries[" + line + "]." + detail.field(), detail.message()));
			}
		}
		return named.isEmpty() ? exception : AccRefusals.validation(named);
	}

	// ---- loading ----------------------------------------------------------------------------------------------------------

	private GroupRow groupRow(UUID id) {
		List<GroupRow> found = jdbc.query("select id, mode, direction, archived_at from acc_groups where id = ?",
				(rs, number) -> new GroupRow(rs.getObject("id", UUID.class), rs.getString("mode"), rs.getString("direction"),
						rs.getTimestamp("archived_at") != null), id);
		if (found.isEmpty()) {
			throw AccRefusals.notFound("group");
		}
		return found.get(0);
	}

	private AccRules.PayeeRef payeeRef(UUID id) {
		List<AccRules.PayeeRef> found = jdbc.query("select id, group_id from acc_payees where id = ?",
				(rs, number) -> new AccRules.PayeeRef(rs.getObject("id", UUID.class), rs.getObject("group_id", UUID.class)), id);
		return found.isEmpty() ? null : found.get(0);
	}

	private AccRules.TypeRef typeRef(UUID id) {
		List<AccRules.TypeRef> found = jdbc.query("""
				select id, group_id, kind, needs_period, needs_description, archived_at from acc_types where id = ?
				""", (rs, number) -> new AccRules.TypeRef(rs.getObject("id", UUID.class), rs.getObject("group_id", UUID.class),
						rs.getString("kind"), rs.getBoolean("needs_period"), rs.getBoolean("needs_description"),
						rs.getTimestamp("archived_at") != null), id);
		return found.isEmpty() ? null : found.get(0);
	}

	private AccRules.AccountRef accountRef(UUID id) {
		List<AccRules.AccountRef> found = jdbc.query("select id, archived_at from acc_accounts where id = ?",
				(rs, number) -> new AccRules.AccountRef(rs.getObject("id", UUID.class), rs.getTimestamp("archived_at") != null), id);
		return found.isEmpty() ? null : found.get(0);
	}

	private boolean exists(String sql, UUID id) {
		return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, id));
	}

	private Entry view(UUID id) {
		List<Entry> found = jdbc.query(ENTRY_ROWS + " where e.id = ?", (rs, number) -> entry(rs), id);
		if (found.isEmpty()) {
			throw AccRefusals.notFound("entry");
		}
		return found.get(0);
	}

	private static Entry entry(ResultSet rs) throws SQLException {
		return new Entry(rs.getObject("id", UUID.class), rs.getLong("no"), rs.getString("kind"), rs.getString("status"),
				rs.getObject("group_id", UUID.class), rs.getString("group_name_en"), rs.getString("group_name_bn"),
				rs.getString("direction"), rs.getObject("payee_id", UUID.class), rs.getString("payee_name"),
				rs.getObject("type_id", UUID.class), rs.getString("type_name_en"), rs.getString("type_name_bn"),
				rs.getString("type_kind"), rs.getString("unit_en"), rs.getString("unit_bn"),
				rs.getDate("entry_date").toLocalDate().toString(), rs.getString("period"), quantityText(rs.getBigDecimal("quantity")),
				moneyText(rs.getBigDecimal("unit_price")), AccMoney.format(rs.getBigDecimal("amount")),
				rs.getObject("account_id", UUID.class), rs.getString("account_name"), rs.getString("reference"),
				rs.getString("counterparty"), rs.getObject("batch_id", UUID.class), rs.getString("batch_name_en"),
				rs.getString("batch_name_bn"), rs.getObject("live_exam_id", UUID.class), rs.getString("live_exam_title_en"),
				rs.getString("live_exam_title_bn"), rs.getString("description"), rs.getString("note"),
				rs.getObject("paired_entry_id", UUID.class), rs.getString("legacy_source"), rs.getString("recorded_by_name"),
				rs.getTimestamp("recorded_at").toInstant().toString(), instant(rs.getTimestamp("corrected_at")),
				instant(rs.getTimestamp("voided_at")), rs.getString("voided_by_name"), rs.getString("void_reason"),
				rs.getLong("version"));
	}

	private static String instant(Timestamp value) {
		return value == null ? null : value.toInstant().toString();
	}

	private static String moneyText(BigDecimal value) {
		return value == null ? null : AccMoney.format(value);
	}

	private static String quantityText(BigDecimal value) {
		return value == null ? null : AccMoney.formatQuantity(value);
	}

	/** A correction's before or after values, read from its JSON column. */
	private Map<String, Object> values(String text) {
		if (text == null) {
			return Map.of();
		}
		JsonNode node = json.readTree(text);
		return json.convertValue(node, Map.class);
	}

	private static String key(UUID payee, UUID type, String period) {
		return payee + "|" + type + "|" + period;
	}

}
