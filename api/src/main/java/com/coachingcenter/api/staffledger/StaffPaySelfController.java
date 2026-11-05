package com.coachingcenter.api.staffledger;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.web.PageResponse;

/**
 * The signed-in System Admin's or instructor's own pay (staff pay plan, section 5, phase 3). Every route acts on the signed-in
 * person only and needs STAFF_PAY_SELF. The Master Admin, who is not paid through this, is refused.
 */
@RestController
public class StaffPaySelfController {

	private final StaffLedgerService ledger;

	private final PermissionChecker permissions;

	public StaffPaySelfController(StaffLedgerService ledger, PermissionChecker permissions) {
		this.ledger = ledger;
		this.permissions = permissions;
	}

	@GetMapping("/api/v1/me/pay/summary")
	public StaffLedgerService.SelfSummary summary() {
		return ledger.selfSummary(me());
	}

	/** The signed-in System Admin's salary months, with the next pay date and the amount due on it (phase 5). */
	@GetMapping("/api/v1/me/pay/salary")
	public StaffSalaryService.SalaryView salary() {
		return ledger.selfSalary(me());
	}

	@GetMapping("/api/v1/me/pay/ledger")
	public PageResponse<StaffLedgerService.LedgerRow> ledger(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return ledger.selfLedger(me(), page, size);
	}

	/** The signed-in person. A caller without STAFF_PAY_SELF is refused. */
	private UUID me() {
		if (!permissions.has(Permissions.STAFF_PAY_SELF)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor) {
			return actor.userId();
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

}
