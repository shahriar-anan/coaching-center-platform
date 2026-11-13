package com.coachingcenter.api.offline;

import java.util.List;
import java.util.UUID;

/**
 * One class of a routine preview or confirmed routine (plan section 5). Dates and times are kept as text (yyyy-MM-dd, HH:mm) so the
 * stored JSON reads the same on every server. {@code instructorText} is the name as the PDF wrote it; {@code instructorId} is set once
 * the name is matched to an offline instructor (plan G4). {@code flags} never block saving; a missing date, time or subject, or an
 * unmatched name, does block the confirm.
 */
public record OfflineRoutineRow(int page, String date, String day, String time, String startsAt, String endsAt, String subject,
		String topic, String kind, String instructorText, UUID instructorId, List<Flag> flags) {

	/** One reason a row needs a look: a code the screens can key on, and the reason in English. */
	public record Flag(String code, String reason) {
	}

}
