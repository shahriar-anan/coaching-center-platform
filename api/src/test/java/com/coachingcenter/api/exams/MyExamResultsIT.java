package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The student's own list of sat exams (GET /api/v1/me/exam-results). Two standalone exams (no package) reached through EXAM
 * grants: the student sees the ones they sat, newest first, and never another student's sitting. A written script's score
 * stays hidden until it is published. The window is 12:00 to 12:40 Dhaka (06:00 to 06:40 UTC).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, MyExamResultsIT.ClockConfig.class })
class MyExamResultsIT extends PostgresIntegrationTest {

	private static final Instant BEFORE = Instant.parse("2026-10-20T04:00:00Z");

	private static final Instant WRITTEN_SUBMITTED = Instant.parse("2026-10-20T06:20:00Z");

	private static final Instant AFTER_DEADLINES = Instant.parse("2026-10-20T07:10:00Z");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Autowired
	private MutableClock clock;

	private AuthoringFixture fx;

	@BeforeEach
	void setUp() {
		clock.set(BEFORE);
		fx = new AuthoringFixture(mockMvc, dataSource, passwords, json);
	}

	@Test
	void aStudentSeesTheirOwnStandaloneSittingsAndAWrittenScoreWaitsForPublishing() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID category = fx.category(staff, "My results category");

		UUID mcq = fx.createStandalone(staff, category, "MCQ", "My results MCQ", AuthoringFixture.LIVE_AT, 40, null);
		fx.question(staff, mcq, "Two plus two", 2);
		fx.publishNew(master, mcq);

		UUID written = fx.createStandalone(staff, category, "WRITTEN", "My results written", AuthoringFixture.LIVE_AT, 40, null);
		fx.uploadPdf(staff, written);
		fx.publishNew(master, written);

		UUID other = fx.createStandalone(staff, category, "MCQ", "My results other", AuthoringFixture.LIVE_AT, 40, null);
		fx.question(staff, other, "Three plus three", 2);
		fx.publishNew(master, other);

		UUID student = fx.seed("STUDENT");
		UUID stranger = fx.seed("STUDENT");
		grant(master, student, mcq);
		grant(master, student, written);
		grant(master, stranger, other);
		String studentToken = fx.token(student);
		String strangerToken = fx.token(stranger);

		// The student sits the MCQ and submits it, then starts and uploads the written script. The stranger sits their exam.
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		sit(studentToken, mcq);
		clock.set(Instant.parse("2026-10-20T06:12:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + mcq + "/submit").header("Authorization", "Bearer " + studentToken))
			.andExpect(status().isOk());
		clock.set(Instant.parse("2026-10-20T06:15:00Z"));
		sit(studentToken, written);
		mockMvc.perform(multipart("/api/v1/live-exams/" + written + "/answer-pdf")
			.file(new MockMultipartFile("file", "answer.pdf", "application/pdf", realPdf()))
			.header("Authorization", "Bearer " + studentToken))
			.andExpect(status().isNoContent());
		clock.set(Instant.parse("2026-10-20T06:16:00Z"));
		sit(strangerToken, other);

		// Before the written deadline: the script is in, the mark is not, and a mark already in the row stays hidden.
		clock.set(WRITTEN_SUBMITTED);
		new JdbcTemplate(dataSource).update("update live_exam_participants set score = 7, passed = true where exam_id = ? and student_id = ?",
				written, student);
		JsonNode midway = list(studentToken, "examType", "WRITTEN");
		JsonNode midwayRow = midway.get("content").get(0);
		assertThat(midwayRow.get("status").asText()).isEqualTo("SUBMITTED");
		assertThat(midwayRow.get("resultVisible").asBoolean()).isFalse();
		assertNoScore(midwayRow);

		// After both deadlines: the MCQ is posted and shown, the written script waits for marks, and the stranger's sitting is absent.
		clock.set(AFTER_DEADLINES);
		JsonNode all = list(studentToken);
		assertThat(all.get("totalElements").asInt()).isEqualTo(2);
		JsonNode writtenRow = all.get("content").get(0);
		JsonNode mcqRow = all.get("content").get(1);
		assertThat(UUID.fromString(writtenRow.get("examId").asText())).isEqualTo(written);
		assertThat(UUID.fromString(mcqRow.get("examId").asText())).isEqualTo(mcq);
		assertThat(writtenRow.get("status").asText()).isEqualTo("AWAITING_RESULT");
		assertThat(writtenRow.get("resultVisible").asBoolean()).isFalse();
		assertNoScore(writtenRow);
		assertThat(mcqRow.get("status").asText()).isEqualTo("RESULT_READY");
		assertThat(mcqRow.get("resultVisible").asBoolean()).isTrue();
		assertThat(mcqRow.get("score").asInt()).isZero();
		assertThat(mcqRow.get("maximum").asInt()).isEqualTo(2);
		assertThat(mcqRow.get("passed").asBoolean()).isFalse();
		assertThat(mcqRow.get("kind").asText()).isEqualTo("LIVE");
		assertThat(mcqRow.get("attempts").asInt()).isEqualTo(1);
		assertThat(mcqRow.get("examType").asText()).isEqualTo("MCQ");
		assertThat(mcqRow.get("titleEn").asText()).isEqualTo("My results MCQ");
		assertThat(mcqRow.hasNonNull("moduleId")).isFalse();
		for (JsonNode row : all.get("content")) {
			assertThat(row.get("examId").asText()).isNotEqualTo(other.toString());
		}

		// Staff publish the written mark. The score then shows, and the student's type filter keeps the written row.
		new JdbcTemplate(dataSource).update("update live_exam_participants set published_at = ? where exam_id = ? and student_id = ?",
				Timestamp.from(AFTER_DEADLINES), written, student);
		JsonNode published = list(studentToken, "examType", "WRITTEN");
		assertThat(published.get("totalElements").asInt()).isEqualTo(1);
		JsonNode publishedRow = published.get("content").get(0);
		assertThat(publishedRow.get("resultVisible").asBoolean()).isTrue();
		assertThat(publishedRow.get("status").asText()).isEqualTo("RESULT_READY");
		assertThat(publishedRow.get("score").asInt()).isEqualTo(7);
		assertThat(publishedRow.get("passed").asBoolean()).isTrue();

		// The stranger sees only their own sitting.
		JsonNode strangerList = list(strangerToken);
		assertThat(strangerList.get("totalElements").asInt()).isEqualTo(1);
		assertThat(UUID.fromString(strangerList.get("content").get(0).get("examId").asText())).isEqualTo(other);
	}

	@Test
	void aStudentReadsThePostedResultOfAnArchivedExamButNotTheExamItself() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID category = fx.category(staff, "Archived result category");
		UUID mcq = fx.createStandalone(staff, category, "MCQ", "Archived result MCQ", AuthoringFixture.LIVE_AT, 40, null);
		fx.question(staff, mcq, "Two plus two", 2);
		fx.publishNew(master, mcq);
		UUID student = fx.seed("STUDENT");
		grant(master, student, mcq);
		String token = fx.token(student);

		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		sit(token, mcq);
		clock.set(Instant.parse("2026-10-20T06:12:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + mcq + "/submit").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());

		// Staff archive the exam: it leaves the student website, so the exam read answers 404 while the result still reads.
		clock.set(AFTER_DEADLINES);
		mockMvc.perform(post("/api/v1/live-exams/" + mcq + "/archive").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/live-exams/" + mcq).header("Authorization", "Bearer " + token))
			.andExpect(status().isNotFound());
		JsonNode result = bodyOf(mockMvc.perform(get("/api/v1/live-exams/" + mcq + "/result").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString());
		assertThat(result.get("score").asInt()).isZero();
		assertThat(result.get("maximum").asInt()).isEqualTo(2);
		assertThat(result.get("examTitleEn").asText()).isEqualTo("Archived result MCQ");
		assertThat(result.get("examType").asText()).isEqualTo("MCQ");
	}

	@Test
	void aStudentReadsTheirOwnAnswerScriptBeforeMarkingAndNobodyElsesGetsIt() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID category = fx.category(staff, "Own answer category");
		UUID mcq = fx.createStandalone(staff, category, "MCQ", "Own answer MCQ", AuthoringFixture.LIVE_AT, 40, null);
		fx.question(staff, mcq, "Two plus two", 2);
		fx.publishNew(master, mcq);
		UUID written = fx.createStandalone(staff, category, "WRITTEN", "Own answer written", AuthoringFixture.LIVE_AT, 40, null);
		fx.uploadPdf(staff, written);
		fx.publishNew(master, written);
		UUID student = fx.seed("STUDENT");
		UUID stranger = fx.seed("STUDENT");
		grant(master, student, mcq);
		grant(master, student, written);
		String token = fx.token(student);
		String strangerToken = fx.token(stranger);

		// The student sits the MCQ and submits it, then uploads the written script. The stranger never sits the written exam.
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		sit(token, mcq);
		clock.set(Instant.parse("2026-10-20T06:12:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + mcq + "/submit").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		clock.set(Instant.parse("2026-10-20T06:15:00Z"));
		sit(token, written);
		mockMvc.perform(multipart("/api/v1/live-exams/" + written + "/answer-pdf")
			.file(new MockMultipartFile("file", "answer.pdf", "application/pdf", realPdf()))
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isNoContent());

		// Before marking and publish, the student gets the script they uploaded, as a PDF.
		clock.set(WRITTEN_SUBMITTED);
		byte[] answer = mockMvc.perform(get("/api/v1/live-exams/" + written + "/answer-pdf").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_PDF))
			.andReturn()
			.getResponse()
			.getContentAsByteArray();
		assertThat(new String(answer, StandardCharsets.ISO_8859_1)).startsWith("%PDF");
		mockMvc.perform(get("/api/v1/live-exams/" + written + "/answer-pdf").param("attempt", "1")
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/live-exams/" + written + "/answer-pdf").param("attempt", "9")
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isNotFound());

		// The result says which attempts have a script: the written one does, the MCQ does not. A result opens once the window has closed.
		clock.set(AFTER_DEADLINES);
		assertThat(bodyOf(mockMvc.perform(get("/api/v1/live-exams/" + written + "/result").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString()).get("answerPdf").asBoolean()).isTrue();
		assertThat(bodyOf(mockMvc.perform(get("/api/v1/live-exams/" + mcq + "/result").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString()).get("answerPdf").asBoolean()).isFalse();

		// The MCQ sitting has no script, and the stranger has no sitting on the written exam: both get 404.
		mockMvc.perform(get("/api/v1/live-exams/" + mcq + "/answer-pdf").header("Authorization", "Bearer " + token))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/live-exams/" + written + "/answer-pdf").header("Authorization", "Bearer " + strangerToken))
			.andExpect(status().isNotFound());
	}

	/** Right after an early submit the score row is already visible (resultVisible), and the key shows at once too. */
	@Test
	void aSubmittedMcqShowsItsScoreRowAndItsKeyAtOnce() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID category = fx.category(staff, "Early score category");
		UUID mcq = fx.createStandalone(staff, category, "MCQ", "Early score MCQ", AuthoringFixture.LIVE_AT, 40, null);
		fx.question(staff, mcq, "Two plus two", 2);
		fx.publishNew(master, mcq);
		UUID student = fx.seed("STUDENT");
		grant(master, student, mcq);
		String token = fx.token(student);

		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		sit(token, mcq);
		clock.set(Instant.parse("2026-10-20T06:12:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + mcq + "/submit").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());

		clock.set(Instant.parse("2026-10-20T06:13:00Z"));
		JsonNode row = list(token).get("content").get(0);
		assertThat(UUID.fromString(row.get("examId").asText())).isEqualTo(mcq);
		assertThat(row.get("status").asText()).isEqualTo("SUBMITTED");
		assertThat(row.get("resultVisible").asBoolean()).isTrue();
		assertThat(row.hasNonNull("score")).as("score").isTrue();
		assertThat(row.get("maximum").asInt()).isEqualTo(2);

		JsonNode result = bodyOf(mockMvc.perform(get("/api/v1/live-exams/" + mcq + "/result").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString());
		assertThat(result.get("score").asInt()).isZero();
		assertThat(result.get("questions").get(0).get("stem").asText()).isEqualTo("Two plus two");
		assertThat(result.has("reviewOpensAt")).as("reviewOpensAt is always absent now").isFalse();
	}

	private void grant(String master, UUID student, UUID exam) throws Exception {
		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"studentId":"%s","scope":"EXAM","moduleId":null,"examId":"%s","reason":"Standalone grant"}
					""".formatted(student, exam)))
			.andExpect(status().isCreated());
	}

	private void sit(String token, UUID exam) throws Exception {
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isOk());
	}

	/** The student's list, with the given name and value query pairs. */
	private JsonNode list(String token, String... pairs) throws Exception {
		var request = get("/api/v1/me/exam-results").param("size", "20").header("Authorization", "Bearer " + token);
		for (int i = 0; i < pairs.length; i += 2) {
			request.param(pairs[i], pairs[i + 1]);
		}
		return bodyOf(mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}

	private JsonNode bodyOf(String body) throws Exception {
		return json.readTree(body);
	}

	/** A hidden result shows neither a score nor a pass flag, so no mark can leak. */
	private static void assertNoScore(JsonNode row) {
		assertThat(row.hasNonNull("score")).as("score").isFalse();
		assertThat(row.hasNonNull("passed")).as("passed").isFalse();
	}

	/** A PDF that PDFBox can parse, as the sitting compresses every upload. */
	private static byte[] realPdf() throws Exception {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			PDPage page = new PDPage();
			document.addPage(page);
			try (PDPageContentStream stream = new PDPageContentStream(document, page, AppendMode.OVERWRITE, false)) {
				stream.beginText();
				stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
				stream.newLineAtOffset(40, 700);
				stream.showText("Answer script");
				stream.endText();
			}
			document.save(output);
			return output.toByteArray();
		}
	}

	@TestConfiguration
	static class ClockConfig {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(Instant.parse("2026-10-20T04:00:00Z"));
		}

	}

}
