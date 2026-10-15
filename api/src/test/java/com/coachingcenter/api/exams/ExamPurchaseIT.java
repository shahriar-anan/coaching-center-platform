package com.coachingcenter.api.exams;

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
class ExamPurchaseIT extends PostgresIntegrationTest {

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

	private String otherStudent;

	private UUID paidModule;

	private UUID openModule;

	@BeforeEach
	void seed() throws Exception {
		master = token(seed("MASTER_ADMIN"));
		system = token(seed("SYSTEM_ADMIN"));
		student = registerStudent();
		otherStudent = registerStudent();
		paidModule = publishPaidModule(400);
		openModule = publishOpenModule();
	}

	@Test
	void purchaseAndVerify() throws Exception {
		assertThat(columns("exam_module_purchases")).doesNotContain("expires_at");
		mockMvc.perform(get("/api/v1/me/purchases").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isEmpty());

		mockMvc.perform(get("/api/v1/catalog/exam-modules/" + paidModule).header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessGranted").value(false));

		String trx = "exam-trx-" + UUID.randomUUID();
		MvcResult purchase = mockMvc
			.perform(post("/api/v1/exam-modules/" + paidModule + "/purchases").header("Authorization", "Bearer " + student)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"method":"BKASH","transactionRef":"%s"}
						""".formatted(trx)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.purchaseStatus").value("AWAITING_PAYMENT"))
			.andExpect(jsonPath("$.paymentStatus").value("PENDING"))
			.andExpect(jsonPath("$.amount").value(400))
			.andExpect(jsonPath("$.paymentNumber").isNotEmpty())
			.andReturn();
		UUID paymentId = UUID.fromString(json.readTree(purchase.getResponse().getContentAsString()).get("paymentId").asText());
		UUID purchaseId = UUID.fromString(json.readTree(purchase.getResponse().getContentAsString()).get("purchaseId").asText());

		assertThat(new JdbcTemplate(dataSource).queryForObject(
				"select payable_type from payments where id = ?", String.class, paymentId)).isEqualTo("EXAM_MODULE");
		assertThat(new JdbcTemplate(dataSource).queryForObject("select price_paid from exam_module_purchases where id = ?",
				BigDecimal.class, purchaseId)).isEqualByComparingTo("400");

		mockMvc.perform(get("/api/v1/me/exam-module-purchases").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].moduleId").value(paidModule.toString()));
		mockMvc.perform(get("/api/v1/me/exam-module-purchases").header("Authorization", "Bearer " + otherStudent))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isEmpty());

		mockMvc.perform(post("/api/v1/exam-modules/" + openModule + "/purchases").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"method\":\"BKASH\",\"transactionRef\":\"open-" + UUID.randomUUID() + "\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EXAM_NOT_FOR_SALE"));

		mockMvc.perform(post("/api/v1/exam-modules/" + paidModule + "/purchases").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"method":"BKASH","transactionRef":"%s"}
					""".formatted(trx)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DUPLICATE_TRANSACTION_REF"));

		String trx2 = "exam-trx2-" + UUID.randomUUID();
		mockMvc.perform(post("/api/v1/exam-modules/" + paidModule + "/purchases").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"method":"NAGAD","transactionRef":"%s"}
					""".formatted(trx2)))
			.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/verify").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"result\":\"SUCCESS\"}"))
			.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/verify").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"result\":\"SUCCESS\"}"))
			.andExpect(status().isOk());
		assertThat(new JdbcTemplate(dataSource).queryForObject(
				"select count(*) from audit_log where action = 'PAYMENT_SUCCESS' and entity_id = ?", Integer.class,
				paymentId)).isEqualTo(1);
		mockMvc.perform(get("/api/v1/me/purchases").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isEmpty());

		mockMvc.perform(get("/api/v1/catalog/exam-modules/" + paidModule).header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessGranted").value(true));

		new JdbcTemplate(dataSource).update("update exam_module_purchases set started_at = now() - interval '1 year' where id = ?",
				purchaseId);
		mockMvc.perform(get("/api/v1/catalog/exam-modules/" + paidModule).header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessGranted").value(true));

		mockMvc.perform(post("/api/v1/exam-modules/" + paidModule + "/purchases").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"method\":\"BKASH\",\"transactionRef\":\"after-" + UUID.randomUUID() + "\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ALREADY_PURCHASED"));

		mockMvc.perform(get("/api/v1/payments").header("Authorization", "Bearer " + master).param("payableType", "EXAM_MODULE"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].titleEn").isNotEmpty())
			.andExpect(jsonPath("$.content[0].titleBn").isNotEmpty());

		mockMvc.perform(patch("/api/v1/exam-modules/" + paidModule).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"DRAFT\"}"))
			.andExpect(status().isOk());
		String failTrx = "fail-" + UUID.randomUUID();
		mockMvc.perform(post("/api/v1/exam-modules/" + paidModule + "/purchases").header("Authorization",
				"Bearer " + otherStudent)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"method":"BKASH","transactionRef":"%s"}
					""".formatted(failTrx)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EXAM_NOT_FOR_SALE"));

		mockMvc.perform(patch("/api/v1/exam-modules/" + paidModule).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"PUBLISHED\"}"))
			.andExpect(status().isOk());

		UUID verifyModule = publishPaidModule(150);
		String failTrx2 = "fail2-" + UUID.randomUUID();
		MvcResult pendingPay = mockMvc
			.perform(post("/api/v1/exam-modules/" + verifyModule + "/purchases").header("Authorization",
					"Bearer " + otherStudent)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"method":"BKASH","transactionRef":"%s"}
						""".formatted(failTrx2)))
			.andExpect(status().isCreated())
			.andReturn();
		UUID failPaymentId = UUID
			.fromString(json.readTree(pendingPay.getResponse().getContentAsString()).get("paymentId").asText());
		mockMvc.perform(patch("/api/v1/exam-modules/" + verifyModule).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"DRAFT\"}"))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/payments/" + failPaymentId + "/verify").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"result\":\"SUCCESS\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EXAM_NOT_FOR_SALE"));
		assertThat(new JdbcTemplate(dataSource).queryForObject("select status from payments where id = ?", String.class,
				failPaymentId)).isEqualTo("PENDING");

		String failedTrx = "failed-" + UUID.randomUUID();
		UUID draftModule = createDraftPaidModule();
		UUID failSale = publishPaidModule(80);
		MvcResult failedPay = mockMvc
			.perform(post("/api/v1/exam-modules/" + failSale + "/purchases").header("Authorization", "Bearer " + student)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"method\":\"BKASH\",\"transactionRef\":\"failpay-" + UUID.randomUUID() + "\"}"))
			.andExpect(status().isCreated())
			.andReturn();
		UUID failedPaymentId = UUID
			.fromString(json.readTree(failedPay.getResponse().getContentAsString()).get("paymentId").asText());
		UUID failedPurchaseId = UUID
			.fromString(json.readTree(failedPay.getResponse().getContentAsString()).get("purchaseId").asText());
		mockMvc.perform(post("/api/v1/payments/" + failedPaymentId + "/verify").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"result\":\"FAILED\"}"))
			.andExpect(status().isOk());
		assertThat(new JdbcTemplate(dataSource).queryForObject(
				"select status from exam_module_purchases where id = ?", String.class, failedPurchaseId))
			.isEqualTo("PAYMENT_FAILED");
		assertThat(new JdbcTemplate(dataSource).queryForObject(
				"select count(*) from audit_log where action = 'PAYMENT_FAILED' and entity_id = ?", Integer.class,
				failedPaymentId)).isEqualTo(1);
		mockMvc.perform(get("/api/v1/catalog/exam-modules/" + failSale).header("Authorization", "Bearer " + student))
			.andExpect(jsonPath("$.accessGranted").value(false));
		mockMvc.perform(post("/api/v1/payments/" + failedPaymentId + "/verify").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"result\":\"FAILED\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PAYMENT_NOT_PENDING"));

		UUID priced = publishPaidModule(80);
		MvcResult pricedPay = mockMvc
			.perform(post("/api/v1/exam-modules/" + priced + "/purchases").header("Authorization", "Bearer " + otherStudent)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"method\":\"NAGAD\",\"transactionRef\":\"price-" + UUID.randomUUID() + "\"}"))
			.andExpect(status().isCreated())
			.andReturn();
		UUID pricedPurchase = UUID
			.fromString(json.readTree(pricedPay.getResponse().getContentAsString()).get("purchaseId").asText());
		mockMvc.perform(patch("/api/v1/exam-modules/" + priced).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"price\":999}"))
			.andExpect(status().isOk());
		assertThat(new JdbcTemplate(dataSource).queryForObject(
				"select price_paid from exam_module_purchases where id = ?", BigDecimal.class, pricedPurchase))
			.isEqualByComparingTo("80");

		mockMvc.perform(post("/api/v1/exam-modules/" + draftModule + "/purchases").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"method":"BKASH","transactionRef":"%s"}
					""".formatted(failedTrx)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EXAM_NOT_FOR_SALE"));

		UUID enrolled = createModule();
		UUID course = createCourse();
		mockMvc.perform(patch("/api/v1/exam-modules/" + enrolled + "/access").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"accessMode\":\"ENROLLED_ONLY\",\"courseId\":\"" + course + "\"}"))
			.andExpect(status().isOk());
		mockMvc.perform(patch("/api/v1/exam-modules/" + enrolled).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"PUBLISHED\"}"))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/exam-modules/" + enrolled + "/purchases").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"method\":\"BKASH\",\"transactionRef\":\"enrolled-" + UUID.randomUUID() + "\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EXAM_NOT_FOR_SALE"));

		UUID archivedSale = publishPaidModule(70);
		mockMvc.perform(post("/api/v1/exam-modules/" + archivedSale + "/archive").header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		mockMvc.perform(post("/api/v1/exam-modules/" + archivedSale + "/purchases").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"method\":\"BKASH\",\"transactionRef\":\"arch-" + UUID.randomUUID() + "\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EXAM_NOT_FOR_SALE"));

		UUID modeChange = publishPaidModule(90);
		MvcResult modePay = mockMvc
			.perform(post("/api/v1/exam-modules/" + modeChange + "/purchases").header("Authorization", "Bearer " + otherStudent)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"method\":\"NAGAD\",\"transactionRef\":\"mode-" + UUID.randomUUID() + "\"}"))
			.andExpect(status().isCreated())
			.andReturn();
		UUID modePayment = UUID.fromString(json.readTree(modePay.getResponse().getContentAsString()).get("paymentId").asText());
		mockMvc.perform(patch("/api/v1/exam-modules/" + modeChange).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"price\":null}"))
			.andExpect(status().isOk());
		mockMvc.perform(patch("/api/v1/exam-modules/" + modeChange + "/access").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"accessMode\":\"OPEN\",\"courseId\":null}"))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/payments/" + modePayment + "/verify").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"result\":\"SUCCESS\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EXAM_NOT_FOR_SALE"));
		assertThat(new JdbcTemplate(dataSource).queryForObject("select status from payments where id = ?", String.class,
				modePayment)).isEqualTo("PENDING");

		String docs = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse()
			.getContentAsString();
		assertThat(docs).doesNotContain("/exam-modules/{id}/purchases/{purchaseId}");
		assertThat(docs).doesNotContain("/exam-module-purchases/{id}");
	}

	private UUID createCourse() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/courses").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titleEn":"C","titleBn":"ক","contentLanguage":"EN","description":"d","deliveryMode":"ONLINE","accessType":"FULL_FREE"}
					"""))
			.andExpect(status().isCreated())
			.andReturn();
		UUID id = UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
		mockMvc.perform(patch("/api/v1/courses/" + id).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"PUBLISHED\"}"))
			.andExpect(status().isOk());
		return id;
	}

	private UUID publishPaidModule(int price) throws Exception {
		UUID module = createModule();
		mockMvc.perform(patch("/api/v1/exam-modules/" + module).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"price\":" + price + "}"))
			.andExpect(status().isOk());
		mockMvc.perform(patch("/api/v1/exam-modules/" + module + "/access").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"accessMode\":\"PURCHASE_REQUIRED\",\"courseId\":null}"))
			.andExpect(status().isOk());
		mockMvc.perform(patch("/api/v1/exam-modules/" + module).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"PUBLISHED\"}"))
			.andExpect(status().isOk());
		return module;
	}

	private UUID publishOpenModule() throws Exception {
		UUID module = createModule();
		mockMvc.perform(patch("/api/v1/exam-modules/" + module + "/access").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"accessMode\":\"OPEN\",\"courseId\":null}"))
			.andExpect(status().isOk());
		mockMvc.perform(patch("/api/v1/exam-modules/" + module).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"PUBLISHED\"}"))
			.andExpect(status().isOk());
		return module;
	}

	private UUID createDraftPaidModule() throws Exception {
		UUID module = createModule();
		mockMvc.perform(patch("/api/v1/exam-modules/" + module).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"price\":50}"))
			.andExpect(status().isOk());
		mockMvc.perform(patch("/api/v1/exam-modules/" + module + "/access").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"accessMode\":\"PURCHASE_REQUIRED\",\"courseId\":null}"))
			.andExpect(status().isOk());
		return module;
	}

	private UUID createModule() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/exam-modules").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titleEn":"Sale mod","titleBn":"বিক্রি","descriptionEn":"d","descriptionBn":"b"}
					"""))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private java.util.Set<String> columns(String table) {
		return new JdbcTemplate(dataSource).queryForList(
				"select column_name from information_schema.columns where table_schema = 'public' and table_name = ?",
				String.class, table)
			.stream()
			.collect(java.util.stream.Collectors.toSet());
	}

	private String registerStudent() throws Exception {
		String phone = uniquePhone();
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"S","phone":"%s","email":"%s","password":"password1"}
					""".formatted(phone, uniqueEmail())))
			.andExpect(status().isCreated());
		return tokenFromLogin(phone, "password1");
	}

	private UUID seed(String role) {
		UUID id = UUID.randomUUID();
		String phone = uniquePhone();
		new JdbcTemplate(dataSource).update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, phone, uniqueEmail(), passwords.encode("password1"), role);
		if ("SYSTEM_ADMIN".equals(role) || "MASTER_ADMIN".equals(role)) {
			new JdbcTemplate(dataSource).update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, 'Admin')", id);
		}
		return id;
	}

	private String token(UUID userId) throws Exception {
		String phone = new JdbcTemplate(dataSource).queryForObject("select phone from users where id = ?", String.class,
				userId);
		return tokenFromLogin(phone, "password1");
	}

	private String tokenFromLogin(String phone, String password) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"identifier":"%s","password":"%s"}
					""".formatted(phone, password)))
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
		return "exam-buy-" + UUID.randomUUID() + "@example.com";
	}

}
