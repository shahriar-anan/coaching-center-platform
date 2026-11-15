package com.coachingcenter.api.offline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
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
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The batch catalogue (public-admission plan sections 3 and 4, V93): the description, the switch and the order of a batch, its photo,
 * its default exams and packages, and the public cards, batch page, photo and student routine. Every test makes its own batches with
 * random names, so the class can run again on the same database. The public list is checked by the position of this class's batches.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, OfflineBatchCatalogueIT.ClockConfig.class })
@TestPropertySource(properties = "app.offline-admission.enabled=true")
class OfflineBatchCatalogueIT extends PostgresIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-11-13T04:00:00Z");

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
	void theCardsShowOpenAndClosedBatchesInDisplayOrderAndNeverAnArchivedOne() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID later = batch(master, "Later " + unique(), 2, true);
		UUID first = batch(master, "First " + unique(), 1, false);
		UUID archived = batch(master, "Archived " + unique(), 0, true);
		respond(post("/api/v1/offline-batches/" + archived + "/archive", master, null), 200);

		JsonNode cards = json.readTree(get("/api/v1/public/offline-batches").andExpect(status().isOk()).andReturn()
			.getResponse().getContentAsString());
		List<String> ids = new java.util.ArrayList<>();
		cards.forEach(card -> ids.add(card.get("id").asText()));
		assertThat(ids).contains(first.toString(), later.toString()).doesNotContain(archived.toString());
		assertThat(ids.indexOf(first.toString())).isLessThan(ids.indexOf(later.toString()));

		JsonNode closed = card(cards, first);
		assertThat(closed.get("admissionsOpen").asBoolean()).isFalse();
		assertThat(closed.get("courseFee").decimalValue().compareTo(new java.math.BigDecimal("3000"))).isZero();
		assertThat(closed.get("installments").asInt()).isBetween(1, 24);
		assertThat(closed.get("descriptionEn").asText()).isEqualTo("Evening classes, in person.");
		assertThat(closed.hasNonNull("photoUrl")).isFalse();
		assertThat(closed.get("routineAvailable").asBoolean()).isFalse();

		// The batch page of an archived batch answers 404, the same as a missing one.
		get("/api/v1/public/offline-batches/" + archived).andExpect(status().isNotFound());
		get("/api/v1/public/offline-batches/" + UUID.randomUUID()).andExpect(status().isNotFound());
		JsonNode page = json.readTree(get("/api/v1/public/offline-batches/" + later).andExpect(status().isOk()).andReturn()
			.getResponse().getContentAsString());
		assertThat(page.get("nameEn").asText()).startsWith("Later ");
		assertThat(page.has("studentCount")).isFalse();
		assertThat(page.has("items")).isFalse();
	}

	@Test
	void theRoutineIsNotFoundUntilAConfirmedStudentCopyExists() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID batch = batch(master, "Routine " + unique(), 0, true);
		get("/api/v1/public/offline-batches/" + batch + "/routine.pdf").andExpect(status().isNotFound());
		// A routine that is only a preview (not confirmed) is not the live one, so the public copy stays 404.
		jdbc().update("""
				insert into offline_batch_routines (id, batch_id, month, effective_from, original_file, original_name, original_byte_size,
				  student_file, student_byte_size, version, status, legacy_original, uploaded_by, uploaded_at)
				values (?, ?, '2026-12-01', '2026-12-01', null, 'routine.pdf', 10, 'missing-copy', 10, 1, 'PREVIEW', false, ?, now())
				""", UUID.randomUUID(), batch, seed("MASTER_ADMIN", "Uploader"));
		get("/api/v1/public/offline-batches/" + batch + "/routine.pdf").andExpect(status().isNotFound());
		JsonNode card = json.readTree(get("/api/v1/public/offline-batches/" + batch).andExpect(status().isOk()).andReturn()
			.getResponse().getContentAsString());
		assertThat(card.get("routineAvailable").asBoolean()).isFalse();
	}

	@Test
	void masterAdminUploadsReplacesAndRemovesThePhotoAndTheCardShowsIt() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Master");
		String masterToken = token(master);
		UUID batch = batch(masterToken, "Photo " + unique(), 0, true);
		// The photo is part of the batch form, locked while the batch is published (V103).
		respondTo(multipart(HttpMethod.PUT, "/api/v1/offline-batches/" + batch + "/photo")
			.file(new MockMultipartFile("file", "cover.png", "image/png", png()))
			.header("Authorization", "Bearer " + masterToken), 409);
		respond(post("/api/v1/offline-batches/" + batch + "/unpublish", masterToken, null), 200);

		JsonNode photo = respondTo(multipart(HttpMethod.PUT, "/api/v1/offline-batches/" + batch + "/photo")
			.file(new MockMultipartFile("file", "cover.png", "image/png", png()))
			.header("Authorization", "Bearer " + masterToken), 200);
		assertThat(photo.get("photoUploaded").asBoolean()).isTrue();
		assertThat(photo.get("admissionsOpen").asBoolean()).isTrue();
		assertThat(photo.hasNonNull("photoUploadedAt")).isTrue();
		// Staff read the photo on its own route, draft or not: before this route a GET here answered 405 and the photo never showed.
		MvcResult staffPhoto = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/offline-batches/" + batch + "/photo").header("Authorization", "Bearer " + masterToken))
			.andExpect(status().isOk()).andExpect(header().string("Content-Type", "image/jpeg")).andReturn();
		assertThat(staffPhoto.getResponse().getContentAsByteArray().length).isPositive();
		respond(post("/api/v1/offline-batches/" + batch + "/publish", masterToken, null), 200);

		JsonNode card = json.readTree(get("/api/v1/public/offline-batches/" + batch).andExpect(status().isOk()).andReturn()
			.getResponse().getContentAsString());
		String url = card.get("photoUrl").asText();
		assertThat(url).startsWith("/api/v1/public/offline-batches/" + batch + "/photo?v=");
		MvcResult served = get(url).andExpect(status().isOk())
			.andExpect(header().string("Content-Type", "image/jpeg")).andReturn();
		assertThat(served.getResponse().getContentAsByteArray().length).isPositive();

		// A second upload replaces the first; the old file is no longer the batch's.
		respond(post("/api/v1/offline-batches/" + batch + "/unpublish", masterToken, null), 200);
		respondTo(multipart(HttpMethod.PUT, "/api/v1/offline-batches/" + batch + "/photo")
			.file(new MockMultipartFile("file", "again.jpg", "image/jpeg", jpeg()))
			.header("Authorization", "Bearer " + masterToken), 200);
		String replaced = jdbc().queryForObject("select photo_stored_name from offline_batches where id = ?", String.class, batch);
		assertThat(replaced).isNotBlank();

		// A text file is refused, whatever its name says.
		JsonNode refused = respondTo(multipart(HttpMethod.PUT, "/api/v1/offline-batches/" + batch + "/photo")
			.file(new MockMultipartFile("file", "notes.jpg", "image/jpeg", "not a picture".getBytes()))
			.header("Authorization", "Bearer " + masterToken), 400);
		assertThat(refused.get("code").asText()).isEqualTo("VALIDATION_FAILED");

		// A picture over 5 MB is refused before it is read.
		byte[] big = new byte[5 * 1024 * 1024 + 1];
		System.arraycopy(png(), 0, big, 0, Math.min(png().length, big.length));
		respondTo(multipart(HttpMethod.PUT, "/api/v1/offline-batches/" + batch + "/photo")
			.file(new MockMultipartFile("file", "huge.png", "image/png", big))
			.header("Authorization", "Bearer " + masterToken), 413);

		JsonNode removed = respondTo(delete("/api/v1/offline-batches/" + batch + "/photo")
			.header("Authorization", "Bearer " + masterToken), 200);
		assertThat(removed.get("photoUploaded").asBoolean()).isFalse();
		get("/api/v1/public/offline-batches/" + batch + "/photo").andExpect(status().isNotFound());
	}

	@Test
	void staffWhoCannotManageBatchesCannotChangeThePhoto() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID batch = batch(token(seed("MASTER_ADMIN", "Master")), "Refused " + unique(), 0, true);
		respondTo(multipart(HttpMethod.PUT, "/api/v1/offline-batches/" + batch + "/photo")
			.file(new MockMultipartFile("file", "cover.png", "image/png", png()))
			.header("Authorization", "Bearer " + staff), 403);
		respondTo(delete("/api/v1/offline-batches/" + batch + "/photo").header("Authorization", "Bearer " + staff), 403);
	}

	@Test
	void aBatchIsOfflineAndCarriesNoExamsOrPackagesAndTheItemsRouteIsGone() throws Exception {
		// P17: exams and packages are chosen per student at onboarding, never on the batch. The batch view has no items field.
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID batch = batch(master, "No items " + unique(), 0, true);
		JsonNode view = respond(mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/offline-batches/" + batch)
			.header("Authorization", "Bearer " + master)), 200);
		assertThat(view.has("items")).isFalse();
		assertThat(view.get("descriptionEn").asText()).isEqualTo("Evening classes, in person.");

		// The route that replaced the batch defaults is gone, for the Master Admin as for anyone else.
		respond(putJson("/api/v1/offline-batches/" + batch + "/items", master, "{\"items\":[]}"), 404);

		// V95 dropped the table that held the defaults.
		assertThat(jdbc().queryForObject("select to_regclass('offline_batch_items') is null", Boolean.class)).isTrue();
	}

	@Test
	void theOfficeEditsTheDescriptionsSwitchAndOrderAndACleanedDescriptionIsNull() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID batch = batch(master, "Edit " + unique(), 0, true);
		respond(post("/api/v1/offline-batches/" + batch + "/unpublish", master, null), 200);
		JsonNode edited = respond(patch("/api/v1/offline-batches/" + batch, master,
				"{\"descriptionBn\":\"সন্ধ্যার ক্লাস\",\"admissionsOpen\":false,\"displayOrder\":5}"), 200);
		assertThat(edited.get("descriptionBn").asText()).isEqualTo("সন্ধ্যার ক্লাস");
		assertThat(edited.get("descriptionEn").asText()).isEqualTo("Evening classes, in person.");
		assertThat(edited.get("admissionsOpen").asBoolean()).isFalse();
		assertThat(edited.get("displayOrder").asInt()).isEqualTo(5);

		JsonNode cleared = respond(patch("/api/v1/offline-batches/" + batch, master, "{\"descriptionEn\":\"  \"}"), 200);
		assertThat(cleared.hasNonNull("descriptionEn")).isFalse();

		respond(patch("/api/v1/offline-batches/" + batch, master, "{\"admissionsOpen\":\"maybe\"}"), 400);
		respond(patch("/api/v1/offline-batches/" + batch, master, "{\"displayOrder\":-1}"), 400);
		respond(patch("/api/v1/offline-batches/" + batch, master, "{\"descriptionBn\":\"" + "x".repeat(2001) + "\"}"), 400);
	}

	// ---- helpers ------------------------------------------------------------------------------------------------------------

	private JdbcTemplate jdbc() {
		return new JdbcTemplate(dataSource);
	}

	private ResultActions post(String url, String token, String body) throws Exception {
		var request = MockMvcRequestBuilders.post(url).header("Authorization", "Bearer " + token);
		if (body != null) {
			request = request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mockMvc.perform(request);
	}

	private ResultActions patch(String url, String token, String body) throws Exception {
		return mockMvc.perform(MockMvcRequestBuilders.patch(url).header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions get(String url) throws Exception {
		return mockMvc.perform(MockMvcRequestBuilders.get(url));
	}

	private ResultActions putJson(String url, String token, String body) throws Exception {
		return mockMvc.perform(MockMvcRequestBuilders.put(url).header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	/** A request answered with the expected status; returns its JSON body. */
	private JsonNode respond(ResultActions actions, int expected) throws Exception {
		MvcResult result = actions.andExpect(status().is(expected)).andReturn();
		String text = result.getResponse().getContentAsString();
		return text.isBlank() ? json.createObjectNode() : json.readTree(text);
	}

	private JsonNode respondTo(RequestBuilder request, int expected) throws Exception {
		return respond(mockMvc.perform(request), expected);
	}

	private static JsonNode card(JsonNode cards, UUID id) {
		for (JsonNode card : cards) {
			if (card.get("id").asText().equals(id.toString())) {
				return card;
			}
		}
		throw new AssertionError("No card for " + id);
	}

	/** A batch made through the API, with a description and the given order and switch. */
	private UUID batch(String token, String name, int order, boolean open) throws Exception {
		MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/offline-batches")
			.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"nameEn":"%s","nameBn":"ব্যাচ","endsOn":"2027-04-30","courseFee":3000,
					 "descriptionEn":"Evening classes, in person.","admissionsOpen":%s,"displayOrder":%d}
					""".formatted(name, open, order)))
			.andExpect(status().isCreated())
			.andReturn();
		UUID id = UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
		// A batch starts as a draft (V103); the helper publishes it, as the Master Admin does before students apply.
		mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/offline-batches/" + id + "/publish").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		return id;
	}

	private UUID seed(String role, String name) {
		UUID id = UUID.randomUUID();
		jdbc().update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, "+8801" + ThreadLocalRandom.current().nextInt(300000000, 999999999), "batch-cat-" + id + "@example.com",
				passwords.encode("password1"), role);
		jdbc().update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, ?)", id, name);
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

	private static String unique() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	/** A small real PNG, made in memory. */
	private static byte[] png() throws IOException {
		BufferedImage image = new BufferedImage(64, 36, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		return out.toByteArray();
	}

	/** A small real JPEG, made in memory. */
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
