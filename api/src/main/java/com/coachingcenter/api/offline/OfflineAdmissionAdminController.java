package com.coachingcenter.api.offline;

import static com.coachingcenter.api.offline.OfflineSupport.actorId;
import static com.coachingcenter.api.offline.OfflineSupport.require;

import java.util.List;
import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.web.PageResponse;
import com.coachingcenter.api.files.FileResponses;
import com.coachingcenter.api.offline.OfflineAdmissionAdminService.AdmissionDetail;
import com.coachingcenter.api.offline.OfflineAdmissionAdminService.Caller;
import com.coachingcenter.api.offline.OfflineAdmissionAdminService.ItemAdded;
import com.coachingcenter.api.offline.OfflineAdmissionAdminService.QueueRow;
import com.coachingcenter.api.users.StudentPhotoService;

import tools.jackson.databind.JsonNode;

/**
 * The staff and Master Admin routes for offline admissions (plan section 5, 4.6). Each route checks its permission first. A student has
 * none of these routes; the student reaches their own admission through {@link OfflineAdmissionStudentController}.
 */
@RestController
public class OfflineAdmissionAdminController {

	private final OfflineAdmissionAdminService admin;

	private final OfflineAdmissionOnboardingService onboarding;

	private final PermissionChecker permissions;

	private final OfflineAdmissionReports reports;

	public OfflineAdmissionAdminController(OfflineAdmissionAdminService admin, OfflineAdmissionOnboardingService onboarding,
			PermissionChecker permissions, OfflineAdmissionReports reports) {
		this.admin = admin;
		this.onboarding = onboarding;
		this.permissions = permissions;
		this.reports = reports;
	}

	/** The tab and dashboard counts (plan 5). Staff and the Master Admin both hold the read permission. */
	@GetMapping("/api/v1/offline-admissions/counts")
	public OfflineAdmissionReports.Counts counts() {
		require(permissions, Permissions.OFFLINE_ADMISSION_READ);
		return reports.counts();
	}

	/** The overdue list of the fee page: one row per admission, the oldest overdue due, most days late first (plan 6.3). */
	@GetMapping("/api/v1/offline-admissions/overdue")
	public PageResponse<OfflineAdmissionReports.OverdueRow> overdue(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		if (!permissions.has(Permissions.OFFLINE_FEE_RECORD) && !permissions.has(Permissions.OFFLINE_ADMISSION_READ)) {
			throw OfflineSupport.denied();
		}
		return reports.overdue(page, size);
	}

	/** The history of one admission: its audit events, payments and submissions, newest first (plan 6.3). */
	@GetMapping("/api/v1/offline-admissions/{id}/history")
	public List<OfflineAdmissionReports.HistoryRow> history(@PathVariable UUID id) {
		require(permissions, Permissions.OFFLINE_ADMISSION_READ);
		return reports.history(id);
	}

	@GetMapping("/api/v1/offline-admissions")
	public PageResponse<QueueRow> queue(@RequestParam(required = false) String status,
			@RequestParam(required = false) UUID batchId, @RequestParam(required = false) String q,
			@RequestParam(required = false) String flag, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		require(permissions, Permissions.OFFLINE_ADMISSION_READ);
		return admin.queue(status, batchId, q, flag, page, size);
	}

	@GetMapping("/api/v1/offline-admissions/{id}")
	public AdmissionDetail detail(@PathVariable UUID id) {
		require(permissions, Permissions.OFFLINE_ADMISSION_READ);
		return admin.detail(id);
	}

	@PatchMapping("/api/v1/offline-admissions/{id}/setup")
	public AdmissionDetail setup(@PathVariable UUID id, @RequestBody JsonNode body) {
		require(permissions, Permissions.OFFLINE_ADMISSION_REVIEW);
		return admin.setup(actorId(), id, body);
	}

	@PostMapping("/api/v1/offline-admissions/{id}/items")
	public ItemAdded addItem(@PathVariable UUID id, @RequestBody JsonNode body) {
		Caller caller = caller();
		return admin.addItem(actorId(), caller, id, body);
	}

	@DeleteMapping("/api/v1/offline-admissions/{id}/items/{itemId}")
	public AdmissionDetail removeItem(@PathVariable UUID id, @PathVariable UUID itemId) {
		return admin.removeItem(actorId(), caller(), id, itemId);
	}

	@PostMapping("/api/v1/offline-admissions/{id}/request-changes")
	public AdmissionDetail requestChanges(@PathVariable UUID id, @RequestBody JsonNode body) {
		require(permissions, Permissions.OFFLINE_ADMISSION_REVIEW);
		return admin.requestChanges(actorId(), id, body == null || body.get("reason") == null || body.get("reason").isNull()
				? null : body.get("reason").asText());
	}

	@PostMapping("/api/v1/offline-admissions/{id}/ready")
	public AdmissionDetail ready(@PathVariable UUID id) {
		require(permissions, Permissions.OFFLINE_ADMISSION_REVIEW);
		return admin.ready(actorId(), id);
	}

	@PostMapping("/api/v1/offline-admissions/{id}/back-to-review")
	public AdmissionDetail backToReview(@PathVariable UUID id) {
		require(permissions, Permissions.OFFLINE_ADMISSION_REVIEW);
		return admin.backToReview(actorId(), id);
	}

	@PostMapping("/api/v1/offline-admissions/{id}/onboard")
	public AdmissionDetail onboard(@PathVariable UUID id, @RequestBody(required = false) JsonNode body) {
		require(permissions, Permissions.OFFLINE_ADMISSION_ONBOARD);
		String rollNo = body != null && body.hasNonNull("rollNo") ? body.get("rollNo").asText() : null;
		return onboarding.onboard(actorId(), id, rollNo);
	}

	/** The next free roll for the admission's batch, or null when the batch has none to start from (rule A7). */
	@GetMapping("/api/v1/offline-admissions/{id}/next-roll")
	public OfflineAdmissionOnboardingService.SuggestedRoll nextRoll(@PathVariable UUID id) {
		require(permissions, Permissions.OFFLINE_ADMISSION_ONBOARD);
		return onboarding.suggestRoll(id);
	}

	/** The Master Admin sets or changes a student's roll number once onboarded (plan O3). */
	@PatchMapping("/api/v1/offline-admissions/{id}/roll")
	public AdmissionDetail setRoll(@PathVariable UUID id, @RequestBody JsonNode body) {
		require(permissions, Permissions.OFFLINE_ADMISSION_ONBOARD);
		String rollNo = body != null && body.hasNonNull("rollNo") ? body.get("rollNo").asText() : null;
		return onboarding.setRoll(actorId(), id, rollNo);
	}

	/**
	 * A new code for an admission that waits for one (P8): the join code, or the activation code of an account the onboarding made. The
	 * old code stops working. Only the Master Admin, who onboards.
	 */
	@PostMapping("/api/v1/offline-admissions/{id}/resend-code")
	public AdmissionDetail resendCode(@PathVariable UUID id) {
		require(permissions, Permissions.OFFLINE_ADMISSION_ONBOARD);
		return onboarding.resendCode(actorId(), id);
	}

	@PatchMapping("/api/v1/offline-admissions/{id}/terms")
	public AdmissionDetail terms(@PathVariable UUID id, @RequestBody JsonNode body) {
		require(permissions, Permissions.OFFLINE_ADMISSION_ONBOARD);
		return onboarding.terms(actorId(), id, body);
	}

	@PostMapping("/api/v1/offline-admissions/{id}/close")
	public AdmissionDetail close(@PathVariable UUID id, @RequestBody JsonNode body) {
		require(permissions, Permissions.OFFLINE_ADMISSION_ONBOARD);
		return onboarding.close(actorId(), id, body == null || body.get("reason") == null || body.get("reason").isNull()
				? null : body.get("reason").asText());
	}

	@PutMapping(value = "/api/v1/offline-admissions/{id}/signed-form", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public AdmissionDetail uploadSigned(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
		require(permissions, Permissions.OFFLINE_ADMISSION_REVIEW);
		return admin.uploadSigned(actorId(), id, file);
	}

	/** The signed copy, private to staff. Read by anyone who can read the admission. */
	@GetMapping("/api/v1/offline-admissions/{id}/signed-form")
	public ResponseEntity<InputStreamResource> signedForm(@PathVariable UUID id) {
		require(permissions, Permissions.OFFLINE_ADMISSION_READ);
		OfflineAdmissionAdminService.SignedFile file = admin.openSigned(id);
		return FileResponses.inline(file.body(), file.contentType(), file.byteSize(), file.fileName(), "private, no-cache");
	}

	/** The student's photo, which is their profile photo (plan side effect 10). */
	@GetMapping("/api/v1/offline-admissions/{id}/photo")
	public ResponseEntity<InputStreamResource> photo(@PathVariable UUID id) {
		require(permissions, Permissions.OFFLINE_ADMISSION_READ);
		StudentPhotoService.PhotoFile file = admin.openPhoto(id);
		return FileResponses.inline(file.body(), file.contentType(), file.byteSize(), StudentPhotoService.FILE_NAME,
				"private, no-cache");
	}

	/** Exams and packages are the review's before onboarding and the Master Admin's after it (D16). */
	private Caller caller() {
		return new Caller(permissions.has(Permissions.OFFLINE_ADMISSION_REVIEW), permissions.has(Permissions.OFFLINE_ADMISSION_ONBOARD));
	}

}
