package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

import com.coachingcenter.api.exams.McqDocxParser.ImportProblem;
import com.coachingcenter.api.exams.McqDocxParser.ImportResult;

/** The Word reader, on files built in memory: no client file is copied into the repository. */
class McqDocxParserTest {

	@Test
	void readsTheHeaderQuestionsOptionsAndBoldAnswers() {
		ImportResult result = McqDocxParser.parse(docx(
				p(r("পূর্ণমান: ১০০   সময়: ৫৫ মিনিট")),
				p(r("১. কোন আদালতের আসামিকে মৃত্যুদণ্ড দেওয়ার ক্ষমতা নেই?")),
				p(r("ক) হাইকোর্ট ডিভিশন   খ) "), b("যুগ্ম দায়রা জজ"), r("   গ) দায়রা জজ   ঘ) অতিরিক্ত দায়রা জজ")),
				p(r("২. দ্বিতীয় প্রশ্ন?")),
				p(r("ক) "), b("প্রথম")), p(r("খ) দ্বিতীয়")), p(r("গ) তৃতীয়")), p(r("ঘ) চতুর্থ"))));

		assertThat(result.blocked()).isFalse();
		assertThat(result.header()).containsExactly("পূর্ণমান: ১০০   সময়: ৫৫ মিনিট");
		assertThat(result.durationMinutes()).isEqualTo(55);
		assertThat(result.fullMarks()).isEqualTo(100);
		assertThat(result.questions()).hasSize(2);
		assertThat(result.questions().get(0).stem()).isEqualTo("কোন আদালতের আসামিকে মৃত্যুদণ্ড দেওয়ার ক্ষমতা নেই?");
		assertThat(result.questions().get(0).options()).extracting(McqDocxParser.ImportedOption::text)
			.containsExactly("হাইকোর্ট ডিভিশন", "যুগ্ম দায়রা জজ", "দায়রা জজ", "অতিরিক্ত দায়রা জজ");
		assertThat(result.questions().get(0).options()).extracting(McqDocxParser.ImportedOption::correct)
			.containsExactly(false, true, false, false);
		assertThat(result.questions().get(1).options()).extracting(McqDocxParser.ImportedOption::correct)
			.containsExactly(true, false, false, false);
		// Two questions against a header of 100 marks: the import goes ahead, with a warning.
		assertThat(result.problems()).isEmpty();
		assertThat(result.warnings()).containsExactly("FULL_MARKS_DIFFER");
	}

	@Test
	void readsTheExplanationAfterTheOptions() {
		ImportResult result = McqDocxParser.parse(docx(
				p(r("১. প্রশ্ন এক?")), p(b("ক) এক")), p(r("খ) দুই")),
				p(r("ব্যাখ্যা: প্রথম লাইন।")), p(r("দ্বিতীয় লাইন।")),
				p(r("২. প্রশ্ন দুই?")), p(r("ক) এক")), p(b("খ) দুই")),
				p(r("৩. প্রশ্ন তিন?")), p(r("ক) এক")), p(b("খ) দুই")), p(r("Explanation: In English."))));

		assertThat(result.problems()).isEmpty();
		assertThat(result.questions()).extracting(McqDocxParser.ImportedQuestion::explanation)
			.containsExactly("প্রথম লাইন। দ্বিতীয় লাইন।", null, "In English.");
	}

	@Test
	void anExplanationIsNotReadAsOptionsOrAsTheBoldAnswer() {
		ImportResult result = McqDocxParser.parse(docx(
				p(r("১. প্রশ্ন এক?")), p(r("ক) এক")), p(b("খ) দুই")),
				p(r("ব্যাখ্যা: "), b("সঠিক উত্তর খ) দুই, ক) নয়।"))));

		assertThat(result.problems()).isEmpty();
		assertThat(result.questions().get(0).options()).extracting(McqDocxParser.ImportedOption::correct)
			.containsExactly(false, true);
		assertThat(result.questions().get(0).explanation()).isEqualTo("সঠিক উত্তর খ) দুই, ক) নয়।");
	}

	@Test
	void aColonlessWordThatStartsWithExplanationStaysInTheStem() {
		ImportResult result = McqDocxParser.parse(docx(
				p(r("১. প্রশ্ন")), p(r("ব্যাখ্যা করুন?")), p(r("ক) এক")), p(b("খ) দুই"))));

		assertThat(result.problems()).isEmpty();
		assertThat(result.questions().get(0).stem()).isEqualTo("প্রশ্ন ব্যাখ্যা করুন?");
		assertThat(result.questions().get(0).explanation()).isNull();
	}

	@Test
	void anExplanationBeforeTheOptionsLeavesTheQuestionWithoutOptions() {
		ImportResult result = McqDocxParser.parse(docx(
				p(r("১. প্রশ্ন এক?")), p(r("ব্যাখ্যা: আগে লেখা।")), p(r("ক) এক")), p(b("খ) দুই"))));

		assertThat(result.problems()).contains(new ImportProblem(1, "FEW_OPTIONS"));
	}

	@Test
	void aQuestionWithNoBoldOptionIsBlocked() {
		ImportResult result = McqDocxParser.parse(docx(
				p(r("১. প্রশ্ন এক?")), p(r("ক) এক")), p(r("খ) দুই")),
				p(r("২. প্রশ্ন দুই?")), p(r("ক) এক")), p(r("খ) "), b("দুই"))));

		assertThat(result.blocked()).isTrue();
		assertThat(result.problems()).containsExactly(new ImportProblem(1, "NO_BOLD_OPTION"));
	}

	@Test
	void aQuestionWithTwoBoldOptionsIsBlocked() {
		ImportResult result = McqDocxParser.parse(docx(
				p(r("১. প্রশ্ন?")), p(b("ক) এক")), p(b("খ) দুই"))));

		assertThat(result.problems()).contains(new ImportProblem(1, "MANY_BOLD_OPTIONS"));
	}

	@Test
	void aPartlyBoldOptionIsBlocked() {
		ImportResult result = McqDocxParser.parse(docx(
				p(r("১. প্রশ্ন?")), p(r("ক) "), b("হাই"), r("কোর্ট ডিভিশন")), p(r("খ) দুই"))));

		assertThat(result.problems()).contains(new ImportProblem(1, "PARTIAL_BOLD"));
	}

	@Test
	void aGapInNumberingIsBlocked() {
		ImportResult result = McqDocxParser.parse(docx(
				p(r("১. এক?")), p(b("ক) হ্যাঁ")), p(r("খ) না")),
				p(r("৩. তিন?")), p(b("ক) হ্যাঁ")), p(r("খ) না"))));

		assertThat(result.problems()).contains(new ImportProblem(2, "NUMBER_ORDER"));
	}

	@Test
	void optionsOutOfOrderAreBlocked() {
		ImportResult result = McqDocxParser.parse(docx(
				p(r("১. এক?")), p(r("খ) দুই")), p(b("ক) এক"))));

		assertThat(result.problems()).contains(new ImportProblem(1, "OPTION_ORDER"));
	}

	@Test
	void aFileWithNoQuestionsIsBlocked() {
		ImportResult result = McqDocxParser.parse(docx(p(r("পূর্ণমান: ১০০"))));

		assertThat(result.problems()).containsExactly(new ImportProblem(null, "NO_QUESTIONS"));
	}

	@Test
	void anythingThatIsNotAWordFileIsRefused() {
		ImportResult result = McqDocxParser.parse(new byte[] { 1, 2, 3, 4 });

		assertThat(result.problems()).containsExactly(new ImportProblem(null, "NOT_A_DOCX"));
		assertThat(result.questions()).isEmpty();
	}

	@Test
	void textOfTrackedDeletionsIsNotRead() {
		ImportResult result = McqDocxParser.parse(docx(
				p(r("১. প্রশ্ন"), "<w:del><w:r><w:delText>মুছে ফেলা</w:delText></w:r></w:del>", r(" কী?")),
				p(b("ক) হ্যাঁ")), p(r("খ) না"))));

		assertThat(result.questions().get(0).stem()).isEqualTo("প্রশ্ন কী?");
		assertThat(result.problems()).isEmpty();
	}

	/** One paragraph of the document, from its runs. */
	private static String p(String... runs) {
		return "<w:p>" + String.join("", runs) + "</w:p>";
	}

	private static String r(String text) {
		return "<w:r><w:t xml:space=\"preserve\">" + text + "</w:t></w:r>";
	}

	private static String b(String text) {
		return "<w:r><w:rPr><w:b/></w:rPr><w:t xml:space=\"preserve\">" + text + "</w:t></w:r>";
	}

	/** A minimal .docx: a zip holding only {@code word/document.xml}, which is all the reader needs. */
	private static byte[] docx(String... paragraphs) {
		String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
				+ "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>"
				+ String.join("", List.of(paragraphs)) + "</w:body></w:document>";
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
				zip.putNextEntry(new ZipEntry("word/document.xml"));
				zip.write(xml.getBytes(StandardCharsets.UTF_8));
				zip.closeEntry();
			}
			return bytes.toByteArray();
		}
		catch (java.io.IOException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
