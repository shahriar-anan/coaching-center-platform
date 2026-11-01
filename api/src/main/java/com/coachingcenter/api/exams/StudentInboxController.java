package com.coachingcenter.api.exams;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

	private final AnnouncementService announcements;

	private final PermissionChecker permissions;

	public StudentInboxController(StudentInboxService inbox, AnnouncementService announcements,
			PermissionChecker permissions) {
		this.inbox = inbox;
		this.announcements = announcements;
		this.permissions = permissions;
	}

	/**
	 * The announcements on one package ({@code kind=package}) or one exam ({@code kind=exam}) that the student may see there:
	 * newest first, withdrawn ones left out. A non-holder gets an empty list. An unknown kind is 404.
	 */
	@GetMapping("/api/v1/me/announcements")
	public List<AnnouncementService.StudentAnnouncement> announcementsOn(@RequestParam(required = false) String kind,
			@RequestParam UUID id) {
		UUID student = student();
		String audience = switch (kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT)) {
			case "package" -> AnnouncementAudience.MODULE;
			case "exam" -> AnnouncementAudience.EXAM;
			default -> throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
		};
		return announcements.visibleTo(student, audience, id);
	}

	/** {@code group} is RESULTS, EXAMS or ANNOUNCEMENTS (N1). Absent means every notice, as before. */
	@GetMapping("/api/v1/me/notices")
	public PageResponse<StudentInboxService.NoticeView> list(@RequestParam(required = false) String group,
			@PageableDefault(size = 20) Pageable pageable) {
		NoticeGroup tab = NoticeGroup.parse(group);
		return PageResponse.from(inbox.list(student(), tab, pageable));
	}

	/** {@code count} is the total unread; {@code byGroup} counts them per tab (N1). */
	@GetMapping("/api/v1/me/notices/unread-count")
	public StudentInboxService.UnreadCounts unreadCount() {
		return inbox.unreadCount(student());
	}

	@PostMapping("/api/v1/me/notices/{id}/read")
	public StudentInboxService.NoticeView read(@PathVariable UUID id) {
		return inbox.markRead(student(), id);
	}

	/** Marks every unread notice read, or only the tab named by {@code group} (N1). */
	@PostMapping("/api/v1/me/notices/read-all")
	public Map<String, Integer> readAll(@RequestParam(required = false) String group) {
		NoticeGroup tab = NoticeGroup.parse(group);
		return Map.of("updated", inbox.markAllRead(student(), tab));
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
