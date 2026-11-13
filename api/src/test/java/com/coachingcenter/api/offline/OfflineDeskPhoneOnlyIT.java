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
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import javax.imageio.ImageIO;
import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The phone-only desk start (product decision, 13 November 2026): the desk types a phone number, the account and the admission are made
 * with a 10-minute activation code, the student activates with it, and fills in the form. The clock is fixed at 10:00 in Dhaka on
 * 20 October 2026 and moved by the tests that need time to pass.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, OfflineDeskPhoneOnlyIT.ClockConfig.class })
@TestPropertySource(properties = "app.offline-admission.enabled=true")
class OfflineDeskPhoneOnlyIT extends PostgresIntegrationTest {

	private static final Instant START = Instant.parse("2026-10-20T04:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Autowired
	private MutableClock clock;

	@BeforeEach
	void startClock() {
		clock.set(START);
	}

	@Test
	void deskStartNeedsOnlyAPhoneAndGivesACodeTenMinutesAhead() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Master");
		String localPhone = localPhone();

		JsonNode created = deskCreated(token(master), "{\"phone\":\"" + localPhone + "\"}");

		String phone = "+88" + localPhone;
		assertThat(created.get("phone").asText()).isEqualTo(phone);
		assertThat(created.get("code").asText()).hasSize(8);
		assertThat(Instant.parse(created.get("expiresAt").asText())).isEqualTo(START.plus(Duration.ofMinutes(10)));

		UUID admissionId = UUID.fromString(created.get("admissionId").asText());
		UUID studentId = UUID.fromString(created.get("studentId").asText());
		assertThat(jdbc().queryForObject("select email from users where id = ?", String.class, studentId)).isNull();
		assertThat(jdbc().queryForObject("select status from users where id = ?", String.class, studentId))
			.isEqualTo("PENDING_ACTIVATION");
		assertThat(jdbc().queryForObject("select full_name from student_profiles where user_id = ?", String.class, studentId))
			.isEqualTo(phone);
		assertThat(jdbc().queryForObject("select status from offline_admissions where id = ?", String.class, admissionId))
			.isEqualTo("AWAITING_FORM");
		assertThat(jdbc().queryForObject("select activation_code from offline_admissions where id = ?", String.class, admissionId))
			.isEqualTo(created.get("code").asText());
		assertThat(jdbc().queryForObject("select count(*) from one_time_codes where user_id = ? and purpose = 'ACTIVATION'"
				+ " and used_at is null", Long.class, studentId)).isEqualTo(1L);
	}

	@Test
	void theLiveCodeCanBeReadAgainUntilItExpires() throws Exception {
		String student = token(seed("MASTER_ADMIN", "Master"));
		JsonNode created = deskCreated(student, "{\"phone\":\"" + localPhone() + "\"}");
		UUID admissionId = UUID.fromString(created.get("admissionId").asText());

		clock.set(START.plus(Duration.ofMinutes(4)));
		JsonNode read = codeView(student, admissionId);

		assertThat(read.get("code").asText()).isEqualTo(created.get("code").asText());
		assertThat(read.get("expiresAt").asText()).isEqualTo(created.get("expiresAt").asText());
		assertThat(read.get("expired").asBoolean()).isFalse();
		assertThat(read.get("activated").asBoolean()).isFalse();
	}

	@Test
	void theStudentActivatesWithThePhoneAndTheCodeAndThenLogsIn() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String localPhone = localPhone();
		JsonNode created = deskCreated(master, "{\"phone\":\"" + localPhone + "\"}");
		String phone = "+88" + localPhone;
		UUID admissionId = UUID.fromString(created.get("admissionId").asText());

		activate(phone, created.get("code").asText(), "new-password-1").andExpect(status().isNoContent());

		String student = login(phone, "new-password-1");
		assertThat(student).isNotBlank();
		assertThat(jdbc().queryForObject("select status from users where phone = ?", String.class, phone)).isEqualTo("ACTIVE");
		// The activation clears the plain code and keeps the time it happened.
		assertThat(jdbc().queryForObject("select activation_code from offline_admissions where id = ?", String.class, admissionId))
			.isNull();
		assertThat(jdbc().queryForObject("select activation_code_expires_at from offline_admissions where id = ?", Instant.class,
				admissionId)).isNull();
		assertThat(jdbc().queryForObject("select activated_at from offline_admissions where id = ?", Instant.class, admissionId))
			.isEqualTo(START);
	}

	@Test
	void afterActivationTheDeskReadsNoCodeAndCannotMakeANewOne() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String phone = "+88" + localPhone();
		JsonNode created = deskCreated(master, "{\"phone\":\"" + phone.substring(3) + "\"}");
		UUID admissionId = UUID.fromString(created.get("admissionId").asText());
		activate(phone, created.get("code").asText(), "new-password-1").andExpect(status().isNoContent());

		JsonNode read = codeView(master, admissionId);
		assertThat(read.get("activated").asBoolean()).isTrue();
		assertThat(read.get("code").isNull()).isTrue();

		mockMvc.perform(post("/api/v1/offline-admissions/" + admissionId + "/activation-code")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ADMISSION_STATE"));
	}

	@Test
	void regeneratingReplacesTheCodeAndTheOldOneStopsWorking() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String phone = "+88" + localPhone();
		JsonNode created = deskCreated(master, "{\"phone\":\"" + phone.substring(3) + "\"}");
		UUID admissionId = UUID.fromString(created.get("admissionId").asText());
		String oldCode = created.get("code").asText();

		clock.set(START.plus(Duration.ofMinutes(3)));
		JsonNode issued = regenerated(master, admissionId);

		String newCode = issued.get("code").asText();
		assertThat(newCode).isNotEqualTo(oldCode);
		assertThat(Instant.parse(issued.get("expiresAt").asText())).isEqualTo(START.plus(Duration.ofMinutes(13)));
		assertThat(codeView(master, admissionId).get("code").asText()).isEqualTo(newCode);

		activate(phone, oldCode, "new-password-1")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("CODE_ALREADY_USED"));
		activate(phone, newCode, "new-password-1").andExpect(status().isNoContent());
	}

	@Test
	void anExpiredCodeIsNotReturnedAndCannotActivate() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String phone = "+88" + localPhone();
		JsonNode created = deskCreated(master, "{\"phone\":\"" + phone.substring(3) + "\"}");
		UUID admissionId = UUID.fromString(created.get("admissionId").asText());
		String code = created.get("code").asText();

		clock.set(START.plus(Duration.ofMinutes(11)));
		JsonNode read = codeView(master, admissionId);

		assertThat(read.get("code").isNull()).isTrue();
		assertThat(read.get("expired").asBoolean()).isTrue();
		assertThat(read.get("activated").asBoolean()).isFalse();
		assertThat(Instant.parse(read.get("expiresAt").asText())).isEqualTo(START.plus(Duration.ofMinutes(10)));
		assertThat(jdbc().queryForObject("select activation_code from offline_admissions where id = ?", String.class, admissionId))
			.isNull();

		activate(phone, code, "new-password-1")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("CODE_EXPIRED"));
	}

	@Test
	void aPhoneThatAlreadyBelongsToAnAccountIsRefused() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String phone = "+88" + localPhone();
		seedStudent(phone, uniqueEmail());

		deskCreate(master, "{\"phone\":\"" + phone + "\"}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DUPLICATE_PHONE"));
	}

	@Test
	void aPhoneThatIsNotAMobileNumberIsRefusedByField() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));

		JsonNode refusal = readTree(deskCreate(master, "{\"phone\":\"12345\"}").andExpect(status().isBadRequest()));
		assertThat(refusal.get("code").asText()).isEqualTo("VALIDATION_FAILED");
		assertThat(fieldNames(refusal)).contains("phone");

		JsonNode missing = readTree(deskCreate(master, "{}").andExpect(status().isBadRequest()));
		assertThat(fieldNames(missing)).contains("phone");
	}

	@Test
	void anInstructorCannotStartOrReadADeskAdmission() throws Exception {
		String instructor = token(seed("INSTRUCTOR", "Instructor"));
		String phone = "+88" + localPhone();

		deskCreate(instructor, "{\"phone\":\"" + phone + "\"}").andExpect(status().isForbidden());
		assertThat(jdbc().queryForObject("select count(*) from users where phone = ?", Long.class, phone)).isZero();

		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID admissionId = UUID.fromString(deskCreated(master, "{\"phone\":\"" + phone.substring(3) + "\"}")
			.get("admissionId").asText());
		mockMvc.perform(get("/api/v1/offline-admissions/" + admissionId + "/activation-code")
			.header("Authorization", "Bearer " + instructor))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/offline-admissions/" + admissionId + "/activation-code")
			.header("Authorization", "Bearer " + instructor))
			.andExpect(status().isForbidden());
	}

	@Test
	void theQueueListsTheAwaitingStudentWithItsCodeExpiryAndTheCountsCountIt() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		JsonNode created = deskCreated(master, "{\"phone\":\"" + localPhone() + "\"}");
		String admissionId = created.get("admissionId").asText();

		JsonNode row = queueRow(master, admissionId);

		assertThat(row.get("status").asText()).isEqualTo("AWAITING_FORM");
		assertThat(row.get("activated").asBoolean()).isFalse();
		assertThat(Instant.parse(row.get("codeExpiresAt").asText())).isEqualTo(START.plus(Duration.ofMinutes(10)));

		clock.set(START.plus(Duration.ofMinutes(11)));
		assertThat(queueRow(master, admissionId).get("codeExpiresAt").isNull()).isTrue();

		JsonNode counts = readTree(mockMvc.perform(get("/api/v1/offline-admissions/counts")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk()));
		assertThat(counts.get("awaitingForm").asLong()).isGreaterThanOrEqualTo(1L);
	}

	@Test
	void theSubmitNoLongerNeedsThanaOrDistrict() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String phone = "+88" + localPhone();
		JsonNode created = deskCreated(master, "{\"phone\":\"" + phone.substring(3) + "\"}");
		activate(phone, created.get("code").asText(), "new-password-1").andExpect(status().isNoContent());
		String student = login(phone, "new-password-1");

		saveForm(student, formWithoutThanaOrDistrict(uniqueEmail()));
		uploadPhoto(student);
		JsonNode submitted = submit(student);

		assertThat(submitted.get("state").asText()).isEqualTo("SUBMITTED");
		assertThat(submitted.get("submitCount").asInt()).isEqualTo(1);
	}

	@Test
	void theSubmitCopiesTheFullNameAndTheEmailToTheAccount() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String phone = "+88" + localPhone();
		JsonNode created = deskCreated(master, "{\"phone\":\"" + phone.substring(3) + "\"}");
		UUID studentId = UUID.fromString(created.get("studentId").asText());
		activate(phone, created.get("code").asText(), "new-password-1").andExpect(status().isNoContent());
		String student = login(phone, "new-password-1");
		String email = uniqueEmail();

		saveForm(student, formWithoutThanaOrDistrict(email));
		uploadPhoto(student);
		submit(student);

		assertThat(jdbc().queryForObject("select full_name from student_profiles where user_id = ?", String.class, studentId))
			.isEqualTo("Rahim Uddin");
		assertThat(jdbc().queryForObject("select email from users where id = ?", String.class, studentId)).isEqualTo(email);
	}

	@Test
	void theSubmitRefusesAnEmailThatAnotherAccountUses() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String phone = "+88" + localPhone();
		JsonNode created = deskCreated(master, "{\"phone\":\"" + phone.substring(3) + "\"}");
		UUID studentId = UUID.fromString(created.get("studentId").asText());
		activate(phone, created.get("code").asText(), "new-password-1").andExpect(status().isNoContent());
		String student = login(phone, "new-password-1");
		String taken = uniqueEmail();
		seedStudent("+88" + localPhone(), taken);

		saveForm(student, formWithoutThanaOrDistrict(taken));
		uploadPhoto(student);
		JsonNode refusal = submitRefused(student);

		assertThat(refusal.get("code").asText()).isEqualTo("VALIDATION_FAILED");
		assertThat(fieldNames(refusal)).contains("email");
		assertThat(refusal.get("fieldErrors").toString()).contains("This email is already used by another account.");
		assertThat(jdbc().queryForObject("select status from offline_admissions where student_id = ?", String.class, studentId))
			.isEqualTo("AWAITING_FORM");
		assertThat(jdbc().queryForObject("select email from users where id = ?", String.class, studentId)).isNull();
	}

	@Test
	void aStudentWithNoEmailIsNotMailedAndNothingFails() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String phone = "+88" + localPhone();
		JsonNode created = deskCreated(master, "{\"phone\":\"" + phone.substring(3) + "\"}");
		activate(phone, created.get("code").asText(), "new-password-1").andExpect(status().isNoContent());
		String student = login(phone, "new-password-1");

		// A password change mails the account; the desk account has no address, so no mail is sent and the change still succeeds.
		mockMvc.perform(post("/api/v1/auth/change-password").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"currentPassword\":\"new-password-1\",\"newPassword\":\"newer-password-2\"}"))
			.andExpect(status().isNoContent());
		login(phone, "newer-password-2");
	}

	// ---- helpers -----------------------------------------------------------------------------------------------------

	private JdbcTemplate jdbc() {
		return new JdbcTemplate(dataSource);
	}

	/** A Bangladesh mobile in the 01 form, unique enough for one test run. */
	private static String localPhone() {
		return "01" + ThreadLocalRandom.current().nextInt(300000000, 999999999);
	}

	private static String uniqueEmail() {
		return "desk-phone-" + UUID.randomUUID() + "@example.com";
	}

	/** A 13-digit NID. */
	private static String validNid() {
		return String.format("%013d", ThreadLocalRandom.current().nextLong(1_000_000_000_000L));
	}

	/** An 11-digit Bangladesh mobile starting 017. */
	private static String validMobile() {
		return "017" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
	}

	/** The form as the student sends it now: no thana, no district. */
	private static String formWithoutThanaOrDistrict(String email) {
		return """
				{"fullName":"Rahim Uddin","fatherName":"Karim Uddin","motherName":"Amena Begum",
				 "dateOfBirth":"2004-05-10","email":"%s","gender":"MALE","university":"Dhaka University",
				 "semesterBatch":"2024-25","nid":"%s","mobile":"%s","emergencyMobile":"01912345678",
				 "hallMess":"Shahid Minar Hall",
				 "presentAddress":"House 1, Road 2, Dhaka","permanentAddress":"Village, Thana, District"}
				""".formatted(email, validNid(), validMobile());
	}

	private ResultActions deskCreate(String token, String body) throws Exception {
		return mockMvc.perform(post("/api/v1/offline-admissions/desk").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	/** A desk start that must succeed with 201; returns the response body. */
	private JsonNode deskCreated(String token, String body) throws Exception {
		return readTree(deskCreate(token, body).andExpect(status().isCreated()));
	}

	private JsonNode codeView(String token, UUID admissionId) throws Exception {
		return readTree(mockMvc.perform(get("/api/v1/offline-admissions/" + admissionId + "/activation-code")
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk()));
	}

	private JsonNode regenerated(String token, UUID admissionId) throws Exception {
		return readTree(mockMvc.perform(post("/api/v1/offline-admissions/" + admissionId + "/activation-code")
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk()));
	}

	/** The queue row of one admission, found by paging through the queue (the other tests share the database). */
	private JsonNode queueRow(String token, String admissionId) throws Exception {
		for (int page = 0;; page++) {
			JsonNode body = readTree(mockMvc.perform(get("/api/v1/offline-admissions")
				.param("status", "AWAITING_FORM").param("page", String.valueOf(page)).param("size", "100")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()));
			for (JsonNode row : body.get("content")) {
				if (admissionId.equals(row.get("id").asText())) {
					return row;
				}
			}
			if (page + 1 >= body.get("totalPages").asInt()) {
				throw new AssertionError("The admission is not in the queue: " + admissionId);
			}
		}
	}

	private ResultActions activate(String phone, String code, String password) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/activate")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"phone\":\"%s\",\"code\":\"%s\",\"password\":\"%s\"}".formatted(phone, code, password)));
	}

	private void saveForm(String token, String body) throws Exception {
		mockMvc.perform(put("/api/v1/me/admission/form").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body))
			.andExpect(status().isOk());
	}

	private void uploadPhoto(String token) throws Exception {
		mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/me/admission/photo")
			.file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpeg()))
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
	}

	private JsonNode submit(String token) throws Exception {
		return readTree(mockMvc.perform(post("/api/v1/me/admission/submit").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk()));
	}

	/** A submit that must be refused with 400; returns the error body. */
	private JsonNode submitRefused(String token) throws Exception {
		return readTree(mockMvc.perform(post("/api/v1/me/admission/submit").header("Authorization", "Bearer " + token))
			.andExpect(status().isBadRequest()));
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

	/** A small real JPEG, made in memory. */
	private static byte[] jpeg() throws IOException {
		BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "jpg", out);
		return out.toByteArray();
	}

	/** A staff or instructor account with a known password, inserted as the other offline tests do. */
	private UUID seed(String role, String name) {
		UUID id = UUID.randomUUID();
		jdbc().update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, "+8801" + ThreadLocalRandom.current().nextInt(300000000, 999999999), "desk-" + id + "@example.com",
				passwords.encode("password1"), role);
		if ("STUDENT".equals(role)) {
			jdbc().update("INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, ?, ?)", id, name,
					"D" + id.toString().substring(0, 8));
		}
		else {
			jdbc().update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, ?)", id, name);
		}
		return id;
	}

	/** An active student with a given phone and email, as an account made before this feature has. */
	private UUID seedStudent(String phone, String email) {
		UUID id = UUID.randomUUID();
		jdbc().update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, 'STUDENT', 'ACTIVE', now(), now())
				""", id, phone, email, passwords.encode("password1"));
		jdbc().update("INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, ?, ?)", id, "Existing",
				"E" + id.toString().substring(0, 8));
		return id;
	}

	private String token(UUID userId) throws Exception {
		String phone = jdbc().queryForObject("select phone from users where id = ?", String.class, userId);
		return login(phone, "password1");
	}

	private String login(String identifier, String password) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"identifier\":\"%s\",\"password\":\"%s\"}".formatted(identifier, password)))
			.andExpect(status().isOk())
			.andReturn();
		return json.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
	}

	@TestConfiguration
	static class ClockConfig {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(START);
		}

	}

}
