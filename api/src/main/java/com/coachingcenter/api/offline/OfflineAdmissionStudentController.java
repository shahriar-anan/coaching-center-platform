package com.coachingcenter.api.offline;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;

import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.offline.OfflineAdmissionService.AdmissionView;
import com.coachingcenter.api.users.StudentPhotoService;
import tools.jackson.databind.JsonNode;

/**
 * The student's own admission (plan section 5). Every route acts on the signed-in student's own open admission, so nobody
 * can read or change another student's form. A student without an admission gets 404 on each of them.
 */
@RestController
public class OfflineAdmissionStudentController {

	private final OfflineAdmissionService admissions;

	private final StudentPhotoService photos;

	public OfflineAdmissionStudentController(OfflineAdmissionService admissions, StudentPhotoService photos) {
		this.admissions = admissions;
		this.photos = photos;
	}

	@GetMapping("/api/v1/me/admission")
	public AdmissionView current() {
		return admissions.current(studentId());
	}

	@PutMapping("/api/v1/me/admission/form")
	public AdmissionView saveForm(@RequestBody JsonNode body) {
		return admissions.saveDraft(studentId(), body);
	}

	/** The admission photo is the student's profile photo (plan side effect 10), so one upload serves both. */
	@PutMapping(value = "/api/v1/me/admission/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public AdmissionView photo(@RequestParam("file") MultipartFile file) {
		UUID studentId = studentId();
		admissions.requireOpenForm(studentId);
		photos.upload(studentId, file);
		return admissions.current(studentId);
	}

	@PostMapping("/api/v1/me/admission/submit")
	public AdmissionView submit() {
		return admissions.submit(studentId());
	}

	private static UUID studentId() {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor) {
			return actor.userId();
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

}
