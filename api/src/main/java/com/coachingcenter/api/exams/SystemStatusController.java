package com.coachingcenter.api.exams;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

/** GET /api/v1/system-status: the Master Admin's configuration checks. */
@RestController
public class SystemStatusController {

	private final SystemStatusService status;

	private final PermissionChecker permissions;

	public SystemStatusController(SystemStatusService status, PermissionChecker permissions) {
		this.status = status;
		this.permissions = permissions;
	}

	@GetMapping("/api/v1/system-status")
	public SystemStatusResponse get() {
		if (!permissions.has(Permissions.SYSTEM_STATUS_READ)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
		return new SystemStatusResponse(status.checks());
	}

	public record SystemStatusResponse(List<SystemStatusService.Check> checks) {
	}

}
