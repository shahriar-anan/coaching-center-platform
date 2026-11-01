package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class SampleSeedGuardTest {

	@Test
	void productionProfileIsRefusedAndSecretsStayOutOfTheMessage() {
		assertThatThrownBy(() -> SampleSeed.refuseProduction(new String[] { "prod" }))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("The sample seed cannot run on the prod profile.");
		assertThatThrownBy(() -> SampleSeed.refuseProduction(new String[] { "staging", "prod" }))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("The sample seed cannot run on the prod profile.");
		assertThatThrownBy(() -> SampleSeed.refuseProduction(new String[] { "dev", "prod" }))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("The sample seed cannot run on the prod profile.");
		assertThatCode(() -> SampleSeed.refuseProduction(new String[] { "staging" })).doesNotThrowAnyException();
		assertThatCode(() -> SampleSeed.refuseProduction(new String[] { "dev" })).doesNotThrowAnyException();

		assertThatThrownBy(() -> SampleSeed.required("SEED_SAMPLE_PASSWORD", " "))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("SEED_SAMPLE_PASSWORD is required.");
		assertThatThrownBy(() -> SampleSeed.requirePassword("SEED_SAMPLE_PASSWORD", "short"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("SEED_SAMPLE_PASSWORD must be 8 to 72 characters.")
			.hasMessageNotContaining("short");
		assertThatCode(() -> SampleSeed.requirePassword("SEED_SAMPLE_PASSWORD", "twelve-chars")).doesNotThrowAnyException();
	}

	@Test
	void theSpecHasThePlansCountsAndStates() {
		assertThat(SampleSeed.CATEGORIES).hasSize(8);
		assertThat(SampleSeed.PACKAGES).hasSize(3);
		assertThat(SampleSeed.EXAMS).hasSize(20);
		assertThat(SampleSeed.EXAMS).filteredOn(exam -> exam.written).hasSize(6);
		assertThat(SampleSeed.EXAMS).filteredOn(exam -> !exam.written).hasSize(14);
		assertThat(SampleSeed.EXAMS).filteredOn(exam -> exam.draft).hasSize(1);
		assertThat(SampleSeed.MCQ_SITTINGS.size() + SampleSeed.WRITTEN_SITTINGS.size()).isEqualTo(22);
		assertThat(SampleSeed.ORDERS).hasSize(8);
		assertThat(SampleSeed.REVIEWS).hasSize(7);
		assertThat(SampleSeed.ANNOUNCEMENTS).hasSize(6);
		assertThat(SampleSeed.ACCOUNTS).hasSize(10);
		assertThat(SampleSeed.bankItems()).hasSize(12);
		for (SampleSeed.Exam exam : SampleSeed.EXAMS) {
			if (!exam.written) {
				assertThat(exam.questions).as("exam %d", exam.number).hasSize(SampleSeed.MCQ_QUESTIONS);
			}
		}
	}

	@Test
	void openExamsAreOpenNowAndDatedExamsSitAtElevenPm() {
		var schedule = new SampleSeed.Schedule(Instant.parse("2026-11-01T06:00:00Z"), LocalDate.of(2026, 11, 1));
		// Exam 3 is dated three days ahead: 23:00 Dhaka on 4 November 2026, 17:00 UTC.
		assertThat(SampleSeed.exam(3).liveAt(schedule)).isEqualTo(Instant.parse("2026-11-04T17:00:00Z"));
		assertThat(SampleSeed.exam(3).endsAt(schedule)).isEqualTo(Instant.parse("2026-11-04T17:20:00Z"));
		// Exam 9 is open: it started at 23:00 Dhaka on 31 October and runs to 23:20 Dhaka on 30 December.
		assertThat(SampleSeed.exam(9).liveAt(schedule)).isEqualTo(Instant.parse("2026-10-31T17:00:00Z"));
		assertThat(SampleSeed.exam(9).endsAt(schedule)).isEqualTo(Instant.parse("2026-12-30T17:20:00Z"));
		// The undated and practice exams have no window.
		assertThat(SampleSeed.exam(18).liveAt(schedule)).isNull();
		assertThat(SampleSeed.exam(19).liveAt(schedule)).isNull();
	}

}
