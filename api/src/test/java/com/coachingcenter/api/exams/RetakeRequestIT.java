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
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
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
 * A student asks for another written retake. Only the Master Admin or the System Admin can decide. Approval makes the grant and
 * the student may sit it; a decline carries a note the student sees. A waiting request blocks a second one.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, ArchivedAndRetakeMarkingIT.ClockConfig.class })
class RetakeRequestIT extends PostgresIntegrationTest {

	private static final Instant BEFORE = Instant.parse("2026-10-20T04:00:00Z");

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
	void aStudentRequestsAndTheMasterAdminApprovesOrDeclines() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID category = fx.category(staff, "Retake requests");
		UUID exam = fx.createStandalone(staff, category, "WRITTEN", "Retake request exam", AuthoringFixture.LIVE_AT, 40, null);
		fx.uploadPdf(staff, exam);
		fx.publishNew(master, exam);

		UUID approved = fx.seed("STUDENT");
		UUID declined = fx.seed("STUDENT");
		grant(master, approved, exam);
		grant(master, declined, exam);
		String approvedToken = fx.token(approved);
		String declinedToken = fx.token(declined);

		// Both sit and submit the first script. Submitting posts it, so the attempt is finished at once.
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		take(approvedToken, exam);
		take(declinedToken, exam);
		clock.set(Instant.parse("2026-10-20T06:20:00Z"));
		upload(approvedToken, exam);
		upload(declinedToken, exam);

		// A retake is asked for once the result is out: both first sheets are marked and published.
		for (UUID student : new UUID[] { approved, declined }) {
			UUID sheet = new JdbcTemplate(dataSource).queryForObject(
				"select id from live_exam_participants where exam_id = ? and student_id = ? and attempt_no = 1 and deleted_at is null",
				UUID.class, exam, student);
			mockMvc.perform(multipart("/api/v1/live-exam-participants/" + sheet + "/evaluation")
				.file(new MockMultipartFile("file", "mark.pdf", "application/pdf", realPdf()))
				.param("marks", "6")
				.header("Authorization", "Bearer " + master))
				.andExpect(status().isOk());
			mockMvc.perform(post("/api/v1/live-exam-participants/" + sheet + "/publish").header("Authorization", "Bearer " + master))
				.andExpect(status().isOk());
		}

		// Before a request: the student can ask.
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/retake-request").header("Authorization", "Bearer " + approvedToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.canRequest").value(true))
			.andExpect(jsonPath("$.request").doesNotExist());

		// The student asks, with a reason. A second request while this one waits is refused.
		JsonNode created = body(mockMvc.perform(post("/api/v1/live-exams/" + exam + "/retake-request")
			.header("Authorization", "Bearer " + approvedToken)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\":\"Lost the first sheet\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("PENDING")));
		UUID requestId = UUID.fromString(created.get("id").asText());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/retake-request").header("Authorization", "Bearer " + approvedToken)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isConflict());
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/retake-request").header("Authorization", "Bearer " + approvedToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.canRequest").value(false))
			.andExpect(jsonPath("$.blockedReason").value("PENDING"));

		// The centre's list is for the Master Admin and the System Admin only.
		mockMvc.perform(get("/api/v1/retake-requests").header("Authorization", "Bearer " + approvedToken))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/retake-requests").header("Authorization", "Bearer " + fx.staff()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].studentId").value(approved.toString()));

		// The Master Admin approves: the grant is made, so the student may sit the retake.
		mockMvc.perform(post("/api/v1/retake-requests/" + requestId + "/approve").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("APPROVED"));
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/retake-request").header("Authorization", "Bearer " + approvedToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.canRequest").value(false))
			.andExpect(jsonPath("$.blockedReason").value("GRANT_ACTIVE"));
		assertThat(countNotices("RETAKE_AVAILABLE", approved)).isEqualTo(1);

		// The other student is declined with a note, which they see and which tells them they can ask again.
		JsonNode second = body(mockMvc.perform(post("/api/v1/live-exams/" + exam + "/retake-request")
			.header("Authorization", "Bearer " + declinedToken)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isCreated()));
		UUID secondId = UUID.fromString(second.get("id").asText());
		mockMvc.perform(post("/api/v1/retake-requests/" + secondId + "/decline").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"note\":\"Please bring the paper to the office first\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DECLINED"));
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/retake-request").header("Authorization", "Bearer " + declinedToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.request.status").value("DECLINED"))
			.andExpect(jsonPath("$.request.decisionNote").value("Please bring the paper to the office first"))
			.andExpect(jsonPath("$.canRequest").value(true));
		assertThat(countNotices("RETAKE_DECLINED", declined)).isEqualTo(1);

		// A decided request cannot be decided again.
		mockMvc.perform(post("/api/v1/retake-requests/" + secondId + "/approve").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isConflict());
	}

	private long countNotices(String kind, UUID student) {
		return new JdbcTemplate(dataSource).queryForObject(
				"select count(*) from student_notices where kind = ? and user_id = ?", Long.class, kind, student);
	}

	private void grant(String master, UUID student, UUID exam) throws Exception {
		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"studentId":"%s","scope":"EXAM","moduleId":null,"examId":"%s","reason":"Written grant"}
					""".formatted(student, exam)))
			.andExpect(status().isCreated());
	}

	private void take(String token, UUID exam) throws Exception {
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isOk());
	}

	private void upload(String token, UUID exam) throws Exception {
		mockMvc.perform(multipart("/api/v1/live-exams/" + exam + "/answer-pdf")
			.file(new MockMultipartFile("file", "answer.pdf", "application/pdf", realPdf()))
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isNoContent());
	}

	private JsonNode body(org.springframework.test.web.servlet.ResultActions request) throws Exception {
		return json.readTree(request.andReturn().getResponse().getContentAsString());
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
				stream.showText("Written script");
				stream.endText();
			}
			document.save(output);
			return output.toByteArray();
		}
	}

}
