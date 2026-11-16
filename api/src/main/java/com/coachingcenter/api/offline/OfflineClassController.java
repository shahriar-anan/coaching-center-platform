package com.coachingcenter.api.offline;

import static com.coachingcenter.api.offline.OfflineSupport.actorId;
import static com.coachingcenter.api.offline.OfflineSupport.require;

import java.time.LocalDate;
import java.util.List;
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
import com.coachingcenter.api.offline.OfflineClassService.BulkResult;
import com.coachingcenter.api.offline.OfflineClassService.ClassView;
import com.coachingcenter.api.offline.OfflineClassService.UnsettledCounts;
import com.coachingcenter.api.offline.OfflineClassService.UnsettledPage;

import tools.jackson.databind.JsonNode;

/**
 * Offline classes and their settling (plan section 6, stage 3). Staff and the Master Admin see and edit the classes of a batch
 * (OFFLINE_BATCH_ROUTINE). Staff record a payment or a not-taken mark (OFFLINE_CLASS_SETTLE). Only the Master Admin approves, rejects
 * and voids (OFFLINE_CLASS_APPROVE). Students never reach these routes.
 */
@RestController
public class OfflineClassController {

	private final OfflineClassService classes;

	private final PermissionChecker permissions;

	public OfflineClassController(OfflineClassService classes, PermissionChecker permissions) {
		this.classes = classes;
		this.permissions = permissions;
	}

	@GetMapping("/api/v1/offline-batches/{id}/classes")
	public List<ClassView> list(@PathVariable UUID id, @RequestParam(required = false) String month,
			@RequestParam(required = false) String status, @RequestParam(required = false) UUID instructorId) {
		require(permissions, Permissions.OFFLINE_BATCH_ROUTINE);
		return classes.list(id, OfflineClassService.month(month), status, instructorId);
	}

	@PostMapping("/api/v1/offline-batches/{id}/classes")
	@ResponseStatus(HttpStatus.CREATED)
	public ClassView create(@PathVariable UUID id, @RequestBody JsonNode body) {
		require(permissions, Permissions.OFFLINE_BATCH_ROUTINE);
		return classes.create(id, actorId(), body);
	}

	@PatchMapping("/api/v1/offline-classes/{id}")
	public ClassView update(@PathVariable UUID id, @RequestBody JsonNode body) {
		require(permissions, Permissions.OFFLINE_BATCH_ROUTINE);
		return classes.update(id, actorId(), body);
	}

	/** Deletes a class by hand (16 Nov). Both admin roles, as they add and edit classes. Answers 204. */
	@org.springframework.web.bind.annotation.DeleteMapping("/api/v1/offline-classes/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable UUID id) {
		require(permissions, Permissions.OFFLINE_BATCH_ROUTINE);
		classes.delete(id, actorId());
	}

	@PostMapping("/api/v1/offline-classes/{id}/payment")
	public ClassView payment(@PathVariable UUID id, @RequestBody JsonNode body) {
		require(permissions, Permissions.OFFLINE_CLASS_SETTLE);
		return classes.recordPayment(id, actorId(), permissions.has(Permissions.OFFLINE_CLASS_APPROVE), body);
	}

	@PostMapping("/api/v1/offline-classes/{id}/not-taken")
	public ClassView notTaken(@PathVariable UUID id, @RequestBody JsonNode body) {
		require(permissions, Permissions.OFFLINE_CLASS_SETTLE);
		return classes.requestNotTaken(id, actorId(), permissions.has(Permissions.OFFLINE_CLASS_APPROVE), body);
	}

	@PostMapping("/api/v1/offline-class-payments/{id}/approve")
	public ClassView approvePayment(@PathVariable UUID id) {
		require(permissions, Permissions.OFFLINE_CLASS_APPROVE);
		return classes.approvePayment(id, actorId());
	}

	@PostMapping("/api/v1/offline-class-payments/{id}/reject")
	public ClassView rejectPayment(@PathVariable UUID id, @RequestBody JsonNode body) {
		require(permissions, Permissions.OFFLINE_CLASS_APPROVE);
		return classes.rejectPayment(id, actorId(), body);
	}

	@PostMapping("/api/v1/offline-class-payments/{id}/void")
	public ClassView voidPayment(@PathVariable UUID id, @RequestBody JsonNode body) {
		require(permissions, Permissions.OFFLINE_CLASS_APPROVE);
		return classes.voidPayment(id, actorId(), body);
	}

	@PostMapping("/api/v1/offline-class-payments/approve-many")
	public List<BulkResult> approvePayments(@RequestBody JsonNode body) {
		require(permissions, Permissions.OFFLINE_CLASS_APPROVE);
		return classes.approvePayments(actorId(), body);
	}

	@PostMapping("/api/v1/offline-class-decisions/{id}/approve")
	public ClassView approveNotTaken(@PathVariable UUID id) {
		require(permissions, Permissions.OFFLINE_CLASS_APPROVE);
		return classes.approveNotTaken(id, actorId());
	}

	@PostMapping("/api/v1/offline-class-decisions/{id}/reject")
	public ClassView rejectNotTaken(@PathVariable UUID id, @RequestBody JsonNode body) {
		require(permissions, Permissions.OFFLINE_CLASS_APPROVE);
		return classes.rejectNotTaken(id, actorId(), body);
	}

	@PostMapping("/api/v1/offline-class-decisions/approve-many")
	public List<BulkResult> approveNotTakenMany(@RequestBody JsonNode body) {
		require(permissions, Permissions.OFFLINE_CLASS_APPROVE);
		return classes.approveNotTakenMany(actorId(), body);
	}

	@GetMapping("/api/v1/offline-classes/unsettled")
	public UnsettledPage unsettled(@RequestParam(required = false) UUID batchId, @RequestParam(required = false) UUID instructorId,
			@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
		require(permissions, Permissions.OFFLINE_CLASS_SETTLE);
		return classes.unsettled(batchId, instructorId, from, to, page, size);
	}

	@GetMapping("/api/v1/offline-classes/unsettled/counts")
	public UnsettledCounts counts() {
		require(permissions, Permissions.OFFLINE_CLASS_SETTLE);
		return classes.counts();
	}

}
