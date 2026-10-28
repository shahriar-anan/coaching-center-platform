package com.coachingcenter.api.exams;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
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
public class StaffExamListController {

	private final StaffExamListService lists;

	private final PermissionChecker permissions;

	public StaffExamListController(StaffExamListService lists, PermissionChecker permissions) {
		this.lists = lists;
		this.permissions = permissions;
	}

	/**
	 * Staff see every exam; an instructor sees only the exams assigned to them. {@code sort} accepts {@code liveAt},
	 * {@code title} and {@code updatedAt}.
	 */
	@GetMapping("/api/v1/live-exams")
	public PageResponse<StaffExamListService.StaffExamRow> list(
			@RequestParam(required = false) UUID packageId,
			@RequestParam(required = false) UUID categoryId,
			@RequestParam(required = false, defaultValue = "false") boolean standalone,
			@RequestParam(required = false) String type,
			@RequestParam(required = false) String mode,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String phase,
			@RequestParam(required = false) String q,
			@RequestParam(required = false, defaultValue = "false") boolean pending,
			@RequestParam(required = false, defaultValue = "false") boolean popular,
			@PageableDefault(size = 20) Pageable pageable) {
		UUID instructor = null;
		if (!permissions.has(Permissions.EXAM_READ)) {
			if (!permissions.has(Permissions.LIVE_EXAM_EVALUATE)) {
				throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
			}
			instructor = actorId();
		}
		StaffExamListService.ListQuery query = new StaffExamListService.ListQuery(packageId, categoryId, standalone,
				LiveExamTypeFilter.parse(type), LiveExamTypeFilter.parseMode(mode), status,
				LiveExamTypeFilter.parsePhase(phase), q, pending, popular);
		return PageResponse.from(lists.list(query, instructor, pageable));
	}

	private static UUID actorId() {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor) {
			return actor.userId();
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

}
