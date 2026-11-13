package com.coachingcenter.api.offline;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.http.HttpStatus;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

/**
 * Reads the table of a batch routine PDF with the position of every glyph (plan section 5, steps 1 and 2).
 *
 * <p>The header row (Date, Day, Subject, Instructor, Topics, Time) sets the column ranges: each column is the span between the midpoints
 * of the neighbouring header labels. Text is grouped into lines by height and into runs by gaps. A run that crosses a column boundary is
 * a merged cell and is kept whole. A row starts at a content line (any column but Date and Day) that is not a second line of the row
 * above. A date or day belongs to the row that starts above it, and a day belongs to the nearest date of its page block; a date cell
 * that spans several rows is carried down to the rows under it.
 *
 * <p>The reader also returns the geometry of each row (its cell in the Instructor column and the name boxes inside it), which the
 * student copy uses to paint the names out. It never changes or saves anything.
 */
final class OfflineRoutineReader {

	static final String FORMAT_MESSAGE = "This PDF does not match the routine format.";

	static final int DATE = 0;

	static final int DAY = 1;

	static final int SUBJECT = 2;

	static final int INSTRUCTOR = 3;

	static final int TOPICS = 4;

	static final int TIME = 5;

	/** Text lines within this height of each other are one line. */
	private static final float LINE_TOLERANCE = 1.5f;

	/**
	 * Glyphs this close in height are one line when grouping them into runs. Superscripts ("rd" and "th" in "3rd") sit about four points
	 * above the baseline; a wider window keeps them in the same run, and the rows are 13 points or more apart, so no two rows merge.
	 */
	private static final float LINE_CLUSTER = 6f;

	/** A gap wider than this ends a run of text. */
	private static final float RUN_GAP = 4f;

	/** A gap wider than this, inside a run, is a space. */
	private static final float WORD_GAP = 1f;

	/** A content line this far below the first line of the row above starts a new row; a closer one is that row's second line. */
	private static final float ROW_BREAK = 20f;

	/** A run may sit this close to a column boundary without crossing it. */
	private static final float EDGE = 3f;

	/** A date or day this far above a row's top still belongs to that row (tolerance for the text's baseline). */
	private static final float ROW_SLACK = 3f;

	/** The rule above a row lies 11.8 points above its first baseline; the cell starts 1 point below that. */
	private static final float CELL_ABOVE = 10.8f;

	/** The rule below a row lies 11.8 points above the next row's first baseline; the cell ends 1 point above it. */
	private static final float CELL_BELOW = 12.8f;

	/** The last row of a page has no next row to measure from: its bottom rule lies this far below its first baseline. */
	private static final float LAST_ONE_LINE = 16.4f;

	private static final float LAST_TWO_LINES = 44f;

	/** Text of 12 points, measured from its baseline. */
	private static final float NAME_ASCENT = 10f;

	private static final float NAME_DESCENT = 4f;

	private static final float NAME_PAD = 2f;

	private OfflineRoutineReader() {
	}

	/** A run of text in one line, with its place on the page (top-left origin, points). */
	record Run(int page, float x0, float x1, float y, String text) {

		float centre() {
			return (x0 + x1) / 2;
		}

	}

	/** A rectangle on a page, measured in points from the top-left corner. */
	record Rect(float x0, float y0, float x1, float y1) {
	}

	/**
	 * One table row as the page shows it. {@code date} and {@code day} are carried down from the block above when the row has none.
	 * {@code cell} is the row's Instructor cell, or null when the cell is part of a merged cell that carries other text; {@code names}
	 * are the boxes of the text in that cell.
	 */
	record RawRow(int page, String date, String day, String time, String subject, String topic, String instructor, Rect cell,
			List<Rect> names) {
	}

	/** The rows of a routine, in reading order, with the page count. */
	record Parsed(int pageCount, List<RawRow> rows) {
	}

	/** Reads the routine. A file that is not a PDF, or has no header row, or no rows, is refused and nothing is kept. */
	static Parsed read(byte[] bytes) {
		List<Glyph> glyphs = new ArrayList<>();
		int pages;
		try (PDDocument document = Loader.loadPDF(bytes)) {
			pages = document.getNumberOfPages();
			PDFTextStripper stripper = new PDFTextStripper() {
				@Override
				protected void writeString(String text, List<TextPosition> positions) {
					int page = getCurrentPageNo();
					for (TextPosition position : positions) {
						String unicode = position.getUnicode();
						if (unicode == null || unicode.isBlank()) {
							continue;
						}
						float x = position.getXDirAdj();
						glyphs.add(new Glyph(page, x, x + position.getWidthDirAdj(), position.getYDirAdj(), unicode));
					}
				}
			};
			stripper.setStartPage(1);
			stripper.setEndPage(pages);
			stripper.getText(document);
		}
		catch (IOException | RuntimeException exception) {
			throw format();
		}
		List<Run> runs = runs(glyphs);
		Header header = header(runs);
		if (header == null) {
			throw format();
		}
		List<Row> rows = rows(runs, header);
		if (rows.isEmpty()) {
			throw format();
		}
		return new Parsed(pages, finish(rows, header));
	}

	/** The rows of a routine: the header's column bounds, and the table body below it. */
	private static final class Header {

		final int page;

		final float y;

		/** The midpoints between the label centres: bounds[i] separates column i from column i + 1. */
		final float[] bounds = new float[5];

		Header(int page, float y, float[] centres) {
			this.page = page;
			this.y = y;
			for (int i = 0; i < 5; i++) {
				bounds[i] = (centres[i] + centres[i + 1]) / 2;
			}
		}

		int column(float x) {
			for (int i = 0; i < bounds.length; i++) {
				if (x < bounds[i]) {
					return i;
				}
			}
			return bounds.length;
		}

		/** A run that reaches over a column boundary (a merged cell). */
		boolean spans(Run run) {
			for (float bound : bounds) {
				if (run.x0() < bound - EDGE && run.x1() > bound + EDGE) {
					return true;
				}
			}
			return false;
		}

		boolean isBody(Run run) {
			return run.page() > page || run.page() == page && run.y() > y + LINE_TOLERANCE;
		}

	}

	/** A row while it is being read: its first line's height and its text by column. */
	private static final class Row {

		final int page;

		final float top;

		final List<Run> subject = new ArrayList<>();

		final List<Run> instructor = new ArrayList<>();

		final List<Run> topics = new ArrayList<>();

		final List<Run> time = new ArrayList<>();

		final List<Run> spans = new ArrayList<>();

		final List<Run> dates = new ArrayList<>();

		final List<Run> days = new ArrayList<>();

		Row(int page, float top) {
			this.page = page;
			this.top = top;
		}

		boolean twoLines() {
			return time.stream().map(Run::y).distinct().count() > 1;
		}

	}

	private record Glyph(int page, float x0, float x1, float y, String text) {
	}

	/** Groups the glyphs into lines (by height), then the lines into runs (by gaps). Runs come in reading order. */
	private static List<Run> runs(List<Glyph> glyphs) {
		List<Glyph> sorted = new ArrayList<>(glyphs);
		sorted.sort(Comparator.comparingInt(Glyph::page).thenComparingDouble(Glyph::y).thenComparingDouble(Glyph::x0));
		List<Run> runs = new ArrayList<>();
		int start = 0;
		while (start < sorted.size()) {
			Glyph first = sorted.get(start);
			int end = start;
			while (end < sorted.size() && sorted.get(end).page() == first.page() && Math.abs(sorted.get(end).y() - first.y()) <= LINE_CLUSTER) {
				end++;
			}
			List<Glyph> line = new ArrayList<>(sorted.subList(start, end));
			line.sort(Comparator.comparingDouble(Glyph::x0));
			runs.addAll(runsOfLine(line));
			start = end;
		}
		return runs;
	}

	private static List<Run> runsOfLine(List<Glyph> line) {
		List<Run> runs = new ArrayList<>();
		if (line.isEmpty()) {
			return runs;
		}
		Glyph first = line.get(0);
		StringBuilder text = new StringBuilder();
		float x0 = 0;
		float x1 = 0;
		Glyph previous = null;
		for (Glyph glyph : line) {
			if (previous == null || glyph.x0() - previous.x1() > RUN_GAP) {
				if (previous != null) {
					runs.add(new Run(first.page(), x0, x1, first.y(), text.toString().trim()));
				}
				text = new StringBuilder(glyph.text());
				x0 = glyph.x0();
				x1 = glyph.x1();
			}
			else {
				if (glyph.x0() - previous.x1() > WORD_GAP) {
					text.append(' ');
				}
				text.append(glyph.text());
				x1 = Math.max(x1, glyph.x1());
			}
			previous = glyph;
		}
		runs.add(new Run(first.page(), x0, x1, first.y(), text.toString().trim()));
		return runs;
	}

	/** The header line: the six labels on one line, in order. Null when no such line exists. */
	private static Header header(List<Run> runs) {
		for (Run date : runs) {
			if (labelOf(date.text()) != DATE) {
				continue;
			}
			Run[] found = new Run[6];
			found[DATE] = date;
			for (Run run : runs) {
				int label = labelOf(run.text());
				if (label < 0 || run.page() != date.page() || Math.abs(run.y() - date.y()) > LINE_TOLERANCE || found[label] != null) {
					continue;
				}
				found[label] = run;
			}
			boolean complete = true;
			for (Run run : found) {
				complete &= run != null;
			}
			if (!complete) {
				continue;
			}
			float[] centres = new float[6];
			for (int i = 0; i < 6; i++) {
				centres[i] = found[i].centre();
				if (i > 0 && centres[i] <= centres[i - 1]) {
					return null;
				}
			}
			return new Header(date.page(), date.y(), centres);
		}
		return null;
	}

	private static int labelOf(String text) {
		String key = text.toLowerCase(Locale.ROOT).replaceAll("\\s", "");
		return switch (key) {
			case "date" -> DATE;
			case "day" -> DAY;
			case "subject" -> SUBJECT;
			case "instructor" -> INSTRUCTOR;
			case "topics", "topic" -> TOPICS;
			case "time" -> TIME;
			default -> -1;
		};
	}

	private static List<Row> rows(List<Run> runs, Header header) {
		List<Row> rows = new ArrayList<>();
		List<Run> dateRuns = new ArrayList<>();
		List<Run> dayRuns = new ArrayList<>();
		Row current = null;
		for (Run run : runs) {
			if (!header.isBody(run)) {
				continue;
			}
			boolean span = header.spans(run);
			int column = span ? -1 : header.column(run.centre());
			if (!span && column == DATE) {
				dateRuns.add(run);
				continue;
			}
			if (!span && column == DAY) {
				dayRuns.add(run);
				continue;
			}
			if (current == null || current.page != run.page() || run.y() >= current.top + ROW_BREAK) {
				current = new Row(run.page(), run.y());
				rows.add(current);
			}
			if (span) {
				current.spans.add(run);
			}
			else if (column == SUBJECT) {
				current.subject.add(run);
			}
			else if (column == INSTRUCTOR) {
				current.instructor.add(run);
			}
			else if (column == TOPICS) {
				current.topics.add(run);
			}
			else {
				current.time.add(run);
			}
		}
		for (Run run : dateRuns) {
			Row row = rowAbove(rows, run);
			if (row != null) {
				row.dates.add(run);
			}
		}
		for (Run run : dayRuns) {
			Row row = dayRow(rows, dateRuns, run);
			if (row != null) {
				row.days.add(run);
			}
		}
		return rows;
	}

	/** The row a date or day line belongs to: the last row of its page whose first line is at or above it. */
	private static Row rowAbove(List<Row> rows, Run run) {
		Row found = null;
		for (Row row : rows) {
			if (row.page == run.page() && row.top <= run.y() + ROW_SLACK) {
				found = row;
			}
		}
		if (found == null) {
			for (Row row : rows) {
				if (row.page == run.page()) {
					return row;
				}
			}
		}
		return found;
	}

	/** A day belongs to the block of the nearest date on its page, so a day on the second row of a block joins its date. */
	private static Row dayRow(List<Row> rows, List<Run> dateRuns, Run day) {
		Run nearest = null;
		for (Run date : dateRuns) {
			if (date.page() == day.page() && (nearest == null || Math.abs(date.y() - day.y()) < Math.abs(nearest.y() - day.y()))) {
				nearest = date;
			}
		}
		if (nearest != null && Math.abs(nearest.y() - day.y()) <= ROW_BREAK) {
			return rowAbove(rows, nearest);
		}
		return rowAbove(rows, day);
	}

	private static List<OfflineRoutineReader.RawRow> finish(List<Row> rows, Header header) {
		List<RawRow> raw = new ArrayList<>();
		String carriedDate = null;
		String carriedDay = null;
		for (int index = 0; index < rows.size(); index++) {
			Row row = rows.get(index);
			Row next = index + 1 < rows.size() && rows.get(index + 1).page == row.page ? rows.get(index + 1) : null;
			float bandTop = row.top - CELL_ABOVE;
			float bandBottom = next != null ? next.top - CELL_BELOW : row.top + (row.twoLines() ? LAST_TWO_LINES : LAST_ONE_LINE) - 1f;
			String date = compact(join(row.dates));
			String day = compact(join(row.days));
			if (date.isEmpty()) {
				date = carriedDate == null ? "" : carriedDate;
				if (day.isEmpty() && carriedDay != null) {
					day = carriedDay;
				}
			}
			if (!date.isEmpty()) {
				carriedDate = date;
			}
			if (!day.isEmpty()) {
				carriedDay = day;
			}
			boolean merged = row.spans.stream().anyMatch(run -> run.x0() < header.bounds[3] && run.x1() > header.bounds[2]);
			Rect cell = merged ? null : new Rect(header.bounds[2] + 1.5f, bandTop, header.bounds[3] - 1.5f, bandBottom);
			List<Rect> names = new ArrayList<>();
			if (!merged) {
				for (Run run : row.instructor) {
					names.add(new Rect(run.x0() - NAME_PAD, run.y() - NAME_ASCENT, run.x1() + NAME_PAD, run.y() + NAME_DESCENT));
				}
			}
			String topic = join(row.topics);
			if (!row.spans.isEmpty()) {
				topic = (topic + " " + join(row.spans)).trim();
			}
			raw.add(new RawRow(row.page, date, day, compact(join(row.time)), join(row.subject), topic, join(row.instructor), cell, names));
		}
		return raw;
	}

	/** The text of runs in reading order, one space between runs. */
	private static String join(List<Run> runs) {
		List<Run> ordered = new ArrayList<>(runs);
		ordered.sort(Comparator.comparingDouble(Run::y).thenComparingDouble(Run::x0));
		StringBuilder text = new StringBuilder();
		for (Run run : ordered) {
			if (!text.isEmpty()) {
				text.append(' ');
			}
			text.append(run.text());
		}
		return text.toString().replaceAll("\\s+", " ").trim();
	}

	/** Text with every space removed: a date "25-10- 2026", a day "Frid ay" and a time "9.00AM- 10.20AM" are one value each. */
	private static String compact(String text) {
		return text.replaceAll("\\s", "");
	}

	private static ApiException format() {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, FORMAT_MESSAGE);
	}

}
