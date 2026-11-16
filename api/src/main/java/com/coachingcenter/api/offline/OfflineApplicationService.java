package com.coachingcenter.api.offline;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.coachingcenter.api.auth.PhoneNumbers;
import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.error.FieldErrorDetail;
import com.coachingcenter.api.files.ExamImageStorage;
import com.coachingcenter.api.files.LocalFileStorage;
import com.coachingcenter.api.offline.OfflineAdmissionService.AdmissionView;
import com.coachingcenter.api.staffnotifications.StaffNotificationKind;
import com.coachingcenter.api.users.StudentPhotoService;
import com.coachingcenter.api.users.User;
import com.coachingcenter.api.users.UserRepository;

import tools.jackson.databind.JsonNode;

/**
 * Applying for a batch (public-admission plan, sections 2 and 5; P1, P3, P10, P11, Q2, Q4). An applicant with no account fills the form
 * under a token (no email code, P10); the office reviews it, and onboarding makes or matches the account. A signed-in student applies with
 * the form prefilled from the account. One open application per student and batch; the same email applying again to the same batch
 * while an application is open opens that one.
 */
@Service
public class OfflineApplicationService {

	/** The largest photo accepted before processing: the same limit as the profile photo (V94). */
	private static final long MAX_PHOTO_BYTES = StudentPhotoService.MAX_UPLOAD_BYTES;

	private final OfflineAdmissionRepository admissions;

	private final OfflineAdmissionSubmissionRepository submissions;

	private final OfflineBatchRepository batches;

	private final OfflineAdmissionService form;

	private final OfflineApplicationMail publicMail;

	private final ExamImageStorage images;

	private final LocalFileStorage fileStorage;

	private final UserRepository users;

	private final AuditRecorder audit;

	private final JdbcTemplate jdbc;

	private final Clock clock;

	public OfflineApplicationService(OfflineAdmissionRepository admissions, OfflineAdmissionSubmissionRepository submissions,
			OfflineBatchRepository batches, OfflineAdmissionService form, OfflineApplicationMail publicMail, ExamImageStorage images,
			LocalFileStorage fileStorage, UserRepository users, AuditRecorder audit, JdbcTemplate jdbc, Clock clock,
			StudentPhotoService photos) {
		this.admissions = admissions;
		this.submissions = submissions;
		this.batches = batches;
		this.form = form;
		this.publicMail = publicMail;
		this.images = images;
		this.fileStorage = fileStorage;
		this.users = users;
		this.audit = audit;
		this.jdbc = jdbc;
		this.clock = clock;
		this.photos = photos;
	}

	private final StudentPhotoService photos;

	/**
	 * Whether a visitor with no account may start a new application (16 Nov, issue 3). Off by default: a visitor signs up or logs in
	 * first. Applications already started keep working (D5), and a repeat start still emails the link to finish one.
	 */
	@org.springframework.beans.factory.annotation.Value("${app.offline-admission.visitor-applications:false}")
	private boolean visitorApplications;

	// ---- the applicant with no account ----------------------------------------------------------------------------------------

	/**
	 * Starts an application for a batch with the form's email. The body names the batch and the email, and may carry the form fields too.
	 * An open application of the same email for the same batch is returned instead of a new one. The result carries the token that the
	 * browser keeps; the old token of that application stops working.
	 */
	@Transactional
	public Started start(JsonNode body) {
		List<FieldErrorDetail> problems = new ArrayList<>();
		UUID batchId = uuid(body, "batchId", problems);
		String email = body != null && body.has("email") ? body.get("email").asText("").trim().toLowerCase(Locale.ROOT) : "";
		if (email.isEmpty()) {
			problems.add(new FieldErrorDetail("email", "Enter your email address."));
		}
		else if (!email.contains("@") || email.indexOf('@') == 0 || email.indexOf('@') == email.length() - 1) {
			problems.add(new FieldErrorDetail("email", "Enter a valid email address."));
		}
		OfflineBatch batch = batchId == null ? null : batches.findById(batchId).orElse(null);
		if (batchId != null && batch == null) {
			problems.add(new FieldErrorDetail("batchId", "Choose a batch from the list."));
		}
		if (!problems.isEmpty()) {
			throw validation(problems);
		}
		requireOpenBatch(batch);
		requireNotStaffEmail(email);
		Instant now = clock.instant();
		String token = OfflineApplicationMail.newToken();
		OfflineAdmission existing = admissions.findOpenPublicApplication(email, batch.getId(), OfflineAdmission.CLOSED).orElse(null);
		if (existing != null) {
			// F7, decision D2: nothing of the open application is shown here, and its browser token keeps working. A private link to it goes
			// to the email on it instead, at most once every few minutes, and the caller answers APPLICATION_EXISTS.
			// A form already sent cannot be changed, so a link to it would only open "cannot be changed"; the office emails after review.
			if (OfflineAdmissionService.FORM_OPEN.contains(existing.getStatus()) && existing.getStudentId() == null) {
				sendResumeLink(existing, now);
			}
			return Started.exists();
		}
		if (!visitorApplications) {
			throw new ApiException(ErrorCode.ACCOUNT_REQUIRED, HttpStatus.FORBIDDEN,
					"Create an account or log in to apply for a batch.");
		}
		OfflineAdmission admission = new OfflineAdmission();
		admission.setId(UUID.randomUUID());
		admission.setStudentId(null);
		admission.setSource(OfflineAdmission.SOURCE_PUBLIC);
		admission.setBatchId(batch.getId());
		admission.setStatus(OfflineAdmission.AWAITING_FORM);
		admission.setCreatedAt(now);
		admission.setSubmitCount(0);
		admission.setEmail(email);
		admission.setAccessTokenHash(publicMail.hash(token));
		OfflineAdmissionService.apply(body, admission);
		admission.setEmail(email);
		admission.setDraftSavedAt(now);
		admissions.saveAndFlush(admission);
		audit.record(AuditRecorder.OFFLINE_APPLICATION_STARTED, AuditRecorder.OFFLINE_ADMISSION_ENTITY, admission.getId());
		return new Started(token, form.view(admission));
	}

	/** Saves whatever fields the body names, for the application the token opens. Partial saves are allowed, as the signed-in form has them. */
	@Transactional
	public AdmissionView saveForm(String token, JsonNode body) {
		OfflineAdmission admission = byToken(token);
		OfflineAdmissionService.requireFormOpen(admission);
		OfflineAdmissionService.apply(body, admission);
		admission.setDraftSavedAt(clock.instant());
		admissions.saveAndFlush(admission);
		return form.view(admission);
	}

	/** The applicant's photo. It is re-encoded and resized like the profile photo, and kept on the application until onboarding (V94). */
	@Transactional
	public AdmissionView savePhoto(String token, MultipartFile file) {
		OfflineAdmission admission = byToken(token);
		OfflineAdmissionService.requireFormOpen(admission);
		if (file == null || file.isEmpty()) {
			throw validation(List.of(new FieldErrorDetail("file", "Choose a photo.")));
		}
		if (file.getSize() > MAX_PHOTO_BYTES) {
			throw new ApiException(ErrorCode.FILE_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE, "File is too large.");
		}
		if (!StudentPhotoService.hasJpegPngOrWebpSignature(file)) {
			throw validation(List.of(new FieldErrorDetail("file", "Upload a JPEG, PNG or WebP picture.")));
		}
		ExamImageStorage.StoredExamImage stored = images.store(file);
		if (stored.byteSize() > MAX_PHOTO_BYTES) {
			images.delete(stored.storedFileName());
			throw new ApiException(ErrorCode.FILE_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE, "File is too large.");
		}
		fileStorage.commitReplacement(stored.storedFileName(), admission.getPhotoStoredName());
		admission.setPhotoStoredName(stored.storedFileName());
		admission.setPhotoContentType(stored.contentType());
		admission.setPhotoByteSize(stored.byteSize());
		admissions.saveAndFlush(admission);
		return form.view(admission);
	}

	/**
	 * Sends the application to the office. Every required field and the photo must be there. The office is told once (the same staff
	 * notice a signed-in form makes), and the applicant gets one mail that the application was received (P10: no code is asked for).
	 */
	@Transactional
	public AdmissionView submit(String token) {
		OfflineAdmission admission = byToken(token);
		OfflineAdmissionService.requireFormOpen(admission);
		requireOpenBatch(batches.findById(admission.getBatchId()).orElse(null));
		List<FieldErrorDetail> problems = new ArrayList<>(form.formProblems(admission, admission.getPhotoStoredName() != null));
		requireNotStaffEmail(admission.getEmail());
		if (!problems.isEmpty()) {
			throw validation(problems);
		}
		admission.setMobile(PhoneNumbers.normalize(admission.getMobile()));
		// The emergency number is optional (16 Nov): a blank one is kept empty.
		admission.setEmergencyMobile(admission.getEmergencyMobile() == null || admission.getEmergencyMobile().isBlank() ? null
				: PhoneNumbers.normalize(admission.getEmergencyMobile()));
		boolean resubmit = OfflineAdmission.CHANGES_REQUESTED.equals(admission.getStatus()) || admission.getSubmitCount() > 0;
		Instant now = clock.instant();
		admission.setStatus(OfflineAdmission.SUBMITTED);
		admission.setSubmittedAt(now);
		admission.setSubmitCount(admission.getSubmitCount() + 1);
		admission.setChangesReason(null);
		admissions.saveAndFlush(admission);

		OfflineAdmissionSubmission row = new OfflineAdmissionSubmission();
		row.setId(UUID.randomUUID());
		row.setAdmissionId(admission.getId());
		row.setSubmittedAt(now);
		row.setSubmittedBy(null);
		row.setFormJson(form.formJson(OfflineAdmissionService.form(admission)));
		submissions.saveAndFlush(row);

		audit.record(resubmit ? AuditRecorder.OFFLINE_ADMISSION_RESUBMITTED : AuditRecorder.OFFLINE_ADMISSION_SUBMITTED,
				AuditRecorder.OFFLINE_ADMISSION_ENTITY, admission.getId());
		form.notifyAdmins(admission, resubmit ? StaffNotificationKind.ADMISSION_RESUBMITTED : StaffNotificationKind.ADMISSION_SUBMITTED);
		if (!resubmit) {
			String[] batch = batchNames(admission.getBatchId());
			publicMail.applicationReceived(admission.getEmail(), admission.getFullName(), batch[1], batch[0]);
		}
		return form.view(admission);
	}

	/**
	 * The form again, for an application that was sent back (Q4). The reopen link lasts 7 days and works while the application needs
	 * changes. The result carries a new application token for the browser, so the form can be saved and sent.
	 */
	@Transactional
	public Started reopen(String linkToken) {
		OfflineAdmission admission = admissions.findFirstByReopenTokenHash(publicMail.hash(linkToken == null ? "" : linkToken))
			.orElseThrow(OfflineApplicationService::notFound);
		Instant now = clock.instant();
		if (admission.getReopenTokenExpiresAt() == null || admission.getReopenTokenExpiresAt().isBefore(now)) {
			throw new ApiException(ErrorCode.CODE_EXPIRED, HttpStatus.BAD_REQUEST, "This link has expired. Apply again for the batch.");
		}
		// The link opens a form the applicant may still change: one sent back for changes, or one not sent yet (a resume link, F7).
		if (!OfflineAdmissionService.FORM_OPEN.contains(admission.getStatus()) || admission.getStudentId() != null) {
			throw new ApiException(ErrorCode.ADMISSION_STATE, HttpStatus.CONFLICT, "This form cannot be changed any more.");
		}
		String token = OfflineApplicationMail.newToken();
		admission.setAccessTokenHash(publicMail.hash(token));
		admissions.saveAndFlush(admission);
		return new Started(token, form.view(admission));
	}

	/** How soon after one resume link another may be mailed for the same application. */
	private static final java.time.Duration RESUME_LINK_GAP = java.time.Duration.ofMinutes(5);

	/**
	 * Mails a private link that opens the application again (the reopen link, valid 7 days). Skipped when a link was made in the last few
	 * minutes, so the form cannot be used to flood someone's inbox.
	 */
	private void sendResumeLink(OfflineAdmission admission, Instant now) {
		Instant expires = admission.getReopenTokenExpiresAt();
		if (expires != null && expires.minus(OfflineApplicationMail.CODE_DAYS, ChronoUnit.DAYS).plus(RESUME_LINK_GAP).isAfter(now)) {
			return;
		}
		String link = OfflineApplicationMail.newToken();
		admission.setReopenTokenHash(publicMail.hash(link));
		admission.setReopenTokenExpiresAt(now.plus(OfflineApplicationMail.CODE_DAYS, ChronoUnit.DAYS));
		admissions.saveAndFlush(admission);
		String[] batch = batchNames(admission.getBatchId());
		publicMail.resume(admission.getEmail(), admission.getFullName(), batch[1], batch[0], publicMail.reopenLink(link));
		audit.record(AuditRecorder.OFFLINE_REOPEN_LINK_SENT, AuditRecorder.OFFLINE_ADMISSION_ENTITY, admission.getId());
	}

	/** The applicant's open application for the token, or 404 when the token does not open one. */
	private OfflineAdmission byToken(String token) {
		return admissions.findFirstByAccessTokenHash(publicMail.hash(token == null ? "" : token))
			.filter(found -> found.getStudentId() == null)
			.orElseThrow(OfflineApplicationService::notFound);
	}

	// ---- the signed-in student ------------------------------------------------------------------------------------------------

	/**
	 * A signed-in student applies for a batch (plan section 5, B). The form is prefilled from the account: the name, the email and the mobile.
	 * Refused only when the student already has an open admission for this batch (P11).
	 */
	@Transactional
	public AdmissionView apply(UUID studentId, UUID batchId) {
		OfflineBatch batch = batchId == null ? null : batches.findById(batchId).orElse(null);
		if (batch == null) {
			throw validation(List.of(new FieldErrorDetail("batchId", "Choose a batch from the list.")));
		}
		// F2: the student's own open application for this batch is returned as it is, so a refresh in the middle of the form loses nothing.
		OfflineAdmission open = admissions.findFirstByStudentIdAndBatchIdAndStatusNot(studentId, batchId, OfflineAdmission.CLOSED)
			.orElse(null);
		if (open != null) {
			return form.view(open);
		}
		User account = users.findByIdAndDeletedAtIsNull(studentId).orElseThrow(OfflineApplicationService::notFound);
		// 16 Nov: an application this person started without an account carries on under the account, with what they typed.
		OfflineAdmission started = ownNoAccountApplication(account, batchId);
		if (started != null) {
			return form.view(started);
		}
		requireOpenBatch(batch);
		Instant now = clock.instant();
		OfflineAdmission admission = new OfflineAdmission();
		admission.setId(UUID.randomUUID());
		admission.setStudentId(studentId);
		admission.setSource(OfflineAdmission.SOURCE_ACCOUNT);
		admission.setBatchId(batchId);
		admission.setStatus(OfflineAdmission.AWAITING_FORM);
		admission.setCreatedBy(studentId);
		admission.setCreatedAt(now);
		admission.setSubmitCount(0);
		admission.setFullName(jdbc.queryForList("select full_name from student_profiles where user_id = ?", String.class, studentId)
			.stream().findFirst().orElse(null));
		admission.setEmail(account.getEmail());
		admission.setMobile(account.getPhone());
		carryForward(studentId, admission);
		admissions.saveAndFlush(admission);
		audit.record(AuditRecorder.OFFLINE_APPLICATION_STARTED, AuditRecorder.OFFLINE_ADMISSION_ENTITY, admission.getId());
		return form.view(admission);
	}

	/**
	 * A new application starts from the student's last form (17 Nov): the form they last sent in, or, when none was sent, the last one
	 * with anything typed. The name, email and mobile stay the account's; a field the account lacks (a mobile, for an account made
	 * without one) is taken from that form too. The photo is the account's profile photo, so it is there already. The student can change
	 * anything before sending it.
	 */
	private void carryForward(UUID studentId, OfflineAdmission next) {
		List<OfflineAdmission> earlier = admissions.findByStudentIdAndDeletedAtIsNullOrderByCreatedAtDesc(studentId);
		OfflineAdmission last = earlier.stream().filter(a -> a.getSubmittedAt() != null).findFirst()
			.orElseGet(() -> earlier.stream().filter(OfflineApplicationService::hasFormFields).findFirst().orElse(null));
		if (last == null) {
			return;
		}
		if (next.getFullName() == null || next.getFullName().isBlank()) {
			next.setFullName(last.getFullName());
		}
		if (next.getMobile() == null || next.getMobile().isBlank()) {
			next.setMobile(last.getMobile());
		}
		next.setFatherName(last.getFatherName());
		next.setMotherName(last.getMotherName());
		next.setDateOfBirth(last.getDateOfBirth());
		next.setGender(last.getGender());
		next.setUniversity(last.getUniversity());
		next.setSemesterBatch(last.getSemesterBatch());
		next.setNid(last.getNid());
		next.setEmergencyMobile(last.getEmergencyMobile());
		next.setHallMess(last.getHallMess());
		next.setPresentAddress(last.getPresentAddress());
		next.setPermanentAddress(last.getPermanentAddress());
	}

	/** True when anything beyond the name, email and mobile was typed into the form. */
	private static boolean hasFormFields(OfflineAdmission a) {
		return a.getFatherName() != null || a.getMotherName() != null || a.getDateOfBirth() != null || a.getUniversity() != null
				|| a.getPresentAddress() != null || a.getPermanentAddress() != null || a.getHallMess() != null;
	}

	/**
	 * The open application for this batch that the account's owner started without an account (16 Nov), linked to the account and
	 * returned; null when there is none. Email checks are optional and mail may not arrive, so an unverified email alone is not proof:
	 * it is linked only when the account's email is verified, or when both its email and its phone are the application's. The browser
	 * token and the emailed link of the application stop working, and its photo becomes the account's when the account has none.
	 */
	private OfflineAdmission ownNoAccountApplication(User account, UUID batchId) {
		if (account.getEmail() == null) {
			return null;
		}
		OfflineAdmission a = admissions.findOpenPublicApplication(account.getEmail(), batchId, OfflineAdmission.CLOSED)
			.filter(found -> found.getDeletedAt() == null)
			.orElse(null);
		if (a == null) {
			return null;
		}
		boolean verified = account.getEmailVerifiedAt() != null;
		boolean samePhone = false;
		if (account.getPhone() != null && a.getMobile() != null && !a.getMobile().isBlank()) {
			try {
				samePhone = account.getPhone().equals(PhoneNumbers.normalize(a.getMobile()));
			}
			catch (ApiException invalid) {
				samePhone = false;
			}
		}
		if (!verified && !samePhone) {
			return null;
		}
		a.setStudentId(account.getId());
		a.setAccessTokenHash(null);
		a.setReopenTokenHash(null);
		a.setReopenTokenExpiresAt(null);
		admissions.saveAndFlush(a);
		if (a.getPhotoStoredName() != null) {
			photos.adoptIfMissing(account.getId(), a.getPhotoStoredName(), a.getPhotoContentType(),
					a.getPhotoByteSize() == null ? 0L : a.getPhotoByteSize());
		}
		audit.record(AuditRecorder.OFFLINE_APPLICATION_LINKED, AuditRecorder.OFFLINE_ADMISSION_ENTITY, a.getId());
		return a;
	}

	/** Every admission of the signed-in student, one per batch and newest first (P11). */
	@Transactional(readOnly = true)
	public List<AdmissionView> mine(UUID studentId) {
		List<AdmissionView> views = new ArrayList<>();
		for (OfflineAdmission admission : admissions.findByStudentIdAndDeletedAtIsNullOrderByCreatedAtDesc(studentId)) {
			// The full admission view, so each section of the student's page has its form, its batch and its dates.
			views.add(form.view(admission));
		}
		return views;
	}

	/** One of the student's own admissions as the list shows it. Another student's, or an unknown id, is 404. */
	@Transactional(readOnly = true)
	public AdmissionSummary summaryFor(UUID studentId, UUID admissionId) {
		return summary(admissions.findById(admissionId)
			.filter(found -> studentId.equals(found.getStudentId()) && found.getDeletedAt() == null)
			.orElseThrow(OfflineApplicationService::notFound));
	}

	/** One admission as the student's list shows it: the batch, the state, where it came from, and the join state. */
	AdmissionSummary summary(OfflineAdmission a) {
		String[] batch = a.getBatchId() == null ? new String[] { null, null } : batchNamesOrNull(a.getBatchId());
		boolean joinPending = a.getJoinCodeSentAt() != null && a.getJoinCodeUsedAt() == null
				&& OfflineAdmission.READY.equals(a.getStatus());
		boolean afterOnboarding = OfflineAdmission.ONBOARDED.equals(a.getStatus()) || OfflineAdmission.CLOSED.equals(a.getStatus());
		return new AdmissionSummary(a.getId(), a.getBatchId(), batch[0], batch[1], a.getStatus(), a.getSource(), a.getSubmittedAt(),
				a.getCreatedAt(), a.getChangesReason(), joinPending, afterOnboarding ? a.getOnboardedAt() : null);
	}

	/**
	 * An application's token, for the browser, and the form as it stands. {@code alreadyApplied} is set, with no token and no form, when the
	 * email already has an open application for the batch; the controller answers APPLICATION_EXISTS (F7).
	 */
	public record Started(String token, AdmissionView admission, @com.fasterxml.jackson.annotation.JsonIgnore boolean alreadyApplied) {

		public Started(String token, AdmissionView admission) {
			this(token, admission, false);
		}

		static Started exists() {
			return new Started(null, null, true);
		}

	}

	/** One of the signed-in student's admissions, for the list and the dashboard tile. */
	public record AdmissionSummary(UUID id, UUID batchId, String batchNameEn, String batchNameBn, String state, String source,
			Instant submittedAt, Instant createdAt, String changesReason, boolean joinCodePending, Instant onboardedAt) {
	}

	// ---- checks and helpers ---------------------------------------------------------------------------------------------------

	/** A batch that is archived, or whose admissions are closed, takes no new application (plan section 9). */
	private static void requireOpenBatch(OfflineBatch batch) {
		// A draft batch takes no application (V103): students do not see it.
		if (batch == null || !OfflineBatchService.ACTIVE.equals(batch.getStatus()) || !batch.isPublished()) {
			throw new ApiException(ErrorCode.ADMISSION_STATE, HttpStatus.CONFLICT, "This batch is not open for applications.");
		}
		if (!batch.isAdmissionsOpen()) {
			throw new ApiException(ErrorCode.ADMISSION_STATE, HttpStatus.CONFLICT, "Admissions for this batch are closed.");
		}
	}

	/** An email that belongs to a staff account cannot apply (plan section 9). */
	private void requireNotStaffEmail(String email) {
		if (email == null || email.isBlank()) {
			return;
		}
		boolean staff = users.findByEmailAndDeletedAtIsNull(email.trim().toLowerCase(Locale.ROOT))
			.filter(user -> !"STUDENT".equals(user.getRole()))
			.isPresent();
		if (staff) {
			throw validation(List.of(new FieldErrorDetail("email", "This email cannot be used to apply. Use another address.")));
		}
	}

	private String[] batchNames(UUID batchId) {
		return jdbc.queryForObject("select name_en, name_bn from offline_batches where id = ?",
				(rs, row) -> new String[] { rs.getString("name_en"), rs.getString("name_bn") }, batchId);
	}

	private String[] batchNamesOrNull(UUID batchId) {
		return jdbc.query("select name_en, name_bn from offline_batches where id = ?",
				(rs, row) -> new String[] { rs.getString("name_en"), rs.getString("name_bn") }, batchId).stream().findFirst()
			.orElse(new String[] { null, null });
	}

	private static UUID uuid(JsonNode body, String key, List<FieldErrorDetail> problems) {
		if (body == null || !body.has(key) || body.get(key).isNull()) {
			problems.add(new FieldErrorDetail(key, "Choose a batch from the list."));
			return null;
		}
		try {
			return UUID.fromString(body.get(key).asText().trim());
		}
		catch (IllegalArgumentException exception) {
			problems.add(new FieldErrorDetail(key, "Choose a batch from the list."));
			return null;
		}
	}

	private static ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	private static ApiException validation(List<FieldErrorDetail> problems) {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.", problems);
	}

}
