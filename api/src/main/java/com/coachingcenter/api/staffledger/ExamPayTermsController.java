package com.coachingcenter.api.staffledger;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

import tools.jackson.databind.JsonNode;

/**
 * Pay per written exam (staff pay plan, phase 8, decision P18). The Master Admin sets the rate (O1). The assigned instructor
 * reads the exam's terms, and reads their own written exams with the rate each sheet earns at.
 */
@RestController
public class ExamPayTermsController {

	private final ExamPayTermsService terms;

	private final PermissionChecker permissions;

	public ExamPayTermsController(ExamPayTermsService terms, PermissionChecker permissions) {
		this.terms = terms;
		this.permissions = permissions;
	}

	/** The Master Admin, or the instructor the exam is assigned to (read only). Anyone else is refused. */
	@GetMapping("/api/v1/live-exams/{id}/pay-terms")
	public ExamPayTermsService.PayTerms payTerms(@PathVariable UUID id) {
		return terms.view(id, actor(), permissions.has(Permissions.STAFF_PAY_MANAGE));
	}

	/** Sets or clears the exam's rate per sheet. Takes effect for sheets published from now on. */
	@PutMapping("/api/v1/live-exams/{id}/pay-terms")
	public ExamPayTermsService.PayTerms setPayTerms(@PathVariable UUID id, @RequestBody JsonNode body) {
		require(Permissions.STAFF_PAY_MANAGE);
		return terms.setRate(id, body, actor());
	}

	/** The signed-in instructor's assigned written exams. */
	@GetMapping("/api/v1/me/pay/exams")
	public List<ExamPayTermsService.MyExam> myExams() {
		require(Permissions.STAFF_PAY_SELF);
		return terms.myExams(actor());
	}

	private void require(String permission) {
		if (!permissions.has(permission)) {
			throw denied();
		}
	}

	private static ApiException denied() {
		return new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
	}

	private static UUID actor() {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor) {
			return actor.userId();
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

}
