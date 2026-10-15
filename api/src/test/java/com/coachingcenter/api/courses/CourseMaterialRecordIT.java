package com.coachingcenter.api.courses;

import static org.hamcrest.Matchers.hasItem;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
import com.coachingcenter.api.files.LocalFileStorage;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class CourseMaterialRecordIT extends PostgresIntegrationTest {

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
	void materialRecord() throws Exception {
		String manage = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		UUID courseId = createCourse(manage);
		UUID lectureId = createNode(manage, courseId, "{\"nodeType\":\"LECTURE\",\"title\":\"Lecture\"}");
		UUID sectionId = createNode(manage, courseId, "{\"nodeType\":\"SECTION\",\"title\":\"Section\"}");

		MvcResult first = mockMvc
			.perform(multipart("/api/v1/courses/" + courseId + "/materials")
				.file(pdf("file", "notes-a.pdf", "a"))
				.param("lectureNodeId", lectureId.toString())
				.header("Authorization", "Bearer " + manage))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.title").value("notes-a.pdf"))
			.andExpect(jsonPath("$.kind").value("OTHER"))
			.andExpect(jsonPath("$.position").value(0))
			.andReturn();
		UUID firstId = id(first);

		mockMvc.perform(multipart("/api/v1/courses/" + courseId + "/materials")
			.file(pdf("file", "notes-b.pdf", "b"))
			.param("lectureNodeId", lectureId.toString())
			.header("Authorization", "Bearer " + manage))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.position").value(1));

		mockMvc.perform(multipart("/api/v1/courses/" + courseId + "/materials")
			.file(pdf("file", "syllabus.pdf", "course"))
			.param("title", "Syllabus")
			.param("description", "Read this first")
			.param("kind", "HANDOUT")
			.header("Authorization", "Bearer " + manage))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.title").value("Syllabus"))
			.andExpect(jsonPath("$.description").value("Read this first"))
			.andExpect(jsonPath("$.kind").value("HANDOUT"));

		Set<String> beforeBatch = storedNames();
		int beforeCount = countMaterials();
		mockMvc.perform(multipart("/api/v1/courses/" + courseId + "/materials/batch")
			.file(pdf("files", "one.pdf", "one"))
			.file(pdf("files", "two.pdf", "two"))
			.param("lectureNodeId", lectureId.toString())
			.param("titles", "One")
			.param("titles", "Two")
			.header("Authorization", "Bearer " + manage))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$[0].title").value("One"))
			.andExpect(jsonPath("$[0].position").value(2))
			.andExpect(jsonPath("$[1].title").value("Two"))
			.andExpect(jsonPath("$[1].position").value(3));

		mockMvc.perform(multipart("/api/v1/courses/" + courseId + "/materials/batch")
			.file(pdf("files", "ok.pdf", "ok"))
			.file(new MockMultipartFile("files", "big.pdf", "application/pdf", new byte[2048]))
			.param("lectureNodeId", lectureId.toString())
			.header("Authorization", "Bearer " + manage))
			.andExpect(status().isPayloadTooLarge())
			.andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
		assertThat(countMaterials()).isEqualTo(beforeCount + 2);
		assertThat(storedNames()).isEqualTo(union(beforeBatch, storedNamesAfter(beforeBatch)));
		assertThat(extraStored(beforeBatch)).hasSize(2);

		mockMvc.perform(multipart("/api/v1/courses/" + courseId + "/materials")
			.file(pdf("file", "bad.pdf", "bad"))
			.param("lectureNodeId", sectionId.toString())
			.header("Authorization", "Bearer " + manage))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		MvcResult sectionFile = mockMvc
			.perform(multipart("/api/v1/courses/" + courseId + "/materials")
				.file(pdf("file", "section.pdf", "section"))
				.param("sectionNodeId", sectionId.toString())
				.param("title", "Section notes")
				.param("description", "Keep")
				.param("kind", "SLIDE")
				.header("Authorization", "Bearer " + manage))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.sectionNodeId").value(sectionId.toString()))
			.andReturn();
		UUID sectionMaterialId = id(sectionFile);
		String sectionStored = storedName(sectionMaterialId);

		mockMvc.perform(multipart("/api/v1/courses/" + courseId + "/materials")
			.file(pdf("file", "both.pdf", "both"))
			.param("lectureNodeId", lectureId.toString())
			.param("sectionNodeId", sectionId.toString())
			.header("Authorization", "Bearer " + manage))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		mockMvc.perform(multipart("/api/v1/courses/" + courseId + "/materials")
			.file(pdf("file", "wrong.pdf", "wrong"))
			.param("sectionNodeId", lectureId.toString())
			.header("Authorization", "Bearer " + manage))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		mockMvc.perform(patch("/api/v1/courses/" + courseId + "/materials/" + sectionMaterialId)
			.header("Authorization", "Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"title":"Renamed","description":null,"kind":"EXERCISE","position":0}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Renamed"))
			.andExpect(jsonPath("$.description").doesNotExist())
			.andExpect(jsonPath("$.kind").value("EXERCISE"))
			.andExpect(jsonPath("$.position").value(0))
			.andExpect(jsonPath("$.sectionNodeId").value(sectionId.toString()));
		mockMvc.perform(patch("/api/v1/courses/" + courseId + "/materials/" + sectionMaterialId)
			.header("Authorization", "Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"kind\":\"AUDIO\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		mockMvc.perform(patch("/api/v1/courses/" + courseId + "/materials/" + sectionMaterialId)
			.header("Authorization", "Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"free\":true}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.free").value(true));
		mockMvc.perform(patch("/api/v1/courses/" + courseId + "/materials/" + sectionMaterialId)
			.header("Authorization", "Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"free\":false}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("FREE_CONTENT_FIXED"));
		mockMvc.perform(patch("/api/v1/courses/" + courseId + "/materials/" + firstId)
			.header("Authorization", "Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"free\":true}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/courses/" + courseId + "/materials/" + sectionMaterialId)
			.file(pdf("file", "section-new.pdf", "replaced-section"))
			.header("Authorization", "Bearer " + manage))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Renamed"))
			.andExpect(jsonPath("$.kind").value("EXERCISE"))
			.andExpect(jsonPath("$.position").value(0))
			.andExpect(jsonPath("$.sectionNodeId").value(sectionId.toString()))
			.andExpect(jsonPath("$.free").value(true))
			.andExpect(jsonPath("$.originalFileName").value("section-new.pdf"));
		assertThat(Files.exists(fileStorage.resolve(sectionStored))).isFalse();
		assertThat(Files.readString(fileStorage.resolve(storedName(sectionMaterialId)))).isEqualTo("replaced-section");

		UUID emptySection = createNode(manage, courseId, "{\"nodeType\":\"SECTION\",\"title\":\"Empty\"}");
		UUID emptyFile = id(mockMvc.perform(multipart("/api/v1/courses/" + courseId + "/materials")
			.file(pdf("file", "gone.pdf", "gone"))
			.param("sectionNodeId", emptySection.toString())
			.header("Authorization", "Bearer " + manage))
			.andExpect(status().isCreated())
			.andReturn());
		String emptyStored = storedName(emptyFile);
		UUID occupiedSection = createNode(manage, courseId, "{\"nodeType\":\"SECTION\",\"title\":\"Occupied\"}");
		createNode(manage, courseId, "{\"nodeType\":\"LECTURE\",\"title\":\"Inside\",\"parentId\":\"" + occupiedSection + "\"}");
		UUID keptFile = id(mockMvc.perform(multipart("/api/v1/courses/" + courseId + "/materials")
			.file(pdf("file", "kept.pdf", "kept"))
			.param("sectionNodeId", occupiedSection.toString())
			.header("Authorization", "Bearer " + manage))
			.andExpect(status().isCreated())
			.andReturn());
		mockMvc.perform(delete("/api/v1/courses/" + courseId + "/nodes/" + occupiedSection).header("Authorization",
				"Bearer " + master))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SECTION_NOT_EMPTY"));
		assertThat(Files.exists(fileStorage.resolve(storedName(keptFile)))).isTrue();
		mockMvc.perform(delete("/api/v1/courses/" + courseId + "/nodes/" + emptySection).header("Authorization",
				"Bearer " + master))
			.andExpect(status().isNoContent());
		assertThat(Files.exists(fileStorage.resolve(emptyStored))).isFalse();

		mockMvc.perform(patch("/api/v1/courses/" + courseId + "/materials/" + firstId).header("Authorization",
				"Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"position\":1}"))
			.andExpect(status().isOk());
		JsonNode listed = json.readTree(mockMvc
			.perform(get("/api/v1/courses/" + courseId + "/materials").header("Authorization", "Bearer " + manage))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString());
		ArrayNode lectureFiles = json.createArrayNode();
		for (JsonNode item : listed) {
			if (item.hasNonNull("lectureNodeId") && lectureId.toString().equals(item.get("lectureNodeId").asText())) {
				lectureFiles.add(item);
			}
		}
		for (int index = 1; index < lectureFiles.size(); index++) {
			assertThat(lectureFiles.get(index).get("position").asInt())
				.isGreaterThan(lectureFiles.get(index - 1).get("position").asInt());
		}

		publish(master, courseId);
		UUID coverId = id(mockMvc.perform(multipart("/api/v1/courses/" + courseId + "/cover")
			.file(new MockMultipartFile("file", "cover.png", "image/png", "png".getBytes()))
			.header("Authorization", "Bearer " + manage))
			.andExpect(status().isCreated())
			.andReturn());
		String student = token(seed("STUDENT"));
		mockMvc.perform(get("/api/v1/catalog/courses/" + courseId).header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.courseMaterials[?(@.originalFileName == 'syllabus.pdf')].title", hasItem("Syllabus")))
			.andExpect(jsonPath("$.courseMaterials[?(@.originalFileName == 'syllabus.pdf')].kind", hasItem("HANDOUT")))
			.andExpect(jsonPath("$.courseMaterials[?(@.id == '%s')]".formatted(coverId)).doesNotExist())
			.andExpect(jsonPath("$.courseMaterials[?(@.id == '%s')]".formatted(sectionMaterialId)).doesNotExist())
			.andExpect(jsonPath("$.sectionMaterials[?(@.id == '%s')].sectionNodeId".formatted(sectionMaterialId),
					hasItem(sectionId.toString())))
			.andExpect(jsonPath("$.sectionMaterials[?(@.id == '%s')].title".formatted(sectionMaterialId), hasItem("Renamed")))
			.andExpect(jsonPath("$.sectionMaterials[?(@.id == '%s')].free".formatted(sectionMaterialId), hasItem(true)));

		mockMvc.perform(patch("/api/v1/courses/" + courseId + "/nodes/" + lectureId).header("Authorization",
				"Bearer " + manage)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"free\":true}"))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/catalog/courses/" + courseId + "/lectures/" + lectureId)
			.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.materialIds").isNotEmpty())
			.andExpect(jsonPath("$.materials[?(@.id == '%s')].title".formatted(firstId), hasItem("notes-a.pdf")))
			.andExpect(jsonPath("$.materials[?(@.id == '%s')].kind".formatted(firstId), hasItem("OTHER")));

		UUID paidSection = createNode(manage, courseId, "{\"nodeType\":\"SECTION\",\"title\":\"Paid files\"}");
		UUID paidFile = id(mockMvc.perform(multipart("/api/v1/courses/" + courseId + "/materials")
			.file(pdf("file", "paid.pdf", "paid"))
			.param("sectionNodeId", paidSection.toString())
			.header("Authorization", "Bearer " + manage))
			.andExpect(status().isCreated())
			.andReturn());
		String outsider = token(seed("STUDENT"));
		mockMvc.perform(get("/api/v1/files/" + paidFile).header("Authorization", "Bearer " + outsider))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ENROLLMENT_REQUIRED"));
		mockMvc.perform(get("/api/v1/catalog/courses/" + courseId + "/materials/" + paidFile)
			.header("Authorization", "Bearer " + outsider))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ENROLLMENT_REQUIRED"));
		mockMvc.perform(get("/api/v1/files/" + sectionMaterialId).header("Authorization", "Bearer " + outsider))
			.andExpect(status().isOk());

		UUID enrolledId = seed("STUDENT");
		String enrolled = token(enrolledId);
		mockMvc.perform(post("/api/v1/courses/" + courseId + "/admissions").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"studentId\":\"" + enrolledId + "\"}"))
			.andExpect(status().isCreated());
		mockMvc.perform(get("/api/v1/files/" + paidFile).header("Authorization", "Bearer " + enrolled))
			.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/courses/" + courseId + "/close").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/files/" + paidFile).header("Authorization", "Bearer " + enrolled))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/catalog/courses/" + courseId + "/materials/" + sectionMaterialId)
			.header("Authorization", "Bearer " + enrolled))
			.andExpect(status().isNotFound());
	}

	private Set<String> extraStored(Set<String> before) throws Exception {
		return storedNames().stream().filter(name -> !before.contains(name)).collect(Collectors.toSet());
	}

	private Set<String> storedNamesAfter(Set<String> before) throws Exception {
		return extraStored(before);
	}

	private Set<String> union(Set<String> left, Set<String> right) {
		return Stream.concat(left.stream(), right.stream()).collect(Collectors.toSet());
	}

	private Set<String> storedNames() throws Exception {
		Path root = fileStorage.rootDirectory();
		if (!Files.exists(root)) {
			return Set.of();
		}
		try (Stream<Path> paths = Files.list(root)) {
			return paths.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
		}
	}

	private void publish(String master, UUID courseId) throws Exception {
		mockMvc.perform(patch("/api/v1/courses/" + courseId).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"price\":100,\"status\":\"PUBLISHED\"}"))
			.andExpect(status().isOk());
	}

	private MockMultipartFile pdf(String part, String name, String body) {
		return new MockMultipartFile(part, name, "application/pdf", body.getBytes());
	}

	private int countMaterials() {
		return new JdbcTemplate(dataSource).queryForObject("select count(*) from materials where deleted_at is null",
				Integer.class);
	}

	private String storedName(UUID materialId) {
		return new JdbcTemplate(dataSource).queryForObject("select stored_file_name from materials where id = ?",
				String.class, materialId);
	}

	private UUID createCourse(String token) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/courses").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titleEn":"Course","titleBn":"কোর্স","contentLanguage":"EN","description":"d","deliveryMode":"ONLINE","accessType":"FREEMIUM"}
					"""))
			.andExpect(status().isCreated())
			.andReturn();
		return id(result);
	}

	private UUID createNode(String token, UUID courseId, String body) throws Exception {
		MvcResult result = mockMvc
			.perform(post("/api/v1/courses/" + courseId + "/nodes").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated())
			.andReturn();
		return id(result);
	}

	private UUID id(MvcResult result) throws Exception {
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private UUID seed(String role) {
		UUID userId = UUID.randomUUID();
		String phone = uniquePhone();
		new JdbcTemplate(dataSource).update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", userId, phone, uniqueEmail(), passwords.encode("password1"), role);
		if ("STUDENT".equals(role)) {
			new JdbcTemplate(dataSource).update(
					"INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, 'Seed', ?)", userId,
					"S" + userId.toString().substring(0, 8));
		}
		else {
			new JdbcTemplate(dataSource).update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, 'Admin')",
					userId);
		}
		return userId;
	}

	private String token(UUID userId) throws Exception {
		String phone = new JdbcTemplate(dataSource).queryForObject("select phone from users where id = ?", String.class,
				userId);
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"identifier\":\"" + phone + "\",\"password\":\"password1\"}"))
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
		return "material-" + UUID.randomUUID() + "@example.com";
	}

}
