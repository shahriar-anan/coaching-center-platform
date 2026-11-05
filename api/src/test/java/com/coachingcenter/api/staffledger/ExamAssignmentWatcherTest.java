package com.coachingcenter.api.staffledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.coachingcenter.api.staffledger.ExamAssignmentWatcher.Change;

/** The comparison rule of the assignment watcher, without a database. */
class ExamAssignmentWatcherTest {

	private static final UUID ONE = UUID.randomUUID();

	private static final UUID TWO = UUID.randomUUID();

	@Test
	void theFirstRunBaselinesEachExamWithoutANotice() {
		assertThat(ExamAssignmentWatcher.changesFor(false, null, ONE, true))
			.containsExactly(new Change("ASSIGNED", ONE, null, false));
		assertThat(ExamAssignmentWatcher.changesFor(false, null, null, true))
			.containsExactly(new Change("UNASSIGNED", null, null, false));
	}

	@Test
	void anExamTheWatcherHasNotSeenBeforeIsNotifiedWhenAssignedLater() {
		assertThat(ExamAssignmentWatcher.changesFor(false, null, ONE, false))
			.containsExactly(new Change("ASSIGNED", ONE, null, true));
		assertThat(ExamAssignmentWatcher.changesFor(false, null, null, false)).isEmpty();
	}

	@Test
	void anAssignmentToAnUnassignedExamIsNotified() {
		assertThat(ExamAssignmentWatcher.changesFor(true, null, ONE, false))
			.containsExactly(new Change("ASSIGNED", ONE, null, true));
	}

	@Test
	void anUnassignmentNotifiesThePreviousInstructor() {
		assertThat(ExamAssignmentWatcher.changesFor(true, ONE, null, false))
			.containsExactly(new Change("UNASSIGNED", null, ONE, true));
	}

	@Test
	void aReassignmentIsAnUnassignmentThenAnAssignment() {
		assertThat(ExamAssignmentWatcher.changesFor(true, ONE, TWO, false)).containsExactly(
				new Change("UNASSIGNED", null, ONE, true),
				new Change("ASSIGNED", TWO, null, true));
	}

	@Test
	void noChangeWritesNothing() {
		assertThat(ExamAssignmentWatcher.changesFor(true, ONE, ONE, false)).isEmpty();
		assertThat(ExamAssignmentWatcher.changesFor(true, null, null, false)).isEmpty();
		assertThat(ExamAssignmentWatcher.changesFor(true, null, null, true)).isEmpty();
	}

}
