package com.coachingcenter.api.offline;

import static com.coachingcenter.api.offline.OfflineSupport.state;
import static com.coachingcenter.api.offline.OfflineSupport.validation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.auth.AuthService;
import com.coachingcenter.api.auth.PhoneNumbers;
import com.coachingcenter.api.auth.UserActivated;
import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.error.FieldErrorDetail;
import com.coachingcenter.api.users.AccountService;
import com.coachingcenter.api.users.User;
import com.coachingcenter.api.users.UserRepository;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The desk's phone-only start of an offline admission (product decision, 13 November 2026). Staff or the Master Admin type a phone
 * number; the account, the AWAITING_FORM admission and a 10-minute activation code are made in one transaction. The desk can read the
 * live code again until it expires or the student activates, and can make a new one at any time, which replaces the old one.
 */
@Service
public class OfflineAdmissionDeskService {

	/** How long a desk activation code lasts. */
	static final Duration CODE_LIFETIME = Duration.ofMinutes(10);

	private static final String PENDING = "PENDING_ACTIVATION";

	private final OfflineAdmissionRepository admissions;

	private final AccountService accounts;

	private final AuthService auth;

	private final UserRepository users;

	private final AuditRecorder audit;

	private final Clock clock;

	private final JdbcTemplate jdbc;

	public OfflineAdmissionDeskService(OfflineAdmissionRepository admissions, AccountService accounts, AuthService auth,
			UserRepository users, AuditRecorder audit, Clock clock, JdbcTemplate jdbc) {
		this.jdbc = jdbc;
		this.admissions = admissions;
		this.accounts = accounts;
		this.auth = auth;
		this.users = users;
		this.audit = audit;
		this.clock = clock;
	}

	/** What the desk gets back when it starts an admission: the admission, the account, and the code to read out. */
	public record DeskCreated(UUID admissionId, UUID studentId, String phone, String code, Instant expiresAt) {
	}

	/**
	 * The live code, or the reason there is none. {@code expired} is true when a code was issued and has run out; {@code activated} is
	 * true once the student has set a password. The shape is the same in every case, with nulls where a value does not apply.
	 */
	public record CodeView(@JsonInclude(JsonInclude.Include.ALWAYS) String code,
			@JsonInclude(JsonInclude.Include.ALWAYS) Instant expiresAt, boolean expired, boolean activated) {
	}

	/** A new code, made by the desk. */
	public record CodeIssued(String code, Instant expiresAt) {
	}

	/**
	 * Starts the admission from a phone number. The phone is normalised and checked for a duplicate by the account (DUPLICATE_PHONE, 409).
	 * The code expires 10 minutes after the clock's now.
	 */
	@Transactional
	public DeskCreated create(String phone, UUID createdBy) {
		String normalized = normalizedPhone(phone);
		refuseIfOpenAdmission(normalized);
		Instant expiresAt = clock.instant().plus(CODE_LIFETIME);
		AccountService.CreatedAccount created = accounts.createDeskStudent(normalized, createdBy, expiresAt);
		OfflineAdmission admission = new OfflineAdmission();
		admission.setId(UUID.randomUUID());
		admission.setStudentId(created.id());
		admission.setStatus(OfflineAdmission.AWAITING_FORM);
		// The form starts with the phone the desk entered (as 01...), so the student does not type it twice.
		admission.setMobile(normalized.substring(3));
		admission.setCreatedBy(createdBy);
		admission.setCreatedAt(clock.instant());
		admission.setSubmitCount(0);
		admission.setActivationCode(created.activationCode());
		admission.setActivationCodeExpiresAt(expiresAt);
		admission.setActivationCodeIssuedBy(createdBy);
		admissions.saveAndFlush(admission);
		audit.record(AuditRecorder.OFFLINE_ADMISSION_CREATED, AuditRecorder.OFFLINE_ADMISSION_ENTITY, admission.getId());
		return new DeskCreated(admission.getId(), created.id(), created.phone(), created.activationCode(), expiresAt);
	}

	/**
	 * The code the desk can read out. An expired code is never returned: it is cleared here, the first time it is read after it runs out
	 * (V87). A student who has activated gets no code.
	 */
	@Transactional
	public CodeView current(UUID admissionId) {
		OfflineAdmission admission = admission(admissionId);
		if (activated(student(admission))) {
			return new CodeView(null, null, false, true);
		}
		Instant expiresAt = admission.getActivationCodeExpiresAt();
		if (expiresAt == null) {
			return new CodeView(null, null, false, false);
		}
		if (!expiresAt.isAfter(clock.instant())) {
			admission.setActivationCode(null);
			return new CodeView(null, expiresAt, true, false);
		}
		audit.record(AuditRecorder.ACTIVATION_CODE_SHOWN, AuditRecorder.OFFLINE_ADMISSION_ENTITY, admissionId);
		return new CodeView(admission.getActivationCode(), expiresAt, false, false);
	}

	/**
	 * A new 10-minute code. Every earlier unused activation code of the student stops working (the activation code is replaced, so the
	 * old one is refused). Refused once the student has activated, and for a closed admission.
	 */
	@Transactional
	public CodeIssued regenerate(UUID admissionId, UUID actorId) {
		OfflineAdmission admission = admissions.findForUpdate(admissionId).orElseThrow(OfflineSupport::notFound);
		if (OfflineAdmission.CLOSED.equals(admission.getStatus())) {
			throw state("This admission is closed, so it has no activation code.");
		}
		// F13: a 10-minute desk code is made only for a desk admission; an online applicant gets a join code or a 7-day activation code.
		if (!OfflineAdmission.SOURCE_DESK.equals(admission.getSource())) {
			throw state("This admission is not a desk admission, so a desk code cannot be made for it.");
		}
		User student = student(admission);
		if (activated(student)) {
			throw state("The student has already activated the account.");
		}
		Instant expiresAt = clock.instant().plus(CODE_LIFETIME);
		String code = auth.issueActivationCode(student, actorId, expiresAt);
		admission.setActivationCode(code);
		admission.setActivationCodeExpiresAt(expiresAt);
		admission.setActivationCodeIssuedBy(actorId);
		audit.record(AuditRecorder.ACTIVATION_CODE_ISSUED, AuditRecorder.OFFLINE_ADMISSION_ENTITY, admissionId);
		return new CodeIssued(code, expiresAt);
	}

	/**
	 * Runs inside the activation's transaction (AuthService.activate). The plain code is cleared, so the desk cannot read it again, and the
	 * activation time is kept.
	 */
	@EventListener
	public void onActivated(UserActivated event) {
		admissions.findFirstByStudentIdAndStatusNotOrderByCreatedAtDesc(event.userId(), OfflineAdmission.CLOSED).ifPresent(admission -> {
			admission.setActivationCode(null);
			admission.setActivationCodeExpiresAt(null);
			if (admission.getActivatedAt() == null) {
				admission.setActivatedAt(clock.instant());
			}
		});
	}

	private OfflineAdmission admission(UUID admissionId) {
		return admissions.findById(admissionId).orElseThrow(OfflineSupport::notFound);
	}

	private User student(OfflineAdmission admission) {
		return users.findByIdAndDeletedAtIsNull(admission.getStudentId()).orElseThrow(OfflineSupport::notFound);
	}

	/** Activated means the student has set a password, so the account is no longer pending. */
	static boolean activated(User student) {
		return !PENDING.equals(student.getStatus());
	}

	/**
	 * P13: the desk is for new students. A phone that belongs to a student with an open offline admission is refused, and the message names the
	 * batch they are in. An account with no open admission is refused as a duplicate phone, as before (the account step).
	 */
	private void refuseIfOpenAdmission(String phone) {
		users.findByPhoneAndDeletedAtIsNull(phone).ifPresent(existing -> admissions
			.findFirstByStudentIdAndStatusNotOrderByCreatedAtDesc(existing.getId(), OfflineAdmission.CLOSED).ifPresent(open -> {
				String[] batch = open.getBatchId() == null ? new String[] { null, null } : batchNames(open.getBatchId());
				String text = "This phone number already has an offline admission" + (batch[0] == null ? "." : " in " + batch[0] + ".")
					+ " Use the batch page to apply instead.";
				throw new ApiException(ErrorCode.OPEN_ADMISSION, HttpStatus.CONFLICT, text, new OpenAdmissionDetails(batch[0], batch[1]));
			}));
	}

	/** The batch's English and Bangla names, or nulls when the batch is gone. */
	private String[] batchNames(UUID batchId) {
		return jdbc.query("select name_en, name_bn from offline_batches where id = ?",
				(rs, row) -> new String[] { rs.getString("name_en"), rs.getString("name_bn") }, batchId).stream().findFirst()
			.orElse(new String[] { null, null });
	}

	/** The batch the refusal names, in both languages (the web reads these as the batch's names). */
	public record OpenAdmissionDetails(String batchNameEn, String batchNameBn) {
	}

	private static String normalizedPhone(String phone) {
		try {
			return PhoneNumbers.normalize(phone);
		}
		catch (ApiException invalid) {
			throw validation(List.of(new FieldErrorDetail("phone", "Enter a Bangladeshi mobile number, such as 01711000012.")));
		}
	}

}
