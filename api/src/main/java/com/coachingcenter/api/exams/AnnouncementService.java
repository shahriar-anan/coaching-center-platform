package com.coachingcenter.api.exams;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
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

import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Staff announcements (R-5). Sending writes the announcement and one ANNOUNCEMENT notice per target student, in one
 * transaction. A notice is never edited or deleted afterwards.
 */
@Service
public class AnnouncementService {

	static final int MAX_TITLE_LENGTH = 500;

	static final int MAX_BODY_LENGTH = 5000;

	/** Every active student. The message key is per student, so the unique (kind, message_key) holds. */
	private static final String TO_ALL = """
			INSERT INTO student_notices (id, user_id, kind, message_key, exam_id, module_id, participant_id, created_at,
			  announcement_id)
			SELECT gen_random_uuid(), u.id, 'ANNOUNCEMENT', CAST(u.id AS VARCHAR) || ':' || CAST(:announcementId AS VARCHAR),
			  NULL, NULL, NULL, :createdAt, :announcementId
			FROM users u
			WHERE u.role = 'STUDENT' AND u.status = 'ACTIVE' AND u.deleted_at IS NULL
			""";

	/**
	 * Active students with access to the module: a roster row, or an active grant on the module or on one of its exams.
	 * The notice names the module.
	 */
	private static final String TO_MODULE = """
			INSERT INTO student_notices (id, user_id, kind, message_key, exam_id, module_id, participant_id, created_at,
			  announcement_id)
			SELECT gen_random_uuid(), u.id, 'ANNOUNCEMENT', CAST(u.id AS VARCHAR) || ':' || CAST(:announcementId AS VARCHAR),
			  NULL, :moduleId, NULL, :createdAt, :announcementId
			FROM users u
			WHERE u.role = 'STUDENT' AND u.status = 'ACTIVE' AND u.deleted_at IS NULL
			  AND (EXISTS (SELECT 1 FROM live_exam_roster r WHERE r.module_id = :moduleId AND r.student_id = u.id)
			    OR EXISTS (SELECT 1 FROM live_exam_grants g
			      WHERE g.module_id = :moduleId AND g.student_id = u.id AND g.revoked_at IS NULL))
			""";

	/** Newest first. The body is left out on purpose: the history shows what was sent and to how many. */
	private static final String HISTORY = """
			SELECT a.id, a.title_en, a.title_bn, a.audience, a.module_id, m.title_en AS module_title_en,
			  m.title_bn AS module_title_bn, a.created_at,
			  (SELECT count(*) FROM student_notices n WHERE n.announcement_id = a.id) AS recipients
			FROM announcements a
			LEFT JOIN live_exam_modules m ON m.id = a.module_id
			ORDER BY a.created_at DESC, a.id DESC
			LIMIT :limit OFFSET :offset
			""";

	private static final int MAX_PAGE_SIZE = 100;

	private final NamedParameterJdbcTemplate jdbc;

	private final JdbcTemplate plain;

	private final LiveExamModuleRepository modules;

	private final AuditRecorder audit;

	private final Clock clock;

	public AnnouncementService(JdbcTemplate jdbc, LiveExamModuleRepository modules, AuditRecorder audit, Clock clock) {
		this.jdbc = new NamedParameterJdbcTemplate(jdbc);
		this.plain = jdbc;
		this.modules = modules;
		this.audit = audit;
		this.clock = clock;
	}

	/**
	 * Sends an announcement to all students ({@code audience} ALL, no module) or to the students with access to one
	 * module ({@code audience} MODULE). Titles and bodies may be blank, but not all four.
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
		String audience = request.audience().trim().toUpperCase();
		UUID moduleId = request.moduleId();
		if (AUDIENCE_ALL.equals(audience)) {
			if (moduleId != null) {
				throw validationFailed();
			}
		}
		else if (AUDIENCE_MODULE.equals(audience)) {
			if (moduleId == null) {
				throw validationFailed();
			}
			if (modules.findByIdAndDeletedAtIsNull(moduleId).isEmpty()) {
				throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
			}
		}
		else {
			throw validationFailed();
		}

		UUID id = UUID.randomUUID();
		Instant now = clock.instant();
		plain.update("""
				INSERT INTO announcements (id, title_en, title_bn, body_en, body_bn, audience, module_id, created_by, created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", id, titleEn, titleBn, bodyEn, bodyBn, audience, moduleId, actor.userId(), Timestamp.from(now));
		MapSqlParameterSource params = new MapSqlParameterSource()
			.addValue("announcementId", id)
			.addValue("createdAt", Timestamp.from(now));
		int recipients;
		if (AUDIENCE_ALL.equals(audience)) {
			recipients = jdbc.update(TO_ALL, params);
		}
		else {
			recipients = jdbc.update(TO_MODULE, params.addValue("moduleId", moduleId));
		}
		audit.record(AuditRecorder.ANNOUNCEMENT_SENT, AuditRecorder.ANNOUNCEMENT_ENTITY, id);
		return new AnnouncementView(id, audience, moduleId, recipients, now);
	}

	/** The sent announcements, newest first, without bodies (AG-01). Size is capped at {@link #MAX_PAGE_SIZE}. */
	@Transactional(readOnly = true)
	public Page<AnnouncementRow> history(Pageable pageable) {
		int size = Math.min(Math.max(pageable.getPageSize(), 1), MAX_PAGE_SIZE);
		Long total = plain.queryForObject("SELECT count(*) FROM announcements", Long.class);
		MapSqlParameterSource params = new MapSqlParameterSource().addValue("limit", size)
			.addValue("offset", (long) pageable.getPageNumber() * size);
		List<AnnouncementRow> rows = jdbc.query(HISTORY, params, (rs, number) -> new AnnouncementRow(
				rs.getObject("id", UUID.class), rs.getString("title_en"), rs.getString("title_bn"),
				rs.getString("audience"), rs.getObject("module_id", UUID.class), rs.getString("module_title_en"),
				rs.getString("module_title_bn"), rs.getTimestamp("created_at").toInstant(), rs.getInt("recipients")));
		return new PageImpl<>(rows, PageRequest.of(pageable.getPageNumber(), size), total == null ? 0 : total);
	}

	static final String AUDIENCE_ALL = "ALL";

	static final String AUDIENCE_MODULE = "MODULE";

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

	/** {@code audience} is ALL or MODULE; {@code moduleId} is required for MODULE and must be empty for ALL. */
	public record AnnouncementRequest(@Size(max = MAX_TITLE_LENGTH) String titleEn,
			@Size(max = MAX_TITLE_LENGTH) String titleBn, @Size(max = MAX_BODY_LENGTH) String bodyEn,
			@Size(max = MAX_BODY_LENGTH) String bodyBn, @NotBlank String audience, UUID moduleId) {
	}

	/** {@code recipients} is how many students got the notice. */
	public record AnnouncementView(UUID id, String audience, UUID moduleId, int recipients, Instant createdAt) {
	}

	/** One history row. The module titles are set for a MODULE audience only. {@code recipients} is the notice count. */
	public record AnnouncementRow(UUID id, String titleEn, String titleBn, String audience, UUID moduleId,
			String moduleTitleEn, String moduleTitleBn, Instant createdAt, int recipients) {
	}

}
