package com.coachingcenter.api.exams;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

@RestController
public class LiveExamResultsController {

	private final LiveExamResultsService results;

	private final PermissionChecker permissions;

	public LiveExamResultsController(LiveExamResultsService results, PermissionChecker permissions) {
		this.results = results;
		this.permissions = permissions;
	}

	/** {@code sort}: {@code submittedAt,desc} (default), {@code submittedAt,asc}, {@code score,desc}, {@code score,asc}, {@code studentName}. */
	@GetMapping("/api/v1/live-exams/{id}/results")
	public LiveExamResultsService.ResultsReport report(@PathVariable UUID id, @RequestParam(required = false) String kind,
			@RequestParam(required = false) String status, @RequestParam(required = false) String q,
			@RequestParam(required = false) String sort, @PageableDefault(size = 20) Pageable pageable) {
		if (!permissions.has(Permissions.EXAM_READ) && !permissions.has(Permissions.LIVE_EXAM_EVALUATE)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
		return results.report(id, actor(), kind, status, q, sort, pageable);
	}

	private static CurrentActor actor() {
		var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor) {
			return actor;
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

}
