package com.coachingcenter.api.offline;

import static com.coachingcenter.api.offline.OfflineSupport.actorId;
import static com.coachingcenter.api.offline.OfflineSupport.require;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.offline.OfflineInstructorPayService.Cards;
import com.coachingcenter.api.offline.OfflineInstructorPayService.PaySummary;
import com.coachingcenter.api.offline.OfflineInstructorPayService.PaymentList;
import com.coachingcenter.api.offline.OfflineInstructorPayService.PaymentView;

import tools.jackson.databind.JsonNode;

/**
 * Payments to offline instructors (24 Nov, instructor-pay-simple-plan.md). The Master Admin alone: a System Admin gets 403 on every
 * route here.
 */
@RestController
public class OfflineInstructorPayController {

	private final OfflineInstructorPayService pay;

	private final PermissionChecker permissions;

	public OfflineInstructorPayController(OfflineInstructorPayService pay, PermissionChecker permissions) {
		this.pay = pay;
		this.permissions = permissions;
	}

	/** One instructor's payments, every status, newest first. */
	@GetMapping("/api/v1/offline-instructors/{id}/payments")
	public PaymentList history(@PathVariable UUID id, @RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to, @RequestParam(required = false) String kind) {
		require(permissions, Permissions.OFFLINE_INSTRUCTOR_PAY);
		return pay.history(id, from, to, kind);
	}

	@GetMapping("/api/v1/offline-instructors/{id}/pay-summary")
	public PaySummary summary(@PathVariable UUID id) {
		require(permissions, Permissions.OFFLINE_INSTRUCTOR_PAY);
		return pay.summary(id);
	}

	@PostMapping("/api/v1/offline-instructors/{id}/payments")
	@ResponseStatus(HttpStatus.CREATED)
	public PaymentView record(@PathVariable UUID id, @RequestBody JsonNode body) {
		require(permissions, Permissions.OFFLINE_INSTRUCTOR_PAY);
		return pay.record(id, actorId(), body);
	}

	/** Payments across instructors: approved and voided, newest first. */
	@GetMapping("/api/v1/offline-instructor-payments")
	public PaymentList all(@RequestParam(required = false) UUID instructorId, @RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to, @RequestParam(required = false) UUID batchId,
			@RequestParam(required = false) String kind) {
		require(permissions, Permissions.OFFLINE_INSTRUCTOR_PAY);
		return pay.all(instructorId, from, to, batchId, kind);
	}

	/** Each instructor's paid-this-month and last payment date, for the instructors list. */
	@GetMapping("/api/v1/offline-instructor-payments/figures")
	public java.util.Map<UUID, OfflineInstructorPayService.ListFigures> figures() {
		require(permissions, Permissions.OFFLINE_INSTRUCTOR_PAY);
		return pay.listFigures();
	}

	@GetMapping("/api/v1/offline-instructor-payments/cards")
	public Cards cards() {
		require(permissions, Permissions.OFFLINE_INSTRUCTOR_PAY);
		return pay.cards();
	}

	/** Corrects a payment; {@code recordKind} is OTHER (recorded by hand) or CLASS (a class payment from before 24 Nov). */
	@PatchMapping("/api/v1/offline-instructor-payments/{recordKind}/{id}")
	public PaymentView edit(@PathVariable String recordKind, @PathVariable UUID id, @RequestBody JsonNode body) {
		require(permissions, Permissions.OFFLINE_INSTRUCTOR_PAY);
		return pay.edit(recordKind, id, actorId(), body);
	}

	@PostMapping("/api/v1/offline-instructor-payments/{recordKind}/{id}/void")
	public PaymentView voidPayment(@PathVariable String recordKind, @PathVariable UUID id, @RequestBody(required = false) JsonNode body) {
		require(permissions, Permissions.OFFLINE_INSTRUCTOR_PAY);
		return pay.voidPayment(recordKind, id, actorId(), body);
	}

}
