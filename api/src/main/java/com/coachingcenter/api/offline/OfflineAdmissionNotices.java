package com.coachingcenter.api.offline;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/**
 * Writes the student notices and the outbox mails of an offline admission (plan section 7). Both are inserted in the caller's
 * transaction, so a rolled-back change leaves none. Each row has a dedupe key: a repeat is a silent no-op, not an error that would
 * abort the caller. The insert follows {@code StudentNoticeWriter}: only an active student with the role gets a row, so a suspended
 * student gets nothing (plan side effect 14). A mail also needs an account email (C6); mail is sent by the scheduled scan after the
 * commit, as every other exam mail is.
 */
@Component
class OfflineAdmissionNotices {

	static final String ADMISSION_APPROVED = "ADMISSION_APPROVED";

	static final String ADMISSION_CHANGES_REQUESTED = "ADMISSION_CHANGES_REQUESTED";

	static final String ADMISSION_UPDATED = "ADMISSION_UPDATED";

	static final String ADMISSION_CLOSED = "ADMISSION_CLOSED";

	static final String BATCH_ROUTINE_UPDATED = "BATCH_ROUTINE_UPDATED";

	/**
	 * The join code of an admitted batch is ready for the account holder (V94). Since V101 the notice carries the code, as the email
	 * does, while it still works.
	 */
	static final String ADMISSION_JOIN_CODE = "ADMISSION_JOIN_CODE";

	/** A student who already has access to a batch was onboarded to another one (P14). */
	static final String ADMISSION_BATCH_ADDED = "ADMISSION_BATCH_ADDED";

	/** A result sheet was published for an exam of the student's batch (V97). The key is the sheet and the student. */
	static final String OFFLINE_RESULT_PUBLISHED = "OFFLINE_RESULT_PUBLISHED";

	/** A newer result sheet was published for the exam, so the result changed (V97). The key is the sheet and the student. */
	static final String OFFLINE_RESULT_UPDATED = "OFFLINE_RESULT_UPDATED";

	private static final String INSERT_NOTICE = """
			insert into student_notices (id, user_id, kind, message_key, exam_id, module_id, participant_id, created_at, code)
			select ?, u.id, ?, ?, null, null, null, ?, ?
			from users u
			where u.id = ? and u.role = 'STUDENT' and u.status = 'ACTIVE' and u.deleted_at is null
			on conflict (kind, message_key) do nothing
			""";

	/**
	 * The join-code notice. A second code sent with the same key (the same instant) puts the new code on that notice instead of being
	 * dropped, so the newest code is always shown.
	 */
	private static final String INSERT_CODE_NOTICE = """
			insert into student_notices (id, user_id, kind, message_key, exam_id, module_id, participant_id, created_at, code)
			select ?, u.id, ?, ?, null, null, null, ?, ?
			from users u
			where u.id = ? and u.role = 'STUDENT' and u.status = 'ACTIVE' and u.deleted_at is null
			on conflict (kind, message_key) do update set code = excluded.code
			""";

	private static final String INSERT_MAIL = """
			insert into exam_outbound_mail (id, kind, message_key, user_id, attempt_count, status, created_at, updated_at, payload)
			select ?, ?, ?, u.id, 0, 'PENDING', ?, ?, ?
			from users u
			where u.id = ? and u.role = 'STUDENT' and u.status = 'ACTIVE' and u.deleted_at is null
			  and u.email is not null and u.email <> ''
			on conflict (kind, message_key) do nothing
			""";

	private final JdbcTemplate jdbc;

	private final ObjectMapper json;

	private final Clock clock;

	OfflineAdmissionNotices(JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
		this.jdbc = jdbc;
		this.json = json;
		this.clock = clock;
	}

	/** One student notice (in-app). Returns true when the row was new. */
	boolean notice(UUID studentId, String kind, String key) {
		return jdbc.update(INSERT_NOTICE, UUID.randomUUID(), kind, key, Timestamp.from(clock.instant()), null, studentId) == 1;
	}

	/** One student notice that shows a code (the join code, V101). Returns true when the row was new. */
	boolean noticeWithCode(UUID studentId, String kind, String key, String code) {
		return jdbc.update(INSERT_CODE_NOTICE, UUID.randomUUID(), kind, key, Timestamp.from(clock.instant()), code, studentId) == 1;
	}

	/**
	 * Takes the join code off every join-code notice of one admission (its keys start with the admission's id): the code was replaced,
	 * used, or the admission was closed, so it no longer works and is not shown.
	 */
	void clearJoinCodes(UUID admissionId) {
		jdbc.update("update student_notices set code = null where kind = ? and message_key like ? and code is not null",
				ADMISSION_JOIN_CODE, admissionId + ":%");
	}

	/**
	 * One student mail with its values. The payload is written as flat JSON; a mail to a student with no account email is not written
	 * at all. Returns true when the row was new.
	 */
	boolean mail(UUID studentId, String kind, String key, Map<String, ?> payload) {
		return mailRaw(studentId, kind, key, json.writeValueAsString(payload));
	}

	/** One student mail whose payload is a list of rows, as the terms change needs (old and new values). */
	boolean mailWithChanges(UUID studentId, String kind, String key, List<Map<String, String>> changes) {
		return mailRaw(studentId, kind, key, json.writeValueAsString(Map.of("changes", changes)));
	}

	private boolean mailRaw(UUID studentId, String kind, String key, String payload) {
		Timestamp now = Timestamp.from(clock.instant());
		return jdbc.update(INSERT_MAIL, UUID.randomUUID(), kind, key, now, now, payload, studentId) == 1;
	}

}
