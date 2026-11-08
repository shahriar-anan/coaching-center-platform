package com.coachingcenter.api.exams;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

/**
 * The student's own payment plans and receipts (plan section 6). Each route answers 404 for a row that is not the caller's, so
 * another student's plan or receipt cannot be told apart from one that does not exist.
 */
@RestController
public class StudentInstallmentController {

	private final InstallmentPlanReader reader;

	private final LiveExamReceiptPdf receiptPdf;

	private final PermissionChecker permissions;

	public StudentInstallmentController(InstallmentPlanReader reader, LiveExamReceiptPdf receiptPdf,
			PermissionChecker permissions) {
		this.reader = reader;
		this.receiptPdf = receiptPdf;
		this.permissions = permissions;
	}

	/** The schedule and payments of one of the student's own plans. No staff notes and no contact details. */
	@GetMapping("/api/v1/me/installment-plans/{id}")
	public InstallmentPlanReader.PlanDetail plan(@PathVariable UUID id) {
		UUID studentId = student();
		return reader.detail(id, studentId).map(InstallmentPlanReader.PlanDetail::forStudent)
			.orElseThrow(InstallmentPlanController::notFound);
	}

	/** The PDF of one of the student's own payments. A voided payment is stamped VOID. */
	@GetMapping("/api/v1/me/installment-payments/{id}/receipt.pdf")
	public ResponseEntity<byte[]> receipt(@PathVariable UUID id) {
		UUID studentId = student();
		InstallmentPlanReader.PaymentReceipt receipt = reader.receipt(id, studentId)
			.orElseThrow(InstallmentPlanController::notFound);
		return InstallmentPlanController.pdf(receiptPdf, receipt);
	}

	private UUID student() {
		if (!permissions.has(Permissions.LIVE_EXAM_BROWSE)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor) {
			if (actor.role() != Role.STUDENT) {
				throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
			}
			return actor.userId();
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

}
