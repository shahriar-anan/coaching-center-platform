package com.coachingcenter.api.exams;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.web.PageResponse;

/** Who is enrolled in a package or an exam, for the Master Admin. Uses the same permission as granting access. */
@RestController
public class LiveExamEnrolmentListController {

	private final LiveExamEnrolmentListService enrolments;

	private final PermissionChecker permissions;

	public LiveExamEnrolmentListController(LiveExamEnrolmentListService enrolments, PermissionChecker permissions) {
		this.enrolments = enrolments;
		this.permissions = permissions;
	}

	@GetMapping("/api/v1/live-exam-modules/{moduleId}/enrolments")
	public PageResponse<LiveExamEnrolmentListService.EnrolledStudent> forModule(@PathVariable UUID moduleId,
			@PageableDefault(size = 20) Pageable pageable) {
		require();
		return PageResponse.from(enrolments.forModule(moduleId, pageable));
	}

	@GetMapping("/api/v1/live-exams/{examId}/enrolments")
	public PageResponse<LiveExamEnrolmentListService.EnrolledStudent> forExam(@PathVariable UUID examId,
			@PageableDefault(size = 20) Pageable pageable) {
		require();
		return PageResponse.from(enrolments.forExam(examId, pageable));
	}

	private void require() {
		if (!permissions.has(Permissions.LIVE_EXAM_GRANT)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
	}

}
