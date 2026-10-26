package com.coachingcenter.api.exams;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
public class LiveExamAnswerReleaseController {

	private final LiveExamAnswerReleaseService releases;

	private final PermissionChecker permissions;

	public LiveExamAnswerReleaseController(LiveExamAnswerReleaseService releases, PermissionChecker permissions) {
		this.releases = releases;
		this.permissions = permissions;
	}

	/** Master Admin and System Admin release or withhold the MCQ correct answers of one exam (R-4). */
	@PutMapping("/api/v1/live-exams/{id}/answers-released")
	public LiveExamAnswerReleaseService.ReleaseView setReleased(@PathVariable UUID id,
			@Valid @RequestBody ReleaseRequest request) {
		if (!permissions.has(Permissions.LIVE_EXAM_RELEASE)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
		return releases.setReleased(id, actor().userId(), request.released());
	}

	private static CurrentActor actor() {
		var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor) {
			return actor;
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

	public record ReleaseRequest(@NotNull Boolean released) {
	}

}
