package com.coachingcenter.api.offline;

import static com.coachingcenter.api.offline.OfflineSupport.day;
import static com.coachingcenter.api.offline.OfflineSupport.has;
import static com.coachingcenter.api.offline.OfflineSupport.money;
import static com.coachingcenter.api.offline.OfflineSupport.notFound;
import static com.coachingcenter.api.offline.OfflineSupport.reason;
import static com.coachingcenter.api.offline.OfflineSupport.state;
import static com.coachingcenter.api.offline.OfflineSupport.uuid;
import static com.coachingcenter.api.offline.OfflineSupport.validation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import com.coachingcenter.api.common.web.PageResponse;
import com.coachingcenter.api.exams.LiveExamGrantAdminService;
import com.coachingcenter.api.exams.LiveExamModuleRepository;
import com.coachingcenter.api.exams.LiveExamRepository;
import com.coachingcenter.api.exams.OfflineAccessGrants;
import com.coachingcenter.api.files.LocalFileStorage;
import com.coachingcenter.api.staffnotifications.StaffNotificationKind;
import com.coachingcenter.api.staffnotifications.StaffNotificationWriter;
import com.coachingcenter.api.users.StudentPhotoRepository;
import com.coachingcenter.api.users.StudentPhotoService;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The staff side of an offline admission before onboarding (plan sections 4.2, 5, C14, C16): the queue and the detail, the setup, the
 * exams and packages, sending the form back, marking it ready, and the signed copy. The Master Admin's steps (onboarding, the terms
 * after it, and closing) are in {@link OfflineAdmissionOnboardingService}. Every change locks the admission row first, so two
 * changes run one after the other and the second one sees the first one's result.
 */
@Service
public class OfflineAdmissionAdminService {

	static final String AWAITING_FORM = OfflineAdmission.AWAITING_FORM;

	static final String SUBMITTED = OfflineAdmission.SUBMITTED;

	static final String CHANGES_REQUESTED = OfflineAdmission.CHANGES_REQUESTED;

	static final String READY = OfflineAdmission.READY;

	static final String ONBOARDED = OfflineAdmission.ONBOARDED;

	static final String CLOSED = OfflineAdmission.CLOSED;

	/** The states the queue can filter on. */
	static final List<String> STATES = List.of(AWAITING_FORM, SUBMITTED, CHANGES_REQUESTED, READY, ONBOARDED, CLOSED);

	/** The money flags the queue filters on (plan 4.3, D13): unpaid, overdue, and pending-approval. */
	static final List<String> FLAGS = List.of("unpaid", "overdue", "pending-approval");

	/** The largest signed copy that may be uploaded (D9), the same as a routine PDF. */
	static final long MAX_SIGNED_BYTES = 20L * 1024 * 1024;

	static final int MAX_PAGE_SIZE = 100;

	private static final String EXAM = "EXAM";

	private static final String MODULE = "MODULE";

	private static final String PUBLISHED = "PUBLISHED";

	private final OfflineAdmissionRepository admissions;

	private final OfflineAdmissionSubmissionRepository submissions;

	private final OfflineAdmissionItemRepository items;

	private final OfflineBatchRepository batches;

	private final OfflineAdmissionService form;

	private final StudentPhotoRepository photoRows;

	private final StudentPhotoService photos;

	private final LocalFileStorage fileStorage;

	private final StaffNotificationWriter staffNotices;

	private final OfflineAdmissionNotices studentNotices;

	private final OfflineAccessGrants grants;

	private final LiveExamRepository exams;

	private final LiveExamModuleRepository modules;

	private final AuditRecorder audit;

	private final JdbcTemplate jdbc;

	private final ObjectMapper json;

	private final Clock clock;

	private final OfflineAdmissionMoney money;

	public OfflineAdmissionAdminService(OfflineAdmissionRepository admissions, OfflineAdmissionSubmissionRepository submissions,
			OfflineAdmissionItemRepository items, OfflineBatchRepository batches, OfflineAdmissionService form,
			StudentPhotoRepository photoRows, StudentPhotoService photos, LocalFileStorage fileStorage,
			StaffNotificationWriter staffNotices, OfflineAdmissionNotices studentNotices, OfflineAccessGrants grants,
			LiveExamRepository exams, LiveExamModuleRepository modules, AuditRecorder audit, JdbcTemplate jdbc, ObjectMapper json,
			Clock clock, OfflineAdmissionMoney money) {
		this.money = money;
		this.admissions = admissions;
		this.submissions = submissions;
		this.items = items;
		this.batches = batches;
		this.form = form;
		this.photoRows = photoRows;
		this.photos = photos;
		this.fileStorage = fileStorage;
		this.staffNotices = staffNotices;
		this.studentNotices = studentNotices;
		this.grants = grants;
		this.exams = exams;
		this.modules = modules;
		this.audit = audit;
		this.jdbc = jdbc;
		this.json = json;
		this.clock = clock;
	}

	/**
	 * Who may do an exam or package change: the staff review (before onboarding) and the Master Admin's onboarding right (after it,
	 * D16). The controller passes what the caller holds; the service refuses the rest.
	 */
	public record Caller(boolean review, boolean onboard) {
	}

	// ---- queue and detail -------------------------------------------------------------------------------------------------

	/**
	 * The queue (plan 5, 6.3): filtered by state, batch, a search over name, mobile and NID (staff only, since this is a staff route),
	 * and a money flag (plan 4.3). Newest first by the time the form was submitted, or created when it was not yet. Each row carries
	 * its flags, what it still owes, and its next unpaid due. The flag filter is applied in SQL, so the paging and the total agree with it.
	 */
	@Transactional(readOnly = true)
	public PageResponse<QueueRow> queue(String status, UUID batchId, String q, String flag, int page, int size) {
		List<FieldErrorDetail> problems = new ArrayList<>();
		List<String> where = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (status != null && !status.isBlank()) {
			String value = status.trim().toUpperCase(Locale.ROOT);
			if (!STATES.contains(value)) {
				problems.add(new FieldErrorDetail("status", "Choose one of the listed states."));
			}
			else {
				where.add("a.status = ?");
				args.add(value);
			}
		}
		if (batchId != null) {
			where.add("a.batch_id = ?");
			args.add(batchId);
		}
		if (q != null && !q.isBlank()) {
			String like = "%" + escapeLike(q.trim()) + "%";
			where.add("(a.full_name ilike ? escape '!' or a.mobile like ? escape '!' or a.nid like ? escape '!')");
			args.add(like);
			args.add(like);
			args.add(like);
		}
		String flagValue = flag == null || flag.isBlank() ? null : flag.trim().toLowerCase(Locale.ROOT);
		if (flagValue != null && !FLAGS.contains(flagValue)) {
			problems.add(new FieldErrorDetail("flag", "Choose unpaid, overdue or pending-approval."));
		}
		else if (flagValue != null) {
			where.add(flagPredicate(flagValue));
			if ("overdue".equals(flagValue)) {
				args.add(money.today());
			}
		}
		if (!problems.isEmpty()) {
			throw validation(problems);
		}
		int pageSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
		int pageNumber = Math.max(0, page);
		String whereSql = where.isEmpty() ? "" : " where " + String.join(" and ", where);
		Long total = jdbc.queryForObject("select count(*) from offline_admissions a" + whereSql, Long.class, args.toArray());
		long count = total == null ? 0L : total;
		// The clock's now comes first: it is the first placeholder, in the select list, for the live code's expiry (V87).
		List<Object> pageArgs = new ArrayList<>();
		pageArgs.add(Timestamp.from(clock.instant()));
		pageArgs.addAll(args);
		pageArgs.add(pageSize);
		pageArgs.add((long) pageNumber * pageSize);
		List<QueueRow> found = jdbc.query("""
				select a.id, a.student_id, a.full_name, a.mobile, a.batch_id, b.name_en, b.name_bn, a.status, a.submit_count,
				  a.submitted_at, a.created_at, (p.user_id is not null) as has_photo,
				  (u.status <> 'PENDING_ACTIVATION') as activated,
				  case when a.activation_code is not null and a.activation_code_expires_at > ? then a.activation_code_expires_at end
				    as code_expires_at
				from offline_admissions a
				join users u on u.id = a.student_id
				left join offline_batches b on b.id = a.batch_id
				left join student_photos p on p.user_id = a.student_id
				""" + whereSql + """
				 order by coalesce(a.submitted_at, a.created_at) desc, a.id
				 limit ? offset ?
				""", (rs, row) -> new QueueRow(rs.getObject("id", UUID.class), rs.getObject("student_id", UUID.class),
				rs.getString("full_name"), rs.getString("mobile"), rs.getObject("batch_id", UUID.class), rs.getString("name_en"),
				rs.getString("name_bn"), rs.getString("status"), rs.getInt("submit_count"), instant(rs.getTimestamp("submitted_at")),
				instant(rs.getTimestamp("created_at")), rs.getBoolean("has_photo"), List.of(), null, null,
				rs.getBoolean("activated"), instant(rs.getTimestamp("code_expires_at"))), pageArgs.toArray());
		// The money of the whole page in two queries, then each row's flags from it (plan 4.3).
		Map<UUID, OfflineAdmissionMoney.Money> moneyByAdmission = money.page(found.stream().map(QueueRow::id).toList(), money.today());
		List<QueueRow> rows = new ArrayList<>();
		for (QueueRow row : found) {
			OfflineAdmissionMoney.Money due = moneyByAdmission.getOrDefault(row.id(), OfflineAdmissionMoney.Money.NONE);
			rows.add(new QueueRow(row.id(), row.studentId(), row.fullName(), row.mobile(), row.batchId(), row.batchNameEn(),
					row.batchNameBn(), row.status(), row.submitCount(), row.submittedAt(), row.createdAt(), row.hasPhoto(),
					due.flags(row.status()), due.owedTotal(), due.nextDueOn(), row.activated(), row.codeExpiresAt()));
		}
		int totalPages = (int) ((count + pageSize - 1) / pageSize);
		return new PageResponse<>(rows, pageNumber, pageSize, count, totalPages);
	}

	/** The SQL predicate of one money flag on the queue (plan 4.3, D13). {@code flag} is one of {@link #FLAGS}, already lower case. */
	private static String flagPredicate(String flag) {
		return switch (flag) {
			case "unpaid" -> "(" + OfflineAdmissionMoney.NO_PAYMENT_PREDICATE + " or " + OfflineAdmissionMoney.unpaidDue(false) + ")";
			case "overdue" -> OfflineAdmissionMoney.unpaidDue(true);
			default -> OfflineAdmissionMoney.PENDING_PREDICATE;
		};
	}

	@Transactional(readOnly = true)
	public AdmissionDetail detail(UUID id) {
		return view(load(id));
	}

	// ---- setup ------------------------------------------------------------------------------------------------------------

	/**
	 * The setup (plan 4.2, D7, D10, D12, C11, C16): batch, monthly fee, admitted on, first due, course end. Allowed only while the
	 * form is under review. A batch fills the fee and the end date when they are empty. Admitted on defaults to the day the account
	 * was made at the desk; the first due date defaults to one calendar month after it, and moves with it while it is still the default.
	 */
	@Transactional
	public AdmissionDetail setup(UUID actorId, UUID id, JsonNode body) {
		OfflineAdmission a = lock(id);
		if (READY.equals(a.getStatus())) {
			throw state("The setup is locked while the admission is ready. Press Back to review to change it.");
		}
		if (!SUBMITTED.equals(a.getStatus())) {
			throw state("The setup can be changed only while the form is under review. This admission is " + a.getStatus() + ".");
		}
		List<FieldErrorDetail> problems = new ArrayList<>();
		UUID batchId = uuid(body, "batchId", problems);
		BigDecimal fee = money(body, "monthlyFee", problems);
		LocalDate admittedOn = day(body, "admittedOn", problems);
		LocalDate firstDueOn = day(body, "firstDueOn", problems);
		LocalDate courseEndsOn = day(body, "courseEndsOn", problems);
		OfflineBatch batch = null;
		if (batchId != null) {
			batch = batches.findById(batchId).orElse(null);
			if (batch == null) {
				problems.add(new FieldErrorDetail("batchId", "Choose a batch from the list."));
			}
			else if (!OfflineBatchService.ACTIVE.equals(batch.getStatus())) {
				problems.add(new FieldErrorDetail("batchId", "This batch is archived and cannot be chosen."));
			}
		}
		if (!problems.isEmpty()) {
			throw validation(problems);
		}
		LocalDate oldAdmittedOn = a.getAdmittedOn();
		if (admittedOn != null) {
			a.setAdmittedOn(admittedOn);
		}
		else if (a.getAdmittedOn() == null) {
			a.setAdmittedOn(OfflineFeeSchedule.today(a.getCreatedAt()));
		}
		if (firstDueOn != null) {
			a.setFirstDueOn(firstDueOn);
		}
		else if (a.getFirstDueOn() == null) {
			a.setFirstDueOn(a.getAdmittedOn().plusMonths(1));
		}
		else if (admittedOn != null && oldAdmittedOn != null && a.getFirstDueOn().equals(oldAdmittedOn.plusMonths(1))) {
			a.setFirstDueOn(admittedOn.plusMonths(1));
		}
		if (batch != null) {
			a.setBatchId(batch.getId());
			if (fee == null && a.getMonthlyFee() == null) {
				a.setMonthlyFee(batch.getDefaultMonthlyFee());
			}
			if (courseEndsOn == null && a.getCourseEndsOn() == null) {
				a.setCourseEndsOn(batch.getEndsOn());
			}
		}
		if (fee != null) {
			a.setMonthlyFee(fee);
		}
		if (courseEndsOn != null) {
			a.setCourseEndsOn(courseEndsOn);
		}
		if (a.getFirstDueOn().isBefore(a.getAdmittedOn())) {
			problems.add(new FieldErrorDetail("firstDueOn", "The first due date cannot be before the admitted-on date."));
		}
		if (a.getCourseEndsOn() != null && !a.getCourseEndsOn().isAfter(a.getFirstDueOn())) {
			problems.add(new FieldErrorDetail("courseEndsOn", "The course end date must be after the first due date."));
		}
		if (!problems.isEmpty()) {
			throw validation(problems);
		}
		admissions.saveAndFlush(a);
		audit.record(AuditRecorder.OFFLINE_ADMISSION_SETUP_CHANGED, AuditRecorder.OFFLINE_ADMISSION_ENTITY, id);
		return view(a);
	}

	// ---- exams and packages -----------------------------------------------------------------------------------------------

	/** One exam or package added to an admission, with the overlaps it has with what the student already holds (plan 4.4). */
	public record ItemAdded(AdmissionDetail admission, List<LiveExamGrantAdminService.QuoteOverlap> overlaps, boolean alreadyHeld) {
	}

	/**
	 * Adds an exam or package (plan 5). Before onboarding it is only on the list, and the response carries the warnings. After
	 * onboarding (the Master Admin only, D16) the grant is made at once, capped at the course end, and the student is told once.
	 */
	@Transactional
	public ItemAdded addItem(UUID actorId, Caller caller, UUID id, JsonNode body) {
		OfflineAdmission a = lock(id);
		boolean onboarded = ONBOARDED.equals(a.getStatus());
		requireItemsOpen(a, caller);
		List<FieldErrorDetail> problems = new ArrayList<>();
		String scope = body != null && has(body, "scope") ? body.get("scope").asText().trim().toUpperCase(Locale.ROOT) : "";
		UUID examId = uuid(body, "examId", problems);
		UUID moduleId = uuid(body, "moduleId", problems);
		if (!EXAM.equals(scope) && !MODULE.equals(scope)) {
			problems.add(new FieldErrorDetail("scope", "Choose an exam or a package."));
		}
		else if (EXAM.equals(scope)) {
			moduleId = null;
			if (examId == null) {
				problems.add(new FieldErrorDetail("examId", "Choose an exam."));
			}
			else if (exams.findByIdAndDeletedAtIsNull(examId).filter(exam -> PUBLISHED.equals(exam.getStatus())).isEmpty()) {
				problems.add(new FieldErrorDetail("examId", "Only a published exam can be added."));
			}
		}
		else {
			examId = null;
			if (moduleId == null) {
				problems.add(new FieldErrorDetail("moduleId", "Choose a package."));
			}
			else if (modules.findByIdAndDeletedAtIsNull(moduleId).filter(module -> PUBLISHED.equals(module.getStatus())).isEmpty()) {
				problems.add(new FieldErrorDetail("moduleId", "Only a published package can be added."));
			}
		}
		if (!problems.isEmpty()) {
			throw validation(problems);
		}
		UUID target = EXAM.equals(scope) ? examId : moduleId;
		boolean duplicate = items.findByAdmissionIdAndRemovedAtIsNull(id).stream()
			.anyMatch(item -> scope.equals(item.getScope()) && target.equals(EXAM.equals(scope) ? item.getExamId() : item.getModuleId()));
		if (duplicate) {
			throw validation(List.of(new FieldErrorDetail(EXAM.equals(scope) ? "examId" : "moduleId", "This is already in the admission.")));
		}
		Instant now = clock.instant();
		OfflineAdmissionItem item = newItem(id, scope, examId, moduleId, actorId, now);
		if (!onboarded) {
			items.saveAndFlush(item);
			audit.record(AuditRecorder.OFFLINE_ADMISSION_ITEM_ADDED, AuditRecorder.OFFLINE_ADMISSION_ENTITY, id);
			List<LiveExamGrantAdminService.QuoteOverlap> overlaps = grants.overlaps(a.getStudentId(), scope, moduleId, examId, now);
			boolean held = grants.holds(a.getStudentId(), scope, moduleId, examId, now);
			return new ItemAdded(view(a), overlaps, held);
		}
		String name = itemName(scope, moduleId, examId);
		if (grants.holds(a.getStudentId(), scope, moduleId, examId, now)) {
			throw new ApiException(ErrorCode.GRANT_ALREADY_ACTIVE, HttpStatus.CONFLICT,
					"The student already has active access to \"" + name + "\".");
		}
		String before = itemSummary(id);
		items.saveAndFlush(item);
		UUID grantId = grants.grant(a.getStudentId(), scope, moduleId, examId, OfflineFeeSchedule.endOfDay(a.getCourseEndsOn()),
				actorId, now);
		item.setGrantId(grantId);
		items.saveAndFlush(item);
		audit.record(AuditRecorder.OFFLINE_ACCESS_GRANTED, AuditRecorder.LIVE_EXAM_GRANT_ENTITY, grantId);
		audit.record(AuditRecorder.OFFLINE_ADMISSION_ITEM_ADDED, AuditRecorder.OFFLINE_ADMISSION_ENTITY, id);
		notifyItemsChanged(a, now, before, itemSummary(id));
		return new ItemAdded(view(a), List.of(), false);
	}

	/** Removes an exam or package. After onboarding its grant is revoked, with the reason, and the student is told once (D16). */
	@Transactional
	public AdmissionDetail removeItem(UUID actorId, Caller caller, UUID id, UUID itemId) {
		OfflineAdmission a = lock(id);
		boolean onboarded = ONBOARDED.equals(a.getStatus());
		requireItemsOpen(a, caller);
		OfflineAdmissionItem item = items.findById(itemId)
			.filter(found -> id.equals(found.getAdmissionId()) && found.getRemovedAt() == null)
			.orElseThrow(OfflineSupport::notFound);
		Instant now = clock.instant();
		String before = onboarded ? itemSummary(id) : null;
		item.setRemovedAt(now);
		items.saveAndFlush(item);
		if (item.getGrantId() != null) {
			grants.revoke(item.getGrantId(), actorId, "Removed from offline admission", now);
			audit.record(AuditRecorder.OFFLINE_ACCESS_REVOKED, AuditRecorder.LIVE_EXAM_GRANT_ENTITY, item.getGrantId());
		}
		audit.record(AuditRecorder.OFFLINE_ADMISSION_ITEM_REMOVED, AuditRecorder.OFFLINE_ADMISSION_ENTITY, id);
		if (onboarded) {
			notifyItemsChanged(a, now, before, itemSummary(id));
		}
		return view(a);
	}

	// ---- review -----------------------------------------------------------------------------------------------------------

	/**
	 * Sends the form back with a reason (D5, C14). From the review or from ready, the student sees the reason, fixes the form and
	 * submits it again. The reason is kept on the latest submission too, as history.
	 */
	@Transactional
	public AdmissionDetail requestChanges(UUID actorId, UUID id, String reasonText) {
		OfflineAdmission a = lock(id);
		if (!SUBMITTED.equals(a.getStatus()) && !READY.equals(a.getStatus())) {
			throw state("The form can be sent back only while it is under review or ready. This admission is " + a.getStatus() + ".");
		}
		List<FieldErrorDetail> problems = new ArrayList<>();
		String text = reason(reasonText, "reason", problems);
		if (!problems.isEmpty()) {
			throw validation(problems);
		}
		Instant now = clock.instant();
		a.setStatus(CHANGES_REQUESTED);
		a.setChangesReason(text);
		a.setChangesRequestedBy(actorId);
		a.setChangesRequestedAt(now);
		a.setReadyBy(null);
		a.setReadyAt(null);
		admissions.saveAndFlush(a);
		submissions.findByAdmissionIdOrderBySubmittedAtAsc(id).stream().reduce((first, second) -> second).ifPresent(latest -> {
			latest.setReturnedReason(text);
			latest.setReturnedBy(actorId);
			latest.setReturnedAt(now);
			submissions.saveAndFlush(latest);
		});
		audit.record(AuditRecorder.OFFLINE_ADMISSION_CHANGES_REQUESTED, AuditRecorder.OFFLINE_ADMISSION_ENTITY, id);
		studentNotices.notice(a.getStudentId(), OfflineAdmissionNotices.ADMISSION_CHANGES_REQUESTED, id + ":" + now.toEpochMilli());
		studentNotices.mail(a.getStudentId(), OfflineAdmissionNotices.ADMISSION_CHANGES_REQUESTED,
				a.getStudentId() + "/" + id + "/" + now.toEpochMilli(), Map.of("reason", text));
		return view(a);
	}

	/**
	 * Marks the form ready for the Master Admin (C16, plan 4.2). Refused, with each missing part named, until the form is valid, a batch,
	 * a fee above zero, the dates and at least one exam or package are all in place. Every Master Admin is told.
	 */
	@Transactional
	public AdmissionDetail ready(UUID actorId, UUID id) {
		OfflineAdmission a = lock(id);
		if (!SUBMITTED.equals(a.getStatus())) {
			throw state("Only a form under review can be marked ready. This admission is " + a.getStatus() + ".");
		}
		List<FieldErrorDetail> problems = readyProblems(a, photoRows.existsById(a.getStudentId()), activeItemCount(id));
		if (!problems.isEmpty()) {
			throw validation(problems);
		}
		Instant now = clock.instant();
		a.setStatus(READY);
		a.setReadyBy(actorId);
		a.setReadyAt(now);
		admissions.saveAndFlush(a);
		audit.record(AuditRecorder.OFFLINE_ADMISSION_READY, AuditRecorder.OFFLINE_ADMISSION_ENTITY, id);
		Map<String, String> payload = Map.of("personName", personName(a));
		for (UUID master : masterAdminIds()) {
			staffNotices.notify(master, StaffNotificationKind.ADMISSION_READY, payload, link(id),
					"ADMISSION_READY:" + id + ":" + now.toEpochMilli() + ":" + master);
		}
		return view(a);
	}

	/**
	 * Returns a ready admission to review (C16). The setup opens again, and whoever marked it ready is told when someone else did it.
	 */
	@Transactional
	public AdmissionDetail backToReview(UUID actorId, UUID id) {
		OfflineAdmission a = lock(id);
		if (!READY.equals(a.getStatus())) {
			throw state("Only a ready admission can go back to review. This admission is " + a.getStatus() + ".");
		}
		UUID readyBy = a.getReadyBy();
		Instant now = clock.instant();
		a.setStatus(SUBMITTED);
		a.setReadyBy(null);
		a.setReadyAt(null);
		admissions.saveAndFlush(a);
		audit.record(AuditRecorder.OFFLINE_ADMISSION_BACK_TO_REVIEW, AuditRecorder.OFFLINE_ADMISSION_ENTITY, id);
		if (readyBy != null && !readyBy.equals(actorId)) {
			staffNotices.notify(readyBy, StaffNotificationKind.ADMISSION_RETURNED, Map.of("personName", personName(a)), link(id),
					"ADMISSION_RETURNED:" + id + ":" + now.toEpochMilli() + ":" + readyBy);
		}
		return view(a);
	}

	// ---- signed copy and photo --------------------------------------------------------------------------------------------

	/**
	 * Stores the signed copy of the form (D9): a PDF, or a JPEG, PNG or WebP photo of the paper. Optional at any time. The type is read
	 * from the first bytes, not from the name or the type the browser sends.
	 */
	@Transactional
	public AdmissionDetail uploadSigned(UUID actorId, UUID id, MultipartFile file) {
		OfflineAdmission a = load(id);
		if (file == null || file.isEmpty()) {
			throw validation(List.of(new FieldErrorDetail("file", "Choose a file.")));
		}
		if (file.getSize() > MAX_SIGNED_BYTES) {
			throw new ApiException(ErrorCode.FILE_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE, "File is too large.");
		}
		byte[] bytes;
		try {
			bytes = file.getBytes();
		}
		catch (IOException exception) {
			throw validation(List.of(new FieldErrorDetail("file", "The file could not be read.")));
		}
		String contentType = signedType(bytes);
		if (contentType == null) {
			throw validation(List.of(new FieldErrorDetail("file", "Upload a PDF, a JPEG, a PNG or a WebP file.")));
		}
		LocalFileStorage.StoredFile stored = fileStorage.save(file.getOriginalFilename(), contentType,
				new ByteArrayInputStream(bytes), bytes.length, MAX_SIGNED_BYTES);
		fileStorage.commitReplacement(stored.storedFileName(), a.getSignedStoredName());
		a.setSignedStoredName(stored.storedFileName());
		a.setSignedOriginalName(originalName(file.getOriginalFilename()));
		a.setSignedContentType(contentType);
		a.setSignedByteSize((long) bytes.length);
		a.setSignedUploadedBy(actorId);
		a.setSignedUploadedAt(clock.instant());
		admissions.saveAndFlush(a);
		audit.record(AuditRecorder.OFFLINE_ADMISSION_SIGNED_UPLOADED, AuditRecorder.OFFLINE_ADMISSION_ENTITY, id);
		return view(a);
	}

	/** The signed copy's bytes, or 404 when none was uploaded. */
	@Transactional(readOnly = true)
	public SignedFile openSigned(UUID id) {
		OfflineAdmission a = load(id);
		if (a.getSignedStoredName() == null) {
			throw notFound();
		}
		Path path = fileStorage.resolve(a.getSignedStoredName());
		if (!Files.exists(path)) {
			throw notFound();
		}
		try {
			InputStream body = Files.newInputStream(path);
			String name = a.getSignedOriginalName() == null ? "signed-form" : a.getSignedOriginalName();
			return new SignedFile(body, a.getSignedContentType(), a.getSignedByteSize() == null ? 0L : a.getSignedByteSize(), name);
		}
		catch (IOException exception) {
			throw notFound();
		}
	}

	/** The student's photo, which is their profile photo (plan side effect 10). 404 when there is none. */
	@Transactional(readOnly = true)
	public StudentPhotoService.PhotoFile openPhoto(UUID id) {
		OfflineAdmission a = load(id);
		if (!photoRows.existsById(a.getStudentId())) {
			throw notFound();
		}
		return photos.open(a.getStudentId());
	}

	/** The signed copy's bytes and how to name them. */
	public record SignedFile(InputStream body, String contentType, long byteSize, String fileName) {
	}

	// ---- shared with the onboarding service -------------------------------------------------------------------------------

	/** The admission, locked until the caller's transaction ends (every change goes through here). */
	OfflineAdmission lock(UUID id) {
		return admissions.findForUpdate(id).orElseThrow(OfflineSupport::notFound);
	}

	OfflineAdmission load(UUID id) {
		return admissions.findById(id).orElseThrow(OfflineSupport::notFound);
	}

	/** Whether the student has a photo on file, which the form needs (the admission photo is the profile photo). */
	boolean hasPhoto(OfflineAdmission a) {
		return photoRows.existsById(a.getStudentId());
	}

	/** The count of exams and packages the admission holds, and so the count the ready check needs. */
	long activeItemCount(UUID admissionId) {
		return items.findByAdmissionIdAndRemovedAtIsNull(admissionId).size();
	}

	/**
	 * Every requirement the ready step checks, one entry per missing part (plan 6.3, principle 2). Empty when the admission may be
	 * marked ready. The onboarding check uses the same list.
	 */
	List<FieldErrorDetail> readyProblems(OfflineAdmission a, boolean hasPhoto, long activeItems) {
		List<FieldErrorDetail> problems = new ArrayList<>(form.formProblems(a, hasPhoto));
		if (a.getBatchId() == null) {
			problems.add(new FieldErrorDetail("batchId", "Choose a batch."));
		}
		else if (batches.findById(a.getBatchId()).filter(batch -> OfflineBatchService.ACTIVE.equals(batch.getStatus())).isEmpty()) {
			problems.add(new FieldErrorDetail("batchId", "The batch is archived. Choose another batch."));
		}
		if (a.getMonthlyFee() == null || a.getMonthlyFee().signum() <= 0) {
			problems.add(new FieldErrorDetail("monthlyFee", "Enter a monthly fee above zero."));
		}
		if (a.getAdmittedOn() == null) {
			problems.add(new FieldErrorDetail("admittedOn", "Enter the date the student was admitted."));
		}
		if (a.getFirstDueOn() == null) {
			problems.add(new FieldErrorDetail("firstDueOn", "Enter the first due date."));
		}
		else if (a.getAdmittedOn() != null && a.getFirstDueOn().isBefore(a.getAdmittedOn())) {
			problems.add(new FieldErrorDetail("firstDueOn", "The first due date cannot be before the admitted-on date."));
		}
		if (a.getCourseEndsOn() == null) {
			problems.add(new FieldErrorDetail("courseEndsOn", "Enter the course end date."));
		}
		else if (a.getFirstDueOn() != null && !a.getCourseEndsOn().isAfter(a.getFirstDueOn())) {
			problems.add(new FieldErrorDetail("courseEndsOn", "The course end date must be after the first due date."));
		}
		if (activeItems == 0) {
			problems.add(new FieldErrorDetail("items", "Add at least one exam or package."));
		}
		return problems;
	}

	/** The English titles of the active exams and packages, joined with a comma. "-" when there are none. */
	String itemSummary(UUID admissionId) {
		List<String> titles = new ArrayList<>();
		for (OfflineAdmissionItem item : activeItems(admissionId)) {
			titles.add(itemName(item.getScope(), item.getModuleId(), item.getExamId()));
		}
		return titles.isEmpty() ? "-" : String.join(", ", titles);
	}

	/** The English and Bangla titles of the active exams and packages, each joined with "; ", for the approval mail. */
	String[] itemTitles(UUID admissionId) {
		List<String> en = new ArrayList<>();
		List<String> bn = new ArrayList<>();
		for (OfflineAdmissionItem item : activeItems(admissionId)) {
			String[] titles = grants.titles(item.getScope(), item.getModuleId(), item.getExamId());
			if (titles != null) {
				en.add(titles[0]);
				bn.add(titles[1] == null || titles[1].isBlank() ? titles[0] : titles[1]);
			}
		}
		return new String[] { String.join("; ", en), String.join("; ", bn) };
	}

	List<OfflineAdmissionItem> activeItems(UUID admissionId) {
		List<OfflineAdmissionItem> active = new ArrayList<>(items.findByAdmissionIdAndRemovedAtIsNull(admissionId));
		active.sort(Comparator.comparing(OfflineAdmissionItem::getAddedAt));
		return active;
	}

	/** The personal name for a staff notification, cut to the payload's length. Never an NID, a date of birth or an address. */
	static String personName(OfflineAdmission a) {
		return a.getFullName() == null ? "" : StaffNotificationWriter.fitText(a.getFullName());
	}

	static String link(UUID admissionId) {
		return "/staff/admissions/" + admissionId;
	}

	/** Every active Master Admin. Each gets the staff notice once (the dedupe key names the recipient). */
	List<UUID> masterAdminIds() {
		return jdbc.queryForList(
				"select id from users where role = 'MASTER_ADMIN' and status = 'ACTIVE' and deleted_at is null", UUID.class);
	}

	/** One exam or package's name in English, or a placeholder when it has been deleted. */
	String itemName(String scope, UUID moduleId, UUID examId) {
		String[] titles = grants.titles(scope, moduleId, examId);
		return titles == null || titles[0] == null ? "(removed)" : titles[0];
	}

	/** Tells the student the exams and packages changed (D16), with the old and new values. */
	void notifyItemsChanged(OfflineAdmission a, Instant now, String before, String after) {
		List<Map<String, String>> changes = List.of(Map.of("labelBn", "পরীক্ষা ও প্যাকেজ", "labelEn", "Exams and packages", "old",
				before == null ? "-" : before, "new", after));
		studentNotices.notice(a.getStudentId(), OfflineAdmissionNotices.ADMISSION_UPDATED, a.getId() + ":" + now.toEpochMilli());
		studentNotices.mailWithChanges(a.getStudentId(), OfflineAdmissionNotices.ADMISSION_UPDATED,
				a.getStudentId() + "/" + a.getId() + "/" + now.toEpochMilli(), changes);
	}

	/** The admission as the staff detail shows it. NID and date of birth are in the form, so this is for staff only. */
	AdmissionDetail view(OfflineAdmission a) {
		boolean hasPhoto = photoRows.existsById(a.getStudentId());
		List<OfflineAdmissionItem> active = activeItems(a.getId());
		List<ItemView> itemViews = new ArrayList<>();
		for (OfflineAdmissionItem item : active) {
			String[] titles = grants.titles(item.getScope(), item.getModuleId(), item.getExamId());
			itemViews.add(new ItemView(item.getId(), item.getScope(), item.getExamId(), item.getModuleId(),
					titles == null ? "(removed)" : titles[0], titles == null ? "(removed)" : titles[1], item.getGrantId(),
					item.getAddedAt()));
		}
		List<SubmissionView> history = new ArrayList<>();
		for (OfflineAdmissionSubmission row : submissions.findByAdmissionIdOrderBySubmittedAtAsc(a.getId())) {
			history.add(new SubmissionView(row.getId(), row.getSubmittedAt(), readForm(row.getFormJson()), row.getReturnedReason(),
					row.getReturnedBy(), row.getReturnedAt()));
		}
		BatchInfo batch = a.getBatchId() == null ? null
				: batches.findById(a.getBatchId()).map(found -> new BatchInfo(found.getId(), found.getNameEn(), found.getNameBn()))
					.orElse(null);
		Setup setup = new Setup(a.getBatchId(), batch == null ? null : batch.nameEn(), batch == null ? null : batch.nameBn(),
				a.getMonthlyFee(), a.getAdmittedOn(), a.getFirstDueOn(), a.getCourseEndsOn());
		List<FieldErrorDetail> readyProblems = SUBMITTED.equals(a.getStatus()) || CHANGES_REQUESTED.equals(a.getStatus())
				? readyProblems(a, hasPhoto, active.size()) : List.of();
		SignedCopy signed = a.getSignedStoredName() == null ? null
				: new SignedCopy(a.getSignedOriginalName(), a.getSignedUploadedAt(), a.getSignedUploadedBy());
		List<ItemOverlaps> overlaps = itemOverlaps(a, active);
		return new AdmissionDetail(a.getId(), a.getStudentId(), a.getStatus(), a.getVersion() == null ? 0L : a.getVersion(),
				a.getSubmitCount(), a.getSubmittedAt(), a.getCreatedAt(), hasPhoto, hasPhoto ? photoPath(a) : null,
				OfflineAdmissionService.form(a), a.getChangesReason(), a.getChangesRequestedAt(), setup, itemViews, history,
				readyProblems, a.getReadyBy(), a.getReadyAt(), a.getOnboardedBy(), a.getOnboardedAt(), a.getClosedAt(),
				a.getClosedReason(), signed, overlaps);
	}

	/**
	 * The overlaps of each active item, through the same check that adding an item uses (plan 4.4). An item whose exam or package has
	 * been deleted has none, since the check cannot judge it.
	 */
	private List<ItemOverlaps> itemOverlaps(OfflineAdmission a, List<OfflineAdmissionItem> active) {
		Instant now = clock.instant();
		List<ItemOverlaps> found = new ArrayList<>();
		for (OfflineAdmissionItem item : active) {
			String[] titles = grants.titles(item.getScope(), item.getModuleId(), item.getExamId());
			boolean live = EXAM.equals(item.getScope())
					? exams.findByIdAndDeletedAtIsNull(item.getExamId()).isPresent()
					: modules.findByIdAndDeletedAtIsNull(item.getModuleId()).isPresent();
			List<LiveExamGrantAdminService.QuoteOverlap> warnings = live
					? grants.overlaps(a.getStudentId(), item.getScope(), item.getModuleId(), item.getExamId(), now)
					: List.of();
			found.add(new ItemOverlaps(item.getId(), titles == null ? "(removed)" : titles[0],
					titles == null ? "(removed)" : titles[1], warnings));
		}
		return found;
	}

	private OfflineAdmissionItem newItem(UUID admissionId, String scope, UUID examId, UUID moduleId, UUID actorId, Instant now) {
		OfflineAdmissionItem item = new OfflineAdmissionItem();
		item.setId(UUID.randomUUID());
		item.setAdmissionId(admissionId);
		item.setScope(scope);
		item.setExamId(examId);
		item.setModuleId(moduleId);
		item.setAddedBy(actorId);
		item.setAddedAt(now);
		return item;
	}

	/** Items change only in the states the caller may change them in (plan 5). READY and CLOSED are locked for everyone. */
	private void requireItemsOpen(OfflineAdmission a, Caller caller) {
		String status = a.getStatus();
		if (CLOSED.equals(status)) {
			throw state("This admission is closed, so its exams and packages cannot change.");
		}
		if (READY.equals(status)) {
			throw state("The exams and packages are locked while the admission is ready. Press Back to review to change them.");
		}
		if (ONBOARDED.equals(status)) {
			if (!caller.onboard()) {
				throw OfflineSupport.denied();
			}
			return;
		}
		if (!caller.review()) {
			throw OfflineSupport.denied();
		}
	}

	private static Instant instant(Timestamp value) {
		return value == null ? null : value.toInstant();
	}

	private OfflineAdmissionService.Form readForm(String formJson) {
		try {
			return json.readValue(formJson, OfflineAdmissionService.Form.class);
		}
		catch (tools.jackson.core.JacksonException exception) {
			return null;
		}
	}

	private String photoPath(OfflineAdmission a) {
		return "/api/v1/offline-admissions/" + a.getId() + "/photo";
	}

	/** The type of a signed copy from its first bytes: a PDF, a JPEG, a PNG or a WebP. Null for anything else. */
	static String signedType(byte[] bytes) {
		if (startsWith(bytes, new byte[] { '%', 'P', 'D', 'F', '-' })) {
			return "application/pdf";
		}
		if (startsWith(bytes, new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF })) {
			return "image/jpeg";
		}
		if (startsWith(bytes, new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A })) {
			return "image/png";
		}
		if (bytes.length >= 12 && startsWith(bytes, new byte[] { 'R', 'I', 'F', 'F' })
				&& bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
			return "image/webp";
		}
		return null;
	}

	private static boolean startsWith(byte[] bytes, byte[] magic) {
		if (bytes.length < magic.length) {
			return false;
		}
		for (int i = 0; i < magic.length; i++) {
			if (bytes[i] != magic[i]) {
				return false;
			}
		}
		return true;
	}

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

	/** Escapes the LIKE wildcards in a search, with '!' as the escape character. */
	private static String escapeLike(String text) {
		return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
	}

	// ---- views ------------------------------------------------------------------------------------------------------------

	/**
	 * One row of the queue. {@code flags} are NO_PAYMENT, OVERDUE and PENDING_APPROVAL (plan 4.3); {@code owedTotal} is the sum of the
	 * unpaid live dues, and {@code nextDueOn} the earliest of them (both null or zero before onboarding). {@code activated} says the
	 * student has set a password; {@code codeExpiresAt} is the expiry of the desk's live activation code, null when no code is live.
	 */
	public record QueueRow(UUID id, UUID studentId, String fullName, String mobile, UUID batchId, String batchNameEn,
			String batchNameBn, String status, int submitCount, Instant submittedAt, Instant createdAt, boolean hasPhoto,
			List<String> flags, BigDecimal owedTotal, @JsonInclude(JsonInclude.Include.ALWAYS) LocalDate nextDueOn,
			boolean activated, @JsonInclude(JsonInclude.Include.ALWAYS) Instant codeExpiresAt) {
	}

	public record AdmissionDetail(UUID id, UUID studentId, String state, long version, int submitCount, Instant submittedAt,
			Instant createdAt, boolean hasPhoto, String photoUrl, OfflineAdmissionService.Form form, String changesReason,
			Instant changesRequestedAt, Setup setup, List<ItemView> items, List<SubmissionView> submissions,
			List<FieldErrorDetail> readyProblems, UUID readyBy, Instant readyAt, UUID onboardedBy, Instant onboardedAt,
			Instant closedAt, String closedReason, SignedCopy signedCopy, List<ItemOverlaps> overlaps) {
	}

	/**
	 * What one active exam or package overlaps with: the same check that adding it uses (plan 4.4). {@code warnings} is empty when the
	 * student holds nothing that overlaps it. Read only; an overlap never blocks.
	 */
	public record ItemOverlaps(UUID itemId, String titleEn, String titleBn, List<LiveExamGrantAdminService.QuoteOverlap> warnings) {
	}

	public record Setup(UUID batchId, String batchNameEn, String batchNameBn, BigDecimal monthlyFee, LocalDate admittedOn,
			LocalDate firstDueOn, LocalDate courseEndsOn) {
	}

	public record ItemView(UUID id, String scope, UUID examId, UUID moduleId, String titleEn, String titleBn, UUID grantId,
			Instant addedAt) {
	}

	/** One submission of the form. {@code returnedReason} is set when staff sent that submission back. */
	public record SubmissionView(UUID id, Instant submittedAt, OfflineAdmissionService.Form form, String returnedReason,
			UUID returnedBy, Instant returnedAt) {
	}

	public record SignedCopy(String fileName, Instant uploadedAt, UUID uploadedBy) {
	}

	private record BatchInfo(UUID id, String nameEn, String nameBn) {
	}

}
