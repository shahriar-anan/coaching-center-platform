package com.coachingcenter.api.offline;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.coachingcenter.api.offline.OfflineRoutineReader.RawRow;

/**
 * Turns the rows the reader found into checked rows (plan section 5, steps 3 to 5): dates, days and times are read, a class is an EXAM or
 * a LECTURE, an instructor name is matched to the known instructors, and the flags are set. Flags are recomputed for the whole routine
 * because some depend on the row above (the order of dates) or on every other row (a repeated lecture).
 */
final class OfflineRoutineRules {

	static final String DATE_UNREADABLE = "DATE_UNREADABLE";

	/** The date is before the day the routine counts from: it still becomes a class, as an earlier class. */
	static final String DATE_PAST = "DATE_PAST";

	static final String DATE_OUT_OF_ORDER = "DATE_OUT_OF_ORDER";

	static final String WEEKDAY_MISMATCH = "WEEKDAY_MISMATCH";

	static final String TIME_UNREADABLE = "TIME_UNREADABLE";

	static final String NO_INSTRUCTOR = "NO_INSTRUCTOR";

	static final String INSTRUCTOR_UNKNOWN = "INSTRUCTOR_UNKNOWN";

	static final String EXAM_ROW = "EXAM_ROW";

	static final String DUPLICATE_LECTURE = "DUPLICATE_LECTURE";

	private static final Pattern DATE = Pattern.compile("(\\d{1,2})[-/](\\d{1,2})[-/](\\d{4})");

	private static final Pattern TIME = Pattern.compile("(\\d{1,2})[.:](\\d{2})(AM|PM)?(?:-(\\d{1,2})[.:](\\d{2})(AM|PM)?)?",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern LECTURE = Pattern.compile("lecture-?(\\d+)", Pattern.CASE_INSENSITIVE);

	/** A class's start and end, Dhaka clock. */
	record Times(LocalTime start, LocalTime end) {
	}

	private OfflineRoutineRules() {
	}

	/** Every name the known instructors answer to (their name and aliases), trimmed and lower-cased, to the instructor. */
	static Map<String, UUID> index(List<OfflineInstructor> instructors) {
		Map<String, UUID> index = new HashMap<>();
		for (OfflineInstructor instructor : instructors) {
			index.putIfAbsent(key(instructor.getName()), instructor.getId());
			if (instructor.getAliases() != null) {
				for (String alias : instructor.getAliases()) {
					index.putIfAbsent(key(alias), instructor.getId());
				}
			}
		}
		return index;
	}

	/** A name for matching: trimmed, inner spaces collapsed, case ignored. */
	static String key(String name) {
		return name == null ? "" : name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	/** The rows the reader found, checked, with flags. {@code from} is the day the routine counts from (the day it is uploaded). */
	static List<OfflineRoutineRow> build(List<RawRow> raw, LocalDate from, int defaultMinutes, Map<String, UUID> instructors) {
		List<OfflineRoutineRow> rows = new ArrayList<>();
		for (RawRow row : raw) {
			LocalDate date = date(row.date());
			String day = row.day() == null ? "" : row.day().trim();
			if (day.isEmpty() && date != null) {
				day = weekday(date);
			}
			Times times = times(row.time(), defaultMinutes);
			String subject = clean(row.subject());
			String topic = noDash(clean(row.topic()));
			String instructor = noDash(clean(row.instructor()));
			String kind = isExam(subject, topic, instructor) ? OfflineClass.KIND_EXAM : OfflineClass.KIND_LECTURE;
			if (OfflineClass.KIND_EXAM.equals(kind) && !instructor.isEmpty() && !nameLike(instructor)) {
				topic = (instructor + " " + topic).trim();
				instructor = "";
			}
			UUID instructorId = instructor.isEmpty() ? null : instructors.get(key(instructor));
			rows.add(new OfflineRoutineRow(row.page(), date == null ? null : date.toString(), day, row.time() == null ? "" : row.time(),
					times == null ? null : times.start().toString(), times == null ? null : times.end().toString(), subject, topic, kind,
					instructor, instructorId, List.of()));
		}
		return flag(rows, from);
	}

	/**
	 * The same rows with their flags set again, for a routine that has been edited or matched. A date before {@code from}, the day the
	 * routine counts from, is flagged as past.
	 */
	static List<OfflineRoutineRow> flag(List<OfflineRoutineRow> rows, LocalDate from) {
		Map<String, List<Integer>> lectures = new LinkedHashMap<>();
		for (int index = 0; index < rows.size(); index++) {
			OfflineRoutineRow row = rows.get(index);
			LocalDate date = row.date() == null ? null : LocalDate.parse(row.date());
			if (date != null && OfflineClass.KIND_LECTURE.equals(row.kind())) {
				Matcher lecture = LECTURE.matcher(row.topic() == null ? "" : row.topic());
				if (lecture.find()) {
					String key = YearMonth.from(date) + "|" + key(row.subject()) + "|" + Integer.parseInt(lecture.group(1));
					lectures.computeIfAbsent(key, ignored -> new ArrayList<>()).add(index);
				}
			}
		}
		List<List<OfflineRoutineRow.Flag>> flags = new ArrayList<>();
		LocalDate previous = null;
		for (OfflineRoutineRow row : rows) {
			List<OfflineRoutineRow.Flag> found = new ArrayList<>();
			LocalDate date = row.date() == null ? null : LocalDate.parse(row.date());
			if (date == null) {
				found.add(flag(DATE_UNREADABLE, "The date could not be read."));
			}
			else {
				if (from != null && date.isBefore(from)) {
					found.add(flag(DATE_PAST, "This date has passed: it is added as an earlier class."));
				}
				if (previous != null && date.isBefore(previous)) {
					found.add(flag(DATE_OUT_OF_ORDER, "Date is earlier than the row above."));
				}
				if (!sameDay(row.day(), date)) {
					found.add(flag(WEEKDAY_MISMATCH, "The day says " + row.day() + ", but this date is a " + weekday(date) + "."));
				}
				previous = date;
			}
			if (row.startsAt() == null) {
				found.add(flag(TIME_UNREADABLE, "The time could not be read."));
			}
			boolean named = row.instructorText() != null && !row.instructorText().isEmpty();
			if (!named && row.instructorId() == null) {
				found.add(flag(NO_INSTRUCTOR, "No instructor yet."));
			}
			if (named && row.instructorId() == null) {
				found.add(flag(INSTRUCTOR_UNKNOWN, "New instructor: " + row.instructorText() + " is added to the instructors when the routine is confirmed."));
			}
			if (OfflineClass.KIND_EXAM.equals(row.kind())) {
				found.add(flag(EXAM_ROW, "Exam row: set who is paid."));
			}
			flags.add(found);
		}
		for (List<Integer> repeated : lectures.values()) {
			if (repeated.size() < 2) {
				continue;
			}
			for (int index : repeated) {
				OfflineRoutineRow row = rows.get(index);
				Matcher lecture = LECTURE.matcher(row.topic());
				lecture.find();
				flags.get(index).add(flag(DUPLICATE_LECTURE, "Lecture " + Integer.parseInt(lecture.group(1)) + " of " + row.subject()
						+ " appears more than once this month."));
			}
		}
		List<OfflineRoutineRow> checked = new ArrayList<>();
		for (int index = 0; index < rows.size(); index++) {
			OfflineRoutineRow row = rows.get(index);
			checked.add(new OfflineRoutineRow(row.page(), row.date(), row.day(), row.time(), row.startsAt(), row.endsAt(), row.subject(),
					row.topic(), row.kind(), row.instructorText(), row.instructorId(), List.copyOf(flags.get(index))));
		}
		return checked;
	}

	/** A date written as day-month-year (with a dash or a slash), or null when it is blank or not a real date. */
	static LocalDate date(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		Matcher matcher = DATE.matcher(text.replaceAll("\\s", ""));
		if (!matcher.matches()) {
			return null;
		}
		try {
			return LocalDate.of(Integer.parseInt(matcher.group(3)), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(1)));
		}
		catch (DateTimeException exception) {
			return null;
		}
	}

	/**
	 * A time: "5.10 PM" is a start with the default length; "9.00AM-10.20AM" is a start and an end, and "9.00-10.20AM" takes the
	 * missing meridiem from the end. Null when it cannot be read, or when the end is not after the start, or when the class runs past
	 * midnight.
	 */
	static Times times(String text, int defaultMinutes) {
		if (text == null || text.isBlank()) {
			return null;
		}
		Matcher matcher = TIME.matcher(text.replaceAll("\\s", ""));
		if (!matcher.matches()) {
			return null;
		}
		String startMeridiem = matcher.group(3);
		String endMeridiem = matcher.group(6);
		if (startMeridiem == null) {
			startMeridiem = endMeridiem;
		}
		LocalTime start = clock(matcher.group(1), matcher.group(2), startMeridiem);
		if (start == null) {
			return null;
		}
		if (matcher.group(4) != null) {
			LocalTime end = clock(matcher.group(4), matcher.group(5), endMeridiem == null ? startMeridiem : endMeridiem);
			return end == null || !end.isAfter(start) ? null : new Times(start, end);
		}
		if (start.plusMinutes(defaultMinutes).isBefore(start)) {
			return null;
		}
		return new Times(start, start.plusMinutes(defaultMinutes));
	}

	private static LocalTime clock(String hour, String minute, String meridiem) {
		int h = Integer.parseInt(hour);
		int m = Integer.parseInt(minute);
		if (m > 59 || meridiem == null || h < 1 || h > 12) {
			return null;
		}
		boolean pm = "PM".equalsIgnoreCase(meridiem);
		if (pm && h < 12) {
			h += 12;
		}
		if (!pm && h == 12) {
			h = 0;
		}
		return LocalTime.of(h, m);
	}

	static String weekday(LocalDate date) {
		return date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
	}

	/** A day the PDF wrote matches the date when it is the weekday's name, or its first three letters or more, ignoring case. */
	private static boolean sameDay(String said, LocalDate date) {
		if (said == null || said.isBlank()) {
			return true;
		}
		String full = weekday(date).toLowerCase(Locale.ROOT);
		String text = said.replaceAll("\\s", "").toLowerCase(Locale.ROOT);
		return full.equals(text) || text.length() >= 3 && full.startsWith(text);
	}

	private static boolean isExam(String... texts) {
		for (String text : texts) {
			if (text != null && text.toLowerCase(Locale.ROOT).contains("exam")) {
				return true;
			}
		}
		return false;
	}

	/** A name, not a phrase: no digits or brackets, and at most four words. Exam rows use it to tell a name from a topic. */
	static boolean nameLike(String text) {
		return !text.matches(".*[0-9()].*") && text.trim().split("\\s+").length <= 4;
	}

	private static String clean(String text) {
		return text == null ? "" : text.replaceAll("\\s+", " ").trim();
	}

	/** A dash on its own is a blank cell (the routine writes "-" for no instructor or no topic). */
	private static String noDash(String text) {
		return "-".equals(text) || "–".equals(text) || "—".equals(text) ? "" : text;
	}

	private static OfflineRoutineRow.Flag flag(String code, String reason) {
		return new OfflineRoutineRow.Flag(code, reason);
	}

}
