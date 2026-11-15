package com.coachingcenter.api.offline;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.error.FieldErrorDetail;
import com.coachingcenter.api.files.ExamImageStorage;
import com.coachingcenter.api.files.LocalFileStorage;
import com.coachingcenter.api.files.PdfUploads;
import com.coachingcenter.api.users.StudentPhotoService;
import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.databind.JsonNode;

/**
 * Offline batches (plan section 4.1, D11, C10). The Master Admin creates, edits and archives a batch. Staff and the Master Admin see
 * the batches and upload the routine. A batch's fee and end date are only defaults: each admission keeps its own copy, so an edit
 * never changes an admission silently.
 *
 * <p>The public catalogue (V93, public-admission plan section 3) reads the same batches: the cards, the batch page, its photo and the
 * student copy of its routine. An archived batch is never shown publicly.
 */
@Service
public class OfflineBatchService {

	/** The largest routine PDF, the same as a package routine (V78). */
	public static final long MAX_ROUTINE_BYTES = 20L * 1024 * 1024;

	/** The largest batch photo accepted before processing (the brief sets 5 MB). */
	public static final long MAX_PHOTO_BYTES = 5L * 1024 * 1024;

	public static final String ACTIVE = "ACTIVE";

	public static final String ARCHIVED = "ARCHIVED";

	private static final String PDF = "application/pdf";

	private static final String FALLBACK_NAME = "routine.pdf";

	private static final int MAX_NAME = 200;

	/** The longest description in either language (V93). */
	private static final int MAX_DESCRIPTION = 2000;

	/** The length of a class when a routine gives only its start time (plan section 5, step 3). */
	private static final int DEFAULT_CLASS_MINUTES = 80;

	private final OfflineBatchRepository batches;

	private final PdfUploads uploads;

	private final LocalFileStorage fileStorage;

	private final AuditRecorder audit;

	private final OfflineAdmissionNotices notices;

	private final JdbcTemplate jdbc;

	private final Clock clock;

	private final ExamImageStorage images;

	public OfflineBatchService(OfflineBatchRepository batches, PdfUploads uploads, LocalFileStorage fileStorage,
			AuditRecorder audit, OfflineAdmissionNotices notices, JdbcTemplate jdbc, Clock clock, ExamImageStorage images) {
		this.batches = batches;
		this.uploads = uploads;
		this.fileStorage = fileStorage;
		this.audit = audit;
		this.notices = notices;
		this.jdbc = jdbc;
		this.clock = clock;
		this.images = images;
	}

	/** Creates an active batch. The name in both languages, the end date and the default fee are required. */
	@Transactional
	public BatchView create(UUID actorId, JsonNode body) {
		List<FieldErrorDetail> problems = new ArrayList<>();
		String nameEn = name(body, "nameEn", problems);
		String nameBn = name(body, "nameBn", problems);
		LocalDate startsOn = date(body, "startsOn", problems);
		LocalDate endsOn = date(body, "endsOn", problems);
		if (endsOn == null && !has(body, "endsOn")) {
			problems.add(new FieldErrorDetail("endsOn", "Enter the end date."));
		}
		BigDecimal fee = fee(body, "courseFee", problems);
		if (fee == null && !has(body, "courseFee")) {
			problems.add(new FieldErrorDetail("courseFee", "Enter the course fee."));
		}
		Integer installments = OfflineSupport.installments(body, "defaultInstallments", problems);
		Integer classMinutes = has(body, "defaultClassMinutes") ? classMinutes(body, problems) : null;
		if (startsOn != null && endsOn != null && endsOn.isBefore(startsOn)) {
			problems.add(new FieldErrorDetail("endsOn", "The end date cannot be before the start date."));
		}
		String descriptionEn = text(body, "descriptionEn", problems);
		String descriptionBn = text(body, "descriptionBn", problems);
		boolean admissionsOpen = !has(body, "admissionsOpen") || flag(body, "admissionsOpen", problems);
		Integer displayOrder = has(body, "displayOrder") ? order(body, problems) : 0;
		String rollStart = rollNumber(body, "rollStart", problems);
		String whatsappUrl = whatsappLink(body, problems);
		if (!problems.isEmpty()) {
			throw validation(problems);
		}
		Instant now = clock.instant();
		OfflineBatch batch = new OfflineBatch();
		batch.setId(UUID.randomUUID());
		batch.setNameEn(nameEn);
		batch.setNameBn(nameBn);
		batch.setStartsOn(startsOn);
		batch.setEndsOn(endsOn);
		batch.setCourseFee(fee);
		// Without a number from the admin, the batch is paid in as many instalments as it has months (course-fee-plan.md).
		batch.setDefaultInstallments(installments != null ? installments
				: OfflineFeeSchedule.fittingInstallments(startsOn != null ? startsOn : OfflineFeeSchedule.today(now), endsOn));
		batch.setDefaultMonthlyFee(firstInstallment(batch));
		batch.setDefaultClassMinutes(classMinutes == null ? DEFAULT_CLASS_MINUTES : classMinutes);
		batch.setDescriptionEn(descriptionEn);
		batch.setDescriptionBn(descriptionBn);
		batch.setAdmissionsOpen(admissionsOpen);
		batch.setDisplayOrder(displayOrder == null ? 0 : displayOrder);
		batch.setStatus(ACTIVE);
		batch.setRollStart(rollStart);
		batch.setWhatsappUrl(whatsappUrl);
		batch.setCreatedBy(actorId);
		batch.setCreatedAt(now);
		batch.setUpdatedAt(now);
		batches.saveAndFlush(batch);
		audit.record(AuditRecorder.OFFLINE_BATCH_CREATED, AuditRecorder.OFFLINE_BATCH_ENTITY, batch.getId());
		return view(batch);
	}

	/** Changes the fields the body names. An archived batch cannot be changed, and a published one only once it is a draft again. */
	@Transactional
	public BatchView update(UUID id, JsonNode body) {
		OfflineBatch batch = find(id);
		requireActive(batch);
		requireDraft(batch);
		List<FieldErrorDetail> problems = new ArrayList<>();
		if (has(body, "rollStart")) {
			String rollStart = rollNumber(body, "rollStart", problems);
			if (problems.isEmpty()) {
				// Once a student has a roll in the batch, the starting roll cannot move: the next suggestion would change under them.
				if (!java.util.Objects.equals(rollStart, batch.getRollStart()) && batchHasRolls(id)) {
					problems.add(new FieldErrorDetail("rollStart",
							"Students in this batch already have roll numbers, so the starting roll cannot change."));
				}
				else {
					batch.setRollStart(rollStart);
				}
			}
		}
		if (has(body, "nameEn")) batch.setNameEn(name(body, "nameEn", problems));
		if (has(body, "nameBn")) batch.setNameBn(name(body, "nameBn", problems));
		if (has(body, "startsOn")) batch.setStartsOn(date(body, "startsOn", problems));
		if (has(body, "endsOn")) {
			LocalDate endsOn = date(body, "endsOn", problems);
			if (endsOn == null) {
				problems.add(new FieldErrorDetail("endsOn", "Enter the end date."));
			}
			else {
				batch.setEndsOn(endsOn);
			}
		}
		if (has(body, "courseFee")) {
			BigDecimal fee = fee(body, "courseFee", problems);
			if (fee != null) {
				batch.setCourseFee(fee);
			}
		}
		if (has(body, "defaultInstallments")) {
			Integer installments = OfflineSupport.installments(body, "defaultInstallments", problems);
			if (installments != null) {
				batch.setDefaultInstallments(installments);
			}
		}
		batch.setDefaultMonthlyFee(firstInstallment(batch));
		if (has(body, "defaultClassMinutes")) {
			Integer classMinutes = classMinutes(body, problems);
			if (classMinutes != null) {
				batch.setDefaultClassMinutes(classMinutes);
			}
		}
		// An explicit null or blank clears a description; an absent key leaves it alone.
		if (body != null && body.has("descriptionEn")) batch.setDescriptionEn(text(body, "descriptionEn", problems));
		if (body != null && body.has("descriptionBn")) batch.setDescriptionBn(text(body, "descriptionBn", problems));
		if (has(body, "admissionsOpen")) batch.setAdmissionsOpen(flag(body, "admissionsOpen", problems));
		// An explicit null or blank removes the WhatsApp link; an absent key leaves it alone.
		if (body != null && body.has("whatsappUrl")) batch.setWhatsappUrl(whatsappLink(body, problems));
		if (has(body, "displayOrder")) {
			Integer displayOrder = order(body, problems);
			if (displayOrder != null) {
				batch.setDisplayOrder(displayOrder);
			}
		}
		if (batch.getStartsOn() != null && batch.getEndsOn() != null && batch.getEndsOn().isBefore(batch.getStartsOn())) {
			problems.add(new FieldErrorDetail("endsOn", "The end date cannot be before the start date."));
		}
		if (!problems.isEmpty()) {
			throw validation(problems);
		}
		batch.setUpdatedAt(clock.instant());
		batches.saveAndFlush(batch);
		audit.record(AuditRecorder.OFFLINE_BATCH_UPDATED, AuditRecorder.OFFLINE_BATCH_ENTITY, id);
		return view(batch);
	}

	/**
	 * Archives a batch. It is refused while the batch has onboarded students (R5), and the message names how many. An archived batch
	 * cannot be chosen for a new admission.
	 */
	@Transactional
	public BatchView archive(UUID id) {
		OfflineBatch batch = find(id);
		if (ARCHIVED.equals(batch.getStatus())) {
			return view(batch);
		}
		long onboarded = onboardedCount(id);
		if (onboarded > 0) {
			throw new ApiException(ErrorCode.ADMISSION_STATE, HttpStatus.CONFLICT, "This batch has " + onboarded
					+ " onboarded " + (onboarded == 1 ? "student" : "students") + " and cannot be archived.");
		}
		batch.setStatus(ARCHIVED);
		batch.setUpdatedAt(clock.instant());
		batches.saveAndFlush(batch);
		audit.record(AuditRecorder.OFFLINE_BATCH_ARCHIVED, AuditRecorder.OFFLINE_BATCH_ENTITY, id);
		return view(batch);
	}

	/** The batches, active ones first, then the display order and the name. {@code status} narrows them to one state; null lists both. */
	@Transactional(readOnly = true)
	public List<BatchView> list(String status) {
		List<OfflineBatch> found = status == null || status.isBlank() ? batches.findAll() : batches.findByStatusOrderByNameEnAsc(
				status.trim().toUpperCase(Locale.ROOT));
		List<BatchView> views = new ArrayList<>();
		for (OfflineBatch batch : found) {
			views.add(view(batch));
		}
		views.sort(java.util.Comparator.comparing(BatchView::status).thenComparingInt(BatchView::displayOrder)
				.thenComparing(BatchView::nameEn));
		return views;
	}

	@Transactional(readOnly = true)
	public BatchView get(UUID id) {
		return view(find(id));
	}

	/** The students admitted to the batch, every state, newest admission first. A closed admission stays listed as history. */
	@Transactional(readOnly = true)
	public List<BatchStudent> students(UUID id) {
		find(id);
		return jdbc.query("""
				select a.id, a.student_id, a.full_name, a.mobile, a.status, a.created_at, a.roll_no
				from offline_admissions a where a.batch_id = ?
				order by a.created_at desc, a.id
				""", (rs, row) -> new BatchStudent(rs.getObject("id", UUID.class), rs.getObject("student_id", UUID.class),
				rs.getString("full_name"), rs.getString("mobile"), rs.getString("status"), rs.getString("roll_no")), id);
	}

	/**
	 * Stores a new routine PDF, replacing the old one, the same way a package routine is stored (V78). Every onboarded student of the
	 * batch gets one in-app notice for this upload (plan section 7). No email is sent for it.
	 */
	@Transactional
	public BatchView uploadRoutine(UUID id, MultipartFile file, UUID actorId) {
		OfflineBatch batch = find(id);
		LocalFileStorage.StoredFile stored = uploads.store(file, MAX_ROUTINE_BYTES);
		String previous = batch.getRoutineStoredName();
		fileStorage.commitReplacement(stored.storedFileName(), previous);
		Instant now = clock.instant();
		batch.setRoutineStoredName(stored.storedFileName());
		batch.setRoutineOriginalName(originalName(stored.originalFileName()));
		batch.setRoutineByteSize(stored.byteSize());
		batch.setRoutineContentType(PDF);
		batch.setRoutineUploadedBy(actorId);
		batch.setRoutineUploadedAt(now);
		batch.setUpdatedAt(now);
		batch.setRoutineStudentsNotified(onboardedStudents(id).size());
		batches.saveAndFlush(batch);
		audit.record(AuditRecorder.OFFLINE_BATCH_ROUTINE_UPLOADED, AuditRecorder.OFFLINE_BATCH_ENTITY, id);
		notifyOnboardedStudents(id, now);
		return view(batch);
	}

	/** The students onboarded into the batch right now. */
	List<UUID> onboardedStudents(UUID batchId) {
		return jdbc.queryForList("select student_id from offline_admissions where batch_id = ? and status = 'ONBOARDED'", UUID.class,
				batchId);
	}

	/**
	 * Tells each onboarded student of the batch that its routine changed: one BATCH_ROUTINE_UPDATED notice each (plan section 7, F12).
	 * Used by the routine upload and by the routine confirm. Returns how many students were onboarded.
	 */
	int notifyOnboardedStudents(UUID batchId, Instant now) {
		List<UUID> students = onboardedStudents(batchId);
		for (UUID student : students) {
			notices.notice(student, OfflineAdmissionNotices.BATCH_ROUTINE_UPDATED, batchId + ":" + now.toEpochMilli() + ":" + student);
		}
		return students.size();
	}

	/** The routine PDF's bytes, or 404 when the batch has none. The caller holds the routine permission. */
	@Transactional(readOnly = true)
	public RoutineFile openRoutine(UUID id) {
		OfflineBatch batch = find(id);
		if (batch.getRoutineStoredName() == null) {
			throw notFound();
		}
		Path path = fileStorage.resolve(batch.getRoutineStoredName());
		if (!Files.exists(path)) {
			throw notFound();
		}
		try {
			InputStream body = Files.newInputStream(path);
			String name = batch.getRoutineOriginalName() == null ? FALLBACK_NAME : batch.getRoutineOriginalName();
			return new RoutineFile(body, name, batch.getRoutineByteSize() == null ? 0L : batch.getRoutineByteSize());
		}
		catch (IOException exception) {
			throw notFound();
		}
	}

	/** The number of students onboarded into the batch and still on it (a closed admission no longer counts). */
	long onboardedCount(UUID batchId) {
		Long count = jdbc.queryForObject("select count(*) from offline_admissions where batch_id = ? and status = 'ONBOARDED'", Long.class,
				batchId);
		return count == null ? 0L : count;
	}

	/** The WhatsApp link in the body: blank or null means none; otherwise a group or channel invite link (WhatsAppLinks). */
	private static String whatsappLink(JsonNode body, List<FieldErrorDetail> problems) {
		if (body == null || !body.has("whatsappUrl") || body.get("whatsappUrl").isNull()) {
			return null;
		}
		String url = com.coachingcenter.api.common.web.WhatsAppLinks.clean(body.get("whatsappUrl").asText());
		String problem = com.coachingcenter.api.common.web.WhatsAppLinks.problem(url);
		if (problem != null) {
			problems.add(new FieldErrorDetail("whatsappUrl", problem));
			return null;
		}
		return url;
	}

	/** Whether any student of the batch has a roll number yet. */
	private boolean batchHasRolls(UUID batchId) {
		Boolean any = jdbc.queryForObject("select exists(select 1 from offline_admissions where batch_id = ? and roll_no is not null)",
				Boolean.class, batchId);
		return Boolean.TRUE.equals(any);
	}

	/** A roll number in the body: blank or absent means none; otherwise 4 to 12 digits (rule O2). */
	static String rollNumber(JsonNode body, String key, List<FieldErrorDetail> problems) {
		if (!has(body, key) || body.get(key).isNull()) {
			return null;
		}
		String value = body.get(key).asText().trim();
		if (value.isEmpty()) {
			return null;
		}
		if (!value.matches("\\d{4,12}")) {
			problems.add(new FieldErrorDetail(key, "Use 4 to 12 digits for the roll number."));
			return null;
		}
		return value;
	}

	/** The first instalment of the batch's course fee: what the previous release reads as the monthly fee (V100). */
	private static BigDecimal firstInstallment(OfflineBatch batch) {
		return OfflineFeeSchedule.split(batch.getCourseFee(), batch.getDefaultInstallments()).get(0);
	}

	BatchView view(OfflineBatch batch) {
		return new BatchView(batch.getId(), batch.getNameEn(), batch.getNameBn(), batch.getStartsOn(), batch.getEndsOn(),
				batch.getCourseFee(), batch.getDefaultInstallments(), batch.getStatus(), onboardedCount(batch.getId()),
				batch.getRoutineStoredName() != null, batch.getRoutineOriginalName(), batch.getRoutineUploadedAt(),
				uploaderName(batch.getRoutineUploadedBy()), batch.getRoutineStudentsNotified(), batch.getDefaultClassMinutes(),
				batch.getDescriptionEn(), batch.getDescriptionBn(), batch.getPhotoStoredName() != null, batch.isAdmissionsOpen(),
				batch.getDisplayOrder() == null ? 0 : batch.getDisplayOrder(), batch.getRollStart(), batch.getWhatsappUrl(),
				batch.isPublished(), batch.getPublishedAt(), batch.getPhotoUploadedAt());
	}

	/** The public cards: the published batches that are not archived, the lowest display order first (plan section 3.2, V103). */
	@Transactional(readOnly = true)
	public List<PublicBatchView> publicList() {
		List<PublicBatchView> views = new ArrayList<>();
		for (OfflineBatch batch : batches.findByStatusOrderByDisplayOrderAscNameEnAsc(ACTIVE)) {
			if (!batch.isPublished()) {
				continue;
			}
			views.add(publicView(batch));
		}
		return views;
	}

	/** One public batch. An archived batch is not shown, so it answers 404 like a missing one. */
	@Transactional(readOnly = true)
	public PublicBatchView publicGet(UUID id) {
		return publicView(publicFind(id));
	}

	/** Answers 404 unless the batch exists and is not archived. The routine route uses it before it reads the file. */
	@Transactional(readOnly = true)
	public void requirePublic(UUID id) {
		publicFind(id);
	}

	/** The batch photo for the public cards, or 404 when there is none or the batch is archived. */
	@Transactional(readOnly = true)
	public PhotoFile openPhoto(UUID id) {
		return photoOf(publicFind(id));
	}

	/**
	 * The batch photo for the staff screens: any batch, draft or archived included, since staff edit and review it there. 404 when it has
	 * no photo.
	 */
	@Transactional(readOnly = true)
	public PhotoFile openStaffPhoto(UUID id) {
		return photoOf(find(id));
	}

	private PhotoFile photoOf(OfflineBatch batch) {
		if (batch.getPhotoStoredName() == null) {
			throw notFound();
		}
		Path path = fileStorage.resolve(batch.getPhotoStoredName());
		if (!Files.exists(path)) {
			throw notFound();
		}
		try {
			return new PhotoFile(Files.newInputStream(path), batch.getPhotoContentType(),
					batch.getPhotoByteSize() == null ? 0L : batch.getPhotoByteSize());
		}
		catch (IOException exception) {
			throw notFound();
		}
	}

	private OfflineBatch publicFind(UUID id) {
		OfflineBatch batch = find(id);
		// A draft is not shown to students (V103), like an archived batch.
		if (!ACTIVE.equals(batch.getStatus()) || !batch.isPublished()) {
			throw notFound();
		}
		return batch;
	}

	private PublicBatchView publicView(OfflineBatch batch) {
		boolean routine = Boolean.TRUE.equals(jdbc.queryForObject("""
				select exists (select 1 from offline_batch_routines
				where batch_id = ? and status = ? and student_file is not null and legacy_original = false)
				""", Boolean.class, batch.getId(), OfflineBatchRoutine.STATUS_CONFIRMED));
		String photo = batch.getPhotoStoredName() == null || batch.getPhotoUploadedAt() == null ? null
				: "/api/v1/public/offline-batches/" + batch.getId() + "/photo?v=" + batch.getPhotoUploadedAt().toEpochMilli();
		return new PublicBatchView(batch.getId(), batch.getNameEn(), batch.getNameBn(), batch.getDescriptionEn(),
				batch.getDescriptionBn(), batch.getStartsOn(), batch.getEndsOn(), batch.getCourseFee(), batch.getDefaultInstallments(),
				batch.isAdmissionsOpen(), photo, routine);
	}

	/** Stores a new batch photo, replacing the old one. Re-encoded and resized like the profile photo (V93). */
	@Transactional
	public BatchView uploadPhoto(UUID id, MultipartFile file, UUID actorId) {
		OfflineBatch batch = find(id);
		requireActive(batch);
		requireDraft(batch);
		if (file == null || file.isEmpty()) {
			throw validation(List.of(new FieldErrorDetail("file", "Choose a photo.")));
		}
		if (file.getSize() > MAX_PHOTO_BYTES) {
			throw tooLarge();
		}
		if (!StudentPhotoService.hasJpegPngOrWebpSignature(file)) {
			throw validation(List.of(new FieldErrorDetail("file", "Upload a JPEG, PNG or WebP picture.")));
		}
		ExamImageStorage.StoredExamImage stored = images.store(file);
		if (stored.byteSize() > MAX_PHOTO_BYTES) {
			images.delete(stored.storedFileName());
			throw tooLarge();
		}
		String previous = batch.getPhotoStoredName();
		fileStorage.commitReplacement(stored.storedFileName(), previous);
		Instant now = clock.instant();
		batch.setPhotoStoredName(stored.storedFileName());
		batch.setPhotoContentType(stored.contentType());
		batch.setPhotoByteSize(stored.byteSize());
		batch.setPhotoUploadedBy(actorId);
		batch.setPhotoUploadedAt(now);
		batch.setUpdatedAt(now);
		batches.saveAndFlush(batch);
		audit.record(AuditRecorder.OFFLINE_BATCH_PHOTO_UPLOADED, AuditRecorder.OFFLINE_BATCH_ENTITY, id);
		return view(batch);
	}

	/** Removes the batch photo. A batch with no photo is left unchanged. */
	@Transactional
	public BatchView removePhoto(UUID id) {
		OfflineBatch batch = find(id);
		requireActive(batch);
		requireDraft(batch);
		String previous = batch.getPhotoStoredName();
		if (previous == null) {
			return view(batch);
		}
		batch.setPhotoStoredName(null);
		batch.setPhotoContentType(null);
		batch.setPhotoByteSize(null);
		batch.setPhotoUploadedBy(null);
		batch.setPhotoUploadedAt(null);
		batch.setUpdatedAt(clock.instant());
		batches.saveAndFlush(batch);
		fileStorage.commitReplacement(null, previous);
		audit.record(AuditRecorder.OFFLINE_BATCH_PHOTO_REMOVED, AuditRecorder.OFFLINE_BATCH_ENTITY, id);
		return view(batch);
	}

	/** An archived batch cannot take a photo, the same as it cannot be edited. */
	/** The batch form is locked while the batch is published (V103): the Master Admin moves it to draft to edit it. */
	private static void requireDraft(OfflineBatch batch) {
		if (batch.isPublished()) {
			throw new ApiException(ErrorCode.ADMISSION_STATE, HttpStatus.CONFLICT,
					"This batch is published. Move it to draft to edit it.");
		}
	}

	/**
	 * Publishes a draft batch (Master Admin, V103): students see it on the Admission page and admissions can choose it. Publishing a
	 * published batch changes nothing.
	 */
	@Transactional
	public BatchView publish(UUID id, UUID actorId) {
		OfflineBatch batch = find(id);
		requireActive(batch);
		if (!batch.isPublished()) {
			Instant now = clock.instant();
			batch.setPublishedAt(now);
			batch.setPublishedBy(actorId);
			batch.setUpdatedAt(now);
			batches.saveAndFlush(batch);
			audit.record(AuditRecorder.OFFLINE_BATCH_PUBLISHED, AuditRecorder.OFFLINE_BATCH_ENTITY, id);
		}
		return view(batch);
	}

	/**
	 * Moves a published batch back to draft (Master Admin, V103) so its form can be edited. It leaves the Admission page and new admissions
	 * cannot choose it; students already in it keep their batch, routine, fees and results.
	 */
	@Transactional
	public BatchView unpublish(UUID id) {
		OfflineBatch batch = find(id);
		requireActive(batch);
		if (batch.isPublished()) {
			batch.setPublishedAt(null);
			batch.setPublishedBy(null);
			batch.setUpdatedAt(clock.instant());
			batches.saveAndFlush(batch);
			audit.record(AuditRecorder.OFFLINE_BATCH_UNPUBLISHED, AuditRecorder.OFFLINE_BATCH_ENTITY, id);
		}
		return view(batch);
	}

	private static void requireActive(OfflineBatch batch) {
		if (!ACTIVE.equals(batch.getStatus())) {
			throw new ApiException(ErrorCode.ADMISSION_STATE, HttpStatus.CONFLICT, "This batch is archived, so it cannot be changed.");
		}
	}

	private static ApiException tooLarge() {
		return new ApiException(ErrorCode.FILE_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE, "File is too large.");
	}

	/** A description: optional, blank becomes null, at most 2,000 characters. */
	private static String text(JsonNode body, String key, List<FieldErrorDetail> problems) {
		if (body == null || !body.has(key) || body.get(key).isNull()) {
			return null;
		}
		String value = body.get(key).asText().trim();
		if (value.isEmpty()) {
			return null;
		}
		if (value.length() > MAX_DESCRIPTION) {
			problems.add(new FieldErrorDetail(key, "This is longer than " + MAX_DESCRIPTION + " characters."));
			return null;
		}
		return value;
	}

	/** A switch: a JSON true or false. Anything else is a problem for that field. */
	private static boolean flag(JsonNode body, String key, List<FieldErrorDetail> problems) {
		JsonNode node = body.get(key);
		if (node.isBoolean()) {
			return node.booleanValue();
		}
		problems.add(new FieldErrorDetail(key, "Choose on or off."));
		return false;
	}

	/** The display order: a whole number from 0 to 9999. */
	private static Integer order(JsonNode body, List<FieldErrorDetail> problems) {
		try {
			int order = Integer.parseInt(body.get("displayOrder").asText().trim());
			if (order < 0 || order > 9999) {
				problems.add(new FieldErrorDetail("displayOrder", "Enter an order from 0 to 9999."));
				return null;
			}
			return order;
		}
		catch (NumberFormatException exception) {
			problems.add(new FieldErrorDetail("displayOrder", "Enter the order as a number."));
			return null;
		}
	}

	/** The name of whoever uploaded the routine, from the staff or student profile. Null when there is no uploader or no name. */
	private String uploaderName(UUID userId) {
		if (userId == null) {
			return null;
		}
		return jdbc.queryForList("""
				select coalesce(ap.full_name, sp.full_name) from users u
				left join admin_profiles ap on ap.user_id = u.id
				left join student_profiles sp on sp.user_id = u.id
				where u.id = ?
				""", String.class, userId).stream().findFirst().orElse(null);
	}

	private OfflineBatch find(UUID id) {
		return batches.findById(id).orElseThrow(OfflineBatchService::notFound);
	}

	private static ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	private static ApiException validation(List<FieldErrorDetail> problems) {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.", problems);
	}

	private static boolean has(JsonNode body, String key) {
		return body != null && body.has(key) && !body.get(key).isNull();
	}

	private static String name(JsonNode body, String key, List<FieldErrorDetail> problems) {
		if (!has(body, key) || body.get(key).asText().isBlank()) {
			problems.add(new FieldErrorDetail(key, "This field is required."));
			return null;
		}
		String value = body.get(key).asText().trim();
		if (value.length() > MAX_NAME) {
			problems.add(new FieldErrorDetail(key, "This is longer than " + MAX_NAME + " characters."));
			return null;
		}
		return value;
	}

	private static LocalDate date(JsonNode body, String key, List<FieldErrorDetail> problems) {
		if (!has(body, key) || body.get(key).asText().isBlank()) {
			return null;
		}
		try {
			return LocalDate.parse(body.get(key).asText().trim());
		}
		catch (java.time.format.DateTimeParseException exception) {
			problems.add(new FieldErrorDetail(key, "Enter the date as year-month-day."));
			return null;
		}
	}

	private static BigDecimal fee(JsonNode body, String key, List<FieldErrorDetail> problems) {
		if (!has(body, key)) {
			return null;
		}
		try {
			BigDecimal fee = new BigDecimal(body.get(key).asText().trim());
			if (fee.signum() < 0) {
				problems.add(new FieldErrorDetail(key, "The fee cannot be negative."));
				return null;
			}
			return fee.setScale(2, java.math.RoundingMode.HALF_UP);
		}
		catch (NumberFormatException exception) {
			problems.add(new FieldErrorDetail(key, "Enter the fee as a number."));
			return null;
		}
	}

	/** The class length in minutes: a whole number from 15 to 300. A value that is not one is a problem for that field. */
	private static Integer classMinutes(JsonNode body, List<FieldErrorDetail> problems) {
		try {
			int minutes = Integer.parseInt(body.get("defaultClassMinutes").asText().trim());
			if (minutes < 15 || minutes > 300) {
				problems.add(new FieldErrorDetail("defaultClassMinutes", "Enter a class length from 15 to 300 minutes."));
				return null;
			}
			return minutes;
		}
		catch (NumberFormatException exception) {
			problems.add(new FieldErrorDetail("defaultClassMinutes", "Enter the class length as a number of minutes."));
			return null;
		}
	}

	/** The browser's name without its folders, trimmed to the column's length. Blank becomes null. */
	private static String originalName(String name) {
		if (name == null) {
			return null;
		}
		String base = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1).trim();
		if (base.isEmpty()) {
			return null;
		}
		return base.length() > 500 ? base.substring(0, 500) : base;
	}

	/**
	 * One batch as the staff and the Master Admin see it. {@code studentCount} counts the onboarded students. {@code routineUploadedBy}
	 * is the uploader's name, and {@code studentsNotified} is how many students the last routine upload notified (null when not known).
	 * {@code defaultClassMinutes} is the length of a class that a routine gives only a start time for (plan section 5). The last fields
	 * are the description, whether a photo is on file, the admissions switch and the display order (V93).
	 */
	public record BatchView(UUID id, String nameEn, String nameBn, LocalDate startsOn, LocalDate endsOn,
			BigDecimal courseFee, int defaultInstallments, String status, long studentCount, boolean routineUploaded, String routineFileName,
			Instant routineUploadedAt, @JsonInclude(JsonInclude.Include.ALWAYS) String routineUploadedBy,
			@JsonInclude(JsonInclude.Include.ALWAYS) Integer studentsNotified, int defaultClassMinutes, String descriptionEn,
			String descriptionBn, boolean photoUploaded, boolean admissionsOpen, int displayOrder, String rollStart, String whatsappUrl,
			boolean published, Instant publishedAt, Instant photoUploadedAt) {
	}

	/**
	 * One batch on the public cards and the batch page. The photo and the routine are links the page follows, so the view carries
	 * only whether a routine is there. No counts, no staff fields.
	 */
	public record PublicBatchView(UUID id, String nameEn, String nameBn, String descriptionEn, String descriptionBn,
			LocalDate startsOn, LocalDate endsOn, BigDecimal courseFee, int installments, boolean admissionsOpen, String photoUrl,
			boolean routineAvailable) {
	}

	/** A student admitted to the batch. The mobile is shown to staff only, since these routes are staff routes. */
	public record BatchStudent(UUID admissionId, UUID studentId, String fullName, String mobile, String status, String roll) {
	}

	/** The routine PDF bytes. The response writes the stream and closes it. */
	public record RoutineFile(InputStream body, String fileName, long byteSize) {
	}

	/** The batch photo bytes. The response writes the stream and closes it. */
	public record PhotoFile(InputStream body, String contentType, long byteSize) {
	}

}
