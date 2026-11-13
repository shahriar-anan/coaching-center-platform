package com.coachingcenter.api.offline;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Matches the rows of a routine to the classes the batch already has (plan section 4.4). A row matches a class with the same date, start
 * time and subject; when none matches, the same date and subject match, which is a class whose time moved. A class is matched once.
 *
 * <p>The matching looks at every class of the batch, so a class that two routines both list is one class, not two. Only the classes that
 * came from a routine of the same month can be cancelled by a row that is no longer there. A settled class (paid, or not taken) is
 * never changed: a row that matches one leaves it alone.
 */
final class OfflineRoutineMatcher {

	enum Action {
		/** A new class is added. */
		ADD,
		/** An unsettled class takes the row's values. */
		UPDATE,
		/** A settled class matches the row and is left as it is. */
		SETTLED
	}

	/** What a row does; {@code existing} is the matched class (null for ADD), {@code changed} says whether an UPDATE changes anything. */
	record RowPlan(int index, Action action, OfflineClass existing, boolean changed) {
	}

	/**
	 * The whole plan: each row's action, the unsettled classes of the month that no row matches (to cancel), and the settled classes of
	 * the month that no row matches (kept, shown in the difference).
	 */
	record Plan(List<RowPlan> rows, List<OfflineClass> cancel, List<OfflineClass> settledKept) {
	}

	private OfflineRoutineMatcher() {
	}

	static Plan plan(List<OfflineRoutineRow> rows, List<OfflineClass> batchClasses, Set<UUID> monthRoutines) {
		List<OfflineClass> candidates = new ArrayList<>();
		for (OfflineClass classItem : batchClasses) {
			if (!OfflineClass.STATUS_CANCELLED.equals(classItem.getStatus())) {
				candidates.add(classItem);
			}
		}
		Set<UUID> used = new HashSet<>();
		OfflineClass[] matched = new OfflineClass[rows.size()];
		for (int index = 0; index < rows.size(); index++) {
			OfflineRoutineRow row = rows.get(index);
			if (row.date() == null || row.startsAt() == null) {
				continue;
			}
			LocalDate date = LocalDate.parse(row.date());
			LocalTime start = LocalTime.parse(row.startsAt());
			matched[index] = take(candidates, used, classItem -> date.equals(classItem.getHeldOn())
					&& start.equals(classItem.getStartsAt()) && sameSubject(row.subject(), classItem.getSubject()));
		}
		for (int index = 0; index < rows.size(); index++) {
			OfflineRoutineRow row = rows.get(index);
			if (matched[index] != null || row.date() == null) {
				continue;
			}
			LocalDate date = LocalDate.parse(row.date());
			matched[index] = take(candidates, used, classItem -> date.equals(classItem.getHeldOn())
					&& sameSubject(row.subject(), classItem.getSubject()));
		}
		List<RowPlan> plans = new ArrayList<>();
		for (int index = 0; index < rows.size(); index++) {
			OfflineClass existing = matched[index];
			if (existing == null) {
				plans.add(new RowPlan(index, Action.ADD, null, true));
			}
			else if (OfflineClass.STATUS_SCHEDULED.equals(existing.getStatus())) {
				plans.add(new RowPlan(index, Action.UPDATE, existing, differs(existing, rows.get(index))));
			}
			else {
				plans.add(new RowPlan(index, Action.SETTLED, existing, false));
			}
		}
		List<OfflineClass> cancel = new ArrayList<>();
		List<OfflineClass> settledKept = new ArrayList<>();
		for (OfflineClass classItem : candidates) {
			if (used.contains(classItem.getId()) || classItem.getRoutineId() == null || !monthRoutines.contains(classItem.getRoutineId())) {
				continue;
			}
			if (OfflineClass.STATUS_SCHEDULED.equals(classItem.getStatus())) {
				cancel.add(classItem);
			}
			else {
				settledKept.add(classItem);
			}
		}
		return new Plan(plans, cancel, settledKept);
	}

	/** The first candidate not yet used that the test accepts, marked as used. */
	private static OfflineClass take(List<OfflineClass> candidates, Set<UUID> used, Predicate<OfflineClass> test) {
		for (OfflineClass candidate : candidates) {
			if (!used.contains(candidate.getId()) && test.test(candidate)) {
				used.add(candidate.getId());
				return candidate;
			}
		}
		return null;
	}

	/** Whether a row would change the class: the date, times, subject, topic, kind or instructor. */
	static boolean differs(OfflineClass classItem, OfflineRoutineRow row) {
		return !Objects.equals(classItem.getHeldOn(), row.date() == null ? null : LocalDate.parse(row.date()))
				|| !Objects.equals(classItem.getStartsAt(), row.startsAt() == null ? null : LocalTime.parse(row.startsAt()))
				|| !Objects.equals(classItem.getEndsAt(), row.endsAt() == null ? null : LocalTime.parse(row.endsAt()))
				|| !sameSubject(row.subject(), classItem.getSubject())
				|| !Objects.equals(blankToNull(classItem.getTopic()), blankToNull(row.topic()))
				|| !Objects.equals(classItem.getKind(), row.kind())
				|| !Objects.equals(classItem.getInstructorId(), row.instructorId());
	}

	private static boolean sameSubject(String a, String b) {
		return OfflineRoutineRules.key(a).equals(OfflineRoutineRules.key(b));
	}

	private static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text.trim();
	}

}
