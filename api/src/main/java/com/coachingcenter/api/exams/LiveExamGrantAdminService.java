package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.error.FieldErrorDetail;
import com.coachingcenter.api.users.User;
import com.coachingcenter.api.users.UserRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Access that staff give by hand, and take back (R-2, R-3). Purchases stay in {@link LiveExamOrderService}. A grant made
 * with a {@code payment} also opens a payment plan (plan direct-enrolment-installments); one without is No charge.
 */
@Service
public class LiveExamGrantAdminService {

	/** The reason stored when a payment is taken and the Master Admin gives none. */
	static final String OFFLINE_REASON = "Offline payment";

	private final LiveExamGrantRepository grants;

	private final LiveExamModuleRepository modules;

	private final LiveExamRepository exams;

	private final LiveExamMemberships memberships;

	private final UserRepository users;

	private final AuditRecorder audit;

	private final Clock clock;

	private final StudentNoticeWriter notices;

	private final LiveExamAccess access;

	private final LiveExamPaymentPlanService plans;

	private final LiveExamRosterService rosters;

	private final LiveExamOverlaps overlapping;

	private final ExamMailService examMail;

	private final AfterCommit afterCommit;

	public LiveExamGrantAdminService(LiveExamGrantRepository grants, LiveExamModuleRepository modules,
			LiveExamRepository exams, UserRepository users, AuditRecorder audit, Clock clock, StudentNoticeWriter notices,
			LiveExamMemberships memberships, LiveExamAccess access, LiveExamPaymentPlanService plans,
			LiveExamRosterService rosters, LiveExamOverlaps overlapping, ExamMailService examMail, AfterCommit afterCommit) {
		this.access = access;
		this.memberships = memberships;
		this.grants = grants;
		this.modules = modules;
		this.exams = exams;
		this.users = users;
		this.audit = audit;
		this.clock = clock;
		this.notices = notices;
		this.plans = plans;
		this.rosters = rosters;
		this.overlapping = overlapping;
		this.examMail = examMail;
		this.afterCommit = afterCommit;
	}

	/**
	 * Grants a student access to one module or one exam, by hand. Without a payment a reason is required (No charge). With a
	 * payment the reason is optional, and a payment plan is opened in the same transaction. Refused with 409 while the student
	 * already has an active grant on the same target.
	 */
	@Transactional
	public GrantView grant(CurrentActor actor, GrantRequest request) {
		Instant now = clock.instant();
		String scope = request.scope().trim().toUpperCase();
		UUID moduleId;
		LiveExam exam = null;
		LiveExamPaymentPlanService.PriceSnapshot price = null;
		if (LiveExamOrder.MODULE.equals(scope)) {
			if (request.moduleId() == null || request.examId() != null) {
				throw validationFailed();
			}
			moduleId = request.moduleId();
			// Locks the package first, so a roster add and this enrolment cannot both pass the roster check (plan 14.4).
			modules.lockById(moduleId).orElseThrow(this::notFound);
			LiveExamModule target = modules.findByIdAndDeletedAtIsNull(moduleId).orElseThrow(this::notFound);
			requirePublished(LiveExamModule.PUBLISHED.equals(target.getStatus()));
			price = snapshot(ExamPricing.view(target, now));
		}
		else if (LiveExamOrder.EXAM.equals(scope)) {
			if (request.examId() == null) {
				throw validationFailed();
			}
			exam = exams.findByIdAndDeletedAtIsNull(request.examId()).orElseThrow(this::notFound);
			requirePublished(LiveExam.PUBLISHED.equals(exam.getStatus()));
			// An exam grant names no package (R5). A package named here must be one the exam is in now.
			if (request.moduleId() != null && !memberships.activeModuleIds(exam.getId()).contains(request.moduleId())) {
				throw validationFailed();
			}
			moduleId = null;
			price = snapshot(ExamPricing.view(exam, now, false));
		}
		else {
			throw validationFailed();
		}
		String reason = request.reason() == null ? "" : request.reason().trim();
		if (request.payment() == null) {
			if (reason.isEmpty()) {
				throw validationFailed();
			}
		}
		else {
			if (exam != null && exam.isFree()) {
				throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.",
						List.of(new FieldErrorDetail("payment", "A free exam takes no payment. Use No charge.")));
			}
			if (reason.isEmpty()) {
				reason = OFFLINE_REASON;
			}
		}
		User student = users.findByIdAndDeletedAtIsNull(request.studentId())
			.filter(user -> Role.STUDENT.name().equals(user.getRole()))
			.orElseThrow(this::notFound);
		if (exam == null) {
			if (access.holdsModuleGrant(student.getId(), moduleId, now)) {
				throw grantActive();
			}
			// A roster row whose access has ended does not block (plan 14.7, P2).
			if (access.rosterLive(student.getId(), moduleId, now)) {
				throw rosterBlock();
			}
			access.retireExpiredGrants(student.getId(), scope, moduleId, null, now, LiveExamAccess.RETIRED);
		}
		else {
			if (access.holdsExamGrant(student.getId(), exam, now)) {
				throw grantActive();
			}
			access.retireExpiredGrants(student.getId(), scope, null, exam, now, LiveExamAccess.RETIRED);
		}
		LiveExamGrant grant = new LiveExamGrant();
		grant.setId(UUID.randomUUID());
		grant.setStudentId(student.getId());
		grant.setScope(scope);
		grant.setModuleId(moduleId);
		grant.setExamId(exam == null ? null : exam.getId());
		grant.setSource(LiveExamGrant.SOURCE_MANUAL);
		grant.setGrantedBy(actor.userId());
		grant.setReason(reason);
		grant.setCreatedAt(now);
		try {
			grants.saveAndFlush(grant);
		}
		catch (DataIntegrityViolationException exception) {
			// Two requests for the same student and target raced: the unique index keeps one (plan section 6).
			throw grantActive();
		}
		LiveExamPaymentPlanService.PlanView payment = null;
		if (request.payment() != null) {
			payment = plans.open(grant.getId(), student.getId(), price, request.payment(), actor.userId());
		}
		UUID targetId = exam == null ? moduleId : exam.getId();
		notices.accessGranted(student.getId(), targetId, scope);
		audit.record(AuditRecorder.ACCESS_GRANTED, AuditRecorder.LIVE_EXAM_GRANT_ENTITY, grant.getId());
		// The exam-time email goes out after the commit, so a refused or rolled-back enrolment sends nothing (plan 14.3, P52).
		afterCommit.run(() -> examMail.onDirectEnrolment(scope, targetId, student.getId()));
		if (payment == null) {
			return view(grant);
		}
		return view(grant, payment.planId(), payment.receiptNo());
	}

	/**
	 * What an enrolment of one target would charge, for the form. A free exam says so and has no price to take. A target with no
	 * price has none, and the Master Admin types the total. With a {@code studentId} the quote also carries the warnings: an
	 * unfinished plan on the same target, access the student already holds by any route (plan section 4), the roster and direct
	 * enrolment state of a package, and the overlapping exams or packages (plan 14.4). Plan figures in the overlaps are sent only
	 * when {@code withMoney} is true.
	 */
	@Transactional(readOnly = true)
	public QuoteView quote(String scopeName, UUID moduleId, UUID examId, UUID studentId, boolean withMoney) {
		Instant now = clock.instant();
		String scope = scopeName == null ? "" : scopeName.trim().toUpperCase();
		if (studentId != null && users.findByIdAndDeletedAtIsNull(studentId)
			.filter(user -> Role.STUDENT.name().equals(user.getRole()))
			.isEmpty()) {
			throw notFound();
		}
		if (LiveExamOrder.MODULE.equals(scope) && moduleId != null && examId == null) {
			LiveExamModule module = modules.findByIdAndDeletedAtIsNull(moduleId).orElseThrow(this::notFound);
			PriceView view = ExamPricing.view(module, now);
			Boolean held = null;
			UUID unfinished = null;
			Boolean onRoster = null;
			Boolean rosterStarted = null;
			Boolean directGrantActive = null;
			List<QuoteOverlap> overlaps = null;
			if (studentId != null) {
				AccessState state = access.moduleState(studentId, moduleId, now);
				held = state.granted() && !state.expired();
				unfinished = plans.unfinishedPlanId(studentId, LiveExamOrder.MODULE, moduleId, null);
				boolean rostered = access.rosterLive(studentId, moduleId, now);
				onRoster = rostered;
				rosterStarted = rostered && rosters.startedAny(moduleId, studentId);
				directGrantActive = access.holdsDirectModuleGrant(studentId, moduleId, now);
				overlaps = overlapping.forPackage(studentId, moduleId, now, withMoney);
			}
			return new QuoteView(view.price(), view.discountPercent(), view.effectivePrice(), false, true, unfinished, held,
					onRoster, rosterStarted, directGrantActive, overlaps);
		}
		if (LiveExamOrder.EXAM.equals(scope) && examId != null && moduleId == null) {
			LiveExam exam = exams.findByIdAndDeletedAtIsNull(examId).orElseThrow(this::notFound);
			PriceView view = ExamPricing.view(exam, now, false);
			Boolean held = null;
			UUID unfinished = null;
			Boolean onRoster = null;
			Boolean rosterStarted = null;
			Boolean directGrantActive = null;
			List<QuoteOverlap> overlaps = null;
			if (studentId != null) {
				held = access.canSit(studentId, exam, now);
				unfinished = plans.unfinishedPlanId(studentId, LiveExamOrder.EXAM, null, examId);
				// The roster block applies to a package only (D22), so an exam quote never reports a roster.
				onRoster = false;
				rosterStarted = false;
				directGrantActive = access.holdsDirectExamGrant(studentId, exam, now);
				overlaps = overlapping.forExam(studentId, exam, now, withMoney);
			}
			return new QuoteView(view.price(), view.discountPercent(), view.effectivePrice(), exam.isFree(), false, unfinished,
					held, onRoster, rosterStarted, directGrantActive, overlaps);
		}
		throw validationFailed();
	}

	/**
	 * Takes back access from a purchase or a manual grant (R-2). The row stays for the audit trail, and no money moves.
	 * A grant that is already revoked is not found.
	 */
	@Transactional
	public GrantView revoke(CurrentActor actor, UUID grantId, String reason) {
		String text = reason == null ? "" : reason.trim();
		if (text.isEmpty() || text.length() > 500) {
			throw validationFailed();
		}
		LiveExamGrant grant = grants.findById(grantId)
			.filter(found -> found.getRevokedAt() == null)
			.orElseThrow(this::notFound);
		grant.setRevokedAt(clock.instant());
		grant.setRevokedBy(actor.userId());
		grant.setRevokeReason(text);
		grants.saveAndFlush(grant);
		audit.record(AuditRecorder.ACCESS_REVOKED, AuditRecorder.LIVE_EXAM_GRANT_ENTITY, grant.getId());
		return view(grant);
	}

	static GrantView view(LiveExamGrant grant) {
		return view(grant, null, null);
	}

	/** The grant as the response shows it. A plan and its first receipt are named when the enrolment took a payment. */
	static GrantView view(LiveExamGrant grant, UUID paymentPlanId, String receiptNo) {
		return new GrantView(grant.getId(), grant.getStudentId(), grant.getScope(), grant.getModuleId(), grant.getExamId(),
				grant.getSource(), grant.getGrantedBy(), grant.getReason(), grant.getCreatedAt(), grant.getRevokedAt(),
				grant.getRevokedBy(), grant.getRevokeReason(), paymentPlanId, receiptNo);
	}

	private static LiveExamPaymentPlanService.PriceSnapshot snapshot(PriceView view) {
		return new LiveExamPaymentPlanService.PriceSnapshot(view.price(), view.discountPercent(), view.effectivePrice());
	}

	private static ApiException grantActive() {
		return new ApiException(ErrorCode.GRANT_ALREADY_ACTIVE, HttpStatus.CONFLICT,
				"This student already has active access to that module or exam.");
	}

	private static ApiException rosterBlock() {
		return new ApiException(ErrorCode.STUDENT_ON_ROSTER, HttpStatus.CONFLICT,
				"This student is on the roster for this package. Remove them from the roster before enrolling them.");
	}

	private static ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
	}

	private ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	/**
	 * {@code scope} is MODULE (with {@code moduleId}) or EXAM (with {@code examId}). {@code reason} is required without a
	 * {@code payment} (No charge), and optional with one. {@code payment} is the optional payment part (plan section 6).
	 */
	public record GrantRequest(@NotNull UUID studentId, @NotBlank String scope, UUID moduleId, UUID examId,
			@Size(max = 500) String reason, @Valid LiveExamPaymentPlanService.DirectPaymentRequest payment) {

		/** A No charge enrolment, as it was before payments existed. */
		public GrantRequest(UUID studentId, String scope, UUID moduleId, UUID examId, String reason) {
			this(studentId, scope, moduleId, examId, reason, null);
		}
	}

	/** {@code paymentPlanId} and {@code receiptNo} are set only when the enrolment took a payment (null for No charge). */
	public record GrantView(UUID id, UUID studentId, String scope, UUID moduleId, UUID examId, String source,
			UUID grantedBy, String reason, Instant createdAt, Instant revokedAt, UUID revokedBy, String revokeReason,
			UUID paymentPlanId, String receiptNo) {
	}

	/**
	 * The price an enrolment of a target would charge. {@code standardPrice} is the price after any active discount, and is null
	 * when there is no price. {@code free} is true for a free exam, which takes no payment. {@code unfinishedPlanId} and
	 * {@code accessHeld} are set only when the quote named a student: the plan with an unpaid balance on this target (null when
	 * none), and whether the student has active access to it by any route. The rest are set only with a student too:
	 * {@code onRoster} (a live roster row on the package; false for an exam), {@code rosterStarted} (that row has a sitting),
	 * {@code directGrantActive} (a live manual grant on this target) and {@code overlaps} (the exams or packages the student
	 * already holds that overlap this enrolment). An overlap never blocks.
	 */
	public record QuoteView(BigDecimal listPrice, Integer discountPercent, BigDecimal standardPrice, boolean free,
			boolean moduleOnly, UUID unfinishedPlanId, Boolean accessHeld, Boolean onRoster, Boolean rosterStarted,
			Boolean directGrantActive, List<QuoteOverlap> overlaps) {
	}

	/**
	 * One exam or package the student already holds that overlaps the enrolment. {@code kind} is EXAM or PACKAGE, and {@code via}
	 * is DIRECT, ONLINE, BUNDLE, ROSTER or PACKAGE (an exam held through a package). {@code planId}, {@code paid} and {@code due}
	 * are set only when the access comes from a payment plan and the caller may see money.
	 */
	public record QuoteOverlap(String kind, UUID id, String titleEn, String titleBn, String via, UUID planId,
			BigDecimal paid, BigDecimal due, String packageTitleEn, String packageTitleBn) {
	}

	/** Direct enrolment is only for a published exam or package. Existing grants stay when one is archived or unpublished. */
	private static void requirePublished(boolean published) {
		if (!published) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Direct enrollment needs a published exam or package.");
		}
	}

}
