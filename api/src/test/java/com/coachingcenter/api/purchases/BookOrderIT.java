package com.coachingcenter.api.purchases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class BookOrderIT extends PostgresIntegrationTest {

	/** A new value each run: transaction references are unique, and the test database keeps rows between runs. */
	private static final String RUN = UUID.randomUUID().toString().substring(0, 8);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	private String master;

	private String student;

	private UUID studentId;

	private UUID availableBook;

	private UUID hiddenBook;

	@BeforeEach
	void seed() throws Exception {
		master = token(seed("MASTER_ADMIN"));
		availableBook = createBook(true);
		hiddenBook = createBook(false);
		String phone = uniquePhone();
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"Buyer","phone":"%s","email":"%s","password":"password1"}
					""".formatted(phone, uniqueEmail())))
			.andExpect(status().isCreated());
		student = tokenFromPhone(phone);
		studentId = new JdbcTemplate(dataSource).queryForObject("select id from users where phone = ?", UUID.class,
				phone);
	}

	@Test
	void bookOrderFlow() throws Exception {
		mockMvc.perform(post("/api/v1/books/" + availableBook + "/orders").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{
					  "fulfillment":"DELIVERY",
					  "paymentMethod":"ONLINE",
					  "phone":"+8801711111111",
					  "address":"House 1, Road 2",
					  "method":"BKASH",
					  "transactionRef":"book-trx-%s"
					}
					""".formatted(RUN)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("PLACED"))
			.andExpect(jsonPath("$.paymentStatus").value("PENDING"));

		mockMvc.perform(post("/api/v1/books/" + availableBook + "/orders").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{
					  "fulfillment":"DELIVERY",
					  "paymentMethod":"ONLINE",
					  "phone":"+8801711111111",
					  "method":"BKASH",
					  "transactionRef":"book-trx-%s"
					}
					""".formatted(RUN)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("ADDRESS_REQUIRED"));

		mockMvc.perform(post("/api/v1/books/" + availableBook + "/orders").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{
					  "fulfillment":"PICKUP",
					  "paymentMethod":"CASH",
					  "phone":"+8801711111111"
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.paymentId").doesNotExist());

		Integer cashPayments = new JdbcTemplate(dataSource).queryForObject(
				"select count(*) from payments p join book_orders o on p.payable_id = o.id where o.payment_method = 'CASH'",
				Integer.class);
		assertThat(cashPayments).isZero();

		mockMvc.perform(post("/api/v1/books/" + availableBook + "/orders").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{
					  "fulfillment":"DELIVERY",
					  "paymentMethod":"CASH",
					  "phone":"+8801711111111",
					  "address":"Somewhere"
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("CASH_REQUIRES_PICKUP"));

		mockMvc.perform(post("/api/v1/books/" + hiddenBook + "/orders").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{
					  "fulfillment":"PICKUP",
					  "paymentMethod":"CASH",
					  "phone":"+8801711111111"
					}
					"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("BOOK_UNAVAILABLE"));

		mockMvc.perform(get("/api/v1/me/orders").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2));

		String docs = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse()
			.getContentAsString();
		tools.jackson.databind.JsonNode paths = json.readTree(docs).get("paths");
		assertThat(paths.has("/api/v1/live-exam-orders/{id}/cancel")).isTrue();
		paths.properties().forEach(path -> {
			String name = path.getKey();
			if (name.startsWith("/api/v1/book") || "/api/v1/me/orders".equals(name)) {
				assertThat(name).doesNotContain("cancel");
				assertThat(path.getValue().toString()).doesNotContain("cancel");
				assertThat(path.getValue().toString()).doesNotContain("Cancel");
			}
		});
	}

	private UUID createBook(boolean available) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/books").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{
					  "titleEn":"Book-%s",
					  "titleBn":"বই",
					  "author":"Author",
					  "description":"d"
					}
					""".formatted(UUID.randomUUID().toString().substring(0, 8))))
			.andExpect(status().isCreated())
			.andReturn();
		UUID id = UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
		mockMvc.perform(patch("/api/v1/books/" + id).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"price\":80}"))
			.andExpect(status().isOk());
		if (available) {
			mockMvc.perform(patch("/api/v1/books/" + id).header("Authorization", "Bearer " + master)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"available\":true}"))
				.andExpect(status().isOk());
		}
		return id;
	}

	private UUID seed(String role) {
		UUID id = UUID.randomUUID();
		String phone = uniquePhone();
		new JdbcTemplate(dataSource).update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, phone, uniqueEmail(), passwords.encode("password1"), role);
		new JdbcTemplate(dataSource).update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, 'Admin')", id);
		return id;
	}

	private String token(UUID userId) throws Exception {
		String phone = new JdbcTemplate(dataSource).queryForObject("select phone from users where id = ?", String.class,
				userId);
		return tokenFromPhone(phone);
	}

	private String tokenFromPhone(String phone) throws Exception {
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
		return "order-" + UUID.randomUUID() + "@example.com";
	}

}
