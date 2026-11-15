package com.coachingcenter.api.offline;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.coachingcenter.api.staffnotifications.StaffNotificationKind;
import com.coachingcenter.api.staffnotifications.StaffNotificationWriter;

/**
 * The student notices, the mail outbox rows and the staff notifications of the monthly fee (plan section 7). The inserts follow
 * {@code StudentNoticeWriter} and {@code ExamMailQueue}: a student row is written only for an active student, and a mail row only
 * for a student with a verified email, so a suspended student gets nothing. A repeat is a silent no-op, because each kind and key
 * pair is unique. Every key is built by the caller, so it holds the due or the payment id and the kind.
 */
@Component
class OfflineFeeNotices {

	/** A payment was approved and a receipt issued. Keyed by the payment. */
	static final String RECEIVED = "OFFLINE_FEE_RECEIVED";

	/** A payment was voided and its dues are due again. Keyed by the payment. */
	static final String VOIDED = "OFFLINE_FEE_VOIDED";

	/** A due is three days away. Keyed by the due. */
	static final String DUE_SOON = "OFFLINE_FEE_DUE_SOON";

	/** A due is today. Keyed by the due. */
	static final String DUE_TODAY = "OFFLINE_FEE_DUE_TODAY";

	/** A due is three days past and still unpaid. Keyed by the due. */
	static final String OVERDUE = "OFFLINE_FEE_OVERDUE";

	/** The receipt mail of an approved payment. Keyed by the payment. */
	static final String MAIL_RECEIPT = "OFFLINE_FEE_RECEIPT";

	private static final String NOTICE = """
			insert into student_notices (id, user_id, kind, message_key, created_at)
			select ?, u.id, ?, ?, ?
			from users u
			where u.id = ? and u.role = 'STUDENT' and u.status = 'ACTIVE' and u.deleted_at is null
			on conflict (kind, message_key) do nothing
			""";

	private static final String MAIL = """
			insert into exam_outbound_mail (id, kind, message_key, user_id, attempt_count, status, created_at, updated_at)
			select ?, ?, ?, u.id, 0, 'PENDING', ?, ?
			from users u
			where u.id = ? and u.role = 'STUDENT' and u.status = 'ACTIVE' and u.deleted_at is null
			  and u.email is not null and u.email <> ''
			on conflict (kind, message_key) do nothing
			""";

	private static final String MASTER_ADMINS = """
			select id from users where role = 'MASTER_ADMIN' and status = 'ACTIVE' and deleted_at is null
			order by created_at, id
			""";

	private static final String ADMINS = """
			select id from users where role in ('MASTER_ADMIN', 'SYSTEM_ADMIN') and status = 'ACTIVE' and deleted_at is null
			order by role, created_at, id
			""";

	private final JdbcTemplate jdbc;

	private final StaffNotificationWriter staff;

	private final Clock clock;

	OfflineFeeNotices(JdbcTemplate jdbc, StaffNotificationWriter staff, Clock clock) {
		this.jdbc = jdbc;
		this.staff = staff;
		this.clock = clock;
	}

	/** One student notice. Returns true when it was new. */
	boolean studentNotice(String kind, UUID key, UUID studentId) {
		return jdbc.update(NOTICE, UUID.randomUUID(), kind, key.toString(), Timestamp.from(clock.instant()), studentId) == 1;
	}

	/** One mail row for the student, sent after commit by the mail job. Returns true when it was new. */
	boolean studentMail(String kind, UUID key, UUID studentId) {
		Timestamp now = Timestamp.from(clock.instant());
		return jdbc.update(MAIL, UUID.randomUUID(), kind, key.toString(), now, now, studentId) == 1;
	}

	/** The Master Admins, the people told about a pending payment. */
	List<UUID> masterAdmins() {
		return jdbc.queryForList(MASTER_ADMINS, UUID.class);
	}

	/** The Master Admins and the System Admins, the people told about an overdue fee. */
	List<UUID> admins() {
		return jdbc.queryForList(ADMINS, UUID.class);
	}

	/**
	 * One staff notification to each recipient. The dedupe key is the base plus the recipient, so a repeat adds nothing. Returns how
	 * many rows were new.
	 */
	int staff(StaffNotificationKind kind, List<UUID> recipients, Map<String, ?> payload, String link, String dedupeBase) {
		int written = 0;
		for (UUID recipient : recipients) {
			if (staff.notify(recipient, kind, payload, link, dedupeBase + ":" + recipient)) {
				written++;
			}
		}
		return written;
	}

}
