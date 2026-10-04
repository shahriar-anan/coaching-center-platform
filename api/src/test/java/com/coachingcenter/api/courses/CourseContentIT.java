package com.coachingcenter.api.courses;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;

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
import com.coachingcenter.api.files.FileStorageProperties;
import com.coachingcenter.api.files.LocalFileStorage;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class CourseContentIT extends PostgresIntegrationTest {

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
	void lectureAndMaterialContent() throws Exception {
		assertThat(FileStorageProperties.DEFAULT_MATERIAL_MAX_BYTES).isEqualTo(104857600L);

		String manage = token(seed("SYSTEM_ADMIN"));
		String read = manage;
		UUID courseId = createCourse(manage);
		UUID lectureId = createLecture(manage, courseId);

		mockMvc.perform(patch("/api/v1/courses/" + courseId + "/nodes/" + lectureId).header("Authorization",
				"Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"videoUrl\":\"https://www.youtube.com/watch?v=dQw4w9WgXcQ\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.videoProvider").value("YOUTUBE"))
			.andExpect(jsonPath("$.videoRef").value("dQw4w9WgXcQ"));

		mockMvc.perform(patch("/api/v1/courses/" + courseId + "/nodes/" + lectureId).header("Authorization",
				"Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"videoUrl\":\"https://youtu.be/oHg5SJYRHA0\"}"))
			.andExpect(jsonPath("$.videoRef").value("oHg5SJYRHA0"));

		mockMvc.perform(patch("/api/v1/courses/" + courseId + "/nodes/" + lectureId).header("Authorization",
				"Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"videoUrl\":\"https://www.youtube.com/embed/9bZkp7q19f0\"}"))
			.andExpect(jsonPath("$.videoRef").value("9bZkp7q19f0"));

		mockMvc.perform(patch("/api/v1/courses/" + courseId + "/nodes/" + lectureId).header("Authorization",
				"Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"videoUrl\":\"https://example.com/video\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_YOUTUBE_URL"));

		mockMvc.perform(patch("/api/v1/courses/" + courseId + "/nodes/" + lectureId).header("Authorization",
				"Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"notes\":\"Lecture notes\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.notes").value("Lecture notes"));

		Integer beforeMaterials = countMaterials();
		MockMultipartFile lectureFile = new MockMultipartFile("file", "lecture.pdf", "application/pdf",
				"pdf-bytes".getBytes());
		MvcResult lectureMaterial = mockMvc
			.perform(multipart("/api/v1/courses/" + courseId + "/materials").file(lectureFile)
				.param("lectureNodeId", lectureId.toString())
				.header("Authorization", "Bearer " + manage))
			.andExpect(status().isCreated())
			.andReturn();
		UUID lectureMaterialId = materialId(lectureMaterial);

		MockMultipartFile courseFile = new MockMultipartFile("file", "course.pdf", "application/pdf",
				"course".getBytes());
		MvcResult courseMaterial = mockMvc
			.perform(multipart("/api/v1/courses/" + courseId + "/materials").file(courseFile).param("free", "true")
				.header("Authorization", "Bearer " + manage))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.free").value(true))
			.andReturn();
		UUID courseMaterialId = materialId(courseMaterial);

		mockMvc.perform(get("/api/v1/courses/" + courseId + "/materials").header("Authorization", "Bearer " + read))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[?(@.id == '%s')].lectureNodeId".formatted(lectureMaterialId)).value(lectureId.toString()))
			.andExpect(jsonPath("$[?(@.id == '%s')].free".formatted(courseMaterialId)).value(true));

		MockMultipartFile cover = new MockMultipartFile("file", "cover.png", "image/png", "png".getBytes());
		mockMvc.perform(multipart("/api/v1/courses/" + courseId + "/cover").file(cover).header("Authorization",
				"Bearer " + manage))
			.andExpect(status().isCreated());
		mockMvc.perform(get("/api/v1/courses/" + courseId).header("Authorization", "Bearer " + read))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.coverFileId").isNotEmpty());

		String student = token(seed("STUDENT"));
		mockMvc.perform(get("/api/v1/courses/" + courseId + "/nodes").header("Authorization", "Bearer " + student))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/courses/" + courseId + "/materials").header("Authorization", "Bearer " + student))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/courses/" + courseId + "/nodes")).andExpect(status().isUnauthorized());

		String storedName = new JdbcTemplate(dataSource).queryForObject(
				"select stored_file_name from materials where id = ?", String.class, courseMaterialId);
		assertThat(storedName).isNotEqualTo("course.pdf");
		try (Stream<Path> paths = Files.list(fileStorage.rootDirectory())) {
			assertThat(paths.map(Path::getFileName).map(Path::toString).toList()).contains(storedName);
		}

		mockMvc.perform(patch("/api/v1/courses/" + courseId + "/materials/" + courseMaterialId).header("Authorization",
				"Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"free\":false}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("FREE_CONTENT_FIXED"));

		mockMvc.perform(get("/api/v1/files/" + lectureMaterialId).header("Authorization", "Bearer " + read))
			.andExpect(status().isOk())
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
				.string("Content-Type", "application/pdf"));

		mockMvc.perform(get("/api/v1/files/" + lectureMaterialId)).andExpect(status().isUnauthorized());

		MockMultipartFile huge = new MockMultipartFile("file", "big.bin", "application/octet-stream", new byte[2048]);
		mockMvc.perform(multipart("/api/v1/courses/" + courseId + "/materials").file(huge).header("Authorization",
				"Bearer " + manage))
			.andExpect(status().isPayloadTooLarge())
			.andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
		assertThat(countMaterials()).isEqualTo(beforeMaterials + 3);
	}

	@Test
	void replaceDeleteAndClearLectureContent() throws Exception {
		String manage = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		UUID courseId = createCourse(manage);
		UUID sectionId = createNode(manage, courseId, "{\"nodeType\":\"SECTION\",\"title\":\"Section\"}");
		UUID lectureId = createLecture(manage, courseId);
		UUID otherLecture = createNode(manage, courseId, """
				{"parentId":"%s","nodeType":"LECTURE","title":"In section"}
				""".formatted(sectionId));

		MockMultipartFile onSection = new MockMultipartFile("file", "section.pdf", "application/pdf", "nope".getBytes());
		mockMvc.perform(multipart("/api/v1/courses/" + courseId + "/materials").file(onSection)
			.param("lectureNodeId", sectionId.toString())
			.header("Authorization", "Bearer " + manage))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		MockMultipartFile lectureFile = new MockMultipartFile("file", "lecture.pdf", "application/pdf",
				"pdf-bytes".getBytes());
		UUID lectureMaterialId = materialId(mockMvc
			.perform(multipart("/api/v1/courses/" + courseId + "/materials").file(lectureFile)
				.param("lectureNodeId", lectureId.toString())
				.header("Authorization", "Bearer " + manage))
			.andExpect(status().isCreated())
			.andReturn());
		MockMultipartFile courseFile = new MockMultipartFile("file", "course.pdf", "application/pdf", "course".getBytes());
		UUID courseMaterialId = materialId(mockMvc
			.perform(multipart("/api/v1/courses/" + courseId + "/materials").file(courseFile).param("free", "true")
				.header("Authorization", "Bearer " + manage))
			.andExpect(status().isCreated())
			.andReturn());
		MockMultipartFile otherFile = new MockMultipartFile("file", "other.pdf", "application/pdf", "other".getBytes());
		UUID otherMaterialId = materialId(mockMvc
			.perform(multipart("/api/v1/courses/" + courseId + "/materials").file(otherFile)
				.param("lectureNodeId", otherLecture.toString())
				.header("Authorization", "Bearer " + manage))
			.andExpect(status().isCreated())
			.andReturn());

		mockMvc.perform(patch("/api/v1/courses/" + courseId + "/nodes/" + lectureId).header("Authorization",
				"Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"videoUrl\":\"https://youtu.be/oHg5SJYRHA0\"}"))
			.andExpect(status().isOk());
		mockMvc.perform(patch("/api/v1/courses/" + courseId + "/nodes/" + lectureId).header("Authorization",
				"Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"videoUrl\":null}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.videoProvider").doesNotExist())
			.andExpect(jsonPath("$.videoRef").doesNotExist());

		String previousName = storedName(lectureMaterialId);
		MockMultipartFile replacement = new MockMultipartFile("file", "replaced.pdf", "application/pdf",
				"replaced-bytes".getBytes());
		mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/courses/" + courseId + "/materials/" + lectureMaterialId)
			.file(replacement)
			.header("Authorization", "Bearer " + manage))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(lectureMaterialId.toString()))
			.andExpect(jsonPath("$.lectureNodeId").value(lectureId.toString()))
			.andExpect(jsonPath("$.originalFileName").value("replaced.pdf"));
		assertThat(java.nio.file.Files.exists(fileStorage.resolve(previousName))).isFalse();
		assertThat(java.nio.file.Files.exists(fileStorage.resolve(storedName(lectureMaterialId)))).isTrue();

		mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/courses/" + courseId + "/materials/" + courseMaterialId)
			.file(new MockMultipartFile("file", "course-new.pdf", "application/pdf", "new".getBytes()))
			.header("Authorization", "Bearer " + manage))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.free").value(true))
			.andExpect(jsonPath("$.lectureNodeId").doesNotExist());

		String keptName = storedName(courseMaterialId);
		mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/courses/" + courseId + "/materials/" + courseMaterialId)
			.file(new MockMultipartFile("file", "big.bin", "application/octet-stream", new byte[2048]))
			.header("Authorization", "Bearer " + manage))
			.andExpect(status().isPayloadTooLarge())
			.andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
		assertThat(storedName(courseMaterialId)).isEqualTo(keptName);
		assertThat(java.nio.file.Files.exists(fileStorage.resolve(keptName))).isTrue();

		mockMvc.perform(delete("/api/v1/courses/" + courseId + "/materials/" + lectureMaterialId).header("Authorization",
				"Bearer " + manage))
			.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/v1/courses/" + courseId + "/materials/" + lectureMaterialId).header("Authorization",
				"Bearer " + master))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/courses/" + courseId + "/materials").header("Authorization", "Bearer " + manage))
			.andExpect(jsonPath("$[?(@.id == '%s')]".formatted(lectureMaterialId)).doesNotExist());

		MockMultipartFile cover = new MockMultipartFile("file", "cover.png", "image/png", "png".getBytes());
		MvcResult coverResult = mockMvc
			.perform(multipart("/api/v1/courses/" + courseId + "/cover").file(cover).header("Authorization",
					"Bearer " + manage))
			.andExpect(status().isCreated())
			.andReturn();
		UUID coverId = UUID.fromString(json.readTree(coverResult.getResponse().getContentAsString()).get("id").asText());
		mockMvc.perform(delete("/api/v1/courses/" + courseId + "/materials/" + coverId).header("Authorization",
				"Bearer " + master))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		mockMvc.perform(delete("/api/v1/courses/" + courseId + "/nodes/" + otherLecture).header("Authorization",
				"Bearer " + master))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/courses/" + courseId + "/materials").header("Authorization", "Bearer " + manage))
			.andExpect(jsonPath("$[?(@.id == '%s')]".formatted(otherMaterialId)).doesNotExist())
			.andExpect(jsonPath("$[?(@.id == '%s')]".formatted(courseMaterialId)).exists());
		mockMvc.perform(delete("/api/v1/courses/" + courseId + "/materials/" + courseMaterialId).header("Authorization",
				"Bearer " + master))
			.andExpect(status().isNoContent());
		assertThat(java.nio.file.Files.exists(fileStorage.resolve(keptName))).isFalse();
	}

	private Integer countMaterials() {
		return new JdbcTemplate(dataSource).queryForObject("select count(*) from materials where deleted_at is null",
				Integer.class);
	}

	private UUID materialId(MvcResult result) throws Exception {
		JsonNode node = json.readTree(result.getResponse().getContentAsString());
		return UUID.fromString(node.get("id").asText());
	}

	private UUID createCourse(String token) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/courses").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{
					  "titleEn":"Course",
					  "titleBn":"কোর্স",
					  "contentLanguage":"EN",
					  "description":"d",
					  "deliveryMode":"ONLINE",
					  "accessType":"FREEMIUM"
					}
					"""))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private String storedName(UUID materialId) {
		return new JdbcTemplate(dataSource).queryForObject("select stored_file_name from materials where id = ?",
				String.class, materialId);
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

	private UUID createLecture(String token, UUID courseId) throws Exception {
		MvcResult result = mockMvc
			.perform(post("/api/v1/courses/" + courseId + "/nodes").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nodeType\":\"LECTURE\",\"title\":\"Lecture\"}"))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
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
		return "content-" + UUID.randomUUID() + "@example.com";
	}

}
