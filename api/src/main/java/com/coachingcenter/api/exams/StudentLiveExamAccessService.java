package com.coachingcenter.api.exams;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

/**
 * What a student can sit, and why, for staff. One row per roster membership and one row per purchase grant, so a
 * student with both a roster row and a purchase shows both reasons. An exam purchase row names its one exam. A grant
 * row carries the grant id, which a revoke needs; a roster row carries none. Each row says when its access ends, and an
 * exam that is free is not listed (NR-01, C3). An exam's package on its row is the primary one, for display only.
 */
@Service
public class StudentLiveExamAccessService {

	static final String ROSTER = "ROSTER";

	static final String MODULE_PURCHASE = "MODULE_PURCHASE";

	static final String EXAM_PURCHASE = "EXAM_PURCHASE";

	private static final int MAX_SIZE = 100;

	private static final String ROWS = """
			SELECT m.id AS module_id, m.title_en AS module_title_en, m.title_bn AS module_title_bn,
			  'ROSTER' AS via, CAST(NULL AS UUID) AS order_id, CAST(NULL AS VARCHAR) AS tran_id,
			  CAST(NULL AS UUID) AS exam_id, CAST(NULL AS VARCHAR) AS exam_title_en, CAST(NULL AS VARCHAR) AS exam_title_bn,
			  CAST(NULL AS UUID) AS grant_id, r.created_at AS granted_at
			FROM live_exam_roster r
			JOIN live_exam_modules m ON m.id = r.module_id AND m.deleted_at IS NULL
			WHERE r.student_id = :studentId
			UNION ALL
			SELECT m.id, m.title_en, m.title_bn, 'MODULE_PURCHASE', g.order_id, o.tran_id,
			  CAST(NULL AS UUID), CAST(NULL AS VARCHAR), CAST(NULL AS VARCHAR), g.id, g.created_at
			FROM live_exam_grants g
			JOIN live_exam_modules m ON m.id = g.module_id AND m.deleted_at IS NULL
			JOIN live_exam_orders o ON o.id = g.order_id
			WHERE g.student_id = :studentId AND g.scope = 'MODULE' AND g.revoked_at IS NULL
			UNION ALL
			SELECT pm.id, pm.title_en, pm.title_bn, 'EXAM_PURCHASE', g.order_id, o.tran_id,
			  e.id, e.title_en, e.title_bn, g.id, g.created_at
			FROM live_exam_grants g
			JOIN live_exams e ON e.id = g.exam_id AND e.deleted_at IS NULL
			JOIN live_exam_orders o ON o.id = g.order_id
			LEFT JOIN LATERAL (
			  SELECT m.id, m.title_en, m.title_bn FROM live_exam_package_exams p
			  JOIN live_exam_modules m ON m.id = p.module_id AND m.deleted_at IS NULL
			  WHERE p.exam_id = e.id AND p.removed_at IS NULL
			  ORDER BY p.added_at, p.id LIMIT 1
			) pm ON TRUE
			WHERE g.student_id = :studentId AND g.scope = 'EXAM' AND NOT e.is_free AND g.revoked_at IS NULL
			UNION ALL
			SELECT m.id, m.title_en, m.title_bn, 'MANUAL', CAST(NULL AS UUID), CAST(NULL AS VARCHAR),
			  CAST(NULL AS UUID), CAST(NULL AS VARCHAR), CAST(NULL AS VARCHAR), g.id, g.created_at
			FROM live_exam_grants g
			JOIN live_exam_modules m ON m.id = g.module_id AND m.deleted_at IS NULL
			WHERE g.student_id = :studentId AND g.scope = 'MODULE' AND g.source = 'MANUAL' AND g.revoked_at IS NULL
			UNION ALL
			SELECT pm.id, pm.title_en, pm.title_bn, 'MANUAL', CAST(NULL AS UUID), CAST(NULL AS VARCHAR),
			  e.id, e.title_en, e.title_bn, g.id, g.created_at
			FROM live_exam_grants g
			JOIN live_exams e ON e.id = g.exam_id AND e.deleted_at IS NULL
			LEFT JOIN LATERAL (
			  SELECT m.id, m.title_en, m.title_bn FROM live_exam_package_exams p
			  JOIN live_exam_modules m ON m.id = p.module_id AND m.deleted_at IS NULL
			  WHERE p.exam_id = e.id AND p.removed_at IS NULL
			  ORDER BY p.added_at, p.id LIMIT 1
			) pm ON TRUE
			WHERE g.student_id = :studentId AND g.scope = 'EXAM' AND g.source = 'MANUAL' AND NOT e.is_free
			  AND g.revoked_at IS NULL
			""";

	private final NamedParameterJdbcTemplate jdbc;

	private final LiveExamAccess access;

	private final LiveExamRepository liveExams;

	private final Clock clock;

	StudentLiveExamAccessService(JdbcTemplate jdbc, LiveExamAccess access, LiveExamRepository liveExams, Clock clock) {
		this.jdbc = new NamedParameterJdbcTemplate(jdbc);
		this.access = access;
		this.liveExams = liveExams;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Page<AccessRow> list(UUID studentId, Pageable pageable) {
		requireStudent(studentId);
		int size = Math.min(Math.max(pageable.getPageSize(), 1), MAX_SIZE);
		Pageable page = PageRequest.of(pageable.getPageNumber(), size);
		MapSqlParameterSource params = new MapSqlParameterSource("studentId", studentId);
		Long total = jdbc.queryForObject("SELECT count(*) FROM (" + ROWS + ") access", params, Long.class);
		params.addValue("limit", size).addValue("offset", (long) page.getPageNumber() * size);
		List<AccessRow> rows = jdbc.query("""
				SELECT * FROM (
				""" + ROWS + """
				) access
				ORDER BY module_title_en, via, tran_id NULLS FIRST, exam_title_en NULLS FIRST
				LIMIT :limit OFFSET :offset
				""", params, (rs, number) -> {
					List<ExamRef> exams = new ArrayList<>();
					UUID examId = rs.getObject("exam_id", UUID.class);
					if (examId != null) {
						exams.add(new ExamRef(examId, rs.getString("exam_title_en"), rs.getString("exam_title_bn")));
					}
					return new AccessRow(rs.getObject("module_id", UUID.class), rs.getString("module_title_en"),
							rs.getString("module_title_bn"), rs.getString("via"), exams,
							rs.getObject("order_id", UUID.class), rs.getString("tran_id"),
							rs.getObject("grant_id", UUID.class), instant(rs.getTimestamp("granted_at")), null, false);
				});
		Instant now = clock.instant();
		return new PageImpl<>(rows.stream().map(row -> withExpiry(row, now)).toList(), page, total == null ? 0 : total);
	}

	/**
	 * When the row's access ends. An exam row ends with its own window and access days; a package row ends when the package's
	 * paid exams have all run out.
	 */
	private AccessRow withExpiry(AccessRow row, Instant now) {
		Instant endsAt;
		if (row.exams().isEmpty()) {
			endsAt = access.moduleEnd(row.moduleId(), row.grantedAt());
		}
		else {
			endsAt = liveExams.findByIdAndDeletedAtIsNull(row.exams().get(0).id())
				.map(exam -> AccessWindow.end(exam, row.grantedAt()))
				.orElse(null);
		}
		return new AccessRow(row.moduleId(), row.moduleTitleEn(), row.moduleTitleBn(), row.via(), row.exams(), row.orderId(),
				row.tranId(), row.grantId(), row.grantedAt(), endsAt, LiveExamAccess.expired(endsAt, now));
	}

	private static Instant instant(Timestamp value) {
		return value == null ? null : value.toInstant();
	}

	private void requireStudent(UUID studentId) {
		Integer found = jdbc.getJdbcOperations().queryForObject(
				"SELECT count(*) FROM users WHERE id = ? AND role = 'STUDENT' AND deleted_at IS NULL", Integer.class,
				studentId);
		if (found == null || found == 0) {
			throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
		}
	}

	/**
	 * {@code exams} names the bought exam for an {@code EXAM_PURCHASE} row, and is empty when the whole module is open.
	 * {@code grantId} is the grant a revoke takes, and is null for a roster row. {@code grantedAt} is when the access began,
	 * and {@code endsAt} is when it ends (null for lifetime), with {@code expired} true from that moment.
	 */
	public record AccessRow(UUID moduleId, String moduleTitleEn, String moduleTitleBn, String via, List<ExamRef> exams,
			UUID orderId, String tranId, UUID grantId, Instant grantedAt, Instant endsAt, boolean expired) {
	}

	public record ExamRef(UUID id, String titleEn, String titleBn) {
	}

}
