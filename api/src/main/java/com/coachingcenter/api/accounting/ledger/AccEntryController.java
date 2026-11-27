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

import com.coachingcenter.api.accounting.ledger.AccEntryService.CopyDraft;
import com.coachingcenter.api.accounting.ledger.AccEntryService.Entry;
import com.coachingcenter.api.accounting.ledger.AccEntryService.EntryDetail;
import com.coachingcenter.api.accounting.ledger.AccEntryService.EntryInput;
import com.coachingcenter.api.accounting.ledger.AccEntryService.EntryPatch;
import com.coachingcenter.api.accounting.ledger.AccEntryService.ManyInput;
import com.coachingcenter.api.accounting.ledger.AccEntryService.VoidRequest;
import com.coachingcenter.api.auth.permission.PermissionChecker;

/** Entries of the accounting section (contracts 3.1). Master Admin only: every route checks the ACCOUNTING permission. */
@RestController
@RequestMapping("/api/v1/accounting")
public class AccEntryController {

	private final AccEntryService entries;

	private final PermissionChecker permissions;

	public AccEntryController(AccEntryService entries, PermissionChecker permissions) {
		this.entries = entries;
		this.permissions = permissions;
	}

	@PostMapping("/entries")
	@ResponseStatus(HttpStatus.CREATED)
	public List<Entry> create(@RequestBody EntryInput body) {
		require(permissions);
		return entries.create(body);
	}

	@PostMapping("/entries/many")
	@ResponseStatus(HttpStatus.CREATED)
	public List<Entry> createMany(@RequestBody ManyInput body) {
		require(permissions);
		return entries.createMany(body);
	}

	@GetMapping("/groups/{id}/copy-source")
	public List<CopyDraft> copySource(@PathVariable UUID id, @RequestParam(required = false) String month) {
		require(permissions);
		return entries.copySource(id, month);
	}

	@GetMapping("/entries/{id}")
	public EntryDetail get(@PathVariable UUID id) {
		require(permissions);
		return entries.get(id);
	}

	@PatchMapping("/entries/{id}")
	public Entry edit(@PathVariable UUID id, @RequestBody EntryPatch body) {
		require(permissions);
		return entries.edit(id, body);
	}

	@PostMapping("/entries/{id}/void")
	public List<Entry> voidEntry(@PathVariable UUID id, @RequestBody VoidRequest body) {
		require(permissions);
		return entries.voidEntry(id, body);
	}

}
