package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class LiveCatalogIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Test
	void creatingACategoryRequiresBothTitlesAndStoresAThirdLevelInParentOrder() throws Exception {
		String system = token(seed("SYSTEM_ADMIN"));

		mockMvc.perform(post("/api/v1/live-exam-categories").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"titleEn\":\"Only English\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		mockMvc.perform(post("/api/v1/live-exam-categories").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"titleBn\":\"শুধু বাংলা\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		UUID science = createCategory(system, "Science", "বিজ্ঞান", null, null);
		UUID physics = createCategory(system, "Physics", "পদার্থ", science, 0);
		UUID paper = createCategory(system, "Physics 1st paper", "পদার্থ প্রথম পত্র", physics, 0);

		UUID storedParent = new JdbcTemplate(dataSource).queryForObject(
				"select parent_id from live_exam_categories where id = ?", UUID.class, paper);
		assertThat(storedParent).isEqualTo(physics);

		JsonNode tree = tree(system);
		assertThat(rootIds(tree)).contains(science).doesNotContain(physics, paper);
		JsonNode scienceNode = find(tree, science);
		assertThat(scienceNode.get("children")).hasSize(1);
		JsonNode physicsNode = scienceNode.get("children").get(0);
		assertThat(physicsNode.get("id").asText()).isEqualTo(physics.toString());
		assertThat(physicsNode.get("parentId").asText()).isEqualTo(science.toString());
		assertThat(physicsNode.get("children")).hasSize(1);
		JsonNode paperNode = physicsNode.get("children").get(0);
		assertThat(paperNode.get("id").asText()).isEqualTo(paper.toString());
		assertThat(paperNode.get("titleEn").asText()).isEqualTo("Physics 1st paper");
		assertThat(paperNode.get("titleBn").asText()).isEqualTo("পদার্থ প্রথম পত্র");
		assertThat(paperNode.get("parentId").asText()).isEqualTo(physics.toString());
		assertThat(preorder(scienceNode)).containsExactly("Science", "Physics", "Physics 1st paper");
	}

	@Test
	void categoryParentCycleIsRejected() throws Exception {
		String system = token(seed("SYSTEM_ADMIN"));
		UUID root = createCategory(system, "Cycle root", "চক্র মূল", null, null);
		UUID child = createCategory(system, "Cycle child", "চক্র শাখা", root, 0);
		UUID grandchild = createCategory(system, "Cycle leaf", "চক্র পাতা", child, 0);

		mockMvc.perform(patch("/api/v1/live-exam-categories/" + root).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"parentId":"%s"}
					""".formatted(grandchild)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("CATEGORY_CYCLE"));
		mockMvc.perform(patch("/api/v1/live-exam-categories/" + child).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"parentId":"%s"}
					""".formatted(child)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("CATEGORY_CYCLE"));

		JsonNode tree = tree(system);
		JsonNode rootParent = find(tree, root).get("parentId");
		assertThat(rootParent == null || rootParent.isNull()).isTrue();
		assertThat(find(tree, child).get("parentId").asText()).isEqualTo(root.toString());
	}

	@Test
	void twoModulesInOneCategoryAreReturnedAndARootModuleIsReturnedThere() throws Exception {
		String system = token(seed("SYSTEM_ADMIN"));
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		Integer oldModules = jdbc.queryForObject("select count(*) from exam_modules", Integer.class);
		Integer oldNodes = jdbc.queryForObject("select count(*) from exam_nodes", Integer.class);

		UUID science = createCategory(system, "Root science", "মূল বিজ্ঞান", null, null);
		UUID physics = createCategory(system, "Nested physics", "নেস্টেড পদার্থ", science, 0);
		UUID first = createModule(system, physics, "Weekly physics", "সাপ্তাহিক পদার্থ", 0);
		UUID second = createModule(system, physics, "Monthly physics", "মাসিক পদার্থ", 1);
		UUID rootModule = createModule(system, science, "Science overview", "বিজ্ঞান পরিচিতি", 0);

		assertThat(jdbc.queryForObject("select count(*) from exam_modules", Integer.class)).isEqualTo(oldModules);
		assertThat(jdbc.queryForObject("select count(*) from exam_nodes", Integer.class)).isEqualTo(oldNodes);
		assertThat(jdbc.queryForObject(
				"select count(*) from live_exam_modules where id = ? and access_mode = 'MANUAL' and status = 'DRAFT'",
				Integer.class, first)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from exam_modules where id = ?", Integer.class, first)).isZero();

		JsonNode scienceNode = find(tree(system), science);
		assertThat(moduleIds(scienceNode.get("modules"))).containsExactly(rootModule);
		JsonNode physicsNode = find(scienceNode.get("children"), physics);
		assertThat(moduleIds(physicsNode.get("modules"))).containsExactly(first, second);

		assertThat(moduleIds(moduleList(system, physics))).containsExactly(first, second);
		assertThat(moduleIds(moduleList(system, science))).containsExactly(rootModule);
	}

	@Test
	void staffEditStaysDraftUntilFinalSaveThenPublish() throws Exception {
		String system = token(seed("SYSTEM_ADMIN"));
		UUID category = createCategory(system, "Publish shelf", "প্রকাশ তাক", null, null);
		UUID module = createModule(system, category, "Algebra", "বীজগণিত", null);

		mockMvc.perform(get("/api/v1/live-exam-modules/" + module).header("Authorization", "Bearer " + system))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.finallySaved").value(false))
			.andExpect(jsonPath("$.accessMode").value("MANUAL"));

		mockMvc.perform(patch("/api/v1/live-exam-modules/" + module).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"titleEn\":\"Algebra revised\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.finallySaved").value(false));

		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/publish")
			.header("Authorization", "Bearer " + system))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("FINAL_SAVE_REQUIRED"));

		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/final-save")
			.header("Authorization", "Bearer " + system))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.finallySaved").value(true));
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/publish")
			.header("Authorization", "Bearer " + system))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PUBLISHED"));

		mockMvc.perform(patch("/api/v1/live-exam-modules/" + module).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"descriptionEn\":\"Edited after publish\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.finallySaved").value(false));
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/publish")
			.header("Authorization", "Bearer " + system))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("FINAL_SAVE_REQUIRED"));
	}

	@Test
	void studentListOmitsDraftUntilPublish() throws Exception {
		String system = token(seed("SYSTEM_ADMIN"));
		UUID studentId = seed("STUDENT");
		String student = token(studentId);
		UUID category = createCategory(system, "Student shelf", "ছাত্র তাক", null, null);
		UUID draft = createModule(system, category, "Still draft", "এখনো খসড়া", 0);
		UUID published = createModule(system, category, "Ready", "প্রস্তুত", 1);
		mark(draft, studentId);
		mark(published, studentId);
		finalSave(system, published);
		publish(system, published);

		mockMvc.perform(get("/api/v1/live-exam-categories").header("Authorization", "Bearer " + student))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/live-exam-modules/" + draft).header("Authorization", "Bearer " + student))
			.andExpect(status().isNotFound());

		assertThat(moduleIds(moduleList(student, category))).containsExactly(published);

		finalSave(system, draft);
		publish(system, draft);
		assertThat(moduleIds(moduleList(student, category))).containsExactly(draft, published);
	}

	@Test
	void systemAdminDeleteIsForbiddenAndMasterAdminSoftDeletes() throws Exception {
		UUID masterId = seed("MASTER_ADMIN");
		String master = token(masterId);
		String system = token(seed("SYSTEM_ADMIN"));
		UUID category = createCategory(system, "To delete", "মুছতে", null, null);
		UUID module = createModule(system, category, "To delete module", "মডিউল মুছতে", null);
		finalSave(system, module);
		publish(system, module);
		UUID studentId = seed("STUDENT");
		String student = token(studentId);
		mark(module, studentId);

		mockMvc.perform(delete("/api/v1/live-exam-categories/" + category).header("Authorization", "Bearer " + system))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		mockMvc.perform(post("/api/v1/live-exam-categories/" + category + "/archive")
			.header("Authorization", "Bearer " + system))
			.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/v1/live-exam-modules/" + module).header("Authorization", "Bearer " + system))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/archive")
			.header("Authorization", "Bearer " + system))
			.andExpect(status().isForbidden());
		assertThat(moduleIds(moduleList(student, category))).containsExactly(module);

		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/archive").header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/live-exam-modules/" + module).header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ARCHIVED"));
		assertThat(moduleIds(moduleList(student, category))).isEmpty();
		Integer archivedRow = new JdbcTemplate(dataSource).queryForObject(
				"select count(*) from live_exam_modules where id = ? and status = 'ARCHIVED' and deleted_at is null",
				Integer.class, module);
		assertThat(archivedRow).isEqualTo(1);

		mockMvc.perform(delete("/api/v1/live-exam-modules/" + module).header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/live-exam-modules/" + module).header("Authorization", "Bearer " + master))
			.andExpect(status().isNotFound());
		assertThat(moduleIds(find(tree(master), category).get("modules"))).isEmpty();
		Integer deletedModule = new JdbcTemplate(dataSource).queryForObject(
				"select count(*) from live_exam_modules where id = ? and deleted_at is not null", Integer.class, module);
		assertThat(deletedModule).isEqualTo(1);
		Integer moduleAudit = new JdbcTemplate(dataSource).queryForObject(
				"select count(*) from audit_log where action = 'LIVE_EXAM_MODULE_DELETE' and entity_type = 'LIVE_EXAM_MODULE' and entity_id = ? and actor_user_id = ?",
				Integer.class, module, masterId);
		assertThat(moduleAudit).isEqualTo(1);

		UUID empty = createCategory(system, "Empty shelf", "খালি তাক", null, null);
		mockMvc.perform(delete("/api/v1/live-exam-categories/" + empty).header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/live-exam-categories/" + empty).header("Authorization", "Bearer " + master))
			.andExpect(status().isNotFound());
		assertThat(find(tree(master), empty)).isNull();
		Integer deletedCategory = new JdbcTemplate(dataSource).queryForObject(
				"select count(*) from live_exam_categories where id = ? and deleted_at is not null", Integer.class, empty);
		assertThat(deletedCategory).isEqualTo(1);

		UUID archived = createCategory(system, "Archived shelf", "আর্কাইভ তাক", null, null);
		mockMvc.perform(post("/api/v1/live-exam-categories/" + archived + "/archive")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/live-exam-categories/" + archived).header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.archived").value(true));
		Integer archivedCategory = new JdbcTemplate(dataSource).queryForObject(
				"select count(*) from live_exam_categories where id = ? and archived_at is not null and deleted_at is null",
				Integer.class, archived);
		assertThat(archivedCategory).isEqualTo(1);
	}

	@Test
	void anArchivedCategoryHidesItsBranchAndRejectsNewPlacement() throws Exception {
		String system = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		UUID studentId = seed("STUDENT");
		String student = token(studentId);
		UUID science = createCategory(system, "Science", "বিজ্ঞান", null, null);
		UUID physics = createCategory(system, "Physics", "পদার্থ", science, null);
		UUID arts = createCategory(system, "Arts", "কলা", null, null);
		UUID thermo = createModule(system, physics, "Thermo", "তাপ", null);
		UUID literature = createModule(system, arts, "Literature", "সাহিত্য", null);
		finalSave(system, thermo);
		publish(system, thermo);
		finalSave(system, literature);
		publish(system, literature);
		mark(thermo, studentId);
		mark(literature, studentId);

		assertThat(moduleIds(allModules(student))).contains(thermo, literature);

		mockMvc.perform(post("/api/v1/live-exam-categories/" + science + "/archive").header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		mockMvc.perform(delete("/api/v1/live-exam-categories/" + science).header("Authorization", "Bearer " + master))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CATEGORY_NOT_EMPTY"));

		JsonNode scienceNode = find(tree(master), science);
		assertThat(scienceNode.get("archived").asBoolean()).isTrue();
		assertThat(scienceNode.get("children").get(0).get("id").asText()).isEqualTo(physics.toString());
		assertThat(moduleIds(scienceNode.get("children").get(0).get("modules"))).containsExactly(thermo);
		mockMvc.perform(get("/api/v1/live-exam-modules/" + thermo).header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PUBLISHED"));

		assertThat(moduleIds(allModules(student))).contains(literature).doesNotContain(thermo);
		assertThat(moduleIds(moduleList(student, physics))).isEmpty();
		mockMvc.perform(get("/api/v1/live-exam-modules/" + thermo).header("Authorization", "Bearer " + student))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/live-exam-modules/" + literature).header("Authorization", "Bearer " + student))
			.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/live-exam-categories").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"parentId":"%s","titleEn":"Biology","titleBn":"জীব"}
					""".formatted(science)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CATEGORY_ARCHIVED"));
		mockMvc.perform(post("/api/v1/live-exam-categories").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"parentId":"%s","titleEn":"Optics","titleBn":"আলো"}
					""".formatted(physics)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CATEGORY_ARCHIVED"));
		mockMvc.perform(post("/api/v1/live-exam-modules").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"categoryId":"%s","titleEn":"Later","titleBn":"পরে","descriptionEn":"d","descriptionBn":"বি"}
					""".formatted(physics)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CATEGORY_ARCHIVED"));
		mockMvc.perform(patch("/api/v1/live-exam-modules/" + literature).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"categoryId":"%s"}
					""".formatted(physics)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CATEGORY_ARCHIVED"));
		UUID loose = createCategory(system, "Loose", "আলগা", null, null);
		mockMvc.perform(patch("/api/v1/live-exam-categories/" + loose).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"parentId":"%s"}
					""".formatted(physics)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CATEGORY_ARCHIVED"));

		UUID draft = createModule(system, arts, "Draft after", "পরে খসড়া", 1);
		finalSave(system, draft);
		mockMvc.perform(post("/api/v1/live-exam-categories/" + arts + "/archive").header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		mockMvc.perform(post("/api/v1/live-exam-modules/" + draft + "/publish").header("Authorization", "Bearer " + system))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CATEGORY_ARCHIVED"));
		assertThat(moduleIds(allModules(student))).doesNotContain(literature, thermo);
	}

	private UUID createCategory(String token, String titleEn, String titleBn, UUID parentId, Integer position)
			throws Exception {
		String parent = parentId == null ? "" : "\"parentId\":\"%s\",".formatted(parentId);
		String pos = position == null ? "" : "\"position\":%d,".formatted(position);
		MvcResult result = mockMvc.perform(post("/api/v1/live-exam-categories").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{%s%s"titleEn":"%s","titleBn":"%s"}
					""".formatted(parent, pos, titleEn, titleBn)))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private UUID createModule(String token, UUID categoryId, String titleEn, String titleBn, Integer position)
			throws Exception {
		String pos = position == null ? "" : ",\"position\":%d".formatted(position);
		MvcResult result = mockMvc.perform(post("/api/v1/live-exam-modules").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{
					  "categoryId":"%s",
					  "titleEn":"%s",
					  "titleBn":"%s",
					  "descriptionEn":"English description",
					  "descriptionBn":"বাংলা বিবরণ"%s
					}
					""".formatted(categoryId, titleEn, titleBn, pos)))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private void finalSave(String token, UUID moduleId) throws Exception {
		mockMvc.perform(post("/api/v1/live-exam-modules/" + moduleId + "/final-save").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.finallySaved").value(true));
	}

	private void publish(String token, UUID moduleId) throws Exception {
		mockMvc.perform(post("/api/v1/live-exam-modules/" + moduleId + "/publish").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PUBLISHED"));
	}

	private JsonNode tree(String token) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/live-exam-categories").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn();
		return json.readTree(result.getResponse().getContentAsString());
	}

	private JsonNode allModules(String token) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/live-exam-modules").header("Authorization", "Bearer " + token)
			.param("size", "100"))
			.andExpect(status().isOk())
			.andReturn();
		return json.readTree(result.getResponse().getContentAsString()).get("content");
	}

	private JsonNode moduleList(String token, UUID categoryId) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/live-exam-modules").header("Authorization", "Bearer " + token)
			.param("categoryId", categoryId.toString())
			.param("size", "100"))
			.andExpect(status().isOk())
			.andReturn();
		return json.readTree(result.getResponse().getContentAsString()).get("content");
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

	private List<UUID> rootIds(JsonNode tree) {
		List<UUID> ids = new ArrayList<>();
		for (JsonNode node : tree) {
			ids.add(UUID.fromString(node.get("id").asText()));
		}
		return ids;
	}

	private List<UUID> moduleIds(JsonNode modules) {
		List<UUID> ids = new ArrayList<>();
		if (modules == null || !modules.isArray()) {
			return ids;
		}
		for (JsonNode module : modules) {
			ids.add(UUID.fromString(module.get("id").asText()));
		}
		return ids;
	}

	private List<String> preorder(JsonNode node) {
		List<String> titles = new ArrayList<>();
		titles.add(node.get("titleEn").asText());
		for (JsonNode child : node.get("children")) {
			titles.addAll(preorder(child));
		}
		return titles;
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

	private void mark(UUID moduleId, UUID studentId) {
		new JdbcTemplate(dataSource).update(
				"insert into live_exam_roster (module_id, student_id, created_at) values (?, ?, now())", moduleId,
				studentId);
	}

	private static String uniquePhone() {
		int suffix = java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999);
		return "+8801" + suffix;
	}

	private static String uniqueEmail() {
		return "live-catalog-" + UUID.randomUUID() + "@example.com";
	}

}
