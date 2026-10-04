package com.coachingcenter.api.courses;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class CourseCategoryIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Test
	void masterAdminOrdersCoursesBesideSubcategories() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		UUID main = createCategory(master, "Main Category", "মূল", null, null);
		UUID sub1 = createCategory(master, "Sub Category-1", "উপ-১", main, 0);
		UUID course1 = createCourse(master, "Course-1", "কোর্স-১");
		place(master, course1, main, 1);
		UUID sub2 = createCategory(master, "Sub Category-2", "উপ-২", main, 2);
		UUID course2 = createCourse(master, "Course-2", "কোর্স-২");
		place(master, course2, main, 3);

		JsonNode tree = tree(master);
		assertThat(rootIds(tree)).doesNotContain(sub1);
		JsonNode children = find(tree, main).get("children");
		assertThat(children).hasSize(4);
		assertChild(children.get(0), "CATEGORY", sub1, "Sub Category-1", 0);
		assertChild(children.get(1), "COURSE", course1, "Course-1", 1);
		assertChild(children.get(2), "CATEGORY", sub2, "Sub Category-2", 2);
		assertChild(children.get(3), "COURSE", course2, "Course-2", 3);
		assertThat(children.get(1).has("children")).isFalse();

		mockMvc.perform(get("/api/v1/courses/" + course1).header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.categoryId").value(main.toString()))
			.andExpect(jsonPath("$.categoryPosition").value(1));
		mockMvc.perform(get("/api/v1/courses/" + course2).header("Authorization", "Bearer " + master))
			.andExpect(jsonPath("$.categoryId").value(main.toString()));

		Integer categoryRows = new JdbcTemplate(dataSource).queryForObject(
				"select count(*) from content_nodes where id in (?,?,?)", Integer.class, main, sub1, sub2);
		assertThat(categoryRows).isZero();

		UUID rootLecture = createNode(master, course1, """
				{"nodeType":"LECTURE","title":"Root lecture"}
				""");
		UUID section = createNode(master, course1, """
				{"nodeType":"SECTION","title":"Week"}
				""");
		UUID nestedLecture = createNode(master, course1, """
				{"parentId":"%s","nodeType":"LECTURE","title":"Nested lecture"}
				""".formatted(section));
		JsonNode nodes = nodes(master, course1);
		assertThat(parentOf(nodes, rootLecture)).isNull();
		assertThat(parentOf(nodes, nestedLecture)).isEqualTo(section);
		Integer nodeRows = new JdbcTemplate(dataSource).queryForObject(
				"select count(*) from content_nodes where course_id = ? and deleted_at is null", Integer.class, course1);
		assertThat(nodeRows).isEqualTo(3);
	}

	@Test
	void movingACourseLeavesItInOneCategory() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		UUID main = createCategory(master, "Science", "বিজ্ঞান", null, null);
		UUID sub = createCategory(master, "Physics", "পদার্থ", main, 0);
		UUID course = createCourse(master, "Mechanics", "বলবিদ্যা");
		place(master, course, main, 1);
		place(master, course, sub, null);

		JsonNode tree = tree(master);
		assertThat(count(tree, course)).isEqualTo(1);
		assertThat(find(tree, sub).get("children").get(0).get("id").asText()).isEqualTo(course.toString());
		assertThat(childIds(find(tree, main))).doesNotContain(course);
		mockMvc.perform(get("/api/v1/courses/" + course).header("Authorization", "Bearer " + master))
			.andExpect(jsonPath("$.categoryId").value(sub.toString()));

		UUID otherMain = createCategory(master, "Arts", "কলা", null, null);
		UUID otherCourse = createCourse(master, "Drawing", "আঁকা");
		place(master, otherCourse, otherMain, 0);
		JsonNode again = tree(master);
		assertThat(childIds(find(again, otherMain))).containsExactly(otherCourse);
		assertThat(childIds(find(again, main))).doesNotContain(otherCourse);
	}

	@Test
	void clearingAndDeletingACourseUpdatesTheTree() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		UUID main = createCategory(master, "Held", "ধরা", null, null);
		UUID course = createCourse(master, "Temporary", "সাময়িক");
		place(master, course, main, 0);
		mockMvc.perform(patch("/api/v1/courses/" + course).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"categoryId\":null}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.categoryId").doesNotExist());
		assertThat(count(tree(master), course)).isZero();

		UUID alone = createCategory(master, "Soon empty", "খালি", null, null);
		UUID gone = createCourse(master, "Removed", "সরানো");
		place(master, gone, alone, 0);
		mockMvc.perform(delete("/api/v1/courses/" + gone).header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		assertThat(count(tree(master), gone)).isZero();
		mockMvc.perform(delete("/api/v1/course-categories/" + alone).header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
	}

	@Test
	void deleteAuditAndRejectedCategoryShapes() throws Exception {
		UUID masterId = seed("MASTER_ADMIN");
		String master = token(masterId);
		String system = token(seed("SYSTEM_ADMIN"));
		String student = token(seed("STUDENT"));

		UUID createdBySystem = createCategory(system, "System category", "সিস্টেম", null, null);
		mockMvc.perform(delete("/api/v1/course-categories/" + createdBySystem).header("Authorization", "Bearer " + system))
			.andExpect(status().isForbidden());

		UUID parent = createCategory(master, "Occupied", "ভর্তি", null, null);
		UUID child = createCategory(master, "Child", "শিশু", parent, 0);
		mockMvc.perform(delete("/api/v1/course-categories/" + parent).header("Authorization", "Bearer " + master))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CATEGORY_NOT_EMPTY"));
		UUID parked = createCourse(master, "Parked", "রাখা");
		place(master, parked, child, 0);
		mockMvc.perform(delete("/api/v1/course-categories/" + child).header("Authorization", "Bearer " + master))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CATEGORY_NOT_EMPTY"));

		UUID empty = createCategory(master, "Empty", "শূন্য", null, null);
		mockMvc.perform(delete("/api/v1/course-categories/" + empty).header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		assertThat(find(tree(master), empty)).isNull();
		Integer audits = new JdbcTemplate(dataSource).queryForObject(
				"select count(*) from audit_log where action = 'COURSE_CATEGORY_DELETE' and entity_type = 'COURSE_CATEGORY' and entity_id = ? and actor_user_id = ?",
				Integer.class, empty, masterId);
		assertThat(audits).isEqualTo(1);

		mockMvc.perform(get("/api/v1/course-categories").header("Authorization", "Bearer " + student))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/course-categories")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/course-categories").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"titleEn\":\"Only English\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		UUID cycleParent = createCategory(master, "Cycle parent", "চক্র", null, null);
		UUID cycleChild = createCategory(master, "Cycle child", "শাখা", cycleParent, 0);
		mockMvc.perform(patch("/api/v1/course-categories/" + cycleParent).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"parentId":"%s"}
					""".formatted(cycleChild)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("CATEGORY_CYCLE"));
		mockMvc.perform(patch("/api/v1/course-categories/" + cycleParent).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"parentId":"%s"}
					""".formatted(cycleParent)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("CATEGORY_CYCLE"));

		UUID slot = createCategory(master, "Slots", "স্থান", null, null);
		createCategory(master, "First slot", "প্রথম", slot, 0);
		mockMvc.perform(post("/api/v1/course-categories").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"parentId":"%s","titleEn":"Taken","titleBn":"নেওয়া","position":0}
					""".formatted(slot)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CATEGORY_POSITION_TAKEN"));
		mockMvc.perform(post("/api/v1/course-categories").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"parentId":"%s","titleEn":"Missing parent","titleBn":"নেই"}
					""".formatted(UUID.randomUUID())))
			.andExpect(status().isNotFound());
	}

	private void assertChild(JsonNode node, String kind, UUID id, String titleEn, int position) {
		assertThat(node.get("kind").asText()).isEqualTo(kind);
		assertThat(node.get("id").asText()).isEqualTo(id.toString());
		assertThat(node.get("titleEn").asText()).isEqualTo(titleEn);
		assertThat(node.get("position").asInt()).isEqualTo(position);
	}

	private UUID createCategory(String token, String titleEn, String titleBn, UUID parentId, Integer position)
			throws Exception {
		String parent = parentId == null ? "" : "\"parentId\":\"%s\",".formatted(parentId);
		String pos = position == null ? "" : "\"position\":%d,".formatted(position);
		MvcResult result = mockMvc.perform(post("/api/v1/course-categories").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{%s%s"titleEn":"%s","titleBn":"%s"}
					""".formatted(parent, pos, titleEn, titleBn)))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private void place(String token, UUID courseId, UUID categoryId, Integer position) throws Exception {
		String pos = position == null ? "" : ",\"categoryPosition\":%d".formatted(position);
		mockMvc.perform(patch("/api/v1/courses/" + courseId).header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"categoryId":"%s"%s}
					""".formatted(categoryId, pos)))
			.andExpect(status().isOk());
	}

	private UUID createCourse(String token, String titleEn, String titleBn) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/courses").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{
					  "titleEn":"%s",
					  "titleBn":"%s",
					  "contentLanguage":"EN",
					  "description":"d",
					  "deliveryMode":"ONLINE",
					  "accessType":"FULL_FREE"
					}
					""".formatted(titleEn, titleBn)))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private UUID createNode(String token, UUID courseId, String body) throws Exception {
		MvcResult result = mockMvc
			.perform(post("/api/v1/courses/" + courseId + "/nodes").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private JsonNode tree(String token) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/course-categories").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn();
		return json.readTree(result.getResponse().getContentAsString());
	}

	private JsonNode nodes(String token, UUID courseId) throws Exception {
		MvcResult result = mockMvc
			.perform(get("/api/v1/courses/" + courseId + "/nodes").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn();
		return json.readTree(result.getResponse().getContentAsString());
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

	private java.util.List<UUID> rootIds(JsonNode tree) {
		java.util.List<UUID> ids = new java.util.ArrayList<>();
		for (JsonNode node : tree) {
			ids.add(UUID.fromString(node.get("id").asText()));
		}
		return ids;
	}

	private java.util.List<UUID> childIds(JsonNode category) {
		java.util.List<UUID> ids = new java.util.ArrayList<>();
		for (JsonNode child : category.get("children")) {
			ids.add(UUID.fromString(child.get("id").asText()));
		}
		return ids;
	}

	private int count(JsonNode nodes, UUID id) {
		if (nodes == null || !nodes.isArray()) {
			return 0;
		}
		int found = 0;
		for (JsonNode node : nodes) {
			if (id.toString().equals(node.path("id").asText())) {
				found++;
			}
			found += count(node.get("children"), id);
		}
		return found;
	}

	private UUID parentOf(JsonNode nodes, UUID id) {
		for (JsonNode node : nodes) {
			if (id.toString().equals(node.get("id").asText())) {
				JsonNode parent = node.get("parentId");
				if (parent == null || parent.isNull()) {
					return null;
				}
				return UUID.fromString(parent.asText());
			}
		}
		return null;
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
			new JdbcTemplate(dataSource).update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, 'Admin')",
					id);
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
		return "category-" + UUID.randomUUID() + "@example.com";
	}

}
