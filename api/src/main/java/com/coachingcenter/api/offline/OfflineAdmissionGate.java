package com.coachingcenter.api.offline;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

/**
 * Keeps a student with an admission that is not yet onboarded out of exams and checkout (plan 4.5, D4, C8). Online students
 * and students whose admission is onboarded or closed pass straight through.
 */
@Component
public class OfflineAdmissionGate {

	/** The states before onboarding. A student in one of these may fill the form, but not take an exam or buy. */
	static final Set<String> PENDING = Set.of(OfflineAdmission.AWAITING_FORM, OfflineAdmission.SUBMITTED,
			OfflineAdmission.CHANGES_REQUESTED, OfflineAdmission.READY);

	private final OfflineAdmissionRepository admissions;

	private final OfflineAdmissionSwitch admissionSwitch;

	public OfflineAdmissionGate(OfflineAdmissionRepository admissions, OfflineAdmissionSwitch admissionSwitch) {
		this.admissions = admissions;
		this.admissionSwitch = admissionSwitch;
	}

	/**
	 * Refuses the student with {@code ADMISSION_PENDING} while their admission is not yet onboarded. Does nothing while the feature
	 * switch is off, because no admission can be pending then.
	 */
	public void requireNotPending(UUID studentId) {
		if (!admissionSwitch.isEnabled()) {
			return;
		}
		List<OfflineAdmission> open = admissions.findByStudentIdAndStatusNotOrderByCreatedAtDesc(studentId, OfflineAdmission.CLOSED);
		// A student with one onboarded admission (any batch) keeps their access while another batch is still under review (V94, P11).
		if (open.stream().anyMatch(admission -> OfflineAdmission.ONBOARDED.equals(admission.getStatus()))) {
			return;
		}
		// Only a desk admission locks the student (plan section 8, F4): an online application never takes away exams or checkout.
		if (open.stream().anyMatch(admission -> OfflineAdmission.SOURCE_DESK.equals(admission.getSource())
				&& PENDING.contains(admission.getStatus()))) {
			throw new ApiException(ErrorCode.ADMISSION_PENDING, HttpStatus.FORBIDDEN,
					"Your admission is not complete yet. Fill in and submit your admission form first.");
		}
	}

}
