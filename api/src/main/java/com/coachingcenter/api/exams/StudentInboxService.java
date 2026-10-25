package com.coachingcenter.api.exams;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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

/** The signed-in student's own notices. Marks come from the participant row when the notice is read. */
@Service
public class StudentInboxService {

	static final int MAX_SIZE = 50;

	private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

	private static final DateTimeFormatter TIME = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

	/**
	 * An exam notice needs its live exam and module, not deleted. An announcement has no exam: it shows for everyone, or
	 * for the module it names while that module is not deleted.
	 */
	private static final String FROM = """
			from student_notices n
			left join live_exams e on e.id = n.exam_id and e.deleted_at is null
			left join live_exam_modules m on m.id = coalesce(e.module_id, n.module_id) and m.deleted_at is null
			left join announcements a on a.id = n.announcement_id
			left join live_exam_participants p on p.id = n.participant_id
			where n.user_id = :me
			  and (n.exam_id is null or e.id is not null)
			  and (coalesce(e.module_id, n.module_id) is null or m.id is not null)
			""";

	private static final String SELECT = """
			select n.id, n.kind, n.created_at, n.read_at, e.id as exam_id, e.module_id, e.exam_type, e.title_en, e.title_bn,
			  e.live_at, e.duration_minutes, p.score, p.passed, p.published_at,
			  n.module_id as notice_module_id, a.id as announcement_id, a.title_en as announcement_title_en,
			  a.title_bn as announcement_title_bn, a.body_en as announcement_body_en, a.body_bn as announcement_body_bn,
			  a.audience as announcement_audience,
			  case when e.exam_type = 'WRITTEN' then coalesce(e.written_maximum, 0)
			    else (select coalesce(sum(q.marks), 0) from live_exam_questions q
			          where q.exam_id = e.id and q.deleted_at is null) end as maximum
			""";

	private final NamedParameterJdbcTemplate jdbc;

	private final Clock clock;

	StudentInboxService(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = new NamedParameterJdbcTemplate(jdbc);
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Page<NoticeView> list(UUID studentId, Pageable pageable) {
		int size = Math.min(Math.max(pageable.getPageSize(), 1), MAX_SIZE);
		Pageable page = PageRequest.of(pageable.getPageNumber(), size);
		MapSqlParameterSource params = new MapSqlParameterSource("me", studentId);
		Long total = jdbc.queryForObject("select count(*) " + FROM, params, Long.class);
		params.addValue("limit", size).addValue("offset", (long) page.getPageNumber() * size);
		List<NoticeView> rows = jdbc.query(SELECT + FROM + " order by n.created_at desc, n.id desc limit :limit offset :offset",
				params, (rs, number) -> view(rs));
		return new PageImpl<>(rows, page, total == null ? 0 : total);
	}

	@Transactional(readOnly = true)
	public int unreadCount(UUID studentId) {
		Integer count = jdbc.queryForObject("select count(*) " + FROM + " and n.read_at is null",
				new MapSqlParameterSource("me", studentId), Integer.class);
		return count == null ? 0 : count;
	}

	@Transactional
	public NoticeView markRead(UUID studentId, UUID noticeId) {
		MapSqlParameterSource params = new MapSqlParameterSource("me", studentId).addValue("id", noticeId);
		List<NoticeView> found = jdbc.query(SELECT + FROM + " and n.id = :id", params, (rs, number) -> view(rs));
		if (found.isEmpty()) {
			throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
		}
		params.addValue("now", Timestamp.from(clock.instant()));
		jdbc.update("update student_notices set read_at = coalesce(read_at, :now) where id = :id and user_id = :me", params);
		return jdbc.query(SELECT + FROM + " and n.id = :id", params, (rs, number) -> view(rs)).get(0);
	}

	@Transactional
	public int markAllRead(UUID studentId) {
		return jdbc.update("update student_notices set read_at = :now where user_id = :me and read_at is null",
				new MapSqlParameterSource("me", studentId).addValue("now", Timestamp.from(clock.instant())));
	}

	private static NoticeView view(java.sql.ResultSet rs) throws java.sql.SQLException {
		String kind = rs.getString("kind");
		Timestamp liveAt = rs.getTimestamp("live_at");
		String start = liveAt == null ? null : format(liveAt.toInstant());
		String end = liveAt == null ? null : format(liveAt.toInstant().plusSeconds(rs.getInt("duration_minutes") * 60L));
		boolean marks = StudentNoticeWriter.MCQ_SCORE.equals(kind)
				|| (StudentNoticeWriter.WRITTEN_RESULT.equals(kind) && rs.getTimestamp("published_at") != null);
		Integer score = null;
		Integer maximum = null;
		Boolean passed = null;
		if (marks && rs.getObject("score") != null) {
			score = rs.getInt("score");
			maximum = rs.getInt("maximum");
			passed = rs.getBoolean("passed");
			if (rs.wasNull()) {
				passed = null;
			}
		}
		Timestamp readAt = rs.getTimestamp("read_at");
		UUID examId = rs.getObject("exam_id", UUID.class);
		NoticeExam exam = examId == null ? null
				: new NoticeExam(examId, rs.getObject("module_id", UUID.class), rs.getString("exam_type"),
						rs.getString("title_en"), rs.getString("title_bn"), start, end);
		UUID announcementId = rs.getObject("announcement_id", UUID.class);
		NoticeAnnouncement announcement = announcementId == null ? null
				: new NoticeAnnouncement(announcementId, rs.getString("announcement_title_en"),
						rs.getString("announcement_title_bn"), rs.getString("announcement_body_en"),
						rs.getString("announcement_body_bn"), rs.getString("announcement_audience"),
						rs.getObject("notice_module_id", UUID.class));
		return new NoticeView(rs.getObject("id", UUID.class), kind, format(rs.getTimestamp("created_at").toInstant()),
				readAt == null ? null : format(readAt.toInstant()), exam, score, maximum, passed, announcement);
	}

	private static String format(Instant instant) {
		return instant.atZone(DHAKA).format(TIME);
	}

	/** {@code exam} is null for an announcement, and {@code announcement} is null for every other kind. */
	public record NoticeView(UUID id, String kind, String createdAt, String readAt, NoticeExam exam, Integer score,
			Integer maximum, Boolean passed, NoticeAnnouncement announcement) {
	}

	public record NoticeExam(UUID id, UUID moduleId, String examType, String titleEn, String titleBn, String liveAt,
			String endAt) {
	}

	/** {@code moduleId} is set only for an announcement sent to one module. */
	public record NoticeAnnouncement(UUID id, String titleEn, String titleBn, String bodyEn, String bodyBn, String audience,
			UUID moduleId) {
	}

}
