package com.coachingcenter.api.staffnotifications;

import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.web.PageResponse;

/**
 * The staff notification centre (staff pay plan, phase 1). Every route acts on the signed-in person's own notifications and
 * needs STAFF_NOTICE_READ, which the three staff roles hold.
 */
@RestController
public class StaffNotificationController {

	private final StaffNotificationService notifications;

	private final PermissionChecker permissions;

	public StaffNotificationController(StaffNotificationService notifications, PermissionChecker permissions) {
		this.notifications = notifications;
		this.permissions = permissions;
	}

	/** {@code unread=true} keeps only the unread ones. {@code size} is at most 50 (default 20). Newest first. */
	@GetMapping("/api/v1/me/staff-notifications")
	public PageResponse<StaffNotificationService.NotificationView> list(@RequestParam(defaultValue = "false") boolean unread,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		Page<StaffNotificationService.NotificationView> rows = notifications.list(me(), unread, page, size);
		return PageResponse.from(rows);
	}

	@GetMapping("/api/v1/me/staff-notifications/unread-count")
	public StaffNotificationService.UnreadCount unreadCount() {
		return notifications.unreadCount(me());
	}

	@PostMapping("/api/v1/me/staff-notifications/{id}/read")
	public StaffNotificationService.NotificationView read(@PathVariable UUID id) {
		return notifications.markRead(me(), id);
	}

	@PostMapping("/api/v1/me/staff-notifications/read-all")
	public Map<String, Integer> readAll() {
		return Map.of("updated", notifications.markAllRead(me()));
	}

	/** The signed-in staff member. A student or a signed-out caller is refused. */
	private UUID me() {
		if (!permissions.has(Permissions.STAFF_NOTICE_READ)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor) {
			return actor.userId();
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

}
