package com.coachingcenter.api.offline;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.offline.OfflineFeeService.Receipt;
import com.coachingcenter.api.offline.OfflineFeeService.StudentSchedule;

/**
 * The student's own fee routes (plan section 5): the schedule and the approved payments of the signed-in student's admission, and
 * the receipt of one of their approved payments. A pending or voided payment never reaches the student.
 */
@RestController
public class OfflineFeeStudentController {

	private final OfflineFeeService fees;

	private final OfflineFeeReceiptPdf pdf;

	public OfflineFeeStudentController(OfflineFeeService fees, OfflineFeeReceiptPdf pdf) {
		this.fees = fees;
		this.pdf = pdf;
	}

	@GetMapping("/api/v1/me/admission/fees")
	public StudentSchedule schedule() {
		return fees.ownSchedule(studentId());
	}

	@GetMapping(value = "/api/v1/me/admission/payments/{id}/receipt.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
	public ResponseEntity<byte[]> receipt(@PathVariable UUID id) {
		Receipt receipt = fees.receipt(id, studentId());
		return OfflineFeeController.pdfResponse(receipt.payment().receiptNo(), pdf.render(receipt));
	}

	private static UUID studentId() {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor) {
			return actor.userId();
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

}
