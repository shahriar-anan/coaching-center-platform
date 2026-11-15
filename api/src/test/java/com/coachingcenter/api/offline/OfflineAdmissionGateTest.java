package com.coachingcenter.api.offline;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.coachingcenter.api.common.error.ApiException;

/**
 * Finding F4 (review, 13 Nov): only a desk admission locks a student out. An online application, whether it was started signed in or from
 * the public form, never takes away exams or checkout.
 */
class OfflineAdmissionGateTest {

	@Test
	void onlyADeskAdmissionLocksTheStudent() {
		UUID student = UUID.randomUUID();
		OfflineAdmissionRepository admissions = mock(OfflineAdmissionRepository.class);
		OfflineAdmissionSwitch admissionSwitch = mock(OfflineAdmissionSwitch.class);
		when(admissionSwitch.isEnabled()).thenReturn(true);
		OfflineAdmissionGate gate = new OfflineAdmissionGate(admissions, admissionSwitch);

		when(admissions.findByStudentIdAndStatusNotOrderByCreatedAtDesc(student, OfflineAdmission.CLOSED))
			.thenReturn(List.of(admission(OfflineAdmission.SOURCE_ACCOUNT, OfflineAdmission.SUBMITTED)));
		assertThatCode(() -> gate.requireNotPending(student)).doesNotThrowAnyException();

		when(admissions.findByStudentIdAndStatusNotOrderByCreatedAtDesc(student, OfflineAdmission.CLOSED))
			.thenReturn(List.of(admission(OfflineAdmission.SOURCE_PUBLIC, OfflineAdmission.AWAITING_FORM)));
		assertThatCode(() -> gate.requireNotPending(student)).doesNotThrowAnyException();

		when(admissions.findByStudentIdAndStatusNotOrderByCreatedAtDesc(student, OfflineAdmission.CLOSED))
			.thenReturn(List.of(admission(OfflineAdmission.SOURCE_DESK, OfflineAdmission.SUBMITTED)));
		assertThatThrownBy(() -> gate.requireNotPending(student)).isInstanceOf(ApiException.class);
	}

	private static OfflineAdmission admission(String source, String status) {
		OfflineAdmission admission = new OfflineAdmission();
		admission.setSource(source);
		admission.setStatus(status);
		return admission;
	}

}
