package com.coachingcenter.api.offline;

import static com.coachingcenter.api.offline.OfflineSupport.actorId;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

import tools.jackson.databind.JsonNode;

/**
 * The payment records page (payment-records-edit-plan.md). Who sees a kind is who records it today: course fee payments for the Master
 * Admin and the System Admins, direct enrolment payments for the Master Admin only. Instructor payments left this page with the
 * accounting ledger (R3). Finalizing and rejecting a
 * correction is the Master Admin's (P3), and the Master Admin's own correction is final at once.
 */
@RestController
public class PaymentRecordController {

	private final PaymentRecordService records;

	private final PermissionChecker permissions;

	public PaymentRecordController(PaymentRecordService records, PermissionChecker permissions) {
		this.records = records;
		this.permissions = permissions;
	}

	@GetMapping("/api/v1/payment-records")
	public List<PaymentRecordService.PaymentRow> list(@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to, @RequestParam(required = false) String kind,
			@RequestParam(required = false) UUID batchId, @RequestParam(required = false) String q,
			@RequestParam(required = false) String direction, @RequestParam(required = false) String item,
			@RequestParam(required = false) String method, @RequestParam(required = false) String status,
			@RequestParam(required = false) UUID categoryId) {
		Set<String> kinds = visibleKinds(true);
		return records.list(kinds, new PaymentRecordService.Filters(from, to, upper(kind), batchId, q, upper(direction),
				upper(item), upper(method), upper(status), categoryId));
	}

	private static String upper(String value) {
		return value == null || value.isBlank() ? null : value.trim().toUpperCase(java.util.Locale.ROOT);
	}

	@GetMapping("/api/v1/payment-records/edits")
	public List<PaymentRecordService.EditView> edits(@RequestParam(required = false) String status) {
		return records.edits(visibleKinds(false), status);
	}

	@PostMapping("/api/v1/payment-records/{kind}/{id}/edits")
	public PaymentRecordService.EditView propose(@PathVariable String kind, @PathVariable UUID id, @RequestBody JsonNode body) {
		Set<String> kinds = visibleKinds(false);
		return records.propose(actorId(), permissions.has(Permissions.OFFLINE_FEE_APPROVE), kinds, kind, id, body);
	}

	@PostMapping("/api/v1/payment-edits/{id}/finalize")
	public PaymentRecordService.EditView finalizeEdit(@PathVariable UUID id) {
		requireMaster();
		return records.finalizeEdit(actorId(), id);
	}

	@PostMapping("/api/v1/payment-edits/{id}/reject")
	public PaymentRecordService.EditView reject(@PathVariable UUID id, @RequestBody(required = false) JsonNode body) {
		requireMaster();
		String reason = body == null || !body.hasNonNull("reason") ? null : body.get("reason").asText();
		return records.rejectEdit(actorId(), id, reason);
	}

	/**
	 * The kinds of payment the caller records, and so may see and correct. With {@code listing}, also the ones the caller may only see
	 * (16 Nov): online purchases for whoever reads the orders, staff pay for whoever manages it. None at all is refused.
	 */
	private Set<String> visibleKinds(boolean listing) {
		Set<String> kinds = new HashSet<>();
		if (permissions.has(Permissions.OFFLINE_FEE_RECORD)) {
			kinds.add(PaymentRecordService.FEE);
		}
		// R3 (accounting): instructor payments are corrected in Accounting, not here.
		if (permissions.has(Permissions.LIVE_EXAM_INSTALLMENT)) {
			kinds.add(PaymentRecordService.DIRECT);
		}
		if (listing && permissions.has(Permissions.LIVE_EXAM_ORDER_READ)) {
			kinds.add(PaymentRecordService.ONLINE);
		}
		if (listing && permissions.has(Permissions.STAFF_PAY_MANAGE)) {
			kinds.add(PaymentRecordService.STAFF);
		}
		if (kinds.isEmpty()) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
		return kinds;
	}

	private void requireMaster() {
		if (!permissions.has(Permissions.OFFLINE_FEE_APPROVE)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
	}

}
