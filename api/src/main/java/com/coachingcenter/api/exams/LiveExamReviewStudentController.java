package com.coachingcenter.api.exams;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

import tools.jackson.databind.JsonNode;

/** The signed-in student's own reviews (reviews plan, section 6). Students only. */
@RestController
public class LiveExamReviewStudentController {

	private final LiveExamReviewService reviews;

	private final PermissionChecker permissions;

	public LiveExamReviewStudentController(LiveExamReviewService reviews, PermissionChecker permissions) {
		this.reviews = reviews;
		this.permissions = permissions;
	}

	/** {@code kind} is {@code exam} or {@code package}; {@code id} is the exam or package id. Always 200 for a target that exists. */
	@GetMapping("/api/v1/me/reviews/{kind}/{id}")
	public LiveExamReviewService.Mine mine(@PathVariable String kind, @PathVariable UUID id) {
		requireStudent();
		return reviews.mine(actorId(), kind, id);
	}

	/** Body {@code {"rating": 1..5, "comment": optional}}. Creates or replaces the review. */
	@PutMapping("/api/v1/me/reviews/{kind}/{id}")
	public LiveExamReviewService.MyReview save(@PathVariable String kind, @PathVariable UUID id,
			@RequestBody JsonNode body) {
		requireStudent();
		try {
			return reviews.save(actorId(), kind, id, body);
		}
		catch (DataIntegrityViolationException raced) {
			// Two first saves from the same student at once (a double tap on the stars): the other one won the unique
			// index. This is a new transaction, so it finds that row and replaces it.
			return reviews.save(actorId(), kind, id, body);
		}
	}

	@DeleteMapping("/api/v1/me/reviews/{kind}/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable String kind, @PathVariable UUID id) {
		requireStudent();
		reviews.delete(actorId(), kind, id);
	}

	/** Every review the student has, newest first. Used to hide "Rate" on rows already rated. */
	@GetMapping("/api/v1/me/reviews")
	public List<LiveExamReviewService.MyReviewRow> all() {
		requireStudent();
		return reviews.all(actorId());
	}

	private void requireStudent() {
		if (!permissions.has(Permissions.LIVE_EXAM_REVIEW_WRITE)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
	}

	private static UUID actorId() {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor) {
			return actor.userId();
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

}
