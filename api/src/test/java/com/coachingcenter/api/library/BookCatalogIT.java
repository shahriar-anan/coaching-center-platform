package com.coachingcenter.api.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;
import com.coachingcenter.api.files.LocalFileStorage;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class BookCatalogIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Autowired
	private LocalFileStorage fileStorage;

	@Test
	void bookRecordLifecycle() throws Exception {
		assertThat(columns("books")).doesNotContain("stock", "stock_count", "inventory");

		String system = token(seed("SYSTEM_ADMIN"));
		UUID masterActor = seed("MASTER_ADMIN");
		String master = token(masterActor);

		String created = mockMvc.perform(post("/api/v1/books").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content(bookJson()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.titleEn").value("English Book"))
			.andExpect(jsonPath("$.titleBn").value("বাংলা বই"))
			.andExpect(jsonPath("$.author").value("Author Name"))
			.andExpect(jsonPath("$.available").value(false))
			.andReturn()
			.getResponse()
			.getContentAsString();
		UUID bookId = UUID.fromString(json.readTree(created).get("id").asText());

		mockMvc.perform(patch("/api/v1/books/" + bookId).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"price\":250}"))
			.andExpect(status().isForbidden());

		mockMvc.perform(patch("/api/v1/books/" + bookId).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"available\":true}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PRICE_REQUIRED"));

		mockMvc.perform(patch("/api/v1/books/" + bookId).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"price\":250}"))
			.andExpect(status().isOk());

		mockMvc.perform(patch("/api/v1/books/" + bookId).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"available\":true}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.available").value(true));

		mockMvc.perform(patch("/api/v1/books/" + bookId).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"available\":false}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.available").value(false));

		MockMultipartFile cover = new MockMultipartFile("file", "cover.png", "image/png", "png".getBytes());
		MvcResult coverResult = mockMvc
			.perform(multipart("/api/v1/books/" + bookId + "/cover").file(cover).header("Authorization", "Bearer " + system))
			.andExpect(status().isOk())
			.andReturn();
		UUID coverId = UUID.fromString(json.readTree(coverResult.getResponse().getContentAsString()).get("coverFileId").asText());
		String storedName = new JdbcTemplate(dataSource).queryForObject(
				"select stored_file_name from materials where id = ?", String.class, coverId);
		assertThat(storedName).isNotEqualTo("cover.png");
		assertThat(java.nio.file.Files.exists(fileStorage.resolve(storedName))).isTrue();

		MockMultipartFile huge = new MockMultipartFile("file", "big.bin", "application/octet-stream", new byte[2048]);
		mockMvc.perform(multipart("/api/v1/books/" + bookId + "/cover").file(huge).header("Authorization", "Bearer " + system))
			.andExpect(status().isPayloadTooLarge())
			.andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));

		mockMvc.perform(get("/api/v1/books").header("Authorization", "Bearer " + system))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content").isArray())
			.andExpect(jsonPath("$.page").exists())
			.andExpect(jsonPath("$.size").exists());

		UUID toDelete = UUID.fromString(json.readTree(mockMvc
			.perform(post("/api/v1/books").header("Authorization", "Bearer " + system)
				.contentType(MediaType.APPLICATION_JSON)
				.content(bookJson()))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString()).get("id").asText());

		mockMvc.perform(delete("/api/v1/books/" + toDelete).header("Authorization", "Bearer " + system))
			.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/v1/books/" + toDelete).header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/books/" + toDelete).header("Authorization", "Bearer " + master))
			.andExpect(status().isNotFound());
		Integer audit = new JdbcTemplate(dataSource).queryForObject(
				"select count(*) from audit_log where action = 'BOOK_DELETE' and entity_id = ? and actor_user_id = ?",
				Integer.class, toDelete, masterActor);
		assertThat(audit).isEqualTo(1);
	}

	private Set<String> columns(String table) {
		return new JdbcTemplate(dataSource).queryForList(
				"select column_name from information_schema.columns where table_schema = 'public' and table_name = ?",
				String.class, table)
			.stream()
			.collect(java.util.stream.Collectors.toSet());
	}

	private static String bookJson() {
		return """
				{
				  "titleEn":"English Book",
				  "titleBn":"বাংলা বই",
				  "author":"Author Name",
				  "description":"Book description"
				}
				""";
	}

	private UUID seed(String role) {
		UUID id = UUID.randomUUID();
		String phone = uniquePhone();
		new JdbcTemplate(dataSource).update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, phone, uniqueEmail(), passwords.encode("password1"), role);
		new JdbcTemplate(dataSource).update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, 'Admin')", id);
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
		return "book-" + UUID.randomUUID() + "@example.com";
	}

}
