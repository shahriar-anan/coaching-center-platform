package com.coachingcenter.api.exams;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.web.PageResponse;

import jakarta.validation.Valid;

/** Staff announcements. Master Admin and System Admin only (plan section 7). Students and instructors get 403 on every route. */
@RestController
public class AnnouncementController {

	private final AnnouncementService announcements;

	private final PermissionChecker permissions;

	public AnnouncementController(AnnouncementService announcements, PermissionChecker permissions) {
		this.announcements = announcements;
		this.permissions = permissions;
	}

	/** Sends to every student, one package, one exam, or the students picked (R-5, plan section 6). */
	@PostMapping("/api/v1/announcements")
	public ResponseEntity<AnnouncementService.AnnouncementView> send(
			@Valid @RequestBody AnnouncementService.AnnouncementRequest request) {
		requireAnnounce();
		return ResponseEntity.status(HttpStatus.CREATED).body(announcements.send(actor(), request));
	}

	/** How many students a send would reach now. Writes nothing. */
	@GetMapping("/api/v1/announcements/preview")
	public AnnouncementService.Preview preview(@RequestParam(required = false) String audience,
			@RequestParam(required = false) UUID moduleId, @RequestParam(required = false) UUID examId) {
		requireAnnounce();
		return announcements.preview(audience, moduleId, examId);
	}

	/** The sent announcements, newest first, without bodies. {@code audience} narrows the rows (plan section 7). */
	@GetMapping("/api/v1/announcements")
	public PageResponse<AnnouncementService.AnnouncementRow> history(@RequestParam(required = false) String audience,
			@PageableDefault(size = 20) Pageable pageable) {
		requireAnnounce();
		return PageResponse.from(announcements.history(audience, pageable));
	}

	/** Hides an announcement from the notices and the pages. Idempotent. Returns the history row. */
	@PutMapping("/api/v1/announcements/{id}/withdraw")
	public AnnouncementService.AnnouncementRow withdraw(@PathVariable UUID id) {
		requireAnnounce();
		return announcements.withdraw(actor(), id);
	}

	private void requireAnnounce() {
		if (!permissions.has(Permissions.LIVE_EXAM_ANNOUNCE)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
	}

	private static CurrentActor actor() {
		var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor) {
			return actor;
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

}
