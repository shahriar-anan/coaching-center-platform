package com.coachingcenter.api.offline;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.Set;

import com.coachingcenter.api.offline.OfflineResultReader.Line;

/**
 * Reads a result sheet's rows and calculates their marks (plan section 2 and 5; rules R1, R2, A5, A8). The marks are calculated from the
 * raw columns: MCQ = right - wrong x negative; MCQ + written = MCQ + written; written = written. The file's own Total and Position are only
 * kept for the mismatch check. Rank is competition ranking (1, 2, 2, 4) over the totals; a row with right, wrong and written all 0 is a
 * "maybe absent" row and ranks like any 0 (R2). Blocking problems are collected, not thrown, so the preview can show them all. Each problem
 * has a code and its parameters, so the web can show the message in its own language; {@code message} stays in English.
 */
final class OfflineResultFile {

	static final String MCQ = "MCQ";

	static final String MCQ_WRITTEN = "MCQ_WRITTEN";

	static final String WRITTEN = "WRITTEN";

	/** The most student rows a sheet may hold (plan section 2). */
	static final int MAX_ROWS = 2000;

	/** The largest right, wrong or position count read from a sheet. Anything above it is not a count of questions. */
	static final long MAX_COUNT = 100_000L;

	/** Totals that differ by more than this from the file's own total are a mismatch. */
	static final BigDecimal TOLERANCE = new BigDecimal("0.01");

	/** The columns the sheet is read by. */
	enum Field {
		ROLL, FULL, RIGHT, WRONG, NEGATIVE, WRITTEN, TOTAL, POSITION
	}

	/**
	 * A blocking problem: the row it is on (null for the whole file), its roll (may be null), its code and parameters for translation, and the
	 * English message.
	 */
	record Problem(Integer rowNumber, String roll, String code, Map<String, Object> params, String message) {
	}

	/** A reason a row's total is not counted: a code, its parameters, and the English message. Becomes a {@link Problem} on the row. */
	private record Reason(String code, Map<String, Object> params, String message) {
	}

	/**
	 * One student row of the sheet. {@code mcq} is null for a WRITTEN exam and {@code written} for an MCQ one. The file's total and position
	 * are the values in the file, null when the sheet has no such column.
	 */
	record Row(int rowNumber, String roll, Integer right, Integer wrong, BigDecimal mcq, BigDecimal written, BigDecimal total,
			Integer rank, Boolean passed, BigDecimal fileTotal, Integer fileRank, boolean maybeAbsent) {

		/** True when the file's own total or position differs from the calculated one (rule R1). */
		boolean mismatch() {
			return OfflineResultFile.mismatch(total, fileTotal, rank, fileRank);
		}
	}

	/** Whether the file's own total or position differs from the calculated ones: totals by more than 0.01, positions by any number. */
	static boolean mismatch(BigDecimal total, BigDecimal fileTotal, Integer rank, Integer fileRank) {
		return (fileTotal != null && total != null && total.subtract(fileTotal).abs().compareTo(TOLERANCE) > 0)
				|| (fileRank != null && rank != null && fileRank.intValue() != rank.intValue());
	}

	/** The parsed sheet: the type, the full marks and negative read from it, the pass mark, the rows and the blocking problems. */
	record Parsed(String examType, BigDecimal fullMarks, BigDecimal negative, BigDecimal passMark, List<Row> rows,
			List<Problem> problems) {
	}

	private OfflineResultFile() {
	}

	/**
	 * Reads the sheet for one exam type. A file that cannot be read, or one without the columns the type needs, is refused (400) by the
	 * reader or here. Everything else that is wrong with the rows is a problem in the answer.
	 */
	static Parsed parse(byte[] bytes, String examType, BigDecimal passMark) {
		List<Line> lines = OfflineResultReader.read(bytes);
		int header = headerIndex(lines);
		if (header < 0) {
			throw OfflineResultReader.refuse("NO_HEADER", params(),
					"No header row with a Roll column was found. Check that this is the result sheet.");
		}
		Line top = lines.get(header);
		Line bottom = header + 1 < lines.size() && lines.get(header + 1).number() == top.number() + 1 ? lines.get(header + 1) : null;
		Map<Field, Integer> columns = columns(top, bottom);
		requireColumn(columns, Field.ROLL, "Roll", examType);
		requireColumn(columns, Field.FULL, "Number", examType);
		if (!WRITTEN.equals(examType)) {
			requireColumn(columns, Field.RIGHT, "Right", examType);
			requireColumn(columns, Field.WRONG, "Wrong", examType);
			requireColumn(columns, Field.NEGATIVE, "Negative", examType);
		}
		if (!MCQ.equals(examType)) {
			requireColumn(columns, Field.WRITTEN, "Written", examType);
		}
		List<Line> students = new ArrayList<>();
		for (Line line : lines) {
			if (line.number() > top.number() && roll(line, columns) != null) {
				students.add(line);
			}
		}
		if (students.size() > MAX_ROWS) {
			throw OfflineResultReader.refuse("TOO_MANY_ROWS", params("max", MAX_ROWS),
					"The sheet has more than " + MAX_ROWS + " rows. Check that it holds one exam only.");
		}
		List<Problem> problems = new ArrayList<>();
		if (students.isEmpty()) {
			problems.add(new Problem(null, null, "NO_ROWS", params(), "No student rows were found under the header."));
			return new Parsed(examType, null, null, passMark, List.of(), problems);
		}
		BigDecimal fullMarks = null;
		BigDecimal negative = null;
		// Pass one: the full marks and the negative mark, which must be the same on every row (rule A5). The first valid value is the one.
		for (Line line : students) {
			String roll = roll(line, columns);
			BigDecimal full = positive(line, columns, Field.FULL);
			if (full == null) {
				problems.add(new Problem(line.number(), roll, "FULL_MARKS_MISSING", params(),
						"Enter the full marks in the Number column."));
			}
			else if (fullMarks == null) {
				fullMarks = full;
			}
			else if (full.compareTo(fullMarks) != 0) {
				problems.add(new Problem(line.number(), roll, "FULL_MARKS_DIFFER", params("value", plain(full), "first", plain(fullMarks)),
						"The full marks are " + plain(full) + " here but " + plain(fullMarks) + " on the first row. One exam has one full mark."));
			}
			if (!WRITTEN.equals(examType)) {
				BigDecimal value = nonNegative(line, columns, Field.NEGATIVE);
				if (value == null) {
					problems.add(new Problem(line.number(), roll, "NEGATIVE_MISSING", params(),
							"Enter the negative mark in the Negative column."));
				}
				else if (negative == null) {
					negative = value;
				}
				else if (value.compareTo(negative) != 0) {
					problems.add(new Problem(line.number(), roll, "NEGATIVE_DIFFER", params("value", plain(value), "first", plain(negative)),
							"The negative mark is " + plain(value) + " here but " + plain(negative)
									+ " on the first row. One exam has one negative mark."));
				}
			}
		}
		if (fullMarks == null) {
			problems.add(new Problem(null, null, "NO_FULL_MARKS", params(), "No row has full marks in the Number column."));
		}
		BigDecimal deduction = negative == null ? BigDecimal.ZERO : negative;
		// Pass two: each row's marks and its checks against the full marks.
		List<Row> rows = new ArrayList<>();
		Map<String, Integer> firstRowOfRoll = new HashMap<>();
		for (Line line : students) {
			String roll = roll(line, columns);
			Integer first = firstRowOfRoll.putIfAbsent(roll, line.number());
			if (first != null) {
				problems.add(new Problem(line.number(), roll, "ROW_DUPLICATE_ROLL", params("roll", roll, "first", first),
						"Roll " + roll + " is in the sheet more than once (first on row " + first + ")."));
			}
			rows.add(row(line, columns, examType, deduction, fullMarks, passMark, roll, problems));
		}
		if (passMark != null && fullMarks != null && passMark.compareTo(fullMarks) > 0) {
			problems.add(new Problem(null, null, "PASS_MARK_ABOVE_FULL", params("pass", plain(passMark), "full", plain(fullMarks)),
					"The pass mark " + plain(passMark) + " is above the full marks " + plain(fullMarks) + "."));
		}
		return new Parsed(examType, fullMarks, WRITTEN.equals(examType) ? BigDecimal.ZERO : deduction, passMark, rank(rows), problems);
	}

	/** One row's marks, read and calculated. A row with a problem keeps its raw counts but no total, so it is not ranked. */
	private static Row row(Line line, Map<Field, Integer> columns, String examType, BigDecimal negative, BigDecimal fullMarks,
			BigDecimal passMark, String roll, List<Problem> problems) {
		List<Reason> reasons = new ArrayList<>();
		Integer right = null;
		Integer wrong = null;
		BigDecimal written = null;
		BigDecimal mcq = null;
		BigDecimal total = null;
		if (!WRITTEN.equals(examType)) {
			right = count(line, columns, Field.RIGHT, reasons, "Right answers");
			wrong = count(line, columns, Field.WRONG, reasons, "Wrong answers");
		}
		if (!MCQ.equals(examType)) {
			written = number(line, columns, Field.WRITTEN, reasons, "Written marks");
		}
		if (!WRITTEN.equals(examType) && right != null && wrong != null) {
			mcq = round(BigDecimal.valueOf(right).subtract(BigDecimal.valueOf(wrong).multiply(negative)));
		}
		if (MCQ.equals(examType) && mcq != null) {
			total = mcq;
		}
		else if (MCQ_WRITTEN.equals(examType) && mcq != null && written != null) {
			total = round(mcq.add(written));
		}
		else if (WRITTEN.equals(examType) && written != null) {
			total = round(written);
		}
		if (total != null && fullMarks != null && total.compareTo(fullMarks) > 0) {
			reasons.add(new Reason("TOTAL_ABOVE_FULL", params("total", plain(total), "full", plain(fullMarks)),
					"The total " + plain(total) + " is above the full marks " + plain(fullMarks) + "."));
		}
		for (Reason reason : reasons) {
			problems.add(new Problem(line.number(), roll, reason.code(), reason.params(), reason.message()));
		}
		if (!reasons.isEmpty()) {
			total = null;
		}
		boolean maybeAbsent = isZero(right) && isZero(wrong) && isZero(written);
		BigDecimal fileTotal = optionalNumber(line, columns, Field.TOTAL);
		Integer fileRank = optionalWhole(line, columns, Field.POSITION);
		Boolean passed = passMark == null || total == null ? null : total.compareTo(passMark) >= 0;
		return new Row(line.number(), roll, right, wrong, mcq, written, total, null, passed, fileTotal, fileRank, maybeAbsent);
	}

	/** Competition ranking (1, 2, 2, 4) over the rows that have a total. Rows without one stay unranked. */
	private static List<Row> rank(List<Row> rows) {
		List<Row> ranked = new ArrayList<>();
		for (Row row : rows) {
			Integer rank = null;
			if (row.total() != null) {
				int above = 0;
				for (Row other : rows) {
					if (other.total() != null && other.total().compareTo(row.total()) > 0) {
						above++;
					}
				}
				rank = above + 1;
			}
			Boolean passed = row.passed();
			ranked.add(new Row(row.rowNumber(), row.roll(), row.right(), row.wrong(), row.mcq(), row.written(), row.total(), rank,
					passed, row.fileTotal(), row.fileRank(), row.maybeAbsent()));
		}
		return ranked;
	}

	/** The index of the first line with a cell that starts with "Roll", ignoring case. -1 when there is none. */
	private static int headerIndex(List<Line> lines) {
		for (int index = 0; index < lines.size(); index++) {
			for (String text : lines.get(index).cells().values()) {
				if (text.trim().toLowerCase(Locale.ROOT).startsWith("roll")) {
					return index;
				}
			}
		}
		return -1;
	}

	/**
	 * The column of each field. A header is read from its own row first, then from the row under it (the second line of a two-row header,
	 * such as "Right" under "MCQ"), then from both together ("Total" and "marks obtained").
	 */
	static Map<Field, Integer> columns(Line top, Line bottom) {
		Map<Field, Integer> found = new EnumMap<>(Field.class);
		Set<Integer> indexes = new TreeSet<>(top.cells().keySet());
		if (bottom != null) {
			indexes.addAll(bottom.cells().keySet());
		}
		for (int column : indexes) {
			String above = top.cells().getOrDefault(column, "");
			String below = bottom == null ? "" : bottom.cells().getOrDefault(column, "");
			Field field = fieldOf(above);
			if (field == null) {
				field = fieldOf(below);
			}
			if (field == null && !above.isEmpty() && !below.isEmpty()) {
				field = fieldOf(above + " " + below);
			}
			if (field != null) {
				found.putIfAbsent(field, column);
			}
		}
		return found;
	}

	/** The field a header names, ignoring case, spaces and punctuation (plan section 2). Null when it names none. */
	static Field fieldOf(String label) {
		String key = label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
		if (key.isEmpty()) {
			return null;
		}
		if (key.equals("roll") || key.equals("rollno") || key.equals("rollnumber")) {
			return Field.ROLL;
		}
		if (key.equals("number") || key.equals("fullmarks")) {
			return Field.FULL;
		}
		if (key.startsWith("right")) {
			return Field.RIGHT;
		}
		if (key.startsWith("wrong")) {
			return Field.WRONG;
		}
		if (key.startsWith("negative") || key.startsWith("deducted")) {
			return Field.NEGATIVE;
		}
		if (key.startsWith("written")) {
			return Field.WRITTEN;
		}
		if (key.startsWith("total")) {
			return Field.TOTAL;
		}
		if (key.startsWith("position") || key.startsWith("merit")) {
			return Field.POSITION;
		}
		return null;
	}

	private static void requireColumn(Map<Field, Integer> columns, Field field, String name, String examType) {
		if (!columns.containsKey(field)) {
			throw OfflineResultReader.refuse("MISSING_COLUMN", params("column", name, "examType", examType),
					"The sheet has no " + name + " column, so it cannot be read as the " + examLabel(examType)
							+ " type. Choose the exam type that matches the sheet.");
		}
	}

	private static String examLabel(String examType) {
		return switch (examType) {
			case MCQ -> "MCQ";
			case MCQ_WRITTEN -> "MCQ and written";
			default -> "written";
		};
	}

	/** The roll of a line, or null when the roll cell is empty. A whole number written with ".0" is read as the whole number. */
	static String roll(Line line, Map<Field, Integer> columns) {
		String text = text(line, columns, Field.ROLL);
		if (text == null) {
			return null;
		}
		return text.matches("\\d+\\.0+") ? text.substring(0, text.indexOf('.')) : text;
	}

	private static String text(Line line, Map<Field, Integer> columns, Field field) {
		Integer column = columns.get(field);
		if (column == null) {
			return null;
		}
		String text = line.cells().get(column);
		return text == null || text.isBlank() ? null : text.trim();
	}

	/** A whole count: blank is 0, and anything that is not a whole number at least 0 is a reason. */
	private static Integer count(Line line, Map<Field, Integer> columns, Field field, List<Reason> reasons, String label) {
		String text = text(line, columns, field);
		if (text == null) {
			return 0;
		}
		BigDecimal value = parse(text);
		if (value == null) {
			reasons.add(new Reason("NOT_A_NUMBER", params("label", label, "value", text), label + " must be a number, not \"" + text + "\"."));
			return null;
		}
		if (value.signum() < 0) {
			reasons.add(new Reason("BELOW_ZERO", params("label", label, "value", plain(value)), label + " cannot be below zero."));
			return null;
		}
		if (value.stripTrailingZeros().scale() > 0) {
			reasons.add(new Reason("NOT_WHOLE", params("label", label, "value", plain(value)),
					label + " must be a whole number, not " + plain(value) + "."));
			return null;
		}
		if (value.compareTo(BigDecimal.valueOf(MAX_COUNT)) > 0) {
			reasons.add(new Reason("COUNT_TOO_LARGE", params("label", label, "value", plain(value)), label + " is too large."));
			return null;
		}
		return value.intValue();
	}

	/** A number that may be any value: blank is 0, and text that is not a number is a reason. */
	private static BigDecimal number(Line line, Map<Field, Integer> columns, Field field, List<Reason> reasons, String label) {
		String text = text(line, columns, field);
		if (text == null) {
			return BigDecimal.ZERO;
		}
		BigDecimal value = parse(text);
		if (value == null) {
			reasons.add(new Reason("NOT_A_NUMBER", params("label", label, "value", text), label + " must be a number, not \"" + text + "\"."));
			return null;
		}
		return round(value);
	}

	private static BigDecimal positive(Line line, Map<Field, Integer> columns, Field field) {
		BigDecimal value = parse(text(line, columns, field));
		return value == null || value.signum() <= 0 ? null : value;
	}

	private static BigDecimal nonNegative(Line line, Map<Field, Integer> columns, Field field) {
		BigDecimal value = parse(text(line, columns, field));
		return value == null || value.signum() < 0 ? null : value;
	}

	/** The file's own total or position when it has a number there, else null. */
	private static BigDecimal optionalNumber(Line line, Map<Field, Integer> columns, Field field) {
		BigDecimal value = parse(text(line, columns, field));
		return value == null ? null : round(value);
	}

	private static Integer optionalWhole(Line line, Map<Field, Integer> columns, Field field) {
		BigDecimal value = parse(text(line, columns, field));
		if (value == null || value.stripTrailingZeros().scale() > 0 || value.abs().compareTo(BigDecimal.valueOf(MAX_COUNT)) > 0) {
			return null;
		}
		return value.intValue();
	}

	/** The number a cell text holds, or null when it is not a number. */
	private static BigDecimal parse(String text) {
		if (text == null) {
			return null;
		}
		try {
			return new BigDecimal(text.trim());
		}
		catch (NumberFormatException exception) {
			return null;
		}
	}

	private static BigDecimal round(BigDecimal value) {
		return value.setScale(2, RoundingMode.HALF_UP);
	}

	private static boolean isZero(Number value) {
		return value == null || (value instanceof BigDecimal decimal ? decimal.signum() == 0 : value.intValue() == 0);
	}

	/** A number as a reader sees it: without trailing zeros. */
	static String plain(BigDecimal value) {
		return value.stripTrailingZeros().toPlainString();
	}

	/** The parameters of a message as name and value pairs, in the order given (plan F15). */
	static Map<String, Object> params(Object... pairs) {
		Map<String, Object> params = new LinkedHashMap<>();
		for (int index = 0; index < pairs.length; index += 2) {
			params.put((String) pairs[index], pairs[index + 1]);
		}
		return params;
	}

}
