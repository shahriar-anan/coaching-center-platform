package com.coachingcenter.api.common.audit;

import java.time.Instant;
import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.coachingcenter.api.auth.permission.CurrentActor;

@Service
public class AuditRecorder {

	public static final String USER_DELETED = "USER_DELETED";

	public static final String USER_STATUS_CHANGED = "USER_STATUS_CHANGED";

	public static final String ACTIVATION_CODE_ISSUED = "ACTIVATION_CODE_ISSUED";

	public static final String PASSWORD_RESET = "PASSWORD_RESET";

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

	public static final String LIVE_EXAM_CATEGORY_ENTITY = "LIVE_EXAM_CATEGORY";

	public static final String LIVE_EXAM_MODULE_DELETE = "LIVE_EXAM_MODULE_DELETE";

	public static final String LIVE_EXAM_MODULE_ARCHIVE = "LIVE_EXAM_MODULE_ARCHIVE";

	public static final String LIVE_EXAM_MODULE_ENTITY = "LIVE_EXAM_MODULE";

	public static final String LIVE_EXAM_DELETE = "LIVE_EXAM_DELETE";

	public static final String LIVE_EXAM_QUESTION_DELETE = "LIVE_EXAM_QUESTION_DELETE";

	public static final String LIVE_EXAM_ENTITY = "LIVE_EXAM";

	public static final String LIVE_EXAM_INSTRUCTOR_UNASSIGN = "LIVE_EXAM_INSTRUCTOR_UNASSIGN";

	public static final String LIVE_EXAM_RESULT_PUBLISH = "LIVE_EXAM_RESULT_PUBLISH";

	public static final String LIVE_EXAM_PARTICIPANT_ENTITY = "LIVE_EXAM_PARTICIPANT";

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
