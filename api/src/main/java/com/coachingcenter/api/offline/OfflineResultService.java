package com.coachingcenter.api.offline;

import static com.coachingcenter.api.offline.OfflineResultFile.params;
import static com.coachingcenter.api.offline.OfflineSupport.notFound;
import static com.coachingcenter.api.offline.OfflineSupport.state;
import static com.coachingcenter.api.offline.OfflineSupport.validation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
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
import com.coachingcenter.api.offline.OfflineResultFile.Parsed;
import com.coachingcenter.api.offline.OfflineResultFile.Problem;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Exam results of an offline batch (docs/offline-admission/offline-results-plan.md, sections 3 to 5; results-api-contract.md). Staff read an
 * uploaded result sheet for one exam, and see a preview with its problems, warnings and every row (rule R8, A2 to A5). A sheet without
 * problems is saved as the STAGED one. The Master Admin publishes it: the published one becomes REPLACED, and every onboarded student with a
 * row in the sheet is told in the app. A discard removes only the staged sheet. Each change is audited. A save, a publish and a discard lock
 * the exam class first, so they run one after another per exam (plan F2).
 */
@Service
public class OfflineResultService {

	/** The largest result sheet accepted (plan section 2). */
	static final long MAX_FILE_BYTES = 5L * 1024 * 1024;

	static final String FILE_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

	private static final String NO_ROLL_IN_BATCH = "No student of this batch has this roll number.";

	private static final String ROLL_IN_OTHER_BATCH = "This roll number belongs to a student of another batch.";

	private final OfflineBatchRepository batches;

	private final OfflineClassRepository classes;

	private final OfflineResultSheetRepository sheets;

	private final OfflineResultRowRepository rows;

	private final OfflineInstructorRepository instructors;

	private final OfflineAccountMatcher matcher;

	private final LocalFileStorage files;

	private final OfflineAdmissionNotices notices;

	private final AuditRecorder audit;

	private final JdbcTemplate jdbc;

	private final Clock clock;

	public OfflineResultService(OfflineBatchRepository batches, OfflineClassRepository classes, OfflineResultSheetRepository sheets,
			OfflineResultRowRepository rows, OfflineInstructorRepository instructors, OfflineAccountMatcher matcher, LocalFileStorage files,
			OfflineAdmissionNotices notices, AuditRecorder audit, JdbcTemplate jdbc, Clock clock) {
		this.batches = batches;
		this.classes = classes;
		this.sheets = sheets;
		this.rows = rows;
		this.instructors = instructors;
		this.matcher = matcher;
		this.files = files;
		this.notices = notices;
		this.audit = audit;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	// ---- reads ------------------------------------------------------------------------------------------------------------

	/** The exam classes of a batch, newest first, with the state of their results and the published row count (results-api-contract). */
	@Transactional(readOnly = true)
	public List<BatchExam> batchExams(UUID batchId) {
		if (!batches.existsById(batchId)) {
			throw notFound();
		}
		Map<UUID, String> names = instructorNames();
		List<OfflineClass> exams = new ArrayList<>();
		for (OfflineClass classItem : classes.findByBatchIdOrderByHeldOnAscStartsAtAsc(batchId)) {
			if (OfflineClass.KIND_EXAM.equals(classItem.getKind())) {
				exams.add(classItem);
			}
		}
		exams.sort(Comparator.comparing(OfflineClass::getHeldOn).reversed().thenComparing(OfflineClass::getId));
		List<BatchExam> found = new ArrayList<>();
		for (OfflineClass classItem : exams) {
			List<OfflineResultSheet> own = sheets.findByClassIdAndStatusIn(classItem.getId(), List.of(OfflineResultSheet.STATUS_PUBLISHED,
					OfflineResultSheet.STATUS_STAGED));
			Optional<OfflineResultSheet> published = own.stream().filter(sheet -> OfflineResultSheet.STATUS_PUBLISHED.equals(sheet.getStatus()))
				.findFirst();
			boolean staged = own.stream().anyMatch(sheet -> OfflineResultSheet.STATUS_STAGED.equals(sheet.getStatus()));
			String state = published.isPresent() ? (staged ? "PUBLISHED_WITH_STAGED" : "PUBLISHED") : (staged ? "STAGED" : "NONE");
			Integer rowCount = published.map(sheet -> jdbc.queryForObject("select count(*) from offline_result_rows where sheet_id = ?",
					Integer.class, sheet.getId())).orElse(0);
			found.add(new BatchExam(classItem.getId(), classItem.getHeldOn(), classItem.getSubject(), classItem.getTopic(),
					classItem.getInstructorId() == null ? classItem.getInstructorText() : names.get(classItem.getInstructorId()),
					classItem.getKind(), classItem.getStatus(), OfflineClass.STATUS_CANCELLED.equals(classItem.getStatus()), state,
					published.map(OfflineResultSheet::getPublishedAt).orElse(null), rowCount == null ? 0 : rowCount));
		}
		return found;
	}

	/** The published and the staged sheet of one exam, each with its rows (the sheet view of the contract). */
	@Transactional(readOnly = true)
	public ClassResults classResults(UUID classId) {
		OfflineClass classItem = findClass(classId);
		List<OfflineResultSheet> own = sheets.findByClassIdAndStatusIn(classId, List.of(OfflineResultSheet.STATUS_PUBLISHED,
				OfflineResultSheet.STATUS_STAGED));
		SheetView published = null;
		SheetView staged = null;
		for (OfflineResultSheet sheet : own) {
			if (OfflineResultSheet.STATUS_PUBLISHED.equals(sheet.getStatus())) {
				published = sheetView(sheet);
			}
			else {
				staged = sheetView(sheet);
			}
		}
		return new ClassResults(classItem.getId(), classItem.getBatchId(), classItem.getSubject(), classItem.getTopic(),
				classItem.getHeldOn(), OfflineClass.STATUS_CANCELLED.equals(classItem.getStatus()), published, staged);
	}

	/** The uploaded file of one sheet, as it was uploaded. 404 when the sheet is not of this exam or its file is gone. */
	@Transactional(readOnly = true)
	public SheetFile file(UUID classId, UUID sheetId) {
		OfflineResultSheet sheet = sheets.findById(sheetId).filter(found -> classId.equals(found.getClassId())).orElseThrow(OfflineSupport::notFound);
		Path path = files.resolve(sheet.getStoredName());
		if (!Files.isRegularFile(path)) {
			throw notFound();
		}
		try {
			InputStream body = Files.newInputStream(path);
			String contentType = sheet.getContentType() == null ? FILE_CONTENT_TYPE : sheet.getContentType();
			return new SheetFile(body, contentType, sheet.getByteSize(), sheet.getFileName());
		}
		catch (IOException exception) {
			throw new IllegalStateException("Could not open the result file.", exception);
		}
	}

	/** The classes of a batch that have a result sheet, staged or published (rule A1: a routine upload keeps them). */
	@Transactional(readOnly = true)
	public Set<UUID> classesWithResults(UUID batchId) {
		return sheets.classIdsOfBatchWith(batchId, List.of(OfflineResultSheet.STATUS_STAGED, OfflineResultSheet.STATUS_PUBLISHED));
	}

	/** Whether an exam class has a sheet, staged or published (rule A1). */
	@Transactional(readOnly = true)
	public boolean hasResult(UUID classId) {
		return sheets.existsByClassIdAndStatusIn(classId, List.of(OfflineResultSheet.STATUS_STAGED, OfflineResultSheet.STATUS_PUBLISHED));
	}

	// ---- preview and save -------------------------------------------------------------------------------------------------

	/** Reads and checks a sheet, saves nothing. Problems are answered with the preview (HTTP 200), so the dialog can show them. */
	@Transactional(readOnly = true)
	public Preview preview(UUID classId, MultipartFile file, String examType, String passMark) {
		Upload upload = upload(file, examType, passMark);
		return analyse(classId, upload).preview();
	}

	/**
	 * Saves a sheet as the STAGED one (rule R10): an earlier staged sheet of the exam is replaced, and the published one stays. Refused with
	 * the problems (400) when the preview has any. A unique index that a racing save still trips answers 409 (RESULT_CHANGED).
	 */
	@Transactional
	public ClassResults save(UUID classId, UUID actorId, MultipartFile file, String examType, String passMark) {
		Upload upload = upload(file, examType, passMark);
		lockClass(classId);
		try {
			Analysis analysis = analyse(classId, upload);
			if (!analysis.preview().problems().isEmpty()) {
				throw validation(analysis.preview().problems().stream()
					.map(problem -> new FieldErrorDetail("file", describe(problem), problem.code(), problem.params())).toList());
			}
			OfflineClass classItem = analysis.classItem();
			Parsed parsed = analysis.parsed();
			LocalFileStorage.StoredFile stored = files.save(upload.fileName(), upload.contentType(),
					new ByteArrayInputStream(upload.bytes()), upload.bytes().length, MAX_FILE_BYTES);
			Optional<OfflineResultSheet> previous = sheets.findByClassIdAndStatus(classId, OfflineResultSheet.STATUS_STAGED);
			previous.ifPresent(old -> {
				sheets.delete(old);
				sheets.flush();
			});
			previous.ifPresentOrElse(old -> files.commitReplacement(stored.storedFileName(), old.getStoredName()),
					() -> files.commitReplacement(stored.storedFileName(), null));
			Instant now = clock.instant();
			OfflineResultSheet sheet = new OfflineResultSheet(UUID.randomUUID(), classId, classItem.getBatchId(), examType, parsed.fullMarks(),
					parsed.negative(), upload.passMark(), upload.fileName(), upload.contentType(), upload.bytes().length,
					stored.storedFileName(), actorId, now);
			sheets.saveAndFlush(sheet);
			List<OfflineResultRow> saved = new ArrayList<>();
			for (ResultRowView row : analysis.rows()) {
				saved.add(new OfflineResultRow(UUID.randomUUID(), sheet.getId(), row.admissionId(), row.roll(), row.absent(), row.right(),
						row.wrong(), row.mcq(), row.written(), row.total(), row.rank(), row.passed(), row.fileTotal(), row.fileRank(),
						row.rowNumber()));
			}
			rows.saveAll(saved);
			audit.record(AuditRecorder.OFFLINE_RESULT_UPLOADED, AuditRecorder.OFFLINE_RESULT_ENTITY, sheet.getId());
			return classResults(classId);
		}
		catch (DataIntegrityViolationException racing) {
			throw resultChanged();
		}
	}

	// ---- publish and discard ----------------------------------------------------------------------------------------------

	/**
	 * Publishes the staged sheet (rule R10). The published one becomes REPLACED. Every onboarded student with a row in the sheet (present or
	 * absent) gets one in-app notice: OFFLINE_RESULT_PUBLISHED the first time, OFFLINE_RESULT_UPDATED for a new version. 409 when nothing is
	 * staged, and RESULT_CHANGED (409) when a racing publish still trips a unique index.
	 */
	@Transactional
	public ClassResults publish(UUID classId, UUID actorId) {
		lockClass(classId);
		try {
			OfflineResultSheet staged = sheets.findByClassIdAndStatus(classId, OfflineResultSheet.STATUS_STAGED)
				.orElseThrow(() -> state("There is no staged result to publish."));
			Optional<OfflineResultSheet> published = sheets.findByClassIdAndStatus(classId, OfflineResultSheet.STATUS_PUBLISHED);
			Instant now = clock.instant();
			published.ifPresent(old -> {
				old.markReplaced();
				sheets.saveAndFlush(old);
			});
			staged.markPublished(actorId, now);
			sheets.saveAndFlush(staged);
			String kind = published.isPresent() ? OfflineAdmissionNotices.OFFLINE_RESULT_UPDATED : OfflineAdmissionNotices.OFFLINE_RESULT_PUBLISHED;
			for (UUID studentId : notifiedStudents(staged.getId())) {
				notices.notice(studentId, kind, staged.getId() + ":" + studentId);
			}
			audit.record(AuditRecorder.OFFLINE_RESULT_PUBLISHED, AuditRecorder.OFFLINE_RESULT_ENTITY, staged.getId());
			return classResults(classId);
		}
		catch (DataIntegrityViolationException racing) {
			throw resultChanged();
		}
	}

	/** Removes the staged sheet and its file. The published sheet is never touched. 404 when nothing is staged. */
	@Transactional
	public void discardStaged(UUID classId, UUID actorId) {
		lockClass(classId);
		OfflineResultSheet staged = sheets.findByClassIdAndStatus(classId, OfflineResultSheet.STATUS_STAGED)
			.orElseThrow(OfflineSupport::notFound);
		String stored = staged.getStoredName();
		UUID sheetId = staged.getId();
		sheets.delete(staged);
		sheets.flush();
		files.commitReplacement(null, stored);
		audit.record(AuditRecorder.OFFLINE_RESULT_DISCARDED, AuditRecorder.OFFLINE_RESULT_ENTITY, sheetId);
	}

	// ---- analysis ---------------------------------------------------------------------------------------------------------

	/** The check of one upload against the exam and the batch: the preview, and the rows that a save would store. */
	private Analysis analyse(UUID classId, Upload upload) {
		OfflineClass classItem = findClass(classId);
		List<ProblemView> problems = new ArrayList<>();
		LocalDate today = OfflineFeeSchedule.today(clock.instant());
		if (!OfflineClass.KIND_EXAM.equals(classItem.getKind())) {
			problems.add(new ProblemView(null, null, "NOT_EXAM", params(),
					"Results can be uploaded only for an exam. This class is not an exam."));
		}
		if (OfflineClass.STATUS_CANCELLED.equals(classItem.getStatus())) {
			problems.add(new ProblemView(null, null, "CANCELLED", params(), "This exam was cancelled, so it takes no result."));
		}
		if (OfflineClass.STATUS_NOT_TAKEN.equals(classItem.getStatus()) || OfflineClass.STATUS_NOT_TAKEN_PENDING.equals(classItem.getStatus())) {
			problems.add(new ProblemView(null, null, "NOT_TAKEN", params(), "This exam is marked as not taken, so it takes no result."));
		}
		if (classItem.getHeldOn().isAfter(today)) {
			problems.add(new ProblemView(null, null, "FUTURE_DATE", params("heldOn", classItem.getHeldOn().toString()), "This exam is on "
					+ classItem.getHeldOn() + ". A result can be uploaded from the day of the exam (rule A4)."));
		}
		Parsed parsed = OfflineResultFile.parse(upload.bytes(), upload.examType(), upload.passMark());
		for (Problem problem : parsed.problems()) {
			problems.add(new ProblemView(problem.rowNumber(), problem.roll(), problem.code(), problem.params(), problem.message()));
		}
		Map<String, Student> rolls = rollsOf(classItem.getBatchId());
		Map<String, List<Student>> byNumber = byNumber(rolls.values());
		Map<UUID, OfflineResultFile.Row> firstRowOf = new HashMap<>();
		List<WarningView> warnings = new ArrayList<>();
		List<ResultRowView> found = new ArrayList<>();
		for (OfflineResultFile.Row row : parsed.rows()) {
			Student student = rolls.get(row.roll());
			if (student == null) {
				student = zeroPadded(byNumber, row.roll());
				if (student != null) {
					warnings.add(new WarningView(row.rowNumber(), row.roll(), "ROLL_MATCHED_ZERO_PADDED",
							params("roll", row.roll(), "matched", student.roll()),
							"Row " + row.rowNumber() + ": roll " + row.roll() + " matched " + student.roll() + "."));
				}
			}
			if (student != null) {
				OfflineResultFile.Row first = firstRowOf.putIfAbsent(student.admissionId(), row);
				if (first != null && !first.roll().equals(row.roll())) {
					problems.add(new ProblemView(row.rowNumber(), row.roll(), "ROW_DUPLICATE_ROLL",
							params("roll", row.roll(), "first", first.rowNumber()),
							"Roll " + row.roll() + " is in the sheet more than once (first on row " + first.rowNumber() + ")."));
				}
			}
			else {
				boolean elsewhere = jdbc.queryForObject("select count(*) from offline_admissions where roll_no = ? and batch_id is distinct from ?",
						Integer.class, row.roll(), classItem.getBatchId()) > 0;
				problems.add(new ProblemView(row.rowNumber(), row.roll(), elsewhere ? "ROLL_IN_OTHER_BATCH" : "ROLL_NOT_IN_BATCH",
						params("roll", row.roll()), elsewhere ? ROLL_IN_OTHER_BATCH : NO_ROLL_IN_BATCH));
			}
			found.add(new ResultRowView(null, row.rowNumber(), student == null ? row.roll() : student.roll(),
					student == null ? null : student.admissionId(), student == null ? null : student.name(), false, row.right(), row.wrong(),
					row.mcq(), row.written(), row.total(), row.rank(), row.passed(), row.fileTotal(), row.fileRank(), row.mismatch(),
					row.maybeAbsent()));
		}
		Set<UUID> matched = firstRowOf.keySet();
		List<ResultRowView> absent = new ArrayList<>();
		for (Student expected : expectedStudents(classItem.getBatchId(), classItem.getHeldOn())) {
			if (!matched.contains(expected.admissionId())) {
				absent.add(new ResultRowView(null, null, expected.roll(), expected.admissionId(), expected.name(), true, null, null, null,
						null, null, null, null, null, null, false, false));
			}
		}
		absent.sort(Comparator.comparing(ResultRowView::roll, Comparator.nullsLast(OfflineResultService::compareRolls)));
		found.addAll(absent);
		int mismatched = (int) found.stream().filter(ResultRowView::mismatch).count();
		int absentCount = absent.size();
		int matchedCount = (int) found.stream().filter(row -> row.admissionId() != null && !row.absent()).count();
		Preview preview = new Preview(upload.examType(), parsed.fullMarks(), parsed.negative(), upload.passMark(),
				new Counts(found.size(), matchedCount, absentCount, mismatched), found, problems, warnings);
		return new Analysis(classItem, parsed, found, preview);
	}

	/** The batch's students by roll, as the roll is matched: any admission of the batch with a roll, closed ones too (rule A3). */
	private Map<String, Student> rollsOf(UUID batchId) {
		Map<String, Student> rolls = new HashMap<>();
		List<Student> students = jdbc.query("""
				select a.id, a.roll_no, coalesce(sp.full_name, a.full_name) as name from offline_admissions a
				left join student_profiles sp on sp.user_id = a.student_id
				where a.batch_id = ? and a.roll_no is not null
				""", (rs, row) -> new Student(rs.getObject("id", UUID.class), rs.getString("roll_no"), rs.getString("name")), batchId);
		for (Student student : students) {
			rolls.put(student.roll(), student);
		}
		return rolls;
	}

	/** The batch's students by their roll's number, with the leading zeros dropped (rule F9). */
	private static Map<String, List<Student>> byNumber(Collection<Student> students) {
		Map<String, List<Student>> byNumber = new HashMap<>();
		for (Student student : students) {
			byNumber.computeIfAbsent(numberOf(student.roll()), key -> new ArrayList<>()).add(student);
		}
		return byNumber;
	}

	/**
	 * The batch student whose roll has the same number as a sheet roll with no exact match, when exactly one batch roll has that number (rule
	 * F9). Excel stores a typed 0123 as 123, so the sheet's roll may lack its zeros. Null when there is no such student or more than one.
	 */
	private static Student zeroPadded(Map<String, List<Student>> byNumber, String roll) {
		if (!roll.matches("\\d+")) {
			return null;
		}
		List<Student> same = byNumber.getOrDefault(numberOf(roll), List.of());
		return same.size() == 1 ? same.get(0) : null;
	}

	/** Digits with the leading zeros dropped: "0123" is "123", and "000" is "0". */
	static String numberOf(String digits) {
		return digits.replaceFirst("^0+(?=\\d)", "");
	}

	/**
	 * The students expected at an exam (rule A2): onboarded to the batch on or before its date, and not closed before it (a student closed on
	 * the day is still expected). Only these can be absent.
	 */
	private List<Student> expectedStudents(UUID batchId, LocalDate heldOn) {
		return jdbc.query("""
				select a.id, a.roll_no, coalesce(sp.full_name, a.full_name) as name from offline_admissions a
				left join student_profiles sp on sp.user_id = a.student_id
				where a.batch_id = ? and a.onboarded_at is not null
				  and (a.onboarded_at at time zone 'Asia/Dhaka')::date <= ?
				  and (a.closed_at is null or (a.closed_at at time zone 'Asia/Dhaka')::date >= ?)
				order by a.roll_no
				""", (rs, row) -> new Student(rs.getObject("id", UUID.class), rs.getString("roll_no"), rs.getString("name")), batchId, heldOn,
				heldOn);
	}

	/**
	 * The students a sheet is announced to (plan F3): the distinct onboarded students whose admission has a row in it, present or absent. A
	 * student onboarded after the exam has no row, so is not told. Also the count the web shows as notifyCount.
	 */
	private List<UUID> notifiedStudents(UUID sheetId) {
		return jdbc.queryForList("""
				select distinct a.student_id from offline_result_rows r
				join offline_admissions a on a.id = r.admission_id
				where r.sheet_id = ? and a.status = 'ONBOARDED' and a.student_id is not null
				""", UUID.class, sheetId);
	}

	/**
	 * The matched rows of a staged sheet whose admission is now closed, or whose roll has changed since the save (plan F12). Publishing is
	 * not stopped by them; the list only tells staff what changed.
	 */
	private List<ChangedRow> changedSinceSave(UUID batchId, List<OfflineResultRow> stored, Map<UUID, String> names) {
		Map<UUID, Current> now = new HashMap<>();
		for (Current current : jdbc.query("select id, roll_no, status from offline_admissions where batch_id = ?",
				(rs, row) -> new Current(rs.getObject("id", UUID.class), rs.getString("roll_no"), rs.getString("status")), batchId)) {
			now.put(current.id(), current);
		}
		List<ChangedRow> changed = new ArrayList<>();
		for (OfflineResultRow row : stored) {
			Current current = row.getAdmissionId() == null || row.isAbsent() ? null : now.get(row.getAdmissionId());
			if (current == null) {
				continue;
			}
			boolean closed = OfflineAdmission.CLOSED.equals(current.status());
			if (closed || !Objects.equals(row.getRollNo(), current.roll())) {
				changed.add(new ChangedRow(row.getAdmissionId(), names.get(row.getAdmissionId()), row.getRollNo(), current.roll(), closed));
			}
		}
		changed.sort(Comparator.comparing(ChangedRow::oldRoll, Comparator.nullsLast(OfflineResultService::compareRolls)));
		return changed;
	}

	/** The names of the batch's admissions, by admission id, for the rows of a stored sheet. */
	private Map<UUID, String> namesOf(UUID batchId) {
		Map<UUID, String> names = new HashMap<>();
		List<Student> students = jdbc.query("""
				select a.id, null as roll_no, coalesce(sp.full_name, a.full_name) as name from offline_admissions a
				left join student_profiles sp on sp.user_id = a.student_id
				where a.batch_id = ?
				""", (rs, row) -> new Student(rs.getObject("id", UUID.class), null, rs.getString("name")), batchId);
		for (Student student : students) {
			names.put(student.admissionId(), student.name());
		}
		return names;
	}

	private Map<UUID, String> instructorNames() {
		Map<UUID, String> names = new HashMap<>();
		for (OfflineInstructor instructor : instructors.findAllByOrderByNameAsc()) {
			names.put(instructor.getId(), instructor.getName());
		}
		return names;
	}

	private SheetView sheetView(OfflineResultSheet sheet) {
		Map<UUID, String> names = namesOf(sheet.getBatchId());
		List<OfflineResultRow> stored = rows.findBySheetId(sheet.getId());
		List<ResultRowView> views = new ArrayList<>();
		for (OfflineResultRow row : stored) {
			boolean absent = row.isAbsent();
			boolean maybeAbsent = !absent && isZero(row.getRightCount()) && isZero(row.getWrongCount()) && isZero(row.getWritten());
			views.add(new ResultRowView(row.getId(), row.getRowNumber(), row.getRollNo(), row.getAdmissionId(),
					row.getAdmissionId() == null ? null : names.get(row.getAdmissionId()), absent, row.getRightCount(), row.getWrongCount(),
					row.getMcq(), row.getWritten(), row.getTotal(), row.getRank(), row.getPassed(), row.getFileTotal(), row.getFileRank(),
					OfflineResultFile.mismatch(row.getTotal(), row.getFileTotal(), row.getRank(), row.getFileRank()), maybeAbsent));
		}
		views.sort(ROW_ORDER);
		boolean staged = OfflineResultSheet.STATUS_STAGED.equals(sheet.getStatus());
		List<ChangedRow> changed = staged ? changedSinceSave(sheet.getBatchId(), stored, names) : List.of();
		return new SheetView(sheet.getId(), sheet.getStatus(), sheet.getExamType(), sheet.getFullMarks(), sheet.getNegative(),
				sheet.getPassMark(), sheet.getFileName(), publisherName(sheet.getUploadedBy()), sheet.getUploadedAt(),
				publisherName(sheet.getPublishedBy()), sheet.getPublishedAt(), views, notifiedStudents(sheet.getId()).size(), changed);
	}

	private String publisherName(UUID user) {
		return user == null ? null : matcher.nameOf(user);
	}

	private OfflineClass findClass(UUID classId) {
		return classes.findById(classId).orElseThrow(OfflineSupport::notFound);
	}

	/**
	 * Locks the exam class for the rest of the transaction (plan F2). A save, a publish and a discard take it first, so the one for an exam
	 * that runs second sees the first one's result, and a unique index that still trips is answered as RESULT_CHANGED.
	 */
	private OfflineClass lockClass(UUID classId) {
		return classes.findForUpdate(classId).orElseThrow(OfflineSupport::notFound);
	}

	/** The uploaded file's bytes and its name, checked for presence and size. The exam type and pass mark are checked too. */
	private static Upload upload(MultipartFile file, String examType, String passMark) {
		List<FieldErrorDetail> problems = new ArrayList<>();
		if (file == null || file.isEmpty()) {
			problems.add(new FieldErrorDetail("file", "Choose the result sheet (.xlsx)."));
		}
		else if (file.getSize() > MAX_FILE_BYTES) {
			throw new ApiException(ErrorCode.FILE_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE, "The file is larger than 5 MB.");
		}
		String type = examType == null ? "" : examType.trim().toUpperCase(Locale.ROOT);
		if (!OfflineResultFile.MCQ.equals(type) && !OfflineResultFile.MCQ_WRITTEN.equals(type) && !OfflineResultFile.WRITTEN.equals(type)) {
			problems.add(new FieldErrorDetail("examType", "Choose MCQ, MCQ + Written, or Written."));
		}
		BigDecimal pass = null;
		if (passMark != null && !passMark.isBlank()) {
			try {
				pass = new BigDecimal(passMark.trim());
				if (pass.signum() < 0) {
					problems.add(new FieldErrorDetail("passMark", "The pass mark cannot be below zero."));
				}
			}
			catch (NumberFormatException exception) {
				problems.add(new FieldErrorDetail("passMark", "Enter the pass mark as a number, or leave it empty."));
			}
		}
		if (!problems.isEmpty()) {
			throw validation(problems);
		}
		try {
			byte[] bytes = file.getBytes();
			String name = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank() ? "result.xlsx" : file.getOriginalFilename().trim();
			return new Upload(bytes, name.length() > 500 ? name.substring(0, 500) : name, file.getContentType(), type, pass);
		}
		catch (IOException exception) {
			throw new IllegalStateException("Could not read the uploaded file.", exception);
		}
	}

	/** The answer for a save, publish or discard that a racing request won (plan F2). */
	private static ApiException resultChanged() {
		return new ApiException(ErrorCode.RESULT_CHANGED, HttpStatus.CONFLICT, "Someone else changed this result first. Reload and try again.");
	}

	private static String describe(ProblemView problem) {
		String where = problem.rowNumber() == null ? "" : "Row " + problem.rowNumber() + (problem.roll() == null ? "" : ", roll "
				+ problem.roll()) + ": ";
		return where + problem.message();
	}

	private static boolean isZero(Number value) {
		return value == null || (value instanceof BigDecimal decimal ? decimal.signum() == 0 : value.intValue() == 0);
	}

	/** Roll numbers compare as numbers when both are digits, then as text. */
	static int compareRolls(String a, String b) {
		if (a.matches("\\d+") && b.matches("\\d+") && a.length() != b.length()) {
			return Integer.compare(a.length(), b.length());
		}
		return a.compareTo(b);
	}

	/** Rows in the order the sheet is read: the file's rows first, then the absent students by roll. */
	static final Comparator<ResultRowView> ROW_ORDER = Comparator.comparing(ResultRowView::absent)
		.thenComparing(ResultRowView::rowNumber, Comparator.nullsLast(Comparator.naturalOrder()))
		.thenComparing(ResultRowView::roll, Comparator.nullsLast(OfflineResultService::compareRolls));

	// ---- records ----------------------------------------------------------------------------------------------------------

	/** An expected or matched student of the batch: the admission, the roll and the name. */
	record Student(UUID admissionId, String roll, String name) {
	}

	/** The current roll and status of an admission of the batch (for the changes since a save). */
	private record Current(UUID id, String roll, String status) {
	}

	/** One upload, read into memory, with its checked type and pass mark. */
	record Upload(byte[] bytes, String fileName, String contentType, String examType, BigDecimal passMark) {
	}

	/** The result of an analysis: the class, the parsed sheet, the rows that a save would store, and the preview. */
	record Analysis(OfflineClass classItem, Parsed parsed, List<ResultRowView> rows, Preview preview) {
	}

	/**
	 * The preview answer: the type, the marks read from the sheet, the pass mark, the counts, every row, the problems and the warnings. The
	 * warnings are only the ones the rows cannot show (the absent and the differing rows are shown from the rows).
	 */
	@JsonInclude(JsonInclude.Include.ALWAYS)
	public record Preview(String examType, BigDecimal fullMarks, BigDecimal negative, BigDecimal passMark, Counts counts,
			List<ResultRowView> rows, List<ProblemView> problems, List<WarningView> warnings) {
	}

	@JsonInclude(JsonInclude.Include.ALWAYS)
	public record Counts(int rows, int matched, int absent, int mismatched) {
	}

	/**
	 * A blocking problem: the row (null for the whole file), its roll, its code and parameters for translation (plan F15), and the English
	 * message, kept for logs and older clients.
	 */
	@JsonInclude(JsonInclude.Include.ALWAYS)
	public record ProblemView(Integer rowNumber, String roll, String code, Map<String, Object> params, String message) {
	}

	/** A warning that does not block the save, with its code and parameters and the English message (plan F9, F15). */
	@JsonInclude(JsonInclude.Include.ALWAYS)
	public record WarningView(Integer rowNumber, String roll, String code, Map<String, Object> params, String message) {
	}

	/**
	 * One student row of a sheet, as the preview and the sheet view show it. {@code id} is set on a stored row only. {@code mismatch} says the
	 * file's own total or position differs from the calculated one; {@code maybeAbsent} says right, wrong and written are all 0 (rule R2).
	 */
	@JsonInclude(JsonInclude.Include.ALWAYS)
	public record ResultRowView(@JsonInclude(JsonInclude.Include.NON_NULL) UUID id, Integer rowNumber, String roll, UUID admissionId,
			String studentName, boolean absent, Integer right, Integer wrong, BigDecimal mcq, BigDecimal written, BigDecimal total,
			Integer rank, Boolean passed, BigDecimal fileTotal, Integer fileRank, boolean mismatch, boolean maybeAbsent) {
	}

	/**
	 * A matched student of a staged sheet whose admission changed after the save: closed now, or a new roll (plan F12). {@code newRoll} is the
	 * roll the admission has now; {@code closed} is true when it is closed.
	 */
	@JsonInclude(JsonInclude.Include.ALWAYS)
	public record ChangedRow(UUID admissionId, String name, String oldRoll, String newRoll, boolean closed) {
	}

	/**
	 * One stored sheet: its status, marks, file, who uploaded and published it, and its rows. {@code notifyCount} is the number of onboarded
	 * students with a row in it (the ones a publish tells). {@code changedSinceSave} is set on the staged sheet only (plan F12).
	 */
	@JsonInclude(JsonInclude.Include.ALWAYS)
	public record SheetView(UUID id, String status, String examType, BigDecimal fullMarks, BigDecimal negative, BigDecimal passMark,
			String fileName, String uploadedBy, Instant uploadedAt, String publishedBy, Instant publishedAt, List<ResultRowView> rows,
			int notifyCount, List<ChangedRow> changedSinceSave) {
	}

	/** Both sheets of one exam. Either may be null. */
	@JsonInclude(JsonInclude.Include.ALWAYS)
	public record ClassResults(UUID classId, UUID batchId, String subject, String topic, LocalDate heldOn, boolean cancelled,
			SheetView published, SheetView staged) {
	}

	/**
	 * One exam class of the batch in the Results tab. {@code status} is the class's stored status (for example NOT_TAKEN or CANCELLED); a
	 * class that is not taken or cancelled takes no result.
	 */
	@JsonInclude(JsonInclude.Include.ALWAYS)
	public record BatchExam(UUID classId, LocalDate heldOn, String subject, String topic, String instructorName, String kind, String status,
			boolean cancelled, String state, Instant publishedAt, int rowCount) {
	}

	/** The uploaded file: its stream, type, size and name. The caller closes the stream through the response. */
	public record SheetFile(InputStream body, String contentType, long byteSize, String fileName) {
	}

}
