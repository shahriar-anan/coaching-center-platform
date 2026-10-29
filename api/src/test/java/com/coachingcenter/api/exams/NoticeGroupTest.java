package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

/** Which tab each notice kind sits under (N1). Pure mapping, no database. */
class NoticeGroupTest {

	@Test
	void everyKindOfTheInboxLandsInExactlyOneTab() {
		assertThat(NoticeGroup.of("MCQ_SCORE")).isEqualTo(NoticeGroup.RESULTS);
		assertThat(NoticeGroup.of("WRITTEN_RESULT")).isEqualTo(NoticeGroup.RESULTS);
		assertThat(NoticeGroup.of("ANNOUNCEMENT")).isEqualTo(NoticeGroup.ANNOUNCEMENTS);
		for (String kind : new String[] { "EXAM_OPEN", "EXAM_PUBLISHED", "EXAM_REMINDER", "EXAM_STARTING_SOON",
				"RETAKE_AVAILABLE", "EXAM_UPDATED", "PURCHASE_GRANTED" }) {
			assertThat(NoticeGroup.of(kind)).as(kind).isEqualTo(NoticeGroup.EXAMS);
		}
	}

	@Test
	void aKindNotMappedYetFallsBackToExams() {
		assertThat(NoticeGroup.of("SOME_FUTURE_KIND")).isEqualTo(NoticeGroup.EXAMS);
	}

	@Test
	void theTabKindsCoverTheSameSplitAsTheMapping() {
		assertThat(NoticeGroup.RESULTS.kinds()).containsExactlyInAnyOrder("MCQ_SCORE", "WRITTEN_RESULT");
		assertThat(NoticeGroup.ANNOUNCEMENTS.kinds()).containsExactly("ANNOUNCEMENT");
		assertThat(NoticeGroup.EXAMS.kinds()).containsExactlyInAnyOrder("MCQ_SCORE", "WRITTEN_RESULT", "ANNOUNCEMENT");
	}

	@Test
	void theQueryValueIsReadWithoutCaseAndAbsentMeansAll() {
		assertThat(NoticeGroup.parse(null)).isNull();
		assertThat(NoticeGroup.parse("  ")).isNull();
		assertThat(NoticeGroup.parse("results")).isEqualTo(NoticeGroup.RESULTS);
		assertThat(NoticeGroup.parse("Announcements")).isEqualTo(NoticeGroup.ANNOUNCEMENTS);
	}

	@Test
	void anUnknownQueryValueIsAValidationError() {
		assertThatThrownBy(() -> NoticeGroup.parse("PAYMENTS"))
			.isInstanceOfSatisfying(ApiException.class,
					exception -> assertThat(exception.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
	}

}
