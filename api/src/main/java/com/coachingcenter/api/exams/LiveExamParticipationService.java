package com.coachingcenter.api.exams;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

/**
 * Per-exam participation counts for the operate screens. A read of participant rows, not an analytics product. A sitting
 * past its deadline that the {@link LiveExamDeadlineJob} has not posted yet is counted as it will post.
 */
@Service
public class LiveExamParticipationService {

	static final int MAX_SIZE = 100;

	private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

	private static final DateTimeFormatter TIME = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

	private static final String SITTING = "p.submitted_at is null and :now < p.deadline_at";

	private static final String FAILED = "p.status = 'FAILED_TO_SUBMIT' or (p.submitted_at is null and :now >= p.deadline_at"
			+ " and e.exam_type = 'WRITTEN' and p.answer_stored_file_name is null)";

	private static final String SUBMITTED = "p.status = 'SUBMITTED' or (p.submitted_at is null and :now >= p.deadline_at"
			+ " and (e.exam_type = 'MCQ' or p.answer_stored_file_name is not null))";

	private static final String AWAITING = "p.evaluated_at is null and (" + SUBMITTED + ")";

	// A correction waiting for release counts as a draft, and its paper is not counted as published until released.
	private static final String DRAFT = "((p.evaluated_at is not null and p.published_at is null) or p.pending_evaluated_at is not null)";

	private static final String PUBLISHED = "(p.published_at is not null and p.pending_evaluated_at is null)";

	// The package shown beside an exam is the first one it joined that is still active. An exam in no package still lists.
	private static final String FROM = """
			from live_exams e
			left join lateral (
			  select m2.id, m2.title_en, m2.title_bn from live_exam_package_exams pe
			  join live_exam_modules m2 on m2.id = pe.module_id and m2.deleted_at is null
			  where pe.exam_id = e.id and pe.removed_at is null
			  order by pe.added_at, pe.id limit 1
			) m on true
			left join live_exam_categories c on c.id = e.category_id
			left join live_exam_participants p on p.exam_id = e.id and p.deleted_at is null
			""";

	private final NamedParameterJdbcTemplate jdbc;

	private final Clock clock;

	LiveExamParticipationService(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = new NamedParameterJdbcTemplate(jdbc);
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Page<ParticipationRow> list(CurrentActor actor, Filter filter, Pageable pageable) {
		MapSqlParameterSource params = new MapSqlParameterSource("now", Timestamp.from(clock.instant()));
		String where = where(actor, filter, params);
		int size = Math.min(Math.max(pageable.getPageSize(), 1), MAX_SIZE);
		Pageable page = PageRequest.of(pageable.getPageNumber(), size);
		Long total = jdbc.queryForObject("select count(*) from live_exams e where " + where, params, Long.class);
		params.addValue("limit", size).addValue("offset", (long) page.getPageNumber() * size);
		String sql = """
				select e.id, m.id as module_id, m.title_en as module_title_en, m.title_bn as module_title_bn, e.exam_type,
				  e.title_en, e.title_bn, e.live_at, e.ends_at, c.id as category_id, c.title_en as category_title_en, c.title_bn as category_title_bn,
				  count(p.id) filter (where %s) as sitting,
				  count(p.id) filter (where %s) as submitted,
				  count(p.id) filter (where %s) as failed,
				  count(p.id) filter (where e.exam_type = 'WRITTEN' and (%s)) as awaiting,
				  count(p.id) filter (where e.exam_type = 'WRITTEN' and %s) as drafts,
				  count(p.id) filter (where e.exam_type = 'WRITTEN' and %s) as published,
				  count(p.id) filter (where e.exam_type = 'WRITTEN' and p.ready_for_release_at is not null) as ready,
				  count(p.id) filter (where e.exam_type = 'WRITTEN' and p.pending_evaluated_at is not null) as corrections,
				  count(p.id) filter (where p.kind in ('ARCHIVED', 'RETAKE')) as retakes,
				  count(p.id) filter (where p.kind = 'LIVE') as live_n,
				  count(p.id) filter (where p.kind = 'ARCHIVED') as archived_n,
				  count(p.id) filter (where p.kind = 'RETAKE') as retake_n,
				  count(p.id) filter (where p.kind = 'PRACTICE') as practice_n
				%s
				where %s
				group by e.id, m.id, m.title_en, m.title_bn, c.id, c.title_en, c.title_bn
				order by %s
				limit :limit offset :offset
				""".formatted(SITTING, SUBMITTED, FAILED, AWAITING, DRAFT, PUBLISHED, FROM, where, order(filter.sort()));
		List<ParticipationRow> rows = jdbc.query(sql, params, (rs, number) -> {
			boolean written = "WRITTEN".equals(rs.getString("exam_type"));
			Timestamp liveAt = rs.getTimestamp("live_at");
			String start = liveAt == null ? null : format(liveAt.toInstant());
			Timestamp endsAt = rs.getTimestamp("ends_at");
			String end = endsAt == null ? null : format(endsAt.toInstant());
			return new ParticipationRow(rs.getObject("id", UUID.class), rs.getObject("module_id", UUID.class),
					rs.getString("module_title_en"), rs.getString("module_title_bn"), rs.getString("exam_type"),
					rs.getString("title_en"), rs.getString("title_bn"), start, end, rs.getInt("sitting"),
					rs.getInt("submitted"), rs.getInt("failed"), written ? rs.getInt("awaiting") : null,
					written ? rs.getInt("drafts") : null, written ? rs.getInt("published") : null, rs.getInt("retakes"),
					written ? rs.getInt("ready") : null, written ? rs.getInt("corrections") : null, rs.getInt("live_n"),
					rs.getInt("archived_n"), rs.getInt("retake_n"), rs.getInt("practice_n"), rs.getObject("category_id", UUID.class),
					rs.getString("category_title_en"), rs.getString("category_title_bn"));
		});
		return new PageImpl<>(rows, page, total == null ? 0 : total);
	}

	@Transactional(readOnly = true)
	public Summary summary(CurrentActor actor) {
		MapSqlParameterSource params = new MapSqlParameterSource("now", Timestamp.from(clock.instant()));
		String where = where(actor, new Filter(null, null, null, null, null, null, null), params) + " and e.exam_type = 'WRITTEN'";
		String sql = """
				select count(p.id) filter (where %s) as awaiting, count(p.id) filter (where %s) as drafts
				%s
				where %s
				""".formatted(AWAITING, DRAFT, FROM, where);
		return jdbc.queryForObject(sql, params,
				(rs, number) -> new Summary(rs.getInt("awaiting"), rs.getInt("drafts")));
	}

	private String where(CurrentActor actor, Filter filter, MapSqlParameterSource params) {
		List<String> clauses = new ArrayList<>();
		clauses.add("e.deleted_at is null");
		clauses.add("e.status = 'PUBLISHED'");
		if (actor.role() == Role.INSTRUCTOR) {
			params.addValue("me", actor.userId());
			clauses.add("e.instructor_id = :me");
		}
		if (filter.moduleId() != null) {
			if (actor.role() == Role.INSTRUCTOR && !holds(actor.userId(), filter.moduleId())) {
				throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
			}
			params.addValue("moduleId", filter.moduleId());
			clauses.add("exists (select 1 from live_exam_package_exams x where x.exam_id = e.id and x.module_id = :moduleId"
					+ " and x.removed_at is null)");
		}
		if (filter.categoryId() != null) {
			params.addValue("categoryId", filter.categoryId());
			clauses.add("e.category_id = :categoryId");
		}
		if (filter.type() != null && !filter.type().isBlank()) {
			String type = filter.type().trim();
			if (!"MCQ".equals(type) && !"WRITTEN".equals(type)) {
				throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Type is invalid.");
			}
			params.addValue("type", type);
			clauses.add("e.exam_type = :type");
		}
		if (filter.from() != null) {
			params.addValue("from", Timestamp.from(filter.from()));
			clauses.add("e.live_at >= :from");
		}
		if (filter.to() != null) {
			params.addValue("to", Timestamp.from(filter.to()));
			clauses.add("e.live_at <= :to");
		}
		if (filter.q() != null && !filter.q().isBlank()) {
			params.addValue("q", "%" + filter.q().trim().toLowerCase() + "%");
			clauses.add("(lower(e.title_en) like :q or lower(e.title_bn) like :q)");
		}
		return String.join(" and ", clauses);
	}

	private boolean holds(UUID instructorId, UUID moduleId) {
		Integer found = jdbc.queryForObject(
				"select count(*) from live_exams e join live_exam_package_exams pe on pe.exam_id = e.id and pe.removed_at is null"
						+ " where pe.module_id = :m and e.instructor_id = :i and e.deleted_at is null",
				Map.of("m", moduleId, "i", instructorId), Integer.class);
		return found != null && found > 0;
	}

	private static String order(String sort) {
		if (sort == null || sort.isBlank() || "liveAt".equals(sort)) {
			return "e.live_at desc nulls last, e.id";
		}
		if ("title".equals(sort)) {
			return "lower(e.title_en), e.id";
		}
		if ("sitting".equals(sort)) {
			return "sitting desc, e.id";
		}
		if ("category".equals(sort)) {
			return "lower(c.title_en), lower(e.title_en), e.id";
		}
		throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Sort is invalid.");
	}

	private static String format(Instant instant) {
		return instant.atZone(DHAKA).format(TIME);
	}

	public record Filter(UUID moduleId, String type, Instant from, Instant to, String q, String sort, UUID categoryId) {
	}

	public record ParticipationRow(UUID examId, UUID moduleId, String moduleTitleEn, String moduleTitleBn, String examType,
			String titleEn, String titleBn, String liveAt, String endAt, int sitting, int submitted, int failedToSubmit,
			Integer awaitingMarking, Integer draftsAwaitingPublish, Integer published, int retakes, Integer readyForRelease,
			Integer correctionsWaiting, int live, int archived, int retake, int practice, UUID categoryId, String categoryTitleEn,
			String categoryTitleBn) {
	}

	public record Summary(int awaitingMarking, int draftsAwaitingPublish) {
	}

}
