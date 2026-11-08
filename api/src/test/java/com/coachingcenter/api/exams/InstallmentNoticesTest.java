package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.staffnotifications.StaffNotificationGroup;

/**
 * The pure rules of the installment notices (plan sections 6.2 and 7): the message keys, the tab each kind sits under, the staff
 * payload and the staff tab filter. No database. Written, not run.
 */
class InstallmentNoticesTest {

	private static final UUID STUDENT = UUID.fromString("00000000-0000-0000-0000-000000000001");

	private static final UUID INSTALLMENT = UUID.fromString("00000000-0000-0000-0000-000000000002");

	private static final LocalDate DUE = LocalDate.of(2026, 12, 8);

	@Test
	void aDueSoonKeyHoldsTheRecipientTheInstallmentAndItsDate() {
		assertThat(ExamMailTexts.installmentKey(STUDENT, INSTALLMENT, DUE, null))
			.isEqualTo(STUDENT + "/" + INSTALLMENT + "/2026-12-08");
	}

	@Test
	void anOverdueKeyAlsoHoldsTheIsoWeek() {
		assertThat(ExamMailTexts.installmentKey(STUDENT, INSTALLMENT, DUE, "2026-W50"))
			.isEqualTo(STUDENT + "/" + INSTALLMENT + "/2026-12-08/2026-W50");
	}

	@Test
	void everyInstallmentNoticeSitsUnderExamsAndIsNotListedAsExcluded() {
		for (String kind : new String[] { "INSTALLMENT_PAYMENT_RECEIVED", "INSTALLMENT_DUE_SOON", "INSTALLMENT_OVERDUE",
				"INSTALLMENT_PAYMENT_VOIDED", "INSTALLMENT_SCHEDULE_CHANGED" }) {
			assertThat(NoticeGroup.of(kind)).isEqualTo(NoticeGroup.EXAMS);
			assertThat(NoticeGroup.isInstallment(kind)).isTrue();
			// Exams is "not in" the other tabs' kinds, so listing a kind there would hide it from Exams.
			assertThat(NoticeGroup.EXAMS.kinds()).doesNotContain(kind);
		}
		assertThat(NoticeGroup.isInstallment("MCQ_SCORE")).isFalse();
	}

	@Test
	void aStaffPayloadKeepsAMissingBanglaTitleAndMasksLongDigits() {
		Map<String, Object> values = InstallmentStaffNotices.payload("Rahim 123456789012", "Package", null,
				new BigDecimal("2100.00"), DUE, new BigDecimal("4200.00"));
		assertThat(values.keySet()).containsExactly("studentName", "titleEn", "titleBn", "amount", "dueOn", "due");
		assertThat(values).containsEntry("titleBn", null).containsEntry("dueOn", "2026-12-08");
		assertThat((String) values.get("studentName")).startsWith("Rahim 1234").doesNotContainPattern("\\d{9,}");
	}

	@Test
	void aStaffPayloadWithNoNextDateKeepsDueOnNull() {
		Map<String, Object> values = InstallmentStaffNotices.payload("Rahim", "Package", "প্যাকেজ", new BigDecimal("6300.00"),
				null, BigDecimal.ZERO);
		assertThat(values).containsEntry("dueOn", null).containsEntry("titleBn", "প্যাকেজ");
	}

	@Test
	void theStaffGroupReadsItsValuesAndRefusesAnUnknownOne() {
		assertThat(StaffNotificationGroup.parse(null)).isNull();
		assertThat(StaffNotificationGroup.parse("  ")).isNull();
		assertThat(StaffNotificationGroup.parse(" installment ")).isEqualTo(StaffNotificationGroup.INSTALLMENT);
		assertThat(StaffNotificationGroup.isInstallment("INSTALLMENT_OVERDUE")).isTrue();
		assertThat(StaffNotificationGroup.isInstallment("PAYOUT_ACCOUNT_CHANGED")).isFalse();
		assertThatThrownBy(() -> StaffNotificationGroup.parse("bogus")).isInstanceOf(ApiException.class);
	}

}
