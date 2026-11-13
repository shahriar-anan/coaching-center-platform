package com.coachingcenter.api.offline;

import java.util.List;
import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.files.FileResponses;
import com.coachingcenter.api.offline.OfflineAdmissionService.AdmissionItemView;
import com.coachingcenter.api.offline.OfflineAdmissionService.AdmissionView;
import com.coachingcenter.api.offline.OfflineClassService.StudentClassView;
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

	private final OfflineRoutineService routines;

	private final OfflineClassService classes;

	public OfflineAdmissionStudentController(OfflineAdmissionService admissions, StudentPhotoService photos,
			OfflineRoutineService routines, OfflineClassService classes) {
		this.admissions = admissions;
		this.photos = photos;
		this.routines = routines;
		this.classes = classes;
	}

	@GetMapping("/api/v1/me/admission")
	public AdmissionView current() {
		return admissions.current(studentId());
	}

	/** The student's live exams and packages through this admission (plan 6.4, Exams and Packages). */
	@GetMapping("/api/v1/me/admission/items")
	public List<AdmissionItemView> items() {
		return admissions.items(studentId());
	}

	/**
	 * The student copy of the latest confirmed routine of the student's batch (or of {@code month}, yyyy-MM). It has no instructor column
	 * and carries the SYMAM LAW watermark (plan F11, G2). A routine uploaded before the new flow has no student copy and is never served.
	 * Private: the browser may keep it, no shared cache may.
	 */
	@GetMapping("/api/v1/me/admission/batch-routine.pdf")
	public ResponseEntity<InputStreamResource> batchRoutine(@RequestParam(required = false) String month) {
		UUID batchId = admissions.ownBatchId(studentId());
		OfflineBatchService.RoutineFile file = routines.studentCopy(batchId, OfflineClassService.month(month));
		return FileResponses.inline(file.body(), MediaType.APPLICATION_PDF_VALUE, file.byteSize(), file.fileName(),
				"private, no-cache");
	}

	/**
	 * The classes of the student's batch for one month (yyyy-MM, the current month in Dhaka when none is given): date, day, time, subject,
	 * topic and kind. Never an instructor, an amount or a pay field.
	 */
	@GetMapping("/api/v1/me/admission/routine")
	public List<StudentClassView> routine(@RequestParam(required = false) String month) {
		UUID batchId = admissions.ownBatchId(studentId());
		var requested = OfflineClassService.month(month);
		return classes.studentClasses(batchId, requested == null ? classes.currentMonth() : requested);
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
