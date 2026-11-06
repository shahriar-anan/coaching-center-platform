package com.coachingcenter.api.exams;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

import tools.jackson.databind.JsonNode;

/**
 * Staff reads and moderation of reviews (reviews plan, section 6). Moderation is for System Admins and the Master Admin.
 * An instructor may read the queue, scoped to their own exams, and may not write.
 */
@RestController
public class LiveExamReviewStaffController {

	private final LiveExamReviewModerationService moderation;

	private final PermissionChecker permissions;

	public LiveExamReviewStaffController(LiveExamReviewModerationService moderation, PermissionChecker permissions) {
		this.moderation = moderation;
		this.permissions = permissions;
	}

	/**
	 * The queue. {@code status}, {@code kind}, {@code targetId}, {@code rating}, {@code featured} (true or false), {@code q}
	 * (student name, code or phone) and {@code sort} (newest, oldest, lowest, highest) filter and order it. {@code size} is at
	 * most 100.
	 */
	@GetMapping("/api/v1/staff/reviews")
	public PageResponse<LiveExamReviewModerationService.StaffRow> list(@RequestParam(required = false) String status,
			@RequestParam(required = false) String kind, @RequestParam(required = false) UUID targetId,
			@RequestParam(required = false) Integer rating, @RequestParam(required = false) String featured,
			@RequestParam(required = false) String q, @RequestParam(required = false) String sort,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		requireRead();
		var filters = LiveExamReviewModerationService.Filters.parse(status, kind, targetId, rating, featured, q, sort);
		return moderation.list(actor(), filters, Math.max(page, 0), size < 1 ? 20 : Math.min(size, 100));
	}

	/**
	 * The tab counts {@code {pending, approved, hidden, featured}} for the same {@code kind}, {@code targetId}, {@code rating},
	 * {@code featured} and {@code q} as the queue. A {@code status} parameter is not read, so it cannot change the counts.
	 */
	@GetMapping("/api/v1/staff/reviews/counts")
	public LiveExamReviewModerationService.Counts counts(@RequestParam(required = false) String kind,
			@RequestParam(required = false) UUID targetId, @RequestParam(required = false) Integer rating,
			@RequestParam(required = false) String featured, @RequestParam(required = false) String q) {
		requireRead();
		var filters = LiveExamReviewModerationService.Filters.parse(null, kind, targetId, rating, featured, q, null);
		return moderation.counts(actor(), filters);
	}

	@GetMapping("/api/v1/staff/reviews/pending-count")
	public LiveExamReviewModerationService.PendingCount pendingCount() {
		requireModerate();
		return moderation.pendingCount();
	}

	/**
	 * The author's picture of a review in the queue. The same readers as the queue, and an instructor only for their own
	 * exams. Never cached by shared caches.
	 */
	@GetMapping("/api/v1/staff/reviews/{id}/photo")
	public org.springframework.http.ResponseEntity<org.springframework.core.io.InputStreamResource> photo(@PathVariable UUID id) {
		requireRead();
		var photo = moderation.photoOf(actor(), id);
		return com.coachingcenter.api.files.FileResponses.inline(photo.body(), photo.contentType(), photo.byteSize(),
				"profile-picture.jpg", "private, no-cache");
	}

	/** Body {@code {"status": "APPROVED" or "HIDDEN", "reason": required for HIDDEN}}. Returns the review's queue row. */
	@PutMapping("/api/v1/staff/reviews/{id}/status")
	public LiveExamReviewModerationService.StaffRow status(@PathVariable UUID id, @RequestBody JsonNode body) {
		requireModerate();
		return moderation.setStatus(actor(), id, body);
	}

	/** Body {@code {"featured": true or false}}. Only an approved review with a comment; otherwise 409. */
	@PutMapping("/api/v1/staff/reviews/{id}/featured")
	public LiveExamReviewModerationService.StaffRow featured(@PathVariable UUID id, @RequestBody JsonNode body) {
		requireModerate();
		return moderation.setFeatured(actor(), id, body);
	}

	private void requireRead() {
		if (!permissions.has(Permissions.LIVE_EXAM_REVIEW_MODERATE) && !permissions.has(Permissions.LIVE_EXAM_REVIEW_READ)) {
			throw denied();
		}
	}

	private void requireModerate() {
		if (!permissions.has(Permissions.LIVE_EXAM_REVIEW_MODERATE)) {
			throw denied();
		}
	}

	private static ApiException denied() {
		return new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
	}

	private static CurrentActor actor() {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor) {
			return actor;
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

}
