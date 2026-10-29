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
import java.sql.Timestamp;
import java.time.Instant;
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

/** The exam cover (E1): the Master Admin uploads it; staff see a draft's cover, and visitors see only a published exam's. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class ExamCoverIT extends PostgresIntegrationTest {

	private static final Instant WINDOW_START = Instant.parse("2026-10-29T04:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Test
	void masterAdminUploadsShowsAndRemovesTheExamCover() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		String system = token(seed("SYSTEM_ADMIN"));
		UUID exam = publishedExam();
		byte[] png = png(640, 360);

		mockMvc.perform(multipart("/api/v1/live-exams/" + exam + "/cover")
			.file(new MockMultipartFile("file", "cover.png", "image/png", png))
			.header("Authorization", "Bearer " + system))
			.andExpect(status().isForbidden());

		mockMvc.perform(multipart("/api/v1/live-exams/" + exam + "/cover")
			.file(new MockMultipartFile("file", "cover.png", "image/png", png))
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(exam.toString()))
			.andExpect(jsonPath("$.coverUrl").value(org.hamcrest.Matchers.startsWith("/api/v1/public/live-exams/" + exam
					+ "/cover?v=")));

		mockMvc.perform(get("/api/v1/public/live-exams/" + exam + "/cover"))
			.andExpect(status().isOk())
			.andExpect(header().string("Cache-Control", "public, max-age=3600"))
			.andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("image/")));

		mockMvc.perform(get("/api/v1/public/live-exams/" + exam))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.coverUrl").isString());

		mockMvc.perform(delete("/api/v1/live-exams/" + exam + "/cover").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.coverUrl").doesNotExist());

		mockMvc.perform(get("/api/v1/public/live-exams/" + exam + "/cover"))
			.andExpect(status().isNotFound());
	}

	@Test
	void staffSeeTheCoverOfADraftWhileVisitorsDoNot() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		String student = token(seed("STUDENT"));
		UUID draft = insertExam("DRAFT");
		mockMvc.perform(multipart("/api/v1/live-exams/" + draft + "/cover")
			.file(new MockMultipartFile("file", "cover.png", "image/png", png(640, 360)))
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/live-exams/" + draft + "/cover").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(header().string("Cache-Control", "private, no-cache"))
			.andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("image/")));
		mockMvc.perform(get("/api/v1/live-exams/" + draft + "/cover").header("Authorization", "Bearer " + student))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/public/live-exams/" + draft + "/cover")).andExpect(status().isNotFound());
	}

	@Test
	void coverMustBeAnImageOfAtMostTwoMegabytes() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		UUID exam = insertExam("PUBLISHED");

		mockMvc.perform(multipart("/api/v1/live-exams/" + exam + "/cover")
			.file(new MockMultipartFile("file", "cover.pdf", "application/pdf", "%PDF-1.4".getBytes()))
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		byte[] tooLarge = new byte[(int) ExamCoverService.MAX_COVER_BYTES + 1];
		mockMvc.perform(multipart("/api/v1/live-exams/" + exam + "/cover")
			.file(new MockMultipartFile("file", "cover.png", "image/png", tooLarge))
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isPayloadTooLarge())
			.andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
	}

	/** A published exam in a published package, with a cover slot and no cover yet. */
	private UUID publishedExam() {
		return insertExam("PUBLISHED");
	}

	private UUID insertExam(String status) {
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		UUID category = UUID.randomUUID();
		jdbc.update("insert into live_exam_categories (id, title_en, title_bn, position, created_at, updated_at)"
				+ " values (?, 'Cover category', 'কভার', 0, now(), now())", category);
		UUID module = UUID.randomUUID();
		jdbc.update("insert into live_exam_modules (id, category_id, title_en, title_bn, description_en, description_bn,"
				+ " access_mode, status, finally_saved, position, created_at, updated_at)"
				+ " values (?, ?, 'Cover package', 'কভার', '', '', 'MANUAL', 'PUBLISHED', true, 0, now(), now())", module,
				category);
		UUID exam = UUID.randomUUID();
		jdbc.update("insert into live_exams (id, module_id, category_id, exam_type, title_en, title_bn, description_en,"
				+ " description_bn, rules_en, rules_bn, paper_language, live_at, ends_at, duration_minutes, mode, status,"
				+ " finally_saved, pass_percent, answers_changeable, price, is_free, access_days, published_at, created_at,"
				+ " updated_at)"
				+ " values (?, null, ?, 'MCQ', 'Cover exam', 'কভার পরীক্ষা', '', '', '', '', 'BN', ?, ?, 30, 'LIVE', ?,"
				+ " true, 50, true, 300, false, 30, ?, now(), now())", exam, category, Timestamp.from(WINDOW_START),
				Timestamp.from(WINDOW_START.plusSeconds(3600)), status,
				"PUBLISHED".equals(status) ? Timestamp.from(WINDOW_START) : null);
		jdbc.update("insert into live_exam_package_exams (id, module_id, exam_id, position, added_at, removed_at)"
				+ " values (?, ?, ?, 0, ?, null)", UUID.randomUUID(), module, exam, Timestamp.from(WINDOW_START));
		return exam;
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
				""", id, phone, "exam-cover-" + id + "@example.com", passwords.encode("password1"), role);
		if (role.endsWith("ADMIN")) {
			new JdbcTemplate(dataSource).update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, 'Admin')", id);
		}
		else {
			new JdbcTemplate(dataSource).update("INSERT INTO student_profiles (user_id, full_name, student_code)"
					+ " VALUES (?, 'Student', ?)", id, "COVER" + id.toString().substring(0, 8));
		}
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
