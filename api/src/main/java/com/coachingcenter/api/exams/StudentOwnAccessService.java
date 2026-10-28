package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
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
			SELECT r.module_id, 'ROSTER' AS via, CAST(NULL AS UUID) AS order_id, r.created_at AS granted_at
			FROM live_exam_roster r
			JOIN live_exam_modules m ON m.id = r.module_id AND m.deleted_at IS NULL
			WHERE r.student_id = :studentId
			UNION ALL
			SELECT g.module_id, CASE WHEN g.source = 'MANUAL' THEN 'MANUAL' ELSE 'MODULE' END, g.order_id, g.created_at
			FROM live_exam_grants g
			JOIN live_exam_modules m ON m.id = g.module_id AND m.deleted_at IS NULL
			WHERE g.student_id = :studentId AND g.scope = 'MODULE' AND g.revoked_at IS NULL
			ORDER BY module_id, via
			""";

	/**
	 * A student's own single-exam access, from order, bundle and manual grants that are not revoked. An exam that is free is
	 * open to everyone, so it is not listed (NR-01, C3). The package shown is the exam's primary one, for display only.
	 */
	private static final String MY_EXAMS = """
			SELECT g.exam_id, pm.module_id, CASE WHEN g.source = 'MANUAL' THEN 'MANUAL' ELSE 'EXAM' END AS via, g.order_id,
			  g.created_at AS granted_at
			FROM live_exam_grants g
			JOIN live_exams e ON e.id = g.exam_id AND e.deleted_at IS NULL
			LEFT JOIN LATERAL (
			  SELECT p.module_id FROM live_exam_package_exams p
			  JOIN live_exam_modules m ON m.id = p.module_id AND m.deleted_at IS NULL
			  WHERE p.exam_id = e.id AND p.removed_at IS NULL
			  ORDER BY p.added_at, p.id LIMIT 1
			) pm ON TRUE
			WHERE g.student_id = :studentId AND g.scope = 'EXAM' AND g.revoked_at IS NULL AND NOT e.is_free
			ORDER BY g.exam_id, via
			""";

	private final NamedParameterJdbcTemplate jdbc;

	private final ObjectProvider<LiveExamPaymentGateway> gateways;

	private final LiveExamAccess access;

	private final LiveExamRepository liveExams;

	private final Clock clock;

	StudentOwnAccessService(JdbcTemplate jdbc, ObjectProvider<LiveExamPaymentGateway> gateways, LiveExamAccess access,
		LiveExamRepository liveExams, Clock clock) {
		this.jdbc = new NamedParameterJdbcTemplate(jdbc);
		this.gateways = gateways;
		this.access = access;
		this.liveExams = liveExams;
		this.clock = clock;
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
		return new Viewer(access, studentId, clock.instant(), phone != null && !phone.isBlank(),
				gateways.getIfAvailable() != null);
	}

	/** Every row the student holds, expired ones included, each with when its access ends (A2'). */
	@Transactional(readOnly = true)
	public MyAccess mine(UUID studentId) {
		Instant now = clock.instant();
		MapSqlParameterSource params = new MapSqlParameterSource("studentId", studentId);
		List<MyModule> modules = jdbc.query(MY_MODULES, params, (rs, number) -> new MyModule(
				rs.getObject("module_id", UUID.class), rs.getString("via"), rs.getObject("order_id", UUID.class),
				instant(rs.getTimestamp("granted_at")), null, false)).stream()
			.map(row -> {
				Instant endsAt = access.moduleEnd(row.moduleId(), row.grantedAt());
				return new MyModule(row.moduleId(), row.via(), row.orderId(), row.grantedAt(), endsAt,
						LiveExamAccess.expired(endsAt, now));
			})
			.toList();
		List<MyExam> exams = jdbc.query(MY_EXAMS, params, (rs, number) -> new MyExam(
				rs.getObject("exam_id", UUID.class), rs.getObject("module_id", UUID.class), rs.getString("via"),
				rs.getObject("order_id", UUID.class), instant(rs.getTimestamp("granted_at")), null, false)).stream()
			.map(row -> {
				Instant endsAt = liveExams.findByIdAndDeletedAtIsNull(row.examId())
					.map(exam -> AccessWindow.end(exam, row.grantedAt()))
					.orElse(null);
				return new MyExam(row.examId(), row.moduleId(), row.via(), row.orderId(), row.grantedAt(), endsAt,
						LiveExamAccess.expired(endsAt, now));
			})
			.toList();
		return new MyAccess(modules, exams);
	}

	private static Instant instant(Timestamp value) {
		return value == null ? null : value.toInstant();
	}

	/**
	 * {@code via} is {@code ROSTER}, {@code MODULE} or {@code MANUAL}. {@code orderId} is null for a roster row. {@code endsAt}
	 * is when the package's paid exams have all run out (null for lifetime), and {@code expired} is true from that moment.
	 */
	public record MyModule(UUID moduleId, String via, UUID orderId, Instant grantedAt, Instant endsAt, boolean expired) {
	}

	/**
	 * {@code via} is {@code EXAM} or {@code MANUAL}. {@code orderId} is null for a manual grant. {@code moduleId} is the exam's
	 * primary package, for display only, and is null for an exam in no package.
	 */
	public record MyExam(UUID examId, UUID moduleId, String via, UUID orderId, Instant grantedAt, Instant endsAt,
			boolean expired) {
	}

	public record MyAccess(List<MyModule> modules, List<MyExam> exams) {
	}

	/**
	 * {@code state} is {@code NONE}, {@code MODULE}, {@code EXAM}, {@code ROSTER}, {@code BUNDLE}, {@code MANUAL} or
	 * {@code FREE} (a free exam, open to everyone). {@code blockedReason}
	 * is null exactly when {@code buyable} is true, and is always written, even when null. {@code expiresAt} is when the
	 * access the student holds ends (NR-01, S0.3). Once that access has run out, {@code state} is still {@code NONE} (so the
	 * student can buy again), and {@code expiresAt} is the end of the grant that ran out (NR-01, follow-up F3). It is null for
	 * lifetime access, a free exam, or no grant at all, and is always written, even when null.
	 */
	public record Access(String state, boolean buyable,
			@JsonInclude(JsonInclude.Include.ALWAYS) String blockedReason,
			@JsonInclude(JsonInclude.Include.ALWAYS) Instant expiresAt) {
	}

	/**
	 * One signed-in student's view of the catalog, asked per module or exam. A route that has run out (expired) reads as
	 * {@code NONE}, so the student can buy again (A2' and R19).
	 */
	public static final class Viewer {

		static final String NONE = "NONE";

		private final LiveExamAccess access;

		private final UUID studentId;

		private final Instant now;

		private final boolean hasPhone;

		private final boolean checkoutEnabled;

		Viewer(LiveExamAccess access, UUID studentId, Instant now, boolean hasPhone, boolean checkoutEnabled) {
			this.access = access;
			this.studentId = studentId;
			this.now = now;
			this.hasPhone = hasPhone;
			this.checkoutEnabled = checkoutEnabled;
		}

		/** Access to the package as a whole: a roster row or a package grant that has not run out. */
		public Access module(LiveExamModule module) {
			return access(access.moduleState(studentId, module.getId(), now), module.getPrice());
		}

		/** Any route to the exam, through any package it is in, or a grant on the exam. The price is the exam's own price. */
		public Access exam(LiveExam exam) {
			return access(access.state(studentId, exam, now), exam.getPrice());
		}

		private static String state(AccessState state) {
			return state.granted() && !state.expired() ? state.source() : NONE;
		}

		/** Reasons the student cannot fix come before the phone, which they can. */
		private Access access(AccessState found, BigDecimal price) {
			String state = state(found);
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
			// The longest route decides (LiveExamAccess.best). When it has run out, every route has, so its end is the one to show.
			return new Access(state, reason == null, reason, found.endsAt());
		}

	}

}
