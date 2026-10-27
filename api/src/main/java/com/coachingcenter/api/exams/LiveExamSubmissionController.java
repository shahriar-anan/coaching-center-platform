package com.coachingcenter.api.exams;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.web.PageResponse;

@RestController
public class LiveExamSubmissionController {

	private final LiveExamSubmissionFeedService feed;

	private final PermissionChecker permissions;

	public LiveExamSubmissionController(LiveExamSubmissionFeedService feed, PermissionChecker permissions) {
		this.feed = feed;
		this.permissions = permissions;
	}

	/** {@code sort} is {@code submittedAt,desc} (default), {@code submittedAt,asc} or {@code studentName}. */
	@GetMapping("/api/v1/live-exam-submissions")
	public PageResponse<LiveExamSubmissionFeedService.FeedRow> list(@RequestParam(required = false) UUID moduleId,
			@RequestParam(required = false) UUID examId, @RequestParam(required = false) String status,
			@RequestParam(required = false) String q, @RequestParam(required = false) String sort,
			@RequestParam(value = "ready", defaultValue = "false") boolean ready,
			@PageableDefault(size = 20) Pageable pageable) {
		if (!permissions.has(Permissions.LIVE_EXAM_EVALUATE) && !permissions.has(Permissions.EXAM_READ)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
		return PageResponse.from(feed.list(actor(), moduleId, examId, status, q, sort, ready, pageable));
	}

	private static CurrentActor actor() {
		var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor) {
			return actor;
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

}
