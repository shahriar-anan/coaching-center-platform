package com.coachingcenter.api.offline;

import java.util.List;
import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.error.FieldErrorDetail;
import com.coachingcenter.api.files.FileResponses;
import com.coachingcenter.api.offline.OfflineAdmissionService.AdmissionItemView;
import com.coachingcenter.api.offline.OfflineAdmissionService.AdmissionView;
import com.coachingcenter.api.offline.OfflineClassService.StudentClassView;
import com.coachingcenter.api.offline.OfflineFeeService.StudentSchedule;
import com.coachingcenter.api.users.StudentPhotoService;

import tools.jackson.databind.JsonNode;

/**
 * The student's own admissions (plan section 5). Every route acts on the signed-in student's own admission, so nobody can read or change
 * another student's form. The routes without an id answer the newest open admission (or the latest closed one for reading), as they did
 * before a student could hold several (P11). The {@code /api/v1/me/admissions/{id}/...} routes answer one admission each.
 */
@RestController
public class OfflineAdmissionStudentController {

	private final OfflineAdmissionService admissions;

	private final StudentPhotoService photos;

	private final OfflineRoutineService routines;

	private final OfflineClassService classes;

	private final OfflineFeeService fees;

	private final OfflineApplicationService applications;

	private final OfflineAdmissionOnboardingService onboarding;

	public OfflineAdmissionStudentController(OfflineAdmissionService admissions, StudentPhotoService photos,
			OfflineRoutineService routines, OfflineClassService classes, OfflineFeeService fees,
			OfflineApplicationService applications, OfflineAdmissionOnboardingService onboarding) {
		this.admissions = admissions;
		this.photos = photos;
		this.routines = routines;
		this.classes = classes;
		this.fees = fees;
		this.applications = applications;
		this.onboarding = onboarding;
	}

	/** Every admission of the student, one per batch, newest first (P11). */
	@GetMapping("/api/v1/me/admissions")
	public List<AdmissionView> list() {
		return applications.mine(studentId());
	}

	/** The student applies for a batch, prefilled from the account (plan section 2, B). Refused only for the same batch while it is open. */
	@PostMapping("/api/v1/me/admission/apply")
	@ResponseStatus(HttpStatus.CREATED)
	public AdmissionView apply(@RequestBody JsonNode body) {
		return applications.apply(studentId(), batchId(body));
	}

	/**
	 * Joins the batch with the code from the mail (plan section 2, B and C). A wrong or used code is refused, and five wrong tries lock the
	 * code until a new one is sent.
	 */
	@PostMapping("/api/v1/me/admission/join")
	public AdmissionView join(@RequestBody JsonNode body) {
		String code = body != null && body.has("code") ? body.get("code").asText("") : "";
		if (code.isBlank()) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.",
					List.of(new FieldErrorDetail("code", "Enter the code from the email.")));
		}
		UUID student = studentId();
		OfflineAdmissionOnboardingService.JoinOutcome outcome = onboarding.join(student, code);
		if (outcome.refusal() != null) {
			throw new ApiException(outcome.refusal(), HttpStatus.BAD_REQUEST, outcome.message());
		}
		return admissions.viewOwn(student, outcome.admissionId());
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

	/** The items of one of the student's own admissions. */
	@GetMapping("/api/v1/me/admissions/{id}/items")
	public List<AdmissionItemView> itemsOf(@PathVariable UUID id) {
		return admissions.items(studentId(), id);
	}

	/**
	 * The student copy of the latest confirmed routine of the student's batch (or of {@code month}, yyyy-MM). It has no instructor column
	 * and carries the SYMAM LAW watermark (plan F11, G2). A routine uploaded before the new flow has no student copy and is never served.
	 * Private: the browser may keep it, no shared cache may.
	 */
	@GetMapping("/api/v1/me/admission/batch-routine.pdf")
	public ResponseEntity<InputStreamResource> batchRoutine(@RequestParam(required = false) String month) {
		UUID batchId = admissions.ownBatchId(studentId());
		return routinePdf(batchId, month);
	}

	/** The student copy of the routine of one of the student's own admissions. */
	@GetMapping("/api/v1/me/admissions/{id}/batch-routine.pdf")
	public ResponseEntity<InputStreamResource> batchRoutineOf(@PathVariable UUID id, @RequestParam(required = false) String month) {
		return routinePdf(admissions.ownBatchId(studentId(), id), month);
	}

	private ResponseEntity<InputStreamResource> routinePdf(UUID batchId, String month) {
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
		return classesOf(admissions.ownBatchId(studentId()), month);
	}

	/** The classes of one of the student's own admissions' batch for one month. */
	@GetMapping("/api/v1/me/admissions/{id}/routine")
	public List<StudentClassView> routineOf(@PathVariable UUID id, @RequestParam(required = false) String month) {
		return classesOf(admissions.ownBatchId(studentId(), id), month);
	}

	private List<StudentClassView> classesOf(UUID batchId, String month) {
		var requested = OfflineClassService.month(month);
		return classes.studentClasses(batchId, requested == null ? classes.currentMonth() : requested);
	}

	/** The fees of one of the student's own admissions (V94). */
	@GetMapping("/api/v1/me/admissions/{id}/fees")
	public StudentSchedule feesOf(@PathVariable UUID id) {
		return fees.ownSchedule(studentId(), id);
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

	private static UUID batchId(JsonNode body) {
		try {
			if (body != null && body.has("batchId") && !body.get("batchId").isNull()) {
				return UUID.fromString(body.get("batchId").asText().trim());
			}
		}
		catch (IllegalArgumentException exception) {
			// Fall through to the validation answer below.
		}
		throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.",
				List.of(new FieldErrorDetail("batchId", "Choose a batch from the list.")));
	}

	private static UUID studentId() {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor) {
			return actor.userId();
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

}
