package com.coachingcenter.api.exams;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
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

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, LiveEnrolmentListIT.ClockConfig.class })
class LiveEnrolmentListIT extends PostgresIntegrationTest {

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

	/** The clock is shared by every test in the class, so each one starts from the same instant. */
	@BeforeEach
	void resetClock() {
		clock.set(Instant.parse("2026-10-20T04:00:00Z"));
	}

	@Test
	void newestEnrolmentComesFirstAndRevokedOnesAreLeftOut() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID module = module(master, "Listed");
		UUID first = seed("STUDENT", "First Enrolled");
		UUID second = seed("STUDENT", "Second Enrolled");
		String firstGrant = grant(master, first, "MODULE", module, null, "Paid by cash");
		clock.set(clock.instant().plusSeconds(60));
		String secondGrant = grant(master, second, "MODULE", module, null, "Paid by bKash");

		mockMvc.perform(get("/api/v1/live-exam-modules/" + module + "/enrolments").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.content[0].grantId").value(secondGrant))
			.andExpect(jsonPath("$.content[0].studentId").value(second.toString()))
			.andExpect(jsonPath("$.content[0].fullName").value("Second Enrolled"))
			.andExpect(jsonPath("$.content[0].source").value("MANUAL"))
			.andExpect(jsonPath("$.content[0].reason").value("Paid by bKash"))
			.andExpect(jsonPath("$.content[1].grantId").value(firstGrant));

		mockMvc.perform(delete("/api/v1/live-exam-grants/" + firstGrant).header("Authorization", "Bearer " + master)
			.param("reason", "Entered by mistake"))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/live-exam-modules/" + module + "/enrolments").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].grantId").value(secondGrant));
	}

	@Test
	void examListHoldsOnlyGrantsOfThatExam() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID module = module(master, "Exam list");
		UUID exam = exam(master, module);
		UUID student = seed("STUDENT", "Exam Student");
		String examGrant = grant(master, student, "EXAM", null, exam, "Retake for a sick day");

		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/enrolments").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].grantId").value(examGrant))
			.andExpect(jsonPath("$.content[0].studentCode").isNotEmpty());

		mockMvc.perform(get("/api/v1/live-exam-modules/" + module + "/enrolments").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void onlyTheMasterAdminCanReadTheListAndAMissingTargetIsNotFound() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID module = module(master, "Refused read");
		String learner = token(seed("STUDENT", "Curious Student"));

		mockMvc.perform(get("/api/v1/live-exam-modules/" + module + "/enrolments").header("Authorization", "Bearer " + system))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		mockMvc.perform(get("/api/v1/live-exam-modules/" + module + "/enrolments").header("Authorization", "Bearer " + learner))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/live-exam-modules/" + UUID.randomUUID() + "/enrolments").header("Authorization", "Bearer " + master))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
		mockMvc.perform(get("/api/v1/live-exams/" + UUID.randomUUID() + "/enrolments").header("Authorization", "Bearer " + master))
			.andExpect(status().isNotFound());
	}

	@Test
	void aRosterStudentIsListedOnThePackageAndOnItsExams() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID module = module(master, "Roster listed");
		UUID exam = exam(master, module);
		UUID rostered = seed("STUDENT", "Roster Student");
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/roster").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"studentId\":\"" + rostered + "\"}"))
			.andExpect(status().isCreated());

		mockMvc.perform(get("/api/v1/live-exam-modules/" + module + "/enrolments").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].studentId").value(rostered.toString()))
			.andExpect(jsonPath("$.content[0].source").value("ROSTER"))
			.andExpect(jsonPath("$.content[0].grantId").doesNotExist());

		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/enrolments").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].studentId").value(rostered.toString()));
	}

	@Test
	void anExpiredExamGrantIsLeftOutOnceTheExamHasEnded() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID module = module(master, "Expired");
		UUID exam = exam(master, module);
		UUID student = seed("STUDENT", "Expired Student");
		// An exam with no access period never runs out, so this one gets a one-day period.
		new JdbcTemplate(dataSource).update("UPDATE live_exams SET access_days = 1 WHERE id = ?", exam);
		grant(master, student, "EXAM", null, exam, "Retake for a sick day");
		clock.set(Instant.parse("2027-01-01T00:00:00Z"));

		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/enrolments").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void anArchivedPackageKeepsTheStudentsItAlreadyHad() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID module = module(master, "Archived");
		UUID student = seed("STUDENT", "Archived Student");
		String studentGrant = grant(master, student, "MODULE", module, null, "Paid by cash");
		new JdbcTemplate(dataSource).update("UPDATE live_exam_modules SET status = 'ARCHIVED' WHERE id = ?", module);

		mockMvc.perform(get("/api/v1/live-exam-modules/" + module + "/enrolments").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].grantId").value(studentGrant));
	}

	@Test
	void aDeletedStudentIsLeftOut() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID module = module(master, "Deleted");
		UUID student = seed("STUDENT", "Deleted Student");
		grant(master, student, "MODULE", module, null, "Paid by cash");
		new JdbcTemplate(dataSource).update("UPDATE users SET deleted_at = now() WHERE id = ?", student);

		mockMvc.perform(get("/api/v1/live-exam-modules/" + module + "/enrolments").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void theListIsPagedAtTheRequestedSize() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID module = module(master, "Paged");
		for (String name : List.of("Page One", "Page Two", "Page Three")) {
			grant(master, seed("STUDENT", name), "MODULE", module, null, "Paid by cash");
			clock.set(clock.instant().plusSeconds(60));
		}

		mockMvc.perform(get("/api/v1/live-exam-modules/" + module + "/enrolments?page=0&size=2").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(2))
			.andExpect(jsonPath("$.totalElements").value(3))
			.andExpect(jsonPath("$.totalPages").value(2));
		mockMvc.perform(get("/api/v1/live-exam-modules/" + module + "/enrolments?page=1&size=2").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(1));
	}

	@Test
	void aStudentGrantedAgainAfterARevokeAppearsOnceWithTheNewGrant() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID module = module(master, "Regranted");
		UUID student = seed("STUDENT", "Regranted Student");
		String first = grant(master, student, "MODULE", module, null, "First payment");
		mockMvc.perform(delete("/api/v1/live-exam-grants/" + first).header("Authorization", "Bearer " + master)
			.param("reason", "Refund"))
			.andExpect(status().isOk());
		clock.set(clock.instant().plusSeconds(60));
		String second = grant(master, student, "MODULE", module, null, "Second payment");

		mockMvc.perform(get("/api/v1/live-exam-modules/" + module + "/enrolments").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].grantId").value(second))
			.andExpect(jsonPath("$.content[0].reason").value("Second payment"));
	}

	private String grant(String token, UUID student, String scope, UUID module, UUID exam, String reason) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(student, scope, module, exam, reason)))
			.andExpect(status().isCreated())
			.andReturn();
		return json.readTree(result.getResponse().getContentAsString()).get("id").asText();
	}

	private UUID module(String token, String title) throws Exception {
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
		UUID id = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
		publish(token, id);
		return id;
	}

	private void publish(String token, UUID module) throws Exception {
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/final-save").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/publish").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
	}

	private UUID exam(String token, UUID module) throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/exams")
			.header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{
					  "examType":"MCQ","paperLanguage":"BN","titleEn":"Enrolment quiz","titleBn":"কুইজ",
					  "descriptionEn":"d","descriptionBn":"বি","rulesEn":"English rules","rulesBn":"নিয়ম",
					  "liveAt":"2026-10-20T12:00:00+06:00","durationMinutes":40,"passPercent":50
					}
					"""))
			.andExpect(status().isCreated())
			.andReturn();
		UUID id = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
		mockMvc.perform(post("/api/v1/live-exams/" + id + "/questions").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"stem":"Two plus two","marks":2,"explanation":"Because","options":[{"text":"Yes","correct":true},{"text":"No","correct":false}]}
					"""))
			.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/live-exams/" + id + "/final-save").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + id + "/publish").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		return id;
	}

	private static String body(UUID student, String scope, UUID module, UUID exam, String reason) {
		return """
				{"studentId":"%s","scope":"%s","moduleId":%s,"examId":%s,"reason":"%s"}
				""".formatted(student, scope, quoted(module), quoted(exam), reason);
	}

	private static String quoted(UUID id) {
		return id == null ? "null" : "\"" + id + "\"";
	}

	private UUID seed(String role, String name) {
		UUID id = UUID.randomUUID();
		String phone = "+8801" + java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999);
		new JdbcTemplate(dataSource).update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, phone, "enrol-list-" + id + "@example.com", passwords.encode("password1"), role);
		if ("STUDENT".equals(role)) {
			new JdbcTemplate(dataSource).update(
					"INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, ?, ?)", id, name,
					"E" + id.toString().substring(0, 8));
		}
		else {
			new JdbcTemplate(dataSource).update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, ?)", id, name);
		}
		return id;
	}

	private String token(UUID userId) throws Exception {
		String phone = new JdbcTemplate(dataSource).queryForObject("select phone from users where id = ?", String.class, userId);
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

	@TestConfiguration
	static class ClockConfig {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(Instant.parse("2026-10-20T04:00:00Z"));
		}

	}

}
