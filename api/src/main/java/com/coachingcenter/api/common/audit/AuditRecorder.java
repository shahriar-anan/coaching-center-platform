package com.coachingcenter.api.common.audit;

import java.time.Instant;
import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.coachingcenter.api.auth.permission.CurrentActor;

@Service
public class AuditRecorder {

	public static final String USER_DELETED = "USER_DELETED";

	public static final String USER_SUSPENDED = "USER_SUSPENDED";

	public static final String USER_RESTORED = "USER_RESTORED";

	public static final String ACTIVATION_CODE_ISSUED = "ACTIVATION_CODE_ISSUED";

	public static final String PASSWORD_RESET = "PASSWORD_RESET";

	public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";

	public static final String PHONE_CHANGED = "PHONE_CHANGED";

	public static final String GOOGLE_ACCOUNT_CREATED = "GOOGLE_ACCOUNT_CREATED";

	public static final String GOOGLE_ACCOUNT_LINKED = "GOOGLE_ACCOUNT_LINKED";

	public static final String COURSE_DELETE = "COURSE_DELETE";

	public static final String COURSE_ARCHIVE = "COURSE_ARCHIVE";

	public static final String COURSE_CLOSE = "COURSE_CLOSE";

	public static final String COURSE_REOPEN = "COURSE_REOPEN";

	public static final String COURSE_CATEGORY_DELETE = "COURSE_CATEGORY_DELETE";

	public static final String BOOK_DELETE = "BOOK_DELETE";

	public static final String BOOK_CATEGORY_DELETE = "BOOK_CATEGORY_DELETE";

	public static final String COURSE_INSTRUCTOR_DELETE = "COURSE_INSTRUCTOR_DELETE";

	public static final String COURSE_INSTRUCTOR_ENTITY = "COURSE_INSTRUCTOR";

	private static final String USER = "USER";

	public static final String COURSE_ENTITY = "COURSE";

	public static final String COURSE_CATEGORY_ENTITY = "COURSE_CATEGORY";

	public static final String BOOK_ENTITY = "BOOK";

	public static final String BOOK_CATEGORY_ENTITY = "BOOK_CATEGORY";

	public static final String PAYMENT_SUCCESS = "PAYMENT_SUCCESS";

	public static final String PAYMENT_FAILED = "PAYMENT_FAILED";

	public static final String CASH_RECEIVED = "CASH_RECEIVED";

	public static final String PAYMENT_ENTITY = "PAYMENT";

	public static final String BOOK_ORDER_ENTITY = "BOOK_ORDER";

	public static final String EXAM_MODULE_DELETE = "EXAM_MODULE_DELETE";

	public static final String EXAM_MODULE_ARCHIVE = "EXAM_MODULE_ARCHIVE";

	public static final String EXAM_MODULE_ENTITY = "EXAM_MODULE";

	public static final String LIVE_EXAM_CATEGORY_DELETE = "LIVE_EXAM_CATEGORY_DELETE";

	public static final String LIVE_EXAM_CATEGORY_ARCHIVE = "LIVE_EXAM_CATEGORY_ARCHIVE";

	public static final String LIVE_EXAM_CATEGORY_RESTORE = "LIVE_EXAM_CATEGORY_RESTORE";

	public static final String LIVE_EXAM_CATEGORY_ENTITY = "LIVE_EXAM_CATEGORY";

	public static final String LIVE_EXAM_MODULE_DELETE = "LIVE_EXAM_MODULE_DELETE";

	public static final String LIVE_EXAM_MODULE_ARCHIVE = "LIVE_EXAM_MODULE_ARCHIVE";

	public static final String LIVE_EXAM_MODULE_RESTORE = "LIVE_EXAM_MODULE_RESTORE";

	public static final String LIVE_EXAM_MODULE_ENTITY = "LIVE_EXAM_MODULE";

	/** A package's routine PDF was uploaded or replaced (notice media plan, F3). Entity: LIVE_EXAM_MODULE. */
	public static final String MODULE_ROUTINE_UPLOAD = "MODULE_ROUTINE_UPLOAD";

	public static final String MODULE_ROUTINE_REMOVE = "MODULE_ROUTINE_REMOVE";

	public static final String LIVE_EXAM_DELETE = "LIVE_EXAM_DELETE";

	public static final String LIVE_EXAM_ARCHIVE = "LIVE_EXAM_ARCHIVE";

	public static final String LIVE_EXAM_RESTORE = "LIVE_EXAM_RESTORE";

	public static final String LIVE_EXAM_QUESTION_DELETE = "LIVE_EXAM_QUESTION_DELETE";

	public static final String LIVE_EXAM_ENTITY = "LIVE_EXAM";

	public static final String ANSWERS_RELEASED = "ANSWERS_RELEASED";

	public static final String ANSWERS_WITHHELD = "ANSWERS_WITHHELD";

	public static final String LIVE_EXAM_INSTRUCTOR_UNASSIGN = "LIVE_EXAM_INSTRUCTOR_UNASSIGN";

	public static final String LIVE_EXAM_INSTRUCTOR_ASSIGN = "LIVE_EXAM_INSTRUCTOR_ASSIGN";
	/** The exam's own pay per sheet was set, changed or cleared (staff pay plan, phase 8). The row holds the exam id only. */
	public static final String LIVE_EXAM_PAY_TERMS_SET = "LIVE_EXAM_PAY_TERMS_SET";

	public static final String LIVE_EXAM_REVISION_PUBLISH = "LIVE_EXAM_REVISION_PUBLISH";

	public static final String LIVE_EXAM_REVISION_DISCARD = "LIVE_EXAM_REVISION_DISCARD";

	public static final String LIVE_EXAM_MODULE_REVISION_PUBLISH = "LIVE_EXAM_MODULE_REVISION_PUBLISH";

	public static final String LIVE_EXAM_MODULE_REVISION_DISCARD = "LIVE_EXAM_MODULE_REVISION_DISCARD";

	public static final String LIVE_EXAM_RETAKE_GRANT = "LIVE_EXAM_RETAKE_GRANT";

	public static final String LIVE_EXAM_RETAKE_REVOKE = "LIVE_EXAM_RETAKE_REVOKE";

	/** A student asked for another written retake. */
	public static final String LIVE_EXAM_RETAKE_REQUEST = "LIVE_EXAM_RETAKE_REQUEST";

	/** The centre declined a retake request. */
	public static final String LIVE_EXAM_RETAKE_DECLINE = "LIVE_EXAM_RETAKE_DECLINE";

	public static final String LIVE_EXAM_PACKAGE_EXAM_ADD = "LIVE_EXAM_PACKAGE_EXAM_ADD";

	public static final String LIVE_EXAM_PACKAGE_EXAM_REMOVE = "LIVE_EXAM_PACKAGE_EXAM_REMOVE";

	public static final String LIVE_EXAM_POPULAR_SET = "LIVE_EXAM_POPULAR_SET";

	public static final String LIVE_EXAM_RESULT_PUBLISH = "LIVE_EXAM_RESULT_PUBLISH";

	public static final String LIVE_EXAM_PARTICIPANT_ENTITY = "LIVE_EXAM_PARTICIPANT";

	public static final String ACCESS_GRANTED = "ACCESS_GRANTED";

	public static final String ACCESS_REVOKED = "ACCESS_REVOKED";

	public static final String ORDER_CONFIRMED = "ORDER_CONFIRMED";

	public static final String ORDER_REJECTED = "ORDER_REJECTED";

	public static final String LIVE_EXAM_ORDER_REFUND = "LIVE_EXAM_ORDER_REFUND";

	public static final String LIVE_EXAM_ORDER_ENTITY = "LIVE_EXAM_ORDER";

	public static final String ANNOUNCEMENT_SENT = "ANNOUNCEMENT_SENT";

	public static final String ANNOUNCEMENT_WITHDRAWN = "ANNOUNCEMENT_WITHDRAWN";

	public static final String ANNOUNCEMENT_ENTITY = "ANNOUNCEMENT";

	public static final String LIVE_EXAM_GRANT_ENTITY = "LIVE_EXAM_GRANT";

	public static final String LIVE_EXAM_REVIEW_APPROVE = "LIVE_EXAM_REVIEW_APPROVE";

	public static final String LIVE_EXAM_REVIEW_HIDE = "LIVE_EXAM_REVIEW_HIDE";

	public static final String LIVE_EXAM_REVIEW_FEATURE = "LIVE_EXAM_REVIEW_FEATURE";

	public static final String LIVE_EXAM_REVIEW_UNFEATURE = "LIVE_EXAM_REVIEW_UNFEATURE";

	public static final String LIVE_EXAM_REVIEW_ENTITY = "LIVE_EXAM_REVIEW";

	public static final String SITE_NOTICE_CREATE = "SITE_NOTICE_CREATE";

	public static final String SITE_NOTICE_UPDATE = "SITE_NOTICE_UPDATE";

	public static final String SITE_NOTICE_PUBLISH = "SITE_NOTICE_PUBLISH";

	public static final String SITE_NOTICE_UNPUBLISH = "SITE_NOTICE_UNPUBLISH";

	public static final String SITE_NOTICE_DELETE = "SITE_NOTICE_DELETE";

	public static final String SITE_NOTICE_ENTITY = "SITE_NOTICE";

	/** A photo or the PDF was added to a public notice (notice media plan, F1). The row holds the notice id only. */
	public static final String SITE_NOTICE_FILE_ADD = "SITE_NOTICE_FILE_ADD";

	public static final String SITE_NOTICE_FILE_REMOVE = "SITE_NOTICE_FILE_REMOVE";

	public static final String SITE_NOTICE_FILE_REORDER = "SITE_NOTICE_FILE_REORDER";

	/** Staff pay ledger (staff pay plan, phase 3). The audit row holds the action and the ledger id only, never an account. */
	public static final String STAFF_PAYMENT_RECORD = "STAFF_PAYMENT_RECORD";

	public static final String STAFF_ADJUSTMENT_RECORD = "STAFF_ADJUSTMENT_RECORD";

	public static final String STAFF_LEDGER_VOID = "STAFF_LEDGER_VOID";

	public static final String STAFF_PAY_SETTINGS_UPDATE = "STAFF_PAY_SETTINGS_UPDATE";

	public static final String STAFF_PAY_DEFAULTS_UPDATE = "STAFF_PAY_DEFAULTS_UPDATE";

	public static final String STAFF_LEDGER_ENTITY = "STAFF_LEDGER";

	public static final String STAFF_PAY_SETTINGS_ENTITY = "STAFF_PAY_SETTINGS";

	public static final String STAFF_PAY_DEFAULTS_ENTITY = "STAFF_PAY_DEFAULTS";

	public static final String PAYOUT_ACCOUNT_ADDED = "PAYOUT_ACCOUNT_ADDED";

	public static final String PAYOUT_ACCOUNT_UPDATED = "PAYOUT_ACCOUNT_UPDATED";

	public static final String PAYOUT_ACCOUNT_REMOVED = "PAYOUT_ACCOUNT_REMOVED";

	public static final String PAYOUT_ACCOUNT_PREFERRED = "PAYOUT_ACCOUNT_PREFERRED";

	/** The entity of a payout account audit row. The row holds the account's id only, never its numbers. */
	public static final String PAYOUT_ACCOUNT_ENTITY = "STAFF_PAYOUT_ACCOUNT";

	private final AuditLogRepository auditLogs;

	public AuditRecorder(AuditLogRepository auditLogs) {
		this.auditLogs = auditLogs;
	}

	public void record(String action, UUID entityId) {
		record(action, USER, entityId);
	}

	public void record(String action, String entityType, UUID entityId) {
		AuditLog row = new AuditLog();
		row.setActorUserId(actorId());
		row.setAction(action);
		row.setEntityType(entityType);
		row.setEntityId(entityId);
		row.setCreatedAt(Instant.now());
		auditLogs.save(row);
	}

	private static UUID actorId() {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor) {
			return actor.userId();
		}
		return null;
	}

}
