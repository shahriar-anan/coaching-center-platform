package com.coachingcenter.api.offline;

import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.coachingcenter.api.staffnotifications.StaffNotificationKind;
import com.coachingcenter.api.staffnotifications.StaffNotificationWriter;

/**
 * The staff notifications of the offline classes (plan section 7). Each notice is one row per person: the daily digest and the weekly
 * overdue reminder are keyed by the person and the day or the ISO week, and a decision notice is keyed by its batch. The writer checks
 * the recipient is an active staff member, so a suspended person gets nothing. Called inside the caller's transaction.
 */
@Component
class OfflineClassNotices {

	static final String LINK = "/staff/offline-instructor-payments";

	private final StaffNotificationWriter staff;

	OfflineClassNotices(StaffNotificationWriter staff) {
		this.staff = staff;
	}

	/**
	 * The daily digest for one person. {@code waiting} is the number of entries that wait for approval; it is given only to the Master
	 * Admin, and only when there are any. Returns 1 when a row was written.
	 */
	int digest(UUID recipient, LocalDate today, long count, Long waiting) {
		Map<String, Object> payload = waiting == null ? Map.of("count", count) : Map.of("count", count, "waiting", waiting);
		return staff.notify(recipient, StaffNotificationKind.OFFLINE_CLASSES_TO_SETTLE, payload, LINK,
				"OFFLINE_CLASSES_TO_SETTLE:" + today + ":" + recipient) ? 1 : 0;
	}

	/** The weekly reminder for one person: classes held for more than a week. Once per ISO week. Returns 1 when a row was written. */
	int overdue(UUID recipient, LocalDate today, long count) {
		String week = today.get(IsoFields.WEEK_BASED_YEAR) + "-W" + today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
		return staff.notify(recipient, StaffNotificationKind.OFFLINE_CLASSES_OVERDUE, Map.of("count", count), LINK,
				"OFFLINE_CLASSES_OVERDUE:" + week + ":" + recipient) ? 1 : 0;
	}

	/** One decision notice to the person who recorded the entries: how many were approved and rejected in one batch. */
	void decided(UUID recorder, int approved, int rejected) {
		decidedBatch(Map.of(recorder, new int[] { approved, rejected }));
	}

	/**
	 * One decision notice per recorder for one batch of decisions. {@code tally} holds, for each recorder, {approved, rejected}. The batch
	 * has its own id in the key, so a later batch of the same recorder is a new notice.
	 */
	void decidedBatch(Map<UUID, int[]> tally) {
		if (tally.isEmpty()) {
			return;
		}
		String batch = UUID.randomUUID().toString();
		for (Map.Entry<UUID, int[]> entry : tally.entrySet()) {
			int[] counts = entry.getValue();
			staff.notify(entry.getKey(), StaffNotificationKind.OFFLINE_ENTRIES_DECIDED,
					Map.of("approved", counts[0], "rejected", counts[1]), LINK,
					"OFFLINE_ENTRIES_DECIDED:" + batch + ":" + entry.getKey());
		}
	}

}
