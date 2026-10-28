package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * The first attempt, its kind, the deadline and the later attempts (NR-01 D1 and D2, MCQ). The window is 06:00 to 06:40 UTC
 * (12:00 to 12:40 in Dhaka) and a sitting lasts 40 minutes from its own start (D15).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, AttemptsIT.ClockConfig.class })
class AttemptsIT extends PostgresIntegrationTest {

	private static final Instant TEN_AM = Instant.parse("2026-10-20T04:00:00Z");

	private static final String LIVE_AT = "2026-10-20T12:00:00+06:00";

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

	@BeforeEach
	void setClock() {
		clock.set(TEN_AM);
	}

	@Test
	void aStartOneSecondBeforeTheEndIsLiveAndRunsItsFullDuration() throws Exception {
		Fixture fixture = fixture("Edge live");
		clock.set(Instant.parse("2026-10-20T06:39:59Z"));
		take(fixture.studentId(), fixture.exam())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.kind").value("LIVE"))
			.andExpect(jsonPath("$.attemptKind").value("LIVE"))
			.andExpect(jsonPath("$.attemptNo").value(1))
			.andExpect(jsonPath("$.deadlineAt").value("2026-10-20T13:19:59+06:00"));
	}

	@Test
	void aStartAtTheEndIsArchivedAndItsDeadlineIsItsOwnToo() throws Exception {
		Fixture fixture = fixture("Edge archived");
		clock.set(Instant.parse("2026-10-20T06:40:00Z"));
		take(fixture.studentId(), fixture.exam())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.kind").value("RETAKE"))
			.andExpect(jsonPath("$.attemptKind").value("ARCHIVED"))
			.andExpect(jsonPath("$.deadlineAt").value("2026-10-20T13:20:00+06:00"));
	}

	@Test
	void aPracticeExamOpensFromPublishAndItsFirstAttemptIsPractice() throws Exception {
		Fixture fixture = fixture("Practice");
		new JdbcTemplate(dataSource).update(
				"update live_exams set mode = 'PRACTICE', live_at = null, ends_at = null where id = ?", fixture.exam());
		clock.set(Instant.parse("2026-10-21T03:00:00Z"));
		take(fixture.studentId(), fixture.exam())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.kind").value("RETAKE"))
			.andExpect(jsonPath("$.attemptKind").value("PRACTICE"))
			.andExpect(jsonPath("$.deadlineAt").value("2026-10-21T09:40:00+06:00"));
	}

	@Test
	void anUpcomingExamRefusesTheFirstAttempt() throws Exception {
		Fixture fixture = fixture("Upcoming");
		clock.set(Instant.parse("2026-10-20T05:59:59Z"));
		take(fixture.studentId(), fixture.exam())
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SITTING_NOT_OPEN"));
	}

	@Test
	void movingTheWindowAfterTheStartDoesNotChangeTheKind() throws Exception {
		Fixture fixture = fixture("Moved window");
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		take(fixture.studentId(), fixture.exam()).andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + fixture.exam() + "/submit")
			.header("Authorization", "Bearer " + fixture.studentToken()))
			.andExpect(status().isOk());

		// The window moves to start after the attempt was made. R18: the kind was fixed at the start.
		new JdbcTemplate(dataSource).update(
				"update live_exams set live_at = ?, ends_at = ? where id = ?", java.sql.Timestamp.from(Instant.parse("2026-10-20T07:00:00Z")),
				java.sql.Timestamp.from(Instant.parse("2026-10-20T07:40:00Z")), fixture.exam());
		clock.set(Instant.parse("2026-10-20T07:50:00Z"));
		mockMvc.perform(get("/api/v1/live-exams/" + fixture.exam() + "/result")
			.header("Authorization", "Bearer " + fixture.studentToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.attemptKind").value("LIVE"))
			.andExpect(jsonPath("$.kind").value("LIVE"));
		assertThat(new JdbcTemplate(dataSource).queryForObject(
				"select kind from live_exam_participants where exam_id = ? and student_id = ?", String.class, fixture.exam(),
				fixture.studentId())).isEqualTo("LIVE");
	}

	@Test
	void anMcqStudentRetakesDuringAndAfterTheWindowAndCannotRetakeWhileOneIsOpen() throws Exception {
		Fixture fixture = fixture("Retakes");
		String token = fixture.studentToken();
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		take(fixture.studentId(), fixture.exam()).andExpect(status().isOk());
		submit(fixture, "2026-10-20T06:15:00Z");

		// D2 and R11: a retake during the window is still a retake, and it is never the live attempt.
		clock.set(Instant.parse("2026-10-20T06:25:00Z"));
		retake(fixture.exam(), token)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.kind").value("RETAKE"))
			.andExpect(jsonPath("$.attemptKind").value("RETAKE"))
			.andExpect(jsonPath("$.attemptNo").value(2));
		retake(fixture.exam(), token)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ATTEMPT_IN_PROGRESS"));
		submit(fixture, "2026-10-20T06:30:00Z");

		clock.set(Instant.parse("2026-10-20T06:35:00Z"));
		retake(fixture.exam(), token).andExpect(status().isOk()).andExpect(jsonPath("$.attemptNo").value(3));
		submit(fixture, "2026-10-20T06:36:00Z");

		// After the window, retakes carry on (D19: unlimited).
		clock.set(Instant.parse("2026-10-20T06:45:00Z"));
		retake(fixture.exam(), token).andExpect(status().isOk()).andExpect(jsonPath("$.attemptNo").value(4));
		assertThat(attemptCount(fixture)).isEqualTo(4);
		mockMvc.perform(get("/api/v1/live-exams/" + fixture.exam() + "/attempts").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(4))
			.andExpect(jsonPath("$[0].attemptNo").value(1))
			.andExpect(jsonPath("$[0].attemptKind").value("LIVE"))
			.andExpect(jsonPath("$[1].attemptKind").value("RETAKE"));
	}

	@Test
	void aStudentWhoNeverStartedGetsAnArchivedFirstAttemptFromRetakeAfterTheWindow() throws Exception {
		Fixture fixture = fixture("Late retake");
		clock.set(Instant.parse("2026-10-20T06:41:00Z"));
		retake(fixture.exam(), fixture.studentToken())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.kind").value("RETAKE"))
			.andExpect(jsonPath("$.attemptKind").value("ARCHIVED"))
			.andExpect(jsonPath("$.attemptNo").value(1));
	}

	@Test
	void aStudentWithoutAccessGetsNotFoundAndNoAttempt() throws Exception {
		Fixture fixture = fixture("No access");
		UUID stranger = seed("STUDENT");
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + fixture.exam() + "/take")
			.header("Authorization", "Bearer " + token(stranger))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isNotFound());
		assertThat(new JdbcTemplate(dataSource).queryForObject(
				"select count(*) from live_exam_participants where exam_id = ?", Integer.class, fixture.exam())).isZero();
	}

	private void submit(Fixture fixture, String at) throws Exception {
		clock.set(Instant.parse(at));
		mockMvc.perform(post("/api/v1/live-exams/" + fixture.exam() + "/submit")
			.header("Authorization", "Bearer " + fixture.studentToken()))
			.andExpect(status().isOk());
	}

	private org.springframework.test.web.servlet.ResultActions take(UUID studentId, UUID exam) throws Exception {
		return mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take")
			.header("Authorization", "Bearer " + token(studentId))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"));
	}

	private org.springframework.test.web.servlet.ResultActions retake(UUID exam, String token) throws Exception {
		return mockMvc.perform(post("/api/v1/live-exams/" + exam + "/retake").header("Authorization", "Bearer " + token));
	}

	private int attemptCount(Fixture fixture) {
		return new JdbcTemplate(dataSource).queryForObject(
				"select count(*) from live_exam_participants where exam_id = ? and student_id = ? and deleted_at is null",
				Integer.class, fixture.exam(), fixture.studentId());
	}

	/** An MCQ exam in a package, published, with one student on the roster. */
	private Fixture fixture(String title) throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		UUID module = publishModule(staff, title);
		UUID exam = publishMcq(staff, module, title);
		UUID student = seed("STUDENT");
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/roster").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"studentId":"%s"}
					""".formatted(student)))
			.andExpect(status().isCreated());
		return new Fixture(exam, student, token(student));
	}

	private record Fixture(UUID exam, UUID studentId, String studentToken) {
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

	/** A published MCQ with one two-mark question; the correct option is the first. */
	private UUID publishMcq(String staff, UUID module, String title) throws Exception {
		MvcResult created = mockMvc
			.perform(post("/api/v1/live-exam-modules/" + module + "/exams").header("Authorization", "Bearer " + staff)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "examType":"MCQ","paperLanguage":"BN","titleEn":"%s quiz","titleBn":"কুইজ",
						  "descriptionEn":"d","descriptionBn":"বি","rulesEn":"English rules","rulesBn":"নিয়ম",
						  "liveAt":"%s","durationMinutes":40,"passPercent":50
						}
						""".formatted(title, LIVE_AT)))
			.andExpect(status().isCreated())
			.andReturn();
		UUID exam = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/questions").header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"stem":"Two plus two","marks":2,"explanation":"Because","options":[{"text":"Yes","correct":true},{"text":"No","correct":false}]}
					"""))
			.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/final-save").header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + masterToken()))
			.andExpect(status().isOk());
		return exam;
	}

	private String masterToken() throws Exception {
		return token(seed("MASTER_ADMIN"));
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
		return "attempts-" + UUID.randomUUID() + "@example.com";
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
