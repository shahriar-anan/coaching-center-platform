package com.coachingcenter.api.exams;

import java.time.Clock;
import java.time.Instant;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
			  g.created_at AS enrolled_at
			FROM live_exam_grants g
			JOIN users u ON u.id = g.student_id
			LEFT JOIN student_profiles sp ON sp.user_id = u.id
			WHERE g.revoked_at IS NULL AND u.deleted_at IS NULL AND g.scope = 'MODULE' AND g.module_id IN (:modules)
			""";

	/** A roster row: access by name on the roster, with no grant behind it. */
	private static final String ROSTER_ROWS = """
			SELECT NULL::uuid AS grant_id, r.student_id, sp.full_name, sp.student_code, u.phone, u.email,
			  'ROSTER' AS source, NULL AS reason, r.created_at AS enrolled_at
			FROM live_exam_roster r
			JOIN users u ON u.id = r.student_id
			LEFT JOIN student_profiles sp ON sp.user_id = u.id
			WHERE u.deleted_at IS NULL AND r.module_id IN (:modules)
			""";

	/** A grant on the exam itself: not revoked, deleted accounts left out. */
	private static final String EXAM_GRANTS = """
			SELECT g.id AS grant_id, g.student_id, sp.full_name, sp.student_code, u.phone, u.email, g.source, g.reason,
			  g.created_at AS enrolled_at
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

	public LiveExamEnrolmentListService(NamedParameterJdbcTemplate jdbc, LiveExamModuleRepository modules,
			LiveExamRepository exams, LiveExamMemberships memberships, LiveExamAccess access, Clock clock) {
		this.jdbc = jdbc;
		this.modules = modules;
		this.exams = exams;
		this.memberships = memberships;
		this.access = access;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Page<EnrolledStudent> forModule(UUID moduleId, Pageable pageable) {
		modules.findByIdAndDeletedAtIsNull(moduleId).orElseThrow(this::notFound);
		Instant now = clock.instant();
		List<Row> rows = new ArrayList<>();
		MapSqlParameterSource params = new MapSqlParameterSource("modules", List.of(moduleId));
		rows.addAll(jdbc.query(MODULE_GRANTS, params, this::row));
		rows.addAll(jdbc.query(ROSTER_ROWS, params, this::row));
		List<EnrolledStudent> sitting = unique(rows).stream()
			.filter(row -> canSit(access.moduleState(row.studentId(), moduleId, now)))
			.map(Row::toView)
			.sorted(NEWEST_FIRST)
			.toList();
		return slice(sitting, pageable);
	}

	@Transactional(readOnly = true)
	public Page<EnrolledStudent> forExam(UUID examId, Pageable pageable) {
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
		List<EnrolledStudent> sitting = unique(rows).stream()
			.filter(row -> canSit(access.state(row.studentId(), exam, now)))
			.map(Row::toView)
			.sorted(NEWEST_FIRST)
			.toList();
		return slice(sitting, pageable);
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
				rs.getTimestamp("enrolled_at") == null ? null : rs.getTimestamp("enrolled_at").toInstant());
	}

	private ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	/** One student's row before the access check. */
	private record Row(UUID grantId, UUID studentId, String fullName, String studentCode, String phone, String email,
			String source, String reason, Instant enrolledAt) {

		EnrolledStudent toView() {
			return new EnrolledStudent(grantId, studentId, fullName, studentCode, phone, email, source, reason, enrolledAt);
		}
	}

	/**
	 * One student who can sit the package or exam. {@code grantId} is null when access comes from a roster row, which has
	 * no grant to revoke; {@code source} is then {@code ROSTER}.
	 */
	public record EnrolledStudent(UUID grantId, UUID studentId, String fullName, String studentCode, String phone,
			String email, String source, String reason, Instant enrolledAt) {
	}

}
