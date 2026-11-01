package com.coachingcenter.api.exams;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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

import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.error.FieldErrorDetail;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Staff announcements (R-5, plan sections 6 and 7). Sending writes the announcement and one ANNOUNCEMENT notice per recipient,
 * in one transaction. A notice is never edited or deleted afterwards. An announcement can be withdrawn: it then leaves the
 * notices and the package and exam pages, and its record stays.
 */
@Service
public class AnnouncementService {

	static final int MAX_TITLE_LENGTH = 500;

	static final int MAX_BODY_LENGTH = 5000;

	/** Most students one SELECTED send names. */
	static final int MAX_SELECTED = 500;

	/** Most announcements one package or exam page shows. */
	static final int MAX_FOR_TARGET = 50;

	private static final int MAX_PAGE_SIZE = 100;

	private static final Set<String> AUDIENCES = Set.of(AnnouncementAudience.ALL, AnnouncementAudience.MODULE,
			AnnouncementAudience.EXAM, AnnouncementAudience.SELECTED);

	/** The history columns. The body is left out on purpose: the history shows what was sent and to how many. */
	private static final String HISTORY = "SELECT a.id, a.title_en, a.title_bn, a.audience, a.module_id,"
			+ " m.title_en AS module_title_en, m.title_bn AS module_title_bn, a.exam_id,"
			+ " x.title_en AS exam_title_en, x.title_bn AS exam_title_bn, a.withdrawn_at, a.created_at,"
			+ " (SELECT count(*) FROM student_notices n WHERE n.announcement_id = a.id) AS recipients"
			+ " FROM announcements a"
			+ " LEFT JOIN live_exam_modules m ON m.id = a.module_id"
			+ " LEFT JOIN live_exams x ON x.id = a.exam_id";

	private final NamedParameterJdbcTemplate jdbc;

	private final JdbcTemplate plain;

	private final LiveExamModuleRepository modules;

	private final LiveExamRepository exams;

	private final AuditRecorder audit;

	private final Clock clock;

	public AnnouncementService(JdbcTemplate jdbc, LiveExamModuleRepository modules, LiveExamRepository exams,
			AuditRecorder audit, Clock clock) {
		this.jdbc = new NamedParameterJdbcTemplate(jdbc);
		this.plain = jdbc;
		this.modules = modules;
		this.exams = exams;
		this.audit = audit;
		this.clock = clock;
	}

	/**
	 * Sends an announcement. Titles and bodies may be blank, but not all four. The audience decides the target: ALL takes none,
	 * MODULE a package, EXAM an exam, and SELECTED 1 to 500 students. A package or exam with no recipients is allowed: a student
	 * who joins later sees the announcement on the page.
	 */
	@Transactional
	public AnnouncementView send(CurrentActor actor, AnnouncementRequest request) {
		String titleEn = clean(request.titleEn(), MAX_TITLE_LENGTH);
		String titleBn = clean(request.titleBn(), MAX_TITLE_LENGTH);
		String bodyEn = clean(request.bodyEn(), MAX_BODY_LENGTH);
		String bodyBn = clean(request.bodyBn(), MAX_BODY_LENGTH);
		if (titleEn == null && titleBn == null && bodyEn == null && bodyBn == null) {
			throw validationFailed();
		}
		String audience = audience(request.audience());
		UUID moduleId = request.moduleId();
		UUID examId = request.examId();
		List<UUID> studentIds = request.studentIds() == null ? List.of() : request.studentIds();
		if (!AnnouncementAudience.MODULE.equals(audience)) {
			requireAbsent(moduleId);
		}
		if (!AnnouncementAudience.EXAM.equals(audience)) {
			requireAbsent(examId);
		}
		if (!AnnouncementAudience.SELECTED.equals(audience) && !studentIds.isEmpty()) {
			throw validationFailed();
		}
		if (AnnouncementAudience.MODULE.equals(audience)) {
			requireModule(moduleId);
		}
		else if (AnnouncementAudience.EXAM.equals(audience)) {
			requireExam(examId);
		}
		else if (AnnouncementAudience.SELECTED.equals(audience)) {
			studentIds = requireActiveStudents(studentIds);
		}

		UUID id = UUID.randomUUID();
		Instant now = clock.instant();
		plain.update("INSERT INTO announcements (id, title_en, title_bn, body_en, body_bn, audience, module_id, exam_id,"
				+ " created_by, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", id, titleEn, titleBn, bodyEn, bodyBn,
				audience, moduleId, examId, actor.userId(), Timestamp.from(now));
		MapSqlParameterSource params = recipientParams(moduleId, examId, studentIds, now)
			.addValue("announcementId", id)
			.addValue("createdAt", Timestamp.from(now));
		int recipients = jdbc.update(insertNotices(audience), params);
		audit.record(AuditRecorder.ANNOUNCEMENT_SENT, AuditRecorder.ANNOUNCEMENT_ENTITY, id);
		return new AnnouncementView(id, audience, moduleId, examId, recipients, now);
	}

	/**
	 * How many students a send to this target would reach now. It never writes. It uses the same rule and student filter as
	 * the send. SELECTED has no preview: the students are picked by hand.
	 */
	@Transactional(readOnly = true)
	public Preview preview(String audienceValue, UUID moduleId, UUID examId) {
		String audience = audience(audienceValue);
		if (AnnouncementAudience.SELECTED.equals(audience)) {
			throw validationFailed();
		}
		if (AnnouncementAudience.MODULE.equals(audience)) {
			requireModule(moduleId);
			requireAbsent(examId);
		}
		else if (AnnouncementAudience.EXAM.equals(audience)) {
			requireExam(examId);
			requireAbsent(moduleId);
		}
		else {
			requireAbsent(moduleId);
			requireAbsent(examId);
		}
		Integer count = jdbc.queryForObject("SELECT count(*) FROM users u WHERE " + AnnouncementAudience.recipients(audience),
				recipientParams(moduleId, examId, List.of(), clock.instant()), Integer.class);
		return new Preview(count == null ? 0 : count);
	}

	/**
	 * The sent announcements, newest first, without bodies. {@code audience} narrows the rows; absent means every audience.
	 * Size is capped at {@link #MAX_PAGE_SIZE}.
	 */
	@Transactional(readOnly = true)
	public Page<AnnouncementRow> history(String audienceValue, Pageable pageable) {
		String audience = audienceFilter(audienceValue);
		int size = Math.min(Math.max(pageable.getPageSize(), 1), MAX_PAGE_SIZE);
		String where = audience == null ? "" : " WHERE a.audience = :audience";
		MapSqlParameterSource params = new MapSqlParameterSource();
		if (audience != null) {
			params.addValue("audience", audience);
		}
		Long total = jdbc.queryForObject("SELECT count(*) FROM announcements a" + where, params, Long.class);
		params.addValue("limit", size).addValue("offset", (long) pageable.getPageNumber() * size);
		List<AnnouncementRow> rows = jdbc.query(HISTORY + where + " ORDER BY a.created_at DESC, a.id DESC LIMIT :limit OFFSET :offset",
				params, (rs, number) -> row(rs));
		return new PageImpl<>(rows, PageRequest.of(pageable.getPageNumber(), size), total == null ? 0 : total);
	}

	/**
	 * Withdraws an announcement: it leaves the notices, the unread count and the package or exam page, and the record stays.
	 * Withdrawing again changes nothing and is not audited again.
	 */
	@Transactional
	public AnnouncementRow withdraw(CurrentActor actor, UUID id) {
		Integer exists = jdbc.queryForObject("SELECT count(*) FROM announcements WHERE id = :id",
				new MapSqlParameterSource("id", id), Integer.class);
		if (exists == null || exists == 0) {
			throw notFound();
		}
		int changed = jdbc.update("UPDATE announcements SET withdrawn_at = :now, withdrawn_by = :actor"
				+ " WHERE id = :id AND withdrawn_at IS NULL", new MapSqlParameterSource()
					.addValue("now", Timestamp.from(clock.instant()))
					.addValue("actor", actor.userId())
					.addValue("id", id));
		if (changed == 1) {
			audit.record(AuditRecorder.ANNOUNCEMENT_WITHDRAWN, AuditRecorder.ANNOUNCEMENT_ENTITY, id);
		}
		return find(id);
	}

	/**
	 * The announcements a student sees on a package page (MODULE) or an exam page (EXAM): the non-withdrawn ones for that
	 * target, newest first, at most {@link #MAX_FOR_TARGET}. Empty when the target is unknown or deleted, and empty for a
	 * student who neither holds the target nor (for a free exam) may see it. Nothing here tells a caller that other
	 * announcements exist.
	 */
	@Transactional(readOnly = true)
	public List<StudentAnnouncement> visibleTo(UUID studentId, String audience, UUID targetId) {
		boolean visible;
		if (AnnouncementAudience.MODULE.equals(audience)) {
			if (modules.findByIdAndDeletedAtIsNull(targetId).isEmpty()) {
				return List.of();
			}
			visible = holds(studentId, AnnouncementAudience.MODULE, targetId);
		}
		else {
			var exam = exams.findByIdAndDeletedAtIsNull(targetId);
			if (exam.isEmpty()) {
				return List.of();
			}
			visible = LiveExamAccess.free(exam.get()) || holds(studentId, AnnouncementAudience.EXAM, targetId);
		}
		if (!visible) {
			return List.of();
		}
		String column = AnnouncementAudience.MODULE.equals(audience) ? "a.module_id" : "a.exam_id";
		String sql = "SELECT a.id, a.title_en, a.title_bn, a.body_en, a.body_bn, a.created_at FROM announcements a"
				+ " WHERE a.audience = :audience AND " + column + " = :targetId AND a.withdrawn_at IS NULL"
				+ " ORDER BY a.created_at DESC, a.id DESC LIMIT :limit";
		return jdbc.query(sql, new MapSqlParameterSource().addValue("audience", audience)
			.addValue("targetId", targetId)
			.addValue("limit", MAX_FOR_TARGET),
				(rs, number) -> new StudentAnnouncement(rs.getObject("id", UUID.class), rs.getString("title_en"),
						rs.getString("title_bn"), rs.getString("body_en"), rs.getString("body_bn"),
						rs.getTimestamp("created_at").toInstant()));
	}

	/** Whether the student is a recipient of the target, by the same rule as the send. */
	private boolean holds(UUID studentId, String audience, UUID targetId) {
		boolean module = AnnouncementAudience.MODULE.equals(audience);
		MapSqlParameterSource params = recipientParams(module ? targetId : null, module ? null : targetId, List.of(),
				clock.instant()).addValue("me", studentId);
		Integer count = jdbc.queryForObject("SELECT count(*) FROM users u WHERE u.id = :me AND "
				+ AnnouncementAudience.recipients(audience), params, Integer.class);
		return count != null && count > 0;
	}

	/** The notices of one send: one row per recipient, with the target named only for its own audience. */
	private static String insertNotices(String audience) {
		String examId = AnnouncementAudience.EXAM.equals(audience) ? ":examId" : "NULL";
		String moduleId = AnnouncementAudience.MODULE.equals(audience) ? ":moduleId" : "NULL";
		return "INSERT INTO student_notices (id, user_id, kind, message_key, exam_id, module_id, participant_id, created_at,"
				+ " announcement_id)"
				+ " SELECT gen_random_uuid(), u.id, 'ANNOUNCEMENT', CAST(u.id AS VARCHAR) || ':' || CAST(:announcementId AS VARCHAR),"
				+ " " + examId + ", " + moduleId + ", NULL, :createdAt, :announcementId"
				+ " FROM users u WHERE " + AnnouncementAudience.recipients(audience);
	}

	private static MapSqlParameterSource recipientParams(UUID moduleId, UUID examId, List<UUID> studentIds, Instant now) {
		return new MapSqlParameterSource().addValue("moduleId", moduleId).addValue("examId", examId)
			.addValue("studentIds", studentIds).addValue("now", Timestamp.from(now));
	}

	private AnnouncementRow find(UUID id) {
		List<AnnouncementRow> rows = jdbc.query(HISTORY + " WHERE a.id = :id", new MapSqlParameterSource("id", id),
				(rs, number) -> row(rs));
		if (rows.isEmpty()) {
			throw notFound();
		}
		return rows.get(0);
	}

	private static AnnouncementRow row(ResultSet rs) throws SQLException {
		Timestamp withdrawnAt = rs.getTimestamp("withdrawn_at");
		return new AnnouncementRow(rs.getObject("id", UUID.class), rs.getString("title_en"), rs.getString("title_bn"),
				rs.getString("audience"), rs.getObject("module_id", UUID.class), rs.getString("module_title_en"),
				rs.getString("module_title_bn"), rs.getObject("exam_id", UUID.class), rs.getString("exam_title_en"),
				rs.getString("exam_title_bn"), withdrawnAt != null, withdrawnAt == null ? null : withdrawnAt.toInstant(),
				rs.getTimestamp("created_at").toInstant(), rs.getInt("recipients"));
	}

	private void requireModule(UUID moduleId) {
		if (moduleId == null) {
			throw validationFailed();
		}
		if (modules.findByIdAndDeletedAtIsNull(moduleId).isEmpty()) {
			throw notFound();
		}
	}

	private void requireExam(UUID examId) {
		if (examId == null) {
			throw validationFailed();
		}
		if (exams.findByIdAndDeletedAtIsNull(examId).isEmpty()) {
			throw notFound();
		}
	}

	/**
	 * Returns the distinct ids. Every one must be an active, non-deleted student, or the whole send is refused, with each bad id
	 * named in the field errors.
	 */
	private List<UUID> requireActiveStudents(List<UUID> studentIds) {
		if (studentIds.isEmpty()) {
			throw fieldFailed("studentIds", "Choose at least one student.");
		}
		if (studentIds.stream().anyMatch(id -> id == null)) {
			throw fieldFailed("studentIds", "Every student id must be a UUID.");
		}
		List<UUID> distinct = studentIds.stream().distinct().toList();
		if (distinct.size() > MAX_SELECTED) {
			throw fieldFailed("studentIds", "Choose no more than " + MAX_SELECTED + " students per send.");
		}
		Set<UUID> active = new HashSet<>(jdbc.queryForList("SELECT u.id FROM users u WHERE u.id IN (:ids)"
				+ " AND u.role = 'STUDENT' AND u.status = 'ACTIVE' AND u.deleted_at IS NULL",
				new MapSqlParameterSource("ids", distinct), UUID.class));
		List<FieldErrorDetail> bad = new ArrayList<>();
		for (UUID id : distinct) {
			if (!active.contains(id)) {
				bad.add(new FieldErrorDetail("studentIds", id + " is not an active student."));
			}
		}
		if (!bad.isEmpty()) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.", bad);
		}
		return distinct;
	}

	private static void requireAbsent(UUID target) {
		if (target != null) {
			throw validationFailed();
		}
	}

	/** Trims and upper-cases the audience. It must be one of the four. */
	private static String audience(String value) {
		if (value == null || value.isBlank()) {
			throw validationFailed();
		}
		String audience = value.trim().toUpperCase(Locale.ROOT);
		if (!AUDIENCES.contains(audience)) {
			throw validationFailed();
		}
		return audience;
	}

	/** A history filter: absent means every audience, and a value must be one of the four. */
	private static String audienceFilter(String value) {
		return value == null || value.isBlank() ? null : audience(value);
	}

	/** Blank becomes null. Trimmed text over the limit is refused, never cut. */
	private static String clean(String value, int max) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String text = value.trim();
		if (text.length() > max) {
			throw validationFailed();
		}
		return text;
	}

	private static ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
	}

	private static ApiException fieldFailed(String field, String message) {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.",
				List.of(new FieldErrorDetail(field, message)));
	}

	private static ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	/**
	 * {@code audience} is ALL, MODULE, EXAM or SELECTED. {@code moduleId} is required for MODULE, {@code examId} for EXAM, and
	 * {@code studentIds} (1 to 500) for SELECTED. Each must be empty for any other audience.
	 */
	public record AnnouncementRequest(@Size(max = MAX_TITLE_LENGTH) String titleEn,
			@Size(max = MAX_TITLE_LENGTH) String titleBn, @Size(max = MAX_BODY_LENGTH) String bodyEn,
			@Size(max = MAX_BODY_LENGTH) String bodyBn, @NotBlank String audience, UUID moduleId, UUID examId,
			List<UUID> studentIds) {
	}

	/** {@code recipients} is how many students got the notice. */
	public record AnnouncementView(UUID id, String audience, UUID moduleId, UUID examId, int recipients, Instant createdAt) {
	}

	/** The confirm dialog's count. Students who join later are not in it. */
	public record Preview(int recipients) {
	}

	/**
	 * One history row. The module titles are set for a MODULE audience and the exam titles for an EXAM audience.
	 * {@code recipients} is the notice count, and {@code withdrawnAt} is null unless the announcement is withdrawn.
	 */
	public record AnnouncementRow(UUID id, String titleEn, String titleBn, String audience, UUID moduleId,
			String moduleTitleEn, String moduleTitleBn, UUID examId, String examTitleEn, String examTitleBn,
			boolean withdrawn, Instant withdrawnAt, Instant createdAt, int recipients) {
	}

	/** One announcement on a package or exam page. */
	public record StudentAnnouncement(UUID id, String titleEn, String titleBn, String bodyEn, String bodyBn,
			Instant createdAt) {
	}

}
