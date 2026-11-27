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
 * Types (plan 4.2): the sub-categories of one group. The kind is fixed once an entry uses the type, a counted type has a unit, and a
 * deduction is for Payees groups only. Names are unique among the live types of a group.
 */
@Service
public class AccTypeService {

	public static final String ENTITY = "ACC_TYPE";

	public static final String CREATED = "ACC_TYPE_CREATED";

	public static final String UPDATED = "ACC_TYPE_UPDATED";

	public static final String ARCHIVED = "ACC_TYPE_ARCHIVED";

	public static final String RESTORED = "ACC_TYPE_RESTORED";

	static final int MAX_NAME = 100;

	static final int MAX_UNIT = 30;

	private static final Set<String> KINDS = Set.of("COUNTED", "AMOUNT", "DEDUCTION");

	/** The types of the rows named by the where clause. {@code used}: an entry of any state has this type, so the kind is fixed. */
	private static final String TYPE_ROWS = """
			select t.id, t.group_id, t.name_en, t.name_bn, t.kind, t.unit_en, t.unit_bn, t.needs_period, t.needs_description,
			  t.position, t.archived_at, t.version,
			  exists (select 1 from acc_entries e where e.type_id = t.id) as used
			from acc_types t
			""";

	private final JdbcTemplate jdbc;

	private final AuditRecorder audit;

	private final Clock clock;

	public AccTypeService(JdbcTemplate jdbc, AuditRecorder audit, Clock clock) {
		this.jdbc = jdbc;
		this.audit = audit;
		this.clock = clock;
	}

	/** A type as the API answers it. {@code used}: an entry has this type, so its kind cannot change. */
	public record TypeView(UUID id, UUID groupId, String nameEn, String nameBn, String kind, String unitEn, String unitBn,
			boolean needsPeriod, boolean needsDescription, int position, boolean archived, boolean used, long version) {
	}

	/** Body of POST /groups/{id}/types. A counted type needs a unit; the flags left out mean off. */
	public record CreateType(String nameEn, String nameBn, String kind, String unitEn, String unitBn, Boolean needsPeriod,
			Boolean needsDescription) {
	}

	/**
	 * Body of PATCH /types/{id}. A kind left out keeps the kind; a name left out keeps the name; a unit sent null is cleared (a counted
	 * type must keep one); the flags left out keep their value. {@code version} is required.
	 */
	public record UpdateType(String nameEn, String nameBn, String kind, String unitEn, String unitBn, Boolean needsPeriod,
			Boolean needsDescription, Long version) {
	}

	/** The group's type row and the group's mode, locked for an edit. */
	private record Locked(UUID id, UUID groupId, String nameEn, String kind, String unitEn, boolean needsPeriod, boolean needsDescription,
			boolean archived, long version, String mode) {
	}

	@Transactional(readOnly = true)
	public List<TypeView> list(UUID groupId, boolean includeArchived) {
		requireGroup(groupId);
		return jdbc.query(TYPE_ROWS + " where t.group_id = ? and (cast(? as boolean) or t.archived_at is null)"
				+ " order by t.position, t.name_en", (rs, number) -> view(rs), groupId, includeArchived);
	}

	@Transactional
	public TypeView create(UUID groupId, CreateType body) {
		String mode = lockGroup(groupId);
		List<FieldErrorDetail> problems = new ArrayList<>();
		String nameEn = required(body.nameEn(), MAX_NAME, "nameEn", problems);
		String nameBn = text(body.nameBn(), MAX_NAME, "nameBn", problems);
		String kind = choice(body.kind(), KINDS, "kind", problems);
		boolean counted = "COUNTED".equals(kind);
		String unitEn = counted ? unit(body.unitEn(), "unitEn", problems, true) : null;
		String unitBn = counted ? unit(body.unitBn(), "unitBn", problems, false) : null;
		check(problems);
		if (groupArchived(groupId)) {
			throw AccRefusals.refuse("group_archived");
		}
		if ("DEDUCTION".equals(kind) && !"PAYEES".equals(mode)) {
			throw AccRefusals.refuse("group_mode");
		}
		if (nameTaken(groupId, nameEn, null)) {
			throw AccRefusals.refuse("name_taken");
		}
		UUID id = UUID.randomUUID();
		Timestamp now = Timestamp.from(clock.instant());
		int position = jdbc.queryForObject("select coalesce(max(position), 0) + 1 from acc_types where group_id = ?", Integer.class,
				groupId);
		jdbc.update("""
				insert into acc_types (id, group_id, name_en, name_bn, kind, unit_en, unit_bn, needs_period, needs_description, position,
				  created_at, updated_at, version)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
				""", id, groupId, nameEn, nameBn, kind, unitEn, unitBn, Boolean.TRUE.equals(body.needsPeriod()),
				Boolean.TRUE.equals(body.needsDescription()), position, now, now);
		audit.record(CREATED, ENTITY, id);
		return one(id);
	}

	@Transactional
	public TypeView update(UUID id, UpdateType body) {
		Locked type = lock(id);
		if (type.archived()) {
			throw AccRefusals.refuse("type_archived");
		}
		checkVersion(body.version(), type.version());
		List<FieldErrorDetail> problems = new ArrayList<>();
		String nameEn = body.nameEn() == null ? type.nameEn() : required(body.nameEn(), MAX_NAME, "nameEn", problems);
		String nameBn = text(body.nameBn(), MAX_NAME, "nameBn", problems);
		String kind = body.kind() == null ? type.kind() : choice(body.kind(), KINDS, "kind", problems);
		boolean counted = "COUNTED".equals(kind);
		String unitEn = counted ? unit(body.unitEn(), "unitEn", problems, true) : null;
		String unitBn = counted ? unit(body.unitBn(), "unitBn", problems, false) : null;
		boolean needsPeriod = body.needsPeriod() == null ? type.needsPeriod() : body.needsPeriod();
		boolean needsDescription = body.needsDescription() == null ? type.needsDescription() : body.needsDescription();
		check(problems);
		if (!kind.equals(type.kind()) && one(id).used()) {
			throw AccRefusals.refuse("type_kind_used");
		}
		if ("DEDUCTION".equals(kind) && !"PAYEES".equals(type.mode())) {
			throw AccRefusals.refuse("group_mode");
		}
		if (nameTaken(type.groupId(), nameEn, id)) {
			throw AccRefusals.refuse("name_taken");
		}
		jdbc.update("""
				update acc_types set name_en = ?, name_bn = ?, kind = ?, unit_en = ?, unit_bn = ?, needs_period = ?,
				  needs_description = ?, version = version + 1, updated_at = ? where id = ?
				""", nameEn, nameBn, kind, unitEn, unitBn, needsPeriod, needsDescription, Timestamp.from(clock.instant()), id);
		audit.record(UPDATED, ENTITY, id);
		return one(id);
	}

	@Transactional
	public TypeView archive(UUID id) {
		Locked type = lock(id);
		if (!type.archived()) {
			Timestamp now = Timestamp.from(clock.instant());
			jdbc.update("update acc_types set archived_at = ?, version = version + 1, updated_at = ? where id = ?", now, now, id);
			audit.record(ARCHIVED, ENTITY, id);
		}
		return one(id);
	}

	@Transactional
	public TypeView restore(UUID id) {
		Locked type = lock(id);
		if (type.archived()) {
			if (groupArchived(type.groupId())) {
				throw AccRefusals.refuse("group_archived");
			}
			if (nameTaken(type.groupId(), type.nameEn(), id)) {
				throw AccRefusals.refuse("name_taken");
			}
			jdbc.update("update acc_types set archived_at = null, version = version + 1, updated_at = ? where id = ?",
					Timestamp.from(clock.instant()), id);
			audit.record(RESTORED, ENTITY, id);
		}
		return one(id);
	}

	/** Puts the live types of a group in the order sent. The ids must be exactly the group's live types (else 400). */
	@Transactional
	public List<TypeView> order(UUID groupId, List<UUID> ids) {
		lockGroup(groupId);
		List<UUID> live = jdbc.queryForList("select id from acc_types where group_id = ? and archived_at is null order by position, name_en",
				UUID.class, groupId);
		checkOrder(ids, live);
		Timestamp now = Timestamp.from(clock.instant());
		for (int i = 0; i < ids.size(); i++) {
			int changed = jdbc.update("update acc_types set position = ?, updated_at = ? where id = ? and position <> ?", i + 1, now,
					ids.get(i), i + 1);
			if (changed > 0) {
				audit.record(UPDATED, ENTITY, ids.get(i));
			}
		}
		return jdbc.query(TYPE_ROWS + " where t.group_id = ? and t.archived_at is null order by t.position, t.name_en",
				(rs, number) -> view(rs), groupId);
	}

	/** A unit: required for a counted type (when {@code required}), trimmed, at most 30 characters. */
	private static String unit(String raw, String field, List<FieldErrorDetail> problems, boolean required) {
		if (required && (raw == null || raw.isBlank())) {
			problems.add(new FieldErrorDetail(field, "Enter the unit, for example class or answer sheet."));
			return null;
		}
		return text(raw, MAX_UNIT, field, problems);
	}

	/** Locks the group row and returns its mode. An unknown group is 404. */
	private String lockGroup(UUID groupId) {
		List<String> modes = jdbc.queryForList("select mode from acc_groups where id = ? for update", String.class, groupId);
		if (modes.isEmpty()) {
			throw AccRefusals.notFound("group");
		}
		return modes.get(0);
	}

	private void requireGroup(UUID groupId) {
		if (!Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from acc_groups where id = ?)", Boolean.class, groupId))) {
			throw AccRefusals.notFound("group");
		}
	}

	private boolean groupArchived(UUID groupId) {
		return Boolean.TRUE.equals(jdbc.queryForObject("select archived_at is not null from acc_groups where id = ?", Boolean.class,
				groupId));
	}

	private Locked lock(UUID id) {
		List<Locked> found = jdbc.query("""
				select t.id, t.group_id, t.name_en, t.kind, t.unit_en, t.needs_period, t.needs_description, t.archived_at, t.version,
				  g.mode
				from acc_types t join acc_groups g on g.id = t.group_id
				where t.id = ? for update of t
				""", (rs, number) -> locked(rs), id);
		if (found.isEmpty()) {
			throw AccRefusals.notFound("type");
		}
		return found.get(0);
	}

	private static Locked locked(ResultSet rs) throws SQLException {
		return new Locked(rs.getObject("id", UUID.class), rs.getObject("group_id", UUID.class), rs.getString("name_en"),
				rs.getString("kind"), rs.getString("unit_en"), rs.getBoolean("needs_period"), rs.getBoolean("needs_description"),
				rs.getTimestamp("archived_at") != null, rs.getLong("version"), rs.getString("mode"));
	}

	/** Whether another live type of the group has this name, ignoring case. {@code exceptId} is the type being changed, or null. */
	private boolean nameTaken(UUID groupId, String name, UUID exceptId) {
		return Boolean.TRUE.equals(jdbc.queryForObject("""
				select exists (select 1 from acc_types where group_id = ? and archived_at is null and lower(name_en) = lower(?)
				  and (cast(? as uuid) is null or id <> cast(? as uuid)))
				""", Boolean.class, groupId, name, exceptId, exceptId));
	}

	private TypeView one(UUID id) {
		return jdbc.query(TYPE_ROWS + " where t.id = ?", (rs, number) -> view(rs), id).get(0);
	}

	private static TypeView view(ResultSet rs) throws SQLException {
		return new TypeView(rs.getObject("id", UUID.class), rs.getObject("group_id", UUID.class), rs.getString("name_en"),
				rs.getString("name_bn"), rs.getString("kind"), rs.getString("unit_en"), rs.getString("unit_bn"),
				rs.getBoolean("needs_period"), rs.getBoolean("needs_description"), rs.getInt("position"),
				rs.getTimestamp("archived_at") != null, rs.getBoolean("used"), rs.getLong("version"));
	}

}
