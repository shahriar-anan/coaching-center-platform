package com.coachingcenter.api.offline;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.auth.PhoneNumbers;
import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.error.FieldErrorDetail;
import com.coachingcenter.api.staffnotifications.StaffNotificationKind;
import com.coachingcenter.api.staffnotifications.StaffNotificationWriter;
import com.coachingcenter.api.users.AccountService;
import com.coachingcenter.api.users.StudentPhotoRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The student's offline admission form and its submission (plan sections 4.2, 5 and 6.2). Staff and the Master Admin review
 * the form later; this service only takes it in. A form is saved as the student types, and submitted once every required
 * field is valid and the photo is on file (D6, D8).
 */
@Service
public class OfflineAdmissionService {

	/** The states in which the student may change the form. */
	static final Set<String> FORM_OPEN = Set.of(OfflineAdmission.AWAITING_FORM, OfflineAdmission.CHANGES_REQUESTED);

	private static final Set<String> GENDERS = Set.of("MALE", "FEMALE");

	private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

	/** 10, 13 or 17 digits, the lengths of a Bangladeshi national ID. */
	private static final Pattern NID = Pattern.compile("^(\\d{10}|\\d{13}|\\d{17})$");

	/** The longest each text field may be, matching the column sizes in V82. */
	private static final Map<String, Integer> MAX_LENGTH = Map.ofEntries(
			Map.entry("fullName", 200), Map.entry("fatherName", 200), Map.entry("motherName", 200),
			Map.entry("email", 255), Map.entry("university", 200), Map.entry("semesterBatch", 100),
			Map.entry("hallMess", 300), Map.entry("thana", 100), Map.entry("district", 100),
			Map.entry("presentAddress", 1000), Map.entry("permanentAddress", 1000));

	/** The form as the student fills it in. A null field is not filled in yet. */
	public record Form(String fullName, String fatherName, String motherName, LocalDate dateOfBirth, String email,
			String gender, String university, String semesterBatch, String nid, String mobile, String emergencyMobile,
			String hallMess, String thana, String district, String presentAddress, String permanentAddress) {
	}

	/**
	 * What the student's own screens show: the state, the form, and why it was sent back, if it was. The batch, the fee and the dates
	 * are filled only once the admission is onboarded or closed (plan 6.4); before that they are null, and hasRoutine is false.
	 */
	public record AdmissionView(UUID id, String state, String changesReason, Instant submittedAt, int submitCount,
			boolean hasPhoto, Form form, String batchNameEn, String batchNameBn, BigDecimal monthlyFee, LocalDate admittedOn,
			LocalDate courseEndsOn, boolean hasRoutine) {
	}

	/**
	 * One exam or package the student has live access to through this admission. {@code packageExamCount} counts the active exams of
	 * a package, and is null for an exam. {@code accessEndsAt} is the course-end cap of the grant (plan 4.4).
	 */
	public record AdmissionItemView(String scope, UUID examId, UUID moduleId, String titleEn, String titleBn,
			Integer packageExamCount, Instant accessEndsAt) {
	}

	private final OfflineAdmissionRepository admissions;

	private final OfflineAdmissionSubmissionRepository submissions;

	private final AccountService accounts;

	private final StudentPhotoRepository photos;

	private final StaffNotificationWriter notices;

	private final JdbcTemplate jdbc;

	private final ObjectMapper json;

	private final AuditRecorder audit;

	private final Clock clock;

	public OfflineAdmissionService(OfflineAdmissionRepository admissions, OfflineAdmissionSubmissionRepository submissions,
			AccountService accounts, StudentPhotoRepository photos, StaffNotificationWriter notices, JdbcTemplate jdbc,
			ObjectMapper json, AuditRecorder audit, Clock clock) {
		this.admissions = admissions;
		this.submissions = submissions;
		this.accounts = accounts;
		this.photos = photos;
		this.notices = notices;
		this.jdbc = jdbc;
		this.json = json;
		this.audit = audit;
		this.clock = clock;
	}

	/**
	 * The desk creates a student and their admission in one transaction (D1, D3). The student then activates with the OTP and
	 * fills in the form. A student who already has an open admission cannot get a second one (the database refuses it).
	 */
	@Transactional
	public AccountService.CreatedAccount createOfflineStudent(String fullName, String phone, String email, UUID createdBy) {
		AccountService.CreatedAccount created = accounts.createStudent(fullName, phone, email, createdBy);
		OfflineAdmission admission = new OfflineAdmission();
		admission.setId(UUID.randomUUID());
		admission.setStudentId(created.id());
		admission.setStatus(OfflineAdmission.AWAITING_FORM);
		admission.setCreatedBy(createdBy);
		admission.setCreatedAt(clock.instant());
		admission.setSubmitCount(0);
		admissions.saveAndFlush(admission);
		audit.record(AuditRecorder.OFFLINE_ADMISSION_CREATED, AuditRecorder.OFFLINE_ADMISSION_ENTITY, admission.getId());
		return created;
	}

	/**
	 * The student's open admission, or their latest closed one as read-only history (plan 6.4). A student with neither gets a 404, so
	 * the web never shows the page to anyone else (D22). The gate and the form routes still use {@link #open} alone.
	 */
	@Transactional(readOnly = true)
	public AdmissionView current(UUID studentId) {
		return view(ownAdmission(studentId));
	}

	/** The student's live access through this admission: active items whose OFFLINE grant is not revoked and not past its end (plan 6.4). */
	@Transactional(readOnly = true)
	public List<AdmissionItemView> items(UUID studentId) {
		OfflineAdmission admission = ownAdmission(studentId);
		return jdbc.query("""
				select i.scope, i.exam_id, i.module_id, coalesce(e.title_en, m.title_en) as title_en,
				  coalesce(e.title_bn, m.title_bn) as title_bn,
				  case when i.scope = 'MODULE' then (
				    select count(*)::int from live_exam_package_exams pe
				    join live_exams x on x.id = pe.exam_id and x.deleted_at is null
				    where pe.module_id = i.module_id and pe.removed_at is null) end as package_exam_count,
				  g.access_ends_at
				from offline_admission_items i
				join live_exam_grants g on g.id = i.grant_id
				left join live_exams e on e.id = i.exam_id
				left join live_exam_modules m on m.id = i.module_id
				where i.admission_id = ? and i.removed_at is null
				  and g.source = 'OFFLINE' and g.revoked_at is null
				  and (g.access_ends_at is null or g.access_ends_at > ?)
				order by i.added_at, i.id
				""", (rs, row) -> {
			Timestamp endsAt = rs.getTimestamp("access_ends_at");
			return new AdmissionItemView(rs.getString("scope"), rs.getObject("exam_id", UUID.class),
					rs.getObject("module_id", UUID.class), rs.getString("title_en"), rs.getString("title_bn"),
					rs.getObject("package_exam_count", Integer.class), endsAt == null ? null : endsAt.toInstant());
		}, admission.getId(), Timestamp.from(clock.instant()));
	}

	/** The batch of the student's routine: their own admission's batch, once onboarded. 404 when there is none (plan 5). */
	@Transactional(readOnly = true)
	public UUID ownBatchId(UUID studentId) {
		OfflineAdmission admission = ownAdmission(studentId);
		UUID batchId = afterOnboarding(admission) ? admission.getBatchId() : null;
		if (batchId == null) {
			throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
		}
		return batchId;
	}

	/** Saves whatever fields the body names, without checking them (partial saves are allowed). */
	@Transactional
	public AdmissionView saveDraft(UUID studentId, JsonNode body) {
		OfflineAdmission admission = open(studentId);
		requireFormOpen(admission);
		apply(body, admission);
		admission.setDraftSavedAt(clock.instant());
		admissions.saveAndFlush(admission);
		return view(admission);
	}

	/** Refuses a change to the form unless it may be changed now (the photo and the form share this rule). */
	@Transactional(readOnly = true)
	public void requireOpenForm(UUID studentId) {
		requireFormOpen(open(studentId));
	}

	/**
	 * Checks every required field and the photo. When all pass, the form is final for this round: the status moves to
	 * SUBMITTED, the submitted form is kept as a row of history, and every admin is told once.
	 */
	@Transactional
	public AdmissionView submit(UUID studentId) {
		OfflineAdmission admission = open(studentId);
		requireFormOpen(admission);
		List<FieldErrorDetail> problems = formProblems(admission, photos.existsById(studentId));
		if (!problems.isEmpty()) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.", problems);
		}
		// Every number has passed the check, so it is stored in the normal +880 form.
		admission.setMobile(PhoneNumbers.normalize(admission.getMobile()));
		admission.setEmergencyMobile(PhoneNumbers.normalize(admission.getEmergencyMobile()));
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
		row.setSubmittedBy(studentId);
		row.setFormJson(formJson(form(admission)));
		submissions.saveAndFlush(row);

		audit.record(resubmit ? AuditRecorder.OFFLINE_ADMISSION_RESUBMITTED : AuditRecorder.OFFLINE_ADMISSION_SUBMITTED,
				AuditRecorder.OFFLINE_ADMISSION_ENTITY, admission.getId());
		notifyAdmins(admission, resubmit ? StaffNotificationKind.ADMISSION_RESUBMITTED : StaffNotificationKind.ADMISSION_SUBMITTED);
		return view(admission);
	}

	private OfflineAdmission open(UUID studentId) {
		return admissions.findFirstByStudentIdAndStatusNot(studentId, OfflineAdmission.CLOSED)
			.orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found."));
	}

	/** The read path's admission: the open one, else the latest closed one. Only the read routes use it. */
	private OfflineAdmission ownAdmission(UUID studentId) {
		return admissions.findFirstByStudentIdAndStatusNot(studentId, OfflineAdmission.CLOSED)
			.or(() -> admissions.findFirstByStudentIdAndStatusOrderByCreatedAtDesc(studentId, OfflineAdmission.CLOSED))
			.orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found."));
	}

	/** The batch, fee and dates are shown to the student only once the admission is onboarded or closed (plan 6.4). */
	private static boolean afterOnboarding(OfflineAdmission a) {
		return OfflineAdmission.ONBOARDED.equals(a.getStatus()) || OfflineAdmission.CLOSED.equals(a.getStatus());
	}

	private static void requireFormOpen(OfflineAdmission admission) {
		if (!FORM_OPEN.contains(admission.getStatus())) {
			throw new ApiException(ErrorCode.ADMISSION_STATE, HttpStatus.CONFLICT,
					"The admission form cannot be changed right now.");
		}
	}

	/**
	 * Every problem with the form, one entry per field. It changes nothing, so the admin ready check and the admin detail can call it
	 * too. The submit normalises the phone numbers once this returns nothing (plan 6.2, 4.2).
	 */
	List<FieldErrorDetail> formProblems(OfflineAdmission a, boolean hasPhoto) {
		List<FieldErrorDetail> problems = new ArrayList<>();
		required(problems, "fullName", a.getFullName());
		required(problems, "fatherName", a.getFatherName());
		required(problems, "motherName", a.getMotherName());
		required(problems, "email", a.getEmail());
		required(problems, "gender", a.getGender());
		required(problems, "university", a.getUniversity());
		required(problems, "semesterBatch", a.getSemesterBatch());
		required(problems, "nid", a.getNid());
		required(problems, "mobile", a.getMobile());
		required(problems, "emergencyMobile", a.getEmergencyMobile());
		required(problems, "hallMess", a.getHallMess());
		required(problems, "thana", a.getThana());
		required(problems, "district", a.getDistrict());
		required(problems, "presentAddress", a.getPresentAddress());
		required(problems, "permanentAddress", a.getPermanentAddress());
		if (a.getDateOfBirth() == null) {
			problems.add(new FieldErrorDetail("dateOfBirth", "Enter your date of birth."));
		}
		else if (a.getDateOfBirth().isAfter(LocalDate.now(clock))) {
			problems.add(new FieldErrorDetail("dateOfBirth", "The date of birth cannot be in the future."));
		}
		if (a.getEmail() != null && !EMAIL.matcher(a.getEmail()).matches()) {
			problems.add(new FieldErrorDetail("email", "Enter a valid email address."));
		}
		if (a.getGender() != null && !GENDERS.contains(a.getGender())) {
			problems.add(new FieldErrorDetail("gender", "Choose male or female."));
		}
		if (a.getNid() != null && !NID.matcher(a.getNid()).matches()) {
			problems.add(new FieldErrorDetail("nid", "The national ID number must have 10, 13 or 17 digits."));
		}
		phone(problems, "mobile", a.getMobile());
		phone(problems, "emergencyMobile", a.getEmergencyMobile());
		if (!hasPhoto) {
			problems.add(new FieldErrorDetail("photo", "Add your photo. Take one with the camera or choose one from the gallery."));
		}
		checkLengths(problems, a);
		return problems;
	}

	private static void required(List<FieldErrorDetail> problems, String field, String value) {
		if (value == null || value.isBlank()) {
			problems.add(new FieldErrorDetail(field, "This field is required."));
		}
	}

	/** Returns the normalised number, or null after recording a problem (the caller then keeps nothing). */
	private static String phone(List<FieldErrorDetail> problems, String field, String raw) {
		if (raw == null || raw.isBlank()) {
			return raw;
		}
		try {
			return PhoneNumbers.normalize(raw);
		}
		catch (ApiException invalid) {
			problems.add(new FieldErrorDetail(field, "Enter a Bangladeshi mobile number, such as 01711000012."));
			return raw;
		}
	}

	private static void checkLengths(List<FieldErrorDetail> problems, OfflineAdmission a) {
		Map<String, String> values = Map.ofEntries(Map.entry("fullName", nz(a.getFullName())),
				Map.entry("fatherName", nz(a.getFatherName())), Map.entry("motherName", nz(a.getMotherName())),
				Map.entry("email", nz(a.getEmail())), Map.entry("university", nz(a.getUniversity())),
				Map.entry("semesterBatch", nz(a.getSemesterBatch())), Map.entry("hallMess", nz(a.getHallMess())),
				Map.entry("thana", nz(a.getThana())), Map.entry("district", nz(a.getDistrict())),
				Map.entry("presentAddress", nz(a.getPresentAddress())),
				Map.entry("permanentAddress", nz(a.getPermanentAddress())));
		for (Map.Entry<String, Integer> limit : MAX_LENGTH.entrySet()) {
			if (values.get(limit.getKey()).length() > limit.getValue()) {
				problems.add(new FieldErrorDetail(limit.getKey(), "This is longer than " + limit.getValue() + " characters."));
			}
		}
	}

	private static String nz(String value) {
		return value == null ? "" : value;
	}

	/** Copies each field the body names onto the admission. A field left out keeps its saved value. */
	private static void apply(JsonNode body, OfflineAdmission a) {
		if (body == null || !body.isObject()) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
		}
		if (body.has("fullName")) a.setFullName(text(body, "fullName"));
		if (body.has("fatherName")) a.setFatherName(text(body, "fatherName"));
		if (body.has("motherName")) a.setMotherName(text(body, "motherName"));
		if (body.has("dateOfBirth")) a.setDateOfBirth(date(body, "dateOfBirth"));
		if (body.has("email")) a.setEmail(text(body, "email"));
		if (body.has("gender")) a.setGender(text(body, "gender") == null ? null : text(body, "gender").toUpperCase(java.util.Locale.ROOT));
		if (body.has("university")) a.setUniversity(text(body, "university"));
		if (body.has("semesterBatch")) a.setSemesterBatch(text(body, "semesterBatch"));
		if (body.has("nid")) a.setNid(text(body, "nid"));
		if (body.has("mobile")) a.setMobile(text(body, "mobile"));
		if (body.has("emergencyMobile")) a.setEmergencyMobile(text(body, "emergencyMobile"));
		if (body.has("hallMess")) a.setHallMess(text(body, "hallMess"));
		if (body.has("thana")) a.setThana(text(body, "thana"));
		if (body.has("district")) a.setDistrict(text(body, "district"));
		if (body.has("presentAddress")) a.setPresentAddress(text(body, "presentAddress"));
		if (body.has("permanentAddress")) a.setPermanentAddress(text(body, "permanentAddress"));
	}

	private static String text(JsonNode body, String key) {
		JsonNode node = body.get(key);
		if (node == null || node.isNull()) {
			return null;
		}
		String value = node.asText().trim();
		return value.isEmpty() ? null : value;
	}

	private static LocalDate date(JsonNode body, String key) {
		String value = text(body, key);
		if (value == null) {
			return null;
		}
		try {
			return LocalDate.parse(value);
		}
		catch (java.time.format.DateTimeParseException invalid) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.",
					List.of(new FieldErrorDetail(key, "Enter the date as year-month-day.")));
		}
	}

	private void notifyAdmins(OfflineAdmission admission, StaffNotificationKind kind) {
		String name = admission.getFullName() == null ? "" : StaffNotificationWriter.fitText(admission.getFullName());
		List<UUID> recipients = jdbc.queryForList(
				"select id from users where role in ('MASTER_ADMIN', 'SYSTEM_ADMIN') and status = 'ACTIVE' and deleted_at is null",
				UUID.class);
		String link = "/staff/admissions/" + admission.getId();
		for (UUID recipient : recipients) {
			notices.notify(recipient, kind, Map.of("personName", name), link,
					kind.name() + ":" + admission.getId() + ":" + admission.getSubmitCount() + ":" + recipient);
		}
	}

	private String formJson(Form form) {
		return json.writeValueAsString(form);
	}

	private AdmissionView view(OfflineAdmission a) {
		boolean after = afterOnboarding(a);
		BatchLabel batch = after ? batchLabel(a.getBatchId()) : BatchLabel.NONE;
		return new AdmissionView(a.getId(), a.getStatus(), a.getChangesReason(), a.getSubmittedAt(), a.getSubmitCount(),
				photos.existsById(a.getStudentId()), form(a), batch.nameEn(), batch.nameBn(), after ? a.getMonthlyFee() : null,
				after ? a.getAdmittedOn() : null, after ? a.getCourseEndsOn() : null, batch.hasRoutine());
	}

	/** The batch's names and whether it has a routine file. A null batch gives {@link BatchLabel#NONE}. */
	private BatchLabel batchLabel(UUID batchId) {
		if (batchId == null) {
			return BatchLabel.NONE;
		}
		return jdbc.query("select name_en, name_bn, routine_stored_name is not null as has_routine from offline_batches where id = ?",
				(rs, row) -> new BatchLabel(rs.getString("name_en"), rs.getString("name_bn"), rs.getBoolean("has_routine")), batchId)
			.stream()
			.findFirst()
			.orElse(BatchLabel.NONE);
	}

	private record BatchLabel(String nameEn, String nameBn, boolean hasRoutine) {

		static final BatchLabel NONE = new BatchLabel(null, null, false);

	}

	static Form form(OfflineAdmission a) {
		return new Form(a.getFullName(), a.getFatherName(), a.getMotherName(), a.getDateOfBirth(), a.getEmail(),
				a.getGender(), a.getUniversity(), a.getSemesterBatch(), a.getNid(), a.getMobile(), a.getEmergencyMobile(),
				a.getHallMess(), a.getThana(), a.getDistrict(), a.getPresentAddress(), a.getPermanentAddress());
	}

}
