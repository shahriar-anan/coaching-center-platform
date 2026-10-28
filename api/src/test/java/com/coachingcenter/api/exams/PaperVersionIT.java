package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

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

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;
import com.coachingcenter.api.files.FileStorageProperties;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The paper a participant sat (NR-01 D6, E29). Version 2 is inserted through the repositories, because the paper-version
 * endpoint is A's slice and is not in this tree yet. Also checks the legacy vocabulary the current website reads (contracts
 * section 7, items 7 and 8).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, PaperVersionIT.ClockConfig.class })
class PaperVersionIT extends PostgresIntegrationTest {

	private static final Instant TEN_AM = Instant.parse("2026-10-20T04:00:00Z");

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
	private FileStorageProperties files;

	@Autowired
	private LiveExamQuestionPaperRepository papers;

	@Autowired
	private LiveExamRepository exams;

	@BeforeEach
	void setClock() {
		clock.set(TEN_AM);
	}

	@Test
	void aSittingKeepsTheVersionItWasGivenAndANewSitterGetsTheNewOne() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		UUID module = publishModule(staff, "Versions");
		UUID exam = writtenExam(staff, master, module);
		UUID firstId = seed("STUDENT");
		UUID secondId = seed("STUDENT");
		UUID lateId = seed("STUDENT");
		for (UUID student : new UUID[] { firstId, secondId, lateId }) {
			mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/roster").header("Authorization", "Bearer " + master)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"studentId":"%s"}
						""".formatted(student)))
				.andExpect(status().isCreated());
		}
		String first = token(firstId);
		String second = token(secondId);
		String late = token(lateId);

		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take").header("Authorization", "Bearer " + first)
			.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.kind").value("LIVE"))
			.andExpect(jsonPath("$.attemptKind").value("LIVE"));
		byte[] v1 = bytesOf(papers.findTopByExamIdOrderByVersionDesc(exam).orElseThrow().getStoredFileName());

		// The paper is replaced: version 2 is stored, and the exam points at it (through the repositories).
		UUID v2 = addVersion(exam, 2, "%PDF-1.1 version two, which is different\n%%EOF\n".getBytes(StandardCharsets.US_ASCII));
		byte[] version2 = bytesOf(papers.findById(v2).orElseThrow().getStoredFileName());
		assertThat(version2).isNotEqualTo(v1);

		clock.set(Instant.parse("2026-10-20T06:20:00Z"));
		// The student still sitting gets the paper they were given (E29).
		assertThat(questionPdf(first, exam)).isEqualTo(v1);

		// The evaluator (the Master Admin here) reads the paper that participant sat, after the window (06:50 is the deadline).
		clock.set(Instant.parse("2026-10-20T06:50:00Z"));
		UUID firstParticipant = participantOf(exam, firstId);
		assertThat(paperOfParticipant(master, firstParticipant)).isEqualTo(v1);

		// A student who sits after the paper was replaced gets version 2.
		clock.set(Instant.parse("2026-10-20T06:25:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take").header("Authorization", "Bearer " + second)
			.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isOk());
		assertThat(questionPdf(second, exam)).isEqualTo(version2);

		// D6 and contracts 7.7 and 7.8: the late student has no attempt and the window is over. The legacy kind and
		// status are what the current website reads, and the detail says what is really happening.
		clock.set(Instant.parse("2026-10-20T06:45:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take").header("Authorization", "Bearer " + late)
			.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.kind").value("RETAKE"))
			.andExpect(jsonPath("$.attemptKind").value("ARCHIVED"));
		mockMvc.perform(get("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + first))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.myStatus").value("IN_PROGRESS"))
			.andExpect(jsonPath("$.myStatusDetail").value("IN_PROGRESS"));
		UUID nobody = seed("STUDENT");
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/roster").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"studentId":"%s"}
					""".formatted(nobody)))
			.andExpect(status().isCreated());
		mockMvc.perform(get("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + token(nobody)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.myStatus").value("MISSED"))
			.andExpect(jsonPath("$.myStatusDetail").value("ARCHIVED_OPEN"))
			.andExpect(jsonPath("$.retakeAvailable").value(false));
	}

	private byte[] questionPdf(String token, UUID exam) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/live-exams/" + exam + "/question-pdf")
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn();
		return result.getResponse().getContentAsByteArray();
	}

	private byte[] paperOfParticipant(String master, UUID participant) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/live-exam-participants/" + participant + "/question-pdf")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andReturn();
		return result.getResponse().getContentAsByteArray();
	}

	private byte[] bytesOf(String storedName) throws Exception {
		return Files.readAllBytes(Path.of(files.storageDir(), storedName));
	}

	/** Stores a new paper version and points the exam at it. Written through the repositories. */
	private UUID addVersion(UUID exam, int version, byte[] content) throws Exception {
		String stored = "paper-v" + version + "-" + UUID.randomUUID() + ".pdf";
		Files.write(Path.of(files.storageDir(), stored), content);
		LiveExamQuestionPaper paper = new LiveExamQuestionPaper();
		paper.setExamId(exam);
		paper.setVersion(version);
		paper.setStoredFileName(stored);
		paper.setOriginalFileName("paper-v" + version + ".pdf");
		paper.setByteSize((long) content.length);
		paper.setContentType("application/pdf");
		paper.setCreatedAt(clock.instant());
		UUID id = papers.save(paper).getId();
		LiveExam current = exams.findById(exam).orElseThrow();
		current.setQuestionPaperId(id);
		exams.save(current);
		return id;
	}

	private UUID participantOf(UUID exam, UUID student) {
		return new JdbcTemplate(dataSource).queryForObject(
				"select id from live_exam_participants where exam_id = ? and student_id = ?", UUID.class, exam, student);
	}

	/** A published written exam with paper version 1, window 06:00 to 06:40 UTC. */
	private UUID writtenExam(String staff, String master, UUID module) throws Exception {
		MvcResult created = mockMvc
			.perform(post("/api/v1/live-exam-modules/" + module + "/exams").header("Authorization", "Bearer " + staff)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "examType":"WRITTEN","paperLanguage":"BN","titleEn":"Noon paper","titleBn":"খাতা",
						  "descriptionEn":"d","descriptionBn":"বি","rulesEn":"English rules","rulesBn":"নিয়ম",
						  "liveAt":"2026-10-20T12:00:00+06:00","durationMinutes":40,"passPercent":50,"maximum":10
						}
						"""))
			.andExpect(status().isCreated())
			.andReturn();
		UUID exam = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
		MockMultipartFile paper = new MockMultipartFile("file", "paper.pdf", "application/pdf",
				"%PDF-1.1 version one\n%%EOF\n".getBytes(StandardCharsets.US_ASCII));
		mockMvc.perform(multipart("/api/v1/live-exams/" + exam + "/question-pdf").file(paper)
			.header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/final-save").header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
		return exam;
	}

	private UUID publishModule(String token, String title) throws Exception {
		MvcResult category = mockMvc.perform(post("/api/v1/live-exam-categories").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titleEn":"%s cat","titleBn":"বিভাগ"}
					""".formatted(title)))
			.andExpect(status().isCreated())
			.andReturn();
		UUID categoryId = UUID.fromString(json.readTree(category.getResponse().getContentAsString()).get("id").asText());
		MvcResult created = mockMvc.perform(post("/api/v1/live-exam-modules").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"categoryId":"%s","titleEn":"%s","titleBn":"মডিউল","descriptionEn":"d","descriptionBn":"বি"}
					""".formatted(categoryId, title)))
			.andExpect(status().isCreated())
			.andReturn();
		UUID module = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/final-save").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/publish").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		return module;
	}

	private UUID seed(String role) {
		UUID id = UUID.randomUUID();
		new JdbcTemplate(dataSource).update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, uniquePhone(), uniqueEmail(), passwords.encode("password1"), role);
		if ("STUDENT".equals(role)) {
			new JdbcTemplate(dataSource).update(
					"INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, 'Seed', ?)", id,
					"S" + id.toString().substring(0, 8));
		}
		else {
			new JdbcTemplate(dataSource).update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, 'Admin')", id);
		}
		return id;
	}

	private String token(UUID userId) throws Exception {
		String phone = new JdbcTemplate(dataSource).queryForObject("select phone from users where id = ?", String.class,
				userId);
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

	private static String uniquePhone() {
		int suffix = java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999);
		return "+8801" + suffix;
	}

	private static String uniqueEmail() {
		return "paper-" + UUID.randomUUID() + "@example.com";
	}

	@TestConfiguration
	static class ClockConfig {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(TEN_AM);
		}

	}

}
