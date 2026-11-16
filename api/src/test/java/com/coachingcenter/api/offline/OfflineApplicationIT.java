package com.coachingcenter.api.offline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;
import com.coachingcenter.api.auth.CapturingEmailSender;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The public application for a batch (public-admission plan, sections 2 A, B and 5; P1, P3, P10, P11): an applicant with no account
 * starts, fills in, photographs and sends the form under a token, with no email code; a signed-in student applies with the form prefilled
 * from the account; the same batch is refused twice while open; two batches can be open at once; closed, archived and staff emails are
 * refused; the reopen link lasts seven days. Each test uses its own students, batches and emails, so the class runs again on the same
 * database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, OfflineApplicationIT.ClockConfig.class })
@TestPropertySource(properties = { "app.offline-admission.enabled=true", "app.offline-admission.visitor-applications=true" })
class OfflineApplicationIT extends PostgresIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-11-13T04:00:00Z");

	private static final Pattern REOPEN = Pattern.compile("/admission/reopen/([0-9a-f]{64})");

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

	@Autowired
	private CapturingEmailSender emails;

	@BeforeEach
	void fixTheClock() {
		clock.set(NOW);
	}

	@Test
	void anApplicantWithNoAccountSendsTheFormWithNoEmailCodeAndTheSameEmailOpensTheSameApplication() throws Exception {
		UUID masterId = seed("MASTER_ADMIN", "Master");
		String master = token(masterId);
		UUID batch = batch(master, "Public " + unique(), true);
		String email = uniqueEmail();

		JsonNode started = respond(post("/api/v1/public/offline-applications", null, formBody(batch, email, validMobile())), 201);
		String token = started.get("token").asText();
		UUID admission = UUID.fromString(started.get("admission").get("id").asText());
		assertThat(started.get("admission").get("state").asText()).isEqualTo("AWAITING_FORM");
		assertThat(started.get("admission").get("hasPhoto").asBoolean()).isFalse();
		assertThat(jdbc().queryForObject("select source from offline_admissions where id = ?", String.class, admission)).isEqualTo("PUBLIC");
		assertThat(jdbc().queryForObject("select count(*) from offline_admissions where student_id is null and id = ?", Long.class,
				admission)).isEqualTo(1L);

		// The same email for the same batch while it is open is refused with no form and no token (F7). A private link to the open
		// application is mailed instead, and the first token keeps working.
		JsonNode again = respond(post("/api/v1/public/offline-applications", null, formBody(batch, email, validMobile())), 409);
		assertThat(again.get("code").asText()).isEqualTo("APPLICATION_EXISTS");
		assertThat(again.has("token")).isFalse();
		assertThat(again.has("admission")).isFalse();
		assertThat(emails.latestRendered(email)).contains("Continue my application");
		respond(put("/api/v1/public/offline-applications/" + token + "/form", "{\"fullName\":\"Changed\"}"), 200);

		respond(put("/api/v1/public/offline-applications/" + token + "/form", fullForm(email)), 200);
		JsonNode photo = respondTo(multipart(HttpMethod.PUT, "/api/v1/public/offline-applications/" + token + "/photo")
			.file(new MockMultipartFile("file", "me.png", "image/png", png())), 200);
		assertThat(photo.get("hasPhoto").asBoolean()).isTrue();

		JsonNode sent = respond(post("/api/v1/public/offline-applications/" + token + "/submit", null, null), 200);
		assertThat(sent.get("state").asText()).isEqualTo("SUBMITTED");
		assertThat(jdbc().queryForObject("select count(*) from offline_admission_submissions where admission_id = ? and submitted_by is null",
				Long.class, admission)).isEqualTo(1L);
		// The applicant gets one mail saying the application was received; the office gets the staff notice. No code is asked for.
		assertThat(emails.latestRendered(email)).contains("Application received");
		assertThat(notificationCount("ADMISSION_SUBMITTED", masterId)).isPositive();

		// Once sent, the same email is still refused, and no link is mailed: the form cannot be changed, so it would open nothing.
		JsonNode afterSend = respond(post("/api/v1/public/offline-applications", null, formBody(batch, email, validMobile())), 409);
		assertThat(afterSend.get("code").asText()).isEqualTo("APPLICATION_EXISTS");
		assertThat(emails.latestRendered(email)).contains("Application received");
	}

	@Test
	void aSubmittedFormNeedsItsPhotoAndAFormWithAnEmptyRequiredFieldIsRefused() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID batch = batch(master, "Photo " + unique(), true);
		String email = uniqueEmail();
		String token = respond(post("/api/v1/public/offline-applications", null, formBody(batch, email, validMobile())), 201)
			.get("token").asText();
		JsonNode refused = respond(post("/api/v1/public/offline-applications/" + token + "/submit", null, null), 400);
		assertThat(fieldNames(refused)).contains("fullName");
		respond(put("/api/v1/public/offline-applications/" + token + "/form", "{\"fullName\":\"\"}"), 200);
		respondTo(multipart(HttpMethod.PUT, "/api/v1/public/offline-applications/" + token + "/photo")
			.file(new MockMultipartFile("file", "notes.txt", "text/plain", "not a picture".getBytes())), 400);
	}

	@Test
	void theSameBatchIsRefusedForASignedInStudentWhileItIsOpenAndTwoBatchesCanBeOpenAtOnce() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID first = batch(master, "First " + unique(), true);
		UUID second = batch(master, "Second " + unique(), true);
		UUID student = seedStudent("Rahim", uniqueEmail(), validMobile());
		String studentToken = token(student);

		JsonNode open = respond(post("/api/v1/me/admission/apply", studentToken, "{\"batchId\":\"" + first + "\"}"), 201);
		assertThat(open.get("state").asText()).isEqualTo("AWAITING_FORM");
		assertThat(open.get("source").asText()).isEqualTo("ACCOUNT");
		// F2: the same batch while it is open gives back that application, so a refresh loses nothing.
		JsonNode again = respond(post("/api/v1/me/admission/apply", studentToken, "{\"batchId\":\"" + first + "\"}"), 201);
		assertThat(again.get("id").asText()).isEqualTo(open.get("id").asText());
		respond(post("/api/v1/me/admission/apply", studentToken, "{\"batchId\":\"" + second + "\"}"), 201);

		JsonNode list = json.readTree(mockMvc.perform(get("/api/v1/me/admissions").header("Authorization", "Bearer " + studentToken))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		assertThat(list).hasSize(2);
		assertThat(list.get(0).get("batchId").asText()).isIn(first.toString(), second.toString());
	}

	@Test
	void theFormOfASignedInApplicantIsPrefilledFromTheAccount() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID batch = batch(master, "Prefill " + unique(), true);
		String email = uniqueEmail();
		String phone = validMobile();
		UUID student = seedStudent("Karim Ahmed", email, phone);
		String studentToken = token(student);
		respond(post("/api/v1/me/admission/apply", studentToken, "{\"batchId\":\"" + batch + "\"}"), 201);

		JsonNode current = json.readTree(mockMvc.perform(get("/api/v1/me/admission").header("Authorization", "Bearer " + studentToken))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		assertThat(current.get("form").get("fullName").isNull()).isFalse();
		assertThat(current.get("form").get("fullName").asText()).isEqualTo("Karim Ahmed");
		assertThat(current.get("form").get("email").asText()).isEqualTo(email);
	}

	@Test
	void aBatchWithAdmissionsClosedOrArchivedTakesNoApplication() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID closed = batch(master, "Closed " + unique(), false);
		respond(post("/api/v1/public/offline-applications", null, formBody(closed, uniqueEmail(), validMobile())), 409);
		UUID archived = batch(master, "Archived " + unique(), true);
		respond(post("/api/v1/offline-batches/" + archived + "/archive", master, null), 200);
		respond(post("/api/v1/public/offline-applications", null, formBody(archived, uniqueEmail(), validMobile())), 409);
		UUID student = seedStudent("Closed Student", uniqueEmail(), validMobile());
		respond(post("/api/v1/me/admission/apply", token(student), "{\"batchId\":\"" + closed + "\"}"), 409);
	}

	@Test
	void anEmailThatBelongsToAStaffAccountCannotApply() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID batch = batch(master, "Staff mail " + unique(), true);
		UUID staff = seed("SYSTEM_ADMIN", "Staff");
		String staffEmail = jdbc().queryForObject("select email from users where id = ?", String.class, staff);
		JsonNode refused = respond(post("/api/v1/public/offline-applications", null, formBody(batch, staffEmail, validMobile())), 400);
		assertThat(fieldNames(refused)).contains("email");
	}

	@Test
	void theReopenLinkReopensTheFormForSevenDaysThenExpires() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID batch = batch(master, "Reopen " + unique(), true);
		String email = uniqueEmail();
		JsonNode started = respond(post("/api/v1/public/offline-applications", null, formBody(batch, email, validMobile())), 201);
		String token = started.get("token").asText();
		UUID admission = UUID.fromString(started.get("admission").get("id").asText());
		respond(put("/api/v1/public/offline-applications/" + token + "/form", fullForm(email)), 200);
		respondTo(multipart(HttpMethod.PUT, "/api/v1/public/offline-applications/" + token + "/photo")
			.file(new MockMultipartFile("file", "me.jpg", "image/jpeg", jpeg())), 200);
		respond(post("/api/v1/public/offline-applications/" + token + "/submit", null, null), 200);

		respond(post("/api/v1/offline-admissions/" + admission + "/request-changes", master, "{\"reason\":\"NID is short a digit\"}"), 200);
		String mail = emails.latestRendered(email);
		Matcher link = REOPEN.matcher(mail);
		assertThat(link.find()).isTrue();
		String linkToken = link.group(1);
		assertThat(mail).contains("NID is short a digit");

		JsonNode reopened = json.readTree(mockMvc.perform(get("/api/v1/public/offline-applications/reopen/" + linkToken))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		assertThat(reopened.get("admission").get("state").asText()).isEqualTo("CHANGES_REQUESTED");
		assertThat(reopened.get("admission").get("changesReason").asText()).isEqualTo("NID is short a digit");
		String fresh = reopened.get("token").asText();
		respond(put("/api/v1/public/offline-applications/" + fresh + "/form", "{\"nid\":\"1234567890124\"}"), 200);

		// Seven days on the link has run out: it answers 400 and the form does not open.
		jdbc().update("update offline_admissions set reopen_token_expires_at = ? where id = ?",
				Timestamp.from(NOW.minusSeconds(60)), admission);
		mockMvc.perform(get("/api/v1/public/offline-applications/reopen/" + linkToken)).andExpect(status().isBadRequest());
		// Also unknown links answer 404.
		mockMvc.perform(get("/api/v1/public/offline-applications/reopen/" + "0".repeat(64))).andExpect(status().isNotFound());
	}

	@Test
	void theApplicationsOfTheOtherStudentAreNotVisibleToThisOne() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID batch = batch(master, "Private " + unique(), true);
		UUID owner = seedStudent("Owner", uniqueEmail(), validMobile());
		UUID other = seedStudent("Other", uniqueEmail(), validMobile());
		JsonNode created = respond(post("/api/v1/me/admission/apply", token(owner), "{\"batchId\":\"" + batch + "\"}"), 201);
		String id = created.get("id").asText();
		mockMvc.perform(get("/api/v1/me/admissions/" + id + "/items").header("Authorization", "Bearer " + token(other)))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/me/admissions/" + id + "/fees").header("Authorization", "Bearer " + token(other)))
			.andExpect(status().isNotFound());
	}

	// ---- helpers ----------------------------------------------------------------------------------------------------------

	private JdbcTemplate jdbc() {
		return new JdbcTemplate(dataSource);
	}

	private ResultActions post(String url, String token, String body) throws Exception {
		var request = MockMvcRequestBuilders.post(url);
		if (token != null) {
			request = request.header("Authorization", "Bearer " + token);
		}
		if (body != null) {
			request = request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mockMvc.perform(request);
	}

	private ResultActions put(String url, String body) throws Exception {
		return mockMvc.perform(MockMvcRequestBuilders.put(url).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private JsonNode respond(ResultActions actions, int expected) throws Exception {
		MvcResult result = actions.andExpect(status().is(expected)).andReturn();
		String text = result.getResponse().getContentAsString();
		return text.isBlank() ? json.createObjectNode() : json.readTree(text);
	}

	private JsonNode respondTo(RequestBuilder request, int expected) throws Exception {
		return respond(mockMvc.perform(request), expected);
	}

	private static java.util.Set<String> fieldNames(JsonNode refusal) {
		java.util.Set<String> fields = new java.util.HashSet<>();
		refusal.get("fieldErrors").forEach(error -> fields.add(error.get("field").asText()));
		return fields;
	}

	private long notificationCount(String kind, UUID recipient) {
		Long count = jdbc().queryForObject("select count(*) from staff_notifications where kind = ? and recipient_id = ?", Long.class,
				kind, recipient);
		return count == null ? 0L : count;
	}

	/** A batch with an optional open switch, a fee and an end date, made through the API. */
	private UUID batch(String token, String name, boolean open) throws Exception {
		MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/offline-batches").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"nameEn":"%s","nameBn":"ব্যাচ","endsOn":"2027-04-30","courseFee":3000,"admissionsOpen":%s}
					""".formatted(name, open)))
			.andExpect(status().isCreated())
			.andReturn();
		UUID id = UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
		// A batch starts as a draft (V103); the helper publishes it, as the Master Admin does before students apply.
		mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/offline-batches/" + id + "/publish").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		return id;
	}

	/** The public start body: the batch, the email, and a phone, with the rest of the form left for the form route. */
	private String formBody(UUID batch, String email, String mobile) {
		return "{\"batchId\":\"" + batch + "\",\"email\":\"" + email + "\",\"mobile\":\"" + mobile + "\"}";
	}

	/** Every required field of the form, filled in, as the applicant sends it. */
	private String fullForm(String email) {
		return "{\"fullName\":\"Rahim Uddin\",\"fatherName\":\"Karim Uddin\",\"motherName\":\"Amena Begum\","
				+ "\"dateOfBirth\":\"2004-05-10\",\"email\":\"" + email + "\",\"gender\":\"MALE\",\"university\":\"Dhaka University\","
				+ "\"semesterBatch\":\"2024-25\",\"nid\":\"1234567890123\",\"mobile\":\"" + validMobile() + "\","
				+ "\"emergencyMobile\":\"01812345678\",\"hallMess\":\"Shahid Minar Hall\",\"presentAddress\":\"House 1, Dhaka\","
				+ "\"permanentAddress\":\"Village, District\"}";
	}

	private UUID seed(String role, String name) {
		return seedStudent(role, name, uniqueEmail(), validMobile(), role);
	}

	private UUID seedStudent(String name, String email, String phone) {
		return seedStudent("STUDENT", name, email, phone, "STUDENT");
	}

	private UUID seedStudent(String role, String name, String email, String phone, String kind) {
		String stored = phone.startsWith("01") ? "+880" + phone.substring(1) : phone;
		UUID id = UUID.randomUUID();
		jdbc().update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, stored, email, passwords.encode("password1"), kind);
		if ("STUDENT".equals(kind)) {
			jdbc().update("INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, ?, ?)", id, name,
					"A" + id.toString().substring(0, 8));
		}
		else {
			jdbc().update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, ?)", id, name);
		}
		return id;
	}

	private String token(UUID userId) throws Exception {
		String phone = jdbc().queryForObject("select phone from users where id = ?", String.class, userId);
		MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login").header("X-Client-Type", "mobile")
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

	private static String uniqueEmail() {
		return "offline-app-" + UUID.randomUUID() + "@example.com";
	}

	private static String unique() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	/** An 11-digit Bangladesh mobile starting 017. */
	private static String validMobile() {
		return "017" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
	}

	private static byte[] png() throws IOException {
		BufferedImage image = new BufferedImage(64, 36, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		return out.toByteArray();
	}

	private static byte[] jpeg() throws IOException {
		BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "jpg", out);
		return out.toByteArray();
	}

	@TestConfiguration
	static class ClockConfig {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(NOW);
		}

	}

}
