package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

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
 * The marking browser's category view: the feed and the participation rows read by a category and every category under
 * it, the instructor's own exams only, the instructor's name on a paper, and a bad query value answered as a 400.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, MarkingBrowserIT.ClockConfig.class })
class MarkingBrowserIT extends PostgresIntegrationTest {

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
	void theCategoryFeedTakesTheCategoryAndEveryCategoryUnderItAtAnyDepth() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID root = fx.category(staff, "Browser root");
		UUID child = subcategory(staff, root, "Browser child");
		UUID grandchild = subcategory(staff, child, "Browser grandchild");
		UUID other = fx.category(staff, "Browser other");
		UUID atRoot = written(staff, master, root, "At root");
		UUID inChild = written(staff, master, child, "In child");
		UUID inGrandchild = written(staff, master, grandchild, "In grandchild");
		UUID outside = written(staff, master, other, "Outside");

		assertThat(examIds(feed(master, "categoryId", root.toString()))).containsExactlyInAnyOrder(atRoot, inChild, inGrandchild);
		assertThat(examIds(feed(master, "categoryId", child.toString()))).containsExactlyInAnyOrder(inChild, inGrandchild);
		assertThat(examIds(feed(master, "categoryId", grandchild.toString()))).containsOnly(inGrandchild);
		assertThat(examIds(feed(master, "categoryId", other.toString()))).containsOnly(outside);
		assertThat(examIds(feed(master, "examId", inChild.toString()))).containsOnly(inChild);
		assertThat(examIds(feed(master, "categoryId", UUID.randomUUID().toString()))).isEmpty();
	}

	@Test
	void anInstructorSeesOnlyTheirOwnExamsInsideACategory() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID root = fx.category(staff, "Scoped root");
		UUID child = subcategory(staff, root, "Scoped child");
		UUID atRoot = written(staff, master, root, "Scoped at root");
		UUID inChild = written(staff, master, child, "Scoped in child");
		Marker marker = instructor(master, "Browser Marker");
		assign(master, inChild, marker.id());
		publishStaged(master, inChild);

		assertThat(examIds(feed(marker.token(), "categoryId", root.toString()))).containsOnly(inChild).doesNotContain(atRoot);
	}

	@Test
	void aFeedRowNamesTheExamsInstructorAndAPaperWithNoInstructorHasNoName() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID category = fx.category(staff, "Named instructor");
		UUID assigned = written(staff, master, category, "Named exam");
		UUID unassigned = written(staff, master, category, "Unnamed exam");
		Marker marker = instructor(master, "Named Marker");
		assign(master, assigned, marker.id());
		publishStaged(master, assigned);

		JsonNode rows = feed(master, "categoryId", category.toString());
		assertThat(rowFor(rows, assigned).get("instructorName").asText()).isEqualTo("Named Marker");
		assertThat(rowFor(rows, unassigned).has("instructorName")).isFalse();
	}

	@Test
	void aParticipationRowCarriesTheRootFirstCategoryPathOfANestedCategory() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID root = fx.category(staff, "Path root");
		UUID child = subcategory(staff, root, "Path child");
		UUID grandchild = subcategory(staff, child, "Path grandchild");
		UUID exam = written(staff, master, grandchild, "Path exam");

		JsonNode rows = participation(master, "categoryId", grandchild.toString());
		JsonNode row = null;
		for (JsonNode candidate : rows.get("content")) {
			if (exam.toString().equals(candidate.get("examId").asText())) {
				row = candidate;
			}
		}
		assertThat(row).isNotNull();
		JsonNode path = row.get("categoryPath");
		assertThat(path).hasSize(3);
		assertThat(path.get(0).get("id").asText()).isEqualTo(root.toString());
		assertThat(path.get(0).get("titleEn").asText()).isEqualTo("Path root");
		assertThat(path.get(1).get("id").asText()).isEqualTo(child.toString());
		assertThat(path.get(2).get("id").asText()).isEqualTo(grandchild.toString());
		assertThat(path.get(2).get("titleEn").asText()).isEqualTo("Path grandchild");
	}

	@Test
	void aBadQueryValueIsAValidationFailureNotAServerError() throws Exception {
		String master = fx.master();

		mockMvc.perform(get("/api/v1/live-exam-submissions").param("moduleId", "undefined")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.message").value("Parameter moduleId is invalid."));
		mockMvc.perform(get("/api/v1/live-exam-submissions").param("examId", "null")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		mockMvc.perform(get("/api/v1/live-exam-submissions").param("categoryId", "not-a-uuid")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Parameter categoryId is invalid."));
		mockMvc.perform(get("/api/v1/live-exam-participation").param("moduleId", "undefined")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	/** A written exam in the category, published, with one submitted paper. Returns the exam's id. */
	private UUID written(String staff, String master, UUID category, String title) throws Exception {
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
		return exam;
	}

	private UUID subcategory(String token, UUID parent, String title) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/live-exam-categories").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titleEn":"%s","titleBn":"উপবিভাগ","parentId":"%s"}
					""".formatted(title, parent)))
			.andExpect(status().isCreated())
			.andReturn();
		return fx.id(result);
	}

	private void grant(String master, UUID student, UUID exam) throws Exception {
		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"studentId":"%s","scope":"EXAM","moduleId":null,"examId":"%s","reason":"Browser grant"}
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

	/** Publishes what is staged on a published exam, as the Master Admin does after reading its change list (NR-01, E31). */
	private void publishStaged(String master, UUID exam) throws Exception {
		String revision = json.readTree(mockMvc.perform(get("/api/v1/live-exams/" + exam + "/pending-changes")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString()).get("revisionId").asText();
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"revisionId\":\"" + revision + "\"}"))
			.andExpect(status().isOk());
	}

	private void assign(String master, UUID exam, UUID instructor) throws Exception {
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/instructor").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"instructorId":"%s"}
					""".formatted(instructor)))
			.andExpect(status().is2xxSuccessful());
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

	private static Set<UUID> examIds(JsonNode page) {
		Set<UUID> ids = new HashSet<>();
		for (JsonNode row : page.get("content")) {
			ids.add(UUID.fromString(row.get("examId").asText()));
		}
		return ids;
	}

	private static JsonNode rowFor(JsonNode page, UUID exam) {
		for (JsonNode row : page.get("content")) {
			if (exam.toString().equals(row.get("examId").asText())) {
				return row;
			}
		}
		throw new AssertionError("No feed row for exam " + exam);
	}

	/** An instructor, created and activated the way the instructor tests do it, with a token. */
	private Marker instructor(String master, String name) throws Exception {
		String phone = "+8801" + ThreadLocalRandom.current().nextInt(100000000, 999999999);
		String email = "browser-" + UUID.randomUUID() + "@example.com";
		MvcResult created = mockMvc.perform(post("/api/v1/instructors").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"%s","phone":"%s","email":"%s"}
					""".formatted(name, phone, email)))
			.andExpect(status().isCreated())
			.andReturn();
		JsonNode body = json.readTree(created.getResponse().getContentAsString());
		mockMvc.perform(post("/api/v1/auth/activate").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"phone":"%s","code":"%s","password":"password1"}
					""".formatted(phone, body.get("activationCode").asText())))
			.andExpect(status().isNoContent());
		return new Marker(UUID.fromString(body.get("id").asText()), tokenFor(phone));
	}

	private String tokenFor(String phone) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"identifier":"%s","password":"password1"}
					""".formatted(phone)))
			.andExpect(status().isOk())
			.andReturn();
		String body = result.getResponse().getContentAsString();
		String marker = "\"accessToken\":\"";
		int start = body.indexOf(marker) + marker.length();
		return body.substring(start, body.indexOf('"', start));
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

	private record Marker(UUID id, String token) {
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
