package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class AccessWindowTest {

	private static final Instant LIVE_AT = Instant.parse("2026-10-29T04:00:00Z");

	private static final Instant ENDS_AT = LIVE_AT.plus(Duration.ofMinutes(60));

	private static final Instant PUBLISHED = Instant.parse("2026-10-20T00:00:00Z");

	private static final Instant FAR_FUTURE = Instant.parse("2040-01-01T00:00:00Z");

	@Test
	void lifetimeAccessNeverEnds() {
		LiveExam exam = live(null);

		assertThat(AccessWindow.end(exam, LIVE_AT.minus(Duration.ofDays(1)))).isNull();
		assertThat(AccessWindow.expired(exam, LIVE_AT, FAR_FUTURE)).isFalse();
	}

	@Test
	void aFreeExamNeverExpires() {
		LiveExam exam = live(30);
		exam.setFree(true);

		assertThat(AccessWindow.end(exam, LIVE_AT)).isNull();
		assertThat(AccessWindow.expired(exam, LIVE_AT, FAR_FUTURE)).isFalse();
	}

	@Test
	void aGrantBeforeTheWindowEndCountsDaysFromTheWindowEnd() {
		LiveExam exam = live(30);
		Instant grantedBeforeEnd = LIVE_AT.minus(Duration.ofDays(3));

		assertThat(AccessWindow.end(exam, grantedBeforeEnd)).isEqualTo(ENDS_AT.plus(Duration.ofDays(30)));
	}

	@Test
	void aGrantAfterTheWindowEndCountsDaysFromTheGrant() {
		LiveExam exam = live(30);
		Instant grantedLater = ENDS_AT.plus(Duration.ofDays(2));

		assertThat(AccessWindow.end(exam, grantedLater)).isEqualTo(grantedLater.plus(Duration.ofDays(30)));
	}

	@Test
	void theEndIsExclusiveSoAccessIsOverAtTheInstantItIsReached() {
		LiveExam exam = live(30);
		Instant end = ENDS_AT.plus(Duration.ofDays(30));

		assertThat(AccessWindow.expired(exam, LIVE_AT, end.minusMillis(1))).isFalse();
		assertThat(AccessWindow.expired(exam, LIVE_AT, end)).isTrue();
	}

	@Test
	void anUndatedLiveExamHasNotStartedCountingSoItNeverExpires() {
		LiveExam exam = live(30);
		exam.setLiveAt(null);
		exam.setEndsAt(null);

		assertThat(AccessWindow.end(exam, LIVE_AT)).isNull();
		assertThat(AccessWindow.expired(exam, LIVE_AT, FAR_FUTURE)).isFalse();
	}

	@Test
	void aPracticeExamCountsFromItsPublishTimeOrTheGrantIfLater() {
		LiveExam exam = practice(30);
		exam.setPublishedAt(PUBLISHED);

		assertThat(AccessWindow.end(exam, PUBLISHED.minus(Duration.ofDays(1)))).isEqualTo(PUBLISHED.plus(Duration.ofDays(30)));
		assertThat(AccessWindow.end(exam, PUBLISHED.plus(Duration.ofDays(5))))
			.isEqualTo(PUBLISHED.plus(Duration.ofDays(35)));
	}

	@Test
	void anUnpublishedPracticeExamHasNoEndYet() {
		assertThat(AccessWindow.end(practice(30), PUBLISHED)).isNull();
	}

	private static LiveExam live(Integer accessDays) {
		LiveExam exam = new LiveExam();
		exam.setMode(LiveExam.MODE_LIVE);
		exam.setLiveAt(LIVE_AT);
		exam.setEndsAt(ENDS_AT);
		exam.setDurationMinutes(60);
		exam.setAccessDays(accessDays);
		return exam;
	}

	private static LiveExam practice(Integer accessDays) {
		LiveExam exam = new LiveExam();
		exam.setMode(LiveExam.MODE_PRACTICE);
		exam.setDurationMinutes(60);
		exam.setAccessDays(accessDays);
		return exam;
	}

}
