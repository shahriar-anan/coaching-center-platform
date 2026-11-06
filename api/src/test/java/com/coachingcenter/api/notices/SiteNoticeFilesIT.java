package com.coachingcenter.api.notices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import javax.imageio.ImageIO;
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

/**
 * The photos and the PDF of a public notice (notice media plan, F1) over HTTP: the six-photo limit, the order, the PDF's
 * content check, and what a visitor can see before and after publishing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class SiteNoticeFilesIT extends PostgresIntegrationTest {

	private static final String PDF = "%PDF-1.4\n% notice test\n%%EOF\n";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Test
	void photosAndThePdfFollowTheLimitsOrderAndPublishing() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		UUID notice = createNotice(master, "Files notice");

		// Six photos are the most; the seventh is refused with the field named.
		for (int i = 0; i < 6; i++) {
			addPhoto(master, notice, jpeg(200 + i, 150)).andExpect(status().isOk());
		}
		mockMvc.perform(multipart(HttpMethod.POST, "/api/v1/site-notices/" + notice + "/photos")
			.file(new MockMultipartFile("file", "seventh.jpg", "image/jpeg", jpeg(120, 90)))
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors[0].field").value("photos"));
		assertThat(photoIds(notice)).hasSize(6);

		// Removing one closes up the positions; reordering needs exactly the current ids.
		List<String> before = photoIds(notice);
		mockMvc.perform(delete("/api/v1/site-notices/" + notice + "/photos/" + before.get(0))
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.photos.length()").value(5));
		List<String> remaining = photoIds(notice);
		assertThat(remaining).containsExactlyElementsOf(before.subList(1, 6));

		List<String> reversed = new java.util.ArrayList<>(remaining);
		java.util.Collections.reverse(reversed);
		mockMvc.perform(put("/api/v1/site-notices/" + notice + "/photos/order")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"ids\":" + toJsonArray(reversed) + "}")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.photos[0].id").value(reversed.get(0)));
		assertThat(photoIds(notice)).containsExactlyElementsOf(reversed);
		mockMvc.perform(put("/api/v1/site-notices/" + notice + "/photos/order")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"ids\":[\"" + reversed.get(0) + "\"]}")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isBadRequest());

		// The PDF: a file that is not a PDF is refused; a PDF is kept, and a second one replaces it.
		mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/site-notices/" + notice + "/pdf")
			.file(new MockMultipartFile("file", "fake.pdf", "application/pdf", "not a pdf".getBytes(StandardCharsets.UTF_8)))
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isBadRequest());
		mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/site-notices/" + notice + "/pdf")
			.file(new MockMultipartFile("file", "routine.pdf", "application/pdf", PDF.getBytes(StandardCharsets.UTF_8)))
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.pdf.fileName").value("routine.pdf"))
			.andExpect(jsonPath("$.pdf.byteSize").value(PDF.getBytes(StandardCharsets.UTF_8).length));
		mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/site-notices/" + notice + "/pdf")
			.file(new MockMultipartFile("file", "second.pdf", "application/pdf", PDF.getBytes(StandardCharsets.UTF_8)))
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.pdf.fileName").value("second.pdf"));
		assertThat(pdfCount(notice)).isEqualTo(1);

		// Before publishing a visitor sees nothing: the notice, its photos and its PDF are all not found.
		mockMvc.perform(get("/api/v1/public/site-notices/" + notice + "/pdf")).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/public/site-notices/" + notice + "/photos/" + remainingFirst(notice)))
			.andExpect(status().isNotFound());

		// Publishing shows the photos and the PDF to everyone, with a version in each URL.
		mockMvc.perform(post("/api/v1/site-notices/" + notice + "/publish").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/public/site-notices/" + notice))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.photos.length()").value(5))
			.andExpect(jsonPath("$.photos[0].url").value(org.hamcrest.Matchers.startsWith("/api/v1/public/site-notices/" + notice + "/photos/")))
			.andExpect(jsonPath("$.pdf.url").value(org.hamcrest.Matchers.startsWith("/api/v1/public/site-notices/" + notice + "/pdf?v=")));
		mockMvc.perform(get("/api/v1/public/site-notices/" + notice + "/photos/" + remainingFirst(notice)))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", "image/jpeg"))
			.andExpect(header().string("Cache-Control", "public, max-age=3600"))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"));
		mockMvc.perform(get("/api/v1/public/site-notices/" + notice + "/pdf"))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", "application/pdf"))
			.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline;")));
		mockMvc.perform(get("/api/v1/public/site-notices/" + notice + "/pdf").param("download", "1"))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")));

		// The staff preview of a published notice's file needs a staff sign-in.
		String pdfFile = pdfFileId(notice);
		mockMvc.perform(get("/api/v1/site-notices/" + notice + "/files/" + pdfFile))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/site-notices/" + notice + "/files/" + pdfFile).header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(header().string("Cache-Control", "private, no-cache"));

		// Removing the PDF hides it from visitors at once.
		mockMvc.perform(delete("/api/v1/site-notices/" + notice + "/pdf").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.pdf").doesNotExist());
		mockMvc.perform(get("/api/v1/public/site-notices/" + notice + "/pdf")).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/public/site-notices/" + notice))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.pdf").doesNotExist());
	}

	@Test
	void aDraftsFilesAreVisibleOnlyToStaff() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		UUID draft = createNotice(master, "Draft notice");
		addPhoto(master, draft, jpeg(160, 120)).andExpect(status().isOk());
		String photo = photoIds(draft).get(0);

		mockMvc.perform(get("/api/v1/public/site-notices/" + draft + "/photos/" + photo)).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/public/site-notices/" + draft)).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/site-notices/" + draft + "/files/" + photo).header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", "image/jpeg"));
	}

	private UUID createNotice(String token, String title) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/site-notices")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titleEn":"%s","titleBn":"শিরোনাম","bodyEn":"Body","bodyBn":"বিষয়বস্তু"}
					""".formatted(title))
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isCreated())
			.andReturn();
		String body = result.getResponse().getContentAsString();
		int start = body.indexOf("\"id\":\"") + 6;
		return UUID.fromString(body.substring(start, body.indexOf('"', start)));
	}

	private org.springframework.test.web.servlet.ResultActions addPhoto(String token, UUID notice, byte[] image) throws Exception {
		return mockMvc.perform(multipart(HttpMethod.POST, "/api/v1/site-notices/" + notice + "/photos")
			.file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", image))
			.header("Authorization", "Bearer " + token));
	}

	private List<String> photoIds(UUID notice) {
		return new JdbcTemplate(dataSource).queryForList(
				"select id::text from site_notice_files where notice_id = ? and kind = 'PHOTO' order by position", String.class,
				notice);
	}

	private String remainingFirst(UUID notice) {
		return photoIds(notice).get(0);
	}

	private String pdfFileId(UUID notice) {
		return new JdbcTemplate(dataSource).queryForObject(
				"select id::text from site_notice_files where notice_id = ? and kind = 'PDF'", String.class, notice);
	}

	private int pdfCount(UUID notice) {
		Integer count = new JdbcTemplate(dataSource).queryForObject(
				"select count(*) from site_notice_files where notice_id = ? and kind = 'PDF'", Integer.class, notice);
		return count == null ? 0 : count;
	}

	private static String toJsonArray(List<String> ids) {
		return "[" + String.join(",", ids.stream().map(id -> "\"" + id + "\"").toList()) + "]";
	}

	private UUID seed(String role) {
		UUID id = UUID.randomUUID();
		String phone = "+8801" + ThreadLocalRandom.current().nextInt(100000000, 999999999);
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		jdbc.update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, phone, "notice-files-" + id + "@example.com", passwords.encode("password1"), role);
		jdbc.update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, 'Master')", id);
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

	private static byte[] jpeg(int width, int height) throws IOException {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				image.setRGB(x, y, ((x * 7) & 0xFF) << 16 | ((y * 11) & 0xFF) << 8 | 0x80);
			}
		}
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			ImageIO.write(image, "jpg", out);
			return out.toByteArray();
		}
	}

}
