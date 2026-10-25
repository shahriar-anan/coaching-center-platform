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

@RestController
public class StudentLiveExamAccessController {

	private final StudentLiveExamAccessService access;

	private final PermissionChecker permissions;

	public StudentLiveExamAccessController(StudentLiveExamAccessService access, PermissionChecker permissions) {
		this.access = access;
		this.permissions = permissions;
	}

	@GetMapping("/api/v1/students/{id}/live-exam-access")
	public PageResponse<StudentLiveExamAccessService.AccessRow> list(@PathVariable UUID id,
			@PageableDefault(size = 20) Pageable pageable) {
		if (!permissions.has(Permissions.STUDENT_READ)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
		return PageResponse.from(access.list(id, pageable));
	}

}
