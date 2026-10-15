package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
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
import com.coachingcenter.api.files.FileStorageProperties;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class ExamWrittenSubmissionIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Autowired
	private FileStorageProperties fileProperties;

	private String system;

	private String student;

	private String other;

	private UUID module;

	private UUID written;

	@BeforeEach
	void seed() throws Exception {
		system = token(seed("SYSTEM_ADMIN"));
		student = registerStudent();
		other = registerStudent();
		module = publishOpenModule();
		UUID paper = createPaper();
		written = createWritten(paper);
		addWrittenQuestion(written, 4, "Stem A", "Explain A");
		addWrittenQuestion(written, 6, "Stem B", "Explain B");
	}

	@Test
	void writtenSubmissionRules() throws Exception {
		assertThat(fileProperties.examImageMaxBytes()).isEqualTo(15728640L);

		mockMvc.perform(multipart("/api/v1/catalog/exam-modules/" + module + "/components/" + written + "/submission/images")
			.file(image("a.jpg", "image/jpeg", 2000, 1200))
			.header("Authorization", "Bearer " + other))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("EXAM_ACCESS_REQUIRED"));

		grantOpenAccess(student);
		MockMultipartFile source = image("a.jpg", "image/jpeg", 2000, 1200);
		MvcResult first = mockMvc
			.perform(multipart("/api/v1/catalog/exam-modules/" + module + "/components/" + written + "/submission/images")
				.file(source)
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andReturn();
		UUID image1 = imageId(first, 0);
		verifyStoredJpeg(image1, 2000, 1200, Path.of(fileProperties.storageDir()), source.getBytes());

		mockMvc
			.perform(multipart("/api/v1/catalog/exam-modules/" + module + "/components/" + written + "/submission/images")
				.file(image("b.png", "image/png", 800, 600))
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk());
		MvcResult afterPng = mockMvc
			.perform(get("/api/v1/catalog/exam-modules/" + module + "/components/" + written + "/submission")
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andReturn();
		UUID image2 = imageId(afterPng, 1);
		verifyStoredJpeg(image2, 800, 600, Path.of(fileProperties.storageDir()), null);

		mockMvc
			.perform(multipart("/api/v1/catalog/exam-modules/" + module + "/components/" + written + "/submission/images")
				.file(new MockMultipartFile("file", "c.webp", "image/webp", tinyWebp()))
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk());

		MvcResult three = mockMvc
			.perform(get("/api/v1/catalog/exam-modules/" + module + "/components/" + written + "/submission")
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andReturn();
		UUID image3 = imageId(three, 2);

		mockMvc
			.perform(post("/api/v1/catalog/exam-modules/" + module + "/components/" + written + "/submission/images/reorder")
				.header("Authorization", "Bearer " + student)
				.contentType(MediaType.APPLICATION_JSON)
				.content(reorderJson(image3, image1, image2)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.images[0].id").value(image3.toString()));

		mockMvc
			.perform(delete("/api/v1/catalog/exam-modules/" + module + "/components/" + written + "/submission/images/"
					+ image2)
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk());

		mockMvc
			.perform(multipart("/api/v1/catalog/exam-modules/" + module + "/components/" + written + "/submission/images")
				.file(image("d.jpg", "image/jpeg", 400, 400))
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk());
		byte[] atCap = new byte[15 * 1024 * 1024];
		byte[] tiny = image("cap.jpg", "image/jpeg", 20, 20).getBytes();
		System.arraycopy(tiny, 0, atCap, 0, tiny.length);
		mockMvc
			.perform(multipart("/api/v1/catalog/exam-modules/" + module + "/components/" + written + "/submission/images")
				.file(new MockMultipartFile("file", "cap.jpg", "image/jpeg", atCap))
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk());
		MvcResult again = mockMvc
			.perform(get("/api/v1/catalog/exam-modules/" + module + "/components/" + written + "/submission")
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode againImages = json.readTree(again.getResponse().getContentAsString()).get("images");
		UUID[] ordered = new UUID[againImages.size()];
		for (int i = 0; i < againImages.size(); i++) {
			ordered[i] = UUID.fromString(againImages.get(i).get("id").asText());
		}
		UUID againFirst = ordered[ordered.length - 1];
		UUID swap = ordered[0];
		ordered[0] = againFirst;
		ordered[ordered.length - 1] = swap;
		mockMvc
			.perform(post("/api/v1/catalog/exam-modules/" + module + "/components/" + written + "/submission/images/reorder")
				.header("Authorization", "Bearer " + student)
				.contentType(MediaType.APPLICATION_JSON)
				.content(reorderJson(ordered)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.images[0].id").value(againFirst.toString()));
		mockMvc
			.perform(delete("/api/v1/catalog/exam-modules/" + module + "/components/" + written + "/submission/images/"
					+ againFirst)
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk());
		mockMvc
			.perform(multipart("/api/v1/catalog/exam-modules/" + module + "/components/" + written + "/submission/images")
				.file(image("e.jpg", "image/jpeg", 80, 80))
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk());

		mockMvc
			.perform(multipart("/api/v1/catalog/exam-modules/" + module + "/components/" + written + "/submission/images")
				.file(new MockMultipartFile("file", "bad.txt", "text/plain", "x".getBytes()))
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		byte[] twentyMb = new byte[20 * 1024 * 1024];
		mockMvc
			.perform(multipart("/api/v1/catalog/exam-modules/" + module + "/components/" + written + "/submission/images")
				.file(new MockMultipartFile("file", "huge.jpg", "image/jpeg", twentyMb))
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isPayloadTooLarge())
			.andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));

		UUID emptyPaper = createPaper();
		UUID emptyWritten = createWritten(emptyPaper);
		mockMvc
			.perform(multipart("/api/v1/catalog/exam-modules/" + module + "/components/" + emptyWritten + "/submission/images")
				.file(image("z.jpg", "image/jpeg", 100, 100))
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("COMPONENT_EMPTY"));

		mockMvc
			.perform(post("/api/v1/catalog/exam-modules/" + module + "/components/" + written + "/submission/submit")
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
			.andExpect(jsonPath("$.submittedAt").exists())
			.andExpect(jsonPath("$.passPercent").value(50))
			.andExpect(jsonPath("$.questions[0].stem").value("Stem A"))
			.andExpect(jsonPath("$.questions[0].explanation").doesNotExist())
			.andExpect(jsonPath("$.questions[1].maxMarks").value(6));

		new JdbcTemplate(dataSource).update("update questions set stem = ?, explanation = ? where stem = ?",
				"Changed", "Changed exp", "Stem A");

		mockMvc
			.perform(get("/api/v1/catalog/exam-modules/" + module + "/components/" + written + "/submission")
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.questions[0].stem").value("Stem A"));

		mockMvc
			.perform(multipart("/api/v1/catalog/exam-modules/" + module + "/components/" + written + "/submission/images")
				.file(image("late.jpg", "image/jpeg", 100, 100))
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SUBMISSION_LOCKED"));
		mockMvc
			.perform(post("/api/v1/catalog/exam-modules/" + module + "/components/" + written + "/submission/images/reorder")
				.header("Authorization", "Bearer " + student)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"imageIds\":[\"" + image1 + "\"]}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SUBMISSION_LOCKED"));
		mockMvc
			.perform(delete("/api/v1/catalog/exam-modules/" + module + "/components/" + written + "/submission/images/"
					+ image1)
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SUBMISSION_LOCKED"));
		mockMvc
			.perform(post("/api/v1/catalog/exam-modules/" + module + "/components/" + written + "/submission/submit")
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SUBMISSION_LOCKED"));

		UUID liveQuestion = new JdbcTemplate(dataSource).queryForObject(
				"select question_id from component_questions where component_id = ? and deleted_at is null order by position limit 1",
				UUID.class, written);
		mockMvc.perform(delete("/api/v1/exam-components/" + written + "/questions/" + liveQuestion)
			.header("Authorization", "Bearer " + system))
			.andExpect(status().isNoContent());
		mockMvc
			.perform(get("/api/v1/catalog/exam-modules/" + module + "/components/" + written + "/submission")
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.questions[0].stem").value("Stem A"))
			.andExpect(jsonPath("$.questions[0].marksAwarded").doesNotExist())
			.andExpect(jsonPath("$.questions.length()").value(2));

		mockMvc.perform(get("/api/v1/exam-files/" + image1).header("Authorization", "Bearer " + student))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/catalog/exam-modules/" + module + "/components/" + written + "/submission")
			.header("Authorization", "Bearer " + other))
			.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/v1/exam-files/" + image1).header("Authorization", "Bearer " + other))
			.andExpect(status().isForbidden());

		mockMvc
			.perform(get("/api/v1/exam-components/" + written + "/submissions").param("status", "UNDER_REVIEW")
				.header("Authorization", "Bearer " + system))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].status").value("UNDER_REVIEW"));

		mockMvc.perform(get("/api/v1/exam-files/" + image1).header("Authorization", "Bearer " + system))
			.andExpect(status().isOk());

		UUID lateModule = publishOpenModule();
		UUID latePaper = createPaperOn(lateModule);
		UUID lateWritten = createWritten(latePaper);
		addWrittenQuestion(lateWritten, 2, "Late", "L");
		mockMvc.perform(patch("/api/v1/exam-modules/" + lateModule + "/access").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"accessMode\":\"OPEN\",\"courseId\":null}"))
			.andExpect(status().isOk());
		mockMvc
			.perform(multipart("/api/v1/catalog/exam-modules/" + lateModule + "/components/" + lateWritten
					+ "/submission/images")
				.file(image("late.jpg", "image/jpeg", 100, 100))
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk());
		new JdbcTemplate(dataSource).update("update paper_components set deadline_at = ? where id = ?",
				Timestamp.from(Instant.now().minusSeconds(60)), lateWritten);
		mockMvc
			.perform(post("/api/v1/catalog/exam-modules/" + lateModule + "/components/" + lateWritten + "/submission/submit")
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DEADLINE_PASSED"));
		mockMvc
			.perform(get("/api/v1/catalog/exam-modules/" + lateModule + "/components/" + lateWritten + "/submission")
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DRAFT"));

		UUID barePaper = createPaper();
		UUID bare = createWritten(barePaper);
		addWrittenQuestion(bare, 2, "Bare", "B");
		mockMvc
			.perform(multipart("/api/v1/catalog/exam-modules/" + module + "/components/" + bare + "/submission/images")
				.file(image("gone.jpg", "image/jpeg", 40, 40))
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk());
		UUID bareImage = new JdbcTemplate(dataSource).queryForObject(
				"select id from written_submission_images where submission_id = (select id from written_submissions where component_id = ?) and deleted_at is null",
				UUID.class, bare);
		mockMvc
			.perform(delete("/api/v1/catalog/exam-modules/" + module + "/components/" + bare + "/submission/images/"
					+ bareImage)
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk());
		mockMvc
			.perform(post("/api/v1/catalog/exam-modules/" + module + "/components/" + bare + "/submission/submit")
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("SUBMISSION_EMPTY"));

		UUID archiveModule = publishOpenModule();
		UUID archivePaper = createPaperOn(archiveModule);
		UUID archiveWritten = createWritten(archivePaper);
		addWrittenQuestion(archiveWritten, 2, "Keep", "K");
		mockMvc.perform(patch("/api/v1/exam-modules/" + archiveModule + "/access").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"accessMode\":\"OPEN\",\"courseId\":null}"))
			.andExpect(status().isOk());
		mockMvc
			.perform(multipart("/api/v1/catalog/exam-modules/" + archiveModule + "/components/" + archiveWritten
					+ "/submission/images")
				.file(image("keep.jpg", "image/jpeg", 40, 40))
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk());
		String master = token(seed("MASTER_ADMIN"));
		mockMvc.perform(post("/api/v1/exam-modules/" + archiveModule + "/archive").header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		mockMvc
			.perform(post("/api/v1/catalog/exam-modules/" + archiveModule + "/components/" + archiveWritten
					+ "/submission/submit")
				.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UNDER_REVIEW"));
		mockMvc
			.perform(multipart("/api/v1/catalog/exam-modules/" + archiveModule + "/components/" + archiveWritten
					+ "/submission/images")
				.file(image("nope.jpg", "image/jpeg", 40, 40))
				.header("Authorization", "Bearer " + other))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("EXAM_ACCESS_REQUIRED"));

		String docs = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse()
			.getContentAsString();
		assertThat(docs).doesNotContain("/submission/reopen");
		assertThat(docs).doesNotContain("/submission/draft");
	}

	private void verifyStoredJpeg(UUID imageId, int sourceW, int sourceH, Path storageRoot, byte[] original)
			throws Exception {
		String stored = new JdbcTemplate(dataSource).queryForObject(
				"select stored_file_name from written_submission_images where id = ?", String.class, imageId);
		String contentType = new JdbcTemplate(dataSource).queryForObject(
				"select content_type from written_submission_images where id = ?", String.class, imageId);
		assertThat(contentType).isEqualTo("image/jpeg");
		assertThat(stored).matches("[0-9a-fA-F-]{36}");
		assertThat(storageRoot.toString().replace('\\', '/').toLowerCase()).doesNotContain("/static");
		Path file = storageRoot.resolve(stored);
		assertThat(Files.exists(file)).isTrue();
		if (original != null) {
			assertThat(Files.readAllBytes(file)).isNotEqualTo(original);
		}
		BufferedImage img = ImageIO.read(file.toFile());
		int longest = Math.max(img.getWidth(), img.getHeight());
		assertThat(longest).isLessThanOrEqualTo(1600);
		if (Math.max(sourceW, sourceH) > 1600) {
			assertThat(longest).isLessThan(Math.max(sourceW, sourceH));
		}
	}

	private UUID imageId(MvcResult result, int index) throws Exception {
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("images").get(index).get("id")
			.asText());
	}

	private static String reorderJson(UUID... ids) {
		StringBuilder builder = new StringBuilder("{\"imageIds\":[");
		for (int i = 0; i < ids.length; i++) {
			if (i > 0) {
				builder.append(',');
			}
			builder.append('"').append(ids[i]).append('"');
		}
		return builder.append("]}").toString();
	}

	private static byte[] tinyWebp() {
		return java.util.Base64.getDecoder()
			.decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEAAQAcJaQAA3AA/vuUAAA=");
	}

	private MockMultipartFile image(String name, String type, int width, int height) throws Exception {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setColor(Color.BLUE);
		g.fillRect(0, 0, width, height);
		g.dispose();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		String format = type.contains("png") ? "png" : type.contains("webp") ? "webp" : "jpg";
		ImageIO.write(image, format, out);
		return new MockMultipartFile("file", name, type, out.toByteArray());
	}

	private UUID createWritten(UUID paper) throws Exception {
		UUID id = createComponent(paper, "WRITTEN");
		mockMvc.perform(patch("/api/v1/exam-papers/" + paper).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"passPercent\":50}"))
			.andExpect(status().isOk());
		mockMvc.perform(patch("/api/v1/exam-components/" + id).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"descriptionEn":"EN","descriptionBn":"BN","deadlineAt":"2099-01-01T00:00:00Z"}
					"""))
			.andExpect(status().isOk());
		return id;
	}

	private void addWrittenQuestion(UUID componentId, int marks, String stem, String explanation) throws Exception {
		mockMvc.perform(post("/api/v1/exam-components/" + componentId + "/questions").header("Authorization",
				"Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"stem":"%s","marks":%d,"subject":"S","explanation":"%s"}
					""".formatted(stem, marks, explanation)))
			.andExpect(status().isCreated());
	}

	private UUID createComponent(UUID paper, String type) throws Exception {
		MvcResult result = mockMvc
			.perform(post("/api/v1/exam-papers/" + paper + "/components").header("Authorization", "Bearer " + system)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"type\":\"" + type + "\"}"))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private UUID createPaper() throws Exception {
		return createPaperOn(module);
	}

	private UUID createPaperOn(UUID moduleId) throws Exception {
		MvcResult n = mockMvc
			.perform(post("/api/v1/exam-modules/" + moduleId + "/nodes").header("Authorization", "Bearer " + system)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nodeType\":\"PAPER\",\"titleEn\":\"P\",\"titleBn\":\"প\"}"))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(n.getResponse().getContentAsString()).get("id").asText());
	}

	private void grantOpenAccess(String studentToken) throws Exception {
		mockMvc.perform(patch("/api/v1/exam-modules/" + module + "/access").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"accessMode\":\"OPEN\",\"courseId\":null}"))
			.andExpect(status().isOk());
	}

	private UUID publishOpenModule() throws Exception {
		MvcResult m = mockMvc.perform(post("/api/v1/exam-modules").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"titleEn\":\"W\",\"titleBn\":\"ম\",\"descriptionEn\":\"d\",\"descriptionBn\":\"b\"}"))
			.andExpect(status().isCreated())
			.andReturn();
		UUID id = UUID.fromString(json.readTree(m.getResponse().getContentAsString()).get("id").asText());
		mockMvc.perform(patch("/api/v1/exam-modules/" + id).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"PUBLISHED\"}"))
			.andExpect(status().isOk());
		return id;
	}

	private String registerStudent() throws Exception {
		String phone = uniquePhone();
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"S","phone":"%s","email":"%s","password":"password1"}
					""".formatted(phone, uniqueEmail())))
			.andExpect(status().isCreated());
		return tokenFromLogin(phone, "password1");
	}

	private UUID seed(String role) {
		UUID id = UUID.randomUUID();
		String phone = uniquePhone();
		new JdbcTemplate(dataSource).update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, phone, uniqueEmail(), passwords.encode("password1"), role);
		if ("SYSTEM_ADMIN".equals(role) || "MASTER_ADMIN".equals(role)) {
			new JdbcTemplate(dataSource).update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, 'Admin')", id);
		}
		return id;
	}

	private String token(UUID userId) throws Exception {
		String phone = new JdbcTemplate(dataSource).queryForObject("select phone from users where id = ?", String.class,
				userId);
		return tokenFromLogin(phone, "password1");
	}

	private String tokenFromLogin(String phone, String password) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"identifier":"%s","password":"%s"}
					""".formatted(phone, password)))
			.andExpect(status().isOk())
			.andReturn();
		String body = result.getResponse().getContentAsString();
		String marker = "\"accessToken\":\"";
		int start = body.indexOf(marker) + marker.length();
		return body.substring(start, body.indexOf('"', start));
	}

	private static String uniquePhone() {
		return "+8801" + java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999);
	}

	private static String uniqueEmail() {
		return "written-" + UUID.randomUUID() + "@example.com";
	}

}
