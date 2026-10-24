package com.coachingcenter.api.exams;

import java.time.Instant;
import java.time.format.DateTimeParseException;
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
public class LiveExamParticipationController {

	private final LiveExamParticipationService participation;

	private final PermissionChecker permissions;

	public LiveExamParticipationController(LiveExamParticipationService participation, PermissionChecker permissions) {
		this.participation = participation;
		this.permissions = permissions;
	}

	@GetMapping("/api/v1/live-exam-participation")
	public PageResponse<LiveExamParticipationService.ParticipationRow> list(@RequestParam(required = false) UUID moduleId,
			@RequestParam(required = false) String type, @RequestParam(required = false) String from,
			@RequestParam(required = false) String to, @RequestParam(required = false) String q,
			@RequestParam(required = false) String sort, @PageableDefault(size = 20) Pageable pageable) {
		requireReader();
		LiveExamParticipationService.Filter filter = new LiveExamParticipationService.Filter(moduleId, type,
				instant(from), instant(to), q, sort);
		return PageResponse.from(participation.list(actor(), filter, pageable));
	}

	@GetMapping("/api/v1/live-exam-participation/summary")
	public LiveExamParticipationService.Summary summary() {
		requireReader();
		return participation.summary(actor());
	}

	private void requireReader() {
		if (!permissions.has(Permissions.LIVE_EXAM_EVALUATE) && !permissions.has(Permissions.EXAM_READ)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
	}

	private static Instant instant(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return java.time.OffsetDateTime.parse(value.trim()).toInstant();
		}
		catch (DateTimeParseException exception) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Date is invalid.");
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
