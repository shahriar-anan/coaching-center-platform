package com.coachingcenter.api.offline;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.coachingcenter.api.offline.OfflineRoutineMatcher.Action;
import com.coachingcenter.api.offline.OfflineRoutineMatcher.Plan;

/**
 * Rule A1 (plan section 1, results-api-contract): a routine upload never cancels or moves an exam class that has a result sheet. The routine
 * can still list the exam; it is kept and shown as settled.
 */
class OfflineRoutineMatcherTest {

	private static final UUID BATCH = UUID.randomUUID();

	private static final UUID ROUTINE = UUID.randomUUID();

	private static final LocalDate FROM = LocalDate.parse("2026-11-01");

	@Test
	void anExamWithAResultIsKeptWhenTheRoutineNoLongerListsIt() {
		OfflineClass exam = exam(LocalDate.parse("2026-11-10"), "10:00", "Constitution");
		List<OfflineClass> classes = List.of(exam);

		Plan withoutResult = OfflineRoutineMatcher.plan(List.of(), classes, FROM, Set.of());
		assertThat(withoutResult.cancel()).containsExactly(exam);

		Plan withResult = OfflineRoutineMatcher.plan(List.of(), classes, FROM, Set.of(exam.getId()));
		assertThat(withResult.cancel()).isEmpty();
		assertThat(withResult.settledKept()).containsExactly(exam);
	}

	@Test
	void anExamWithAResultIsNotMovedByARowThatMatchesItsDateAndSubject() {
		OfflineClass exam = exam(LocalDate.parse("2026-11-10"), "10:00", "Constitution");
		OfflineRoutineRow movedTime = row("2026-11-10", "11:00", "Constitution");

		Plan plan = OfflineRoutineMatcher.plan(List.of(movedTime), List.of(exam), FROM, Set.of(exam.getId()));

		assertThat(plan.rows()).singleElement().satisfies(row -> {
			assertThat(row.action()).isEqualTo(Action.SETTLED);
			assertThat(row.existing()).isSameAs(exam);
			assertThat(row.changed()).isFalse();
		});
		assertThat(plan.cancel()).isEmpty();
	}

	@Test
	void anExamWithoutAResultIsStillMovedAsBefore() {
		OfflineClass exam = exam(LocalDate.parse("2026-11-10"), "10:00", "Constitution");
		OfflineRoutineRow movedTime = row("2026-11-10", "11:00", "Constitution");

		Plan plan = OfflineRoutineMatcher.plan(List.of(movedTime), List.of(exam), FROM, Set.of());

		assertThat(plan.rows()).singleElement().satisfies(row -> {
			assertThat(row.action()).isEqualTo(Action.UPDATE);
			assertThat(row.changed()).isTrue();
		});
	}

	private static OfflineClass exam(LocalDate heldOn, String startsAt, String subject) {
		OfflineClass classItem = new OfflineClass();
		classItem.setId(UUID.randomUUID());
		classItem.setBatchId(BATCH);
		classItem.setRoutineId(ROUTINE);
		classItem.setHeldOn(heldOn);
		classItem.setStartsAt(LocalTime.parse(startsAt));
		classItem.setEndsAt(LocalTime.parse(startsAt).plusHours(2));
		classItem.setSubject(subject);
		classItem.setKind(OfflineClass.KIND_EXAM);
		classItem.setStatus(OfflineClass.STATUS_SCHEDULED);
		return classItem;
	}

	private static OfflineRoutineRow row(String date, String startsAt, String subject) {
		return new OfflineRoutineRow(1, date, "Tue", startsAt, startsAt, LocalTime.parse(startsAt).plusHours(2).toString(), subject, null,
				OfflineClass.KIND_EXAM, null, null, List.of());
	}

}
