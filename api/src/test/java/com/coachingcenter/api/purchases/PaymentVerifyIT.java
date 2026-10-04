package com.coachingcenter.api.purchases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class PaymentVerifyIT extends PostgresIntegrationTest {

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

	private String studentB;

	private String studentC;

	private UUID courseId;

	private UUID bookId;

	@BeforeEach
	void seed() throws Exception {
		master = token(seed("MASTER_ADMIN"));
		system = token(seed("SYSTEM_ADMIN"));
		courseId = createCourse();
		bookId = createBook();
		student = registerStudent();
		studentB = registerStudent();
		studentC = registerStudent();
	}

	@Test
	void paymentVerificationRules() throws Exception {
		String courseTrx = "trx-course-" + UUID.randomUUID();
		String bookTrx = "trx-book-" + UUID.randomUUID();
		UUID coursePayment = purchaseCourse(courseTrx);
		UUID bookPayment = placeBookOrder(bookTrx);

		mockMvc.perform(post("/api/v1/payments/" + coursePayment + "/verify").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"result\":\"SUCCESS\"}"))
			.andExpect(status().isForbidden());

		String pendingList = mockMvc.perform(get("/api/v1/payments?status=PENDING").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(pendingList).contains(courseTrx);
		assertThat(pendingList).contains(bookTrx);

		mockMvc.perform(patch("/api/v1/courses/" + courseId).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"price\":250}"))
			.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/payments/" + coursePayment + "/verify").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"result\":\"SUCCESS\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paymentStatus").value("SUCCESS"))
			.andExpect(jsonPath("$.enrollmentStatus").value("ACTIVE"));

		BigDecimal pricePaid = new JdbcTemplate(dataSource).queryForObject(
				"select price_paid from enrollments e join payments p on p.payable_id = e.id where p.id = ?",
				BigDecimal.class, coursePayment);
		assertThat(pricePaid).isEqualByComparingTo("100");

		Integer successAudit = new JdbcTemplate(dataSource).queryForObject(
				"select count(*) from audit_log where action = 'PAYMENT_SUCCESS' and entity_id = ?", Integer.class,
				coursePayment);
		assertThat(successAudit).isEqualTo(1);

		UUID closedPayment = purchaseCourse(studentB, "trx-closed-" + UUID.randomUUID());
		mockMvc.perform(post("/api/v1/courses/" + courseId + "/close").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/payments/" + closedPayment + "/verify").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"result\":\"SUCCESS\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("COURSE_NOT_OPEN"));
		assertThat(new JdbcTemplate(dataSource).queryForObject("select status from payments where id = ?", String.class,
				closedPayment)).isEqualTo("PENDING");
		assertThat(new JdbcTemplate(dataSource).queryForObject(
				"select e.status from enrollments e join payments p on p.payable_id = e.id where p.id = ?", String.class,
				closedPayment)).isEqualTo("AWAITING_PAYMENT");

		mockMvc.perform(post("/api/v1/courses/" + courseId + "/reopen").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());

		UUID failPayment = purchaseCourse(studentC, "trx-fail-" + UUID.randomUUID());
		mockMvc.perform(post("/api/v1/payments/" + failPayment + "/verify").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"result\":\"FAILED\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.enrollmentStatus").value("PAYMENT_FAILED"));
		Integer failedAudit = new JdbcTemplate(dataSource).queryForObject(
				"select count(*) from audit_log where action = 'PAYMENT_FAILED' and entity_id = ?", Integer.class,
				failPayment);
		assertThat(failedAudit).isEqualTo(1);

		mockMvc.perform(post("/api/v1/payments/" + bookPayment + "/verify").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"result\":\"SUCCESS\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.orderStatus").value("PAID"));

		UUID failBookPayment = placeBookOrder(studentC, "trx-book-fail-" + UUID.randomUUID());
		mockMvc.perform(post("/api/v1/payments/" + failBookPayment + "/verify").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"result\":\"FAILED\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.orderStatus").value("PAYMENT_FAILED"));

		mockMvc.perform(post("/api/v1/payments/" + coursePayment + "/verify").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"result\":\"SUCCESS\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PAYMENT_NOT_PENDING"));
	}

	@Test
	void paymentListFiltersByStatusAndType() throws Exception {
		String courseTrx = "trx-filter-course-" + UUID.randomUUID();
		String bookTrx = "trx-filter-book-" + UUID.randomUUID();
		UUID coursePayment = purchaseCourse(courseTrx);
		UUID bookPayment = placeBookOrder(bookTrx);
		mockMvc.perform(post("/api/v1/payments/" + coursePayment + "/verify").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"result\":\"SUCCESS\"}"))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/payments/" + bookPayment + "/verify").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"result\":\"FAILED\"}"))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/payments").param("status", "SUCCESS").param("payableType", "COURSE_ENROLLMENT")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[?(@.transactionRef == '" + courseTrx + "')].titleEn").exists())
			.andExpect(jsonPath("$.content[?(@.transactionRef == '" + courseTrx + "')].studentName").exists());
		String bookFailed = mockMvc
			.perform(get("/api/v1/payments").param("status", "FAILED").param("payableType", "BOOK_ORDER")
				.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(bookFailed).contains(bookTrx);
		assertThat(bookFailed).doesNotContain(courseTrx);

		mockMvc.perform(get("/api/v1/payments").param("status", "CANCELLED").header("Authorization", "Bearer " + master))
			.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/v1/payments").param("payableType", "CATEGORY").header("Authorization", "Bearer " + master))
			.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/v1/payments").header("Authorization", "Bearer " + system))
			.andExpect(status().isForbidden());
	}

	private String registerStudent() throws Exception {
		String phone = uniquePhone();
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"Buyer","phone":"%s","email":"%s","password":"password1"}
					""".formatted(phone, uniqueEmail())))
			.andExpect(status().isCreated());
		return tokenFromPhone(phone);
	}

	private UUID purchaseCourse(String trx) throws Exception {
		return purchaseCourse(student, trx);
	}

	private UUID purchaseCourse(String buyer, String trx) throws Exception {
		MvcResult result = mockMvc
			.perform(post("/api/v1/courses/" + courseId + "/purchases").header("Authorization", "Bearer " + buyer)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"method":"BKASH","transactionRef":"%s"}
						""".formatted(trx)))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("paymentId").asText());
	}

	private UUID placeBookOrder(String trx) throws Exception {
		return placeBookOrder(student, trx);
	}

	private UUID placeBookOrder(String buyer, String trx) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/books/" + bookId + "/orders").header("Authorization", "Bearer " + buyer)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{
					  "fulfillment":"DELIVERY",
					  "paymentMethod":"ONLINE",
					  "phone":"+8801711111111",
					  "address":"Addr",
					  "method":"BKASH",
					  "transactionRef":"%s"
					}
					""".formatted(trx)))
			.andExpect(status().isCreated())
			.andReturn();
		JsonNode body = json.readTree(result.getResponse().getContentAsString());
		return UUID.fromString(body.get("paymentId").asText());
	}

	private UUID createCourse() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/courses").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{
					  "titleEn":"PayCourse",
					  "titleBn":"কোর্স",
					  "contentLanguage":"EN",
					  "description":"d",
					  "deliveryMode":"ONLINE",
					  "accessType":"FREEMIUM"
					}
					"""))
			.andExpect(status().isCreated())
			.andReturn();
		UUID id = UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
		mockMvc.perform(patch("/api/v1/courses/" + id).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"price\":100}"))
			.andExpect(status().isOk());
		mockMvc.perform(patch("/api/v1/courses/" + id).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"PUBLISHED\"}"))
			.andExpect(status().isOk());
		return id;
	}

	private UUID createBook() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/books").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titleEn":"PayBook","titleBn":"বই","author":"A","description":"d"}
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
		if ("STUDENT".equals(role)) {
			new JdbcTemplate(dataSource).update(
					"INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, 'Seed', ?)", id,
					"S" + id.toString().substring(0, 8));
		}
		else {
			new JdbcTemplate(dataSource).update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, 'Admin')", id);
		}
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
		return "pay-" + UUID.randomUUID() + "@example.com";
	}

}
