package com.coachingcenter.api.library;

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
class BookCategoryIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Test
	void categoriesAreAFlatListAndABookHasOne() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		int next = nextPosition(master);
		UUID first = createCategory(master, "Category A", "ক্যাটাগরি এ", null);
		UUID second = createCategory(master, "Category B", "ক্যাটাগরি বি", null);

		JsonNode list = list(master);
		JsonNode categoryA = find(list, first);
		JsonNode categoryB = find(list, second);
		assertThat(categoryA.get("position").asInt()).isEqualTo(next);
		assertThat(categoryB.get("position").asInt()).isEqualTo(next + 1);
		assertThat(indexOf(list, first)).isLessThan(indexOf(list, second));
		assertThat(categoryA.has("children")).isFalse();
		assertThat(categoryA.has("parentId")).isFalse();
		assertThat(categoryA.has("kind")).isFalse();

		UUID book1 = createBook(master, "Book-1", "বই-১");
		UUID book2 = createBook(master, "Book-2", "বই-২");
		place(master, book1, first);
		place(master, book2, second);
		mockMvc.perform(get("/api/v1/books/" + book1).header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.categoryId").value(first.toString()));
		mockMvc.perform(get("/api/v1/books/" + book2).header("Authorization", "Bearer " + master))
			.andExpect(jsonPath("$.categoryId").value(second.toString()));

		place(master, book1, second);
		mockMvc.perform(get("/api/v1/books/" + book1).header("Authorization", "Bearer " + master))
			.andExpect(jsonPath("$.categoryId").value(second.toString()));

		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		assertThat(jdbc.queryForObject("select count(*) from content_nodes where id in (?, ?)", Integer.class, first,
				second)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from course_categories where id in (?, ?)", Integer.class, first,
				second)).isZero();
	}

	@Test
	void clearingPlacementLetsAnEmptyCategoryBeDeleted() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		UUID held = createCategory(master, "Held", "ধরা", null);
		UUID temporary = createBook(master, "Temporary", "সাময়িক");
		place(master, temporary, held);
		mockMvc.perform(patch("/api/v1/books/" + temporary).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"categoryId\":null}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.categoryId").doesNotExist());

		UUID alone = createCategory(master, "Soon empty", "খালি", null);
		UUID gone = createBook(master, "Removed", "সরানো");
		place(master, gone, alone);
		mockMvc.perform(delete("/api/v1/books/" + gone).header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		mockMvc.perform(delete("/api/v1/book-categories/" + alone).header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		assertThat(find(list(master), alone)).isNull();
	}

	@Test
	void deleteAuditRejectionsAndCatalogStayFlat() throws Exception {
		UUID masterId = seed("MASTER_ADMIN");
		String master = token(masterId);
		String system = token(seed("SYSTEM_ADMIN"));
		String student = token(seed("STUDENT"));

		UUID createdBySystem = createCategory(system, "System category", "সিস্টেম", null);
		UUID placed = createBook(system, "Placed by system", "সিস্টেম বই");
		place(system, placed, createdBySystem);
		mockMvc.perform(get("/api/v1/books/" + placed).header("Authorization", "Bearer " + system))
			.andExpect(jsonPath("$.categoryId").value(createdBySystem.toString()));
		mockMvc.perform(delete("/api/v1/book-categories/" + createdBySystem).header("Authorization", "Bearer " + system))
			.andExpect(status().isForbidden());

		mockMvc.perform(delete("/api/v1/book-categories/" + createdBySystem).header("Authorization", "Bearer " + master))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CATEGORY_NOT_EMPTY"));

		UUID empty = createCategory(master, "Empty", "শূন্য", null);
		mockMvc.perform(delete("/api/v1/book-categories/" + empty).header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		assertThat(find(list(master), empty)).isNull();
		Integer audits = new JdbcTemplate(dataSource).queryForObject(
				"select count(*) from audit_log where action = 'BOOK_CATEGORY_DELETE' and entity_type = 'BOOK_CATEGORY' and entity_id = ? and actor_user_id = ?",
				Integer.class, empty, masterId);
		assertThat(audits).isEqualTo(1);

		mockMvc.perform(get("/api/v1/book-categories").header("Authorization", "Bearer " + student))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/book-categories")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/book-categories").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"titleEn\":\"Only English\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		UUID slot = createCategory(master, "Slots", "স্থান", null);
		int taken = find(list(master), slot).get("position").asInt();
		mockMvc.perform(post("/api/v1/book-categories").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titleEn":"Taken","titleBn":"নেওয়া","position":%d}
					""".formatted(taken)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CATEGORY_POSITION_TAKEN"));

		UUID book = createBook(master, "Unknown place", "অজানা");
		mockMvc.perform(patch("/api/v1/books/" + book).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"categoryId":"%s"}
					""".formatted(UUID.randomUUID())))
			.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/book-categories").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"parentId":null,"titleEn":"No parent","titleBn":"নেই"}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		String title = "Catalog " + UUID.randomUUID();
		UUID catalogBook = createBook(master, title, "ক্যাটালগ");
		mockMvc.perform(patch("/api/v1/books/" + catalogBook).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"price\":100,\"available\":true}"))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/catalog/books").param("q", title).param("categoryId", UUID.randomUUID().toString())
			.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].titleEn").value(title))
			.andExpect(jsonPath("$.content[0].categoryId").doesNotExist());
	}

	private int nextPosition(String token) throws Exception {
		int max = -1;
		for (JsonNode category : list(token)) {
			max = Math.max(max, category.get("position").asInt());
		}
		return max + 1;
	}

	private UUID createCategory(String token, String titleEn, String titleBn, Integer position) throws Exception {
		String pos = position == null ? "" : "\"position\":%d,".formatted(position);
		MvcResult result = mockMvc.perform(post("/api/v1/book-categories").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{%s"titleEn":"%s","titleBn":"%s"}
					""".formatted(pos, titleEn, titleBn)))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private UUID createBook(String token, String titleEn, String titleBn) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/books").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titleEn":"%s","titleBn":"%s","author":"Author","description":"A book"}
					""".formatted(titleEn, titleBn)))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private void place(String token, UUID bookId, UUID categoryId) throws Exception {
		mockMvc.perform(patch("/api/v1/books/" + bookId).header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"categoryId":"%s"}
					""".formatted(categoryId)))
			.andExpect(status().isOk());
	}

	private JsonNode list(String token) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/book-categories").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn();
		return json.readTree(result.getResponse().getContentAsString());
	}

	private JsonNode find(JsonNode categories, UUID id) {
		for (JsonNode category : categories) {
			if (id.toString().equals(category.path("id").asText())) {
				return category;
			}
		}
		return null;
	}

	private int indexOf(JsonNode categories, UUID id) {
		int index = 0;
		for (JsonNode category : categories) {
			if (id.toString().equals(category.path("id").asText())) {
				return index;
			}
			index++;
		}
		return -1;
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
		return "book-category-" + UUID.randomUUID() + "@example.com";
	}

}
