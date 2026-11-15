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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Routines of a batch (plan sections 4.2, 4.4, 5 and 6, stage 2; changed on 13 Nov 2026). An upload reads the PDF into a PREVIEW
 * routine that the staff check; nothing reaches the classes until the confirm. A routine counts from the day it is uploaded: its earlier
 * dates still become classes, but the confirm only replaces the classes from that day onward. The confirm applies the rows to the batch's
 * classes in one transaction, makes the student copy, supersedes the batch's earlier live routine and tells the onboarded students once.
 * A confirmed routine is never changed; a new upload is the only way to replace it.
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

	/** The subject a class gets when its routine row has none (15 Nov: a missing subject only warns). */
	private static final String NO_SUBJECT = "Class";

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

	private final OfflineResultService results;

	public OfflineRoutineService(OfflineBatchRepository batches, OfflineBatchRoutineRepository routines, OfflineClassRepository classes,
			OfflineInstructorRepository instructors, OfflineInstructorService instructorService, OfflineBatchService batchService,
			PdfUploads uploads, LocalFileStorage files, AuditRecorder audit, JdbcTemplate jdbc, ObjectMapper json, Clock clock,
			OfflineResultService results) {
		this.results = results;
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
	 * Reads a routine PDF into a PREVIEW. A PDF without the routine's header row is refused, and nothing is saved. The original PDF is
	 * kept for staff. There is no month to choose: the routine counts from the day it is uploaded (Dhaka). Its earlier dates are flagged
	 * as past and still become classes; on confirm it replaces only the classes from that day onward. When the batch already has a live
	 * routine, the preview shows the difference.
	 */
	@Transactional
	public RoutineView upload(UUID batchId, MultipartFile file, UUID actorId) {
		OfflineBatch batch = findBatch(batchId);
		if (OfflineBatch.STATUS_ARCHIVED.equals(batch.getStatus())) {
			throw state("This batch is archived, so its routine cannot be uploaded.");
		}
		LocalDate from = today();
		byte[] bytes = pdfBytes(file);
		Parsed parsed = OfflineRoutineReader.read(bytes);
		List<OfflineRoutineRow> rows = OfflineRoutineRules.build(parsed.rows(), from, batch.getDefaultClassMinutes(), index());
		LocalFileStorage.StoredFile stored = uploads.store(file, OfflineBatchService.MAX_ROUTINE_BYTES);
		try {
			OfflineBatchRoutine routine = new OfflineBatchRoutine();
			routine.setId(UUID.randomUUID());
			routine.setBatchId(batchId);
			routine.setEffectiveFrom(from);
			routine.setMonth(from.withDayOfMonth(1));
			routine.setOriginalFile(stored.storedFileName());
			routine.setOriginalName(originalName(stored.originalFileName()));
			routine.setOriginalByteSize(stored.byteSize());
			routine.setRevision(nextRevision(batchId));
			routine.setStatus(OfflineBatchRoutine.STATUS_PREVIEW);
			routine.setLegacyOriginal(false);
			routine.setUploadedBy(actorId);
			routine.setUploadedAt(clock.instant());
			routine.setParseReport(reportJson(batch.getDefaultClassMinutes(), parsed.pageCount(), rows));
			routines.saveAndFlush(routine);
			audit.record(UPLOADED, ENTITY, routine.getId());
			return view(routine);
		}
		catch (RuntimeException exception) {
			files.delete(stored.storedFileName());
			throw exception;
		}
	}

	/** The routines of a batch, the newest first: by the day each counts from, then by version. */
	@Transactional(readOnly = true)
	public List<RoutineSummary> list(UUID batchId) {
		findBatch(batchId);
		List<RoutineSummary> summaries = new ArrayList<>();
		for (OfflineBatchRoutine routine : routines.findByBatchIdOrderByEffectiveFromDescRevisionDesc(batchId)) {
			List<OfflineRoutineRow> rows = rowsFor(routine);
			Report report = report(routine);
			summaries.add(new RoutineSummary(routine.getId(), routine.getEffectiveFrom().toString(), routine.getRevision(),
					routine.getStatus(), routine.isLegacyOriginal(), routine.getOriginalName(), routine.getStudentFile() != null,
					routine.getUploadedAt(), routine.getConfirmedAt(), rows.size(), flagged(rows),
					OfflineBatchRoutine.STATUS_PREVIEW.equals(routine.getStatus())));
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
	 * Confirms a PREVIEW. Nothing in the rows blocks it (Shahriar, 15 Nov): the admin confirms with the warnings in view. A row with no
	 * readable date or time cannot be a class, so it is left out (the warnings say so); a row with no subject is saved as "Class". The instructors come from the PDF: a name
	 * that no instructor has (by name or alias) is added as a new instructor under that name and assigned to its rows, so nobody has to
	 * match names before confirming. A name can still be matched to an existing instructor on the preview, and a class's instructor can be
	 * changed later until it is settled. The classes of the batch change in this transaction: matched unsettled classes take the new
	 * values, settled ones stay, new rows are added and unsettled classes of the month that no row matches are cancelled.
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
		report = withNewInstructors(routine, report, actorId);
		// A row with no date or time cannot be held on a day and hour, so it is left out; every other row becomes a class.
		List<OfflineRoutineRow> usable = report.rows().stream().filter(row -> row.date() != null && row.startsAt() != null).toList();
		report = new Report(report.defaultClassMinutes(), report.pageCount(), usable);
		Instant now = clock.instant();
		Plan plan = OfflineRoutineMatcher.plan(report.rows(), classes.findByBatchIdOrderByHeldOnAscStartsAtAsc(batch.getId()),
				routine.getEffectiveFrom(), results.classesWithResults(batch.getId()));
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
			// The batch has one live routine: the one before it stays as the record.
			routines.findByBatchIdAndStatus(batch.getId(), OfflineBatchRoutine.STATUS_CONFIRMED).ifPresent(prior -> {
				prior.setStatus(OfflineBatchRoutine.STATUS_SUPERSEDED);
				routines.saveAndFlush(prior);
			});
			int revision = confirmedRevision(batch.getId()) + 1;
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
			String day = routine.getEffectiveFrom().toString();
			String name = student ? "routine-" + day + "-student-copy.pdf"
					: routine.getOriginalName() == null ? "routine-" + day + ".pdf" : routine.getOriginalName();
			long size = Files.size(path);
			return new RoutineFile(body, name, size);
		}
		catch (IOException exception) {
			throw notFound();
		}
	}

	/**
	 * The student copy for a batch: without a month, the live routine's; with a month, the newest confirmed routine (live or since
	 * replaced) that counts from a day in or before that month, which is the routine the month was taught from. A routine with no
	 * student copy (the old single routine, which carries names) is never served to a student: the answer is 404.
	 */
	@Transactional(readOnly = true)
	public RoutineFile studentCopy(UUID batchId, YearMonth month) {
		for (OfflineBatchRoutine routine : routines.findByBatchIdOrderByEffectiveFromDescRevisionDesc(batchId)) {
			boolean live = OfflineBatchRoutine.STATUS_CONFIRMED.equals(routine.getStatus());
			boolean confirmedOnce = live || OfflineBatchRoutine.STATUS_SUPERSEDED.equals(routine.getStatus());
			if (month == null ? !live : !confirmedOnce || routine.getEffectiveFrom().isAfter(month.atEndOfMonth())) {
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
		List<OfflineRoutineRow> checked = rowsFor(routine);
		Map<UUID, String> names = instructorNames();
		List<RowView> rows = new ArrayList<>();
		for (int index = 0; index < checked.size(); index++) {
			rows.add(rowView(index, checked.get(index), names));
		}
		Difference difference = null;
		if (OfflineBatchRoutine.STATUS_PREVIEW.equals(routine.getStatus())
				&& routines.findByBatchIdAndStatus(routine.getBatchId(), OfflineBatchRoutine.STATUS_CONFIRMED).isPresent()) {
			difference = difference(routine.getBatchId(), routine.getEffectiveFrom(), report.rows(), names);
		}
		List<String> warnings = warnings(checked);
		return new RoutineView(routine.getId(), routine.getBatchId(), routine.getEffectiveFrom().toString(),
				routine.getRevision(), routine.getStatus(), routine.isLegacyOriginal(), routine.getOriginalName(),
				routine.getStudentFile() != null, routine.getUploadedAt(), routine.getConfirmedAt(), report.rows().size(),
				flagged(checked), unmatched(report.rows()), warnings,
				OfflineBatchRoutine.STATUS_PREVIEW.equals(routine.getStatus()), rows, difference);
	}

	private Difference difference(UUID batchId, LocalDate from, List<OfflineRoutineRow> rows, Map<UUID, String> names) {
		Set<UUID> withResult = results.classesWithResults(batchId);
		Plan plan = OfflineRoutineMatcher.plan(rows, classes.findByBatchIdOrderByHeldOnAscStartsAtAsc(batchId), from, withResult);
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
				changed.add(new Change(classView(planned.existing(), false), row));
			}
			else if (planned.action() == Action.UPDATE || planned.action() == Action.EARLIER) {
				// An earlier class the row matches is left as it is, so it counts as unchanged.
				unchanged++;
			}
			else {
				settled.add(classView(planned.existing(), withResult.contains(planned.existing().getId())));
			}
		}
		List<ClassView> cancelled = new ArrayList<>();
		for (OfflineClass cancel : plan.cancel()) {
			cancelled.add(classView(cancel, false));
		}
		for (OfflineClass kept : plan.settledKept()) {
			settled.add(classView(kept, withResult.contains(kept.getId())));
		}
		return new Difference(added, changed, cancelled, settled, unchanged);
	}

	private static RowView rowView(int index, OfflineRoutineRow row, Map<UUID, String> names) {
		return new RowView(index, row.date(), row.day(), row.time(), row.startsAt(), row.endsAt(), row.subject(), row.topic(), row.kind(),
				row.instructorText(), row.instructorId(), row.instructorId() == null ? null : names.get(row.instructorId()), row.flags());
	}

	/** A class as the preview lists it. {@code keptForResult} is set for an exam with a result sheet, which the routine keeps (rule A1). */
	private static ClassView classView(OfflineClass item, boolean keptForResult) {
		return new ClassView(item.getId(), item.getHeldOn().toString(), item.getStartsAt() == null ? null : item.getStartsAt().toString(),
				item.getSubject(), item.getTopic(), item.getInstructorText(), item.getStatus(), keptForResult);
	}

	/**
	 * What the admin is warned of before confirming (Shahriar, 15 Nov: nothing blocks). A row with no readable date or time is left out of
	 * the classes; a date earlier than the row above it, or outside the batch's dates, is kept as written; a row with no subject is saved
	 * as "Class". A weekday that does not match its date, and the other flags, show on the rows.
	 */
	private static List<String> warnings(List<OfflineRoutineRow> rows) {
		List<String> warnings = new ArrayList<>();
		for (int index = 0; index < rows.size(); index++) {
			OfflineRoutineRow row = rows.get(index);
			if (row.date() == null) {
				warnings.add("Row " + (index + 1) + " has no readable date, so it will be left out.");
			}
			else if (row.startsAt() == null) {
				warnings.add("Row " + (index + 1) + " has no readable time, so it will be left out.");
			}
			if (OfflineRoutineRules.has(row, OfflineRoutineRules.DATE_OUT_OF_ORDER)) {
				warnings.add("Row " + (index + 1) + " has a date earlier than the row above it.");
			}
			if (OfflineRoutineRules.has(row, OfflineRoutineRules.DATE_OUTSIDE_BATCH)) {
				warnings.add("Row " + (index + 1) + " has a date outside the batch's dates.");
			}
			if (row.subject() == null || row.subject().isBlank()) {
				warnings.add("Row " + (index + 1) + " has no subject, so it will be saved as \"Class\".");
			}
		}
		return warnings;
	}

	/**
	 * The report with every name no instructor has assigned to an instructor of that name: an existing one when the name is already
	 * known, otherwise a new one created now. The rows are saved, so the confirmed routine shows who teaches each class. Rows with no
	 * instructor in the PDF stay without one.
	 */
	private Report withNewInstructors(OfflineBatchRoutine routine, Report report, UUID actorId) {
		List<UnmatchedName> names = unmatched(report.rows());
		if (names.isEmpty()) {
			return report;
		}
		Map<String, UUID> assigned = new LinkedHashMap<>();
		for (UnmatchedName name : names) {
			assigned.put(OfflineRoutineRules.key(name.name()), existingOrNew(clip(name.name(), 200), actorId).getId());
		}
		List<OfflineRoutineRow> rows = new ArrayList<>();
		for (OfflineRoutineRow row : report.rows()) {
			UUID instructorId = row.instructorId() == null && row.instructorText() != null && !row.instructorText().isEmpty()
					? assigned.get(OfflineRoutineRules.key(row.instructorText()))
					: row.instructorId();
			rows.add(instructorId == row.instructorId() ? row : new OfflineRoutineRow(row.page(), row.date(), row.day(), row.time(),
					row.startsAt(), row.endsAt(), row.subject(), row.topic(), row.kind(), row.instructorText(), instructorId, row.flags()));
		}
		save(routine, report, rows);
		return report(routine);
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
		item.setSubject(row.subject() == null || row.subject().isBlank() ? NO_SUBJECT : cut(row.subject(), MAX_SUBJECT));
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

	/** The highest version a confirmed (live or replaced) routine of the batch has, or 0. Versions count per batch. */
	private int confirmedRevision(UUID batchId) {
		int max = 0;
		for (OfflineBatchRoutine routine : routines.findByBatchIdOrderByEffectiveFromDescRevisionDesc(batchId)) {
			if (!OfflineBatchRoutine.STATUS_PREVIEW.equals(routine.getStatus())) {
				max = Math.max(max, routine.getRevision());
			}
		}
		return max;
	}

	private int nextRevision(UUID batchId) {
		return confirmedRevision(batchId) + 1;
	}

	/** Today in Dhaka: the day an uploaded routine counts from. */
	private LocalDate today() {
		return LocalDate.now(clock.withZone(OfflineClassService.DHAKA));
	}

	// ---- helpers ----------------------------------------------------------------------------------------------------------

	/** The routine's stored rows and counts. A routine with no stored report (the old single routine) has no rows. */
	/** The routine's rows with the batch-date check applied (worked out on each read, as the batch's dates can change). */
	private List<OfflineRoutineRow> rowsFor(OfflineBatchRoutine routine) {
		OfflineBatch batch = findBatch(routine.getBatchId());
		return OfflineRoutineRules.withBatchDates(report(routine).rows(), batch.getStartsOn(), batch.getEndsOn());
	}

	private Report report(OfflineBatchRoutine routine) {
		if (routine.getParseReport() == null) {
			return new Report(0, 0, List.of());
		}
		return json.readValue(routine.getParseReport(), Report.class);
	}

	private String reportJson(int minutes, int pageCount, List<OfflineRoutineRow> rows) {
		return json.writeValueAsString(new Report(minutes, pageCount, rows));
	}

	/** Stores the rows with their flags worked out again against the day the routine counts from. */
	private void save(OfflineBatchRoutine routine, Report report, List<OfflineRoutineRow> rows) {
		routine.setParseReport(reportJson(report.defaultClassMinutes(), report.pageCount(),
				OfflineRoutineRules.flag(rows, routine.getEffectiveFrom())));
		routines.saveAndFlush(routine);
	}

	private void requirePreview(OfflineBatchRoutine routine) {
		if (OfflineBatchRoutine.STATUS_CONFIRMED.equals(routine.getStatus())) {
			throw state("This routine is confirmed, so its rows cannot be changed. Upload the routine again to change it.");
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

	/**
	 * What the routine's parse report holds: the class length it was read with, the pages and the rows. Reports stored while routines
	 * were monthly also carry a month; it is ignored (the routine's {@code effectiveFrom} is the day it counts from).
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Report(int defaultClassMinutes, int pageCount, List<OfflineRoutineRow> rows) {
	}

	/** One routine in a list. {@code rowCount} and {@code flaggedCount} count the rows; {@code canConfirm} is true for every preview. */
	public record RoutineSummary(UUID id, String effectiveFrom, int version, String status, boolean legacyOriginal, String originalName,
			boolean studentCopy, Instant uploadedAt, Instant confirmedAt, int rowCount, int flaggedCount, boolean canConfirm) {
	}

	/**
	 * One routine in full: its rows, the warnings to read before confirming (nothing blocks), and the difference when the batch already
	 * has a live routine.
	 * {@code effectiveFrom} is the day the routine counts from (yyyy-MM-dd): the classes from that day onward are the ones it replaces.
	 */
	public record RoutineView(UUID id, UUID batchId, String effectiveFrom, int version, String status, boolean legacyOriginal,
			String originalName, boolean studentCopy, Instant uploadedAt, Instant confirmedAt, int rowCount, int flaggedCount,
			List<UnmatchedName> unmatchedNames, List<String> warnings, boolean canConfirm, List<RowView> rows, Difference difference) {
	}

	/** One row with its index and the instructor's name for the screens. */
	public record RowView(int index, String date, String day, String time, String startsAt, String endsAt, String subject, String topic,
			String kind, String instructorText, UUID instructorId, String instructorName, List<Flag> flags) {
	}

	/** A name the routine uses that no instructor answers to yet, and how many rows use it. */
	public record UnmatchedName(String name, int rows) {
	}

	/** A class as it is in the batch now. */
	public record ClassView(UUID id, String date, String startsAt, String subject, String topic, String instructorText, String status,
			boolean keptForResult) {
	}

	/** A row of the preview that changes a class: the class now, and the row. */
	public record Change(ClassView before, RowView after) {
	}

	/** The difference between a preview and the confirmed routine of its month (plan 4.4). */
	public record Difference(List<RowView> added, List<Change> changed, List<ClassView> cancelled, List<ClassView> settled, int unchanged) {
	}

}
