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

import com.coachingcenter.api.accounting.ledger.AccGroupService.IdOrder;
import com.coachingcenter.api.accounting.ledger.AccTypeService.CreateType;
import com.coachingcenter.api.accounting.ledger.AccTypeService.TypeView;
import com.coachingcenter.api.accounting.ledger.AccTypeService.UpdateType;
import com.coachingcenter.api.auth.permission.PermissionChecker;

/** Types of the accounting section (contracts 3.1). Master Admin only: every route checks the ACCOUNTING permission. */
@RestController
@RequestMapping("/api/v1/accounting")
public class AccTypeController {

	private final AccTypeService types;

	private final PermissionChecker permissions;

	public AccTypeController(AccTypeService types, PermissionChecker permissions) {
		this.types = types;
		this.permissions = permissions;
	}

	@GetMapping("/groups/{id}/types")
	public List<TypeView> list(@PathVariable UUID id, @RequestParam(required = false, defaultValue = "false") boolean archived) {
		require(permissions);
		return types.list(id, archived);
	}

	@PostMapping("/groups/{id}/types")
	@ResponseStatus(HttpStatus.CREATED)
	public TypeView create(@PathVariable UUID id, @RequestBody CreateType body) {
		require(permissions);
		return types.create(id, body);
	}

	@PostMapping("/groups/{id}/types/order")
	public List<TypeView> order(@PathVariable UUID id, @RequestBody IdOrder body) {
		require(permissions);
		return types.order(id, body == null ? null : body.ids());
	}

	@PatchMapping("/types/{id}")
	public TypeView update(@PathVariable UUID id, @RequestBody UpdateType body) {
		require(permissions);
		return types.update(id, body);
	}

	@PostMapping("/types/{id}/archive")
	public TypeView archive(@PathVariable UUID id) {
		require(permissions);
		return types.archive(id);
	}

	@PostMapping("/types/{id}/restore")
	public TypeView restore(@PathVariable UUID id) {
		require(permissions);
		return types.restore(id);
	}

}
