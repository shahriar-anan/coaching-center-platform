package com.coachingcenter.api.exams;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.web.PageResponse;

import jakarta.validation.Valid;

/**
 * The Master Admin's Installments pages (plan section 6). Every route checks {@code LIVE_EXAM_INSTALLMENT} itself, since
 * {@code SecurityConfig} only requires a signed-in user.
 */
@RestController
public class InstallmentPlanController {

	private final InstallmentPlanReader reader;

	private final InstallmentPlanService plans;

	private final LiveExamReceiptPdf receiptPdf;

	private final PermissionChecker permissions;

	public InstallmentPlanController(InstallmentPlanReader reader, InstallmentPlanService plans, LiveExamReceiptPdf receiptPdf,
			PermissionChecker permissions) {
		this.reader = reader;
		this.plans = plans;
		this.receiptPdf = receiptPdf;
		this.permissions = permissions;
	}

	/** The plans, most overdue first. The ID filters serve the links from other screens (D18). */
	@GetMapping("/api/v1/installment-plans")
	public PageResponse<InstallmentPlanReader.PlanRow> list(@RequestParam(required = false) String status,
			@RequestParam(required = false) String q, @RequestParam(required = false) UUID studentId,
			@RequestParam(required = false) UUID examId, @RequestParam(required = false) UUID moduleId,
			@RequestParam(defaultValue = "0") int page) {
		require();
		return reader.list(status, q, studentId, examId, moduleId, page);
	}

	/** Total due, overdue count and due-soon count for the summary band. */
	@GetMapping("/api/v1/installment-plans/summary")
	public InstallmentPlanReader.Summary summary() {
		require();
		return reader.summary();
	}

	/** One plan with its schedule, each row's state, and every payment. */
	@GetMapping("/api/v1/installment-plans/{id}")
	public InstallmentPlanReader.PlanDetail detail(@PathVariable UUID id) {
		require();
		return reader.detail(id, null).orElseThrow(InstallmentPlanController::notFound);
	}

	/** Records a payment on the plan. Access is not changed. */
	@PostMapping("/api/v1/installment-plans/{id}/payments")
	public ResponseEntity<InstallmentPlanReader.PaymentView> recordPayment(@PathVariable UUID id,
			@Valid @RequestBody InstallmentPlanService.RecordPaymentRequest request) {
		require();
		return ResponseEntity.status(HttpStatus.CREATED).body(plans.recordPayment(actor(), id, request));
	}

	/** Replaces the unpaid rows. The request is the whole schedule; the paid rows must come back unchanged. */
	@PutMapping("/api/v1/installment-plans/{id}/schedule")
	public InstallmentPlanReader.PlanDetail changeSchedule(@PathVariable UUID id,
			@Valid @RequestBody InstallmentPlanService.ScheduleChangeRequest request) {
		require();
		return plans.changeSchedule(actor(), id, request);
	}

	/** Gives back access that was revoked on this plan, with its original access period. */
	@PostMapping("/api/v1/installment-plans/{id}/restore-access")
	public LiveExamGrantAdminService.GrantView restoreAccess(@PathVariable UUID id) {
		require();
		return plans.restoreAccess(actor(), id);
	}

	/** Voids a payment. Only the latest live payment on its plan can be voided, and the reason is required. */
	@PostMapping("/api/v1/installment-payments/{id}/void")
	public InstallmentPlanReader.PaymentView voidPayment(@PathVariable UUID id,
			@RequestBody(required = false) InstallmentPlanService.VoidRequest request) {
		require();
		return plans.voidPayment(actor(), id, request == null ? null : request.reason());
	}

	/** The staff copy of one payment's receipt. */
	@GetMapping("/api/v1/installment-payments/{id}/receipt.pdf")
	public ResponseEntity<byte[]> receipt(@PathVariable UUID id) {
		require();
		InstallmentPlanReader.PaymentReceipt receipt = reader.receipt(id, null).orElseThrow(InstallmentPlanController::notFound);
		return pdf(receiptPdf, receipt);
	}

	/** The PDF response for a receipt, as an attachment that is never cached. Shared with the student's receipt route. */
	static ResponseEntity<byte[]> pdf(LiveExamReceiptPdf receiptPdf, InstallmentPlanReader.PaymentReceipt receipt) {
		return ResponseEntity.ok()
			.cacheControl(CacheControl.noStore())
			.contentType(MediaType.APPLICATION_PDF)
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename(receiptFileName(receipt), StandardCharsets.UTF_8).build().toString())
			.body(receiptPdf.renderDirect(receipt));
	}

	/** The download name of a receipt: "Receipt - <what was bought> - <paid date>.pdf", with the date in Dhaka time. */
	static String receiptFileName(InstallmentPlanReader.PaymentReceipt receipt) {
		String type = receipt.exam() ? "Exam" : "Package";
		String what = receipt.itemTitleEn() == null ? "" : receipt.itemTitleEn().replaceAll("[^A-Za-z0-9 ._()-]", " ")
			.replaceAll(" +", " ").trim();
		return "Receipt - " + (what.isEmpty() ? type : what) + " - " + receipt.paidOn() + ".pdf";
	}

	private void require() {
		if (!permissions.has(Permissions.LIVE_EXAM_INSTALLMENT)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
	}

	static ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	private static CurrentActor actor() {
		var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor) {
			return actor;
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

}
