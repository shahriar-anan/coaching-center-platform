package com.coachingcenter.api.exams;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

/**
 * Reads an MCQ question paper from a Word file (plan: docs/exam-site/plans/mcq-docx-upload). The header is the text before
 * question 1. A question starts with a number and a full stop or danda. Options are ক) খ) গ) ঘ), and the correct option is the
 * one in bold. The file is only read here, nothing is saved.
 */
public final class McqDocxParser {

	/** Largest unpacked {@code word/document.xml} accepted. A paper of 100 questions is far below this. */
	static final long MAX_DOCUMENT_BYTES = 20L * 1024 * 1024;

	private static final String DOCUMENT = "word/document.xml";

	private static final Pattern QUESTION = Pattern.compile("^([0-9\u09E6-\u09EF]+)\\s*[.\u0964]\\s*(.*)$", Pattern.DOTALL);

	private static final Pattern OPTION_LABEL = Pattern.compile("(?:^|(?<=\\s))([\u0995\u0996\u0997\u0998\u099A])\\)");

	private static final String OPTION_ORDER = "\u0995\u0996\u0997\u0998\u099A";

	private static final Pattern FULL_MARKS = Pattern.compile("\u09AA\u09C2\u09B0\u09CD\u09A3\u09AE\u09BE\u09A8\\s*:\\s*([0-9\u09E6-\u09EF]+)");

	private static final Pattern MINUTES = Pattern.compile("\u09B8\u09AE\u09DF\\s*:\\s*([0-9\u09E6-\u09EF]+)\\s*\u09AE\u09BF\u09A8\u09BF\u099F");

	/** A line that starts a question's explanation: "ব্যাখ্যা:" (with a colon or a visarga) or "Explanation:". */
	private static final Pattern EXPLANATION = Pattern.compile(
			"^(?:ব্[‌‍]?যাখ্[‌‍]?যা|(?i:explanation))\\s*[:ঃ]\\s*(.*)$", Pattern.DOTALL);

	private McqDocxParser() {
	}

	/** One option of a question. {@code correct} is true when it is in bold. */
	public record ImportedOption(String text, boolean correct) {
	}

	/** One question as read from the file. */
	public record ImportedQuestion(int number, String stem, String explanation, List<ImportedOption> options) {
	}

	/** A problem with the file. {@code question} is the question number, or null for the file as a whole. */
	public record ImportProblem(Integer question, String code) {
	}

	/**
	 * The result of reading a file. {@code problems} block the import; {@code warnings} do not. {@code fullMarks} and
	 * {@code durationMinutes} come from the header and are null when the header does not say.
	 */
	public record ImportResult(List<String> header, Integer fullMarks, Integer durationMinutes,
			List<ImportedQuestion> questions, List<ImportProblem> problems, List<String> warnings) {

		public boolean blocked() {
			return !problems.isEmpty();
		}
	}

	/** A paragraph's text, with the bold flag of each character. */
	private static final class Paragraph {

		private final StringBuilder text = new StringBuilder();

		private final List<Boolean> bold = new ArrayList<>();

		private void append(String value, boolean isBold) {
			for (int i = 0; i < value.length(); i++) {
				text.append(value.charAt(i));
				bold.add(isBold);
			}
		}

		private String text() {
			return text.toString();
		}
	}

	/** Reads a Word file's question paper. Never throws for a bad file: a file that cannot be read is reported as a problem. */
	public static ImportResult parse(byte[] file) {
		List<Paragraph> paragraphs;
		try {
			paragraphs = readParagraphs(file);
		}
		catch (IOException | javax.xml.stream.XMLStreamException | IllegalStateException exception) {
			return failed(new ImportProblem(null, "NOT_A_DOCX"));
		}
		return classify(paragraphs);
	}

	private static ImportResult failed(ImportProblem problem) {
		return new ImportResult(List.of(), null, null, List.of(), List.of(problem), List.of());
	}

	private static List<Paragraph> readParagraphs(byte[] file) throws IOException, javax.xml.stream.XMLStreamException {
		byte[] xml = documentXml(file);
		XMLInputFactory factory = XMLInputFactory.newFactory();
		// No DTDs and no external entities: a crafted file cannot read the server.
		factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
		factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
		XMLStreamReader reader = factory.createXMLStreamReader(new ByteArrayInputStream(xml));
		List<Paragraph> paragraphs = new ArrayList<>();
		Paragraph current = null;
		boolean bold = false;
		boolean inRunProperties = false;
		boolean inText = false;
		boolean inDeleted = false;
		while (reader.hasNext()) {
			int event = reader.next();
			if (event == XMLStreamConstants.START_ELEMENT) {
				switch (reader.getLocalName()) {
					case "p" -> {
						current = new Paragraph();
						paragraphs.add(current);
					}
					case "r" -> bold = false;
					case "rPr" -> inRunProperties = true;
					case "b", "bCs" -> {
						if (inRunProperties) {
							String value = reader.getAttributeValue(null, "val");
							bold = !("0".equals(value) || "false".equals(value));
						}
					}
					case "t" -> inText = true;
					case "delText" -> inDeleted = true;
					case "tab", "br" -> {
						if (current != null) {
							current.append(" ", false);
						}
					}
					default -> {
					}
				}
			}
			else if (event == XMLStreamConstants.END_ELEMENT) {
				switch (reader.getLocalName()) {
					case "rPr" -> inRunProperties = false;
					case "t" -> inText = false;
					case "delText" -> inDeleted = false;
					default -> {
					}
				}
			}
			else if ((event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) && inText && !inDeleted
					&& current != null) {
				current.append(reader.getText(), bold);
			}
		}
		reader.close();
		return paragraphs;
	}

	/** The XML of {@code word/document.xml}, read with a size cap so a huge entry cannot exhaust memory. */
	private static byte[] documentXml(byte[] file) throws IOException {
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(file))) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				if (DOCUMENT.equals(entry.getName())) {
					return readCapped(zip);
				}
			}
		}
		throw new IOException("no document part");
	}

	private static byte[] readCapped(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		long total = 0;
		int read;
		while ((read = in.read(buffer)) != -1) {
			total += read;
			if (total > MAX_DOCUMENT_BYTES) {
				throw new IOException("document too large");
			}
			out.write(buffer, 0, read);
		}
		return out.toByteArray();
	}

	private static ImportResult classify(List<Paragraph> paragraphs) {
		List<String> header = new ArrayList<>();
		List<Question> questions = new ArrayList<>();
		Question current = null;
		for (Paragraph paragraph : paragraphs) {
			String raw = paragraph.text();
			String line = raw.strip();
			if (line.isEmpty()) {
				continue;
			}
			// The stripped line sits inside the raw text, after its leading spaces.
			int lead = raw.indexOf(line);
			Matcher question = QUESTION.matcher(line);
			if (question.matches()) {
				current = new Question(parseNumber(question.group(1)));
				questions.add(current);
				// The stem is the end of the line, so its start is the line's end minus its length.
				int bodyStart = lead + line.length() - question.group(2).length();
				readBody(paragraph, current, question.group(2), bodyStart);
				continue;
			}
			if (current == null) {
				header.add(line);
				continue;
			}
			// The explanation runs to the next question. Its text is never read as options or bold, so it may name "খ)".
			Matcher explanation = EXPLANATION.matcher(line);
			if (explanation.matches()) {
				current.inExplanation = true;
				appendExplanation(current, explanation.group(1));
				continue;
			}
			if (current.inExplanation) {
				appendExplanation(current, line);
				continue;
			}
			readBody(paragraph, current, line, lead);
		}
		return finish(header, questions);
	}

	private static void appendExplanation(Question question, String text) {
		if (!text.isBlank()) {
			question.explanation.append(question.explanation.isEmpty() ? "" : " ").append(text.strip());
		}
	}

	/** Reads the text of a question paragraph: stem text, then options. Text after the options is a problem. */
	private static void readBody(Paragraph paragraph, Question question, String body, int offset) {
		String raw = paragraph.text();
		Matcher labels = OPTION_LABEL.matcher(raw);
		List<int[]> found = new ArrayList<>();
		while (labels.find()) {
			if (labels.start() < offset) {
				continue;
			}
			found.add(new int[] { labels.start(), labels.end(), labels.group(1).charAt(0) });
		}
		if (found.isEmpty()) {
			if (question.options.isEmpty()) {
				question.stem.append(question.stem.isEmpty() ? "" : " ").append(body);
			}
			else if (!body.isBlank()) {
				question.problems.add("STRAY_TEXT");
			}
			return;
		}
		int firstLabel = found.get(0)[0];
		String before = raw.substring(offset, firstLabel).strip();
		if (!before.isEmpty()) {
			if (question.options.isEmpty()) {
				question.stem.append(question.stem.isEmpty() ? "" : " ").append(before);
			}
			else {
				question.problems.add("STRAY_TEXT");
			}
		}
		for (int i = 0; i < found.size(); i++) {
			int labelEnd = found.get(i)[1];
			int end = i + 1 < found.size() ? found.get(i + 1)[0] : raw.length();
			char label = (char) found.get(i)[2];
			addOption(question, label, paragraph, labelEnd, end);
		}
	}

	private static void addOption(Question question, char label, Paragraph paragraph, int from, int to) {
		String text = paragraph.text().substring(from, to).strip();
		int seen = 0;
		int boldSeen = 0;
		for (int i = from; i < to; i++) {
			if (!Character.isWhitespace(paragraph.text().charAt(i))) {
				seen++;
				if (paragraph.bold.get(i)) {
					boldSeen++;
				}
			}
		}
		if (text.isEmpty()) {
			question.problems.add("EMPTY_OPTION");
		}
		if (seen > 0 && boldSeen > 0 && boldSeen < seen && boldSeen * 2 < seen) {
			question.problems.add("PARTIAL_BOLD");
		}
		boolean correct = seen > 0 && boldSeen * 2 >= seen;
		question.options.add(new ImportedOption(text, correct));
		if (question.labelCount >= OPTION_ORDER.length() || OPTION_ORDER.charAt(question.labelCount) != label) {
			question.problems.add("OPTION_ORDER");
		}
		question.labelCount++;
	}

	private static ImportResult finish(List<String> header, List<Question> questions) {
		List<ImportProblem> problems = new ArrayList<>();
		List<String> warnings = new ArrayList<>();
		if (questions.isEmpty()) {
			problems.add(new ImportProblem(null, "NO_QUESTIONS"));
			return new ImportResult(header, null, null, List.of(), problems, warnings);
		}
		List<ImportedQuestion> out = new ArrayList<>();
		for (int i = 0; i < questions.size(); i++) {
			Question question = questions.get(i);
			Integer number = question.number;
			if (number == null || number != i + 1) {
				problems.add(new ImportProblem(i + 1, "NUMBER_ORDER"));
			}
			for (String code : question.problems) {
				problems.add(new ImportProblem(question.number, code));
			}
			if (question.stem.isEmpty()) {
				problems.add(new ImportProblem(question.number, "EMPTY_STEM"));
			}
			if (question.options.size() < 2) {
				problems.add(new ImportProblem(question.number, "FEW_OPTIONS"));
			}
			long correct = question.options.stream().filter(ImportedOption::correct).count();
			if (correct == 0) {
				problems.add(new ImportProblem(question.number, "NO_BOLD_OPTION"));
			}
			else if (correct > 1) {
				problems.add(new ImportProblem(question.number, "MANY_BOLD_OPTIONS"));
			}
			out.add(new ImportedQuestion(question.number == null ? i + 1 : question.number, question.stem.toString(),
					question.explanation.isEmpty() ? null : question.explanation.toString(), List.copyOf(question.options)));
		}
		Integer fullMarks = null;
		Integer minutes = null;
		// The header is matched in one spelling: য় can be stored as one character or as য plus a nukta, and NFC leaves
		// the second form as it is (U+09DF is excluded from composition).
		String headerText = Normalizer.normalize(String.join(" ", header), Normalizer.Form.NFC).replace("য়", "য়");
		Matcher marks = FULL_MARKS.matcher(headerText);
		if (marks.find()) {
			fullMarks = Integer.valueOf(latin(marks.group(1)));
		}
		Matcher time = MINUTES.matcher(headerText);
		if (time.find()) {
			minutes = Integer.valueOf(latin(time.group(1)));
		}
		if (fullMarks != null && fullMarks != questions.size()) {
			warnings.add("FULL_MARKS_DIFFER");
		}
		return new ImportResult(List.copyOf(header), fullMarks, minutes, List.copyOf(out), List.copyOf(problems),
				List.copyOf(warnings));
	}

	private static int parseNumber(String digits) {
		try {
			return Integer.parseInt(latin(digits));
		}
		catch (NumberFormatException exception) {
			return -1;
		}
	}

	/** Bangla digits as Latin ones, so numbers read the same however they were typed. */
	static String latin(String text) {
		StringBuilder out = new StringBuilder();
		for (char c : text.toCharArray()) {
			out.append(c >= '\u09E6' && c <= '\u09EF' ? (char) ('0' + (c - '\u09E6')) : c);
		}
		return out.toString();
	}

	/** A question while it is being read. */
	private static final class Question {

		private final Integer number;

		private final StringBuilder stem = new StringBuilder();

		private final List<ImportedOption> options = new ArrayList<>();

		private final StringBuilder explanation = new StringBuilder();

		private boolean inExplanation;

		private final List<String> problems = new ArrayList<>();

		private int labelCount;

		private Question(int number) {
			this.number = number < 0 ? null : number;
		}
	}
}
