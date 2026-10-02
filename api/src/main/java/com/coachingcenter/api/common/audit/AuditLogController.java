package com.coachingcenter.api.common.audit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.web.PageResponse;

@RestController
public class AuditLogController {

	private final AuditLogRepository auditLogs;

	private final PermissionChecker permissions;

	public AuditLogController(AuditLogRepository auditLogs, PermissionChecker permissions) {
		this.auditLogs = auditLogs;
		this.permissions = permissions;
	}

	@GetMapping("/api/v1/audit-log")
	public PageResponse<AuditLog> list(@PageableDefault(size = 20) Pageable pageable) {
		if (!permissions.has(Permissions.AUDIT_READ)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
		return PageResponse.from(auditLogs.findAll(pageable));
	}

}
