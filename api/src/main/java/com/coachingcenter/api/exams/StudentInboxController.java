package com.coachingcenter.api.exams;

import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.web.PageResponse;

@RestController
public class StudentInboxController {

	private final StudentInboxService inbox;

	private final PermissionChecker permissions;

	public StudentInboxController(StudentInboxService inbox, PermissionChecker permissions) {
		this.inbox = inbox;
		this.permissions = permissions;
	}

	@GetMapping("/api/v1/me/notices")
	public PageResponse<StudentInboxService.NoticeView> list(@PageableDefault(size = 20) Pageable pageable) {
		return PageResponse.from(inbox.list(student(), pageable));
	}

	@GetMapping("/api/v1/me/notices/unread-count")
	public Map<String, Integer> unreadCount() {
		return Map.of("count", inbox.unreadCount(student()));
	}

	@PostMapping("/api/v1/me/notices/{id}/read")
	public StudentInboxService.NoticeView read(@PathVariable UUID id) {
		return inbox.markRead(student(), id);
	}

	@PostMapping("/api/v1/me/notices/read-all")
	public Map<String, Integer> readAll() {
		return Map.of("updated", inbox.markAllRead(student()));
	}

	private UUID student() {
		if (!permissions.has(Permissions.LIVE_EXAM_BROWSE)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
		var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor) {
			if (actor.role() != Role.STUDENT) {
				throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
			}
			return actor.userId();
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

}
