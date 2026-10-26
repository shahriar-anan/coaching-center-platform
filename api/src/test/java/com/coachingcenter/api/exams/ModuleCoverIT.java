package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import javax.imageio.ImageIO;
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

import tools.jackson.databind.ObjectMapper;

/** The package cover: only the Master Admin uploads it; once the module is public anyone may see it. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class ModuleCoverIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Test
	void masterAdminUploadsShowsAndRemovesTheCover() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		String system = token(seed("SYSTEM_ADMIN"));
		UUID categoryId = createCategory(master);
		UUID moduleId = createModule(master, categoryId);
		publishModule(moduleId);
		byte[] png = png(640, 360);

		mockMvc.perform(multipart("/api/v1/live-exam-modules/" + moduleId + "/cover")
			.file(new MockMultipartFile("file", "cover.png", "image/png", png))
			.header("Authorization", "Bearer " + system))
			.andExpect(status().isForbidden());

		MvcResult uploaded = mockMvc.perform(multipart("/api/v1/live-exam-modules/" + moduleId + "/cover")
			.file(new MockMultipartFile("file", "cover.png", "image/png", png))
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(moduleId.toString()))
			.andReturn();
		assertThat(json.readTree(uploaded.getResponse().getContentAsString()).get("coverUrl").asText())
			.startsWith("/api/v1/public/live-exam-modules/" + moduleId + "/cover?v=");

		mockMvc.perform(get("/api/v1/public/live-exam-modules/" + moduleId + "/cover"))
			.andExpect(status().isOk())
			.andExpect(header().string("Cache-Control", "public, max-age=3600"))
			.andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("image/")));

		mockMvc.perform(get("/api/v1/public/live-exam-modules/" + moduleId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.coverUrl").isString());

		mockMvc.perform(delete("/api/v1/live-exam-modules/" + moduleId + "/cover").header("Authorization",
				"Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.coverUrl").doesNotExist());

		mockMvc.perform(get("/api/v1/public/live-exam-modules/" + moduleId + "/cover"))
			.andExpect(status().isNotFound());
	}

	@Test
	void coverMustBeAnImageOfAtMostTwoMegabytes() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		UUID moduleId = createModule(master, createCategory(master));

		mockMvc.perform(multipart("/api/v1/live-exam-modules/" + moduleId + "/cover")
			.file(new MockMultipartFile("file", "cover.pdf", "application/pdf", "%PDF-1.4".getBytes()))
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		byte[] tooLarge = new byte[(int) ModuleCoverService.MAX_COVER_BYTES + 1];
		mockMvc.perform(multipart("/api/v1/live-exam-modules/" + moduleId + "/cover")
			.file(new MockMultipartFile("file", "cover.png", "image/png", tooLarge))
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isPayloadTooLarge())
			.andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
	}

	private UUID createCategory(String token) throws Exception {
		MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
			.post("/api/v1/live-exam-categories").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"titleEn\":\"Cover category\",\"titleBn\":\"কভার ক্যাটাগরি\"}"))
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
					  "titleEn":"Cover module",
					  "titleBn":"কভার মডিউল",
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

	private static byte[] png(int width, int height) throws Exception {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		return out.toByteArray();
	}

	private UUID seed(String role) {
		UUID id = UUID.randomUUID();
		String phone = "+8801" + java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999);
		new JdbcTemplate(dataSource).update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, phone, "cover-" + id + "@example.com", passwords.encode("password1"), role);
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
