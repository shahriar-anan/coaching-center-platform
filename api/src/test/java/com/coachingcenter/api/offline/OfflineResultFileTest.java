package com.coachingcenter.api.offline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.FieldErrorDetail;
import com.coachingcenter.api.offline.OfflineResultFile.Parsed;
import com.coachingcenter.api.offline.OfflineResultFile.Row;

/**
 * The reader and the calculation of a result sheet (plan sections 2 and 9): both sample sheets, ties and zero rows, negative totals, two-row
 * headers, and the refusals. The samples are the two files in docs/offline-admission, copied to test resources.
 */
class OfflineResultFileTest {

	private static final BigDecimal FIFTY = new BigDecimal("50");

	@Test
	void mcqSampleMatchesTheFileRowForRow() throws IOException {
		Parsed parsed = OfflineResultFile.parse(sample("exam-01-constitution-mcq.xlsx"), OfflineResultFile.MCQ, null);

		assertThat(parsed.problems()).isEmpty();
		assertThat(parsed.fullMarks()).isEqualByComparingTo("50");
		assertThat(parsed.negative()).isEqualByComparingTo("0.25");
		assertThat(parsed.rows()).hasSize(100);

		Row roll192019 = row(parsed, "192019");
		assertThat(roll192019.right()).isEqualTo(44);
		assertThat(roll192019.wrong()).isEqualTo(6);
		assertThat(roll192019.total()).isEqualByComparingTo("42.50");
		assertThat(roll192019.rank()).isEqualTo(2);
		assertThat(roll192019.mismatch()).isFalse();
		assertThat(roll192019.mcq()).isEqualByComparingTo("42.50");
		assertThat(roll192019.written()).isNull();
	}

	@Test
	void mcqSampleZeroRowsShareARankAndAreMaybeAbsent() throws IOException {
		Parsed parsed = OfflineResultFile.parse(sample("exam-01-constitution-mcq.xlsx"), OfflineResultFile.MCQ, null);

		Row zero = row(parsed, "192001");
		assertThat(zero.maybeAbsent()).isTrue();
		assertThat(zero.total()).isEqualByComparingTo("0");
		// The file's own position for these rows is not the calculated one (R1): the preview flags them.
		assertThat(zero.mismatch()).isTrue();
		assertThat(row(parsed, "192002").rank()).isEqualTo(zero.rank());
		assertThat(row(parsed, "192028").rank()).isEqualTo(1);
	}

	@Test
	void mcqWrittenSampleMatchesTheFileRanks() throws IOException {
		Parsed parsed = OfflineResultFile.parse(sample("exam-01-mcq-written.xlsx"), OfflineResultFile.MCQ_WRITTEN, new BigDecimal("40"));

		assertThat(parsed.problems()).isEmpty();
		assertThat(parsed.fullMarks()).isEqualByComparingTo(FIFTY.add(new BigDecimal("50")));

		Row roll191012 = row(parsed, "191012");
		assertThat(roll191012.mcq()).isEqualByComparingTo("18.75");
		assertThat(roll191012.written()).isEqualByComparingTo("32");
		assertThat(roll191012.total()).isEqualByComparingTo("50.75");
		assertThat(roll191012.rank()).isEqualTo(2);
		assertThat(roll191012.passed()).isTrue();
		assertThat(roll191012.mismatch()).isFalse();

		Row roll191019 = row(parsed, "191019");
		assertThat(roll191019.total()).isEqualByComparingTo("19.75");
		assertThat(roll191019.rank()).isEqualTo(7);
		assertThat(roll191019.passed()).isFalse();

		assertThat(row(parsed, "191011").maybeAbsent()).isTrue();
	}

	@Test
	void tiesShareARankAndNegativeTotalsRankBelowZero() {
		Parsed parsed = OfflineResultFile.parse(workbook(
				List.of("Roll No", "Number", "Right Answer", "Wrong Answer", "Negative(Per Q.)"),
				List.of("1001", "50", "10", "0", "0.25"),
				List.of("1002", "50", "10", "0", "0.25"),
				List.of("1003", "50", "5", "4", "0.25"),
				List.of("1004", "50", "2", "8", "0.25"),
				List.of("1005", "50", "0", "4", "0.25"),
				Arrays.asList("1006", "50", "0", "0", "0.25")), OfflineResultFile.MCQ, null);

		assertThat(parsed.problems()).isEmpty();
		assertThat(rank(parsed, "1001")).isEqualTo(1);
		assertThat(rank(parsed, "1002")).isEqualTo(1);
		assertThat(rank(parsed, "1003")).isEqualTo(3);
		assertThat(rank(parsed, "1004")).isEqualTo(4);
		assertThat(rank(parsed, "1006")).isEqualTo(4);
		assertThat(row(parsed, "1005").total()).isEqualByComparingTo("-1");
		assertThat(rank(parsed, "1005")).isEqualTo(6);
		assertThat(row(parsed, "1006").maybeAbsent()).isTrue();
		assertThat(row(parsed, "1004").maybeAbsent()).isFalse();
	}

	@Test
	void twoRowHeaderAndReorderedColumnsAreRead() {
		Parsed parsed = OfflineResultFile.parse(workbook(
				List.of("SYMAM LAW"),
				Arrays.asList("Written", "Roll No", "Number", "MCQ", null, "Deducted", "Total marks", "Position"),
				Arrays.asList(null, null, null, "Right", "Wrong", "Mark", "obtained", null),
				Arrays.asList("20", "7001", "50", "30", "4", "0.5", null, "1"),
				Arrays.asList("10", "7002", "50", "12", "0", "0.5", null, "2")), OfflineResultFile.MCQ_WRITTEN, null);

		assertThat(parsed.problems()).isEmpty();
		assertThat(parsed.negative()).isEqualByComparingTo("0.5");
		Row first = row(parsed, "7001");
		assertThat(first.total()).isEqualByComparingTo("48");
		assertThat(first.fileRank()).isEqualTo(1);
		assertThat(first.rank()).isEqualTo(1);
		assertThat(row(parsed, "7002").total()).isEqualByComparingTo("22");
		assertThat(row(parsed, "7002").fileTotal()).isNull();
	}

	@Test
	void theWrittenTypeTotalsTheWrittenMarks() {
		Parsed parsed = OfflineResultFile.parse(workbook(
				List.of("Roll", "Number", "Written"),
				List.of("8001", "100", "64.5"),
				List.of("8002", "100", "64.5"),
				Arrays.asList("8003", "100", "10")), OfflineResultFile.WRITTEN, null);

		assertThat(parsed.problems()).isEmpty();
		assertThat(parsed.negative()).isEqualByComparingTo("0");
		assertThat(row(parsed, "8001").total()).isEqualByComparingTo("64.50");
		assertThat(row(parsed, "8001").mcq()).isNull();
		assertThat(row(parsed, "8001").right()).isNull();
		assertThat(rank(parsed, "8002")).isEqualTo(1);
		assertThat(rank(parsed, "8003")).isEqualTo(3);
	}

	@Test
	void passMarkDecidesPassAndFailAndIsNullWithoutOne() {
		byte[] sheet = workbook(
				List.of("Roll", "Number", "Right", "Wrong", "Negative"),
				List.of("9001", "50", "20", "0", "0"),
				List.of("9002", "50", "39", "0", "0"),
				List.of("9003", "50", "40", "0", "0"));

		Parsed withPass = OfflineResultFile.parse(sheet, OfflineResultFile.MCQ, new BigDecimal("40"));
		assertThat(row(withPass, "9001").passed()).isFalse();
		assertThat(row(withPass, "9002").passed()).isFalse();
		assertThat(row(withPass, "9003").passed()).isTrue();

		Parsed noPass = OfflineResultFile.parse(sheet, OfflineResultFile.MCQ, null);
		assertThat(row(noPass, "9001").passed()).isNull();
	}

	@Test
	void problemsAreReportedWithTheirRows() {
		Parsed parsed = OfflineResultFile.parse(workbook(
				List.of("Roll", "Number", "Right", "Wrong", "Negative"),
				List.of("6001", "50", "20", "0", "0.25"),
				List.of("6002", "100", "20", "0", "0.25"),
				List.of("6003", "50", "4.5", "0", "0.25"),
				List.of("6004", "50", "60", "0", "0.25"),
				List.of("6001", "50", "10", "0", "0.25"),
				List.of("6005", "50", "10", "0", "0.5"),
				Arrays.asList(null, "50", "10", "0", "0.25")), OfflineResultFile.MCQ, null);

		assertThat(problem(parsed, "6002")).contains("full marks");
		assertThat(problem(parsed, "6003")).contains("whole number");
		assertThat(problem(parsed, "6004")).contains("above the full marks");
		assertThat(problem(parsed, "6001")).contains("more than once");
		assertThat(problem(parsed, "6005")).contains("negative mark");
		// An empty roll is skipped, so there are five rows read, not seven.
		assertThat(parsed.rows()).hasSize(6);
	}

	@Test
	void anXlsFileIsRefusedWithTheSaveAgainMessage() {
		byte[] xls = new byte[] { (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1, 0, 0 };

		assertThatThrownBy(() -> OfflineResultFile.parse(xls, OfflineResultFile.MCQ, null))
			.isInstanceOfSatisfying(ApiException.class, exception -> assertThat(details(exception))
				.containsExactly(new FieldErrorDetail("file", "Save the file as .xlsx and upload it again.", "FILE_REFUSED", Map.of())));
	}

	@Test
	void aFileThatIsNotAWorkbookIsRefused() {
		assertThatThrownBy(() -> OfflineResultFile.parse("not a sheet".getBytes(StandardCharsets.UTF_8), OfflineResultFile.MCQ, null))
			.isInstanceOfSatisfying(ApiException.class, exception -> assertThat(details(exception))
				.containsExactly(new FieldErrorDetail("file", "This file is not a readable .xlsx workbook.", "FILE_REFUSED", Map.of())));
	}

	@Test
	void problemsCarryACodeAndTheirParameters() {
		Parsed parsed = OfflineResultFile.parse(workbook(
				List.of("Roll", "Number", "Right", "Wrong", "Negative"),
				List.of("6001", "50", "20", "0", "0.25"),
				List.of("6002", "100", "20", "0", "0.25"),
				List.of("6003", "50", "4.5", "0", "0.25"),
				List.of("6004", "50", "60", "0", "0.25"),
				List.of("6001", "50", "10", "0", "0.25"),
				List.of("6005", "50", "10", "0", "0.5")), OfflineResultFile.MCQ, null);

		OfflineResultFile.Problem fullMarks = problemOf(parsed, "6002");
		assertThat(fullMarks.code()).isEqualTo("FULL_MARKS_DIFFER");
		assertThat(fullMarks.params()).containsEntry("value", "100").containsEntry("first", "50");
		assertThat(problemOf(parsed, "6003").code()).isEqualTo("NOT_WHOLE");
		assertThat(problemOf(parsed, "6003").params()).containsEntry("label", "Right answers").containsEntry("value", "4.5");
		assertThat(problemOf(parsed, "6004").code()).isEqualTo("TOTAL_ABOVE_FULL");
		assertThat(problemOf(parsed, "6004").params()).containsEntry("total", "60").containsEntry("full", "50");
		OfflineResultFile.Problem duplicate = problemOf(parsed, "6001");
		assertThat(duplicate.code()).isEqualTo("ROW_DUPLICATE_ROLL");
		assertThat(duplicate.params()).containsEntry("first", 2).containsEntry("roll", "6001");
		assertThat(problemOf(parsed, "6005").code()).isEqualTo("NEGATIVE_DIFFER");
		assertThat(problemOf(parsed, "6005").message()).contains("negative mark");
	}

	@Test
	void aPassMarkAboveTheFullMarksIsBlocked() throws IOException {
		Parsed above = OfflineResultFile.parse(sample("exam-01-constitution-mcq.xlsx"), OfflineResultFile.MCQ, new BigDecimal("60"));
		OfflineResultFile.Problem problem = above.problems().stream().filter(found -> "PASS_MARK_ABOVE_FULL".equals(found.code()))
			.findFirst().orElseThrow();
		assertThat(problem.message()).isEqualTo("The pass mark 60 is above the full marks 50.");
		assertThat(problem.params()).containsEntry("pass", "60").containsEntry("full", "50");

		Parsed equal = OfflineResultFile.parse(sample("exam-01-constitution-mcq.xlsx"), OfflineResultFile.MCQ, new BigDecimal("50"));
		assertThat(equal.problems()).isEmpty();
	}

	@Test
	void partsThatAreNotNeededAreSkippedAndDoNotCountTowardTheCap() {
		Map<String, byte[]> parts = workbookParts(
				List.of("Roll", "Number", "Right", "Wrong", "Negative"),
				List.of("7101", "50", "30", "0", "0.25"));
		// Sixty parts of one megabyte each: together more than the 50 MB the reader may expand, but none of them is read.
		for (int index = 0; index < 60; index++) {
			parts.put("xl/media/picture" + index + ".bin", new byte[1024 * 1024]);
		}

		Parsed parsed = OfflineResultFile.parse(zipOf(parts), OfflineResultFile.MCQ, null);

		assertThat(parsed.problems()).isEmpty();
		assertThat(row(parsed, "7101").total()).isEqualByComparingTo("30");
	}

	@Test
	void aZipWithTooManyEntriesIsRefusedAsTooLarge() {
		Map<String, byte[]> parts = workbookParts(List.of("Roll", "Number", "Right", "Wrong", "Negative"),
				List.of("7201", "50", "30", "0", "0.25"));
		for (int index = 0; index < 1000; index++) {
			parts.put("xl/media/tiny" + index + ".bin", new byte[] { 1 });
		}

		assertTooLarge(zipOf(parts));
	}

	@Test
	void aPartThatExpandsPastTheCapIsRefusedAsTooLarge() {
		Map<String, byte[]> parts = workbookParts(List.of("Roll", "Number", "Right", "Wrong", "Negative"),
				List.of("7301", "50", "30", "0", "0.25"));
		// The shared strings are read, so this part expands to 51 MB, which is over the cap.
		parts.put("xl/sharedStrings.xml", new byte[51 * 1024 * 1024]);

		assertTooLarge(zipOf(parts));
	}

	private static void assertTooLarge(byte[] zip) {
		assertThatThrownBy(() -> OfflineResultFile.parse(zip, OfflineResultFile.MCQ, null))
			.isInstanceOfSatisfying(ApiException.class, exception -> assertThat(details(exception))
				.singleElement()
				.satisfies(detail -> assertThat(detail.message()).isEqualTo("The file is too large to read.")));
	}

	@Test
	void aMissingColumnIsRefusedWithTheTypeNamed() {
		assertThatThrownBy(() -> OfflineResultFile.parse(workbook(
				List.of("Roll", "Number", "Right", "Wrong", "Negative"),
				Arrays.asList("5001", "50", "10", "0", "0.25")), OfflineResultFile.MCQ_WRITTEN, null))
			.isInstanceOfSatisfying(ApiException.class, exception -> assertThat(details(exception))
				.singleElement()
				.satisfies(detail -> assertThat(detail.message()).contains("no Written column")));
	}

	@Test
	void aSheetWithoutARollHeaderIsRefused() {
		assertThatThrownBy(() -> OfflineResultFile.parse(workbook(
				List.of("Name", "Number", "Written"),
				Arrays.asList("Someone", "100", "10")), OfflineResultFile.WRITTEN, null))
			.isInstanceOfSatisfying(ApiException.class, exception -> assertThat(details(exception))
				.singleElement()
				.satisfies(detail -> assertThat(detail.message()).contains("Roll")));
	}

	@SuppressWarnings("unchecked")
	private static List<FieldErrorDetail> details(ApiException exception) {
		return (List<FieldErrorDetail>) exception.details();
	}

	private static Row row(Parsed parsed, String roll) {
		return parsed.rows().stream().filter(row -> roll.equals(row.roll())).findFirst().orElseThrow();
	}

	private static Integer rank(Parsed parsed, String roll) {
		return row(parsed, roll).rank();
	}

	private static String problem(Parsed parsed, String roll) {
		return parsed.problems().stream().filter(problem -> roll.equals(problem.roll())).map(OfflineResultFile.Problem::message)
			.findFirst().orElse("");
	}

	private static OfflineResultFile.Problem problemOf(Parsed parsed, String roll) {
		return parsed.problems().stream().filter(problem -> roll.equals(problem.roll())).findFirst().orElseThrow();
	}

	private static byte[] sample(String name) throws IOException {
		try (InputStream input = OfflineResultFileTest.class.getResourceAsStream("/results/" + name)) {
			assertThat(input).as("sample %s", name).isNotNull();
			return input.readAllBytes();
		}
	}

	/** Builds a one-sheet workbook from rows of cell text. Numbers are written as numbers, everything else as text. */
	static byte[] workbook(List<?>... rows) {
		return zipOf(workbookParts(rows));
	}

	/** The parts of a one-sheet workbook of the given rows, by part name: the workbook, its relationships, and the sheet. */
	static Map<String, byte[]> workbookParts(List<?>... rows) {
		StringBuilder sheet = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
				+ "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
		int number = 0;
		for (List<?> row : rows) {
			number++;
			sheet.append("<row r=\"").append(number).append("\">");
			for (int column = 0; column < row.size(); column++) {
				Object value = row.get(column);
				if (value == null) {
					continue;
				}
				String reference = columnName(column) + number;
				String text = value.toString();
				if (text.matches("-?\\d+(\\.\\d+)?")) {
					sheet.append("<c r=\"").append(reference).append("\"><v>").append(text).append("</v></c>");
				}
				else {
					sheet.append("<c r=\"").append(reference).append("\" t=\"inlineStr\"><is><t>").append(text).append("</t></is></c>");
				}
			}
			sheet.append("</row>");
		}
		sheet.append("</sheetData></worksheet>");
		String workbook = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook "
				+ "xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
				+ "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
				+ "<sheets><sheet name=\"Sheet1\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>";
		String rels = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships "
				+ "xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
				+ "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" "
				+ "Target=\"worksheets/sheet1.xml\"/></Relationships>";
		Map<String, byte[]> parts = new LinkedHashMap<>();
		parts.put("xl/workbook.xml", workbook.getBytes(StandardCharsets.UTF_8));
		parts.put("xl/_rels/workbook.xml.rels", rels.getBytes(StandardCharsets.UTF_8));
		parts.put("xl/worksheets/sheet1.xml", sheet.toString().getBytes(StandardCharsets.UTF_8));
		return parts;
	}

	/** A zip of the parts, in the order given. */
	static byte[] zipOf(Map<String, byte[]> parts) {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
				for (Map.Entry<String, byte[]> part : parts.entrySet()) {
					zip.putNextEntry(new ZipEntry(part.getKey()));
					zip.write(part.getValue());
					zip.closeEntry();
				}
			}
			return bytes.toByteArray();
		}
		catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static String columnName(int index) {
		return String.valueOf((char) ('A' + index));
	}

}
