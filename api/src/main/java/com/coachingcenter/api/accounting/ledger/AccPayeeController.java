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

import com.coachingcenter.api.accounting.ledger.AccPayeeService.CreatePayee;
import com.coachingcenter.api.accounting.ledger.AccPayeeService.LastPrice;
import com.coachingcenter.api.accounting.ledger.AccPayeeService.LinkableUser;
import com.coachingcenter.api.accounting.ledger.AccPayeeService.Payee;
import com.coachingcenter.api.accounting.ledger.AccPayeeService.PayeeRow;
import com.coachingcenter.api.accounting.ledger.AccPayeeService.UpdatePayee;
import com.coachingcenter.api.auth.permission.PermissionChecker;

/** Payees of the accounting section (contracts 3.1). Master Admin only: every route checks the ACCOUNTING permission. */
@RestController
@RequestMapping("/api/v1/accounting")
public class AccPayeeController {

	private final AccPayeeService payees;

	private final PermissionChecker permissions;

	public AccPayeeController(AccPayeeService payees, PermissionChecker permissions) {
		this.payees = payees;
		this.permissions = permissions;
	}

	@GetMapping("/groups/{id}/payees")
	public List<PayeeRow> listForGroup(@PathVariable UUID id, @RequestParam(required = false) String q,
			@RequestParam(required = false, defaultValue = "false") boolean archived) {
		require(permissions);
		return payees.listForGroup(id, q, archived);
	}

	@GetMapping("/payees")
	public List<PayeeRow> search(@RequestParam(required = false) String q,
			@RequestParam(required = false, defaultValue = "20") int limit) {
		require(permissions);
		return payees.search(q, limit);
	}

	@PostMapping("/groups/{id}/payees")
	@ResponseStatus(HttpStatus.CREATED)
	public Payee create(@PathVariable UUID id, @RequestBody CreatePayee body) {
		require(permissions);
		return payees.create(id, body);
	}

	@GetMapping("/payees/{id}")
	public Payee get(@PathVariable UUID id) {
		require(permissions);
		return payees.get(id);
	}

	@PatchMapping("/payees/{id}")
	public Payee update(@PathVariable UUID id, @RequestBody UpdatePayee body) {
		require(permissions);
		return payees.update(id, body);
	}

	@PostMapping("/payees/{id}/archive")
	public Payee archive(@PathVariable UUID id) {
		require(permissions);
		return payees.archive(id);
	}

	@PostMapping("/payees/{id}/restore")
	public Payee restore(@PathVariable UUID id) {
		require(permissions);
		return payees.restore(id);
	}

	@GetMapping("/payees/{id}/last-prices")
	public List<LastPrice> lastPrices(@PathVariable UUID id) {
		require(permissions);
		return payees.lastPrices(id);
	}

	@GetMapping("/users/linkable")
	public List<LinkableUser> linkable(@RequestParam(required = false) String q) {
		require(permissions);
		return payees.linkable(q);
	}

}
