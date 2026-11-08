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

	public LiveExamGrantAdminService(LiveExamGrantRepository grants, LiveExamModuleRepository modules,
			LiveExamRepository exams, UserRepository users, AuditRecorder audit, Clock clock, StudentNoticeWriter notices,
			LiveExamMemberships memberships, LiveExamAccess access, LiveExamPaymentPlanService plans) {
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
		notices.accessGranted(student.getId(), exam == null ? moduleId : exam.getId(), scope);
		audit.record(AuditRecorder.ACCESS_GRANTED, AuditRecorder.LIVE_EXAM_GRANT_ENTITY, grant.getId());
		if (payment == null) {
			return view(grant);
		}
		return view(grant, payment.planId(), payment.receiptNo());
	}

	/**
	 * What an enrolment of one target would charge, for the form. A free exam says so and has no price to take. A target with no
	 * price has none, and the Master Admin types the total. With a {@code studentId} the quote also carries the two warnings:
	 * an unfinished plan on the same target, and access the student already holds by any route (plan section 4).
	 */
	@Transactional(readOnly = true)
	public QuoteView quote(String scopeName, UUID moduleId, UUID examId, UUID studentId) {
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
			if (studentId != null) {
				AccessState state = access.moduleState(studentId, moduleId, now);
				held = state.granted() && !state.expired();
				unfinished = plans.unfinishedPlanId(studentId, LiveExamOrder.MODULE, moduleId, null);
			}
			return new QuoteView(view.price(), view.discountPercent(), view.effectivePrice(), false, true, unfinished, held);
		}
		if (LiveExamOrder.EXAM.equals(scope) && examId != null && moduleId == null) {
			LiveExam exam = exams.findByIdAndDeletedAtIsNull(examId).orElseThrow(this::notFound);
			PriceView view = ExamPricing.view(exam, now, false);
			Boolean held = null;
			UUID unfinished = null;
			if (studentId != null) {
				held = access.canSit(studentId, exam, now);
				unfinished = plans.unfinishedPlanId(studentId, LiveExamOrder.EXAM, null, examId);
			}
			return new QuoteView(view.price(), view.discountPercent(), view.effectivePrice(), exam.isFree(), false, unfinished,
					held);
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
	 * none), and whether the student has active access to it by any route.
	 */
	public record QuoteView(BigDecimal listPrice, Integer discountPercent, BigDecimal standardPrice, boolean free,
			boolean moduleOnly, UUID unfinishedPlanId, Boolean accessHeld) {
	}

	/** Direct enrolment is only for a published exam or package. Existing grants stay when one is archived or unpublished. */
	private static void requirePublished(boolean published) {
		if (!published) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Direct enrollment needs a published exam or package.");
		}
	}

}
