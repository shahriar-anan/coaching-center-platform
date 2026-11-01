package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The marking feed follows the same exam rule as the counts (published exams only), and a paper whose student has no
 * profile still lists, with an empty name.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, MarkingFeedRulesIT.ClockConfig.class })
class MarkingFeedRulesIT extends PostgresIntegrationTest {

	private static final Instant BEFORE = Instant.parse("2026-10-20T04:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private LiveExamDeadlineJob deadlineJob;

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
	void theFeedAndTheCountsListPapersOfPublishedExamsOnly() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID category = fx.category(staff, "Status rule");
		UUID published = written(staff, master, category, "Status published").exam();
		UUID draft = written(staff, master, category, "Status draft").exam();
		UUID archived = written(staff, master, category, "Status archived").exam();
		setStatus(draft, "DRAFT");
		setStatus(archived, "ARCHIVED");

		assertThat(examIds(feed(master, "categoryId", category.toString()))).containsOnly(published);
		assertThat(examIds(feed(master, "examId", draft.toString()))).isEmpty();
		assertThat(examIds(feed(master, "examId", archived.toString()))).isEmpty();
		assertThat(examIds(participation(master, "categoryId", category.toString()))).containsOnly(published);
	}

	@Test
	void aPaperWhoseStudentHasNoProfileStillListsWithAnEmptyName() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID category = fx.category(staff, "No profile");
		Sitting sitting = written(staff, master, category, "No profile exam");
		fx.jdbc().update("DELETE FROM student_profiles WHERE user_id = ?", sitting.student());

		JsonNode page = feed(master, "examId", sitting.exam().toString());
		assertThat(page.get("totalElements").asInt()).isEqualTo(1);
		assertThat(page.get("content").get(0).get("studentName").asText()).isEmpty();
	}

	/** A published written exam with one submitted paper, posted by the deadline job. */
	private Sitting written(String staff, String master, UUID category, String title) throws Exception {
		UUID exam = fx.createStandalone(staff, category, "WRITTEN", title, AuthoringFixture.LIVE_AT, 40, null);
		fx.uploadPdf(staff, exam);
		fx.publishNew(master, exam);
		UUID student = fx.seed("STUDENT");
		grant(master, student, exam);
		String token = fx.token(student);
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		take(token, exam);
		upload(token, exam);
		// A sitting is posted as submitted by the deadline job, once its deadline has passed.
		clock.set(Instant.parse("2026-10-20T06:55:00Z"));
		deadlineJob.settleDue();
		clock.set(BEFORE);
		return new Sitting(exam, student);
	}

	private void setStatus(UUID exam, String status) {
		fx.jdbc().update("UPDATE live_exams SET status = ? WHERE id = ?", status, exam);
	}

	private void grant(String master, UUID student, UUID exam) throws Exception {
		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType("application/json")
			.content("""
					{"studentId":"%s","scope":"EXAM","moduleId":null,"examId":"%s","reason":"Status grant"}
					""".formatted(student, exam)))
			.andExpect(status().isCreated());
	}

	private void take(String token, UUID exam) throws Exception {
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take").header("Authorization", "Bearer " + token)
			.contentType("application/json")
			.content("{}"))
			.andExpect(status().isOk());
	}

	private void upload(String token, UUID exam) throws Exception {
		mockMvc.perform(multipart("/api/v1/live-exams/" + exam + "/answer-pdf")
			.file(new MockMultipartFile("file", "answer.pdf", "application/pdf", realPdf()))
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isNoContent());
	}

	private JsonNode feed(String token, String name, String value) throws Exception {
		return bodyOf(get("/api/v1/live-exam-submissions").param(name, value).header("Authorization", "Bearer " + token));
	}

	private JsonNode participation(String token, String name, String value) throws Exception {
		return bodyOf(get("/api/v1/live-exam-participation").param(name, value).header("Authorization", "Bearer " + token));
	}

	private JsonNode bodyOf(MockHttpServletRequestBuilder request) throws Exception {
		MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
		return json.readTree(result.getResponse().getContentAsString());
	}

	/** The exam ids on a page. A feed row carries examId; a participation row carries the same field. */
	private static Set<UUID> examIds(JsonNode page) {
		Set<UUID> ids = new HashSet<>();
		for (JsonNode row : page.get("content")) {
			ids.add(UUID.fromString(row.get("examId").asText()));
		}
		return ids;
	}

	/** A PDF that PDFBox can parse, as the sitting and the marking compress every upload. */
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

	private record Sitting(UUID exam, UUID student) {
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
