package com.coachingcenter.api.staffledger;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.common.web.PageResponse;

/**
 * The Master Admin's staff pay routes (staff pay plan, section 5, phase 3). The service checks STAFF_PAY_MANAGE on every
 * call. The payout accounts listed here are read only: there is no route that writes another person's account.
 */
@RestController
public class StaffLedgerController {

	private final StaffLedgerService ledger;

	public StaffLedgerController(StaffLedgerService ledger) {
		this.ledger = ledger;
	}

	/** Every System Admin and instructor. {@code role} is SYSTEM_ADMIN or INSTRUCTOR; {@code owing=true} keeps balances above zero. */
	@GetMapping("/api/v1/staff-pay/people")
	public List<StaffLedgerService.PersonRow> people(@RequestParam(required = false) String role,
			@RequestParam(defaultValue = "false") boolean owing) {
		return ledger.people(role, owing);
	}

	@GetMapping("/api/v1/staff-pay/people/{userId}")
	public StaffLedgerService.PersonDetail person(@PathVariable UUID userId) {
		return ledger.person(userId);
	}

	@GetMapping("/api/v1/staff-pay/people/{userId}/ledger")
	public PageResponse<StaffLedgerService.LedgerRow> ledger(@PathVariable UUID userId,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return ledger.ledger(userId, page, size);
	}

	/** Read only, numbers in full, for the person page. */
	/** One person's salary (phase 5): settings, next pay date with its amount, and each salary month with its status. */
	@GetMapping("/api/v1/staff-pay/people/{userId}/salary")
	public StaffSalaryService.SalaryView salary(@PathVariable UUID userId) {
		return ledger.salary(userId);
	}

	@GetMapping("/api/v1/staff-pay/people/{userId}/payout-accounts")
	public List<StaffLedgerService.PayoutAccountRow> payoutAccounts(@PathVariable UUID userId) {
		return ledger.payoutAccounts(userId);
	}

	@PostMapping("/api/v1/staff-pay/people/{userId}/payments")
	public ResponseEntity<StaffLedgerService.LedgerRow> payment(@PathVariable UUID userId,
			@RequestBody(required = false) StaffLedgerService.PaymentForm form) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ledger.recordPayment(userId, form));
	}

	@PostMapping("/api/v1/staff-pay/people/{userId}/adjustments")
	public ResponseEntity<StaffLedgerService.LedgerRow> adjustment(@PathVariable UUID userId,
			@RequestBody(required = false) StaffLedgerService.AdjustmentForm form) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ledger.addAdjustment(userId, form));
	}

	@PostMapping("/api/v1/staff-pay/ledger/{id}/void")
	public StaffLedgerService.LedgerRow voidLine(@PathVariable UUID id,
			@RequestBody(required = false) StaffLedgerService.VoidForm form) {
		return ledger.voidLine(id, form);
	}

	@GetMapping("/api/v1/staff-pay/defaults")
	public StaffLedgerService.Defaults defaults() {
		return ledger.defaults();
	}

	@PutMapping("/api/v1/staff-pay/defaults")
	public StaffLedgerService.Defaults updateDefaults(@RequestBody(required = false) StaffLedgerService.DefaultsForm form) {
		return ledger.updateDefaults(form);
	}

	@PutMapping("/api/v1/staff-pay/people/{userId}/settings")
	public StaffLedgerService.SettingsView settings(@PathVariable UUID userId,
			@RequestBody(required = false) StaffLedgerService.SettingsForm form) {
		return ledger.updateSettings(userId, form);
	}

}
