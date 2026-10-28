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
 * NR-01 final check, case 11: an archived written attempt and a granted written retake through marking, release, the
 * positions, the history and the counts. The window is 12:00 to 12:40 Dhaka (06:00 to 06:40 UTC). Each sitting is 40
 * minutes from its own start.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, ArchivedAndRetakeMarkingIT.ClockConfig.class })
class ArchivedAndRetakeMarkingIT extends PostgresIntegrationTest {

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
	void aLiveArchivedAndRetakePaperAreMarkedReleasedRankedAndCounted() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID category = fx.category(staff, "Written marking");
		UUID exam = fx.createStandalone(staff, category, "WRITTEN", "Written archive", AuthoringFixture.LIVE_AT, 40, null);
		fx.uploadPdf(staff, exam);
		fx.publishNew(master, exam);

		UUID s1 = fx.seed("STUDENT");
		UUID s2 = fx.seed("STUDENT");
		grant(master, s1, exam);
		grant(master, s2, exam);
		String token1 = fx.token(s1);
		String token2 = fx.token(s2);
		Marker marker = instructor(master, "Written marker");
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/instructor").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"instructorId":"%s"}
					""".formatted(marker.id())))
			.andExpect(status().is2xxSuccessful());

		// S1 sits in the window (LIVE). S2 starts after the window (ARCHIVED). S1 gets a retake and sits it (RETAKE).
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		take(token1, exam).andExpect(jsonPath("$.attemptKind").value("LIVE"));
		upload(token1, exam);

		clock.set(Instant.parse("2026-10-20T06:45:00Z"));
		take(token2, exam).andExpect(jsonPath("$.attemptKind").value("ARCHIVED"));
		clock.set(Instant.parse("2026-10-20T06:50:00Z"));
		upload(token2, exam);

		clock.set(Instant.parse("2026-10-20T06:55:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/retake-grants").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"studentId":"%s","reason":"Lost the first sheet"}
					""".formatted(s1)))
			.andExpect(status().isCreated());
		clock.set(Instant.parse("2026-10-20T07:00:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/retake").header("Authorization", "Bearer " + token1))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.attemptKind").value("RETAKE"))
			.andExpect(jsonPath("$.attemptNo").value(2));
		clock.set(Instant.parse("2026-10-20T07:05:00Z"));
		upload(token1, exam);

		// The instructor marks the three papers, then the Master Admin releases them.
		clock.set(Instant.parse("2026-10-20T07:45:00Z"));
		UUID live = participant(exam, s1, 1);
		UUID archived = participant(exam, s2, 1);
		UUID retake = participant(exam, s1, 2);
		mark(marker.token(), live, 6);
		mark(marker.token(), archived, 7);
		mark(marker.token(), retake, 9);
		mockMvc.perform(post("/api/v1/live-exam-participants/" + live + "/ready").header("Authorization", "Bearer " + marker.token()))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exam-participants/" + archived + "/ready").header("Authorization", "Bearer " + marker.token()))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exam-participants/" + retake + "/ready").header("Authorization", "Bearer " + marker.token()))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish-results").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());

		// 1. The feed carries all three papers with their kind and number.
		JsonNode feed = bodyOf(mockMvc.perform(get("/api/v1/live-exam-submissions").param("examId", exam.toString())
			.header("Authorization", "Bearer " + marker.token())));
		Set<String> kinds = new HashSet<>();
		for (JsonNode row : feed.get("content")) {
			kinds.add(row.get("attemptKind").asText() + ":" + row.get("attemptNo").asInt());
		}
		assertThat(kinds).containsExactlyInAnyOrder("LIVE:1", "ARCHIVED:1", "RETAKE:2");

		// 2. The leaderboard holds only S1's live paper (score 6).
		JsonNode board = bodyOf(mockMvc.perform(get("/api/v1/live-exams/" + exam + "/leaderboard")
			.header("Authorization", "Bearer " + token1)));
		assertThat(board.get("rows")).hasSize(1);
		assertThat(board.get("rows").get(0).get("score").asDouble()).isEqualTo(6.0);

		// 3. Positions: the archived paper and the retake are RELATIVE, the first sitting is BOARD.
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/result").header("Authorization", "Bearer " + token2))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.position.type").value("RELATIVE"));
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/result").header("Authorization", "Bearer " + token1))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.position.type").value("RELATIVE"))
			.andExpect(jsonPath("$.attemptNo").value(2));
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/result").param("attempt", "1")
			.header("Authorization", "Bearer " + token1))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.position.type").value("BOARD"));

		// 4. History: S1 has two rows for this exam, attempts 1 and 2.
		JsonNode history = bodyOf(mockMvc.perform(get("/api/v1/students/" + s1 + "/live-exam-results")
			.header("Authorization", "Bearer " + master)));
		Set<String> mine = new HashSet<>();
		for (JsonNode row : history.get("content")) {
			if (exam.toString().equals(row.path("examId").asText())) {
				mine.add(row.get("attemptKind").asText());
			}
		}
		assertThat(mine).containsExactlyInAnyOrder("LIVE", "RETAKE");

		// 5. Counts by kind on the staff results report.
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/results").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.summary.live").value(1))
			.andExpect(jsonPath("$.summary.archived").value(1))
			.andExpect(jsonPath("$.summary.retake").value(1));
	}

	private void grant(String master, UUID student, UUID exam) throws Exception {
		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"studentId":"%s","scope":"EXAM","moduleId":null,"examId":"%s","reason":"Written grant"}
					""".formatted(student, exam)))
			.andExpect(status().isCreated());
	}

	private ResultActions take(String token, UUID exam) throws Exception {
		return mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take").header("Authorization", "Bearer " + token)
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

	private void mark(String token, UUID participant, int marks) throws Exception {
		mockMvc.perform(multipart("/api/v1/live-exam-participants/" + participant + "/evaluation")
			.file(new MockMultipartFile("file", "mark.pdf", "application/pdf", realPdf()))
			.param("marks", String.valueOf(marks))
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
	}

	private UUID participant(UUID exam, UUID student, int attemptNo) {
		return new JdbcTemplate(dataSource).queryForObject(
				"select id from live_exam_participants where exam_id = ? and student_id = ? and attempt_no = ? and deleted_at is null",
				UUID.class, exam, student, attemptNo);
	}

	private JsonNode bodyOf(ResultActions request) throws Exception {
		MvcResult result = request.andExpect(status().isOk()).andReturn();
		return json.readTree(result.getResponse().getContentAsString());
	}

	/** An instructor, created and activated the way the instructor tests do it, with a token. */
	private Marker instructor(String master, String name) throws Exception {
		String phone = "+8801" + ThreadLocalRandom.current().nextInt(100000000, 999999999);
		String email = "archived-" + UUID.randomUUID() + "@example.com";
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
