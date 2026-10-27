package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DemoContentSeedGuardTest {

	@Test
	void productionProfileIsRefusedAndSecretsStayOutOfTheMessage() {
		assertThatThrownBy(() -> DemoContentSeed.refuseProduction(new String[] { "prod" }))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("The demo seed cannot run on the prod profile.");
		assertThatThrownBy(() -> DemoContentSeed.refuseProduction(new String[] { "staging", "prod" }))
			.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> DemoContentSeed.refuseProduction(new String[] { "dev", "prod" }))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("The demo seed cannot run on the prod profile.");
		assertThatCode(() -> DemoContentSeed.refuseProduction(new String[] { "staging" })).doesNotThrowAnyException();
		assertThatCode(() -> DemoContentSeed.refuseProduction(new String[] { "dev" })).doesNotThrowAnyException();

		assertThatThrownBy(() -> DemoContentSeed.required("SEED_DEMO_PASSWORD", " "))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("SEED_DEMO_PASSWORD is required.");
		assertThatThrownBy(() -> DemoContentSeed.requirePassword("SEED_DEMO_PASSWORD", "short"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("SEED_DEMO_PASSWORD must be 8 to 72 characters.")
			.hasMessageNotContaining("short");
		assertThatCode(() -> DemoContentSeed.requirePassword("SEED_DEMO_PASSWORD", "twelve-chars")).doesNotThrowAnyException();
	}

	@Test
	void everyExamIsTwentyMinutesAndTheDailyPapersAreAtElevenPm() {
		DemoContentSeed.Schedule schedule = DemoContentSeed.Schedule.at(java.time.Instant.parse("2026-10-27T10:00:00Z"));
		var exams = new DemoContentSeed(null, null, null, null).exams(schedule);

		assertThat(DemoContentSeed.EXAM_MINUTES).isEqualTo(20);
		assertThat(exams).filteredOn(exam -> exam.number() >= 20 && exam.number() <= 26).hasSize(DemoContentSeed.DAILY_EXAMS);
		for (var exam : exams) {
			if (exam.number() >= 20 && exam.number() <= 26) {
				int day = exam.number() - 19;
				var local = exam.liveAt().atZone(DemoContentSeed.DHAKA);
				assertThat(local.toLocalDate()).isEqualTo(java.time.LocalDate.of(2026, 10, 27).plusDays(day));
				assertThat(local.getHour()).isEqualTo(23);
				assertThat(local.getMinute()).isZero();
				assertThat(exam.type()).isEqualTo(day % 2 == 1 ? LiveExam.MCQ : LiveExam.WRITTEN);
			}
			if (LiveExam.MCQ.equals(exam.type())) {
				assertThat(exam.questions()).hasSize(10);
			}
			else {
				assertThat(exam.questionPdf()).isTrue();
			}
		}
	}

}
