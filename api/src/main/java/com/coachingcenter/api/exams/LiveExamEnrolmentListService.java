package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

/**
 * The students who can sit a package or an exam, newest first, so a Master Admin can see who was just added.
 * "Can sit" is the same rule the access check uses: a grant or a roster row, not revoked and not run out. Purchases,
 * hand grants and roster rows all count, and an archived package keeps the students it already had.
 */
@Service
public class LiveExamEnrolmentListService {

	private static final int MAX_SIZE = 100;

	/** A grant on the package itself: not revoked, deleted accounts left out. */
	private static final String MODULE_GRANTS = """
			SELECT g.id AS grant_id, g.student_id, sp.full_name, sp.student_code, u.phone, u.email, g.source, g.reason,
			  g.created_at AS enrolled_at, g.scope AS grant_scope, g.module_id AS module_id
			FROM live_exam_grants g
			JOIN users u ON u.id = g.student_id
			LEFT JOIN student_profiles sp ON sp.user_id = u.id
			WHERE g.revoked_at IS NULL AND u.deleted_at IS NULL AND g.scope = 'MODULE' AND g.module_id IN (:modules)
			""";

	/** A roster row: access by name on the roster, with no grant behind it. */
	private static final String ROSTER_ROWS = """
			SELECT NULL::uuid AS grant_id, r.student_id, sp.full_name, sp.student_code, u.phone, u.email,
			  'ROSTER' AS source, NULL AS reason, r.created_at AS enrolled_at, NULL AS grant_scope, r.module_id AS module_id
			FROM live_exam_roster r
			JOIN users u ON u.id = r.student_id
			LEFT JOIN student_profiles sp ON sp.user_id = u.id
			WHERE u.deleted_at IS NULL AND r.module_id IN (:modules)
			""";

	/** A grant on the exam itself: not revoked, deleted accounts left out. An exam grant names no package, so module_id is null. */
	private static final String EXAM_GRANTS = """
			SELECT g.id AS grant_id, g.student_id, sp.full_name, sp.student_code, u.phone, u.email, g.source, g.reason,
			  g.created_at AS enrolled_at, g.scope AS grant_scope, NULL::uuid AS module_id
			FROM live_exam_grants g
			JOIN users u ON u.id = g.student_id
			LEFT JOIN student_profiles sp ON sp.user_id = u.id
			WHERE g.revoked_at IS NULL AND u.deleted_at IS NULL AND g.scope = 'EXAM' AND g.exam_id = :examId
			""";

	private final NamedParameterJdbcTemplate jdbc;

	private final LiveExamModuleRepository modules;

	private final LiveExamRepository exams;

	private final LiveExamMemberships memberships;

	private final LiveExamAccess access;

	private final Clock clock;

	private final InstallmentPlanReader plans;

	public LiveExamEnrolmentListService(NamedParameterJdbcTemplate jdbc, LiveExamModuleRepository modules,
			LiveExamRepository exams, LiveExamMemberships memberships, LiveExamAccess access, Clock clock,
			InstallmentPlanReader plans) {
		this.jdbc = jdbc;
		this.modules = modules;
		this.exams = exams;
		this.memberships = memberships;
		this.access = access;
		this.clock = clock;
		this.plans = plans;
	}

	/**
	 * {@code withMoney} is true only for a caller holding {@code LIVE_EXAM_INSTALLMENT}. Only then does a row carry its plan id,
	 * paid, due and status (all null otherwise). {@code query} is optional and matches name, phone, email or student code.
	 */
	@Transactional(readOnly = true)
	public Page<EnrolledStudent> forModule(UUID moduleId, String query, Pageable pageable, boolean withMoney) {
		modules.findByIdAndDeletedAtIsNull(moduleId).orElseThrow(this::notFound);
		Instant now = clock.instant();
		List<Row> rows = new ArrayList<>();
		MapSqlParameterSource params = new MapSqlParameterSource("modules", List.of(moduleId));
		rows.addAll(jdbc.query(MODULE_GRANTS, params, this::row));
		rows.addAll(jdbc.query(ROSTER_ROWS, params, this::row));
		Set<UUID> rostered = rosterStudents(List.of(moduleId));
		Set<UUID> started = startedInModule(moduleId);
		String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		List<EnrolledStudent> sitting = unique(rows).stream()
			.filter(row -> canSit(access.moduleState(row.studentId(), moduleId, now)))
			.filter(row -> matches(row, q))
			.map(row -> row.toView(rostered.contains(row.studentId()), started.contains(row.studentId())))
			.sorted(NEWEST_FIRST)
			.toList();
		return withPlans(slice(sitting, pageable), withMoney);
	}

	/**
	 * The students who can sit an exam, through its own grant or a package it is in. {@code startedAny} is true when the student
	 * has a sitting on this exam.
	 */
	@Transactional(readOnly = true)
	public Page<EnrolledStudent> forExam(UUID examId, Pageable pageable, boolean withMoney) {
		LiveExam exam = exams.findByIdAndDeletedAtIsNull(examId).orElseThrow(this::notFound);
		Instant now = clock.instant();
		List<UUID> packages = memberships.activeModuleIds(examId);
		List<Row> rows = new ArrayList<>();
		// The exam's own grants come first, so a student with both an exam grant and package access is shown once, by the grant.
		rows.addAll(jdbc.query(EXAM_GRANTS, new MapSqlParameterSource("examId", examId), this::row));
		if (!packages.isEmpty()) {
			MapSqlParameterSource params = new MapSqlParameterSource("modules", packages);
			rows.addAll(jdbc.query(MODULE_GRANTS, params, this::row));
			rows.addAll(jdbc.query(ROSTER_ROWS, params, this::row));
		}
		Set<UUID> rostered = packages.isEmpty() ? Set.of() : rosterStudents(packages);
		Set<UUID> started = startedOnExam(examId);
		List<EnrolledStudent> sitting = unique(rows).stream()
			.filter(row -> canSit(access.state(row.studentId(), exam, now)))
			.map(row -> row.toView(rostered.contains(row.studentId()), started.contains(row.studentId())))
			.sorted(NEWEST_FIRST)
			.toList();
		return withPlans(slice(sitting, pageable), withMoney);
	}

	/** Whether the row matches the search text. An empty text matches every row. {@code q} is already trimmed and lower-cased. */
	private static boolean matches(Row row, String q) {
		if (q.isEmpty()) {
			return true;
		}
		return contains(row.fullName(), q) || contains(row.phone(), q) || contains(row.email(), q)
				|| contains(row.studentCode(), q);
	}

	private static boolean contains(String value, String q) {
		return value != null && value.toLowerCase(Locale.ROOT).contains(q);
	}

	/** The students with a roster row on any of these packages, whether or not the row's access has ended. */
	private Set<UUID> rosterStudents(Collection<UUID> moduleIds) {
		return new HashSet<>(jdbc.queryForList("SELECT student_id FROM live_exam_roster WHERE module_id IN (:modules)",
				new MapSqlParameterSource("modules", moduleIds), UUID.class));
	}

	/** The students with a sitting on any exam of the package. */
	private Set<UUID> startedInModule(UUID moduleId) {
		return new HashSet<>(jdbc.queryForList("""
				SELECT DISTINCT p.student_id FROM live_exam_participants p
				JOIN live_exams e ON e.id = p.exam_id
				WHERE e.module_id = :moduleId AND p.deleted_at IS NULL
				""", new MapSqlParameterSource("moduleId", moduleId), UUID.class));
	}

	/** The students with a sitting on this exam. */
	private Set<UUID> startedOnExam(UUID examId) {
		return new HashSet<>(jdbc.queryForList("""
				SELECT DISTINCT p.student_id FROM live_exam_participants p
				WHERE p.exam_id = :examId AND p.deleted_at IS NULL
				""", new MapSqlParameterSource("examId", examId), UUID.class));
	}

	/** Fills each row's plan from its grant, for the callers who may see money. Other rows and callers are left as they are. */
	private Page<EnrolledStudent> withPlans(Page<EnrolledStudent> page, boolean withMoney) {
		if (!withMoney || page.isEmpty()) {
			return page;
		}
		List<UUID> grants = page.getContent().stream().map(EnrolledStudent::grantId).filter(Objects::nonNull).toList();
		Map<UUID, InstallmentPlanReader.PlanRow> byGrant = plans.byGrantIds(grants);
		List<EnrolledStudent> content = page.getContent().stream().map(student -> {
			InstallmentPlanReader.PlanRow plan = student.grantId() == null ? null : byGrant.get(student.grantId());
			return plan == null ? student : student.withPlan(plan.planId(), plan.paid(), plan.due(), plan.status());
		}).toList();
		return new PageImpl<>(content, page.getPageable(), page.getTotalElements());
	}

	private static final Comparator<EnrolledStudent> NEWEST_FIRST =
			Comparator.comparing(EnrolledStudent::enrolledAt, Comparator.nullsLast(Comparator.reverseOrder()))
				.thenComparing(EnrolledStudent::studentId);

	/**
	 * Access is granted for a grant or roster row that has not run out. The access state keeps a run-out grant as
	 * {@code granted} with {@code expired} set, so both flags are checked.
	 */
	private static boolean canSit(AccessState state) {
		return state.granted() && !state.expired();
	}

	/** One row per student, keeping the first one seen: callers list the strongest source first. */
	private static List<Row> unique(List<Row> rows) {
		Map<UUID, Row> byStudent = new LinkedHashMap<>();
		for (Row row : rows) {
			byStudent.putIfAbsent(row.studentId(), row);
		}
		return new ArrayList<>(byStudent.values());
	}

	private static Page<EnrolledStudent> slice(List<EnrolledStudent> all, Pageable pageable) {
		int size = Math.min(Math.max(pageable.getPageSize(), 1), MAX_SIZE);
		PageRequest page = PageRequest.of(pageable.getPageNumber(), size);
		long from = (long) page.getPageNumber() * size;
		List<EnrolledStudent> content = from >= all.size() ? List.of() : all.subList((int) from, (int) Math.min(all.size(), from + size));
		return new PageImpl<>(content, page, all.size());
	}

	private Row row(ResultSet rs, int number) throws SQLException {
		return new Row(rs.getObject("grant_id", UUID.class), rs.getObject("student_id", UUID.class),
				rs.getString("full_name"), rs.getString("student_code"), rs.getString("phone"), rs.getString("email"),
				rs.getString("source"), rs.getString("reason"),
				rs.getTimestamp("enrolled_at") == null ? null : rs.getTimestamp("enrolled_at").toInstant(),
				rs.getString("grant_scope"), rs.getObject("module_id", UUID.class));
	}

	private ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	/**
	 * One student's row before the access check. {@code grantScope} is MODULE or EXAM, or null for a roster row. {@code moduleId}
	 * is the package the row belongs to: set for a package grant and a roster row, null for an exam grant (plan 14.11, P61 to P63).
	 */
	private record Row(UUID grantId, UUID studentId, String fullName, String studentCode, String phone, String email,
			String source, String reason, Instant enrolledAt, String grantScope, UUID moduleId) {

		EnrolledStudent toView(boolean onRoster, boolean startedAny) {
			return new EnrolledStudent(grantId, studentId, fullName, studentCode, phone, email, source, reason, enrolledAt,
					null, null, null, null, onRoster, startedAny, grantScope, moduleId);
		}
	}

	/**
	 * One student who can sit the package or exam. {@code grantId} is null when access comes from a roster row, which has
	 * no grant to revoke; {@code source} is then {@code ROSTER}. {@code paymentPlanId}, {@code paid}, {@code due} and
	 * {@code planStatus} are set only for a caller who may see installments, and only when the grant has a plan.
	 * {@code onRoster} is true when the student has a roster row on the package (for an exam, a package that holds it), whether
	 * or not that row's access has ended. {@code startedAny} is true when the student has a sitting on the package's exams (for
	 * an exam list, on that exam). {@code grantScope} is MODULE or EXAM, or null for a roster row. {@code moduleId} is the
	 * package the row belongs to, or null for an exam grant, so an exam page can link to the package.
	 */
	public record EnrolledStudent(UUID grantId, UUID studentId, String fullName, String studentCode, String phone,
			String email, String source, String reason, Instant enrolledAt, UUID paymentPlanId, BigDecimal paid,
			BigDecimal due, String planStatus, boolean onRoster, boolean startedAny, String grantScope, UUID moduleId) {

		EnrolledStudent withPlan(UUID planId, BigDecimal paid, BigDecimal due, String status) {
			return new EnrolledStudent(grantId, studentId, fullName, studentCode, phone, email, source, reason, enrolledAt,
					planId, paid, due, status, onRoster, startedAny, grantScope, moduleId);
		}
	}

}
