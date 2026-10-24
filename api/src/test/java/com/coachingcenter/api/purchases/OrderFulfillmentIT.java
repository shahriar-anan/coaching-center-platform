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
class OrderFulfillmentIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	private String master;

	private String system;

	private String student;

	private UUID bookId;

	@BeforeEach
	void seed() throws Exception {
		master = token(seed("MASTER_ADMIN"));
		system = token(seed("SYSTEM_ADMIN"));
		bookId = createBook();
		String phone = uniquePhone();
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"Buyer","phone":"%s","email":"%s","password":"password1"}
					""".formatted(phone, uniqueEmail())))
			.andExpect(status().isCreated());
		student = tokenFromPhone(phone);
	}

	@Test
	void orderFulfillmentFlow() throws Exception {
		UUID deliveryOrder = placeOrder("DELIVERY", "trx-del-" + UUID.randomUUID());
		UUID deliveryPayment = paymentForOrder(deliveryOrder);
		mockMvc.perform(post("/api/v1/payments/" + deliveryPayment + "/verify").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"result\":\"SUCCESS\"}"))
			.andExpect(status().isOk());

		mockMvc.perform(patch("/api/v1/book-orders/" + deliveryOrder).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"READY\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("READY"));

		mockMvc.perform(patch("/api/v1/book-orders/" + deliveryOrder).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"DELIVERED\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DELIVERED"));

		UUID pickupOrder = placeOrder("PICKUP", "trx-pick-" + UUID.randomUUID());
		UUID pickupPayment = paymentForOrder(pickupOrder);
		mockMvc.perform(post("/api/v1/payments/" + pickupPayment + "/verify").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"result\":\"SUCCESS\"}"))
			.andExpect(status().isOk());
		mockMvc.perform(patch("/api/v1/book-orders/" + pickupOrder).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"READY\"}"))
			.andExpect(status().isOk());

		mockMvc.perform(patch("/api/v1/book-orders/" + pickupOrder).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"DELIVERED\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("FULFILLMENT_MISMATCH"));

		mockMvc.perform(patch("/api/v1/book-orders/" + pickupOrder).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"COLLECTED\"}"))
			.andExpect(status().isOk());

		UUID cashOrder = placeCashOrder();
		mockMvc.perform(patch("/api/v1/book-orders/" + cashOrder).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"READY\"}"))
			.andExpect(status().isOk());

		mockMvc.perform(patch("/api/v1/book-orders/" + cashOrder).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"COLLECTED\"}"))
			.andExpect(status().isForbidden());

		mockMvc.perform(patch("/api/v1/book-orders/" + cashOrder).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"COLLECTED\"}"))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/book-orders").header("Authorization", "Bearer " + system))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].orderId").exists())
			.andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)));

		mockMvc.perform(get("/api/v1/book-orders").header("Authorization", "Bearer " + student))
			.andExpect(status().isForbidden());

		new JdbcTemplate(dataSource).update("update book_orders set deleted_at = now() where id = ?", cashOrder);
		String listed = mockMvc.perform(get("/api/v1/book-orders").header("Authorization", "Bearer " + system))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(listed).doesNotContain(cashOrder.toString());

		Integer cashAudit = new JdbcTemplate(dataSource).queryForObject(
				"select count(*) from audit_log where action = 'CASH_RECEIVED' and entity_id = ?", Integer.class,
				cashOrder);
		assertThat(cashAudit).isEqualTo(1);

		String docs = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse()
			.getContentAsString();
		assertThat(docs).doesNotContain("CANCELLED");
		assertBookPathsHaveNoCancel(docs);
	}

	private void assertBookPathsHaveNoCancel(String docs) {
		tools.jackson.databind.JsonNode paths = json.readTree(docs).get("paths");
		assertThat(paths.has("/api/v1/live-exam-orders/{id}/cancel")).isTrue();
		paths.properties().forEach(path -> {
			String name = path.getKey();
			if (name.startsWith("/api/v1/book") || "/api/v1/me/orders".equals(name)) {
				assertThat(name).doesNotContain("cancel");
				assertThat(path.getValue().toString()).doesNotContain("cancel");
				assertThat(path.getValue().toString()).doesNotContain("CANCELLED");
			}
		});
	}

	private UUID placeOrder(String fulfillment, String trx) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/books/" + bookId + "/orders").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{
					  "fulfillment":"%s",
					  "paymentMethod":"ONLINE",
					  "phone":"+8801711111111",
					  "address":"Addr",
					  "method":"BKASH",
					  "transactionRef":"%s"
					}
					""".formatted(fulfillment, trx)))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("orderId").asText());
	}

	private UUID placeCashOrder() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/books/" + bookId + "/orders").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{
					  "fulfillment":"PICKUP",
					  "paymentMethod":"CASH",
					  "phone":"+8801711111111"
					}
					"""))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("orderId").asText());
	}

	private UUID paymentForOrder(UUID orderId) throws Exception {
		return new JdbcTemplate(dataSource).queryForObject(
				"select id from payments where payable_type = 'BOOK_ORDER' and payable_id = ?", UUID.class, orderId);
	}

	private UUID createBook() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/books").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titleEn":"FulfillBook","titleBn":"বই","author":"A","description":"d"}
					"""))
			.andExpect(status().isCreated())
			.andReturn();
		UUID id = UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
		mockMvc.perform(patch("/api/v1/books/" + id).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"price\":50,\"available\":true}"))
			.andExpect(status().isOk());
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
		return tokenFromPhone(new JdbcTemplate(dataSource).queryForObject("select phone from users where id = ?",
				String.class, userId));
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
		return "fulfill-" + UUID.randomUUID() + "@example.com";
	}

}
