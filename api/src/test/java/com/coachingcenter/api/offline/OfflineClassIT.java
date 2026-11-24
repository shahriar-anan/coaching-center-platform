package com.coachingcenter.api.offline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import javax.sql.DataSource;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Offline classes (plan sections 4.2 to 4.4, 6): HELD only after the end time, deleting a class by hand, and the student's routine
 * without any pay field. Since 24 Nov a class carries no pay (instructor-pay-simple-plan.md): no amount, no rate, and the class routes
 * for paying and not-taken marks are gone. Each test makes its own batch and classes, so the class can run again on the same database.
 * The clock is fixed at 10:00 in Dhaka on 20 October 2026.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, OfflineClassIT.ClockConfig.class })
@TestPropertySource(properties = "app.offline-admission.enabled=true")
class OfflineClassIT extends PostgresIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-10-20T04:00:00Z");

	private static final String SAMPLE = "fixtures/routine/20th BJS Foundation Batch- NOVEMBER.pdf";

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
	void fixTheClock() {
		clock.set(NOW);
	}

	@Test
	void heldAppearsOnlyAfterTheEndTime() throws Exception {
		Setup s = setup();
		UUID instructor = instructor(s.master, "Held");
		UUID batch = batch(s.master);
		JsonNode created = classAt(s.master, batch, "2026-10-20", "14:00", "15:20", instructor);
		UUID id = id(created);
		assertThat(created.get("status").asText()).isEqualTo("SCHEDULED");

		clock.set(Instant.parse("2026-10-20T09:00:00Z"));
		assertThat(classView(s.master, batch, id).get("status").asText()).isEqualTo("SCHEDULED");

		clock.set(Instant.parse("2026-10-20T10:00:00Z"));
		assertThat(classView(s.master, batch, id).get("status").asText()).isEqualTo("HELD");
	}

	@Test
	void aClassCarriesNoPayAndTheOldPayRoutesAreGone() throws Exception {
		// 24 Nov: a rate sent with a class is ignored, the class has no amount, and a held class is edited like any other.
		Setup s = setup();
		UUID instructor = instructor(s.master, "No pay");
		UUID batch = batch(s.master);
		JsonNode created = respond(post("/api/v1/offline-batches/" + batch + "/classes", s.staff, "{\"heldOn\":\"2026-10-19\",\"startsAt\":\"10:00\","
				+ "\"subject\":\"Contract Act\",\"instructorId\":\"" + instructor + "\",\"rateOverride\":400,\"inRoutine\":false}"), 201);
		UUID held = id(created);
		assertThat(created.has("amount")).isFalse();
		assertThat(created.has("rateOverride")).isFalse();
		assertThat(created.has("payment")).isFalse();
		assertThat(jdbc().queryForObject("select amount from offline_classes where id = ?", java.math.BigDecimal.class, held)).isNull();
		// The class goes into the routine: a pay-only class can no longer be made.
		assertThat(jdbc().queryForObject("select in_routine from offline_classes where id = ?", Boolean.class, held)).isTrue();
		assertThat(classView(s.master, batch, held).get("status").asText()).isEqualTo("HELD");

		JsonNode edited = respond(patch("/api/v1/offline-classes/" + held, s.staff, "{\"version\":" + created.get("version").asLong()
				+ ",\"topic\":\"Lecture-2\"}"), 200);
		assertThat(edited.get("topic").asText()).isEqualTo("Lecture-2");

		respond(post("/api/v1/offline-classes/" + held + "/payment", s.master, "{\"amount\":400}"), 404);
		respond(post("/api/v1/offline-classes/" + held + "/not-taken", s.master, "{\"reason\":\"Holiday\"}"), 404);
		respondTo(get("/api/v1/offline-classes/unsettled/counts").header("Authorization", "Bearer " + s.master), 404);
		respondTo(get("/api/v1/offline-batches/" + batch + "/rates").header("Authorization", "Bearer " + s.master), 404);
	}

	@Test
	void aPayOnlyClassFromBeforeIsLeftOutOfTheBatchList() throws Exception {
		Setup s = setup();
		UUID instructor = instructor(s.master, "Pay only");
		UUID batch = batch(s.master);
		UUID payOnly = id(classAt(s.master, batch, "2026-10-18", "09:00", "10:20", instructor));
		jdbc().update("update offline_classes set in_routine = false where id = ?", payOnly);

		JsonNode listed = respondTo(get("/api/v1/offline-batches/" + batch + "/classes").header("Authorization", "Bearer " + s.master), 200);
		assertThat(listed.toString()).doesNotContain(payOnly.toString());
	}

	@Test
	void aStudentGetsTheStudentCopyAndARoutineWithNoInstructorOrPay() throws Exception {
		Setup s = setup();
		ensureKnown(s.master, "Tuhin Hossain");
		ensureKnown(s.master, "Anarul Islam");
		makeUnknown(s.master, "Milon");
		UUID batch = batch(s.master);
		confirmedSample(s.master, batch);
		UUID studentUser = seed("STUDENT", "Rahim");
		admitted(studentUser, batch, "ONBOARDED");
		String student = token(studentUser);

		byte[] copy = bytes(get("/api/v1/me/admission/batch-routine.pdf").header("Authorization", "Bearer " + student));
		assertThat(copy.length).isGreaterThan(0);
		assertThat(text(copy)).doesNotContain("Tuhin").doesNotContain("Anarul").doesNotContain("Milon");

		String routineJson = new String(bytes(get("/api/v1/me/admission/routine?month=2026-11").header("Authorization", "Bearer " + student)),
				java.nio.charset.StandardCharsets.UTF_8);
		assertThat(routineJson).doesNotContain("Tuhin").doesNotContain("Anarul").doesNotContain("Milon").doesNotContain("amount")
			.doesNotContain("pay").doesNotContain("instructor");
		assertThat(json.readTree(routineJson).size()).isGreaterThan(0);
	}

	@Test
	void aPendingStudentGetsNotFoundOnTheRoutine() throws Exception {
		Setup s = setup();
		UUID batch = batch(s.master);
		UUID student = seed("STUDENT", "Pending");
		admitted(student, batch, "SUBMITTED");
		String token = token(student);

		respondTo(get("/api/v1/me/admission/routine?month=2026-11").header("Authorization", "Bearer " + token), 404);
		respondTo(get("/api/v1/me/admission/batch-routine.pdf").header("Authorization", "Bearer " + token), 404);
	}

	@Test
	void aClassIsDeletedByHandUnlessItWasSettledBefore() throws Exception {
		// 16 Nov: either admin deletes a class with nothing settled; one with old payment records is marked cancelled instead. A class paid
		// before 24 Nov is locked: it cannot be changed or deleted.
		Setup s = setup();
		UUID instructor = instructor(s.master, "Delete");
		UUID batch = batch(s.master);
		UUID plain = id(classAt(s.master, batch, "2026-10-19", "10:00", "11:20", instructor));
		UUID paid = id(classAt(s.master, batch, "2026-10-19", "12:00", "13:20", instructor));
		UUID rejected = id(classAt(s.master, batch, "2026-10-19", "14:00", "15:20", instructor));

		mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/offline-classes/" + plain).header("Authorization", "Bearer " + s.staff))
			.andExpect(status().isNoContent());
		assertThat(jdbc().queryForObject("select count(*) from offline_classes where id = ?", Integer.class, plain)).isZero();

		oldPayment(paid, instructor, "APPROVED");
		jdbc().update("update offline_classes set status = 'PAID' where id = ?", paid);
		mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/offline-classes/" + paid).header("Authorization", "Bearer " + s.master))
			.andExpect(status().isConflict());
		respond(patch("/api/v1/offline-classes/" + paid, s.master, "{\"topic\":\"Changed\"}"), 409);

		oldPayment(rejected, instructor, "REJECTED");
		mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/offline-classes/" + rejected).header("Authorization", "Bearer " + s.master))
			.andExpect(status().isNoContent());
		assertThat(jdbc().queryForObject("select status from offline_classes where id = ?", String.class, rejected)).isEqualTo("CANCELLED");
		JsonNode listed = respondTo(get("/api/v1/offline-batches/" + batch + "/classes").header("Authorization", "Bearer " + s.master), 200);
		assertThat(listed.toString()).doesNotContain(rejected.toString()).doesNotContain(plain.toString()).contains(paid.toString());
		mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/offline-classes/" + rejected).header("Authorization", "Bearer " + s.master))
			.andExpect(status().isNotFound());
	}

	// ---- helpers ----------------------------------------------------------------------------------------------------------

	/** A class payment as it was recorded before 24 Nov. */
	private void oldPayment(UUID classId, UUID instructor, String status) {
		UUID by = adminOf();
		jdbc().update("""
				insert into offline_class_payments (id, class_id, instructor_id, amount, method, paid_on, status, recorded_by, recorded_at)
				values (?, ?, ?, 400, 'CASH', '2026-10-19', ?, ?, now())
				""", UUID.randomUUID(), classId, instructor, status, by);
	}

	private record Setup(String master, String staff) {
	}

	private Setup setup() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		return new Setup(master, staff);
	}

	private JdbcTemplate jdbc() {
		return new JdbcTemplate(dataSource);
	}

	private ResultActions post(String url, String token, String body) throws Exception {
		MockHttpServletRequestBuilder request = MockMvcRequestBuilders.post(url).header("Authorization", "Bearer " + token);
		if (body != null) {
			request = request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mockMvc.perform(request);
	}

	private ResultActions patch(String url, String token, String body) throws Exception {
		return mockMvc.perform(MockMvcRequestBuilders.patch(url).header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private JsonNode respond(ResultActions actions, int expected) throws Exception {
		MvcResult result = actions.andExpect(status().is(expected)).andReturn();
		String text = result.getResponse().getContentAsString();
		return text.isBlank() ? json.createObjectNode() : json.readTree(text);
	}

	private JsonNode respondTo(RequestBuilder request, int expected) throws Exception {
		return respond(mockMvc.perform(request), expected);
	}

	private byte[] bytes(RequestBuilder request) throws Exception {
		return mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
	}

	private static String text(byte[] pdf) throws IOException {
		try (PDDocument document = Loader.loadPDF(pdf)) {
			return new PDFTextStripper().getText(document);
		}
	}

	private static UUID id(JsonNode node) {
		return UUID.fromString(node.get("id").asText());
	}

	private UUID batch(String token) throws Exception {
		JsonNode created = respond(post("/api/v1/offline-batches", token, "{\"nameEn\":\"Classes\",\"nameBn\":\"ক্লাস\","
				+ "\"endsOn\":\"2027-06-30\",\"courseFee\":3000}"), 201);
		return UUID.fromString(created.get("id").asText());
	}

	private UUID instructor(String token, String label) throws Exception {
		String name = label + " " + UUID.randomUUID();
		JsonNode created = respond(post("/api/v1/offline-instructors", token, "{\"name\":\"" + name + "\"}"), 201);
		return UUID.fromString(created.get("id").asText());
	}

	private JsonNode classAt(String token, UUID batch, String heldOn, String startsAt, String endsAt, UUID instructor) throws Exception {
		return respond(post("/api/v1/offline-batches/" + batch + "/classes", token, "{\"heldOn\":\"" + heldOn + "\",\"startsAt\":\""
				+ startsAt + "\",\"endsAt\":\"" + endsAt + "\",\"subject\":\"Contract Act\",\"topic\":\"Lecture-1\",\"instructorId\":\""
				+ instructor + "\"}"), 201);
	}

	/** The class as the batch's list shows it now (the list is the source of the computed status). */
	private JsonNode classView(String token, UUID batch, UUID classId) throws Exception {
		JsonNode list = respondTo(get("/api/v1/offline-batches/" + batch + "/classes").header("Authorization", "Bearer " + token),
				200);
		for (JsonNode item : list) {
			if (item.get("id").asText().equals(classId.toString())) {
				return item;
			}
		}
		throw new AssertionError("Class not in the batch list");
	}

	private UUID seed(String role, String name) {
		UUID id = UUID.randomUUID();
		jdbc().update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, "+8801" + ThreadLocalRandom.current().nextInt(300000000, 999999999), "offline-class-" + id + "@example.com",
				passwords.encode("password1"), role);
		if ("STUDENT".equals(role)) {
			jdbc().update("INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, ?, ?)", id, name,
					"S" + id.toString().substring(0, 8));
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

	/** An admission for a student in a batch, in the given state, with the form, the photo and the batch set. */
	private void admitted(UUID student, UUID batch, String status) {
		UUID admission = UUID.randomUUID();
		jdbc().update("""
				insert into offline_admissions (id, student_id, status, created_by, created_at, submit_count, submitted_at,
				  full_name, father_name, mother_name, date_of_birth, email, gender, university, semester_batch, nid, mobile,
				  emergency_mobile, hall_mess, thana, district, present_address, permanent_address, batch_id)
				values (?, ?, ?, ?, ?, 1, ?, 'Rahim Uddin', 'Karim Uddin', 'Amena Begum', '2004-05-10', ?, 'MALE',
				  'Dhaka University', '2024-25', '1234567890123', ?, '01912345678', 'Shahid Minar Hall', 'Dhanmondi', 'Dhaka',
				  'House 1, Road 2, Dhaka', 'Village, Thana, District', ?)
				""", admission, student, status, adminOf(), Timestamp.from(NOW), Timestamp.from(NOW), uniqueEmail(), validMobile(), batch);
		jdbc().update("insert into student_photos (user_id, stored_name, content_type, byte_size, updated_at) values (?, ?, ?, ?, now())",
				student, "photo-" + UUID.randomUUID() + ".jpg", "image/jpeg", 100L);
	}

	private UUID adminOf() {
		return jdbc().queryForObject("select id from users where role = 'MASTER_ADMIN' order by created_at desc limit 1", UUID.class);
	}

	private static String uniqueEmail() {
		return "offline-class-form-" + UUID.randomUUID() + "@example.com";
	}

	private static String validMobile() {
		return "017" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
	}

	// ---- the sample routine, confirmed for the student's batch ------------------------------------------------------------

	private UUID confirmedSample(String master, UUID batch) throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "20th BJS Foundation Batch- NOVEMBER.pdf", "application/pdf",
				new ClassPathResource(SAMPLE).getInputStream().readAllBytes());
		JsonNode routine = respondTo(multipart("/api/v1/offline-batches/" + batch + "/routines").file(file).param("month", "2026-11")
			.header("Authorization", "Bearer " + master), 201);
		UUID id = id(routine);
		// The sample's 27-07 typo blocks the confirm (15 Nov 2026); the admin fixes it to 27 October first. Milon stays as written
		// (24 Nov: a routine name no instructor has is not made an instructor).
		JsonNode view = respondTo(get("/api/v1/offline-routines/" + id).header("Authorization", "Bearer " + master), 200);
		int typo = -1;
		for (int index = 0; index < view.get("rows").size(); index++) {
			if ("2026-07-27".equals(view.get("rows").get(index).get("date").asText())) {
				typo = index;
			}
		}
		respond(patch("/api/v1/offline-routines/" + id + "/rows/" + typo, master, "{\"date\":\"2026-10-27\"}"), 200);
		respond(post("/api/v1/offline-routines/" + id + "/confirm", master, null), 200);
		return id;
	}

	private void ensureKnown(String token, String name) throws Exception {
		Set<String> keys = new HashSet<>();
		for (JsonNode instructor : liveInstructors(token)) {
			keys.add(key(instructor.get("name").asText()));
			for (JsonNode alias : instructor.get("aliases")) {
				keys.add(key(alias.asText()));
			}
		}
		if (!keys.contains(key(name))) {
			respond(post("/api/v1/offline-instructors", token, "{\"name\":\"" + name + "\"}"), 201);
		}
	}

	/** Renames any live instructor that answers to the name, so the sample's name is unknown. */
	private void makeUnknown(String token, String name) throws Exception {
		for (JsonNode instructor : liveInstructors(token)) {
			boolean named = key(instructor.get("name").asText()).equals(key(name));
			boolean aliased = false;
			for (JsonNode alias : instructor.get("aliases")) {
				aliased |= key(alias.asText()).equals(key(name));
			}
			if (named || aliased) {
				respond(patch("/api/v1/offline-instructors/" + instructor.get("id").asText(), token,
						"{\"name\":\"" + name + " (earlier " + UUID.randomUUID() + ")\",\"aliases\":[]}"), 200);
			}
		}
	}

	private List<JsonNode> liveInstructors(String token) throws Exception {
		JsonNode all = respondTo(get("/api/v1/offline-instructors").header("Authorization", "Bearer " + token), 200);
		List<JsonNode> found = new ArrayList<>();
		all.forEach(found::add);
		return found;
	}

	private static String key(String name) {
		return name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
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
