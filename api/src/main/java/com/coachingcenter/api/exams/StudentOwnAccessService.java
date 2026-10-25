package com.coachingcenter.api.exams;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the signed-in student can sit, for the student website. Only their own rows. The staff list of one student's
 * access is {@link StudentLiveExamAccessService}.
 */
@Service
public class StudentOwnAccessService {

	/** A student's own module-wide access. A revoked grant gives nothing; a manual grant reads {@code MANUAL}. */
	private static final String MY_MODULES = """
			SELECT r.module_id, 'ROSTER' AS via, CAST(NULL AS UUID) AS order_id
			FROM live_exam_roster r
			JOIN live_exam_modules m ON m.id = r.module_id AND m.deleted_at IS NULL
			WHERE r.student_id = :studentId
			UNION ALL
			SELECT g.module_id, CASE WHEN g.source = 'MANUAL' THEN 'MANUAL' ELSE 'MODULE' END, g.order_id
			FROM live_exam_grants g
			JOIN live_exam_modules m ON m.id = g.module_id AND m.deleted_at IS NULL
			WHERE g.student_id = :studentId AND g.scope = 'MODULE' AND g.revoked_at IS NULL
			ORDER BY module_id, via
			""";

	/** A student's own single-exam access, from order, bundle and manual grants that are not revoked. */
	private static final String MY_EXAMS = """
			SELECT g.exam_id, g.module_id, CASE WHEN g.source = 'MANUAL' THEN 'MANUAL' ELSE 'EXAM' END AS via, g.order_id
			FROM live_exam_grants g
			JOIN live_exam_modules m ON m.id = g.module_id AND m.deleted_at IS NULL
			JOIN live_exams e ON e.id = g.exam_id AND e.deleted_at IS NULL
			WHERE g.student_id = :studentId AND g.scope = 'EXAM' AND g.revoked_at IS NULL
			ORDER BY g.exam_id, via
			""";

	private final NamedParameterJdbcTemplate jdbc;

	StudentOwnAccessService(JdbcTemplate jdbc) {
		this.jdbc = new NamedParameterJdbcTemplate(jdbc);
	}

	@Transactional(readOnly = true)
	public MyAccess mine(UUID studentId) {
		MapSqlParameterSource params = new MapSqlParameterSource("studentId", studentId);
		List<MyModule> modules = jdbc.query(MY_MODULES, params, (rs, number) -> new MyModule(
				rs.getObject("module_id", UUID.class), rs.getString("via"), rs.getObject("order_id", UUID.class)));
		List<MyExam> exams = jdbc.query(MY_EXAMS, params,
				(rs, number) -> new MyExam(rs.getObject("exam_id", UUID.class), rs.getObject("module_id", UUID.class),
						rs.getString("via"), rs.getObject("order_id", UUID.class)));
		return new MyAccess(modules, exams);
	}

	/** {@code via} is {@code ROSTER}, {@code MODULE} or {@code MANUAL}. {@code orderId} is null for a roster row. */
	public record MyModule(UUID moduleId, String via, UUID orderId) {
	}

	/** {@code via} is {@code EXAM} or {@code MANUAL}. {@code orderId} is null for a manual grant. */
	public record MyExam(UUID examId, UUID moduleId, String via, UUID orderId) {
	}

	public record MyAccess(List<MyModule> modules, List<MyExam> exams) {
	}

}
