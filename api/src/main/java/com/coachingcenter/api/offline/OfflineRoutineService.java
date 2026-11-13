package com.coachingcenter.api.offline;

import static com.coachingcenter.api.offline.OfflineSupport.notFound;
import static com.coachingcenter.api.offline.OfflineSupport.state;
import static com.coachingcenter.api.offline.OfflineSupport.validation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
import com.coachingcenter.api.offline.OfflineBatchService.RoutineFile;
import com.coachingcenter.api.offline.OfflineInstructorService.InstructorView;
import com.coachingcenter.api.offline.OfflineRoutineMatcher.Action;
import com.coachingcenter.api.offline.OfflineRoutineMatcher.Plan;
import com.coachingcenter.api.offline.OfflineRoutineMatcher.RowPlan;
import com.coachingcenter.api.offline.OfflineRoutineReader.Parsed;
import com.coachingcenter.api.offline.OfflineRoutineRow.Flag;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Monthly routines of a batch (plan sections 4.2, 4.4, 5 and 6, stage 2). An upload reads the PDF into a PREVIEW routine that the staff
 * check; nothing reaches the classes until the confirm. The confirm applies the rows to the batch's classes in one transaction, makes the
 * student copy, supersedes the earlier routine of the same month and tells the onboarded students once. A confirmed routine is never
 * changed; a new upload of the month is the only way to replace it.
 */
@Service
public class OfflineRoutineService {

	public static final String ENTITY = "OFFLINE_ROUTINE";

	public static final String UPLOADED = "OFFLINE_ROUTINE_UPLOADED";

	public static final String ROW_EDITED = "OFFLINE_ROUTINE_ROW_EDITED";

	public static final String INSTRUCTOR_MATCHED = "OFFLINE_ROUTINE_INSTRUCTOR_MATCHED";

	public static final String CONFIRMED = "OFFLINE_ROUTINE_CONFIRMED";

	public static final String DISCARDED = "OFFLINE_ROUTINE_DISCARDED";

	private static final String PDF = "application/pdf";

	private static final String STUDENT_COPY_NAME = "routine-student-copy.pdf";

	private static final int MAX_SUBJECT = 200;

	private static final int MAX_TOPIC = 500;

	private static final int MAX_INSTRUCTOR_TEXT = 200;

	private static final Set<String> KINDS = Set.of(OfflineClass.KIND_LECTURE, OfflineClass.KIND_EXAM, OfflineClass.KIND_OTHER);

	private final OfflineBatchRepository batches;

	private final OfflineBatchRoutineRepository routines;

	private final OfflineClassRepository classes;

	private final OfflineInstructorRepository instructors;

	private final OfflineInstructorService instructorService;

	private final OfflineBatchService batchService;

	private final PdfUploads uploads;

	private final LocalFileStorage files;

	private final AuditRecorder audit;

	private final JdbcTemplate jdbc;

	private final ObjectMapper json;

	private final Clock clock;

	public OfflineRoutineService(OfflineBatchRepository batches, OfflineBatchRoutineRepository routines, OfflineClassRepository classes,
			OfflineInstructorRepository instructors, OfflineInstructorService instructorService, OfflineBatchService batchService,
			PdfUploads uploads, LocalFileStorage files, AuditRecorder audit, JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
		this.batches = batches;
		this.routines = routines;
		this.classes = classes;
		this.instructors = instructors;
		this.instructorService = instructorService;
		this.batchService = batchService;
		this.uploads = uploads;
		this.files = files;
		this.audit = audit;
		this.jdbc = jdbc;
		this.json = json;
		this.clock = clock;
	}

	/**
	 * Reads a routine PDF for one month into a PREVIEW. A PDF without the routine's header row is refused, and nothing is saved. The
	 * original PDF is kept for staff. The month may already have a confirmed routine: the preview then shows the difference.
	 */
	@Transactional
	public RoutineView upload(UUID batchId, String month, MultipartFile file, UUID actorId) {
		OfflineBatch batch = findBatch(batchId);
		if (OfflineBatch.STATUS_ARCHIVED.equals(batch.getStatus())) {
			throw state("This batch is archived, so its routine cannot be uploaded.");
		}
		YearMonth ym = yearMonth(month);
		byte[] bytes = pdfBytes(file);
		Parsed parsed = OfflineRoutineReader.read(bytes);
		List<OfflineRoutineRow> rows = OfflineRoutineRules.build(parsed.rows(), ym, batch.getDefaultClassMinutes(), index());
		LocalFileStorage.StoredFile stored = uploads.store(file, OfflineBatchService.MAX_ROUTINE_BYTES);
		try {
			LocalDate first = ym.atDay(1);
			OfflineBatchRoutine routine = new OfflineBatchRoutine();
			routine.setId(UUID.randomUUID());
			routine.setBatchId(batchId);
			routine.setMonth(first);
			routine.setOriginalFile(stored.storedFileName());
			routine.setOriginalName(originalName(stored.originalFileName()));
			routine.setOriginalByteSize(stored.byteSize());
			routine.setRevision(nextRevision(batchId, first));
			routine.setStatus(OfflineBatchRoutine.STATUS_PREVIEW);
			routine.setLegacyOriginal(false);
			routine.setUploadedBy(actorId);
			routine.setUploadedAt(clock.instant());
			routine.setParseReport(reportJson(ym, batch.getDefaultClassMinutes(), parsed.pageCount(), rows));
			routines.saveAndFlush(routine);
			audit.record(UPLOADED, ENTITY, routine.getId());
			return view(routine);
		}
		catch (RuntimeException exception) {
			files.delete(stored.storedFileName());
			throw exception;
		}
	}

	/** The routines of a batch, the newest month first and the latest revision first within a month. */
	@Transactional(readOnly = true)
	public List<RoutineSummary> list(UUID batchId) {
		findBatch(batchId);
		List<RoutineSummary> summaries = new ArrayList<>();
		for (OfflineBatchRoutine routine : routines.findByBatchIdOrderByMonthDescRevisionDesc(batchId)) {
			Report report = report(routine);
			List<String> blockers = blockers(report.rows());
			summaries.add(new RoutineSummary(routine.getId(), YearMonth.from(routine.getMonth()).toString(), routine.getRevision(),
					routine.getStatus(), routine.isLegacyOriginal(), routine.getOriginalName(), routine.getStudentFile() != null,
					routine.getUploadedAt(), routine.getConfirmedAt(), report.rows().size(), flagged(report.rows()),
					OfflineBatchRoutine.STATUS_PREVIEW.equals(routine.getStatus()) && blockers.isEmpty()));
		}
		return summaries;
	}

	@Transactional(readOnly = true)
	public RoutineView get(UUID id) {
		return view(find(id));
	}

	/** Changes one row of a PREVIEW. The fields the body names change; the others stay. The flags are worked out again. */
	@Transactional
	public RoutineView editRow(UUID id, int index, JsonNode body, UUID actorId) {
		OfflineBatchRoutine routine = find(id);
		requirePreview(routine);
		Report report = report(routine);
		List<OfflineRoutineRow> rows = new ArrayList<>(report.rows());
		if (index < 0 || index >= rows.size()) {
			throw notFound();
		}
		OfflineRoutineRow old = rows.get(index);
		List<FieldErrorDetail> problems = new ArrayList<>();
		String date = old.date();
		String time = old.time();
		String startsAt = old.startsAt();
		String endsAt = old.endsAt();
		if (body.has("date")) {
			date = dateField(text(body, "date"), problems);
		}
		if (body.has("time")) {
			String text = text(body, "time");
			OfflineRoutineRules.Times times = OfflineRoutineRules.times(text, report.defaultClassMinutes());
			if (times == null) {
				problems.add(new FieldErrorDetail("time", "Enter the time as 5.10 PM or 9.00AM-10.20AM."));
			}
			else {
				time = text.replaceAll("\\s", "");
				startsAt = times.start().toString();
				endsAt = times.end().toString();
			}
		}
		if (body.has("startsAt")) {
			startsAt = clockField(text(body, "startsAt"), "startsAt", problems);
		}
		if (body.has("endsAt")) {
			endsAt = clockField(text(body, "endsAt"), "endsAt", problems);
		}
		String day = body.has("day") ? text(body, "day") : old.day();
		if ((day == null || day.isBlank()) && date != null) {
			day = OfflineRoutineRules.weekday(LocalDate.parse(date));
		}
		String subject = body.has("subject") ? clip(text(body, "subject"), MAX_SUBJECT) : old.subject();
		String topic = body.has("topic") ? clip(text(body, "topic"), MAX_TOPIC) : old.topic();
		String kind = old.kind();
		if (body.has("kind")) {
			kind = text(body, "kind").toUpperCase(Locale.ROOT);
			if (!KINDS.contains(kind)) {
				problems.add(new FieldErrorDetail("kind", "Choose lecture, exam or other."));
			}
		}
		String instructorText = body.has("instructorText") ? clip(text(body, "instructorText"), MAX_INSTRUCTOR_TEXT) : old.instructorText();
		UUID instructorId = old.instructorId();
		if (body.has("instructorId")) {
			instructorId = liveInstructorId(body.get("instructorId"), problems);
		}
		else if (!Objects.equals(instructorText, old.instructorText())) {
			instructorId = instructorText.isEmpty() ? null : index().get(OfflineRoutineRules.key(instructorText));
		}
		if (!problems.isEmpty()) {
			throw validation(problems);
		}
		rows.set(index, new OfflineRoutineRow(old.page(), date, day, time, startsAt, endsAt, subject, topic, kind, instructorText,
				instructorId, List.of()));
		save(routine, report, rows);
		audit.record(ROW_EDITED, ENTITY, id);
		return view(routine);
	}

	/**
	 * Matches an unknown name in the routine to an existing instructor, or to a new one, for every row with that name (plan G4). The name
	 * the routine used is added to the instructor's aliases, so the next routine matches it on its own.
	 */
	@Transactional
	public RoutineView matchInstructor(UUID id, JsonNode body, UUID actorId) {
		OfflineBatchRoutine routine = find(id);
		requirePreview(routine);
		List<FieldErrorDetail> problems = new ArrayList<>();
		String name = clip(text(body, "name"), MAX_INSTRUCTOR_TEXT);
		if (name.isEmpty()) {
			problems.add(new FieldErrorDetail("name", "Enter the name as the routine writes it."));
		}
		UUID instructorId = liveInstructorId(body.get("instructorId"), problems);
		String newName = clip(text(body, "newName"), 200);
		if (instructorId == null && newName.isEmpty() && problems.isEmpty()) {
			problems.add(new FieldErrorDetail("instructorId", "Choose an instructor, or enter a new name."));
		}
		if (!problems.isEmpty()) {
			throw validation(problems);
		}
		OfflineInstructor target = instructorId != null ? instructors.findById(instructorId).orElseThrow(OfflineSupport::notFound)
				: existingOrNew(newName, actorId);
		Instant now = clock.instant();
		if (!OfflineRoutineRules.key(target.getName()).equals(OfflineRoutineRules.key(name))) {
			String[] aliases = target.getAliases() == null ? new String[0] : target.getAliases();
			boolean known = Arrays.stream(aliases).anyMatch(alias -> OfflineRoutineRules.key(alias).equals(OfflineRoutineRules.key(name)));
			if (!known) {
				String[] more = Arrays.copyOf(aliases, aliases.length + 1);
				more[aliases.length] = name;
				target.setAliases(more);
				target.setUpdatedAt(now);
				instructors.saveAndFlush(target);
			}
		}
		Report report = report(routine);
		List<OfflineRoutineRow> rows = new ArrayList<>();
		for (OfflineRoutineRow row : report.rows()) {
			boolean same = !row.instructorText().isEmpty() && OfflineRoutineRules.key(row.instructorText()).equals(OfflineRoutineRules.key(name));
			rows.add(same ? new OfflineRoutineRow(row.page(), row.date(), row.day(), row.time(), row.startsAt(), row.endsAt(),
					row.subject(), row.topic(), row.kind(), row.instructorText(), target.getId(), row.flags()) : row);
		}
		save(routine, report, rows);
		audit.record(INSTRUCTOR_MATCHED, ENTITY, id);
		return view(routine);
	}

	/**
	 * Confirms a PREVIEW. Refused while a row lacks a date, time or subject, or while a name is not matched; flags do not block. The classes
	 * of the batch change in this transaction: matched unsettled classes take the new values, settled ones stay, new rows are added and
	 * unsettled classes of the month that no row matches are cancelled.
	 */
	@Transactional
	public RoutineView confirm(UUID id, UUID actorId) {
		OfflineBatchRoutine routine = find(id);
		if (OfflineBatchRoutine.STATUS_CONFIRMED.equals(routine.getStatus())) {
			throw state("This routine is already confirmed.");
		}
		requirePreview(routine);
		OfflineBatch batch = findBatch(routine.getBatchId());
		if (OfflineBatch.STATUS_ARCHIVED.equals(batch.getStatus())) {
			throw state("This batch is archived, so its routine cannot be confirmed.");
		}
		Report report = report(routine);
		List<String> blockers = blockers(report.rows());
		if (!blockers.isEmpty()) {
			throw state("This routine cannot be confirmed yet. " + String.join(" ", blockers));
		}
		LocalDate month = routine.getMonth();
		Instant now = clock.instant();
		Plan plan = OfflineRoutineMatcher.plan(report.rows(), classes.findByBatchIdOrderByHeldOnAscStartsAtAsc(batch.getId()),
				monthRoutines(batch.getId(), month));
		LocalFileStorage.StoredFile copy = null;
		try {
			for (RowPlan planned : plan.rows()) {
				OfflineRoutineRow row = report.rows().get(planned.index());
				if (planned.action() == Action.ADD) {
					classes.save(newClass(batch.getId(), routine.getId(), row, now));
				}
				else if (planned.action() == Action.UPDATE) {
					OfflineClass existing = planned.existing();
					apply(existing, row, routine.getId(), now, batch.getId());
					classes.save(existing);
				}
			}
			for (OfflineClass cancelled : plan.cancel()) {
				cancelled.setStatus(OfflineClass.STATUS_CANCELLED);
				cancelled.setCancelledAt(now);
				cancelled.setUpdatedAt(now);
				classes.save(cancelled);
			}
			routines.findByBatchIdAndMonthAndStatus(batch.getId(), month, OfflineBatchRoutine.STATUS_CONFIRMED).ifPresent(prior -> {
				prior.setStatus(OfflineBatchRoutine.STATUS_SUPERSEDED);
				routines.saveAndFlush(prior);
			});
			int revision = confirmedRevision(batch.getId(), month) + 1;
			byte[] original = originalBytes(routine);
			byte[] studentCopy = OfflineRoutineStudentCopy.render(original, OfflineRoutineReader.read(original).rows());
			copy = files.save(STUDENT_COPY_NAME, PDF, new ByteArrayInputStream(studentCopy), studentCopy.length,
					OfflineBatchService.MAX_ROUTINE_BYTES);
			routine.setStatus(OfflineBatchRoutine.STATUS_CONFIRMED);
			routine.setConfirmedBy(actorId);
			routine.setConfirmedAt(now);
			routine.setRevision(revision);
			routine.setStudentFile(copy.storedFileName());
			routine.setStudentByteSize((long) studentCopy.length);
			routines.saveAndFlush(routine);
			batchService.notifyOnboardedStudents(batch.getId(), now);
			audit.record(CONFIRMED, ENTITY, routine.getId());
			return view(routine);
		}
		catch (RuntimeException exception) {
			if (copy != null) {
				files.delete(copy.storedFileName());
			}
			throw exception;
		}
	}

	/** Discards a PREVIEW. A confirmed routine stays as the record. */
	@Transactional
	public void discard(UUID id, UUID actorId) {
		OfflineBatchRoutine routine = find(id);
		requirePreview(routine);
		String original = routine.getOriginalFile();
		routines.delete(routine);
		files.commitReplacement(null, original);
		audit.record(DISCARDED, ENTITY, id);
	}

	/** The original PDF or the student copy of a routine, for staff. 404 when the routine has no such file. */
	@Transactional(readOnly = true)
	public RoutineFile openFile(UUID id, boolean student) {
		OfflineBatchRoutine routine = find(id);
		String stored = student ? routine.getStudentFile() : routine.getOriginalFile();
		if (stored == null) {
			throw notFound();
		}
		Path path = files.resolve(stored);
		if (!Files.exists(path)) {
			throw notFound();
		}
		try {
			InputStream body = Files.newInputStream(path);
			String month = YearMonth.from(routine.getMonth()).toString();
			String name = student ? "routine-" + month + "-student-copy.pdf"
					: routine.getOriginalName() == null ? "routine-" + month + ".pdf" : routine.getOriginalName();
			long size = Files.size(path);
			return new RoutineFile(body, name, size);
		}
		catch (IOException exception) {
			throw notFound();
		}
	}

	/**
	 * The student copy of the latest confirmed routine of a batch, or of one month. A routine with no student copy (the old single routine,
	 * which carries names) is never served to a student: the answer is 404.
	 */
	@Transactional(readOnly = true)
	public RoutineFile studentCopy(UUID batchId, YearMonth month) {
		for (OfflineBatchRoutine routine : routines.findByBatchIdOrderByMonthDescRevisionDesc(batchId)) {
			if (!OfflineBatchRoutine.STATUS_CONFIRMED.equals(routine.getStatus())) {
				continue;
			}
			if (month != null && !month.equals(YearMonth.from(routine.getMonth()))) {
				continue;
			}
			if (routine.isLegacyOriginal() || routine.getStudentFile() == null) {
				throw notFound();
			}
			return openFile(routine.getId(), true);
		}
		throw notFound();
	}

	// ---- the view ---------------------------------------------------------------------------------------------------------

	private RoutineView view(OfflineBatchRoutine routine) {
		Report report = report(routine);
		Map<UUID, String> names = instructorNames();
		List<RowView> rows = new ArrayList<>();
		for (int index = 0; index < report.rows().size(); index++) {
			rows.add(rowView(index, report.rows().get(index), names));
		}
		Difference difference = null;
		if (OfflineBatchRoutine.STATUS_PREVIEW.equals(routine.getStatus())
				&& routines.findByBatchIdAndMonthAndStatus(routine.getBatchId(), routine.getMonth(),
						OfflineBatchRoutine.STATUS_CONFIRMED).isPresent()) {
			difference = difference(routine.getBatchId(), routine.getMonth(), report.rows(), names);
		}
		List<String> blockers = blockers(report.rows());
		return new RoutineView(routine.getId(), routine.getBatchId(), YearMonth.from(routine.getMonth()).toString(),
				routine.getRevision(), routine.getStatus(), routine.isLegacyOriginal(), routine.getOriginalName(),
				routine.getStudentFile() != null, routine.getUploadedAt(), routine.getConfirmedAt(), report.rows().size(),
				flagged(report.rows()), unmatched(report.rows()), blockers,
				OfflineBatchRoutine.STATUS_PREVIEW.equals(routine.getStatus()) && blockers.isEmpty(), rows, difference);
	}

	private Difference difference(UUID batchId, LocalDate month, List<OfflineRoutineRow> rows, Map<UUID, String> names) {
		Plan plan = OfflineRoutineMatcher.plan(rows, classes.findByBatchIdOrderByHeldOnAscStartsAtAsc(batchId), monthRoutines(batchId, month));
		List<RowView> added = new ArrayList<>();
		List<Change> changed = new ArrayList<>();
		List<ClassView> settled = new ArrayList<>();
		int unchanged = 0;
		for (RowPlan planned : plan.rows()) {
			RowView row = rowView(planned.index(), rows.get(planned.index()), names);
			if (planned.action() == Action.ADD) {
				added.add(row);
			}
			else if (planned.action() == Action.UPDATE && planned.changed()) {
				changed.add(new Change(classView(planned.existing()), row));
			}
			else if (planned.action() == Action.UPDATE) {
				unchanged++;
			}
			else {
				settled.add(classView(planned.existing()));
			}
		}
		List<ClassView> cancelled = new ArrayList<>();
		for (OfflineClass cancel : plan.cancel()) {
			cancelled.add(classView(cancel));
		}
		for (OfflineClass kept : plan.settledKept()) {
			settled.add(classView(kept));
		}
		return new Difference(added, changed, cancelled, settled, unchanged);
	}

	private static RowView rowView(int index, OfflineRoutineRow row, Map<UUID, String> names) {
		return new RowView(index, row.date(), row.day(), row.time(), row.startsAt(), row.endsAt(), row.subject(), row.topic(), row.kind(),
				row.instructorText(), row.instructorId(), row.instructorId() == null ? null : names.get(row.instructorId()), row.flags());
	}

	private static ClassView classView(OfflineClass item) {
		return new ClassView(item.getId(), item.getHeldOn().toString(), item.getStartsAt() == null ? null : item.getStartsAt().toString(),
				item.getSubject(), item.getTopic(), item.getInstructorText(), item.getStatus());
	}

	/** What stops a confirm: a row without a date, time or subject, and a name nobody matched yet. */
	private static List<String> blockers(List<OfflineRoutineRow> rows) {
		List<String> blockers = new ArrayList<>();
		for (int index = 0; index < rows.size(); index++) {
			OfflineRoutineRow row = rows.get(index);
			if (row.date() == null) {
				blockers.add("Row " + (index + 1) + " has no readable date.");
			}
			if (row.startsAt() == null) {
				blockers.add("Row " + (index + 1) + " has no readable time.");
			}
			if (row.subject() == null || row.subject().isBlank()) {
				blockers.add("Row " + (index + 1) + " has no subject.");
			}
		}
		for (UnmatchedName name : unmatched(rows)) {
			blockers.add("Match the instructor \"" + name.name() + "\" (" + name.rows() + (name.rows() == 1 ? " row" : " rows")
					+ ") to an instructor or add a new one.");
		}
		return blockers;
	}

	private static List<UnmatchedName> unmatched(List<OfflineRoutineRow> rows) {
		Map<String, UnmatchedName> found = new LinkedHashMap<>();
		for (OfflineRoutineRow row : rows) {
			if (row.instructorText() == null || row.instructorText().isEmpty() || row.instructorId() != null) {
				continue;
			}
			String key = OfflineRoutineRules.key(row.instructorText());
			UnmatchedName previous = found.get(key);
			found.put(key, new UnmatchedName(previous == null ? row.instructorText() : previous.name(),
					previous == null ? 1 : previous.rows() + 1));
		}
		return List.copyOf(found.values());
	}

	private static int flagged(List<OfflineRoutineRow> rows) {
		int count = 0;
		for (OfflineRoutineRow row : rows) {
			if (!row.flags().isEmpty()) {
				count++;
			}
		}
		return count;
	}

	// ---- the classes ------------------------------------------------------------------------------------------------------

	private OfflineClass newClass(UUID batchId, UUID routineId, OfflineRoutineRow row, Instant now) {
		OfflineClass item = new OfflineClass();
		item.setId(UUID.randomUUID());
		item.setBatchId(batchId);
		item.setCreatedAt(now);
		apply(item, row, routineId, now, batchId);
		item.setStatus(OfflineClass.STATUS_SCHEDULED);
		return item;
	}

	/** Copies the row's values onto a class and sets its due amount: the override, else the instructor's rate in the batch. */
	private void apply(OfflineClass item, OfflineRoutineRow row, UUID routineId, Instant now, UUID batchId) {
		item.setRoutineId(routineId);
		item.setHeldOn(LocalDate.parse(row.date()));
		item.setStartsAt(LocalTime.parse(row.startsAt()));
		item.setEndsAt(row.endsAt() == null ? null : LocalTime.parse(row.endsAt()));
		item.setSubject(cut(row.subject(), MAX_SUBJECT));
		item.setTopic(row.topic() == null || row.topic().isBlank() ? null : cut(row.topic(), MAX_TOPIC));
		item.setKind(row.kind());
		item.setInstructorId(row.instructorId());
		item.setInstructorText(row.instructorText() == null || row.instructorText().isEmpty() ? null
				: cut(row.instructorText(), MAX_INSTRUCTOR_TEXT));
		item.setAmount(item.getRateOverride() != null ? item.getRateOverride() : rate(batchId, row.instructorId()));
		item.setUpdatedAt(now);
	}

	private BigDecimal rate(UUID batchId, UUID instructorId) {
		if (instructorId == null) {
			return null;
		}
		List<BigDecimal> rates = jdbc.query("select rate from offline_batch_instructor_rates where batch_id = ? and instructor_id = ?",
				(rs, rowNum) -> rs.getBigDecimal("rate"), batchId, instructorId);
		return rates.isEmpty() ? null : rates.get(0);
	}

	/** The classes of the month's routines (confirmed or replaced): the only ones a row can cancel. */
	private Set<UUID> monthRoutines(UUID batchId, LocalDate month) {
		Set<UUID> ids = new HashSet<>();
		for (OfflineBatchRoutine routine : routines.findByBatchIdOrderByMonthDescRevisionDesc(batchId)) {
			if (month.equals(routine.getMonth()) && !OfflineBatchRoutine.STATUS_PREVIEW.equals(routine.getStatus())) {
				ids.add(routine.getId());
			}
		}
		return ids;
	}

	private int confirmedRevision(UUID batchId, LocalDate month) {
		int max = 0;
		for (OfflineBatchRoutine routine : routines.findByBatchIdOrderByMonthDescRevisionDesc(batchId)) {
			if (month.equals(routine.getMonth()) && !OfflineBatchRoutine.STATUS_PREVIEW.equals(routine.getStatus())) {
				max = Math.max(max, routine.getRevision());
			}
		}
		return max;
	}

	private int nextRevision(UUID batchId, LocalDate month) {
		return confirmedRevision(batchId, month) + 1;
	}

	// ---- helpers ----------------------------------------------------------------------------------------------------------

	/** The routine's stored rows and counts. A routine with no stored report (the old single routine) has no rows. */
	private Report report(OfflineBatchRoutine routine) {
		if (routine.getParseReport() == null) {
			return new Report(YearMonth.from(routine.getMonth()).toString(), 0, 0, List.of());
		}
		return json.readValue(routine.getParseReport(), Report.class);
	}

	private String reportJson(YearMonth month, int minutes, int pageCount, List<OfflineRoutineRow> rows) {
		return json.writeValueAsString(new Report(month.toString(), minutes, pageCount, rows));
	}

	private void save(OfflineBatchRoutine routine, Report report, List<OfflineRoutineRow> rows) {
		YearMonth month = YearMonth.from(routine.getMonth());
		routine.setParseReport(reportJson(month, report.defaultClassMinutes(), report.pageCount(), OfflineRoutineRules.flag(rows, month)));
		routines.saveAndFlush(routine);
	}

	private void requirePreview(OfflineBatchRoutine routine) {
		if (OfflineBatchRoutine.STATUS_CONFIRMED.equals(routine.getStatus())) {
			throw state("This routine is confirmed, so its rows cannot be changed. Upload a new routine for the month to change it.");
		}
		if (!OfflineBatchRoutine.STATUS_PREVIEW.equals(routine.getStatus())) {
			throw state("This routine was replaced by a later upload, so it cannot be changed.");
		}
	}

	private OfflineInstructor existingOrNew(String name, UUID actorId) {
		String key = OfflineRoutineRules.key(name);
		for (OfflineInstructor instructor : instructors.findByArchivedAtIsNullOrderByNameAsc()) {
			if (OfflineRoutineRules.key(instructor.getName()).equals(key)) {
				return instructor;
			}
		}
		InstructorView created = instructorService.create(actorId, json.createObjectNode().put("name", name));
		return instructors.findById(created.id()).orElseThrow(OfflineSupport::notFound);
	}

	private UUID liveInstructorId(JsonNode node, List<FieldErrorDetail> problems) {
		if (node == null || node.isNull() || node.asText().isBlank()) {
			return null;
		}
		try {
			UUID id = UUID.fromString(node.asText().trim());
			Optional<OfflineInstructor> found = instructors.findById(id).filter(instructor -> instructor.getArchivedAt() == null);
			if (found.isPresent()) {
				return id;
			}
		}
		catch (IllegalArgumentException exception) {
			// reported below
		}
		problems.add(new FieldErrorDetail("instructorId", "Choose one from the list."));
		return null;
	}

	private Map<String, UUID> index() {
		return OfflineRoutineRules.index(instructors.findByArchivedAtIsNullOrderByNameAsc());
	}

	private Map<UUID, String> instructorNames() {
		Map<UUID, String> names = new LinkedHashMap<>();
		for (OfflineInstructor instructor : instructors.findAllByOrderByNameAsc()) {
			names.put(instructor.getId(), instructor.getName());
		}
		return names;
	}

	private OfflineBatchRoutine find(UUID id) {
		return routines.findById(id).orElseThrow(OfflineSupport::notFound);
	}

	private OfflineBatch findBatch(UUID id) {
		return batches.findById(id).orElseThrow(OfflineSupport::notFound);
	}

	private byte[] originalBytes(OfflineBatchRoutine routine) {
		Path path = files.resolve(routine.getOriginalFile());
		try {
			return Files.readAllBytes(path);
		}
		catch (IOException | RuntimeException exception) {
			throw state("The original PDF of this routine is missing, so the student copy cannot be made.");
		}
	}

	private static YearMonth yearMonth(String month) {
		if (month == null || month.isBlank()) {
			throw validation(List.of(new FieldErrorDetail("month", "Choose the month of the routine.")));
		}
		try {
			return YearMonth.parse(month.trim());
		}
		catch (DateTimeParseException exception) {
			throw validation(List.of(new FieldErrorDetail("month", "Enter the month as year-month, for example 2026-11.")));
		}
	}

	private static byte[] pdfBytes(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw validation(List.of(new FieldErrorDetail("file", "Choose the routine PDF.")));
		}
		if (file.getSize() > OfflineBatchService.MAX_ROUTINE_BYTES) {
			throw new ApiException(ErrorCode.FILE_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE, "File is too large.");
		}
		byte[] bytes;
		try {
			bytes = file.getBytes();
		}
		catch (IOException exception) {
			throw validation(List.of(new FieldErrorDetail("file", "This file is not a PDF.")));
		}
		byte[] magic = "%PDF-".getBytes(StandardCharsets.US_ASCII);
		if (bytes.length < magic.length || !Arrays.equals(Arrays.copyOf(bytes, magic.length), magic)) {
			throw validation(List.of(new FieldErrorDetail("file", "This file is not a PDF.")));
		}
		return bytes;
	}

	private static String dateField(String text, List<FieldErrorDetail> problems) {
		if (text == null || text.isBlank()) {
			return null;
		}
		try {
			return LocalDate.parse(text).toString();
		}
		catch (DateTimeParseException exception) {
			problems.add(new FieldErrorDetail("date", "Enter the date as year-month-day."));
			return null;
		}
	}

	private static String clockField(String text, String field, List<FieldErrorDetail> problems) {
		if (text == null || text.isBlank()) {
			return null;
		}
		try {
			return LocalTime.parse(text).toString();
		}
		catch (DateTimeException exception) {
			problems.add(new FieldErrorDetail(field, "Enter the time as hours:minutes, for example 17:10."));
			return null;
		}
	}

	/** The text of a field: null when the body leaves it out, empty when it is sent empty or null, trimmed otherwise. */
	private static String text(JsonNode body, String key) {
		if (body == null || !body.has(key) || body.get(key).isNull()) {
			return "";
		}
		return body.get(key).asText().trim();
	}

	private static String clip(String text, int max) {
		return text == null ? "" : cut(text.replaceAll("\\s+", " ").trim(), max);
	}

	private static String cut(String text, int max) {
		return text.length() > max ? text.substring(0, max) : text;
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

	// ---- records ----------------------------------------------------------------------------------------------------------

	/** What the routine's parse report holds: the month, the class length it was read with, the pages and the rows. */
	public record Report(String month, int defaultClassMinutes, int pageCount, List<OfflineRoutineRow> rows) {
	}

	/** One routine in a list. {@code rowCount} and {@code flaggedCount} count the rows; {@code canConfirm} is true for a preview with nothing blocking. */
	public record RoutineSummary(UUID id, String month, int version, String status, boolean legacyOriginal, String originalName,
			boolean studentCopy, Instant uploadedAt, Instant confirmedAt, int rowCount, int flaggedCount, boolean canConfirm) {
	}

	/** One routine in full: its rows, what blocks the confirm, and the difference when a preview is for a month already confirmed. */
	public record RoutineView(UUID id, UUID batchId, String month, int version, String status, boolean legacyOriginal, String originalName,
			boolean studentCopy, Instant uploadedAt, Instant confirmedAt, int rowCount, int flaggedCount, List<UnmatchedName> unmatchedNames,
			List<String> blockers, boolean canConfirm, List<RowView> rows, Difference difference) {
	}

	/** One row with its index and the instructor's name for the screens. */
	public record RowView(int index, String date, String day, String time, String startsAt, String endsAt, String subject, String topic,
			String kind, String instructorText, UUID instructorId, String instructorName, List<Flag> flags) {
	}

	/** A name the routine uses that no instructor answers to yet, and how many rows use it. */
	public record UnmatchedName(String name, int rows) {
	}

	/** A class as it is in the batch now. */
	public record ClassView(UUID id, String date, String startsAt, String subject, String topic, String instructorText, String status) {
	}

	/** A row of the preview that changes a class: the class now, and the row. */
	public record Change(ClassView before, RowView after) {
	}

	/** The difference between a preview and the confirmed routine of its month (plan 4.4). */
	public record Difference(List<RowView> added, List<Change> changed, List<ClassView> cancelled, List<ClassView> settled, int unchanged) {
	}

}
