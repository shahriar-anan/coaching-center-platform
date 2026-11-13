package com.coachingcenter.api.offline;

import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.offline.OfflineFeeLedger.Payment;
import com.coachingcenter.api.offline.OfflineFeeService.Receipt;
import com.coachingcenter.api.offline.OfflineFeeService.RecordRequest;
import com.coachingcenter.api.offline.OfflineFeeService.ReasonRequest;
import com.coachingcenter.api.offline.OfflineFeeService.Schedule;

/**
 * The staff routes of the monthly fee (plan section 5): record a payment, approve, reject and void, the approval queue, an
 * admission's schedule, and receipts. Each route checks its permission (section 4.6) before it reads or changes anything.
 */
@RestController
public class OfflineFeeController {

	private final OfflineFeeService fees;

	private final OfflineFeeReceiptPdf pdf;

	private final PermissionChecker permissions;

	public OfflineFeeController(OfflineFeeService fees, OfflineFeeReceiptPdf pdf, PermissionChecker permissions) {
		this.fees = fees;
		this.pdf = pdf;
		this.permissions = permissions;
	}

	/** The body of a payment: amount, method, reference, the paid-on date (yyyy-MM-dd) and an optional note. */
	public record RecordPaymentBody(BigDecimal amount, String method, String reference, LocalDate paidOn, String note) {
	}

	/** A rejection or a void: the reason, required. */
	public record ReasonBody(String reason) {
	}

	@PostMapping("/api/v1/offline-admissions/{id}/payments")
	public ResponseEntity<Payment> record(@PathVariable UUID id, @RequestBody RecordPaymentBody body) {
		require(Permissions.OFFLINE_FEE_RECORD);
		Payment payment = fees.record(actor(), id,
				new RecordRequest(body.amount(), body.method(), body.reference(), body.paidOn(), body.note()));
		return ResponseEntity.status(HttpStatus.CREATED).body(payment);
	}

	@PostMapping("/api/v1/offline-fee-payments/{id}/approve")
	public Payment approve(@PathVariable UUID id) {
		require(Permissions.OFFLINE_FEE_APPROVE);
		return fees.approve(actor(), id);
	}

	@PostMapping("/api/v1/offline-fee-payments/{id}/reject")
	public Payment reject(@PathVariable UUID id, @RequestBody ReasonBody body) {
		require(Permissions.OFFLINE_FEE_APPROVE);
		return fees.reject(actor(), id, body.reason());
	}

	@PostMapping("/api/v1/offline-fee-payments/{id}/void")
	public Payment voidPayment(@PathVariable UUID id, @RequestBody ReasonBody body) {
		require(Permissions.OFFLINE_FEE_APPROVE);
		return fees.voidPayment(actor(), id, body.reason());
	}

	/** The approval queue. The Master Admin sees every payment; staff see only the payments they recorded. */
	@GetMapping("/api/v1/offline-fee-payments")
	public List<Payment> queue(@RequestParam(required = false) String status) {
		return fees.queue(actor(), status);
	}

	@GetMapping("/api/v1/offline-admissions/{id}/fees")
	public Schedule schedule(@PathVariable UUID id) {
		require(Permissions.OFFLINE_ADMISSION_READ);
		return fees.schedule(id);
	}

	@GetMapping(value = "/api/v1/offline-fee-payments/{id}/receipt.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
	public ResponseEntity<byte[]> receipt(@PathVariable UUID id) {
		require(Permissions.OFFLINE_ADMISSION_READ);
		Receipt receipt = fees.receipt(id, null);
		return pdfResponse(receipt.payment().receiptNo(), pdf.render(receipt));
	}

	/** The PDF of a receipt, shown inline with the receipt number as its file name. */
	static ResponseEntity<byte[]> pdfResponse(String receiptNo, byte[] bytes) {
		return ResponseEntity.ok()
			.contentType(MediaType.APPLICATION_PDF)
			.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + receiptNo + ".pdf\"")
			.body(bytes);
	}

	private void require(String permission) {
		if (!permissions.has(permission)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
	}

	private static CurrentActor actor() {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor) {
			return actor;
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

}
