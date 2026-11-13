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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.error.FieldErrorDetail;
import com.coachingcenter.api.exams.OfflineAccessGrants;
import com.coachingcenter.api.offline.OfflineAdmissionAdminService.AdmissionDetail;
import com.coachingcenter.api.staffnotifications.StaffNotificationKind;
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

	public OfflineAdmissionOnboardingService(OfflineAdmissionRepository admissions, OfflineAdmissionItemRepository items,
			OfflineFeeDueRepository dues, OfflineAdmissionAdminService admin, OfflineAccessGrants grants,
			OfflineAdmissionNotices studentNotices, StaffNotificationWriter staffNotices, AuditRecorder audit, JdbcTemplate jdbc,
			Clock clock) {
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
	public AdmissionDetail onboard(UUID actorId, UUID id) {
		OfflineAdmission a = admin.lock(id);
		if (!OfflineAdmission.READY.equals(a.getStatus())) {
			throw state("Only a ready admission can be onboarded. This admission is " + a.getStatus() + ".");
		}
		List<FieldErrorDetail> problems = admin.readyProblems(a, admin.hasPhoto(a), admin.activeItemCount(id));
		if (!problems.isEmpty()) {
			throw validation(problems);
		}
		Instant now = clock.instant();
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
		writeSchedule(a, a.getFirstDueOn(), courseEndsOn, a.getMonthlyFee(), now);
		jdbc.update("update student_profiles set full_name = ? where user_id = ?", a.getFullName(), a.getStudentId());
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
		return admin.detail(id);
	}

	/**
	 * Changes the monthly fee and the course end after onboarding (plan 4.3, D16). A new fee applies from the next unpaid future due on;
	 * paid dues keep their amounts. A new end cancels the unpaid future dues beyond it, or adds the dues it now covers, and moves the cap
	 * of every live OFFLINE grant. A due with an approved payment is never cancelled, and the change is refused with the reason.
	 */
	@Transactional
	public AdmissionDetail terms(UUID actorId, UUID id, JsonNode body) {
		OfflineAdmission a = admin.lock(id);
		if (!OfflineAdmission.ONBOARDED.equals(a.getStatus())) {
			throw state("The fee and the end date can be changed only after onboarding. This admission is " + a.getStatus() + ".");
		}
		List<FieldErrorDetail> problems = new ArrayList<>();
		BigDecimal fee = money(body, "monthlyFee", problems);
		LocalDate courseEndsOn = day(body, "courseEndsOn", problems);
		if (!problems.isEmpty()) {
			throw validation(problems);
		}
		boolean feeChanged = fee != null && (a.getMonthlyFee() == null || fee.compareTo(a.getMonthlyFee()) != 0);
		boolean endChanged = courseEndsOn != null && !courseEndsOn.equals(a.getCourseEndsOn());
		if (!feeChanged && !endChanged) {
			return admin.view(a);
		}
		BigDecimal oldFee = a.getMonthlyFee();
		LocalDate oldEnd = a.getCourseEndsOn();
		BigDecimal newFee = fee != null ? fee : oldFee;
		LocalDate newEnd = courseEndsOn != null ? courseEndsOn : oldEnd;
		if (!newEnd.isAfter(a.getFirstDueOn())) {
			throw validation(List.of(new FieldErrorDetail("courseEndsOn", "The course end date must be after the first due date.")));
		}
		Instant now = clock.instant();
		LocalDate today = OfflineFeeSchedule.today(now);
		Set<UUID> paid = paidDueIds(a.getId());
		List<LocalDate> target = OfflineFeeSchedule.dueDates(a.getFirstDueOn(), newEnd);
		List<OfflineFeeDue> rows = dues.findByAdmissionIdOrderBySeqAsc(a.getId());
		if (endChanged) {
			for (OfflineFeeDue due : rows) {
				if (due.getCancelledAt() == null && !target.contains(due.getDueOn())) {
					if (paid.contains(due.getId())) {
						throw validation(List.of(new FieldErrorDetail("courseEndsOn",
								"The due on " + due.getDueOn() + " has a payment, so the course cannot end before it.")));
					}
					due.setCancelledAt(now);
					dues.saveAndFlush(due);
				}
			}
			for (int index = 0; index < target.size(); index++) {
				short seq = (short) (index + 1);
				OfflineFeeDue existing = rows.stream().filter(row -> row.getSeq() == seq).findFirst().orElse(null);
				if (existing == null) {
					dues.saveAndFlush(newDue(a.getId(), seq, target.get(index), newFee, now));
				}
				else if (existing.getCancelledAt() != null) {
					existing.setCancelledAt(null);
					existing.setDueOn(target.get(index));
					dues.saveAndFlush(existing);
				}
			}
		}
		if (feeChanged) {
			for (OfflineFeeDue due : dues.findByAdmissionIdAndCancelledAtIsNullOrderBySeqAsc(a.getId())) {
				if (due.getDueOn().isAfter(today) && !paid.contains(due.getId())) {
					due.setAmount(newFee);
					dues.saveAndFlush(due);
				}
			}
			a.setMonthlyFee(newFee);
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
		notifyTerms(a, now, feeChanged ? oldFee : null, feeChanged ? newFee : null, endChanged ? oldEnd : null,
				endChanged ? newEnd : null);
		return admin.view(a);
	}

	/**
	 * Closes an onboarded admission (plan 4.2, 5): the unpaid dues after today are cancelled, dues already past stay owed, every
	 * OFFLINE grant is revoked with the reason, and the student is told once. The reason is required.
	 */
	@Transactional
	public AdmissionDetail close(UUID actorId, UUID id, String reasonText) {
		OfflineAdmission a = admin.lock(id);
		if (!OfflineAdmission.ONBOARDED.equals(a.getStatus())) {
			throw state("Only an onboarded admission can be closed. This admission is " + a.getStatus() + ".");
		}
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
		a.setStatus(OfflineAdmission.CLOSED);
		a.setClosedAt(now);
		a.setClosedReason(text);
		admissions.saveAndFlush(a);
		audit.record(AuditRecorder.OFFLINE_ADMISSION_CLOSED, AuditRecorder.OFFLINE_ADMISSION_ENTITY, id);
		studentNotices.notice(a.getStudentId(), OfflineAdmissionNotices.ADMISSION_CLOSED, id.toString());
		studentNotices.mail(a.getStudentId(), OfflineAdmissionNotices.ADMISSION_CLOSED, a.getStudentId() + "/" + id,
				Map.of("reason", text, "closedOn", today.toString()));
		return admin.view(a);
	}

	/**
	 * Writes one row per due date, seq 1 first (plan 4.3). Each row is a fixed amount of the monthly fee.
	 */
	private void writeSchedule(OfflineAdmission a, LocalDate firstDueOn, LocalDate courseEndsOn, BigDecimal fee, Instant now) {
		List<LocalDate> dates = OfflineFeeSchedule.dueDates(firstDueOn, courseEndsOn);
		for (int index = 0; index < dates.size(); index++) {
			dues.saveAndFlush(newDue(a.getId(), (short) (index + 1), dates.get(index), fee, now));
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
		return Map.of("batchEn", batch[0], "batchBn", batch[1], "monthlyFee", a.getMonthlyFee().toPlainString(),
				"firstDueOn", a.getFirstDueOn().toString(), "courseEndsOn", a.getCourseEndsOn().toString(), "itemsEn", titles[0],
				"itemsBn", titles[1]);
	}

	private void notifyTerms(OfflineAdmission a, Instant now, BigDecimal oldFee, BigDecimal newFee, LocalDate oldEnd,
			LocalDate newEnd) {
		List<Map<String, String>> changes = new ArrayList<>();
		if (newFee != null) {
			changes.add(Map.of("labelBn", "মাসিক ফি", "labelEn", "Monthly fee", "old", oldFee.toPlainString() + " BDT",
					"new", newFee.toPlainString() + " BDT"));
		}
		if (newEnd != null) {
			changes.add(Map.of("labelBn", "কোর্স শেষ", "labelEn", "Course ends", "old", oldEnd.toString(), "new", newEnd.toString()));
		}
		studentNotices.notice(a.getStudentId(), OfflineAdmissionNotices.ADMISSION_UPDATED, a.getId() + ":" + now.toEpochMilli());
		studentNotices.mailWithChanges(a.getStudentId(), OfflineAdmissionNotices.ADMISSION_UPDATED,
				a.getStudentId() + "/" + a.getId() + "/" + now.toEpochMilli(), changes);
	}

}
