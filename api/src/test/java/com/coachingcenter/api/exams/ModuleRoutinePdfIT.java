package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;
import com.coachingcenter.api.files.LocalFileStorage;

import tools.jackson.databind.ObjectMapper;

/** The package's routine PDF: the Master Admin uploads it; once the package is public anyone may view or download it. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class ModuleRoutinePdfIT extends PostgresIntegrationTest {

	private static final String PDF_HEAD = "%PDF-1.4\n";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Autowired
	private LocalFileStorage storage;

	@Test
	void masterAdminUploadsShowsDownloadsAndRemovesTheRoutinePdf() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		UUID moduleId = createModule(master, createCategory(master));
		publishModule(moduleId);

		MvcResult uploaded = mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/live-exam-modules/" + moduleId + "/routine-pdf")
			.file(new MockMultipartFile("file", "routine.pdf", "application/pdf", pdf(1024)))
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(moduleId.toString()))
			.andExpect(jsonPath("$.routinePdf.fileName").value("routine.pdf"))
			.andExpect(jsonPath("$.routinePdf.byteSize").value(1024))
			.andReturn();
		assertThat(json.readTree(uploaded.getResponse().getContentAsString()).get("routinePdf").get("url").asText())
			.startsWith("/api/v1/public/live-exam-modules/" + moduleId + "/routine.pdf?v=");

		mockMvc.perform(get("/api/v1/public/live-exam-modules/" + moduleId + "/routine.pdf"))
			.andExpect(status().isOk())
			.andExpect(header().string("Cache-Control", "public, max-age=3600"))
			.andExpect(header().string("Content-Type", "application/pdf"))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"))
			.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline;")));
		mockMvc.perform(get("/api/v1/public/live-exam-modules/" + moduleId + "/routine.pdf").param("download", "1"))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")));

		mockMvc.perform(get("/api/v1/public/live-exam-modules/" + moduleId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.routinePdf.fileName").value("routine.pdf"));

		mockMvc.perform(delete("/api/v1/live-exam-modules/" + moduleId + "/routine-pdf").header("Authorization",
				"Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.routinePdf").value(org.hamcrest.Matchers.nullValue()));

		mockMvc.perform(get("/api/v1/public/live-exam-modules/" + moduleId + "/routine.pdf"))
			.andExpect(status().isNotFound());
	}

	@Test
	void staffPreviewsADraftRoutinePdfWhilePublicReadsRefuseIt() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		String student = token(seed("STUDENT"));
		UUID moduleId = createModule(master, createCategory(master));
		mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/live-exam-modules/" + moduleId + "/routine-pdf")
			.file(new MockMultipartFile("file", "routine.pdf", "application/pdf", pdf(512)))
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/live-exam-modules/" + moduleId + "/routine-pdf").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(header().string("Cache-Control", "private, no-cache"))
			.andExpect(header().string("Content-Type", "application/pdf"));
		mockMvc.perform(get("/api/v1/live-exam-modules/" + moduleId + "/routine-pdf").header("Authorization", "Bearer " + student))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/public/live-exam-modules/" + moduleId + "/routine.pdf"))
			.andExpect(status().isNotFound());
	}

	@Test
	void routinePdfIsRefusedUnlessItIsAPdfOfAtMostTwentyMegabytes() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		UUID moduleId = createModule(master, createCategory(master));

		mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/live-exam-modules/" + moduleId + "/routine-pdf")
			.file(new MockMultipartFile("file", "routine.pdf", "application/pdf", "routine table, not a PDF".getBytes()))
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/live-exam-modules/" + moduleId + "/routine-pdf")
			.file(new MockMultipartFile("file", "routine.png", "image/png", pdf(1024)))
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());

		byte[] tooLarge = pdf((int) ModuleRoutinePdfService.MAX_ROUTINE_BYTES + 1);
		mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/live-exam-modules/" + moduleId + "/routine-pdf")
			.file(new MockMultipartFile("file", "routine.pdf", "application/pdf", tooLarge))
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isPayloadTooLarge())
			.andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
	}

	@Test
	void onlyTheCoverManagersUploadOrRemoveIt() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		String system = token(seed("SYSTEM_ADMIN"));
		UUID moduleId = createModule(master, createCategory(master));

		mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/live-exam-modules/" + moduleId + "/routine-pdf")
			.file(new MockMultipartFile("file", "routine.pdf", "application/pdf", pdf(256)))
			.header("Authorization", "Bearer " + system))
			.andExpect(status().isForbidden());
		mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/live-exam-modules/" + moduleId + "/routine-pdf")
			.file(new MockMultipartFile("file", "routine.pdf", "application/pdf", pdf(256)))
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
		mockMvc.perform(delete("/api/v1/live-exam-modules/" + moduleId + "/routine-pdf").header("Authorization",
				"Bearer " + system))
			.andExpect(status().isForbidden());
	}

	@Test
	void publicRoutinePdfIsA404ForADraftOrArchivedPackage() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		UUID moduleId = createModule(master, createCategory(master));
		mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/live-exam-modules/" + moduleId + "/routine-pdf")
			.file(new MockMultipartFile("file", "routine.pdf", "application/pdf", pdf(256)))
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/public/live-exam-modules/" + moduleId + "/routine.pdf"))
			.andExpect(status().isNotFound());

		publishModule(moduleId);
		mockMvc.perform(get("/api/v1/public/live-exam-modules/" + moduleId + "/routine.pdf"))
			.andExpect(status().isOk());

		new JdbcTemplate(dataSource).update("update live_exam_modules set status = ? where id = ?",
				LiveExamModule.ARCHIVED, moduleId);
		mockMvc.perform(get("/api/v1/public/live-exam-modules/" + moduleId + "/routine.pdf"))
			.andExpect(status().isNotFound());
	}

	@Test
	void replacingTheRoutinePdfLeavesExactlyOneStoredFile() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		UUID moduleId = createModule(master, createCategory(master));
		mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/live-exam-modules/" + moduleId + "/routine-pdf")
			.file(new MockMultipartFile("file", "first.pdf", "application/pdf", pdf(300)))
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
		String first = storedName(moduleId);

		mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/live-exam-modules/" + moduleId + "/routine-pdf")
			.file(new MockMultipartFile("file", "স্বাক্ষর-routine.pdf", "application/pdf", pdf(400)))
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.routinePdf.fileName").value("স্বাক্ষর-routine.pdf"))
			.andExpect(jsonPath("$.routinePdf.byteSize").value(400));
		String second = storedName(moduleId);

		assertThat(second).isNotEqualTo(first);
		assertThat(Files.exists(storage.resolve(first))).isFalse();
		assertThat(Files.exists(storage.resolve(second))).isTrue();
		Integer rows = new JdbcTemplate(dataSource).queryForObject(
				"select count(*) from live_exam_module_routines where module_id = ?", Integer.class, moduleId);
		assertThat(rows).isEqualTo(1);
	}

	@Test
	void packageWithNoRoutinePdfCarriesNullNotAnAbsentField() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		UUID moduleId = createModule(master, createCategory(master));
		publishModule(moduleId);

		MvcResult result = mockMvc.perform(get("/api/v1/public/live-exam-modules/" + moduleId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.routinePdf").value(org.hamcrest.Matchers.nullValue()))
			.andReturn();
		assertThat(json.readTree(result.getResponse().getContentAsString()).has("routinePdf")).isTrue();

		mockMvc.perform(get("/api/v1/live-exam-modules/" + moduleId).header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.routinePdf").value(org.hamcrest.Matchers.nullValue()));
	}

	private String storedName(UUID moduleId) {
		return new JdbcTemplate(dataSource).queryForObject(
				"select stored_name from live_exam_module_routines where module_id = ?", String.class, moduleId);
	}

	private UUID createCategory(String token) throws Exception {
		MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
			.post("/api/v1/live-exam-categories").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"titleEn\":\"Routine category\",\"titleBn\":\"রুটিন ক্যাটাগরি\"}"))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private UUID createModule(String token, UUID categoryId) throws Exception {
		MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
			.post("/api/v1/live-exam-modules").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{
					  "categoryId":"%s",
					  "titleEn":"Routine module",
					  "titleBn":"রুটিন মডিউল",
					  "descriptionEn":"English description",
					  "descriptionBn":"বাংলা বিবরণ"
					}
					""".formatted(categoryId)))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private void publishModule(UUID moduleId) {
		new JdbcTemplate(dataSource).update("update live_exam_modules set status = ? where id = ?", LiveExamModule.PUBLISHED, moduleId);
	}

	private static byte[] pdf(int size) {
		byte[] bytes = new byte[size];
		byte[] head = PDF_HEAD.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
		System.arraycopy(head, 0, bytes, 0, Math.min(head.length, size));
		return bytes;
	}

	private UUID seed(String role) {
		UUID id = UUID.randomUUID();
		String phone = "+8801" + java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999);
		new JdbcTemplate(dataSource).update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, phone, "routine-" + id + "@example.com", passwords.encode("password1"), role);
		new JdbcTemplate(dataSource).update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, 'Admin')", id);
		return id;
	}

	private String token(UUID userId) throws Exception {
		String phone = new JdbcTemplate(dataSource).queryForObject("select phone from users where id = ?", String.class,
				userId);
		MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
			.post("/api/v1/auth/login").header("X-Client-Type", "mobile")
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

}
