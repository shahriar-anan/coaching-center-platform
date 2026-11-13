package com.coachingcenter.api.offline;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import javax.imageio.ImageIO;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.context.TestPropertySource;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The offline (desk) admission flow end to end, through the HTTP routes: a staff member creates a student at the desk, the student
 * fills the admission form and uploads a photo, submits it, and is held out of exams until staff accept it.
 *
 * The helpers are copied from {@code InstallmentTestSupport} rather than inherited, because that base class is package-private in
 * the {@code exams} package. The clock is fixed at 10:00 in Dhaka on 20 October 2026.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, OfflineAdmissionFormIT.ClockConfig.class })
@TestPropertySource(properties = "app.offline-admission.enabled=true")
class OfflineAdmissionFormIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	/** The staff user who created this test's admission rows. JUnit makes a new instance per test method. */
	private UUID deskStaff;

	@Test
	void deskCreateWithOfflineAdmissionOpensAwaitingForm() throws Exception {
		UUID masterId = seed("MASTER_ADMIN", "Master");
		String master = token(masterId);

		ResultActions created = deskCreate(master, deskBody("Desk Student", uniquePhone(), uniqueEmail(), "true"));
		created.andExpect(status().isCreated());
		UUID student = UUID.fromString(readTree(created).get("id").asText());

		assertThat(jdbc().queryForObject("select count(*) from offline_admissions where student_id = ?", Long.class, student))
			.isEqualTo(1L);
		assertThat(jdbc().queryForObject("select status from offline_admissions where student_id = ?", String.class, student))
			.isEqualTo("AWAITING_FORM");
		assertThat(jdbc().queryForObject("select created_by from offline_admissions where student_id = ?", UUID.class, student))
			.isEqualTo(masterId);
	}

	@Test
	void deskCreateWithoutTheFlagMakesNoAdmission() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String body = "{\"fullName\":\"Online Student\",\"phone\":\"" + uniquePhone() + "\",\"email\":\"" + uniqueEmail() + "\"}";

		ResultActions created = deskCreate(master, body);
		created.andExpect(status().isCreated());
		UUID student = UUID.fromString(readTree(created).get("id").asText());

		assertThat(jdbc().queryForObject("select count(*) from offline_admissions where student_id = ?", Long.class, student))
			.isZero();
	}

	@Test
	void instructorCannotDeskCreate() throws Exception {
		String instructor = token(seed("INSTRUCTOR", "Instructor"));

		deskCreate(instructor, deskBody("Blocked Student", uniquePhone(), uniqueEmail(), "true"))
			.andExpect(status().isForbidden());
	}

	@Test
	void studentWithoutAdmissionGets404() throws Exception {
		String student = token(seed("STUDENT", "Plain Student"));

		mockMvc.perform(get("/api/v1/me/admission").header("Authorization", "Bearer " + student))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
	}

	@Test
	void formSavesPartialDataAndReturnsIt() throws Exception {
		UUID studentId = seed("STUDENT", "Partial Student");
		admission(studentId, "AWAITING_FORM");
		String student = token(studentId);

		JsonNode saved = saveForm(student, "{\"fullName\":\"Partial Name\",\"university\":\"BUET\"}");

		assertThat(saved.get("state").asText()).isEqualTo("AWAITING_FORM");
		assertThat(saved.get("form").get("fullName").asText()).isEqualTo("Partial Name");
		assertThat(saved.get("form").get("university").asText()).isEqualTo("BUET");
		assertThat(saved.get("form").hasNonNull("nid")).isFalse();

		JsonNode read = view(student);
		assertThat(read.get("form").get("fullName").asText()).isEqualTo("Partial Name");
	}

	@Test
	void formSaveRefusedAfterSubmit() throws Exception {
		UUID studentId = seed("STUDENT", "Submitted Student");
		admission(studentId, "AWAITING_FORM");
		String student = token(studentId);
		saveForm(student, form(validNid(), validMobile()));
		uploadPhoto(student);
		JsonNode submitted = submit(student);
		assertThat(submitted.get("state").asText()).isEqualTo("SUBMITTED");

		mockMvc.perform(put("/api/v1/me/admission/form").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"university\":\"Changed After Submit\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ADMISSION_STATE"));
	}

	@Test
	void submitRefusesEachMissingFieldByName() throws Exception {
		UUID studentId = seed("STUDENT", "Empty Student");
		admission(studentId, "AWAITING_FORM");
		String student = token(studentId);

		JsonNode refusal = submitRefused(student);

		assertThat(refusal.get("code").asText()).isEqualTo("VALIDATION_FAILED");
		Set<String> fields = fieldNames(refusal);
		assertThat(fields).contains("nid", "mobile", "dateOfBirth", "presentAddress", "permanentAddress", "hallMess");
	}

	@Test
	void submitRefusesBadNidLength() throws Exception {
		UUID studentId = seed("STUDENT", "Short Nid Student");
		admission(studentId, "AWAITING_FORM");
		String student = token(studentId);
		saveForm(student, form("12345", validMobile()));
		uploadPhoto(student);

		JsonNode refusal = submitRefused(student);

		assertThat(refusal.get("code").asText()).isEqualTo("VALIDATION_FAILED");
		assertThat(fieldNames(refusal)).contains("nid");
	}

	@Test
	void submitRefusesBadMobile() throws Exception {
		UUID studentId = seed("STUDENT", "Bad Mobile Student");
		admission(studentId, "AWAITING_FORM");
		String student = token(studentId);
		saveForm(student, form(validNid(), "12345"));
		uploadPhoto(student);

		JsonNode refusal = submitRefused(student);

		assertThat(refusal.get("code").asText()).isEqualTo("VALIDATION_FAILED");
		assertThat(fieldNames(refusal)).contains("mobile");
	}

	@Test
	void submitWithEverythingRequiredMovesToSubmitted() throws Exception {
		UUID studentId = seed("STUDENT", "Complete Student");
		admission(studentId, "AWAITING_FORM");
		String student = token(studentId);
		saveForm(student, form(validNid(), validMobile()));
		uploadPhoto(student);

		JsonNode submitted = submit(student);

		assertThat(submitted.get("state").asText()).isEqualTo("SUBMITTED");
		assertThat(submitted.get("submitCount").asInt()).isEqualTo(1);
		assertThat(submitted.get("hasPhoto").asBoolean()).isTrue();
		assertThat(submitted.get("submittedAt").isNull()).isFalse();
	}

	@Test
	void submitWritesStaffNotificationOnce() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Master");
		UUID studentId = seed("STUDENT", "Notified Student");
		admission(studentId, "AWAITING_FORM");
		String student = token(studentId);
		saveForm(student, form(validNid(), validMobile()));
		uploadPhoto(student);

		submit(student);

		assertThat(notificationCount("ADMISSION_SUBMITTED", master)).isEqualTo(1L);
		assertThat(notificationCount("ADMISSION_RESUBMITTED", master)).isZero();
	}

	@Test
	void resubmitAfterChangesRequestedWritesResubmittedNotice() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Master");
		UUID studentId = seed("STUDENT", "Resubmitted Student");
		admission(studentId, "AWAITING_FORM");
		String student = token(studentId);
		saveForm(student, form(validNid(), validMobile()));
		uploadPhoto(student);
		assertThat(submit(student).get("submitCount").asInt()).isEqualTo(1);

		jdbc().update("update offline_admissions set status='CHANGES_REQUESTED' where student_id=?", studentId);
		saveForm(student, "{\"university\":\"Corrected University\"}");
		JsonNode resubmitted = submit(student);

		assertThat(resubmitted.get("state").asText()).isEqualTo("SUBMITTED");
		assertThat(resubmitted.get("submitCount").asInt()).isEqualTo(2);
		assertThat(notificationCount("ADMISSION_RESUBMITTED", master)).isEqualTo(1L);
	}

	@Test
	void photoRejectsANonImageUpload() throws Exception {
		UUID studentId = seed("STUDENT", "Photo Student");
		admission(studentId, "AWAITING_FORM");
		String student = token(studentId);

		mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/me/admission/photo")
			.file(new MockMultipartFile("file", "notes.txt", "text/plain", "this is not an image".getBytes()))
			.header("Authorization", "Bearer " + student))
			.andExpect(status().isBadRequest());

		assertThat(view(student).get("hasPhoto").asBoolean()).isFalse();
	}

	@Test
	void pendingStudentIsRefusedAtTake() throws Exception {
		UUID studentId = seed("STUDENT", "Pending Student");
		admission(studentId, "AWAITING_FORM");
		String student = token(studentId);
		String randomExam = UUID.randomUUID().toString();

		for (String state : new String[] { "AWAITING_FORM", "SUBMITTED", "CHANGES_REQUESTED", "READY" }) {
			jdbc().update("update offline_admissions set status=? where student_id=?", state, studentId);
			mockMvc.perform(post("/api/v1/live-exams/" + randomExam + "/take").header("Authorization", "Bearer " + student)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ADMISSION_PENDING"));
		}
	}

	// ---- helpers -----------------------------------------------------------------------------------------------------

	private JdbcTemplate jdbc() {
		return new JdbcTemplate(dataSource);
	}

	/** A Bangladesh mobile in the +880 form, unique enough for one test run. */
	private static String uniquePhone() {
		return "+8801" + ThreadLocalRandom.current().nextInt(300000000, 999999999);
	}

	private static String uniqueEmail() {
		return "offline-form-" + UUID.randomUUID() + "@example.com";
	}

	/** A 13-digit NID. */
	private static String validNid() {
		return String.format("%013d", ThreadLocalRandom.current().nextLong(1_000_000_000_000L));
	}

	/** An 11-digit Bangladesh mobile starting 017. */
	private static String validMobile() {
		return "017" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
	}

	/** One desk-creation body. {@code offlineAdmission} is written as raw JSON, so pass "true" or "false". */
	private static String deskBody(String fullName, String phone, String email, String offlineAdmission) {
		return "{\"fullName\":\"%s\",\"phone\":\"%s\",\"email\":\"%s\",\"offlineAdmission\":%s}".formatted(fullName, phone, email,
				offlineAdmission);
	}

	/** The whole admission form, with every required field filled. */
	private static String form(String nid, String mobile) {
		return """
				{"fullName":"Rahim Uddin","fatherName":"Karim Uddin","motherName":"Amena Begum",
				 "dateOfBirth":"2004-05-10","email":"%s","gender":"MALE","university":"Dhaka University",
				 "semesterBatch":"2024-25","nid":"%s","mobile":"%s","emergencyMobile":"01912345678",
				 "hallMess":"Shahid Minar Hall","thana":"Dhanmondi","district":"Dhaka",
				 "presentAddress":"House 1, Road 2, Dhaka","permanentAddress":"Village, Thana, District"}
				""".formatted(uniqueEmail(), nid, mobile);
	}

	private ResultActions deskCreate(String token, String body) throws Exception {
		return mockMvc.perform(post("/api/v1/students").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	/** Inserts an admission row for a student, as the desk flow would, created by one desk staff user per test. */
	private void admission(UUID studentId, String status) {
		if (deskStaff == null) {
			deskStaff = seed("MASTER_ADMIN", "Desk Staff");
		}
		jdbc().update("insert into offline_admissions (id, student_id, status, created_by, created_at, submit_count)"
				+ " values (?, ?, ?, ?, now(), 0)", UUID.randomUUID(), studentId, status, deskStaff);
	}

	private JsonNode view(String token) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/me/admission").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn();
		return json.readTree(result.getResponse().getContentAsString());
	}

	private JsonNode saveForm(String token, String body) throws Exception {
		MvcResult result = mockMvc.perform(put("/api/v1/me/admission/form").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body))
			.andExpect(status().isOk())
			.andReturn();
		return json.readTree(result.getResponse().getContentAsString());
	}

	private void uploadPhoto(String token) throws Exception {
		mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/me/admission/photo")
			.file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpeg()))
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.hasPhoto").value(true));
	}

	private JsonNode submit(String token) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/me/admission/submit").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn();
		return json.readTree(result.getResponse().getContentAsString());
	}

	/** A submit that must be refused with 400; returns the error body. */
	private JsonNode submitRefused(String token) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/me/admission/submit").header("Authorization", "Bearer " + token))
			.andExpect(status().isBadRequest())
			.andReturn();
		return json.readTree(result.getResponse().getContentAsString());
	}

	private JsonNode readTree(ResultActions actions) throws Exception {
		MvcResult result = actions.andReturn();
		return json.readTree(result.getResponse().getContentAsString());
	}

	private static Set<String> fieldNames(JsonNode refusal) {
		Set<String> fields = new HashSet<>();
		refusal.get("fieldErrors").forEach(error -> fields.add(error.get("field").asText()));
		return fields;
	}

	private long notificationCount(String kind, UUID recipient) {
		Long count = jdbc().queryForObject("select count(*) from staff_notifications where kind = ? and recipient_id = ?", Long.class,
				kind, recipient);
		return count == null ? 0L : count;
	}

	/** A small real JPEG, made in memory. */
	private static byte[] jpeg() throws IOException {
		BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "jpg", out);
		return out.toByteArray();
	}

	private UUID seed(String role, String name) {
		UUID id = UUID.randomUUID();
		String phone = "+8801" + ThreadLocalRandom.current().nextInt(300000000, 999999999);
		jdbc().update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, phone, "offline-" + id + "@example.com", passwords.encode("password1"), role);
		if ("STUDENT".equals(role)) {
			jdbc().update("INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, ?, ?)", id, name,
					"O" + id.toString().substring(0, 8));
		}
		else {
			jdbc().update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, ?)", id, name);
		}
		return id;
	}

	private String token(UUID userId) throws Exception {
		String phone = jdbc().queryForObject("select phone from users where id = ?", String.class, userId);
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

	@TestConfiguration
	static class ClockConfig {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(Instant.parse("2026-10-20T04:00:00Z"));
		}

	}

}
