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

	private static final String USER = "USER";

	private final AuditLogRepository auditLogs;

	public AuditRecorder(AuditLogRepository auditLogs) {
		this.auditLogs = auditLogs;
	}

	public void record(String action, UUID entityId) {
		AuditLog row = new AuditLog();
		row.setActorUserId(actorId());
		row.setAction(action);
		row.setEntityType(USER);
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
