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
class CourseTreeIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Test
	void contentTreeRules() throws Exception {
		String manage = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		UUID freemium = createCourse(manage, "FREEMIUM");
		UUID fullFree = createCourse(manage, "FULL_FREE");

		UUID rootSection = createNode(manage, freemium, """
				{"nodeType":"SECTION","title":"Root section"}
				""");
		UUID nestedSection = createNode(manage, freemium, """
				{"parentId":"%s","nodeType":"SECTION","title":"Nested section"}
				""".formatted(rootSection));
		UUID nestedLecture = createNode(manage, freemium, """
				{"parentId":"%s","nodeType":"LECTURE","title":"Nested lecture","free":false}
				""".formatted(nestedSection));
		UUID secondRoot = createNode(manage, freemium, """
				{"nodeType":"SECTION","title":"Second root"}
				""");
		mockMvc.perform(post("/api/v1/courses/" + freemium + "/nodes/reorder").header("Authorization", "Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"nodeIds":["%s","%s"]}
					""".formatted(secondRoot, rootSection)))
			.andExpect(status().isNoContent());
		UUID rootLecture = createNode(manage, freemium, """
				{"nodeType":"LECTURE","title":"Root lecture","free":false}
				""");
		mockMvc.perform(patch("/api/v1/courses/" + freemium + "/nodes/" + rootSection).header("Authorization",
				"Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"parentId":"%s"}
					""".formatted(nestedLecture)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("NODE_TYPE_REJECTED"));
		mockMvc.perform(patch("/api/v1/courses/" + freemium + "/nodes/" + rootSection).header("Authorization",
				"Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"parentId":"%s"}
					""".formatted(nestedSection)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("NODE_TYPE_REJECTED"));
		mockMvc.perform(post("/api/v1/courses/" + freemium + "/nodes").header("Authorization", "Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"nodeType":"EXAM","title":"Bad"}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("NODE_TYPE_REJECTED"));
		mockMvc.perform(get("/api/v1/courses/" + freemium + "/nodes").header("Authorization", "Bearer " + manage))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].nodeType").exists());

		UUID freeLecture = createNode(manage, freemium, """
				{"nodeType":"LECTURE","title":"Free lecture","free":true}
				""");
		mockMvc.perform(patch("/api/v1/courses/" + freemium + "/nodes/" + freeLecture).header("Authorization",
				"Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"free\":false}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("FREE_CONTENT_FIXED"));
		createNode(manage, freemium, """
				{"nodeType":"LECTURE","title":"Another free","free":true}
				""");

		UUID fullFreeLecture = createNode(manage, fullFree, """
				{"nodeType":"LECTURE","title":"Full free lecture","free":false}
				""");
		mockMvc.perform(patch("/api/v1/courses/" + fullFree + "/nodes/" + fullFreeLecture).header("Authorization",
				"Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"free\":true}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.free").value(false));

		mockMvc.perform(delete("/api/v1/courses/" + freemium + "/nodes/" + rootLecture).header("Authorization",
				"Bearer " + manage))
			.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/v1/courses/" + freemium + "/nodes/" + rootLecture).header("Authorization",
				"Bearer " + master))
			.andExpect(status().isNoContent());
	}

	@Test
	void sectionDeleteAndLectureMove() throws Exception {
		String manage = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		UUID course = createCourse(manage, "FULL_FREE");

		UUID emptyParent = createNode(manage, course, """
				{"nodeType":"SECTION","title":"Empty parent"}
				""");
		UUID emptyChild = createNode(manage, course, """
				{"parentId":"%s","nodeType":"SECTION","title":"Empty child"}
				""".formatted(emptyParent));
		mockMvc.perform(delete("/api/v1/courses/" + course + "/nodes/" + emptyParent).header("Authorization",
				"Bearer " + manage))
			.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/v1/courses/" + course + "/nodes/" + emptyParent).header("Authorization",
				"Bearer " + master))
			.andExpect(status().isNoContent());
		assertThat(nodeIds(manage, course)).doesNotContain(emptyParent, emptyChild);

		UUID blocked = createNode(manage, course, """
				{"nodeType":"SECTION","title":"Has lecture"}
				""");
		UUID directLecture = createNode(manage, course, """
				{"parentId":"%s","nodeType":"LECTURE","title":"Direct"}
				""".formatted(blocked));
		mockMvc.perform(delete("/api/v1/courses/" + course + "/nodes/" + blocked).header("Authorization",
				"Bearer " + master))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SECTION_NOT_EMPTY"));
		assertThat(nodeIds(manage, course)).contains(blocked, directLecture);

		UUID outer = createNode(manage, course, """
				{"nodeType":"SECTION","title":"Outer"}
				""");
		UUID inner = createNode(manage, course, """
				{"parentId":"%s","nodeType":"SECTION","title":"Inner"}
				""".formatted(outer));
		UUID nestedLecture = createNode(manage, course, """
				{"parentId":"%s","nodeType":"LECTURE","title":"Nested"}
				""".formatted(inner));
		mockMvc.perform(delete("/api/v1/courses/" + course + "/nodes/" + outer).header("Authorization",
				"Bearer " + master))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SECTION_NOT_EMPTY"));
		assertThat(nodeIds(manage, course)).contains(outer, inner, nestedLecture);

		UUID sectionA = createNode(manage, course, """
				{"nodeType":"SECTION","title":"Section A"}
				""");
		UUID moving = createNode(manage, course, """
				{"parentId":"%s","nodeType":"LECTURE","title":"Moving"}
				""".formatted(sectionA));
		UUID sectionB = createNode(manage, course, """
				{"nodeType":"SECTION","title":"Section B"}
				""");
		createNode(manage, course, """
				{"parentId":"%s","nodeType":"LECTURE","title":"Staying"}
				""".formatted(sectionB));
		mockMvc.perform(patch("/api/v1/courses/" + course + "/nodes/" + moving).header("Authorization", "Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"parentId":"%s"}
					""".formatted(sectionB)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.parentId").value(sectionB.toString()))
			.andExpect(jsonPath("$.position").value(1));
		mockMvc.perform(patch("/api/v1/courses/" + course + "/nodes/" + moving).header("Authorization", "Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"parentId\":null}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.parentId").doesNotExist())
			.andExpect(jsonPath("$.position").value(4));
	}

	private java.util.Set<UUID> nodeIds(String token, UUID courseId) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/courses/" + courseId + "/nodes").header("Authorization",
				"Bearer " + token))
			.andExpect(status().isOk())
			.andReturn();
		java.util.Set<UUID> ids = new java.util.HashSet<>();
		for (JsonNode node : json.readTree(result.getResponse().getContentAsString())) {
			ids.add(UUID.fromString(node.get("id").asText()));
		}
		return ids;
	}

	private UUID createCourse(String token, String accessType) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/courses").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{
					  "titleEn":"Course",
					  "titleBn":"কোর্স",
					  "contentLanguage":"EN",
					  "description":"d",
					  "deliveryMode":"ONLINE",
					  "accessType":"%s"
					}
					""".formatted(accessType)))
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
		JsonNode node = json.readTree(result.getResponse().getContentAsString());
		return UUID.fromString(node.get("id").asText());
	}

	private UUID seed(String role) {
		UUID id = UUID.randomUUID();
		String phone = uniquePhone();
		new JdbcTemplate(dataSource).update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, phone, uniqueEmail(), passwords.encode("password1"), role);
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
		return "tree-" + UUID.randomUUID() + "@example.com";
	}

}
