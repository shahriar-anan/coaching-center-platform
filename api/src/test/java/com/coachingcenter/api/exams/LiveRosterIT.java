package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, LiveRosterIT.ClockConfig.class })
class LiveRosterIT extends PostgresIntegrationTest {

	private static final Instant MORNING = Instant.parse("2026-10-20T04:00:00Z");

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
		clock.set(MORNING);
	}

	@Test
	void systemAdminMarkCreatesNoRow() throws Exception {
		String system = token(seed("SYSTEM_ADMIN", "Admin"));
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID module = publishModule(master, "Forbidden");
		Student student = seedStudent("Only Student");
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/roster").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"studentId":"%s"}
					""".formatted(student.id())))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		assertThat(rosterCount(module)).isZero();
	}

	@Test
	void emailAndPhoneEachMarkOneAccount() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID module = publishModule(master, "Direct");
		Student one = seedStudent("Email Person");
		Student two = seedStudent("Phone Person");

		JsonNode byEmail = search(master, module, one.email());
		assertThat(byEmail).hasSize(1);
		assertThat(byEmail.get(0).get("id").asText()).isEqualTo(one.id().toString());
		assertThat(byEmail.get(0).get("email").asText()).isEqualTo(one.email());
		assertThat(byEmail.get(0).get("phone").asText()).isEqualTo(one.phone());
		mark(master, module, one.id());

		JsonNode byPhone = search(master, module, two.phone());
		assertThat(byPhone).hasSize(1);
		assertThat(byPhone.get(0).get("id").asText()).isEqualTo(two.id().toString());
		mark(master, module, two.id());

		assertThat(rosterCount(module)).isEqualTo(2);
		assertThat(marked(module, one.id())).isTrue();
		assertThat(marked(module, two.id())).isTrue();
	}

	@Test
	void nameSearchReturnsBothAndMarkingOneLeavesTheOther() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID module = publishModule(master, "Names");
		String shared = "Shared " + UUID.randomUUID().toString().substring(0, 8);
		Student first = seedStudent(shared);
		Student second = seedStudent(shared);

		JsonNode matches = search(master, module, shared);
		assertThat(matches).hasSize(2);
		assertThat(matches.get(0).get("phone").asText()).isNotEqualTo(matches.get(1).get("phone").asText());
		assertThat(matches.get(0).get("email").asText()).isNotEqualTo(matches.get(1).get("email").asText());
		mark(master, module, first.id());
		assertThat(marked(module, first.id())).isTrue();
		assertThat(marked(module, second.id())).isFalse();
	}

	@Test
	void pasteMarksKnownEmailAndPhoneAndReturnsTheUnknown() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID module = publishModule(master, "Paste");
		Student byEmail = seedStudent("Paste Email");
		Student byPhone = seedStudent("Paste Phone");
		String unknown = "missing-" + UUID.randomUUID() + "@example.com";

		MvcResult result = mockMvc
			.perform(post("/api/v1/live-exam-modules/" + module + "/roster/paste").header("Authorization", "Bearer " + master)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"values":["%s","%s","%s"]}
						""".formatted(byEmail.email(), byPhone.phone(), unknown)))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode body = json.readTree(result.getResponse().getContentAsString());
		assertThat(ids(body.get("marked"))).containsExactlyInAnyOrder(byEmail.id(), byPhone.id());
		assertThat(body.get("unknown")).hasSize(1);
		assertThat(body.get("unknown").get(0).asText()).isEqualTo(unknown);
		assertThat(marked(module, byEmail.id())).isTrue();
		assertThat(marked(module, byPhone.id())).isTrue();
	}

	@Test
	void markedStudentSeesPublishedExamsIncludingOnePublishedLater() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID module = publishModule(staff, "Visible");
		Student markedStudent = seedStudent("Marked");
		Student unmarked = seedStudent("Unmarked");
		UUID published = createExam(staff, module, "Published exam", "2026-10-20T12:00:00+06:00", 50);
		finalSave(staff, published);
		publish(staff, published);
		UUID draft = createExam(staff, module, "Draft exam", "2026-10-20T12:45:00+06:00", null);
		mark(master, module, markedStudent.id());

		assertThat(examIds(markedStudent, module)).containsExactly(published);
		assertThat(examIds(unmarked, module)).isEmpty();

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
			.patch("/api/v1/live-exams/" + draft)
			.header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"passPercent\":50}"))
			.andExpect(status().isOk());
		finalSave(staff, draft);
		publish(staff, draft);
		assertThat(examIds(markedStudent, module)).containsExactlyInAnyOrder(published, draft);

		mockMvc.perform(delete("/api/v1/live-exam-modules/" + module + "/roster/" + markedStudent.id())
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		assertThat(examIds(markedStudent, module)).isEmpty();
		assertThat(marked(module, markedStudent.id())).isFalse();
	}

	private UUID publishModule(String token, String title) throws Exception {
		UUID category = createCategory(token, title + " cat");
		MvcResult created = mockMvc.perform(post("/api/v1/live-exam-modules").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"categoryId":"%s","titleEn":"%s","titleBn":"মডিউল","descriptionEn":"d","descriptionBn":"বি"}
					""".formatted(category, title)))
			.andExpect(status().isCreated())
			.andReturn();
		UUID module = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/final-save").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/publish").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		return module;
	}

	private UUID createCategory(String token, String title) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/live-exam-categories").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titleEn":"%s","titleBn":"বিভাগ"}
					""".formatted(title)))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private UUID createExam(String token, UUID moduleId, String title, String liveAt, Integer passPercent) throws Exception {
		String percent = passPercent == null ? "" : ",\"passPercent\":" + passPercent;
		MvcResult result = mockMvc
			.perform(post("/api/v1/live-exam-modules/" + moduleId + "/exams").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "examType":"MCQ",
						  "paperLanguage":"BN",
						  "titleEn":"%s",
						  "titleBn":"পরীক্ষা",
						  "descriptionEn":"d",
						  "descriptionBn":"বি",
						  "rulesEn":"r",
						  "rulesBn":"নি",
						  "liveAt":"%s",
						  "durationMinutes":40%s
						}
						""".formatted(title, liveAt, percent)))
			.andExpect(status().isCreated())
			.andReturn();
		UUID examId = UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
		mockMvc.perform(post("/api/v1/live-exams/" + examId + "/questions").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"stem":"Stem","marks":2,"options":[{"text":"A","correct":true},{"text":"B","correct":false}]}
					"""))
			.andExpect(status().isCreated());
		return examId;
	}

	private void finalSave(String token, UUID examId) throws Exception {
		mockMvc.perform(post("/api/v1/live-exams/" + examId + "/final-save").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
	}

	private void publish(String token, UUID examId) throws Exception {
		mockMvc.perform(post("/api/v1/live-exams/" + examId + "/publish").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
	}

	private void mark(String token, UUID moduleId, UUID studentId) throws Exception {
		mockMvc.perform(post("/api/v1/live-exam-modules/" + moduleId + "/roster").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"studentId":"%s"}
					""".formatted(studentId)))
			.andExpect(status().isCreated());
	}

	private JsonNode search(String token, UUID moduleId, String query) throws Exception {
		MvcResult result = mockMvc
			.perform(get("/api/v1/live-exam-modules/" + moduleId + "/roster/search").header("Authorization", "Bearer " + token)
				.param("q", query))
			.andExpect(status().isOk())
			.andReturn();
		return json.readTree(result.getResponse().getContentAsString());
	}

	private List<UUID> examIds(Student student, UUID moduleId) throws Exception {
		MvcResult result = mockMvc
			.perform(get("/api/v1/live-exam-modules/" + moduleId + "/exams").header("Authorization", "Bearer " + student.token())
				.param("size", "100"))
			.andExpect(status().isOk())
			.andReturn();
		List<UUID> ids = new ArrayList<>();
		for (JsonNode node : json.readTree(result.getResponse().getContentAsString()).get("content")) {
			ids.add(UUID.fromString(node.get("id").asText()));
		}
		return ids;
	}

	private static List<UUID> ids(JsonNode nodes) {
		List<UUID> ids = new ArrayList<>();
		for (JsonNode node : nodes) {
			ids.add(UUID.fromString(node.get("id").asText()));
		}
		return ids;
	}

	private int rosterCount(UUID moduleId) {
		return new JdbcTemplate(dataSource).queryForObject("select count(*) from live_exam_roster where module_id = ?",
				Integer.class, moduleId);
	}

	private boolean marked(UUID moduleId, UUID studentId) {
		Integer count = new JdbcTemplate(dataSource).queryForObject(
				"select count(*) from live_exam_roster where module_id = ? and student_id = ?", Integer.class, moduleId,
				studentId);
		return count != null && count == 1;
	}

	private UUID seed(String role, String name) {
		UUID id = UUID.randomUUID();
		new JdbcTemplate(dataSource).update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, uniquePhone(), uniqueEmail(), passwords.encode("password1"), role);
		new JdbcTemplate(dataSource).update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, ?)", id, name);
		return id;
	}

	private Student seedStudent(String name) throws Exception {
		UUID id = UUID.randomUUID();
		String phone = uniquePhone();
		String email = uniqueEmail();
		new JdbcTemplate(dataSource).update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, 'STUDENT', 'ACTIVE', now(), now())
				""", id, phone, email, passwords.encode("password1"));
		new JdbcTemplate(dataSource).update(
				"INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, ?, ?)", id, name,
				"S" + id.toString().substring(0, 8));
		return new Student(id, phone, email, token(id));
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
		return "live-roster-" + UUID.randomUUID() + "@example.com";
	}

	private record Student(UUID id, String phone, String email, String token) {
	}

	@TestConfiguration
	static class ClockConfig {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(MORNING);
		}

	}

}
