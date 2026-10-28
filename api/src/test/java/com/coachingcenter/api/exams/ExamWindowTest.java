package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

class ExamWindowTest {

	private static final Instant LIVE_AT = Instant.parse("2026-10-29T04:00:00Z");

	private static final Instant ENDS_AT = LIVE_AT.plus(Duration.ofMinutes(60));

	@Test
	void theLiveWindowRunsFromLiveAtInclusiveToEndsAtExclusive() {
		LiveExam exam = window(LIVE_AT, ENDS_AT, 40);

		assertThat(ExamWindow.phase(exam, LIVE_AT.minusSeconds(1))).isEqualTo(ExamWindow.UPCOMING);
		assertThat(ExamWindow.phase(exam, LIVE_AT)).isEqualTo(ExamWindow.LIVE);
		assertThat(ExamWindow.phase(exam, ENDS_AT.minusSeconds(1))).isEqualTo(ExamWindow.LIVE);
		assertThat(ExamWindow.phase(exam, ENDS_AT)).isEqualTo(ExamWindow.ENDED);
	}

	@Test
	void anUndatedLiveExamIsUnannouncedAndAPracticeExamHasNoWindow() {
		assertThat(ExamWindow.phase(window(null, null, 40), LIVE_AT)).isEqualTo(ExamWindow.UNANNOUNCED);
		assertThat(ExamWindow.phase(practice(), LIVE_AT)).isEqualTo(ExamWindow.PRACTICE);
	}

	@Test
	void aStartBeforeTheWindowEndIsLiveAndAtTheEndIsArchived() {
		LiveExam exam = window(LIVE_AT, ENDS_AT, 40);

		assertThat(ExamWindow.firstAttemptKind(exam, ENDS_AT.minusSeconds(1))).isEqualTo(LiveExamParticipant.LIVE);
		assertThat(ExamWindow.firstAttemptKind(exam, ENDS_AT)).isEqualTo(LiveExamParticipant.ARCHIVED);
	}

	@Test
	void aPracticeAttemptIsAlwaysPractice() {
		assertThat(ExamWindow.firstAttemptKind(practice(), LIVE_AT)).isEqualTo(LiveExamParticipant.PRACTICE);
	}

	@Test
	void theDeadlineIsTheStartPlusTheDurationEvenPastTheWindowEnd() {
		LiveExam exam = window(LIVE_AT, ENDS_AT, 40);

		assertThat(ExamWindow.deadline(exam, LIVE_AT)).isEqualTo(LIVE_AT.plus(Duration.ofMinutes(40)));
		assertThat(ExamWindow.deadline(exam, ENDS_AT.minusSeconds(1)))
			.isEqualTo(ENDS_AT.minusSeconds(1).plus(Duration.ofMinutes(40)))
			.isAfter(ENDS_AT);
		assertThat(ExamWindow.lastLiveDeadline(exam)).isEqualTo(ENDS_AT.plus(Duration.ofMinutes(40)));
		assertThat(ExamWindow.lastLiveDeadline(window(null, null, 40))).isNull();
	}

	@Test
	void aFirstAttemptCanStartFromLiveAtAndAfterTheWindowAndPracticeOnceItIsPublished() {
		LiveExam exam = window(LIVE_AT, ENDS_AT, 40);
		assertThat(ExamWindow.canStartFirstAttempt(exam, LIVE_AT.minusSeconds(1))).isFalse();
		assertThat(ExamWindow.canStartFirstAttempt(exam, LIVE_AT)).isTrue();
		assertThat(ExamWindow.canStartFirstAttempt(exam, ENDS_AT.plusSeconds(3600))).isTrue();

		LiveExam unpublished = practice();
		assertThat(ExamWindow.canStartFirstAttempt(unpublished, LIVE_AT)).isFalse();
		unpublished.setPublishedAt(LIVE_AT);
		assertThat(ExamWindow.canStartFirstAttempt(unpublished, LIVE_AT)).isTrue();
	}

	@Test
	void validateRefusesAWindowThatCannotHold() {
		assertInvalid(() -> ExamWindow.validate(LiveExam.MODE_PRACTICE, LIVE_AT, null, 40));
		assertInvalid(() -> ExamWindow.validate(LiveExam.MODE_PRACTICE, null, ENDS_AT, 40));
		assertInvalid(() -> ExamWindow.validate(LiveExam.MODE_LIVE, LIVE_AT, null, 40));
		assertInvalid(() -> ExamWindow.validate(LiveExam.MODE_LIVE, null, ENDS_AT, 40));
		assertInvalid(() -> ExamWindow.validate(LiveExam.MODE_LIVE, LIVE_AT, LIVE_AT.plusSeconds(39 * 60), 40));
	}

	@Test
	void validateAcceptsAWindowExactlyTheDurationAndAnUndatedLiveOrPracticeExam() {
		assertThatCode(() -> ExamWindow.validate(LiveExam.MODE_LIVE, LIVE_AT, LIVE_AT.plusSeconds(40 * 60), 40))
			.doesNotThrowAnyException();
		assertThatCode(() -> ExamWindow.validate(LiveExam.MODE_LIVE, null, null, 40)).doesNotThrowAnyException();
		assertThatCode(() -> ExamWindow.validate(LiveExam.MODE_PRACTICE, null, null, 40)).doesNotThrowAnyException();
	}

	private static void assertInvalid(Runnable action) {
		assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,
				exception -> assertThat(exception.code()).isEqualTo(ErrorCode.EXAM_WINDOW_INVALID));
	}

	private static LiveExam window(Instant liveAt, Instant endsAt, int durationMinutes) {
		LiveExam exam = new LiveExam();
		exam.setMode(LiveExam.MODE_LIVE);
		exam.setLiveAt(liveAt);
		exam.setEndsAt(endsAt);
		exam.setDurationMinutes(durationMinutes);
		return exam;
	}

	private static LiveExam practice() {
		LiveExam exam = new LiveExam();
		exam.setMode(LiveExam.MODE_PRACTICE);
		exam.setDurationMinutes(30);
		return exam;
	}

}
