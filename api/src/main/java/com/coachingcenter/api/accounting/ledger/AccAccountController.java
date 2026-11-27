package com.coachingcenter.api.accounting.ledger;

import static com.coachingcenter.api.accounting.ledger.LedgerSupport.require;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.accounting.ledger.AccAccountService.AccountView;
import com.coachingcenter.api.accounting.ledger.AccAccountService.CreateAccount;
import com.coachingcenter.api.accounting.ledger.AccAccountService.IdOrder;
import com.coachingcenter.api.accounting.ledger.AccAccountService.UpdateAccount;
import com.coachingcenter.api.auth.permission.PermissionChecker;

/** Money accounts of the accounting section (contracts 3.1). Master Admin only: every route checks the ACCOUNTING permission. */
@RestController
@RequestMapping("/api/v1/accounting")
public class AccAccountController {

	private final AccAccountService accounts;

	private final PermissionChecker permissions;

	public AccAccountController(AccAccountService accounts, PermissionChecker permissions) {
		this.accounts = accounts;
		this.permissions = permissions;
	}

	@GetMapping("/accounts")
	public List<AccountView> list(@RequestParam(required = false, defaultValue = "false") boolean archived) {
		require(permissions);
		return accounts.list(archived);
	}

	@PostMapping("/accounts")
	@ResponseStatus(HttpStatus.CREATED)
	public AccountView create(@RequestBody CreateAccount body) {
		require(permissions);
		return accounts.create(body);
	}

	@PatchMapping("/accounts/{id}")
	public AccountView update(@PathVariable UUID id, @RequestBody UpdateAccount body) {
		require(permissions);
		return accounts.update(id, body);
	}

	@PostMapping("/accounts/{id}/archive")
	public AccountView archive(@PathVariable UUID id) {
		require(permissions);
		return accounts.archive(id);
	}

	@PostMapping("/accounts/{id}/restore")
	public AccountView restore(@PathVariable UUID id) {
		require(permissions);
		return accounts.restore(id);
	}

	@PostMapping("/accounts/order")
	public List<AccountView> order(@RequestBody IdOrder body) {
		require(permissions);
		return accounts.order(body == null ? null : body.ids());
	}

}
