package com.coachingcenter.api.offline;

import static com.coachingcenter.api.offline.OfflineSupport.notFound;
import static com.coachingcenter.api.offline.OfflineSupport.state;
import static com.coachingcenter.api.offline.OfflineSupport.validation;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * Offline classes and their settling (plan sections 4.2 to 4.4, 6, stage 3). A class is SCHEDULED until its end time (Dhaka); after that
 * it is HELD, which is computed and never stored (G5). Staff record a payment or a "not taken", which waits for the Master Admin; the
 * Master Admin's own entries are approved at once. A payment or a decision is never edited: it is approved, rejected or voided.
 */
@Service
public class OfflineClassService {

	public static final String CLASS_ENTITY = "OFFLINE_CLASS";

	public static final String PAYMENT_ENTITY = "OFFLINE_CLASS_PAYMENT";

	public static final String DECISION_ENTITY = "OFFLINE_CLASS_DECISION";

	public static final String CLASS_CREATED = "OFFLINE_CLASS_CREATED";

	public static final String CLASS_UPDATED = "OFFLINE_CLASS_UPDATED";

	public static final String PAYMENT_RECORDED = "OFFLINE_CLASS_PAYMENT_RECORDED";

	public static final String PAYMENT_APPROVED = "OFFLINE_CLASS_PAYMENT_APPROVED";

	public static final String PAYMENT_REJECTED = "OFFLINE_CLASS_PAYMENT_REJECTED";

	public static final String PAYMENT_VOIDED = "OFFLINE_CLASS_PAYMENT_VOIDED";

	public static final String NOT_TAKEN_REQUESTED = "OFFLINE_CLASS_NOT_TAKEN_REQUESTED";

	public static final String NOT_TAKEN_APPROVED = "OFFLINE_CLASS_NOT_TAKEN_APPROVED";

	public static final String NOT_TAKEN_REJECTED = "OFFLINE_CLASS_NOT_TAKEN_REJECTED";

	/** The zone of every class time (plan G5, section 8). */
	public static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

	/** The computed status of a class whose end has passed while it is still unsettled. */
	public static final String HELD = "HELD";

	private static final String PENDING = "PENDING";

	private static final String APPROVED = "APPROVED";

	private static final String REJECTED = "REJECTED";

	private static final String VOIDED = "VOIDED";

	private static final Set<String> METHODS = Set.of("CASH", "BKASH", "NAGAD", "ROCKET", "BANK");

	private static final Set<String> KINDS = Set.of(OfflineClass.KIND_LECTURE, OfflineClass.KIND_EXAM, OfflineClass.KIND_OTHER);

	/** The stored statuses that can hold an unsettled class (SCHEDULED is unsettled only once its end has passed). */
	private static final List<String> UNSETTLED_STORED = List.of(OfflineClass.STATUS_SCHEDULED, OfflineClass.STATUS_PAID_PENDING,
			OfflineClass.STATUS_NOT_TAKEN_PENDING);

	private static final int MAX_BULK = 200;

	private static final int MAX_PAGE = 200;

	private final OfflineBatchRepository batches;

	private final OfflineClassRepository classes;

	private final OfflineClassPaymentRepository payments;

	private final OfflineClassDecisionRepository decisions;

	private final OfflineInstructorRepository instructors;

	private final OfflineBatchInstructorRateRepository rates;

	private final AuditRecorder audit;

	private final OfflineClassNotices notices;

	private final Clock clock;

	private final OfflineResultService results;

	public OfflineClassService(OfflineBatchRepository batches, OfflineClassRepository classes, OfflineClassPaymentRepository payments,
			OfflineClassDecisionRepository decisions, OfflineInstructorRepository instructors, OfflineBatchInstructorRateRepository rates,
			AuditRecorder audit, OfflineClassNotices notices, Clock clock, OfflineResultService results) {
		this.results = results;
		this.notices = notices;
		this.batches = batches;
		this.classes = classes;
		this.payments = payments;
		this.decisions = decisions;
		this.instructors = instructors;
		this.rates = rates;
		this.audit = audit;
		this.clock = clock;
	}

	// ---- reading -------------------------------------------------------------------------------------------------------

	/**
	 * The classes of a batch, optionally for one month, one status (the computed one, so HELD works) and one instructor. Cancelled classes
	 * are left out unless the status asks for them.
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
			String computed = statusOf(classItem);
			boolean keep = wanted == null ? !OfflineClass.STATUS_CANCELLED.equals(computed) : wanted.equals(computed);
			if (keep) {
				views.add(view(classItem, names));
			}
		}
		return views;
	}

	/** The unsettled classes (HELD, waiting for payment approval, waiting for a not-taken approval), oldest first, one page at a time. */
	@Transactional(readOnly = true)
	public UnsettledPage unsettled(UUID batchId, UUID instructorId, LocalDate from, LocalDate to, int page, int size) {
		Map<UUID, String> names = instructorNames();
		List<OfflineClass> found = new ArrayList<>();
		for (OfflineClass classItem : classes.findByStatusIn(UNSETTLED_STORED)) {
			if (batchId != null && !batchId.equals(classItem.getBatchId())) {
				continue;
			}
			if (instructorId != null && !instructorId.equals(classItem.getInstructorId())) {
				continue;
			}
			if (from != null && classItem.getHeldOn().isBefore(from) || to != null && classItem.getHeldOn().isAfter(to)) {
				continue;
			}
			if (isUnsettled(statusOf(classItem))) {
				found.add(classItem);
			}
		}
		found.sort(Comparator.comparing(OfflineClass::getHeldOn).thenComparing(OfflineClass::getStartsAt,
				Comparator.nullsLast(Comparator.naturalOrder())));
		int safeSize = Math.max(1, Math.min(size, MAX_PAGE));
		int safePage = Math.max(0, page);
		int from0 = Math.min(found.size(), safePage * safeSize);
		int to0 = Math.min(found.size(), from0 + safeSize);
		List<ClassView> items = new ArrayList<>();
		for (OfflineClass classItem : found.subList(from0, to0)) {
			items.add(view(classItem, names));
		}
		return new UnsettledPage(items, safePage, safeSize, found.size());
	}

	/** The counts the unsettled page's cards show: HELD classes, classes waiting for approval, and the amount still owed on HELD classes. */
	@Transactional(readOnly = true)
	public UnsettledCounts counts() {
		long held = 0;
		long waiting = 0;
		BigDecimal heldAmount = BigDecimal.ZERO;
		for (OfflineClass classItem : classes.findByStatusIn(UNSETTLED_STORED)) {
			String status = statusOf(classItem);
			if (HELD.equals(status)) {
				held++;
				if (classItem.getAmount() != null) {
					heldAmount = heldAmount.add(classItem.getAmount());
				}
			}
			else if (OfflineClass.STATUS_PAID_PENDING.equals(status) || OfflineClass.STATUS_NOT_TAKEN_PENDING.equals(status)) {
				waiting++;
			}
		}
		return new UnsettledCounts(held, waiting, heldAmount);
	}

	/**
	 * The classes of the student's batch for one month, for the student's routine: date, day, time, subject, topic and kind. Never an
	 * instructor, an amount or a pay field (plan F11). Cancelled classes are not shown.
	 */
	@Transactional(readOnly = true)
	public List<StudentClassView> studentClasses(UUID batchId, YearMonth month) {
		List<StudentClassView> found = new ArrayList<>();
		for (OfflineClass classItem : classes.findByBatchIdOrderByHeldOnAscStartsAtAsc(batchId)) {
			if (OfflineClass.STATUS_CANCELLED.equals(classItem.getStatus()) || !month.equals(YearMonth.from(classItem.getHeldOn()))) {
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
		BigDecimal override = override(body, problems);
		checkOrder(startsAt, endsAt, problems);
		if (!problems.isEmpty()) {
			throw validation(problems);
		}
		Instant now = clock.instant();
		OfflineClass classItem = new OfflineClass();
		classItem.setId(UUID.randomUUID());
		classItem.setBatchId(batchId);
		classItem.setHeldOn(heldOn);
		classItem.setStartsAt(startsAt);
		classItem.setEndsAt(endsAt);
		classItem.setSubject(subject);
		classItem.setTopic(text(body, "topic", 500).isEmpty() ? null : text(body, "topic", 500));
		classItem.setKind(kind);
		classItem.setInstructorId(instructorId);
		classItem.setRateOverride(override);
		classItem.setAmount(amountOf(batchId, instructorId, override));
		classItem.setStatus(OfflineClass.STATUS_SCHEDULED);
		classItem.setCreatedAt(now);
		classItem.setUpdatedAt(now);
		classes.saveAndFlush(classItem);
		audit.record(CLASS_CREATED, CLASS_ENTITY, classItem.getId());
		return view(classItem, instructorNames());
	}

	/**
	 * Changes a class that is not settled yet (SCHEDULED, or HELD by its end time): the instructor (a substitute), the rate override, the date,
	 * the times, the subject and the topic. Refused once a payment or a not-taken request is waiting, or settled. A {@code version} that no
	 * longer matches is refused (409), so an older screen cannot overwrite a newer edit.
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
			throw state("This class is already waiting for approval, paid or not taken, so it cannot be changed.");
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
		if (body.has("rateOverride")) {
			classItem.setRateOverride(override(body, problems));
		}
		checkOrder(classItem.getStartsAt(), classItem.getEndsAt(), problems);
		if (!problems.isEmpty()) {
			throw validation(problems);
		}
		classItem.setAmount(amountOf(classItem.getBatchId(), classItem.getInstructorId(), classItem.getRateOverride()));
		classItem.setUpdatedAt(clock.instant());
		classes.saveAndFlush(classItem);
		audit.record(CLASS_UPDATED, CLASS_ENTITY, id);
		return view(classItem, instructorNames());
	}

	// ---- payments and not-taken requests ----------------------------------------------------------------------------

	/**
	 * Records a payment to the class's instructor. The class must be HELD, with an instructor and an amount, and no live payment. The amount
	 * may differ from the due amount. Staff create a PENDING payment (the class becomes PAID_PENDING); the Master Admin's is APPROVED at once
	 * (PAID). The paid-on date may be in the past, but not in the future.
	 */
	@Transactional
	public ClassView recordPayment(UUID classId, UUID actorId, boolean master, JsonNode body) {
		OfflineClass classItem = findClass(classId);
		List<FieldErrorDetail> problems = new ArrayList<>();
		BigDecimal amount = OfflineSupport.money(body, "amount", problems);
		if (problems.isEmpty() && (amount == null || amount.signum() <= 0)) {
			problems.add(new FieldErrorDetail("amount", "Enter the amount paid."));
		}
		String method = text(body, "method", 16).toUpperCase(java.util.Locale.ROOT);
		if (!METHODS.contains(method)) {
			problems.add(new FieldErrorDetail("method", "Choose cash, bKash, Nagad, Rocket or bank."));
		}
		LocalDate today = LocalDate.now(clock.withZone(DHAKA));
		LocalDate paidOn = OfflineSupport.day(body, "paidOn", problems);
		if (paidOn == null && problems.isEmpty()) {
			paidOn = today;
		}
		if (paidOn != null && paidOn.isAfter(today)) {
			problems.add(new FieldErrorDetail("paidOn", "The paid-on date cannot be in the future."));
		}
		String reference = text(body, "reference", 100);
		String note = text(body, "note", 500);
		if (!problems.isEmpty()) {
			throw validation(problems);
		}
		String status = statusOf(classItem);
		if (!HELD.equals(status)) {
			throw state(OfflineClass.STATUS_SCHEDULED.equals(status) ? "This class has not finished yet, so it cannot be paid."
					: "This class is already waiting for approval or settled.");
		}
		if (classItem.getInstructorId() == null) {
			throw state("Choose the instructor for this class before recording a payment.");
		}
		if (classItem.getAmount() == null) {
			throw state("Set a rate or an amount for this class before recording a payment.");
		}
		if (livePayment(classId).isPresent()) {
			throw state("This class already has a payment. Void it before recording another.");
		}
		Instant now = clock.instant();
		OfflineClassPayment payment = new OfflineClassPayment();
		payment.setId(UUID.randomUUID());
		payment.setClassId(classId);
		payment.setInstructorId(classItem.getInstructorId());
		payment.setAmount(amount.setScale(2, java.math.RoundingMode.HALF_UP));
		payment.setMethod(method);
		payment.setReference(reference.isEmpty() ? null : reference);
		payment.setPaidOn(paidOn);
		payment.setNote(note.isEmpty() ? null : note);
		payment.setRecordedBy(actorId);
		payment.setRecordedAt(now);
		if (master) {
			payment.setStatus(APPROVED);
			payment.setDecidedBy(actorId);
			payment.setDecidedAt(now);
			classItem.setStatus(OfflineClass.STATUS_PAID);
		}
		else {
			payment.setStatus(PENDING);
			classItem.setStatus(OfflineClass.STATUS_PAID_PENDING);
		}
		payments.saveAndFlush(payment);
		touch(classItem, actorId, now);
		classes.saveAndFlush(classItem);
		audit.record(PAYMENT_RECORDED, PAYMENT_ENTITY, payment.getId());
		return view(classItem, instructorNames());
	}

	/**
	 * Asks for a class to be marked not taken (the instructor did not come, or the class did not run). Staff's request waits for the Master
	 * Admin; the Master Admin's is approved at once. The class must be HELD.
	 */
	@Transactional
	public ClassView requestNotTaken(UUID classId, UUID actorId, boolean master, JsonNode body) {
		OfflineClass classItem = findClass(classId);
		List<FieldErrorDetail> problems = new ArrayList<>();
		String reason = OfflineSupport.reason(body.has("reason") ? body.get("reason").asText() : "", "reason", problems);
		if (!problems.isEmpty()) {
			throw validation(problems);
		}
		String status = statusOf(classItem);
		if (!HELD.equals(status)) {
			throw state(OfflineClass.STATUS_SCHEDULED.equals(status) ? "This class has not finished yet, so it cannot be marked not taken."
					: "This class is already waiting for approval or settled.");
		}
		Instant now = clock.instant();
		OfflineClassDecision decision = new OfflineClassDecision();
		decision.setId(UUID.randomUUID());
		decision.setClassId(classId);
		decision.setRequestedBy(actorId);
		decision.setRequestedAt(now);
		decision.setReason(reason);
		if (master) {
			decision.setStatus(APPROVED);
			decision.setDecidedBy(actorId);
			decision.setDecidedAt(now);
			classItem.setStatus(OfflineClass.STATUS_NOT_TAKEN);
		}
		else {
			decision.setStatus(PENDING);
			classItem.setStatus(OfflineClass.STATUS_NOT_TAKEN_PENDING);
		}
		decisions.saveAndFlush(decision);
		touch(classItem, actorId, now);
		classes.saveAndFlush(classItem);
		audit.record(NOT_TAKEN_REQUESTED, DECISION_ENTITY, decision.getId());
		return view(classItem, instructorNames());
	}

	// ---- approvals (the Master Admin) -------------------------------------------------------------------------------

	/** Approves a waiting payment: the class becomes PAID. */
	@Transactional
	public ClassView approvePayment(UUID paymentId, UUID actorId) {
		ClassView view = approvePaymentRecord(paymentId, actorId);
		notices.decided(findPayment(paymentId).getRecordedBy(), 1, 0);
		return view;
	}

	ClassView approvePaymentRecord(UUID paymentId, UUID actorId) {
		OfflineClassPayment payment = findPayment(paymentId);
		if (!PENDING.equals(payment.getStatus())) {
			throw state("This payment is not waiting for approval.");
		}
		OfflineClass classItem = findClass(payment.getClassId());
		if (!OfflineClass.STATUS_PAID_PENDING.equals(classItem.getStatus())) {
			throw state("This class is not waiting for a payment approval.");
		}
		Instant now = clock.instant();
		payment.setStatus(APPROVED);
		payment.setDecidedBy(actorId);
		payment.setDecidedAt(now);
		payments.saveAndFlush(payment);
		classItem.setStatus(OfflineClass.STATUS_PAID);
		touch(classItem, actorId, now);
		classes.saveAndFlush(classItem);
		audit.record(PAYMENT_APPROVED, PAYMENT_ENTITY, paymentId);
		return view(classItem, instructorNames());
	}

	/** Rejects a waiting payment with a reason: the class returns to HELD. */
	@Transactional
	public ClassView rejectPayment(UUID paymentId, UUID actorId, JsonNode body) {
		ClassView view = rejectPaymentRecord(paymentId, actorId, body);
		notices.decided(findPayment(paymentId).getRecordedBy(), 0, 1);
		return view;
	}

	ClassView rejectPaymentRecord(UUID paymentId, UUID actorId, JsonNode body) {
		OfflineClassPayment payment = findPayment(paymentId);
		String reason = reasonOf(body);
		if (!PENDING.equals(payment.getStatus())) {
			throw state("This payment is not waiting for approval.");
		}
		OfflineClass classItem = findClass(payment.getClassId());
		Instant now = clock.instant();
		payment.setStatus(REJECTED);
		payment.setDecidedBy(actorId);
		payment.setDecidedAt(now);
		payment.setDecisionReason(reason);
		payments.saveAndFlush(payment);
		classItem.setStatus(OfflineClass.STATUS_SCHEDULED);
		touch(classItem, actorId, now);
		classes.saveAndFlush(classItem);
		audit.record(PAYMENT_REJECTED, PAYMENT_ENTITY, paymentId);
		return view(classItem, instructorNames());
	}

	/** Voids an approved payment with a reason: the class returns to HELD and can be paid again. */
	@Transactional
	public ClassView voidPayment(UUID paymentId, UUID actorId, JsonNode body) {
		OfflineClassPayment payment = findPayment(paymentId);
		String reason = reasonOf(body);
		if (!APPROVED.equals(payment.getStatus())) {
			throw state("Only an approved payment can be voided.");
		}
		OfflineClass classItem = findClass(payment.getClassId());
		if (!OfflineClass.STATUS_PAID.equals(classItem.getStatus())) {
			throw state("This class is not paid, so the payment cannot be voided.");
		}
		Instant now = clock.instant();
		payment.setStatus(VOIDED);
		payment.setVoidedBy(actorId);
		payment.setVoidedAt(now);
		payment.setVoidReason(reason);
		payments.saveAndFlush(payment);
		classItem.setStatus(OfflineClass.STATUS_SCHEDULED);
		touch(classItem, actorId, now);
		classes.saveAndFlush(classItem);
		audit.record(PAYMENT_VOIDED, PAYMENT_ENTITY, paymentId);
		return view(classItem, instructorNames());
	}

	/** Approves a waiting not-taken request: the class becomes NOT_TAKEN and is locked. */
	@Transactional
	public ClassView approveNotTaken(UUID decisionId, UUID actorId) {
		ClassView view = approveNotTakenRecord(decisionId, actorId);
		notices.decided(findDecision(decisionId).getRequestedBy(), 1, 0);
		return view;
	}

	ClassView approveNotTakenRecord(UUID decisionId, UUID actorId) {
		OfflineClassDecision decision = findDecision(decisionId);
		if (!PENDING.equals(decision.getStatus())) {
			throw state("This request is not waiting for approval.");
		}
		OfflineClass classItem = findClass(decision.getClassId());
		if (!OfflineClass.STATUS_NOT_TAKEN_PENDING.equals(classItem.getStatus())) {
			throw state("This class is not waiting for a not-taken approval.");
		}
		Instant now = clock.instant();
		decision.setStatus(APPROVED);
		decision.setDecidedBy(actorId);
		decision.setDecidedAt(now);
		decisions.saveAndFlush(decision);
		classItem.setStatus(OfflineClass.STATUS_NOT_TAKEN);
		touch(classItem, actorId, now);
		classes.saveAndFlush(classItem);
		audit.record(NOT_TAKEN_APPROVED, DECISION_ENTITY, decisionId);
		return view(classItem, instructorNames());
	}

	/** Rejects a waiting not-taken request with a reason: the class returns to HELD. */
	@Transactional
	public ClassView rejectNotTaken(UUID decisionId, UUID actorId, JsonNode body) {
		ClassView view = rejectNotTakenRecord(decisionId, actorId, body);
		notices.decided(findDecision(decisionId).getRequestedBy(), 0, 1);
		return view;
	}

	ClassView rejectNotTakenRecord(UUID decisionId, UUID actorId, JsonNode body) {
		OfflineClassDecision decision = findDecision(decisionId);
		String reason = reasonOf(body);
		if (!PENDING.equals(decision.getStatus())) {
			throw state("This request is not waiting for approval.");
		}
		OfflineClass classItem = findClass(decision.getClassId());
		Instant now = clock.instant();
		decision.setStatus(REJECTED);
		decision.setDecidedBy(actorId);
		decision.setDecidedAt(now);
		decision.setDecisionReason(reason);
		decisions.saveAndFlush(decision);
		classItem.setStatus(OfflineClass.STATUS_SCHEDULED);
		touch(classItem, actorId, now);
		classes.saveAndFlush(classItem);
		audit.record(NOT_TAKEN_REJECTED, DECISION_ENTITY, decisionId);
		return view(classItem, instructorNames());
	}

	/** Approves several waiting payments; each id is handled on its own and reported. Each recorder gets one notice for the batch. */
	@Transactional
	public List<BulkResult> approvePayments(UUID actorId, JsonNode body) {
		return bulkDecide(body, id -> {
			approvePaymentRecord(id, actorId);
			return findPayment(id).getRecordedBy();
		});
	}

	/** Approves several waiting not-taken requests; each id is handled on its own and reported. */
	@Transactional
	public List<BulkResult> approveNotTakenMany(UUID actorId, JsonNode body) {
		return bulkDecide(body, id -> {
			approveNotTakenRecord(id, actorId);
			return findDecision(id).getRequestedBy();
		});
	}

	/** The figures the notice jobs read at {@code now}: the unsettled classes, the entries waiting for approval, and the held classes past a week. */
	@Transactional(readOnly = true)
	public Snapshot snapshot(Instant now) {
		Instant weekAgo = now.minus(Duration.ofDays(7));
		long unsettled = 0;
		long overWeek = 0;
		for (OfflineClass classItem : classes.findByStatusIn(UNSETTLED_STORED)) {
			String status = statusAt(classItem, now);
			if (HELD.equals(status)) {
				unsettled++;
				Instant end = endsAt(classItem);
				if (end != null && end.isBefore(weekAgo)) {
					overWeek++;
				}
			}
			else if (OfflineClass.STATUS_PAID_PENDING.equals(status) || OfflineClass.STATUS_NOT_TAKEN_PENDING.equals(status)) {
				unsettled++;
			}
		}
		long pending = payments.countByStatus(PENDING) + decisions.countByStatus(PENDING);
		return new Snapshot(unsettled, pending, overWeek);
	}

	private static Instant endsAt(OfflineClass classItem) {
		LocalTime end = classItem.getEndsAt() != null ? classItem.getEndsAt() : classItem.getStartsAt();
		return end == null ? null : ZonedDateTime.of(classItem.getHeldOn(), end, DHAKA).toInstant();
	}

	// ---- helpers -	// ---- helpers --------------------------------------------------------------------------------------------------------

	/** One decision of a bulk approval: it returns who recorded the entry, and throws when the entry cannot be approved. */
	private interface Step {
		UUID run(UUID id);
	}

	/** Runs the step for each id. A failure is reported for its id; each recorder gets one notice with the counts of this batch. */
	private List<BulkResult> bulkDecide(JsonNode body, Step step) {
		List<BulkResult> results = new ArrayList<>();
		Map<UUID, int[]> tally = new HashMap<>();
		for (UUID id : ids(body)) {
			try {
				UUID recorder = step.run(id);
				tally.computeIfAbsent(recorder, ignored -> new int[2])[0]++;
				results.add(new BulkResult(id, true, null));
			}
			catch (ApiException exception) {
				results.add(new BulkResult(id, false, exception.getMessage()));
			}
		}
		notices.decidedBatch(tally);
		return results;
	}

	private static List<UUID> ids(JsonNode body) {
		JsonNode node = body == null ? null : body.get("ids");
		if (node == null || !node.isArray() || node.isEmpty() || node.size() > MAX_BULK) {
			throw validation(List.of(new FieldErrorDetail("ids", "Choose between 1 and " + MAX_BULK + " entries.")));
		}
		List<UUID> ids = new ArrayList<>();
		for (JsonNode item : node) {
			try {
				ids.add(UUID.fromString(item.asText()));
			}
			catch (IllegalArgumentException exception) {
				throw validation(List.of(new FieldErrorDetail("ids", "One of the entries is not valid.")));
			}
		}
		return ids;
	}

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

	private static boolean isUnsettled(String status) {
		return HELD.equals(status) || OfflineClass.STATUS_PAID_PENDING.equals(status) || OfflineClass.STATUS_NOT_TAKEN_PENDING.equals(status);
	}

	/** The due amount of a class: the override, else the instructor's rate in the batch, else none. */
	private BigDecimal amountOf(UUID batchId, UUID instructorId, BigDecimal override) {
		if (override != null) {
			return override;
		}
		if (instructorId == null) {
			return null;
		}
		return rates.findById(new OfflineBatchInstructorRate.Key(batchId, instructorId)).map(OfflineBatchInstructorRate::getRate)
			.orElse(null);
	}

	private Optional<OfflineClassPayment> livePayment(UUID classId) {
		return payments.findByClassIdOrderByRecordedAtDesc(classId).stream()
			.filter(payment -> PENDING.equals(payment.getStatus()) || APPROVED.equals(payment.getStatus()))
			.findFirst();
	}

	private void touch(OfflineClass classItem, UUID actorId, Instant now) {
		classItem.setStatusBy(actorId);
		classItem.setStatusAt(now);
		classItem.setUpdatedAt(now);
	}

	private ClassView view(OfflineClass classItem, Map<UUID, String> names) {
		PaymentView payment = livePayment(classItem.getId()).map(item -> new PaymentView(item.getId(), item.getInstructorId(),
				item.getAmount(), item.getMethod(), item.getReference(), item.getPaidOn(), item.getNote(), item.getStatus())).orElse(null);
		DecisionView notTaken = decisions.findByClassIdOrderByRequestedAtDesc(classItem.getId()).stream()
			.filter(decision -> PENDING.equals(decision.getStatus()))
			.findFirst()
			.map(decision -> new DecisionView(decision.getId(), decision.getReason(), decision.getStatus(), decision.getRequestedAt()))
			.orElse(null);
		return new ClassView(classItem.getId(), classItem.getBatchId(), classItem.getRoutineId(), classItem.getHeldOn(),
				classItem.getStartsAt(), classItem.getEndsAt(), classItem.getSubject(), classItem.getTopic(), classItem.getKind(),
				classItem.getInstructorId(), classItem.getInstructorId() == null ? null : names.get(classItem.getInstructorId()),
				classItem.getInstructorText(), classItem.getAmount(), classItem.getRateOverride(), statusOf(classItem),
				classItem.getVersion() == null ? 0L : classItem.getVersion(), payment, notTaken, results.hasResult(classItem.getId()));
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

	private OfflineClass findClass(UUID id) {
		return classes.findById(id).orElseThrow(OfflineSupport::notFound);
	}

	private OfflineClassPayment findPayment(UUID id) {
		return payments.findById(id).orElseThrow(OfflineSupport::notFound);
	}

	private OfflineClassDecision findDecision(UUID id) {
		return decisions.findById(id).orElseThrow(OfflineSupport::notFound);
	}

	private static String reasonOf(JsonNode body) {
		List<FieldErrorDetail> problems = new ArrayList<>();
		String reason = OfflineSupport.reason(body != null && body.has("reason") ? body.get("reason").asText() : "", "reason", problems);
		if (!problems.isEmpty()) {
			throw validation(problems);
		}
		return reason;
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

	/** An override rate, or null when it is sent null or left out. A negative or non-number is a problem for that field. */
	private static BigDecimal override(JsonNode body, List<FieldErrorDetail> problems) {
		if (body == null || !body.has("rateOverride") || body.get("rateOverride").isNull()) {
			return null;
		}
		List<FieldErrorDetail> own = new ArrayList<>();
		BigDecimal value = OfflineSupport.money(body, "rateOverride", own);
		problems.addAll(own);
		return value;
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

	/** One class as the staff see it: the computed status, the amount due and the live payment or not-taken request. */
	public record ClassView(UUID id, UUID batchId, UUID routineId, LocalDate heldOn, LocalTime startsAt, LocalTime endsAt, String subject,
			String topic, String kind, UUID instructorId, String instructorName, String instructorText, BigDecimal amount,
			BigDecimal rateOverride, String status, long version, PaymentView payment, DecisionView notTakenRequest, boolean hasResult) {
	}

	/** The live payment of a class (pending or approved). */
	public record PaymentView(UUID id, UUID instructorId, BigDecimal amount, String method, String reference, LocalDate paidOn, String note,
			String status) {
	}

	/** A waiting not-taken request. */
	public record DecisionView(UUID id, String reason, String status, Instant requestedAt) {
	}

	/** The result of one id in a bulk approval. */
	public record BulkResult(UUID id, boolean ok, String message) {
	}

	/** One page of the unsettled classes. */
	public record UnsettledPage(List<ClassView> items, int page, int size, long total) {
	}

	/** The counts the unsettled page shows. */
	public record UnsettledCounts(long held, long waitingApproval, BigDecimal heldAmount) {
	}

	/** The figures the notice jobs read: unsettled classes, entries waiting for approval, and held classes past a week. */
	public record Snapshot(long unsettled, long pendingEntries, long heldOverAWeek) {
	}

	/** One class on the student's routine: no instructor, amount or pay. */
	public record StudentClassView(LocalDate date, String day, LocalTime startsAt, LocalTime endsAt, String subject, String topic, String kind) {
	}

}
