package com.coachingcenter.api.exams;

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
 * student with both a roster row and a purchase shows both reasons. An exam purchase row names its one exam.
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
			  CAST(NULL AS UUID) AS exam_id, CAST(NULL AS VARCHAR) AS exam_title_en, CAST(NULL AS VARCHAR) AS exam_title_bn
			FROM live_exam_roster r
			JOIN live_exam_modules m ON m.id = r.module_id AND m.deleted_at IS NULL
			WHERE r.student_id = :studentId
			UNION ALL
			SELECT m.id, m.title_en, m.title_bn, 'MODULE_PURCHASE', g.order_id, o.tran_id,
			  CAST(NULL AS UUID), CAST(NULL AS VARCHAR), CAST(NULL AS VARCHAR)
			FROM live_exam_grants g
			JOIN live_exam_modules m ON m.id = g.module_id AND m.deleted_at IS NULL
			JOIN live_exam_orders o ON o.id = g.order_id
			WHERE g.student_id = :studentId AND g.scope = 'MODULE'
			UNION ALL
			SELECT m.id, m.title_en, m.title_bn, 'EXAM_PURCHASE', g.order_id, o.tran_id,
			  e.id, e.title_en, e.title_bn
			FROM live_exam_grants g
			JOIN live_exam_modules m ON m.id = g.module_id AND m.deleted_at IS NULL
			JOIN live_exams e ON e.id = g.exam_id AND e.deleted_at IS NULL
			JOIN live_exam_orders o ON o.id = g.order_id
			WHERE g.student_id = :studentId AND g.scope = 'EXAM'
			""";

	private final NamedParameterJdbcTemplate jdbc;

	StudentLiveExamAccessService(JdbcTemplate jdbc) {
		this.jdbc = new NamedParameterJdbcTemplate(jdbc);
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
							rs.getObject("order_id", UUID.class), rs.getString("tran_id"));
				});
		return new PageImpl<>(rows, page, total == null ? 0 : total);
	}

	private void requireStudent(UUID studentId) {
		Integer found = jdbc.getJdbcOperations().queryForObject(
				"SELECT count(*) FROM users WHERE id = ? AND role = 'STUDENT' AND deleted_at IS NULL", Integer.class,
				studentId);
		if (found == null || found == 0) {
			throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
		}
	}

	/** {@code exams} names the bought exam for an {@code EXAM_PURCHASE} row, and is empty when the whole module is open. */
	public record AccessRow(UUID moduleId, String moduleTitleEn, String moduleTitleBn, String via, List<ExamRef> exams,
			UUID orderId, String tranId) {
	}

	public record ExamRef(UUID id, String titleEn, String titleBn) {
	}

}
