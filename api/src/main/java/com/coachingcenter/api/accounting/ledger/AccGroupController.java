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

import com.coachingcenter.api.accounting.ledger.AccGroupService.CreateGroup;
import com.coachingcenter.api.accounting.ledger.AccGroupService.GroupView;
import com.coachingcenter.api.accounting.ledger.AccGroupService.IdOrder;
import com.coachingcenter.api.accounting.ledger.AccGroupService.UpdateGroup;
import com.coachingcenter.api.auth.permission.PermissionChecker;

/** Groups of the accounting section (contracts 3.1). Master Admin only: every route checks the ACCOUNTING permission. */
@RestController
@RequestMapping("/api/v1/accounting")
public class AccGroupController {

	private final AccGroupService groups;

	private final PermissionChecker permissions;

	public AccGroupController(AccGroupService groups, PermissionChecker permissions) {
		this.groups = groups;
		this.permissions = permissions;
	}

	@GetMapping("/groups")
	public List<GroupView> list(@RequestParam(required = false, defaultValue = "false") boolean archived) {
		require(permissions);
		return groups.list(archived);
	}

	@PostMapping("/groups")
	@ResponseStatus(HttpStatus.CREATED)
	public GroupView create(@RequestBody CreateGroup body) {
		require(permissions);
		return groups.create(body);
	}

	@PatchMapping("/groups/{id}")
	public GroupView update(@PathVariable UUID id, @RequestBody UpdateGroup body) {
		require(permissions);
		return groups.update(id, body);
	}

	@PostMapping("/groups/{id}/archive")
	public GroupView archive(@PathVariable UUID id) {
		require(permissions);
		return groups.archive(id);
	}

	@PostMapping("/groups/{id}/restore")
	public GroupView restore(@PathVariable UUID id) {
		require(permissions);
		return groups.restore(id);
	}

	@PostMapping("/groups/order")
	public List<GroupView> order(@RequestBody IdOrder body) {
		require(permissions);
		return groups.order(body == null ? null : body.ids());
	}

}
