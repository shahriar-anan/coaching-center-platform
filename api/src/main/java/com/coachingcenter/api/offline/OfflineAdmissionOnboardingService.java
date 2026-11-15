package com.coachingcenter.api.offline;

import static com.coachingcenter.api.offline.OfflineSupport.day;
import static com.coachingcenter.api.offline.OfflineSupport.has;
import static com.coachingcenter.api.offline.OfflineSupport.money;
import static com.coachingcenter.api.offline.OfflineSupport.reason;
import static com.coachingcenter.api.offline.OfflineSupport.state;
import static com.coachingcenter.api.offline.OfflineSupport.validation;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.math.BigInteger;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.auth.AuthService;
import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.error.FieldErrorDetail;
import com.coachingcenter.api.exams.OfflineAccessGrants;
import com.coachingcenter.api.offline.OfflineAdmissionAdminService.AdmissionDetail;
import com.coachingcenter.api.staffnotifications.StaffNotificationKind;
import com.coachingcenter.api.users.AccountService;
import com.coachingcenter.api.users.User;
import com.coachingcenter.api.users.UserRepository;
import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.staffnotifications.StaffNotificationWriter;

import tools.jackson.databind.JsonNode;

/**
 * The Master Admin's steps on an offline admission (plan sections 4.2, 4.3, 4.4, 5; D2, D16, C5, C12): onboarding (the one button),
 * the fee and the end date after it, and closing. Each one runs in one transaction. A refusal in the middle rolls back every row it
 * had written, so the admission is left as it was (plan 4.4).
 */
@Service
public class OfflineAdmissionOnboardingService {

	private final OfflineAdmissionRepository admissions;

	private final OfflineAdmissionItemRepository items;

	private final OfflineFeeDueRepository dues;

	private final OfflineAdmissionAdminService admin;

	private final OfflineAccessGrants grants;

	private final OfflineAdmissionNotices studentNotices;

	private final StaffNotificationWriter staffNotices;

	private final AuditRecorder audit;

	private final JdbcTemplate jdbc;

	private final Clock clock;

	private final AccountService accounts;

	private final AuthService auth;

	private final UserRepository users;

	private final OfflineApplicationMail publicMail;

	/** Digits only, 4 to 12 long (rule O2). */
	private static final Pattern ROLL = Pattern.compile("\\d{4,12}");

	/** The field error for a roll another student already has. */
	static final String ROLL_TAKEN = "This roll number is already used by another student.";

	private final OfflineAccountMatcher matcher;

	/** Wrong join codes an admission takes before it is locked until a new code is sent (plan section 9). */
	static final int MAX_CODE_TRIES = 5;

	public OfflineAdmissionOnboardingService(OfflineAdmissionRepository admissions, OfflineAdmissionItemRepository items,
			OfflineFeeDueRepository dues, OfflineAdmissionAdminService admin, OfflineAccessGrants grants,
			OfflineAdmissionNotices studentNotices, StaffNotificationWriter staffNotices, AuditRecorder audit, JdbcTemplate jdbc,
			Clock clock, AccountService accounts, AuthService auth, UserRepository users, OfflineApplicationMail publicMail,
			OfflineAccountMatcher matcher) {
		this.accounts = accounts;
		this.auth = auth;
		this.users = users;
		this.publicMail = publicMail;
		this.matcher = matcher;
		this.admissions = admissions;
		this.items = items;
		this.dues = dues;
		this.admin = admin;
		this.grants = grants;
		this.studentNotices = studentNotices;
		this.staffNotices = staffNotices;
		this.audit = audit;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/**
	 * Onboards a ready admission (D2). In one transaction: every active exam or package gets an OFFLINE grant capped at the end of the
	 * course end date in Dhaka (no per-item notice, C5); the monthly schedule is written; the form's name becomes the profile name; the
	 * student gets one approval notice and one mail; and whoever marked it ready is told, unless that was the Master Admin.
	 * An item the student already holds refuses the whole onboarding, naming it.
	 */
	@Transactional
	public AdmissionDetail onboard(UUID actorId, UUID id, String rollNo) {
		OfflineAdmission a = admin.lock(id);
		if (!OfflineAdmission.READY.equals(a.getStatus())) {
			throw state("Only a ready admission can be onboarded. This admission is " + a.getStatus() + ".");
		}
		if (a.getJoinCodeSentAt() != null && a.getJoinCodeUsedAt() == null) {
			throw state("A join code was already sent for this admission. Send a new code instead.");
		}
		List<FieldErrorDetail> problems = admin.readyProblems(a, admin.hasPhoto(a));
		if (!problems.isEmpty()) {
			throw validation(problems);
		}
		// Rule R4: every onboarded student has a roll number, unique across all batches.
		a.setRollNo(checkedRoll(rollNo, a.getId()));
		flushRoll(a);
		Instant now = clock.instant();
		if (a.getStudentId() == null) {
			// Q3 and the 13 Nov decision: an applicant with no account is joined to the account with the same email. A phone that belongs
			// to another account is never joined: the form must be fixed first, so a typed number cannot attach a stranger's account.
			Optional<User> match = matcher.find(a.getEmail());
			if (match.isPresent()) {
				return joinMatchedAccount(actorId, a, match.get(), now);
			}
			Optional<User> phoneOwner = matcher.phoneOwner(a.getMobile());
			if (phoneOwner.isPresent()) {
				throw validation(List.of(new FieldErrorDetail("mobile", "This mobile number belongs to the account of "
						+ matcher.nameOf(phoneOwner.get().getId()) + ", whose email is different. Send the form back so the applicant fixes the "
						+ "email or the mobile number.")));
			}
			return makeAccountAndOnboard(actorId, a, now);
		}
		if (!OfflineAdmission.SOURCE_DESK.equals(a.getSource())) {
			// A signed-in applicant (or a matched one): the join code is mailed, and the student joins the batch from the link (plan B, C).
			return joinedByCode(actorId, a, now);
		}
		complete(actorId, a, now, true);
		noticeBatchAdded(a, now);
		return admin.detail(id);
	}

	/** The roll a student is given (rules R4, O2): required, 4 to 12 digits, and not used by any other admission. */
	String checkedRoll(String raw, UUID admissionId) {
		String roll = raw == null ? "" : raw.trim();
		if (roll.isEmpty()) {
			throw validation(List.of(new FieldErrorDetail("rollNo", "Enter the roll number.")));
		}
		if (!ROLL.matcher(roll).matches()) {
			throw validation(List.of(new FieldErrorDetail("rollNo", "Use 4 to 12 digits for the roll number.")));
		}
		if (rollTaken(roll, admissionId)) {
			throw validation(List.of(new FieldErrorDetail("rollNo", ROLL_TAKEN)));
		}
		return roll;
	}

	/**
	 * Writes the roll at once. Two onboardings that check the same free roll at the same moment both pass {@link #checkedRoll}; the unique
	 * index lets only one of them commit, and the other is answered with the same field error instead of a server error (rule R4).
	 */
	private void flushRoll(OfflineAdmission a) {
		try {
			admissions.saveAndFlush(a);
		}
		catch (DataIntegrityViolationException racing) {
			throw validation(List.of(new FieldErrorDetail("rollNo", ROLL_TAKEN)));
		}
	}

	/** Whether another admission (not {@code except}, when given) already has this roll. */
	boolean rollTaken(String roll, UUID except) {
		Integer count = except == null
				? jdbc.queryForObject("select count(*) from offline_admissions where roll_no = ?", Integer.class, roll)
				: jdbc.queryForObject("select count(*) from offline_admissions where roll_no = ? and id <> ?", Integer.class, roll, except);
		return count != null && count > 0;
	}

	/**
	 * The next free roll for an admission's batch (rule A7): one more than the highest roll in the batch, or the batch's starting roll for
	 * the first student. Taken rolls are skipped, and the width of the number is kept. Null when the batch has no roll to start from.
	 */
	@Transactional(readOnly = true)
	public SuggestedRoll suggestRoll(UUID id) {
		OfflineAdmission a = admin.load(id);
		if (a.getBatchId() == null) {
			return new SuggestedRoll(null);
		}
		String highest = jdbc.queryForObject("select max(roll_no::numeric)::text from offline_admissions where batch_id = ? and roll_no is not null",
				String.class, a.getBatchId());
		String start = jdbc.queryForObject("select roll_start from offline_batches where id = ?", String.class, a.getBatchId());
		String base = highest != null ? highest : start;
		if (base == null) {
			return new SuggestedRoll(null);
		}
		BigInteger next = new BigInteger(base);
		if (highest != null) {
			next = next.add(BigInteger.ONE);
		}
		while (rollTaken(String.format("%0" + base.length() + "d", next), null)) {
			next = next.add(BigInteger.ONE);
		}
		return new SuggestedRoll(String.format("%0" + base.length() + "d", next));
	}

	/** A suggested roll, or null when there is none to suggest. Sent as {@code suggestion}. */
	public record SuggestedRoll(@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
			String suggestion) {
	}

	/**
	 * The Master Admin sets or changes the roll of an onboarded student (plan O3, and students onboarded before rolls existed). The same
	 * rules as at onboarding apply, and the change is audited.
	 */
	@Transactional
	public AdmissionDetail setRoll(UUID actorId, UUID id, String rollNo) {
		OfflineAdmission a = admin.lock(id);
		if (!OfflineAdmission.ONBOARDED.equals(a.getStatus())) {
			throw state("The roll number can be set only after onboarding. This admission is " + a.getStatus() + ".");
		}
		a.setRollNo(checkedRoll(rollNo, id));
		flushRoll(a);
		audit.record(AuditRecorder.OFFLINE_ROLL_SET, AuditRecorder.OFFLINE_ADMISSION_ENTITY, id);
		return admin.detail(id);
	}

	/** The join code goes out for a student's admission. The batch-added notice waits for the join, when the batch is really added (P14). */
	private AdmissionDetail joinedByCode(UUID actorId, OfflineAdmission a, Instant now) {
		return sendJoinCode(actorId, a, now, false);
	}

	/** P14: the student already has access to other batches, so they get a notice that a batch was added. None when they have no other. */
	private void noticeBatchAdded(OfflineAdmission a, Instant now) {
		if (a.getStudentId() != null && !alreadyAccessTo(a.getStudentId(), a.getId()).isEmpty()) {
			studentNotices.notice(a.getStudentId(), OfflineAdmissionNotices.ADMISSION_BATCH_ADDED, a.getId() + ":" + now.toEpochMilli());
		}
	}

	/** The English names of the batches the student has access to (onboarded), not counting this admission (P14). */
	List<String> alreadyAccessTo(UUID studentId, UUID admissionId) {
		return jdbc.queryForList("""
				select b.name_en from offline_admissions a join offline_batches b on b.id = a.batch_id
				where a.student_id = ? and a.status = 'ONBOARDED' and a.id <> ?
				order by a.onboarded_at, a.id
				""", String.class, studentId, admissionId);
	}

	/**
	 * The batches a student has access to, as {bn, en} pairs, in the order they were onboarded: the onboarded ones, and then the batch of this
	 * admission when {@code includeThis} is set (P14: the student email lists them all).
	 */
	List<String[]> batchesWithAccess(UUID studentId, UUID admissionId, UUID batchId, boolean includeThis) {
		List<String[]> batches = new ArrayList<>(jdbc.query("""
				select b.name_bn, b.name_en from offline_admissions a join offline_batches b on b.id = a.batch_id
				where a.student_id = ? and a.status = 'ONBOARDED' and a.id <> ?
				order by a.onboarded_at, a.id
				""", (rs, row) -> new String[] { rs.getString(1), rs.getString(2) }, studentId, admissionId));
		if (includeThis) {
			String[] own = batchNames(batchId);
			batches.add(new String[] { own[1], own[0] });
		}
		return batches;
	}

	/** The core of onboarding, shared by the desk, a join, and a new account: grants, schedule, status, notices, and the staff note. */
	private void complete(UUID actorId, OfflineAdmission a, Instant now, boolean renameProfile) {
		UUID id = a.getId();
		LocalDate courseEndsOn = a.getCourseEndsOn();
		Instant accessEndsAt = OfflineFeeSchedule.endOfDay(courseEndsOn);
		for (OfflineAdmissionItem item : admin.activeItems(id)) {
			String name = admin.itemName(item.getScope(), item.getModuleId(), item.getExamId());
			if (grants.holds(a.getStudentId(), item.getScope(), item.getModuleId(), item.getExamId(), now)) {
				throw new ApiException(ErrorCode.GRANT_ALREADY_ACTIVE, HttpStatus.CONFLICT,
						"The student already has active access to \"" + name + "\". Remove it from this admission, then onboard again.");
			}
			UUID grantId = grants.grant(a.getStudentId(), item.getScope(), item.getModuleId(), item.getExamId(), accessEndsAt,
					actorId, now);
			item.setGrantId(grantId);
			items.saveAndFlush(item);
			audit.record(AuditRecorder.OFFLINE_ACCESS_GRANTED, AuditRecorder.LIVE_EXAM_GRANT_ENTITY, grantId);
		}
		writeSchedule(a, now);
		if (renameProfile) {
			jdbc.update("update student_profiles set full_name = ? where user_id = ?", a.getFullName(), a.getStudentId());
		}
		a.setStatus(OfflineAdmission.ONBOARDED);
		a.setOnboardedBy(actorId);
		a.setOnboardedAt(now);
		admissions.saveAndFlush(a);
		audit.record(AuditRecorder.OFFLINE_ADMISSION_ONBOARDED, AuditRecorder.OFFLINE_ADMISSION_ENTITY, id);

		String[] titles = admin.itemTitles(id);
		studentNotices.notice(a.getStudentId(), OfflineAdmissionNotices.ADMISSION_APPROVED, id.toString());
		studentNotices.mail(a.getStudentId(), OfflineAdmissionNotices.ADMISSION_APPROVED, a.getStudentId() + "/" + id,
				approvalPayload(a, titles));
		UUID readyBy = a.getReadyBy();
		if (readyBy != null && !readyBy.equals(actorId)) {
			staffNotices.notify(readyBy, StaffNotificationKind.ADMISSION_ONBOARDED, Map.of("personName", OfflineAdmissionAdminService.personName(a)),
					OfflineAdmissionAdminService.link(id), "ADMISSION_ONBOARDED:" + id + ":" + readyBy);
		}
	}

	/**
	 * An applicant whose email or phone already belongs to a student account (Q3): that account is linked, nothing is made, and the join
	 * code goes out. A staff account cannot be admitted this way.
	 */
	private AdmissionDetail joinMatchedAccount(UUID actorId, OfflineAdmission a, User account, Instant now) {
		if (!Role.STUDENT.name().equals(account.getRole())) {
			throw validation(List.of(new FieldErrorDetail("email", "This email or phone belongs to a staff account, so it cannot be admitted.")));
		}
		if (admin.matchedHasBatch(account.getId(), a)) {
			String[] batch = batchNames(a.getBatchId());
			throw state(matcher.nameOf(account.getId()) + " already has access to " + batch[0] + ". Send this application back, or close it.");
		}
		a.setStudentId(account.getId());
		a.setMatchedUserId(account.getId());
		admin.adoptApplicationPhoto(a);
		audit.record(AuditRecorder.OFFLINE_ADMISSION_MATCHED, AuditRecorder.OFFLINE_ADMISSION_ENTITY, a.getId());
		return joinedByCode(actorId, a, now);
	}

	/**
	 * An applicant with no account and no match: the account is made (pending), the admission is onboarded at once, and the activation
	 * code goes out by mail with the batch (Q3, plan 2A.5). The account is active after the activation (AuthService.activate).
	 */
	private AdmissionDetail makeAccountAndOnboard(UUID actorId, OfflineAdmission a, Instant now) {
		AccountService.CreatedAccount created = accounts.createAdmittedStudent(a.getFullName(), a.getMobile(), a.getEmail(), actorId,
				now.plus(OfflineApplicationMail.CODE_DAYS, ChronoUnit.DAYS));
		a.setStudentId(created.id());
		admin.adoptApplicationPhoto(a);
		audit.record(AuditRecorder.OFFLINE_ADMISSION_ACCOUNT_CREATED, AuditRecorder.OFFLINE_ADMISSION_ENTITY, a.getId());
		complete(actorId, a, now, true);
		String[] batch = batchNames(a.getBatchId());
		publicMail.activation(created.email(), a.getFullName(), batch[1], batch[0], created.activationCode(), false);
		// The activation code is the one that was sent, so the staff panel shows when, and can send a new one.
		a.setJoinCodeSentAt(now);
		return admin.detail(a.getId());
	}

	/**
	 * Makes a new join code for an admission that waits for one, and mails it to the account (plan B, C). The code is kept as a hash with its
	 * expiry, so the old one stops working the moment this one is saved (P8). The in-app notice carries no code.
	 */
	private AdmissionDetail sendJoinCode(UUID actorId, OfflineAdmission a, Instant now, boolean resent) {
		String code = OfflineApplicationMail.newCode();
		a.setJoinCodeHash(publicMail.hash(code));
		a.setJoinCodeExpiresAt(now.plus(OfflineApplicationMail.CODE_DAYS, ChronoUnit.DAYS));
		a.setJoinCodeSentAt(now);
		a.setJoinCodeSentBy(actorId);
		a.setJoinCodeUsedAt(null);
		a.setJoinCodeFailures(0);
		admissions.saveAndFlush(a);
		studentNotices.notice(a.getStudentId(), OfflineAdmissionNotices.ADMISSION_JOIN_CODE, a.getId() + ":" + now.toEpochMilli());
		String[] batch = batchNames(a.getBatchId());
		String to = accountEmail(a);
		if (to != null) {
			publicMail.join(to, matcher.nameOf(a.getStudentId()), batch[1], batch[0], batchesWithAccess(a.getStudentId(), a.getId(), a.getBatchId(), true), code, resent);
		}
		audit.record(resent ? AuditRecorder.OFFLINE_ADMISSION_CODE_RESENT : AuditRecorder.OFFLINE_ADMISSION_JOIN_CODE_SENT,
				AuditRecorder.OFFLINE_ADMISSION_ENTITY, a.getId());
		return admin.detail(a.getId());
	}

	/**
	 * A signed-in student joins a batch with the join code from the mail (plan B, C, and section 5). The code is checked against each of the
	 * student's admissions that wait for one. A wrong try is counted and kept (the caller refuses it); five wrong tries lock the code until a
	 * new one is sent. Returns the admission that was joined, or the reason it was not.
	 */
	@Transactional
	public JoinOutcome join(UUID studentId, String code) {
		Instant now = clock.instant();
		String hash = publicMail.hash(code == null ? "" : code.trim().toUpperCase(Locale.ROOT));
		List<OfflineAdmission> waiting = admissions.findByStudentIdAndStatusAndJoinCodeHashIsNotNullOrderByCreatedAtDesc(studentId,
				OfflineAdmission.READY);
		if (waiting.isEmpty()) {
			return JoinOutcome.refused(ErrorCode.INVALID_CODE, "Code is no longer valid.");
		}
		for (OfflineAdmission candidate : waiting) {
			if (candidate.getJoinCodeUsedAt() != null || !hash.equals(candidate.getJoinCodeHash())) {
				continue;
			}
			if (candidate.getJoinCodeFailures() >= MAX_CODE_TRIES) {
				return JoinOutcome.refused(ErrorCode.CODE_ATTEMPTS_EXCEEDED, "Code is no longer valid.");
			}
			if (candidate.getJoinCodeExpiresAt().isBefore(now)) {
				return JoinOutcome.refused(ErrorCode.CODE_EXPIRED, "Code is no longer valid.");
			}
			OfflineAdmission a = admin.lock(candidate.getId());
			// The setup was checked when the code went out; the batch or an item may have changed since. The code is kept, so the office
			// can fix the admission and the same code still works, or send a new one.
			if (!admin.readyProblems(a, admin.hasPhoto(a)).isEmpty() || holdsAnItem(a, now)) {
				return JoinOutcome.refused(ErrorCode.ADMISSION_STATE,
						"This admission needs the office to look at it again before you can join. Please contact the office.");
			}
			a.setJoinCodeUsedAt(now);
			admissions.saveAndFlush(a);
			complete(a.getJoinCodeSentBy(), a, now, false);
			noticeBatchAdded(a, now);
			audit.record(AuditRecorder.OFFLINE_ADMISSION_JOINED, AuditRecorder.OFFLINE_ADMISSION_ENTITY, a.getId());
			return JoinOutcome.joined(a.getId());
		}
		// No code matches: every waiting admission counts one wrong try, so no code can be guessed without limit, and the caller refuses.
		for (OfflineAdmission candidate : waiting) {
			OfflineAdmission locked = admin.lock(candidate.getId());
			if (locked.getJoinCodeFailures() < MAX_CODE_TRIES) {
				locked.setJoinCodeFailures(locked.getJoinCodeFailures() + 1);
				admissions.saveAndFlush(locked);
			}
		}
		return JoinOutcome.refused(ErrorCode.INVALID_CODE, "Code is not valid.");
	}

	/** True when the student already holds one of the admission's exams or packages, which would refuse the onboarding. */
	private boolean holdsAnItem(OfflineAdmission a, Instant now) {
		return admin.activeItems(a.getId()).stream()
			.anyMatch(item -> grants.holds(a.getStudentId(), item.getScope(), item.getModuleId(), item.getExamId(), now));
	}

	/**
	 * N2: a student whose account the onboarding made gets the approval notice and mail when they activate it, since a pending account gets
	 * neither. Runs inside the activation's transaction. The dedupe keys are the onboarding's own, so nobody is told twice.
	 */
	@org.springframework.context.event.EventListener
	public void onActivated(com.coachingcenter.api.auth.UserActivated event) {
		users.flush();
		for (OfflineAdmission a : admissions.findByStudentIdOrderByCreatedAtDesc(event.userId())) {
			if (!OfflineAdmission.ONBOARDED.equals(a.getStatus())) {
				continue;
			}
			studentNotices.notice(a.getStudentId(), OfflineAdmissionNotices.ADMISSION_APPROVED, a.getId().toString());
			studentNotices.mail(a.getStudentId(), OfflineAdmissionNotices.ADMISSION_APPROVED, a.getStudentId() + "/" + a.getId(),
					approvalPayload(a, admin.itemTitles(a.getId())));
		}
	}

	/**
	 * A new code for an admission that waits for one (P8): a join code for an account holder, or a new activation code for an account the
	 * onboarding made and that is not yet activated. The old code stops working.
	 */
	@Transactional
	public AdmissionDetail resendCode(UUID actorId, UUID id) {
		OfflineAdmission a = admin.lock(id);
		Instant now = clock.instant();
		if (a.getStudentId() != null && OfflineAdmission.READY.equals(a.getStatus()) && a.getJoinCodeSentAt() != null
				&& a.getJoinCodeUsedAt() == null) {
			return sendJoinCode(actorId, a, now, true);
		}
		User account = a.getStudentId() == null ? null : users.findByIdAndDeletedAtIsNull(a.getStudentId()).orElse(null);
		if (OfflineAdmission.ONBOARDED.equals(a.getStatus()) && OfflineAdmission.SOURCE_PUBLIC.equals(a.getSource())
				&& a.getMatchedUserId() == null && account != null && "PENDING_ACTIVATION".equals(account.getStatus())) {
			String code = auth.issueActivationCodeUnmailed(account, actorId, now.plus(OfflineApplicationMail.CODE_DAYS, ChronoUnit.DAYS));
			a.setJoinCodeSentAt(now);
			String[] batch = batchNames(a.getBatchId());
			publicMail.activation(accountEmail(a), a.getFullName(), batch[1], batch[0], code, true);
			audit.record(AuditRecorder.OFFLINE_ADMISSION_CODE_RESENT, AuditRecorder.OFFLINE_ADMISSION_ENTITY, id);
			return admin.detail(id);
		}
		throw state("There is no code to send for this admission.");
	}

	/** The result of a join: the admission joined, or the code's refusal (the caller turns a refusal into the error). */
	public record JoinOutcome(UUID admissionId, ErrorCode refusal, String message) {

		static JoinOutcome joined(UUID admissionId) {
			return new JoinOutcome(admissionId, null, null);
		}

		static JoinOutcome refused(ErrorCode code, String message) {
			return new JoinOutcome(null, code, message);
		}

	}

	/** The batch's English and Bangla names, in that order. */
	private String[] batchNames(UUID batchId) {
		return jdbc.queryForObject("select name_en, name_bn from offline_batches where id = ?",
				(rs, row) -> new String[] { rs.getString("name_en"), rs.getString("name_bn") }, batchId);
	}

	/** The address the mail goes to: the account's, else the form's (an account with no address yet). Null when neither. */
	private String accountEmail(OfflineAdmission a) {
		String fromAccount = a.getStudentId() == null ? null
				: users.findByIdAndDeletedAtIsNull(a.getStudentId()).map(User::getEmail).orElse(null);
		if (fromAccount != null && !fromAccount.isBlank()) {
			return fromAccount;
		}
		return a.getEmail() == null || a.getEmail().isBlank() ? null : a.getEmail().trim().toLowerCase(Locale.ROOT);
	}

	/**
	 * Changes the course fee, the number of instalments and the course end after onboarding (plan 4.3, D16, course-fee-plan.md). Instalments
	 * already paid (in part or in full) and instalments whose date has come are kept as they are. A new fee or count spreads what is left of
	 * the fee over the other instalments, whole taka with the rest on the last, and cancels unpaid instalments beyond the new count. A new
	 * end moves the cap of every live OFFLINE grant; the last instalment must fall on or before it. Refused, with the reason, when the fee
	 * is less than what is kept, or when the kept instalments leave no room for the rest.
	 */
	@Transactional
	public AdmissionDetail terms(UUID actorId, UUID id, JsonNode body) {
		OfflineAdmission a = admin.lock(id);
		if (!OfflineAdmission.ONBOARDED.equals(a.getStatus())) {
			throw state("The fee and the end date can be changed only after onboarding. This admission is " + a.getStatus() + ".");
		}
		List<FieldErrorDetail> problems = new ArrayList<>();
		BigDecimal fee = money(body, "courseFee", problems);
		Integer count = OfflineSupport.installments(body, "installments", problems);
		LocalDate courseEndsOn = day(body, "courseEndsOn", problems);
		if (!problems.isEmpty()) {
			throw validation(problems);
		}
		// An admission onboarded before V100 that the backfill could not fill reads its fee and count from its live instalments.
		List<OfflineFeeDue> live = dues.findByAdmissionIdAndCancelledAtIsNullOrderBySeqAsc(a.getId());
		BigDecimal oldFee = a.getCourseFee() != null ? a.getCourseFee()
				: live.stream().map(OfflineFeeDue::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
		Integer oldCount = a.getInstallments() != null ? a.getInstallments() : Math.max(1, live.size());
		LocalDate oldEnd = a.getCourseEndsOn();
		boolean feeChanged = fee != null && (oldFee == null || fee.compareTo(oldFee) != 0);
		boolean countChanged = count != null && !count.equals(oldCount);
		boolean endChanged = courseEndsOn != null && !courseEndsOn.equals(oldEnd);
		if (!feeChanged && !countChanged && !endChanged) {
			return admin.view(a);
		}
		BigDecimal newFee = fee != null ? fee : oldFee;
		int newCount = count != null ? count : oldCount;
		LocalDate newEnd = courseEndsOn != null ? courseEndsOn : oldEnd;
		if (!newEnd.isAfter(a.getFirstDueOn())) {
			throw validation(List.of(new FieldErrorDetail("courseEndsOn", "The course end date must be after the first due date.")));
		}
		LocalDate last = OfflineFeeSchedule.installmentDate(a.getFirstDueOn(), newCount);
		if (last.isAfter(newEnd)) {
			throw validation(List.of(new FieldErrorDetail(countChanged ? "installments" : "courseEndsOn", "The last instalment would be due on "
					+ last + ", after the course ends on " + newEnd + ". Choose fewer instalments or a later course end.")));
		}
		Instant now = clock.instant();
		if (feeChanged || countChanged) {
			respread(a, newFee, newCount, now);
			a.setCourseFee(newFee);
			a.setInstallments(newCount);
			a.setMonthlyFee(OfflineAdmissionAdminService.firstInstallment(a));
		}
		if (endChanged) {
			a.setCourseEndsOn(newEnd);
			Instant cap = OfflineFeeSchedule.endOfDay(newEnd);
			for (OfflineAdmissionItem item : items.findByAdmissionIdAndRemovedAtIsNull(a.getId())) {
				if (item.getGrantId() != null) {
					grants.capEnd(item.getGrantId(), cap);
				}
			}
		}
		admissions.saveAndFlush(a);
		audit.record(AuditRecorder.OFFLINE_ADMISSION_TERMS_CHANGED, AuditRecorder.OFFLINE_ADMISSION_ENTITY, id);
		notifyTerms(a, now, feeChanged ? oldFee : null, feeChanged ? newFee : null, countChanged ? oldCount : null,
				countChanged ? newCount : null, endChanged ? oldEnd : null, endChanged ? newEnd : null);
		return admin.view(a);
	}

	/**
	 * Spreads what is left of a new course fee over the instalments that are still open (course-fee-plan.md section 3). An instalment with
	 * an approved payment, or whose date has come, is kept with its amount; the others, up to {@code count}, share the rest; open ones
	 * beyond {@code count} are cancelled.
	 */
	private void respread(OfflineAdmission a, BigDecimal fee, int count, Instant now) {
		LocalDate today = OfflineFeeSchedule.today(now);
		Set<UUID> paid = paidDueIds(a.getId());
		List<OfflineFeeDue> rows = dues.findByAdmissionIdOrderBySeqAsc(a.getId());
		Map<Integer, OfflineFeeDue> bySeq = new HashMap<>();
		BigDecimal keptSum = BigDecimal.ZERO;
		Set<Integer> keptSeqs = new HashSet<>();
		for (OfflineFeeDue due : rows) {
			bySeq.put((int) due.getSeq(), due);
			if (due.getCancelledAt() != null || (!paid.contains(due.getId()) && due.getDueOn().isAfter(today))) {
				continue;
			}
			if (due.getSeq() > count) {
				throw validation(List.of(new FieldErrorDetail("installments", "Instalment " + due.getSeq() + " (due " + due.getDueOn()
						+ ") is already paid or due, so the course fee needs at least " + due.getSeq() + " instalments.")));
			}
			keptSeqs.add((int) due.getSeq());
			keptSum = keptSum.add(due.getAmount());
		}
		BigDecimal rest = fee.subtract(keptSum);
		if (rest.signum() < 0) {
			throw validation(List.of(new FieldErrorDetail("courseFee", "৳" + keptSum.toPlainString()
					+ " is already paid or due, so the course fee cannot be less than that.")));
		}
		List<Integer> open = new ArrayList<>();
		for (int seq = 1; seq <= count; seq++) {
			if (!keptSeqs.contains(seq)) {
				open.add(seq);
			}
		}
		if (open.isEmpty() && rest.signum() > 0) {
			throw validation(List.of(new FieldErrorDetail("installments", "Every instalment is already paid or due. Add an instalment for the ৳"
					+ rest.toPlainString() + " that is left.")));
		}
		if (!open.isEmpty() && rest.compareTo(BigDecimal.valueOf(open.size())) < 0) {
			throw validation(List.of(new FieldErrorDetail("installments", "Only ৳" + rest.toPlainString() + " is left for " + open.size()
					+ " instalments. Choose fewer instalments.")));
		}
		List<BigDecimal> amounts = OfflineFeeSchedule.split(rest, open.size());
		for (int index = 0; index < open.size(); index++) {
			int seq = open.get(index);
			LocalDate dueOn = OfflineFeeSchedule.installmentDate(a.getFirstDueOn(), seq);
			OfflineFeeDue existing = bySeq.get(seq);
			if (existing == null) {
				dues.saveAndFlush(newDue(a.getId(), (short) seq, dueOn, amounts.get(index), now));
			}
			else {
				existing.setCancelledAt(null);
				existing.setDueOn(dueOn);
				existing.setAmount(amounts.get(index));
				dues.saveAndFlush(existing);
			}
		}
		for (OfflineFeeDue due : rows) {
			if (due.getSeq() > count && due.getCancelledAt() == null) {
				due.setCancelledAt(now);
				dues.saveAndFlush(due);
			}
		}
	}

	/**
	 * Closes an admission in any open state (plan 4.2, 5; the 13 Nov decision D1): the unpaid dues after today are cancelled, dues already
	 * past stay owed, every OFFLINE grant is revoked with the reason, and the student is told once. Before onboarding there are no dues or
	 * grants; a live join code and a reopen link stop working. The reason is required.
	 */
	@Transactional
	public AdmissionDetail close(UUID actorId, UUID id, String reasonText) {
		OfflineAdmission a = admin.lock(id);
		if (OfflineAdmission.CLOSED.equals(a.getStatus())) {
			throw state("This admission is already closed.");
		}
		boolean wasOnboarded = OfflineAdmission.ONBOARDED.equals(a.getStatus());
		List<FieldErrorDetail> problems = new ArrayList<>();
		String text = reason(reasonText, "reason", problems);
		if (!problems.isEmpty()) {
			throw validation(problems);
		}
		Instant now = clock.instant();
		LocalDate today = OfflineFeeSchedule.today(now);
		Set<UUID> paid = paidDueIds(a.getId());
		for (OfflineFeeDue due : dues.findByAdmissionIdAndCancelledAtIsNullOrderBySeqAsc(a.getId())) {
			if (due.getDueOn().isAfter(today) && !paid.contains(due.getId())) {
				due.setCancelledAt(now);
				dues.saveAndFlush(due);
			}
		}
		for (OfflineAdmissionItem item : items.findByAdmissionIdAndRemovedAtIsNull(a.getId())) {
			if (item.getGrantId() != null) {
				grants.revoke(item.getGrantId(), actorId, "Admission closed", now);
				audit.record(AuditRecorder.OFFLINE_ACCESS_REVOKED, AuditRecorder.LIVE_EXAM_GRANT_ENTITY, item.getGrantId());
			}
		}
		if (!wasOnboarded) {
			// Nothing was granted or scheduled yet. The codes and links of the application stop working.
			OfflineAdmissionAdminService.clearJoinCode(a);
			a.setReopenTokenHash(null);
			a.setReopenTokenExpiresAt(null);
		}
		a.setStatus(OfflineAdmission.CLOSED);
		a.setClosedAt(now);
		a.setClosedReason(text);
		admissions.saveAndFlush(a);
		audit.record(AuditRecorder.OFFLINE_ADMISSION_CLOSED, AuditRecorder.OFFLINE_ADMISSION_ENTITY, id);
		if (a.getStudentId() != null) {
			studentNotices.notice(a.getStudentId(), OfflineAdmissionNotices.ADMISSION_CLOSED, id.toString());
			studentNotices.mail(a.getStudentId(), OfflineAdmissionNotices.ADMISSION_CLOSED, a.getStudentId() + "/" + id,
					Map.of("reason", text, "closedOn", today.toString()));
		}
		return admin.view(a);
	}

	/**
	 * Writes one row per instalment, seq 1 first (plan 4.3, course-fee-plan.md): the course fee split into the chosen number of monthly
	 * instalments from the first due date, whole taka with the rest on the last.
	 */
	private void writeSchedule(OfflineAdmission a, Instant now) {
		List<BigDecimal> amounts = OfflineFeeSchedule.split(a.getCourseFee(), a.getInstallments());
		List<LocalDate> dates = OfflineFeeSchedule.installmentDates(a.getFirstDueOn(), a.getInstallments());
		for (int index = 0; index < amounts.size(); index++) {
			dues.saveAndFlush(newDue(a.getId(), (short) (index + 1), dates.get(index), amounts.get(index), now));
		}
	}

	private static OfflineFeeDue newDue(UUID admissionId, short seq, LocalDate dueOn, BigDecimal amount, Instant now) {
		OfflineFeeDue due = new OfflineFeeDue();
		due.setId(UUID.randomUUID());
		due.setAdmissionId(admissionId);
		due.setSeq(seq);
		due.setDueOn(dueOn);
		due.setAmount(amount);
		due.setCreatedAt(now);
		return due;
	}

	/** The dues of the admission that have at least one approved payment allocated to them. */
	private Set<UUID> paidDueIds(UUID admissionId) {
		return new HashSet<>(jdbc.queryForList("""
				select distinct al.due_id from offline_fee_allocations al
				join offline_fee_payments p on p.id = al.payment_id
				join offline_fee_dues d on d.id = al.due_id
				where d.admission_id = ? and p.status = 'APPROVED'
				""", UUID.class, admissionId));
	}

	/** The values the approval mail is written with: the batch, the fee, the dates and the exams and packages, in both languages. */
	private Map<String, String> approvalPayload(OfflineAdmission a, String[] titles) {
		String[] batch = jdbc.queryForObject("select name_en, name_bn from offline_batches where id = ?",
				(rs, row) -> new String[] { rs.getString("name_en"), rs.getString("name_bn") }, a.getBatchId());
		List<String[]> allBatches = batchesWithAccess(a.getStudentId(), a.getId(), a.getBatchId(), true);
		return Map.of("batchEn", batch[0], "batchBn", batch[1], "courseFee", a.getCourseFee().toPlainString(),
				"installments", String.valueOf(a.getInstallments()),
				"firstDueOn", a.getFirstDueOn().toString(), "courseEndsOn", a.getCourseEndsOn().toString(), "itemsEn", titles[0],
				"itemsBn", titles[1], "batchesEn", joinNames(allBatches, 1), "batchesBn", joinNames(allBatches, 0));
	}

	/** The names at one index of each {bn, en} pair, joined with a semicolon. Used for the batch list in the student email (P14). */
	private static String joinNames(List<String[]> pairs, int index) {
		return String.join("; ", pairs.stream().map(pair -> pair[index]).toList());
	}

	private void notifyTerms(OfflineAdmission a, Instant now, BigDecimal oldFee, BigDecimal newFee, Integer oldCount, Integer newCount,
			LocalDate oldEnd, LocalDate newEnd) {
		List<Map<String, String>> changes = new ArrayList<>();
		if (newFee != null) {
			changes.add(Map.of("labelBn", "কোর্স ফি", "labelEn", "Course fee", "old", oldFee.toPlainString() + " BDT",
					"new", newFee.toPlainString() + " BDT"));
		}
		if (newCount != null) {
			changes.add(Map.of("labelBn", "কিস্তির সংখ্যা", "labelEn", "Instalments", "old", String.valueOf(oldCount),
					"new", String.valueOf(newCount)));
		}
		if (newEnd != null) {
			changes.add(Map.of("labelBn", "কোর্স শেষ", "labelEn", "Course ends", "old", oldEnd.toString(), "new", newEnd.toString()));
		}
		studentNotices.notice(a.getStudentId(), OfflineAdmissionNotices.ADMISSION_UPDATED, a.getId() + ":" + now.toEpochMilli());
		studentNotices.mailWithChanges(a.getStudentId(), OfflineAdmissionNotices.ADMISSION_UPDATED,
				a.getStudentId() + "/" + a.getId() + "/" + now.toEpochMilli(), changes);
	}

}
