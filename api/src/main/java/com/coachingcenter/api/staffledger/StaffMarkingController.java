package com.coachingcenter.api.staffledger;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

/**
 * Instructor marking pay (staff pay plan, section 5, phase 4): the instructor's own figures and the Master Admin's figures for
 * one instructor. Both are read only.
 */
@RestController
public class StaffMarkingController {

	private final StaffMarkingService marking;

	private final PermissionChecker permissions;

	public StaffMarkingController(StaffMarkingService marking, PermissionChecker permissions) {
		this.marking = marking;
		this.permissions = permissions;
	}

	/** The signed-in instructor's marking: totals and each exam. A System Admin or the Master Admin is refused. */
	@GetMapping("/api/v1/me/pay/marking")
	public StaffMarkingService.MarkingView myMarking() {
		if (!permissions.has(Permissions.STAFF_PAY_SELF)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
		return marking.self(me());
	}

	/** One instructor's marking, for the Master Admin's person page. */
	@GetMapping("/api/v1/staff-pay/people/{userId}/marking")
	public StaffMarkingService.MarkingView personMarking(@PathVariable UUID userId) {
		return marking.person(userId);
	}

	private UUID me() {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor) {
			return actor.userId();
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

}
