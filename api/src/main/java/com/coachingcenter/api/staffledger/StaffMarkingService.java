package com.coachingcenter.api.staffledger;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

/**
 * What an instructor's marking is worth (staff pay plan, decision P5 and P6): per exam, how many sheets were marked, how many
 * are waiting to be published, how many of those are ready for release, how many were published and earned, and what the
 * waiting ones would bring at the current rate. Read only; the figures come from the marking columns and the ledger.
 *
 * <p>
 * A sheet counts as marked by an instructor when that instructor's mark is the live draft or the released one
 * ({@code evaluated_by}) or a correction waiting for release ({@code pending_evaluated_by}). Only written, submitted papers count.
 */
@Service
public class StaffMarkingService {

	private static final String DENIED = "Access is denied.";

	private static final String INSTRUCTOR = "INSTRUCTOR";

	private static final String MARKED = """
			select p.exam_id, count(*) as marked,
			  count(*) filter (where p.published_at is null) as waiting,
			  count(*) filter (where p.published_at is null and p.ready_for_release_at is not null) as ready
			from live_exam_participants p
			join live_exams e on e.id = p.exam_id and e.exam_type = 'WRITTEN'
			where p.deleted_at is null and p.status = 'SUBMITTED'
			  and ((p.evaluated_by = :user and p.evaluated_at is not null)
			    or (p.pending_evaluated_by = :user and p.pending_evaluated_at is not null))
			group by p.exam_id
			""";

	private static final String PUBLISHED = """
			select exam_id, count(*) as published, coalesce(sum(amount), 0) as earned
			from staff_ledger
			where user_id = :user and kind = 'MARKING' and voided_at is null
			group by exam_id
			""";

	private static final String RATE = """
			select coalesce(s.sheet_rate, d.sheet_rate) as rate
			from staff_pay_defaults d left join staff_pay_settings s on s.user_id = :user
			where d.id = 1
			""";

	private final NamedParameterJdbcTemplate jdbc;

	private final PermissionChecker permissions;

	public StaffMarkingService(JdbcTemplate jdbc, PermissionChecker permissions) {
		this.jdbc = new NamedParameterJdbcTemplate(jdbc);
		this.permissions = permissions;
	}

	/** The signed-in instructor's own marking. A person who is not an instructor is refused. */
	@Transactional(readOnly = true)
	public MarkingView self(UUID me) {
		if (!isInstructor(me)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, DENIED);
		}
		return view(me);
	}

	/** One instructor's marking, for the Master Admin. Anyone else is not found: marking pay is for instructors. */
	@Transactional(readOnly = true)
	public MarkingView person(UUID userId) {
		if (!permissions.has(Permissions.STAFF_PAY_MANAGE)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, DENIED);
		}
		if (!isInstructor(userId)) {
			throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
		}
		return view(userId);
	}

	private MarkingView view(UUID instructor) {
		MapSqlParameterSource params = new MapSqlParameterSource("user", instructor);
		BigDecimal rate = jdbc.queryForObject(RATE, params, BigDecimal.class);

		Map<UUID, ExamFigures> byExam = new HashMap<>();
		jdbc.query(MARKED, params, (rs, number) -> {
			ExamFigures figures = byExam.computeIfAbsent(rs.getObject("exam_id", UUID.class), key -> new ExamFigures());
			figures.marked = rs.getInt("marked");
			figures.waiting = rs.getInt("waiting");
			figures.ready = rs.getInt("ready");
			return null;
		});
		jdbc.query(PUBLISHED, params, (rs, number) -> {
			ExamFigures figures = byExam.computeIfAbsent(rs.getObject("exam_id", UUID.class), key -> new ExamFigures());
			figures.published = rs.getInt("published");
			figures.earned = rs.getBigDecimal("earned");
			return null;
		});

		List<ExamMarking> exams = new ArrayList<>();
		if (!byExam.isEmpty()) {
			Map<UUID, String[]> titles = titles(byExam.keySet());
			for (Map.Entry<UUID, ExamFigures> entry : byExam.entrySet()) {
				ExamFigures figures = entry.getValue();
				String[] title = titles.getOrDefault(entry.getKey(), new String[] { "", "" });
				exams.add(new ExamMarking(entry.getKey(), title[0], title[1], figures.marked, figures.waiting, figures.ready,
						figures.published, StaffPayCalculator.scale(figures.earned),
						StaffPayCalculator.scale(rate.multiply(BigDecimal.valueOf(figures.waiting)))));
			}
		}
		exams.sort(Comparator.comparing(ExamMarking::titleEn, Comparator.nullsLast(Comparator.<String>naturalOrder()))
			.thenComparing(ExamMarking::examId));
		return new MarkingView(StaffPayCalculator.scale(rate), totals(exams), exams);
	}

	private MarkingTotals totals(List<ExamMarking> exams) {
		int marked = 0;
		int waiting = 0;
		int ready = 0;
		int published = 0;
		BigDecimal earned = BigDecimal.ZERO;
		BigDecimal expected = BigDecimal.ZERO;
		for (ExamMarking exam : exams) {
			marked += exam.marked();
			waiting += exam.waiting();
			ready += exam.ready();
			published += exam.published();
			earned = earned.add(exam.earned());
			expected = expected.add(exam.expected());
		}
		return new MarkingTotals(marked, waiting, ready, published, StaffPayCalculator.scale(earned),
				StaffPayCalculator.scale(expected));
	}

	private Map<UUID, String[]> titles(Iterable<UUID> examIds) {
		List<UUID> ids = new ArrayList<>();
		examIds.forEach(ids::add);
		Map<UUID, String[]> titles = new HashMap<>();
		jdbc.query("select id, title_en, title_bn from live_exams where id in (:ids)",
				new MapSqlParameterSource("ids", ids), (rs, number) -> {
					titles.put(rs.getObject("id", UUID.class), new String[] { rs.getString("title_en"), rs.getString("title_bn") });
					return null;
				});
		return titles;
	}

	private boolean isInstructor(UUID userId) {
		Integer found = jdbc.queryForObject("select count(*) from users where id = :id and role = :role",
				new MapSqlParameterSource().addValue("id", userId).addValue("role", INSTRUCTOR), Integer.class);
		return found != null && found > 0;
	}

	/** Per exam counts while they are being gathered. */
	private static final class ExamFigures {

		private int marked;

		private int waiting;

		private int ready;

		private int published;

		private BigDecimal earned = BigDecimal.ZERO;

	}

	/**
	 * The marking figures. {@code sheetRate} is the instructor's rate now (the override, else the default). {@code totals} sums
	 * the exams. {@code exams} lists each exam with any marking, by title.
	 */
	public record MarkingView(BigDecimal sheetRate, MarkingTotals totals, List<ExamMarking> exams) {
	}

	/** Money is {@link BigDecimal} to two places. {@code expected} is the waiting sheets at the current rate. */
	public record MarkingTotals(int marked, int waiting, int ready, int published, BigDecimal earned, BigDecimal expected) {
	}

	/** One exam's marking for the instructor. Titles are the exam's own; the screen picks the language. */
	public record ExamMarking(UUID examId, String titleEn, String titleBn, int marked, int waiting, int ready, int published,
			BigDecimal earned, BigDecimal expected) {
	}

}
