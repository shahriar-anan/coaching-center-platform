package com.coachingcenter.api.accounting.report;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

/**
 * The reads of the accounting section: the home page, the entries list and its CSV (contracts.md, section 3.2). Master Admin only: every
 * route answers 403 to anyone else.
 */
@RestController
@RequestMapping("/api/v1/accounting")
public class AccReportController {

	private final AccReportService reports;

	private final AccEntryListService entries;

	private final PermissionChecker permissions;

	public AccReportController(AccReportService reports, AccEntryListService entries, PermissionChecker permissions) {
		this.reports = reports;
		this.entries = entries;
		this.permissions = permissions;
	}

	@GetMapping("/dashboard")
	public AccReportService.Dashboard dashboard() {
		requireAccounting();
		return reports.dashboard();
	}

	@GetMapping("/entries")
	public AccEntryListService.EntryPage entries(@RequestParam(required = false) String from,
			@RequestParam(required = false) String to, @RequestParam(required = false) String groupId,
			@RequestParam(required = false) String payeeId, @RequestParam(required = false) String typeId,
			@RequestParam(required = false) String kind, @RequestParam(required = false) String accountId,
			@RequestParam(required = false) String batchId, @RequestParam(required = false) String status,
			@RequestParam(required = false) String q, @RequestParam(required = false) String sort,
			@RequestParam(required = false) String page, @RequestParam(required = false) String size) {
		requireAccounting();
		return entries.list(new AccEntryListService.Request(from, to, groupId, payeeId, typeId, kind, accountId, batchId, status, q,
				sort, page, size));
	}

	@GetMapping("/entries.csv")
	public ResponseEntity<byte[]> entriesCsv(@RequestParam(required = false) String from,
			@RequestParam(required = false) String to, @RequestParam(required = false) String groupId,
			@RequestParam(required = false) String payeeId, @RequestParam(required = false) String typeId,
			@RequestParam(required = false) String kind, @RequestParam(required = false) String accountId,
			@RequestParam(required = false) String batchId, @RequestParam(required = false) String status,
			@RequestParam(required = false) String q, @RequestParam(required = false) String sort) {
		requireAccounting();
		AccEntryListService.Csv csv = entries.csv(new AccEntryListService.Request(from, to, groupId, payeeId, typeId, kind, accountId,
				batchId, status, q, sort, null, null));
		return ResponseEntity.status(HttpStatus.OK)
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + csv.fileName() + "\"")
			.contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
			.body(csv.bytes());
	}

	@GetMapping("/reports/owed")
	public AccReportService.OwedReport owed() {
		requireAccounting();
		return reports.owed();
	}

	@GetMapping("/reports/monthly")
	public AccReportService.MonthlyReport monthly(@RequestParam(required = false) String from,
			@RequestParam(required = false) String to) {
		requireAccounting();
		return reports.monthly(from, to);
	}

	@GetMapping("/groups/{id}/summary")
	public AccReportService.GroupSummary summary(@PathVariable UUID id, @RequestParam(required = false) String from,
			@RequestParam(required = false) String to) {
		requireAccounting();
		return reports.summary(id, from, to);
	}

	@GetMapping("/payees/{id}/statement")
	public AccReportService.Statement statement(@PathVariable UUID id, @RequestParam(required = false) String from,
			@RequestParam(required = false) String to) {
		requireAccounting();
		return reports.statement(id, from, to);
	}

	private void requireAccounting() {
		if (!permissions.has(Permissions.ACCOUNTING)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
	}

}
