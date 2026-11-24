package com.coachingcenter.api.offline;

import static com.coachingcenter.api.offline.OfflineSupport.state;
import static com.coachingcenter.api.offline.OfflineSupport.validation;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.error.FieldErrorDetail;

import tools.jackson.databind.JsonNode;

/**
 * Offline classes (plan sections 4.2 to 4.4, 6). A class is SCHEDULED until its end time (Dhaka); after that it is HELD, which is computed
 * and never stored (G5). Since 24 Nov a class carries no pay (instructor-pay-simple-plan.md): the Master Admin pays instructors by hand
 * ({@link OfflineInstructorPayService}). Classes paid or marked not taken before then keep that status, and stay locked, since their
 * payment or mark points at them.
 */
@Service
public class OfflineClassService {

	public static final String CLASS_ENTITY = "OFFLINE_CLASS";

	public static final String CLASS_CREATED = "OFFLINE_CLASS_CREATED";

	public static final String CLASS_UPDATED = "OFFLINE_CLASS_UPDATED";

	/** A class deleted by hand from the batch's classes (16 Nov). */
	public static final String CLASS_DELETED = "OFFLINE_CLASS_DELETED";

	/** Why a class settled before 24 Nov cannot be changed or deleted. */
	static final String LOCKED = "This class has a payment or a not-taken mark in the records, so it can't be changed.";

	/** The zone of every class time (plan G5, section 8). */
	public static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

	/** The computed status of a scheduled class whose end has passed. */
	public static final String HELD = "HELD";

	private static final Set<String> KINDS = Set.of(OfflineClass.KIND_LECTURE, OfflineClass.KIND_EXAM, OfflineClass.KIND_OTHER);

	private final OfflineBatchRepository batches;

	private final OfflineClassRepository classes;

	private final OfflineClassPaymentRepository payments;

	private final OfflineClassDecisionRepository decisions;

	private final OfflineInstructorRepository instructors;

	private final AuditRecorder audit;

	private final Clock clock;

	private final OfflineResultService results;

	public OfflineClassService(OfflineBatchRepository batches, OfflineClassRepository classes, OfflineClassPaymentRepository payments,
			OfflineClassDecisionRepository decisions, OfflineInstructorRepository instructors, AuditRecorder audit, Clock clock,
			OfflineResultService results) {
		this.results = results;
		this.batches = batches;
		this.classes = classes;
		this.payments = payments;
		this.decisions = decisions;
		this.instructors = instructors;
		this.audit = audit;
		this.clock = clock;
	}

	// ---- reading -------------------------------------------------------------------------------------------------------

	/**
	 * The classes of a batch, optionally for one month, one status (the computed one, so HELD works) and one instructor. Cancelled classes
	 * are left out unless the status asks for them. A pay-only class (kept out of the routine, 17 Nov) is never listed: it existed only for
	 * a payment, which the instructor's payment history still shows.
	 */
	@Transactional(readOnly = true)
	public List<ClassView> list(UUID batchId, YearMonth month, String status, UUID instructorId) {
		findBatch(batchId);
		Map<UUID, String> names = instructorNames();
		String wanted = status == null || status.isBlank() ? null : status.trim().toUpperCase(java.util.Locale.ROOT);
		List<ClassView> views = new ArrayList<>();
		for (OfflineClass classItem : classes.findByBatchIdOrderByHeldOnAscStartsAtAsc(batchId)) {
			if (month != null && !month.equals(YearMonth.from(classItem.getHeldOn()))) {
				continue;
			}
			if (instructorId != null && !instructorId.equals(classItem.getInstructorId())) {
				continue;
			}
			if (!classItem.isInRoutine()) {
				continue;
			}
			String computed = statusOf(classItem);
			boolean keep = wanted == null ? !OfflineClass.STATUS_CANCELLED.equals(computed) : wanted.equals(computed);
			if (keep) {
				views.add(view(classItem, names));
			}
		}
		return views;
	}

	/**
	 * The classes of the student's batch for one month, for the student's routine: date, day, time, subject, topic and kind. Never an
	 * instructor, an amount or a pay field (plan F11). Cancelled classes are not shown.
	 */
	@Transactional(readOnly = true)
	public List<StudentClassView> studentClasses(UUID batchId, YearMonth month) {
		List<StudentClassView> found = new ArrayList<>();
		for (OfflineClass classItem : classes.findByBatchIdOrderByHeldOnAscStartsAtAsc(batchId)) {
			if (OfflineClass.STATUS_CANCELLED.equals(classItem.getStatus()) || !classItem.isInRoutine()
					|| !month.equals(YearMonth.from(classItem.getHeldOn()))) {
				continue;
			}
			found.add(new StudentClassView(classItem.getHeldOn(), OfflineRoutineRules.weekday(classItem.getHeldOn()),
					classItem.getStartsAt(), classItem.getEndsAt(), classItem.getSubject(), classItem.getTopic(), classItem.getKind()));
		}
		return found;
	}

	/** The current month in Dhaka, for a student's routine that names no month. */
	public YearMonth currentMonth() {
		return YearMonth.from(LocalDate.now(clock.withZone(DHAKA)));
	}

	// ---- classes -------------------------------------------------------------------------------------------------------

	/** A class added by hand to an active batch. The end defaults to the start plus the batch's class length. */
	@Transactional
	public ClassView create(UUID batchId, UUID actorId, JsonNode body) {
		OfflineBatch batch = findBatch(batchId);
		if (OfflineBatch.STATUS_ARCHIVED.equals(batch.getStatus())) {
			throw state("This batch is archived, so classes cannot be added.");
		}
		List<FieldErrorDetail> problems = new ArrayList<>();
		LocalDate heldOn = OfflineSupport.day(body, "heldOn", problems);
		if (heldOn == null && problems.isEmpty()) {
			problems.add(new FieldErrorDetail("heldOn", "Enter the date of the class."));
		}
		LocalTime startsAt = time(body, "startsAt", problems);
		LocalTime endsAt = time(body, "endsAt", problems);
		if (startsAt == null && problems.isEmpty()) {
			problems.add(new FieldErrorDetail("startsAt", "Enter the start time."));
		}
		if (endsAt == null && startsAt != null) {
			endsAt = startsAt.plusMinutes(batch.getDefaultClassMinutes());
		}
		String subject = text(body, "subject", 200);
		if (subject.isEmpty()) {
			problems.add(new FieldErrorDetail("subject", "Enter the subject."));
		}
		String kind = body.has("kind") ? text(body, "kind", 16).toUpperCase(java.util.Locale.ROOT) : OfflineClass.KIND_LECTURE;
		if (!KINDS.contains(kind)) {
			problems.add(new FieldErrorDetail("kind", "Choose lecture, exam or other."));
		}
		UUID instructorId = instructor(body.get("instructorId"), problems);
		checkOrder(startsAt, endsAt, problems);
		if (!problems.isEmpty()) {
			throw validation(problems);
		}
		Instant now = clock.instant();
		OfflineClass classItem = new OfflineClass();
		classItem.setInRoutine(true);
		classItem.setId(UUID.randomUUID());
		classItem.setBatchId(batchId);
		classItem.setHeldOn(heldOn);
		classItem.setStartsAt(startsAt);
		classItem.setEndsAt(endsAt);
		classItem.setSubject(subject);
		classItem.setTopic(text(body, "topic", 500).isEmpty() ? null : text(body, "topic", 500));
		classItem.setKind(kind);
		classItem.setInstructorId(instructorId);
		classItem.setStatus(OfflineClass.STATUS_SCHEDULED);
		classItem.setCreatedAt(now);
		classItem.setUpdatedAt(now);
		classes.saveAndFlush(classItem);
		audit.record(CLASS_CREATED, CLASS_ENTITY, classItem.getId());
		return view(classItem, instructorNames());
	}

	/**
	 * Deletes a class by hand (16 Nov), from the routine's classes or one added by hand. Refused for an exam with a result, and for a class
	 * paid or marked not taken before 24 Nov (its record points at it). A class with no payment, decision or result sheet at all is
	 * removed; one that still has a rejected or voided payment, a decided request or a discarded sheet is marked cancelled instead, so
	 * those records keep their class. Either way it leaves the batch's classes and the student's routine list. The uploaded routine PDF is
	 * the file as uploaded and does not change; a new routine upload may add the class back if it is still in that routine.
	 */
	@Transactional
	public void delete(UUID id, UUID actorId) {
		OfflineClass classItem = classes.findForUpdate(id).orElseThrow(OfflineSupport::notFound);
		if (OfflineClass.STATUS_CANCELLED.equals(classItem.getStatus())) {
			throw OfflineSupport.notFound();
		}
		if (results.hasResult(id)) {
			throw new ApiException(ErrorCode.OFFLINE_CLASS_HAS_RESULT, HttpStatus.CONFLICT,
					"This exam has a result, so it cannot be deleted. Discard the staged result first; a published result stays with the exam.");
		}
		if (!OfflineClass.STATUS_SCHEDULED.equals(classItem.getStatus())) {
			throw state(LOCKED);
		}
		boolean keep = payments.existsByClassId(id) || decisions.existsByClassId(id) || results.anySheet(id);
		if (keep) {
			Instant now = clock.instant();
			classItem.setStatus(OfflineClass.STATUS_CANCELLED);
			classItem.setCancelledAt(now);
			classItem.setUpdatedAt(now);
			classItem.setStatusBy(actorId);
			classItem.setStatusAt(now);
			classes.saveAndFlush(classItem);
		}
		else {
			classes.delete(classItem);
			classes.flush();
		}
		audit.record(CLASS_DELETED, CLASS_ENTITY, id);
	}

	/**
	 * Changes a class (SCHEDULED, or HELD by its end time): the instructor (a substitute), the date, the times, the subject and the topic.
	 * Refused for a class paid or marked not taken before 24 Nov. A {@code version} that no longer matches is refused (409), so an older
	 * screen cannot overwrite a newer edit.
	 */
	@Transactional
	public ClassView update(UUID id, UUID actorId, JsonNode body) {
		// The class is locked first, so a result that is being saved for it finishes before this edit reads whether it has one (plan F7).
		OfflineClass classItem = classes.findForUpdate(id).orElseThrow(OfflineSupport::notFound);
		if (body.has("status") && OfflineClass.STATUS_CANCELLED.equalsIgnoreCase(body.get("status").asText("").trim())) {
			// Rule A1: an exam with a result is never cancelled. Any other class is cancelled only by a routine upload, not by hand.
			if (results.hasResult(id)) {
				throw new ApiException(ErrorCode.OFFLINE_CLASS_HAS_RESULT, HttpStatus.CONFLICT,
						"This exam has a result, so it cannot be cancelled. The result stays with the exam.");
			}
			throw validation(List.of(new FieldErrorDetail("status", "A class is cancelled by a routine upload, not by hand.")));
		}
		if (!OfflineClass.STATUS_SCHEDULED.equals(classItem.getStatus())) {
			throw state(LOCKED);
		}
		if (body.has("version") && body.get("version").asLong() != classItem.getVersion()) {
			throw new ApiException(ErrorCode.STALE_VERSION, HttpStatus.CONFLICT,
					"Someone else changed this class first. Reload the page and try again.");
		}
		List<FieldErrorDetail> problems = new ArrayList<>();
		if (body.has("heldOn")) {
			LocalDate heldOn = OfflineSupport.day(body, "heldOn", problems);
			if (heldOn == null && problems.isEmpty()) {
				problems.add(new FieldErrorDetail("heldOn", "Enter the date of the class."));
			}
			else if (heldOn != null) {
				// Plan F7: moving the date of an exam with a result would put its result on another day, so it is refused.
				if (!heldOn.equals(classItem.getHeldOn()) && results.hasResult(id)) {
					throw new ApiException(ErrorCode.OFFLINE_CLASS_HAS_RESULT, HttpStatus.CONFLICT,
							"This exam has a result, so its date cannot change. Discard the staged result first, or keep the date.");
				}
				classItem.setHeldOn(heldOn);
			}
		}
		if (body.has("startsAt")) {
			LocalTime startsAt = time(body, "startsAt", problems);
			if (startsAt != null) {
				classItem.setStartsAt(startsAt);
			}
		}
		if (body.has("endsAt")) {
			LocalTime endsAt = time(body, "endsAt", problems);
			if (endsAt != null) {
				classItem.setEndsAt(endsAt);
			}
		}
		if (body.has("subject")) {
			String subject = text(body, "subject", 200);
			if (subject.isEmpty()) {
				problems.add(new FieldErrorDetail("subject", "Enter the subject."));
			}
			else {
				classItem.setSubject(subject);
			}
		}
		if (body.has("topic")) {
			String topic = text(body, "topic", 500);
			classItem.setTopic(topic.isEmpty() ? null : topic);
		}
		if (body.has("instructorId")) {
			classItem.setInstructorId(instructor(body.get("instructorId"), problems));
		}
		checkOrder(classItem.getStartsAt(), classItem.getEndsAt(), problems);
		if (!problems.isEmpty()) {
			throw validation(problems);
		}
		classItem.setUpdatedAt(clock.instant());
		classes.saveAndFlush(classItem);
		audit.record(CLASS_UPDATED, CLASS_ENTITY, id);
		return view(classItem, instructorNames());
	}

	// ---- helpers --------------------------------------------------------------------------------------------------------

	/** The status a class has now: SCHEDULED after its end is HELD; any other stored status is as stored. */
	String statusOf(OfflineClass classItem) {
		return statusAt(classItem, clock.instant());
	}

	/** The status a class has at {@code now}. */
	String statusAt(OfflineClass classItem, Instant now) {
		if (!OfflineClass.STATUS_SCHEDULED.equals(classItem.getStatus())) {
			return classItem.getStatus();
		}
		LocalTime end = classItem.getEndsAt() != null ? classItem.getEndsAt() : classItem.getStartsAt();
		if (end == null) {
			return OfflineClass.STATUS_SCHEDULED;
		}
		Instant endsAt = ZonedDateTime.of(classItem.getHeldOn(), end, DHAKA).toInstant();
		return endsAt.isBefore(now) ? HELD : OfflineClass.STATUS_SCHEDULED;
	}

	private ClassView view(OfflineClass classItem, Map<UUID, String> names) {
		return new ClassView(classItem.getId(), classItem.getBatchId(), classItem.getRoutineId(), classItem.getHeldOn(),
				classItem.getStartsAt(), classItem.getEndsAt(), classItem.getSubject(), classItem.getTopic(), classItem.getKind(),
				classItem.getInstructorId(), classItem.getInstructorId() == null ? null : names.get(classItem.getInstructorId()),
				classItem.getInstructorText(), statusOf(classItem), classItem.getVersion() == null ? 0L : classItem.getVersion(),
				results.hasResult(classItem.getId()));
	}

	private Map<UUID, String> instructorNames() {
		Map<UUID, String> names = new HashMap<>();
		for (OfflineInstructor instructor : instructors.findAllByOrderByNameAsc()) {
			names.put(instructor.getId(), instructor.getName());
		}
		return names;
	}

	private OfflineBatch findBatch(UUID id) {
		return batches.findById(id).orElseThrow(OfflineSupport::notFound);
	}

	private static String text(JsonNode body, String key, int max) {
		if (body == null || !body.has(key) || body.get(key).isNull()) {
			return "";
		}
		String value = body.get(key).asText().trim().replaceAll("\\s+", " ");
		return value.length() > max ? value.substring(0, max) : value;
	}

	private static LocalTime time(JsonNode body, String key, List<FieldErrorDetail> problems) {
		if (body == null || !body.has(key) || body.get(key).isNull() || body.get(key).asText().isBlank()) {
			return null;
		}
		try {
			return LocalTime.parse(body.get(key).asText().trim());
		}
		catch (DateTimeParseException exception) {
			problems.add(new FieldErrorDetail(key, "Enter the time as hours:minutes, for example 17:10."));
			return null;
		}
	}

	private static void checkOrder(LocalTime startsAt, LocalTime endsAt, List<FieldErrorDetail> problems) {
		if (startsAt != null && endsAt != null && !endsAt.isAfter(startsAt)) {
			problems.add(new FieldErrorDetail("endsAt", "The end must be after the start."));
		}
	}

	/** An instructor from the list: null when sent empty, a problem when the id is not a live instructor. */
	private UUID instructor(JsonNode node, List<FieldErrorDetail> problems) {
		if (node == null || node.isNull() || node.asText().isBlank()) {
			return null;
		}
		try {
			UUID id = UUID.fromString(node.asText().trim());
			if (instructors.findById(id).filter(instructor -> instructor.getArchivedAt() == null).isPresent()) {
				return id;
			}
		}
		catch (IllegalArgumentException exception) {
			// reported below
		}
		problems.add(new FieldErrorDetail("instructorId", "Choose one from the list."));
		return null;
	}

	/** A month as year-month, or null when none is given. */
	public static YearMonth month(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		try {
			return YearMonth.parse(text.trim());
		}
		catch (DateTimeParseException exception) {
			throw validation(List.of(new FieldErrorDetail("month", "Enter the month as year-month, for example 2026-11.")));
		}
	}

	// ---- views -----------------------------------------------------------------------------------------------------------

	/**
	 * One class as the staff see it, with its computed status: SCHEDULED, HELD or CANCELLED, or PAID / NOT_TAKEN for a class settled
	 * before 24 Nov (locked). No amount or pay.
	 */
	public record ClassView(UUID id, UUID batchId, UUID routineId, LocalDate heldOn, LocalTime startsAt, LocalTime endsAt, String subject,
			String topic, String kind, UUID instructorId, String instructorName, String instructorText, String status, long version,
			boolean hasResult) {
	}

	/** One class on the student's routine: no instructor, amount or pay. */
	public record StudentClassView(LocalDate date, String day, LocalTime startsAt, LocalTime endsAt, String subject, String topic, String kind) {
	}

}
