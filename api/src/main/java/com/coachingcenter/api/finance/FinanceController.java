package com.coachingcenter.api.finance;

import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.finance.FinanceService.BatchRow;
import com.coachingcenter.api.finance.FinanceService.EntryPage;
import com.coachingcenter.api.finance.FinanceService.InstructorRow;
import com.coachingcenter.api.finance.FinanceService.Month;
import com.coachingcenter.api.finance.FinanceService.Summary;

/**
 * The finance reports (plan section 6, stage 5). The Master Admin only (FINANCE_READ, plan G3): staff get 403 on every route. Read only.
 */
@RestController
public class FinanceController {

	private final FinanceService finance;

	private final PermissionChecker permissions;

	public FinanceController(FinanceService finance, PermissionChecker permissions) {
		this.finance = finance;
		this.permissions = permissions;
	}

	@GetMapping("/api/v1/finance/summary")
	public Summary summary(@RequestParam(required = false) String from, @RequestParam(required = false) String to,
			@RequestParam(required = false) String sources, @RequestParam(required = false) String batchId) {
		require();
		return finance.summary(from, to, sources, batchId);
	}

	@GetMapping("/api/v1/finance/monthly")
	public List<Month> monthly(@RequestParam(required = false) String from, @RequestParam(required = false) String to,
			@RequestParam(required = false) String sources, @RequestParam(required = false) String batchId) {
		require();
		return finance.monthly(from, to, sources, batchId);
	}

	@GetMapping("/api/v1/finance/batches")
	public List<BatchRow> batches(@RequestParam(required = false) String from, @RequestParam(required = false) String to,
			@RequestParam(required = false) String batchId) {
		require();
		return finance.batches(from, to, batchId);
	}

	@GetMapping("/api/v1/finance/instructors")
	public List<InstructorRow> instructors(@RequestParam(required = false) String from, @RequestParam(required = false) String to) {
		require();
		return finance.instructors(from, to);
	}

	@GetMapping("/api/v1/finance/entries")
	public EntryPage entries(@RequestParam(required = false) String from, @RequestParam(required = false) String to,
			@RequestParam(required = false) String sources, @RequestParam(required = false) String batchId,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
		require();
		return finance.entries(from, to, sources, batchId, page, size);
	}

	/** The entries of the same filters as CSV, UTF-8 with a byte order mark so Excel reads the Bangla text. Private, never cached shared. */
	@GetMapping("/api/v1/finance/entries.csv")
	public ResponseEntity<byte[]> entriesCsv(@RequestParam(required = false) String from, @RequestParam(required = false) String to,
			@RequestParam(required = false) String sources, @RequestParam(required = false) String batchId) {
		require();
		byte[] body = finance.entriesCsv(from, to, sources, batchId);
		return ResponseEntity.ok()
			.contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
			.header("Content-Disposition", "attachment; filename=\"finance-entries.csv\"")
			.cacheControl(CacheControl.noCache().cachePrivate())
			.body(body);
	}

	private void require() {
		if (!permissions.has(Permissions.FINANCE_READ)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
	}

}
