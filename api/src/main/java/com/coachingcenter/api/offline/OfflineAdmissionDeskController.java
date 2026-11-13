package com.coachingcenter.api.offline;

import static com.coachingcenter.api.offline.OfflineSupport.actorId;
import static com.coachingcenter.api.offline.OfflineSupport.require;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.FieldErrorDetail;
import com.coachingcenter.api.offline.OfflineAdmissionDeskService.CodeIssued;
import com.coachingcenter.api.offline.OfflineAdmissionDeskService.CodeView;
import com.coachingcenter.api.offline.OfflineAdmissionDeskService.DeskCreated;

import tools.jackson.databind.JsonNode;

/**
 * The desk's routes for a phone-only admission and its activation code (product decision, 13 November 2026). Staff and the Master Admin
 * hold the review permission; the read of the code needs the read permission only. Instructors and students get none of them.
 */
@RestController
public class OfflineAdmissionDeskController {

	private final OfflineAdmissionDeskService desk;

	private final PermissionChecker permissions;

	public OfflineAdmissionDeskController(OfflineAdmissionDeskService desk, PermissionChecker permissions) {
		this.desk = desk;
		this.permissions = permissions;
	}

	/** Starts an admission from a phone number alone: the account, the AWAITING_FORM admission and a 10-minute code. */
	@PostMapping("/api/v1/offline-admissions/desk")
	public ResponseEntity<DeskCreated> create(@RequestBody JsonNode body) {
		require(permissions, Permissions.OFFLINE_ADMISSION_REVIEW);
		if (!OfflineSupport.has(body, "phone")) {
			throw OfflineSupport.validation(List.of(new FieldErrorDetail("phone", "Enter the student's mobile number.")));
		}
		return ResponseEntity.status(HttpStatus.CREATED).body(desk.create(body.get("phone").asText(), actorId()));
	}

	/** The live code of an admission, or why there is none. An expired code is never returned. */
	@GetMapping("/api/v1/offline-admissions/{id}/activation-code")
	public CodeView current(@PathVariable UUID id) {
		require(permissions, Permissions.OFFLINE_ADMISSION_READ);
		return desk.current(id);
	}

	/** A new 10-minute code. The old one stops working at once. */
	@PostMapping("/api/v1/offline-admissions/{id}/activation-code")
	public CodeIssued regenerate(@PathVariable UUID id) {
		require(permissions, Permissions.OFFLINE_ADMISSION_REVIEW);
		return desk.regenerate(id, actorId());
	}

}
