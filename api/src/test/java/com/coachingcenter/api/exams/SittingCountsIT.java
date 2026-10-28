package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Split counts for staff (NR-01 D7) and an archived sitting settled by the deadline job long after the window (D8). One
 * MCQ exam holds a live attempt, its retake and a late (archived) attempt. The window is 06:00 to 06:40 UTC.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, SittingCountsIT.ClockConfig.class })
class SittingCountsIT extends PostgresIntegrationTest {

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
	private LiveExamDeadlineJob deadlineJob;

	@BeforeEach
	void setClock() {
		clock.set(TEN_AM);
	}

	@Test
	void theStaffSummaryAndRowsSplitLiveArchivedAndRetakeAttempts() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		UUID module = publishModule(staff, "Counts");
		UUID exam = mcq(staff, master, module);
		UUID liveId = seed("STUDENT");
		UUID lateId = seed("STUDENT");
		for (UUID student : new UUID[] { liveId, lateId }) {
			mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/roster").header("Authorization", "Bearer " + master)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"studentId":"%s"}
						""".formatted(student)))
				.andExpect(status().isCreated());
		}
		String live = token(liveId);
		String late = token(lateId);

		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		take(exam, live).andExpect(jsonPath("$.attemptKind").value("LIVE"));
		submit(exam, live, "2026-10-20T06:20:00Z");
		retake(exam, live, "2026-10-20T06:30:00Z").andExpect(jsonPath("$.attemptNo").value(2));
		submit(exam, live, "2026-10-20T06:31:00Z");

		clock.set(Instant.parse("2026-10-20T06:45:00Z"));
		take(exam, late).andExpect(jsonPath("$.attemptKind").value("ARCHIVED")).andExpect(jsonPath("$.kind").value("RETAKE"));

		clock.set(Instant.parse("2026-10-20T06:50:00Z"));
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/results").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.summary.live").value(1))
			.andExpect(jsonPath("$.summary.archived").value(1))
			.andExpect(jsonPath("$.summary.retake").value(1))
			.andExpect(jsonPath("$.summary.practice").value(0))
			.andExpect(jsonPath("$.rows.totalElements").value(3));
		// The filter takes one full kind, and the legacy RETAKE value still means every attempt that is not the live one.
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/results?attemptKind=ARCHIVED").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.rows.totalElements").value(1))
			.andExpect(jsonPath("$.rows.content[0].attemptKind").value("ARCHIVED"))
			.andExpect(jsonPath("$.rows.content[0].attemptNo").value(1));
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/results?kind=RETAKE").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.rows.totalElements").value(2));
		// The student's own attempts list: one row per attempt, with its kind and number.
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/attempts").header("Authorization", "Bearer " + live))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].attemptKind").value("LIVE"))
			.andExpect(jsonPath("$[1].attemptKind").value("RETAKE"));
	}

	@Test
	void anArchivedSittingIsSettledByTheDeadlineJobLongAfterTheWindow() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		UUID module = publishModule(staff, "Archived job");
		UUID exam = mcq(staff, master, module);
		UUID lateId = seed("STUDENT");
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/roster").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"studentId":"%s"}
					""".formatted(lateId)))
			.andExpect(status().isCreated());
		String late = token(lateId);

		clock.set(Instant.parse("2026-10-20T06:45:00Z"));
		take(exam, late).andExpect(jsonPath("$.attemptKind").value("ARCHIVED"));
		// Its own deadline is 07:25. The job runs at 07:30 with no read and no submit: the attempt is posted, not left open.
		clock.set(Instant.parse("2026-10-20T06:55:00Z"));
		deadlineJob.settleDue();
		assertThat(statusOf(exam, lateId)).isEqualTo("IN_PROGRESS");
		clock.set(Instant.parse("2026-10-20T07:30:00Z"));
		deadlineJob.settleDue();
		assertThat(statusOf(exam, lateId)).isEqualTo("SUBMITTED");
		assertThat(new JdbcTemplate(dataSource).queryForObject(
				"select submitted_at from live_exam_participants where exam_id = ? and student_id = ?", Object.class, exam, lateId))
			.isNotNull();
	}

	private String statusOf(UUID exam, UUID student) {
		return new JdbcTemplate(dataSource).queryForObject(
				"select status from live_exam_participants where exam_id = ? and student_id = ?", String.class, exam, student);
	}

	private org.springframework.test.web.servlet.ResultActions take(UUID exam, String token) throws Exception {
		return mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isOk());
	}

	private org.springframework.test.web.servlet.ResultActions retake(UUID exam, String token, String at) throws Exception {
		clock.set(Instant.parse(at));
		return mockMvc.perform(post("/api/v1/live-exams/" + exam + "/retake").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
	}

	private void submit(UUID exam, String token, String at) throws Exception {
		clock.set(Instant.parse(at));
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/submit").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
	}

	/** An MCQ exam with one question, window 06:00 to 06:40 UTC, published. */
	private UUID mcq(String staff, String master, UUID module) throws Exception {
		MvcResult created = mockMvc
			.perform(post("/api/v1/live-exam-modules/" + module + "/exams").header("Authorization", "Bearer " + staff)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "examType":"MCQ","paperLanguage":"BN","titleEn":"Count quiz","titleBn":"কুইজ",
						  "descriptionEn":"d","descriptionBn":"বি","rulesEn":"English rules","rulesBn":"নিয়ম",
						  "liveAt":"2026-10-20T12:00:00+06:00","durationMinutes":40,"passPercent":50
						}
						"""))
			.andExpect(status().isCreated())
			.andReturn();
		UUID exam = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/questions").header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"stem":"Two plus two","marks":10,"explanation":"Because","options":[{"text":"Yes","correct":true},{"text":"No","correct":false}]}
					"""))
			.andExpect(status().isCreated());
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
		return "counts-" + UUID.randomUUID() + "@example.com";
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
