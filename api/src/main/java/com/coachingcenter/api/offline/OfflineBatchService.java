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
import com.coachingcenter.api.files.LocalFileStorage;
import com.coachingcenter.api.files.PdfUploads;

import tools.jackson.databind.JsonNode;

/**
 * Offline batches (plan section 4.1, D11, C10). The Master Admin creates, edits and archives a batch. Staff and the Master Admin see
 * the batches and upload the routine. A batch's fee and end date are only defaults: each admission keeps its own copy, so an edit
 * never changes an admission silently.
 */
@Service
public class OfflineBatchService {

	/** The largest routine PDF, the same as a package routine (V78). */
	public static final long MAX_ROUTINE_BYTES = 20L * 1024 * 1024;

	public static final String ACTIVE = "ACTIVE";

	public static final String ARCHIVED = "ARCHIVED";

	private static final String PDF = "application/pdf";

	private static final String FALLBACK_NAME = "routine.pdf";

	private static final int MAX_NAME = 200;

	private final OfflineBatchRepository batches;

	private final PdfUploads uploads;

	private final LocalFileStorage fileStorage;

	private final AuditRecorder audit;

	private final OfflineAdmissionNotices notices;

	private final JdbcTemplate jdbc;

	private final Clock clock;

	public OfflineBatchService(OfflineBatchRepository batches, PdfUploads uploads, LocalFileStorage fileStorage,
			AuditRecorder audit, OfflineAdmissionNotices notices, JdbcTemplate jdbc, Clock clock) {
		this.batches = batches;
		this.uploads = uploads;
		this.fileStorage = fileStorage;
		this.audit = audit;
		this.notices = notices;
		this.jdbc = jdbc;
		this.clock = clock;
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
		BigDecimal fee = fee(body, "defaultMonthlyFee", problems);
		if (fee == null && !has(body, "defaultMonthlyFee")) {
			problems.add(new FieldErrorDetail("defaultMonthlyFee", "Enter the default monthly fee."));
		}
		if (startsOn != null && endsOn != null && endsOn.isBefore(startsOn)) {
			problems.add(new FieldErrorDetail("endsOn", "The end date cannot be before the start date."));
		}
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
		batch.setDefaultMonthlyFee(fee);
		batch.setStatus(ACTIVE);
		batch.setCreatedBy(actorId);
		batch.setCreatedAt(now);
		batch.setUpdatedAt(now);
		batches.saveAndFlush(batch);
		audit.record(AuditRecorder.OFFLINE_BATCH_CREATED, AuditRecorder.OFFLINE_BATCH_ENTITY, batch.getId());
		return view(batch);
	}

	/** Changes the fields the body names. An archived batch cannot be changed. */
	@Transactional
	public BatchView update(UUID id, JsonNode body) {
		OfflineBatch batch = find(id);
		if (!ACTIVE.equals(batch.getStatus())) {
			throw new ApiException(ErrorCode.ADMISSION_STATE, HttpStatus.CONFLICT, "This batch is archived, so it cannot be changed.");
		}
		List<FieldErrorDetail> problems = new ArrayList<>();
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
		if (has(body, "defaultMonthlyFee")) {
			BigDecimal fee = fee(body, "defaultMonthlyFee", problems);
			if (fee != null) {
				batch.setDefaultMonthlyFee(fee);
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

	/** The batches, active ones first and by name. {@code status} narrows them to one state; null lists both. */
	@Transactional(readOnly = true)
	public List<BatchView> list(String status) {
		List<OfflineBatch> found = status == null || status.isBlank() ? batches.findAll() : batches.findByStatusOrderByNameEnAsc(
				status.trim().toUpperCase(java.util.Locale.ROOT));
		List<BatchView> views = new ArrayList<>();
		for (OfflineBatch batch : found) {
			views.add(view(batch));
		}
		views.sort(java.util.Comparator.comparing(BatchView::status).thenComparing(BatchView::nameEn));
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
				select a.id, a.student_id, a.full_name, a.mobile, a.status, a.created_at
				from offline_admissions a where a.batch_id = ?
				order by a.created_at desc, a.id
				""", (rs, row) -> new BatchStudent(rs.getObject("id", UUID.class), rs.getObject("student_id", UUID.class),
				rs.getString("full_name"), rs.getString("mobile"), rs.getString("status")), id);
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
		batches.saveAndFlush(batch);
		audit.record(AuditRecorder.OFFLINE_BATCH_ROUTINE_UPLOADED, AuditRecorder.OFFLINE_BATCH_ENTITY, id);
		for (UUID student : jdbc.queryForList(
				"select student_id from offline_admissions where batch_id = ? and status = 'ONBOARDED'", UUID.class, id)) {
			notices.notice(student, OfflineAdmissionNotices.BATCH_ROUTINE_UPDATED, id + ":" + now.toEpochMilli() + ":" + student);
		}
		return view(batch);
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

	BatchView view(OfflineBatch batch) {
		return new BatchView(batch.getId(), batch.getNameEn(), batch.getNameBn(), batch.getStartsOn(), batch.getEndsOn(),
				batch.getDefaultMonthlyFee(), batch.getStatus(), onboardedCount(batch.getId()),
				batch.getRoutineStoredName() != null, batch.getRoutineOriginalName(), batch.getRoutineUploadedAt());
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

	/** One batch as the staff and the Master Admin see it. {@code studentCount} counts the onboarded students. */
	public record BatchView(UUID id, String nameEn, String nameBn, LocalDate startsOn, LocalDate endsOn,
			BigDecimal defaultMonthlyFee, String status, long studentCount, boolean routineUploaded, String routineFileName,
			Instant routineUploadedAt) {
	}

	/** A student admitted to the batch. The mobile is shown to staff only, since these routes are staff routes. */
	public record BatchStudent(UUID admissionId, UUID studentId, String fullName, String mobile, String status) {
	}

	/** The routine PDF bytes. The response writes the stream and closes it. */
	public record RoutineFile(InputStream body, String fileName, long byteSize) {
	}

}
