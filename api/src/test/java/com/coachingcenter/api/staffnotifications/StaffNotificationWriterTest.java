package com.coachingcenter.api.staffnotifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** The rules the writer applies before a row is written: the link, the dedupe key and the payload. No database. */
class StaffNotificationWriterTest {

	@Test
	void aLinkMustBeAPathInsideTheStaffSite() {
		assertThatCode(() -> StaffNotificationWriter.checkLink("/staff/pay/123e4567-e89b-12d3-a456-426614174000")).doesNotThrowAnyException();
		assertThatCode(() -> StaffNotificationWriter.checkLink("/staff/notifications?unread=true")).doesNotThrowAnyException();
		assertThatCode(() -> StaffNotificationWriter.checkLink("/staff/my-pay")).doesNotThrowAnyException();
	}

	@Test
	void anOutsideOrOddLinkIsRefused() {
		for (String link : Arrays.asList(null, "", "/notices", "//evil.example/staff", "https://evil.example/staff",
				"/staff/x y", "/staff/<script>", "staff/pay")) {
			assertThatThrownBy(() -> StaffNotificationWriter.checkLink(link)).isInstanceOf(IllegalArgumentException.class);
		}
		assertThatThrownBy(() -> StaffNotificationWriter.checkLink("/staff/" + "a".repeat(300)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void aDedupeKeyIsOneToTwoHundredCharactersWhenGiven() {
		assertThatCode(() -> StaffNotificationWriter.checkDedupeKey(null)).doesNotThrowAnyException();
		assertThatCode(() -> StaffNotificationWriter.checkDedupeKey("PAYMENT_RECORDED:" + "x".repeat(50)))
			.doesNotThrowAnyException();
		assertThatThrownBy(() -> StaffNotificationWriter.checkDedupeKey(" ")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> StaffNotificationWriter.checkDedupeKey("k".repeat(201)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void aPayloadKeepsItsFlatValuesInOrder() {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("personName", "Rahim");
		payload.put("sheets", 10);
		payload.put("amount", 500.5);
		payload.put("final", true);
		payload.put("note", null);
		assertThat(StaffNotificationWriter.checkPayload(payload)).containsExactlyEntriesOf(payload);
		assertThat(StaffNotificationWriter.checkPayload(null)).isEmpty();
	}

	@Test
	void aKeyThatNamesAnAccountIsRefused() {
		for (String key : Arrays.asList("accountNumber", "bankName", "routingNumber", "walletNumber", "iban", "cardLast4",
				"Password")) {
			assertThatThrownBy(() -> StaffNotificationWriter.checkPayload(Map.of(key, "x")))
				.isInstanceOf(IllegalArgumentException.class);
		}
	}

	@Test
	void aTextWithALongNumberIsRefusedAndAnObjectIsRefused() {
		assertThatThrownBy(() -> StaffNotificationWriter.checkPayload(Map.of("note", "017123456789")))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> StaffNotificationWriter.checkPayload(Map.of("nested", Map.of("a", 1))))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> StaffNotificationWriter.checkPayload(Map.of("note", "x".repeat(301))))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void theKindListHoldsTheKindsOfEachPhase() {
		assertThat(Arrays.stream(StaffNotificationKind.values()).map(StaffNotificationKind::name).toList()).containsExactly(
				"PAYOUT_ACCOUNT_CHANGED",
				"MARKING_PUBLISHED_OWED", "MARKING_PUBLISHED_EARNED", "PAYMENT_RECORDED", "PAYMENT_VOIDED", "SALARY_DUE_SOON",
				"SALARY_OVERDUE", "MARKING_UNPAID_REMINDER",
				"EXAM_ASSIGNED", "EXAM_UNASSIGNED", "EXAM_RATE_CHANGED", "EXAM_PAY_TERMS_CHANGED");
	}

	@Test
	void fitTextMakesAnyTitleOrNameAcceptableToThePayloadCheck() {
		String longTitle = "ক".repeat(500);
		String numbered = "Model test 2026110512345 batch";
		for (String text : Arrays.asList(longTitle, numbered, "Muslim Law", "")) {
			String fitted = StaffNotificationWriter.fitText(text);
			assertThat(fitted.length()).isLessThanOrEqualTo(StaffNotificationWriter.MAX_TEXT);
			assertThatCode(() -> StaffNotificationWriter.checkPayload(Map.of("examTitleBn", fitted))).doesNotThrowAnyException();
		}
		assertThat(StaffNotificationWriter.fitText("Muslim Law")).isEqualTo("Muslim Law");
		assertThat(StaffNotificationWriter.fitText(numbered)).isEqualTo("Model test 2026… batch");
		assertThat(StaffNotificationWriter.fitText(longTitle)).endsWith("…").hasSize(StaffNotificationWriter.MAX_TEXT);
		assertThat(StaffNotificationWriter.fitText(null)).isNull();
	}

}
