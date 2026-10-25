package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * What the signed-in student can sit, for the student website, and whether they can buy a module or exam. Only their
 * own rows. The staff list of one student's access is {@link StudentLiveExamAccessService}.
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

	/** When a target has more than one module-wide reason, the first one here wins. */
	private static final List<String> MODULE_STATES = List.of("ROSTER", "MODULE", "MANUAL");

	private final NamedParameterJdbcTemplate jdbc;

	private final ObjectProvider<LiveExamPaymentGateway> gateways;

	StudentOwnAccessService(JdbcTemplate jdbc, ObjectProvider<LiveExamPaymentGateway> gateways) {
		this.jdbc = new NamedParameterJdbcTemplate(jdbc);
		this.gateways = gateways;
	}

	/**
	 * Everything the public catalog needs to answer "can this student buy it" for one request: the student's access,
	 * whether they have a phone, and whether checkout is on. Null when the student no longer exists.
	 */
	@Transactional(readOnly = true)
	public Viewer viewer(UUID studentId) {
		List<String> phones = jdbc.getJdbcOperations().queryForList(
				"SELECT phone FROM users WHERE id = ? AND role = 'STUDENT' AND deleted_at IS NULL", String.class, studentId);
		if (phones.isEmpty()) {
			return null;
		}
		String phone = phones.get(0);
		return new Viewer(mine(studentId), phone != null && !phone.isBlank(), gateways.getIfAvailable() != null);
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

	/**
	 * {@code state} is {@code NONE}, {@code MODULE}, {@code EXAM}, {@code ROSTER} or {@code MANUAL}. {@code blockedReason}
	 * is null exactly when {@code buyable} is true, and is always written, even when null.
	 */
	public record Access(String state, boolean buyable,
			@JsonInclude(JsonInclude.Include.ALWAYS) String blockedReason) {
	}

	/** One signed-in student's view of the catalog. Built once per request, then asked per module or exam. */
	public static final class Viewer {

		static final String NONE = "NONE";

		private final Map<UUID, String> moduleStates = new HashMap<>();

		private final Map<UUID, String> examStates = new HashMap<>();

		private final boolean hasPhone;

		private final boolean checkoutEnabled;

		Viewer(MyAccess access, boolean hasPhone, boolean checkoutEnabled) {
			for (MyModule module : access.modules()) {
				moduleStates.merge(module.moduleId(), module.via(),
						(a, b) -> MODULE_STATES.indexOf(a) <= MODULE_STATES.indexOf(b) ? a : b);
			}
			for (MyExam exam : access.exams()) {
				examStates.merge(exam.examId(), exam.via(), (a, b) -> "EXAM".equals(a) ? a : b);
			}
			this.hasPhone = hasPhone;
			this.checkoutEnabled = checkoutEnabled;
		}

		/** Module-wide access only: owning one exam of the module does not own the module. */
		public Access module(LiveExamModule module) {
			return access(moduleStates.getOrDefault(module.getId(), NONE), module.getPrice());
		}

		/** Module-wide access first, then a grant on this exam. The price is the exam's own price. */
		public Access exam(LiveExam exam) {
			String state = moduleStates.getOrDefault(exam.getModuleId(), NONE);
			if (NONE.equals(state)) {
				state = examStates.getOrDefault(exam.getId(), NONE);
			}
			return access(state, exam.getPrice());
		}

		/** Reasons the student cannot fix come before the phone, which they can. */
		private Access access(String state, BigDecimal price) {
			String reason = null;
			if (!NONE.equals(state)) {
				reason = "ALREADY_HAS_ACCESS";
			}
			else if (price == null) {
				reason = "PRICE_REQUIRED";
			}
			else if (!checkoutEnabled) {
				reason = "CHECKOUT_OFF";
			}
			else if (!hasPhone) {
				reason = "PHONE_REQUIRED";
			}
			return new Access(state, reason == null, reason);
		}

	}

}
