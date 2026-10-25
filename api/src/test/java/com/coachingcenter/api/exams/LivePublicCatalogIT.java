package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import javax.sql.DataSource;

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
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class LivePublicCatalogIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Test
	void publishedCatalogIsPublicAndHidesThePaper() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		UUID studentId = seed("STUDENT");
		String student = token(studentId);
		UUID category = createCategory(staff, "Public Law");
		UUID module = createModule(staff, category, "Public Module");
		finalSave(staff, module);
		publishModule(staff, module);
		UUID exam = createExam(staff, module, "EN", "2099-01-01T10:00:00+06:00", "SECRET-STEM-TOKEN", true);

		String anonymousExam = publicExam(null, exam);
		String studentExam = publicExam(student, exam);
		assertThat(withoutAccess(studentExam)).isEqualTo(json.readTree(anonymousExam));
		assertThat(json.readTree(anonymousExam).has("access")).isFalse();
		assertAccess(json.readTree(studentExam), "NONE", false, "PRICE_REQUIRED");
		JsonNode body = json.readTree(anonymousExam);
		assertThat(body.get("paperLanguage").asText()).isEqualTo("EN");
		assertThat(body.get("titleEn").asText()).isEqualTo("Public paper");
		assertThat(body.get("rulesBn").asText()).isEqualTo("নিয়ম");
		assertThat(body.has("questions")).isFalse();
		assertThat(body.has("stem")).isFalse();
		assertThat(body.has("options")).isFalse();
		assertThat(body.has("correct")).isFalse();
		assertThat(anonymousExam).doesNotContain("SECRET-STEM-TOKEN", "SECRET-OPTION-TOKEN", "%PDF");
		mockMvc.perform(get("/api/v1/public/live-exams/" + exam + "/question-pdf")).andExpect(status().isNotFound());

		String anonymousModule = publicModule(null, module);
		assertThat(withoutAccess(publicModule(student, module))).isEqualTo(json.readTree(anonymousModule));
		assertAccess(json.readTree(publicModule(student, module)), "NONE", false, "PRICE_REQUIRED");
		new JdbcTemplate(dataSource).update("update live_exam_modules set price = 500.00 where id = ?", module);
		assertAccess(json.readTree(publicModule(student, module)), "NONE", false, "CHECKOUT_OFF");
		assertThat(json.readTree(anonymousModule).get("descriptionEn").asText()).isEqualTo("English description");
		assertThat(json.readTree(anonymousModule).has("accessMode")).isFalse();

		JsonNode tree = json.readTree(mockMvc.perform(get("/api/v1/public/live-exam-categories"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString());
		JsonNode node = find(tree, category);
		assertThat(node).isNotNull();
		assertThat(node.get("titleBn").asText()).isEqualTo("পাবলিক");
		assertThat(node.get("modules").toString()).contains(module.toString());

		mockMvc.perform(get("/api/v1/public/live-exam-modules").param("categoryId", category.toString()).param("size", "50"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].id").value(module.toString()))
			.andExpect(jsonPath("$.content[0].descriptionBn").value("বাংলা"));
		mockMvc.perform(get("/api/v1/public/live-exam-modules/" + module + "/exams"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].id").value(exam.toString()))
			.andExpect(jsonPath("$.content[0].questions").doesNotExist())
			.andExpect(jsonPath("$.content[0].paperLanguage").value("EN"));

		mockMvc.perform(get("/api/v1/live-exams/" + exam)).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + student))
			.andExpect(status().isNotFound());
		mark(module, studentId);
		assertThat(withoutAccess(publicExam(student, exam))).isEqualTo(json.readTree(anonymousExam));
		assertAccess(json.readTree(publicExam(student, exam)), "ROSTER", false, "ALREADY_HAS_ACCESS");
		mockMvc.perform(get("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paperLanguage").value("EN"))
			.andExpect(jsonPath("$.questions").doesNotExist())
			.andExpect(jsonPath("$.stem").doesNotExist());
		mockMvc.perform(get("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.questions[0].stem").value("SECRET-STEM-TOKEN"));
	}

	@Test
	void draftsAndArchivedBranchesStayOffThePublicRead() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		UUID science = createCategory(staff, "Hidden Science", null);
		UUID physics = createCategory(staff, "Hidden Physics", science);
		UUID module = createModule(staff, physics, "Hidden Module");
		UUID draftModule = createModule(staff, physics, "Draft Module");
		finalSave(staff, module);
		publishModule(staff, module);
		UUID exam = createExam(staff, module, "BN", "2099-01-01T10:00:00+06:00", "Visible stem", true);
		UUID draftExam = createExam(staff, module, "BN", "2099-06-01T10:00:00+06:00", "Draft stem", false);

		mockMvc.perform(get("/api/v1/public/live-exam-modules/" + draftModule)).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/live-exam-modules/" + draftModule).header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DRAFT"));
		mockMvc.perform(get("/api/v1/public/live-exams/" + draftExam)).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/live-exams/" + draftExam).header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DRAFT"));
		mockMvc.perform(get("/api/v1/public/live-exams/" + exam)).andExpect(status().isOk());
		String listed = mockMvc.perform(get("/api/v1/public/live-exam-modules/" + module + "/exams").param("size", "50"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(listed).contains(exam.toString());
		assertThat(listed).doesNotContain(draftExam.toString(), "Draft stem");
		mockMvc.perform(get("/api/v1/public/live-exam-modules/" + draftModule + "/exams")).andExpect(status().isNotFound());

		mockMvc.perform(post("/api/v1/live-exam-categories/" + science + "/archive").header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		JsonNode tree = json.readTree(mockMvc.perform(get("/api/v1/public/live-exam-categories"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString());
		assertThat(find(tree, science)).isNull();
		assertThat(find(tree, physics)).isNull();
		mockMvc.perform(get("/api/v1/public/live-exam-modules/" + module)).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/public/live-exams/" + exam)).andExpect(status().isNotFound());
		JsonNode staffTree = json.readTree(mockMvc.perform(get("/api/v1/live-exam-categories")
			.header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString());
		assertThat(find(staffTree, science).get("archived").asBoolean()).isTrue();
	}

	@Test
	void paperLanguageIsRequiredUntilTheWindowStarts() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		UUID category = createCategory(staff, "Language Law");
		UUID module = createModule(staff, category, "Language Module");
		finalSave(staff, module);
		publishModule(staff, module);

		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/exams").header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content(examBody(null, "2099-01-01T10:00:00+06:00")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/exams").header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content(examBody("BOTH", "2099-01-01T10:00:00+06:00")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/exams").header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content(examBody("bn", "2099-01-01T10:00:00+06:00")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		UUID future = createExam(staff, module, "BN", "2099-01-01T10:00:00+06:00", "Later", true);
		mockMvc.perform(patch("/api/v1/live-exams/" + future).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"paperLanguage\":\"bn\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		mockMvc.perform(get("/api/v1/public/live-exams/" + future))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paperLanguage").value("BN"));
		mockMvc.perform(patch("/api/v1/live-exams/" + future).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"paperLanguage\":\"EN\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paperLanguage").value("EN"))
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.finallySaved").value(false));
		mockMvc.perform(get("/api/v1/public/live-exams/" + future)).andExpect(status().isNotFound());

		UUID past = createBareExam(staff, module, "BN", "2020-01-01T10:00:00+06:00");
		mockMvc.perform(patch("/api/v1/live-exams/" + past).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"paperLanguage\":\"EN\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONTENT_LOCKED"));
		mockMvc.perform(get("/api/v1/live-exams/" + past).header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paperLanguage").value("BN"));
	}

	@Test
	void anUndatedPublishedExamIsListedWithABlankStart() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		UUID category = createCategory(staff, "Open Law");
		UUID module = createModule(staff, category, "Open Module");
		finalSave(staff, module);
		publishModule(staff, module);
		MvcResult created = mockMvc
			.perform(post("/api/v1/live-exam-modules/" + module + "/exams").header("Authorization", "Bearer " + staff)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "examType":"MCQ","paperLanguage":"BN","titleEn":"Public paper","titleBn":"খাতা",
						  "descriptionEn":"d","descriptionBn":"বি","rulesEn":"English rules","rulesBn":"নিয়ম",
						  "liveAt":null,"durationMinutes":40,"passPercent":50
						}
						"""))
			.andExpect(status().isCreated())
			.andReturn();
		UUID exam = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/questions").header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"stem":"Visible stem","marks":2,"options":[{"text":"Yes","correct":true},{"text":"No","correct":false}]}
					"""))
			.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/final-save").header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk());

		JsonNode body = json.readTree(publicExam(null, exam));
		assertThat(body.has("liveAt")).isFalse();
		assertThat(body.has("endAt")).isFalse();
		assertThat(body.has("questions")).isFalse();
		JsonNode listed = json.readTree(mockMvc.perform(get("/api/v1/public/live-exam-modules/" + module + "/exams"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString());
		assertThat(listed.get("content").get(0).get("id").asText()).isEqualTo(exam.toString());
		assertThat(listed.get("content").get(0).has("liveAt")).isFalse();
	}

	@Test
	void eachDoorKeepsOnlyItsTypeAndAnEmptyCategoryShowsInBoth() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		UUID root = createCategory(staff, "Doors");
		UUID mixedCategory = createCategory(staff, "Doors mixed", root);
		UUID writtenCategory = createCategory(staff, "Doors written", root);
		UUID empty = createCategory(staff, "Doors empty");
		UUID emptyChild = createCategory(staff, "Doors empty child", empty);
		UUID mixed = createModule(staff, mixedCategory, "Mixed module");
		finalSave(staff, mixed);
		publishModule(staff, mixed);
		UUID writtenOnly = createModule(staff, writtenCategory, "Written module");
		finalSave(staff, writtenOnly);
		publishModule(staff, writtenOnly);
		UUID firstMcq = createExam(staff, mixed, "BN", "2099-01-01T10:00:00+06:00", "Stem", true);
		UUID secondMcq = createExam(staff, mixed, "BN", "2099-01-02T10:00:00+06:00", "Stem", true);
		UUID mixedWritten = written(createExam(staff, mixed, "BN", "2099-01-03T10:00:00+06:00", "Stem", true));
		UUID onlyWritten = written(createExam(staff, writtenOnly, "BN", "2099-01-04T10:00:00+06:00", "Stem", true));

		JsonNode mcqDoor = categories("MCQ");
		assertThat(find(mcqDoor, root)).isNotNull();
		JsonNode mixedNode = find(mcqDoor, mixedCategory);
		assertThat(mixedNode.get("modules").size()).isEqualTo(1);
		assertThat(mixedNode.get("modules").get(0).get("id").asText()).isEqualTo(mixed.toString());
		assertThat(mixedNode.get("modules").get(0).get("mcqCount").asInt()).isEqualTo(2);
		assertThat(mixedNode.get("modules").get(0).get("writtenCount").asInt()).isEqualTo(1);
		assertThat(find(mcqDoor, writtenCategory)).isNull();
		assertThat(find(mcqDoor, empty)).isNotNull();
		assertThat(find(mcqDoor, emptyChild)).isNotNull();

		JsonNode writtenDoor = categories("written");
		assertThat(find(writtenDoor, mixedCategory).get("modules").get(0).get("id").asText()).isEqualTo(mixed.toString());
		assertThat(find(writtenDoor, writtenCategory).get("modules").get(0).get("mcqCount").asInt()).isZero();
		assertThat(find(writtenDoor, writtenCategory).get("modules").get(0).get("writtenCount").asInt()).isEqualTo(1);
		assertThat(find(writtenDoor, emptyChild)).isNotNull();
		assertThat(find(categories(null), writtenCategory)).isNotNull();

		mockMvc.perform(get("/api/v1/public/live-exam-modules").param("categoryId", writtenCategory.toString())
			.param("examType", "MCQ"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(0));
		mockMvc.perform(get("/api/v1/public/live-exam-modules").param("categoryId", mixedCategory.toString())
			.param("examType", "MCQ"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].mcqCount").value(2));
		mockMvc.perform(get("/api/v1/public/live-exam-modules").param("examType", "WRITTEN").param("size", "100"))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/public/live-exam-modules/" + mixed))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mcqCount").value(2))
			.andExpect(jsonPath("$.writtenCount").value(1));
		mockMvc.perform(get("/api/v1/public/live-exam-modules/" + mixed + "/exams").param("examType", "MCQ"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.content[0].id").value(firstMcq.toString()))
			.andExpect(jsonPath("$.content[1].id").value(secondMcq.toString()));
		mockMvc.perform(get("/api/v1/public/live-exam-modules/" + mixed + "/exams").param("examType", "WRITTEN"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].id").value(mixedWritten.toString()));
		mockMvc.perform(get("/api/v1/public/live-exam-modules/" + mixed + "/exams"))
			.andExpect(jsonPath("$.totalElements").value(3));

		UUID studentId = seed("STUDENT");
		String student = token(studentId);
		mark(mixed, studentId);
		mark(writtenOnly, studentId);
		mockMvc.perform(get("/api/v1/live-exam-modules").header("Authorization", "Bearer " + student)
			.param("examType", "MCQ"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].id").value(mixed.toString()));
		mockMvc.perform(get("/api/v1/live-exam-modules").header("Authorization", "Bearer " + student)
			.param("categoryId", writtenCategory.toString())
			.param("examType", "WRITTEN"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1));
		mockMvc.perform(get("/api/v1/live-exam-modules").header("Authorization", "Bearer " + student))
			.andExpect(jsonPath("$.totalElements").value(2));
		mockMvc.perform(get("/api/v1/live-exam-modules/" + mixed + "/exams").header("Authorization", "Bearer " + student)
			.param("examType", "WRITTEN"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].id").value(mixedWritten.toString()));
		mockMvc.perform(get("/api/v1/live-exam-modules/" + writtenOnly + "/exams").header("Authorization", "Bearer " + student)
			.param("examType", "MCQ"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(0));
		assertThat(onlyWritten).isNotNull();

		for (String path : new String[] { "/api/v1/public/live-exam-categories", "/api/v1/public/live-exam-modules",
				"/api/v1/public/live-exam-modules/" + mixed + "/exams" }) {
			mockMvc.perform(get(path).param("examType", "ESSAY"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		}
		for (String path : new String[] { "/api/v1/live-exam-modules", "/api/v1/live-exam-modules/" + mixed + "/exams" }) {
			mockMvc.perform(get(path).header("Authorization", "Bearer " + student).param("examType", "ESSAY"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		}
	}

	private JsonNode withoutAccess(String body) {
		ObjectNode node = (ObjectNode) json.readTree(body);
		node.remove("access");
		return node;
	}

	private static void assertAccess(JsonNode body, String state, boolean buyable, String blockedReason) {
		JsonNode access = body.get("access");
		assertThat(access.get("state").asText()).isEqualTo(state);
		assertThat(access.get("buyable").asBoolean()).isEqualTo(buyable);
		assertThat(access.get("blockedReason").asText()).isEqualTo(blockedReason);
	}

	private JsonNode categories(String examType) throws Exception {
		var request = get("/api/v1/public/live-exam-categories");
		if (examType != null) {
			request.param("examType", examType);
		}
		return json.readTree(mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse()
			.getContentAsString());
	}

	/** Turns a published MCQ row into a written one, so a test needs no question PDF upload. */
	private UUID written(UUID examId) {
		new JdbcTemplate(dataSource).update(
				"update live_exams set exam_type = 'WRITTEN', written_maximum = 10 where id = ?", examId);
		return examId;
	}

	private String publicExam(String token, UUID examId) throws Exception {
		var request = get("/api/v1/public/live-exams/" + examId);
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		return mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private String publicModule(String token, UUID moduleId) throws Exception {
		var request = get("/api/v1/public/live-exam-modules/" + moduleId);
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		return mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private UUID createCategory(String token, String title) throws Exception {
		return createCategory(token, title, null);
	}

	private UUID createCategory(String token, String title, UUID parentId) throws Exception {
		String parent = parentId == null ? "" : ",\"parentId\":\"" + parentId + "\"";
		MvcResult result = mockMvc.perform(post("/api/v1/live-exam-categories").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titleEn":"%s","titleBn":"পাবলিক"%s}
					""".formatted(title, parent)))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private UUID createModule(String token, UUID categoryId, String title) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/live-exam-modules").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"categoryId":"%s","titleEn":"%s","titleBn":"মডিউল","descriptionEn":"English description","descriptionBn":"বাংলা"}
					""".formatted(categoryId, title)))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private void finalSave(String token, UUID moduleId) throws Exception {
		mockMvc.perform(post("/api/v1/live-exam-modules/" + moduleId + "/final-save").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
	}

	private void publishModule(String token, UUID moduleId) throws Exception {
		mockMvc.perform(post("/api/v1/live-exam-modules/" + moduleId + "/publish").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
	}

	private UUID createBareExam(String token, UUID moduleId, String language, String liveAt) throws Exception {
		MvcResult created = mockMvc
			.perform(post("/api/v1/live-exam-modules/" + moduleId + "/exams").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(examBody(language, liveAt)))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
	}

	private UUID createExam(String token, UUID moduleId, String language, String liveAt, String stem, boolean publish)
			throws Exception {
		MvcResult created = mockMvc
			.perform(post("/api/v1/live-exam-modules/" + moduleId + "/exams").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(examBody(language, liveAt)))
			.andExpect(status().isCreated())
			.andReturn();
		UUID exam = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/questions").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"stem":"%s","marks":2,"options":[{"text":"SECRET-OPTION-TOKEN","correct":true},{"text":"No","correct":false}]}
					""".formatted(stem)))
			.andExpect(status().isCreated());
		if (publish) {
			mockMvc.perform(post("/api/v1/live-exams/" + exam + "/final-save").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());
			mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());
		}
		return exam;
	}

	private static String examBody(String language, String liveAt) {
		String paper = language == null ? "" : ",\"paperLanguage\":\"" + language + "\"";
		return """
				{
				  "examType":"MCQ","titleEn":"Public paper","titleBn":"খাতা",
				  "descriptionEn":"d","descriptionBn":"বি","rulesEn":"English rules","rulesBn":"নিয়ম",
				  "liveAt":"%s","durationMinutes":40,"passPercent":50%s
				}
				""".formatted(liveAt, paper);
	}

	private JsonNode find(JsonNode nodes, UUID id) {
		if (nodes == null || !nodes.isArray()) {
			return null;
		}
		for (JsonNode node : nodes) {
			if (id.toString().equals(node.path("id").asText())) {
				return node;
			}
			JsonNode found = find(node.get("children"), id);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	private void mark(UUID moduleId, UUID studentId) {
		new JdbcTemplate(dataSource).update(
				"insert into live_exam_roster (module_id, student_id, created_at) values (?, ?, now())", moduleId,
				studentId);
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
		return "live-public-" + UUID.randomUUID() + "@example.com";
	}

}
