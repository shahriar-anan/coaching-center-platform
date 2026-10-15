package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class QuestionBankIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	private String system;

	private String master;

	private UUID module;

	private UUID paperA;

	private UUID paperB;

	private UUID mcqQuestion;

	private UUID writtenQuestion;

	private UUID mcqComponent;

	@BeforeEach
	void seed() throws Exception {
		system = token(seed("SYSTEM_ADMIN"));
		master = token(seed("MASTER_ADMIN"));
		module = createModule();
		paperA = createPaper();
		paperB = createPaper();
		mcqComponent = createComponent(paperA, "MCQ");
		UUID writtenComponent = createComponent(paperB, "WRITTEN");
		mcqQuestion = createMcq(mcqComponent);
		writtenQuestion = createWritten(writtenComponent);
	}

	@Test
	void questionBankCuration() throws Exception {
		String student = registerStudent();
		mockMvc.perform(get("/api/v1/question-bank/questions").header("Authorization", "Bearer " + system))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/question-bank/questions").param("size", "200").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
		assertThat(browseRow(mcqQuestion).get("paperIds").toString()).contains(paperA.toString());
		assertThat(browseRow(writtenQuestion).get("paperIds").toString()).contains(paperB.toString());
		mockMvc.perform(post("/api/v1/question-banks").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Bank\",\"questionIds\":[\"" + mcqQuestion + "\"]}"))
			.andExpect(status().isForbidden());

		mockMvc
			.perform(get("/api/v1/question-bank/questions").param("paperId", paperA.toString())
				.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(1));
		mockMvc
			.perform(get("/api/v1/question-bank/questions").param("paperId", paperB.toString())
				.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(1));

		mockMvc
			.perform(get("/api/v1/question-bank/questions").param("paperId", paperA.toString())
				.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(1));

		mockMvc
			.perform(get("/api/v1/question-bank/questions").param("questionType", "WRITTEN").param("paperId",
					paperB.toString())
				.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[?(@.id == '%s')]".formatted(writtenQuestion)).exists());

		mockMvc.perform(post("/api/v1/question-banks").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Mixed\",\"questionIds\":[]}"))
			.andExpect(status().isBadRequest());

		MvcResult created = mockMvc.perform(post("/api/v1/question-banks").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"name":"Mixed","questionIds":["%s","%s"]}
					""".formatted(mcqQuestion, writtenQuestion)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.questions[0].id").value(mcqQuestion.toString()))
			.andReturn();
		UUID bankId = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());

		Integer nameColumns = new JdbcTemplate(dataSource).queryForObject("""
				select count(*) from information_schema.columns
				where table_name = 'question_banks' and column_name in ('name_en','name_bn')
				""", Integer.class);
		assertThat(nameColumns).isZero();

		mockMvc.perform(get("/api/v1/exam-components/" + mcqComponent).header("Authorization", "Bearer " + system))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.questions[0].id").value(mcqQuestion.toString()));

		mockMvc.perform(get("/api/v1/question-banks/" + bankId).header("Authorization", "Bearer " + student))
			.andExpect(status().isForbidden());

		mockMvc.perform(delete("/api/v1/question-banks/" + bankId + "/questions/" + mcqQuestion).header("Authorization",
				"Bearer " + master))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/question-banks/" + bankId).header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.questions.length()").value(1));
		mockMvc.perform(get("/api/v1/exam-components/" + mcqComponent).header("Authorization", "Bearer " + system))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.questions[?(@.id == '%s')]".formatted(mcqQuestion)).exists());

		mockMvc.perform(post("/api/v1/exam-components/" + mcqComponent + "/questions").header("Authorization",
				"Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"stem":"New","marks":1,"subject":"S","options":[{"text":"a","correct":true},{"text":"b","correct":false}]}
					"""))
			.andExpect(status().isCreated());

		String docs = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse()
			.getContentAsString();
		assertThat(docs).doesNotContain("/question-banks/{id}/attempts");
	}

	private JsonNode browseRow(UUID questionId) throws Exception {
		int page = 0;
		while (page < 30) {
			MvcResult result = mockMvc
				.perform(get("/api/v1/question-bank/questions").param("page", Integer.toString(page)).param("size", "100")
					.header("Authorization", "Bearer " + master))
				.andExpect(status().isOk())
				.andReturn();
			JsonNode content = json.readTree(result.getResponse().getContentAsString()).get("content");
			if (content == null || content.isEmpty()) {
				break;
			}
			for (JsonNode row : content) {
				if (questionId.toString().equals(row.get("id").asText())) {
					return row;
				}
			}
			page++;
		}
		throw new AssertionError("Question missing from the bank browse list: " + questionId);
	}

	private String registerStudent() throws Exception {
		String phone = "+8801" + java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999);
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"S","phone":"%s","email":"bank-%s@example.com","password":"password1"}
					""".formatted(phone, UUID.randomUUID())))
			.andExpect(status().isCreated());
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

	private UUID createMcq(UUID componentId) throws Exception {
		MvcResult result = mockMvc
			.perform(post("/api/v1/exam-components/" + componentId + "/questions").header("Authorization",
					"Bearer " + system)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"stem":"MCQ","marks":2,"subject":"S","options":[{"text":"a","correct":true},{"text":"b","correct":false}]}
						"""))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private UUID createWritten(UUID componentId) throws Exception {
		MvcResult result = mockMvc
			.perform(post("/api/v1/exam-components/" + componentId + "/questions").header("Authorization",
					"Bearer " + system)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"stem\":\"W\",\"marks\":3,\"subject\":\"S\"}"))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private UUID createComponent(UUID paperId, String type) throws Exception {
		MvcResult result = mockMvc
			.perform(post("/api/v1/exam-papers/" + paperId + "/components").header("Authorization", "Bearer " + system)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"type\":\"" + type + "\"}"))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private UUID createPaper() throws Exception {
		MvcResult n = mockMvc
			.perform(post("/api/v1/exam-modules/" + module + "/nodes").header("Authorization", "Bearer " + system)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nodeType\":\"PAPER\",\"titleEn\":\"P\",\"titleBn\":\"প\"}"))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(n.getResponse().getContentAsString()).get("id").asText());
	}

	private UUID createModule() throws Exception {
		MvcResult m = mockMvc.perform(post("/api/v1/exam-modules").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"titleEn\":\"QB\",\"titleBn\":\"ম\",\"descriptionEn\":\"d\",\"descriptionBn\":\"b\"}"))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(m.getResponse().getContentAsString()).get("id").asText());
	}

	private UUID seed(String role) {
		UUID id = UUID.randomUUID();
		String phone = uniquePhone();
		new JdbcTemplate(dataSource).update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, phone, uniqueEmail(), passwords.encode("password1"), role);
		if ("MASTER_ADMIN".equals(role)) {
			new JdbcTemplate(dataSource).update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, 'Admin')", id);
		}
		else if ("SYSTEM_ADMIN".equals(role)) {
			new JdbcTemplate(dataSource).update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, 'Admin')", id);
		}
		return id;
	}

	private String token(UUID userId) throws Exception {
		String phone = new JdbcTemplate(dataSource).queryForObject("select phone from users where id = ?", String.class,
				userId);
		return tokenFromLogin(phone, "password1");
	}

	private String tokenFromLogin(String phone, String password) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"identifier":"%s","password":"%s"}
					""".formatted(phone, password)))
			.andExpect(status().isOk())
			.andReturn();
		String body = result.getResponse().getContentAsString();
		String marker = "\"accessToken\":\"";
		int start = body.indexOf(marker) + marker.length();
		return body.substring(start, body.indexOf('"', start));
	}

	private static String uniquePhone() {
		return "+8801" + java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999);
	}

	private static String uniqueEmail() {
		return "bank-" + UUID.randomUUID() + "@example.com";
	}

}
