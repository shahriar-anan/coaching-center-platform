package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
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
	 * for the module it names while that module is not deleted. A withdrawn announcement shows nowhere (plan section 6).
	 * A direct-enrolment money notice names its installment or payment instead, and its target is read without the deleted
	 * filter, so it stays visible (plan section 7). A reminder whose installment was rescheduled away is hidden.
	 */
	private static final String FROM = """
			from student_notices n
			left join live_exams e on e.id = n.exam_id and e.deleted_at is null
			left join live_exam_modules m on m.id = coalesce(e.module_id, n.module_id) and m.deleted_at is null
			left join announcements a on a.id = n.announcement_id
			left join live_exam_participants p on p.id = n.participant_id
			left join live_exam_installments ins on ins.id = n.installment_id
			left join live_exam_direct_payments dp on dp.id = n.direct_payment_id
			left join live_exam_payment_plans ip on ip.id = coalesce(ins.plan_id, dp.plan_id)
			left join live_exam_grants ig on ig.id = ip.grant_id
			left join live_exams ie on ie.id = ig.exam_id
			left join live_exam_modules im on im.id = ig.module_id
			where n.user_id = :me
			  and (n.exam_id is null or e.id is not null)
			  and (coalesce(e.module_id, n.module_id) is null or m.id is not null)
			  and (a.id is null or a.withdrawn_at is null)
			  and (n.kind not in ('INSTALLMENT_DUE_SOON', 'INSTALLMENT_OVERDUE') or n.installment_id is not null)
			""";

	private static final String SELECT = """
			select n.id, n.kind, n.created_at, n.read_at, e.id as exam_id, e.module_id, e.exam_type, e.title_en, e.title_bn,
			  e.live_at, e.duration_minutes, p.score, p.passed, p.published_at,
			  exists (select 1 from student_notices x where x.participant_id = n.participant_id
			    and x.kind = 'WRITTEN_RESULT' and x.created_at < n.created_at) as updated,
			  n.module_id as notice_module_id, a.id as announcement_id, a.title_en as announcement_title_en,
			  a.title_bn as announcement_title_bn, a.body_en as announcement_body_en, a.body_bn as announcement_body_bn,
			  a.audience as announcement_audience, a.exam_id as announcement_exam_id,
			  case when e.exam_type = 'WRITTEN' then coalesce(p.written_maximum, e.written_maximum, 0)
			    when p.question_snapshot is not null then (select coalesce(sum((q ->> 'marks')::int), 0)
			          from jsonb_array_elements(p.question_snapshot::jsonb -> 'questions') q)
			    else (select coalesce(sum(q.marks), 0) from live_exam_questions q
			          where q.exam_id = e.id and q.deleted_at is null) end as maximum,
			  ip.id as inst_plan_id, ip.agreed_total as inst_total, ins.seq as inst_seq, ins.amount as inst_row_amount,
			  ins.due_on as inst_due_on,
			  (select coalesce(sum(r.amount), 0) from live_exam_installments r where r.plan_id = ins.plan_id and r.seq < ins.seq)
			    as inst_before,
			  (select coalesce(sum(x.amount), 0) from live_exam_direct_payments x where x.plan_id = ip.id and x.voided_at is null)
			    as inst_paid_now,
			  dp.amount as pay_amount, dp.paid_to_date as pay_paid, dp.due_after as pay_due, dp.next_due_on as pay_next_on,
			  dp.receipt_no as pay_receipt,
			  coalesce(ie.title_en, im.title_en) as inst_title_en, coalesce(ie.title_bn, im.title_bn) as inst_title_bn
			""";

	private final NamedParameterJdbcTemplate jdbc;

	private final Clock clock;

	StudentInboxService(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = new NamedParameterJdbcTemplate(jdbc);
		this.clock = clock;
	}

	/** One page of the notices, newest first. {@code group} limits it to one tab (N1); null means every notice. */
	@Transactional(readOnly = true)
	public Page<NoticeView> list(UUID studentId, NoticeGroup group, Pageable pageable) {
		int size = Math.min(Math.max(pageable.getPageSize(), 1), MAX_SIZE);
		Pageable page = PageRequest.of(pageable.getPageNumber(), size);
		MapSqlParameterSource params = new MapSqlParameterSource("me", studentId);
		String tab = groupClause(group, "n.kind", params);
		Long total = jdbc.queryForObject("select count(*) " + FROM + tab, params, Long.class);
		params.addValue("limit", size).addValue("offset", (long) page.getPageNumber() * size);
		List<NoticeView> rows = jdbc.query(SELECT + FROM + tab + " order by n.created_at desc, n.id desc limit :limit offset :offset",
				params, (rs, number) -> view(rs));
		return new PageImpl<>(rows, page, total == null ? 0 : total);
	}

	/** Unread notices in all, and in each tab (N1). {@code count} is the total, as before. */
	@Transactional(readOnly = true)
	public UnreadCounts unreadCount(UUID studentId) {
		Map<String, Integer> byGroup = new LinkedHashMap<>();
		for (NoticeGroup group : NoticeGroup.values()) {
			byGroup.put(group.name(), 0);
		}
		jdbc.query("select n.kind, count(*) as total " + FROM + " and n.read_at is null group by n.kind",
				new MapSqlParameterSource("me", studentId), (rs) -> {
					byGroup.merge(NoticeGroup.of(rs.getString("kind")).name(), rs.getInt("total"), Integer::sum);
				});
		int count = byGroup.values().stream().mapToInt(Integer::intValue).sum();
		return new UnreadCounts(count, byGroup);
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

	/** Marks the student's unread notices read: all of them, or only the tab named (N1). Returns how many changed. */
	@Transactional
	public int markAllRead(UUID studentId, NoticeGroup group) {
		MapSqlParameterSource params = new MapSqlParameterSource("me", studentId).addValue("now",
				Timestamp.from(clock.instant()));
		String tab = groupClause(group, "n.kind", params);
		// Only the notices the inbox shows: a withdrawn or deleted-target notice is not marked.
		return jdbc.update("update student_notices set read_at = :now where read_at is null and id in (select n.id " + FROM
				+ tab + ")", params);
	}

	/** The condition for one tab, or nothing for all notices. Exams is "not in" the other two tabs' kinds. */
	private static String groupClause(NoticeGroup group, String column, MapSqlParameterSource params) {
		if (group == null) {
			return "";
		}
		params.addValue("groupKinds", group.kinds());
		return " and " + column + (group == NoticeGroup.EXAMS ? " not in" : " in") + " (:groupKinds)";
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
		// An announcement names its exam in the announcement part, so its exam part stays empty, as it always was.
		NoticeExam exam = examId == null || "ANNOUNCEMENT".equals(kind) ? null
				: new NoticeExam(examId, rs.getObject("module_id", UUID.class), rs.getString("exam_type"),
						rs.getString("title_en"), rs.getString("title_bn"), start, end);
		UUID announcementId = rs.getObject("announcement_id", UUID.class);
		NoticeAnnouncement announcement = announcementId == null ? null
				: new NoticeAnnouncement(announcementId, rs.getString("announcement_title_en"),
						rs.getString("announcement_title_bn"), rs.getString("announcement_body_en"),
						rs.getString("announcement_body_bn"), rs.getString("announcement_audience"),
						rs.getObject("notice_module_id", UUID.class), rs.getObject("announcement_exam_id", UUID.class));
		return new NoticeView(rs.getObject("id", UUID.class), kind, format(rs.getTimestamp("created_at").toInstant()),
				readAt == null ? null : format(readAt.toInstant()), exam, score, maximum, passed, announcement,
				rs.getBoolean("updated") ? Boolean.TRUE : null, NoticeGroup.of(kind).name(), installment(kind, rs));
	}

	/**
	 * The money a direct-enrolment notice is about (plan section 7). A payment notice shows that payment's receipt figures, as
	 * they were when it was recorded. A reminder shows the amount still owed on its installment, and the plan's position now.
	 * A schedule change shows the plan's position now. Null for every other kind, and for a notice whose plan is gone.
	 */
	private static NoticeInstallment installment(String kind, java.sql.ResultSet rs) throws java.sql.SQLException {
		if (!NoticeGroup.isInstallment(kind) || rs.getObject("inst_plan_id") == null) {
			return null;
		}
		BigDecimal total = rs.getBigDecimal("inst_total");
		BigDecimal paidNow = rs.getBigDecimal("inst_paid_now");
		String titleEn = rs.getString("inst_title_en");
		String titleBn = rs.getString("inst_title_bn");
		if (total == null || paidNow == null) {
			return null;
		}
		if (StudentNoticeWriter.INSTALLMENT_PAYMENT_RECEIVED.equals(kind) || StudentNoticeWriter.INSTALLMENT_PAYMENT_VOIDED.equals(kind)) {
			if (rs.getObject("pay_receipt") == null) {
				return null;
			}
			return new NoticeInstallment(titleEn, titleBn, rs.getBigDecimal("pay_amount"), isoDate(rs, "pay_next_on"),
					rs.getBigDecimal("pay_paid"), rs.getBigDecimal("pay_due"), rs.getString("pay_receipt"));
		}
		if (StudentNoticeWriter.INSTALLMENT_DUE_SOON.equals(kind) || StudentNoticeWriter.INSTALLMENT_OVERDUE.equals(kind)) {
			BigDecimal rowAmount = rs.getBigDecimal("inst_row_amount");
			BigDecimal before = rs.getBigDecimal("inst_before");
			if (rowAmount == null || before == null) {
				return null;
			}
			BigDecimal onRow = paidNow.subtract(before).max(BigDecimal.ZERO).min(rowAmount);
			return new NoticeInstallment(titleEn, titleBn, rowAmount.subtract(onRow), isoDate(rs, "inst_due_on"), paidNow,
					total.subtract(paidNow), null);
		}
		return new NoticeInstallment(titleEn, titleBn, null, null, paidNow, total.subtract(paidNow), null);
	}

	private static String isoDate(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
		java.sql.Date value = rs.getDate(column);
		return value == null ? null : value.toLocalDate().toString();
	}

	private static String format(Instant instant) {
		return instant.atZone(DHAKA).format(TIME);
	}

	/**
	 * {@code exam} is null for an announcement, and {@code announcement} is null for every other kind. {@code group} is the
	 * tab the notice sits under (N1). {@code installment} is set only for a direct-enrolment money notice.
	 */
	public record NoticeView(UUID id, String kind, String createdAt, String readAt, NoticeExam exam, Integer score,
			Integer maximum, Boolean passed, NoticeAnnouncement announcement, Boolean updated, String group,
			NoticeInstallment installment) {
	}

	/**
	 * The money a direct-enrolment notice is about, in BDT. {@code dueOn} is the installment's date (or the next date, for a
	 * payment). {@code receiptNo} is set only for a payment notice.
	 */
	public record NoticeInstallment(String titleEn, String titleBn, BigDecimal amount, String dueOn, BigDecimal paid,
			BigDecimal due, String receiptNo) {
	}

	/** {@code count} is every unread notice; {@code byGroup} counts them per tab (N1). */
	public record UnreadCounts(int count, Map<String, Integer> byGroup) {
	}

	public record NoticeExam(UUID id, UUID moduleId, String examType, String titleEn, String titleBn, String liveAt,
			String endAt) {
	}

	/**
	 * {@code moduleId} is set only for an announcement sent to one package, and {@code examId} only for one sent to one exam.
	 */
	public record NoticeAnnouncement(UUID id, String titleEn, String titleBn, String bodyEn, String bodyBn, String audience,
			UUID moduleId, UUID examId) {
	}

}
