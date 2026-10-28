package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * NR-01 final check, case 1: a standalone exam (no package) through its whole life. The exam is created in an open
 * category, belongs to no package, and is reached through an EXAM grant. The reads must tolerate a null package and a
 * null module title. The window is 12:00 to 12:40 Dhaka (06:00 to 06:40 UTC).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, StandaloneExamLifecycleIT.ClockConfig.class })
class StandaloneExamLifecycleIT extends PostgresIntegrationTest {

	private static final Instant BEFORE = Instant.parse("2026-10-20T04:00:00Z");

	private static final Instant SITTING = Instant.parse("2026-10-20T06:10:00Z");

	private static final Instant WINDOW_ENDED = Instant.parse("2026-10-20T06:45:00Z");

	private static final Instant WRITTEN_DEADLINE_PASSED = Instant.parse("2026-10-20T07:00:00Z");

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

	@Autowired
	private LiveExamDeadlineJob deadlineJob;

	private AuthoringFixture fx;

	@BeforeEach
	void setUp() {
		clock.set(BEFORE);
		fx = new AuthoringFixture(mockMvc, dataSource, passwords, json);
	}

	@Test
	void theStandaloneExamsAreListedWithNoPackageAndReachedThroughTheRoutineAndAccess() throws Exception {
		World world = world("Listed");
		clock.set(BEFORE);

		JsonNode listed = contentOf(mockMvc.perform(get("/api/v1/public/live-exam-categories/" + world.category() + "/exams")
			.param("size", "100")));
		assertThat(ids(listed)).containsExactlyInAnyOrder(world.mcq(), world.written());
		for (JsonNode row : listed) {
			assertThat(row.get("packages")).isNotNull().isEmpty();
		}
		mockMvc.perform(get("/api/v1/public/live-exams/" + world.mcq())).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/public/live-exams/" + world.written())).andExpect(status().isOk());
		assertThat(ids(contentOf(mockMvc.perform(get("/api/v1/public/live-exams").param("upcoming", "true")
			.param("size", "1000")))))
			.contains(world.mcq(), world.written());

		JsonNode access = bodyOf(mockMvc.perform(get("/api/v1/me/live-exam-access")
			.header("Authorization", "Bearer " + world.s1Token())));
		JsonNode mcqRow = rowFor(access, world.mcq());
		assertThat(mcqRow).isNotNull();
		assertThat(mcqRow.hasNonNull("moduleId")).isFalse();
		assertThat(rowFor(access, world.written())).isNotNull();
	}

	/**
	 * The routine (the student's list of exams) must show a standalone exam the student holds by an exam grant. Today the
	 * routine query only reaches an exam through a package, so the exam is missing. Bug in LiveExamRepository
	 * (findVisibleRoutineCandidates, the EXAM grant branch sits inside the package EXISTS), not in an NR-01 W1 file.
	 */
	@Test
	void theRoutineShowsAStandaloneExamToTheStudentWhoHoldsItByAnExamGrant() throws Exception {
		World world = world("Routine");
		clock.set(BEFORE);
		mockMvc.perform(get("/api/v1/me/exam-routine").header("Authorization", "Bearer " + world.s1Token()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString(world.mcq().toString())))
			.andExpect(content().string(containsString(world.written().toString())));
	}

	@Test
	void aStandaloneMcqExamIsSatScoredPositionedAndShownOnTheBoardsWithNoPackage() throws Exception {
		World world = world("Mcq life");
		clock.set(SITTING);

		mockMvc.perform(post("/api/v1/live-exams/" + world.mcq() + "/take").header("Authorization", "Bearer " + world.s2Token())
			.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isNotFound());

		mockMvc.perform(post("/api/v1/live-exams/" + world.mcq() + "/take").header("Authorization", "Bearer " + world.s1Token())
			.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.attemptKind").value("LIVE"));
		mockMvc.perform(put("/api/v1/live-exams/" + world.mcq() + "/answers").header("Authorization", "Bearer " + world.s1Token())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"answers":[{"questionId":"%s","optionId":"%s"}]}
					""".formatted(world.paper().questionId(), world.paper().correctOptionId())))
			.andExpect(status().isNoContent());
		mockMvc.perform(post("/api/v1/live-exams/" + world.mcq() + "/submit").header("Authorization", "Bearer " + world.s1Token()))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/live-exams/" + world.mcq() + "/result").header("Authorization", "Bearer " + world.s1Token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(2))
			.andExpect(jsonPath("$.position.type").value("CURRENT"));

		clock.set(WINDOW_ENDED);
		JsonNode board = bodyOf(mockMvc.perform(get("/api/v1/live-exams/" + world.mcq() + "/leaderboard")
			.header("Authorization", "Bearer " + world.s1Token())));
		assertThat(board.get("rows")).hasSize(1);
		assertThat(board.get("rows").get(0).get("score").asDouble()).isEqualTo(2.0);

		mockMvc.perform(get("/api/v1/live-exams/" + world.mcq() + "/results").header("Authorization", "Bearer " + world.master()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.rows.content.length()").value(1))
			.andExpect(jsonPath("$.rows.content[0].attemptNo").value(1));
		mockMvc.perform(get("/api/v1/live-exam-participation").header("Authorization", "Bearer " + world.master()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString(world.mcq().toString())));
		mockMvc.perform(get("/api/v1/students/" + world.s1() + "/live-exam-results")
			.header("Authorization", "Bearer " + world.master()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString(world.mcq().toString())));
	}

	@Test
	void aStandaloneWrittenExamIsMarkedReleasedAndRankedWithNoPackage() throws Exception {
		World world = world("Written life");
		String master = world.master();
		Marker marker = instructor(master, "Standalone marker");
		mockMvc.perform(post("/api/v1/live-exams/" + world.written() + "/instructor").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"instructorId":"%s"}
					""".formatted(marker.id())))
			.andExpect(status().is2xxSuccessful());

		clock.set(SITTING);
		mockMvc.perform(post("/api/v1/live-exams/" + world.written() + "/take").header("Authorization", "Bearer " + world.s1Token())
			.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isOk());
		mockMvc.perform(multipart("/api/v1/live-exams/" + world.written() + "/answer-pdf")
			.file(new MockMultipartFile("file", "answer.pdf", "application/pdf", realPdf()))
			.header("Authorization", "Bearer " + world.s1Token()))
			.andExpect(status().isNoContent());

		clock.set(WRITTEN_DEADLINE_PASSED);
		deadlineJob.settleDue();
		UUID participant = participant(world.written(), world.s1());

		mockMvc.perform(get("/api/v1/live-exam-submissions").param("examId", world.written().toString())
			.header("Authorization", "Bearer " + marker.token()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString(participant.toString())));
		mockMvc.perform(get("/api/v1/live-exam-participants/" + participant + "/question-pdf")
			.header("Authorization", "Bearer " + marker.token()))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/live-exams/" + world.written() + "/question-pdf")
			.header("Authorization", "Bearer " + world.s2Token()))
			.andExpect(status().isNotFound());
		Marker stranger = instructor(master, "Unassigned marker");
		mockMvc.perform(get("/api/v1/live-exam-participants/" + participant + "/question-pdf")
			.header("Authorization", "Bearer " + stranger.token()))
			.andExpect(status().isNotFound());

		mockMvc.perform(multipart("/api/v1/live-exam-participants/" + participant + "/evaluation")
			.file(new MockMultipartFile("file", "mark.pdf", "application/pdf", realPdf()))
			.param("marks", "7")
			.header("Authorization", "Bearer " + marker.token()))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exam-participants/" + participant + "/ready")
			.header("Authorization", "Bearer " + marker.token()))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + world.written() + "/publish-results").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/live-exams/" + world.written() + "/result").header("Authorization", "Bearer " + world.s1Token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(7));
		JsonNode board = bodyOf(mockMvc.perform(get("/api/v1/live-exams/" + world.written() + "/leaderboard")
			.header("Authorization", "Bearer " + world.s1Token())));
		assertThat(board.get("rows")).hasSize(1);
		mockMvc.perform(get("/api/v1/live-exam-participation").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString(world.written().toString())));
		mockMvc.perform(get("/api/v1/students/" + world.s1() + "/live-exam-results").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString(world.written().toString())));
	}

	// ---- set-up -------------------------------------------------------------------------------------------------

	/** A standalone MCQ (2 marks, one correct option) and a standalone written exam, both published, S1 granted both. */
	private World world(String title) throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID category = fx.category(staff, title + " category");

		UUID mcq = fx.createStandalone(staff, category, "MCQ", title + " MCQ", AuthoringFixture.LIVE_AT, 40, null);
		Paper paper = question(staff, mcq);
		fx.publishNew(master, mcq);

		UUID written = fx.createStandalone(staff, category, "WRITTEN", title + " written", AuthoringFixture.LIVE_AT, 40, null);
		fx.uploadPdf(staff, written);
		fx.publishNew(master, written);

		UUID s1 = fx.seed("STUDENT");
		UUID s2 = fx.seed("STUDENT");
		grant(master, s1, mcq);
		grant(master, s1, written);
		return new World(category, mcq, written, paper, s1, fx.token(s1), s2, fx.token(s2), master);
	}

	private void grant(String master, UUID student, UUID exam) throws Exception {
		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"studentId":"%s","scope":"EXAM","moduleId":null,"examId":"%s","reason":"Standalone grant"}
					""".formatted(student, exam)))
			.andExpect(status().isCreated());
	}

	/** One two-mark single-answer question on a standalone MCQ exam, with its correct option, then the final save. */
	private Paper question(String staff, UUID exam) throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/live-exams/" + exam + "/questions")
			.header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"stem":"Two plus two","marks":2,"explanation":"Because","multipleAnswer":false,"options":[{"text":"Four","correct":true},{"text":"Five","correct":false}]}
					"""))
			.andExpect(status().isCreated())
			.andReturn();
		JsonNode body = json.readTree(created.getResponse().getContentAsString());
		UUID correct = null;
		for (JsonNode option : body.get("options")) {
			if (option.get("correct").asBoolean()) {
				correct = UUID.fromString(option.get("id").asText());
			}
		}
		return new Paper(UUID.fromString(body.get("id").asText()), correct);
	}

	/** An instructor, created and activated the way the instructor tests do it, with a token. */
	private Marker instructor(String master, String name) throws Exception {
		String phone = "+8801" + ThreadLocalRandom.current().nextInt(100000000, 999999999);
		String email = "standalone-" + UUID.randomUUID() + "@example.com";
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

	private UUID participant(UUID exam, UUID student) {
		return new JdbcTemplate(dataSource).queryForObject(
				"select id from live_exam_participants where exam_id = ? and student_id = ? and deleted_at is null",
				UUID.class, exam, student);
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
				stream.showText("Answer script");
				stream.endText();
			}
			document.save(output);
			return output.toByteArray();
		}
	}

	private JsonNode contentOf(ResultActions request) throws Exception {
		return bodyOf(request).get("content");
	}

	private JsonNode bodyOf(ResultActions request) throws Exception {
		return json.readTree(request.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}

	private static JsonNode rowFor(JsonNode access, UUID exam) {
		for (JsonNode row : access.get("exams")) {
			if (exam.toString().equals(row.path("examId").asText())) {
				return row;
			}
		}
		return null;
	}

	private static List<UUID> ids(JsonNode nodes) {
		List<UUID> ids = new ArrayList<>();
		for (JsonNode node : nodes) {
			ids.add(UUID.fromString(node.get("id").asText()));
		}
		return ids;
	}

	private record Paper(UUID questionId, UUID correctOptionId) {
	}

	private record Marker(UUID id, String token) {
	}

	private record World(UUID category, UUID mcq, UUID written, Paper paper, UUID s1, String s1Token, UUID s2,
			String s2Token, String master) {
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
