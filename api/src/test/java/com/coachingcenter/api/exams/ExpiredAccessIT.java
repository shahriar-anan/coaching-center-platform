package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Expired access (NR-01, A2'): once a student's access to an exam has ended, a new attempt and the question paper are
 * refused, while the results, the attempts and the marked PDF stay readable. The exams run 06:00 to 06:40 UTC (12:00 to
 * 12:40 in Dhaka) with 30 access days, so access ends on 19 November 2026 at 06:40 UTC.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, ExpiredAccessIT.ClockConfig.class })
class ExpiredAccessIT extends PostgresIntegrationTest {

	/** Before the window. The grants are made now, so their 30 days run from the window end. */
	private static final Instant BEFORE_WINDOW = Instant.parse("2026-10-20T04:00:00Z");

	private static final String NOON = "2026-10-20T12:00:00+06:00";

	private static final Instant SITTING = Instant.parse("2026-10-20T06:10:00Z");

	private static final Instant SUBMITTED = Instant.parse("2026-10-20T06:50:00Z");

	private static final Instant STILL_ACCESS = Instant.parse("2026-10-25T12:00:00Z");

	private static final Instant ACCESS_ENDS = Instant.parse("2026-11-19T06:40:00Z");

	private static final Instant AFTER_EXPIRY = Instant.parse("2026-11-20T00:00:00Z");

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
		clock.set(BEFORE_WINDOW);
		fx = new AuthoringFixture(mockMvc, dataSource, passwords, json);
	}

	@Test
	void anEndedAccessRefusesANewAttemptAndThePaperButKeepsTheResultsAndTheMarkedPdf() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID category = fx.category(staff, "Expired access");
		// The written retake is granted by staff while access still runs, and then used after it has ended.
		// Access days are a commercial field, so only the Master Admin creates these exams (EXAM_PRICE).
		UUID mcq = fx.createStandalone(master, category, "MCQ", "Expiring quiz", NOON, 40, "\"accessDays\":30");
		fx.question(staff, mcq, "Two plus two", 2);
		fx.publishNew(master, mcq);
		UUID written = fx.createStandalone(master, category, "WRITTEN", "Expiring paper", NOON, 40, "\"accessDays\":30");
		fx.uploadPdf(staff, written);
		fx.publishNew(master, written);

		UUID studentId = fx.seed("STUDENT");
		String student = fx.token(studentId);
		UUID neverSat = fx.seed("STUDENT");
		String idle = fx.token(neverSat);
		for (UUID exam : new UUID[] { mcq, written }) {
			grantExam(master, studentId, exam);
		}
		grantExam(master, neverSat, mcq);

		clock.set(SITTING);
		UUID mcqAttempt = take(student, mcq);
		UUID writtenAttempt = take(student, written);
		uploadAnswer(student, written);
		clock.set(Instant.parse("2026-10-20T06:20:00Z"));
		submit(student, mcq);

		clock.set(SUBMITTED);
		submit(student, written);
		evaluate(master, writtenAttempt, "marked");
		mockMvc.perform(post("/api/v1/live-exam-participants/" + writtenAttempt + "/publish")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.published").value(true));

		clock.set(STILL_ACCESS);
		assertThat(expired(student, mcq)).as("access is still running").isFalse();
		mockMvc.perform(post("/api/v1/live-exams/" + written + "/retake-grants")
			.header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"studentId\":\"%s\",\"reason\":\"Lost the first sheet\"}".formatted(studentId)))
			.andExpect(status().isCreated());

		clock.set(AFTER_EXPIRY);
		// A new attempt is refused: the MCQ retake, and the written retake with its grant in hand.
		mockMvc.perform(post("/api/v1/live-exams/" + mcq + "/retake").header("Authorization", "Bearer " + student))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ACCESS_EXPIRED"));
		mockMvc.perform(post("/api/v1/live-exams/" + written + "/retake").header("Authorization", "Bearer " + student))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ACCESS_EXPIRED"));
		// A student who never sat cannot start either.
		mockMvc.perform(post("/api/v1/live-exams/" + mcq + "/take").header("Authorization", "Bearer " + idle)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ACCESS_EXPIRED"));

		// The results and the attempts stay readable.
		mockMvc.perform(get("/api/v1/live-exams/" + mcq + "/result").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.attemptNo").value(1))
			.andExpect(jsonPath("$.maximum").value(2));
		mockMvc.perform(get("/api/v1/live-exams/" + written + "/result").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.evaluatedPdf").value(true))
			.andExpect(jsonPath("$.passed").value(true));
		mockMvc.perform(get("/api/v1/live-exams/" + mcq + "/attempts").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].id").value(mcqAttempt.toString()));
		mockMvc.perform(get("/api/v1/live-exams/" + written + "/attempts").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1));
		// The marked PDF stays readable after release.
		byte[] marked = mockMvc.perform(get("/api/v1/live-exams/" + written + "/evaluated-pdf")
			.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsByteArray();
		assertThat(new String(marked, java.nio.charset.StandardCharsets.US_ASCII)).startsWith("%PDF");

		// The question paper is not: the student's sitting is over, so there is nothing open to read.
		mockMvc.perform(get("/api/v1/live-exams/" + written + "/question-pdf").header("Authorization", "Bearer " + student))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/live-exams/" + mcq + "/question-pdf").header("Authorization", "Bearer " + student))
			.andExpect(status().isNotFound());

		// The access lists show the ended access.
		JsonNode mine = json.readTree(mockMvc.perform(get("/api/v1/me/live-exam-access")
			.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString());
		assertThat(mine.get("exams")).hasSize(2);
		for (JsonNode row : mine.get("exams")) {
			assertThat(row.get("expired").asBoolean()).as("expired %s", row.get("examId")).isTrue();
			assertThat(Instant.parse(row.get("endsAt").asText())).isEqualTo(ACCESS_ENDS);
		}
	}

	private boolean expired(String token, UUID exam) throws Exception {
		JsonNode mine = json.readTree(mockMvc.perform(get("/api/v1/me/live-exam-access").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString());
		for (JsonNode row : mine.get("exams")) {
			if (exam.toString().equals(row.get("examId").asText())) {
				return row.get("expired").asBoolean();
			}
		}
		throw new AssertionError("no access row for " + exam);
	}

	private void grantExam(String master, UUID student, UUID exam) throws Exception {
		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"studentId":"%s","scope":"EXAM","examId":"%s","reason":"Paid before the window"}
					""".formatted(student, exam)))
			.andExpect(status().isCreated());
	}

	private UUID take(String token, UUID exam) throws Exception {
		MvcResult taken = mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isOk())
			.andReturn();
		return UUID.fromString(json.readTree(taken.getResponse().getContentAsString()).get("id").asText());
	}

	private void uploadAnswer(String token, UUID exam) throws Exception {
		mockMvc.perform(multipart("/api/v1/live-exams/" + exam + "/answer-pdf")
			.file(new MockMultipartFile("file", "answer.pdf", "application/pdf", realPdf()))
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isNoContent());
	}

	private void submit(String token, UUID exam) throws Exception {
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/submit").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
	}

	private void evaluate(String master, UUID participant, String marker) throws Exception {
		mockMvc.perform(multipart("/api/v1/live-exam-participants/" + participant + "/evaluation")
			.file(new MockMultipartFile("file", "marked.pdf", "application/pdf", realPdf()))
			.param("marks", "7")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
	}

	/** A real PDF: a script upload is compressed, so a hand-made stub is refused as not a PDF. */
	private static byte[] realPdf() throws Exception {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			PDPage page = new PDPage();
			document.addPage(page);
			try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
				stream.beginText();
				stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
				stream.newLineAtOffset(40, 700);
				stream.showText("Written script");
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
			return new MutableClock(BEFORE_WINDOW);
		}

	}

}
