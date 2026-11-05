package com.coachingcenter.api.staffpay;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.staffpay.PayoutAccountService.PayoutAccountRequest;
import com.coachingcenter.api.staffpay.PayoutAccountService.PayoutAccountView;

/**
 * The signed-in person's own payout accounts (staff pay plan, phase 2). Every route acts on the caller's id and needs
 * STAFF_PAY_SELF, which the System Admin and the instructor hold. There is no route that takes another person's id.
 */
@RestController
public class PayoutAccountController {

	private final PayoutAccountService accounts;

	private final PermissionChecker permissions;

	public PayoutAccountController(PayoutAccountService accounts, PermissionChecker permissions) {
		this.accounts = accounts;
		this.permissions = permissions;
	}

	/** The caller's live accounts, preferred first, with full numbers (the owner's edit form shows them). */
	@GetMapping("/api/v1/me/payout-accounts")
	public List<PayoutAccountView> list() {
		return accounts.list(me());
	}

	@PostMapping("/api/v1/me/payout-accounts")
	public ResponseEntity<PayoutAccountView> add(@RequestBody PayoutAccountRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(accounts.add(me(), request));
	}

	@PutMapping("/api/v1/me/payout-accounts/{id}")
	public PayoutAccountView update(@PathVariable UUID id, @RequestBody PayoutAccountRequest request) {
		return accounts.update(me(), id, request);
	}

	@DeleteMapping("/api/v1/me/payout-accounts/{id}")
	public ResponseEntity<Void> remove(@PathVariable UUID id) {
		accounts.remove(me(), id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/api/v1/me/payout-accounts/{id}/preferred")
	public PayoutAccountView makePreferred(@PathVariable UUID id) {
		return accounts.makePreferred(me(), id);
	}

	/** The signed-in person, when they hold STAFF_PAY_SELF. Anyone else is refused. */
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
