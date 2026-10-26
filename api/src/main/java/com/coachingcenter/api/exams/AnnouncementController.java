package com.coachingcenter.api.exams;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.web.PageResponse;

import jakarta.validation.Valid;

@RestController
public class AnnouncementController {

	private final AnnouncementService announcements;

	private final PermissionChecker permissions;

	public AnnouncementController(AnnouncementService announcements, PermissionChecker permissions) {
		this.announcements = announcements;
		this.permissions = permissions;
	}

	/** Master Admin only. Sends to all students, or to the students with access to one module (R-5). */
	@PostMapping("/api/v1/announcements")
	public ResponseEntity<AnnouncementService.AnnouncementView> send(
			@Valid @RequestBody AnnouncementService.AnnouncementRequest request) {
		if (!permissions.has(Permissions.LIVE_EXAM_ANNOUNCE)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
		return ResponseEntity.status(HttpStatus.CREATED).body(announcements.send(actor(), request));
	}

	/** Master Admin only. The history of sent announcements, newest first, without bodies (AG-01). */
	@GetMapping("/api/v1/announcements")
	public PageResponse<AnnouncementService.AnnouncementRow> history(@PageableDefault(size = 20) Pageable pageable) {
		if (!permissions.has(Permissions.LIVE_EXAM_ANNOUNCE)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
		return PageResponse.from(announcements.history(pageable));
	}

	private static CurrentActor actor() {
		var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor) {
			return actor;
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

}
