package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Bundle orders (plan D1): one payment for several exams of one module, one grant per exam. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, BundleOrderIT.ClockConfig.class, BundleOrderIT.GatewayConfig.class })
class BundleOrderIT extends PostgresIntegrationTest {

	private static final Instant TEN_AM = Instant.parse("2026-10-20T04:00:00Z");

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
	private FakeLiveExamPaymentGateway gateway;

	@Autowired
	private LiveExamOrderRepository orderRepository;

	@Autowired
	private LiveExamOrderTx orderTx;

	private JdbcTemplate jdbc;

	@BeforeEach
	void reset() {
		clock.set(TEN_AM);
		gateway.reset();
		jdbc = new JdbcTemplate(dataSource);
	}

	@Test
	void aTypedReceiptAddressIsKeptOnTheBundleAndAnInvalidOneMakesNoOrder() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		UUID studentId = seed("STUDENT");
		String student = token(studentId);
		UUID module = publishPricedModule(master, "Receipt shop", "1000.00");
		UUID first = publishMcq(master, module, "Receipt first", "100.00", "2026-10-20T12:00:00+06:00");
		UUID second = publishMcq(master, module, "Receipt second", "200.00", "2026-10-20T13:00:00+06:00");

		JsonNode refused = initiate(student, bundleWithReceipt("not-an-email", first, second), 400);
		assertThat(refused.get("fieldErrors").get(0).get("field").asText()).isEqualTo("receiptEmail");
		assertThat(orderCount(studentId)).isZero();

		JsonNode order = initiate(student, bundleWithReceipt("  Buyer@Example.COM ", first, second), 201);
		assertThat(order.get("receiptEmail").asText()).isEqualTo("buyer@example.com");
		UUID orderId = UUID.fromString(order.get("orderId").asText());
		assertThat(jdbc.queryForObject("select receipt_email from live_exam_orders where id = ?", String.class, orderId))
			.isEqualTo("buyer@example.com");
		assertThat(order(student, orderId).get("receiptEmail").asText()).isEqualTo("buyer@example.com");

		// The same open bundle takes the address now sent, and a blank one clears it.
		JsonNode again = initiate(student, bundleWithReceipt("", first, second), 200);
		assertThat(again.get("orderId").asText()).isEqualTo(orderId.toString());
		// A null field is left out of the JSON, so the cleared address is an absent one.
		assertThat(again.hasNonNull("receiptEmail")).isFalse();
		assertThat(jdbc.queryForObject("select receipt_email from live_exam_orders where id = ?", String.class, orderId)).isNull();
		assertThat(orderCount(studentId)).isEqualTo(1);
	}

	@Test
	void threeExamsMakeOneOrderAndAValidIpnGrantsEachOnce() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		UUID studentId = seed("STUDENT");
		String student = token(studentId);
		UUID module = publishPricedModule(master, "Bundle shop", "1000.00");
		UUID first = publishMcq(master, module, "First", "100.00", "2026-10-20T12:00:00+06:00");
		UUID second = publishMcq(master, module, "Second", "200.00", "2026-10-20T13:00:00+06:00");
		UUID third = publishMcq(master, module, "Third", "300.50", "2026-10-20T14:00:00+06:00");
		UUID outside = publishMcq(master, module, "Not bought", "50.00", "2026-10-20T15:00:00+06:00");

		JsonNode order = initiate(student, bundle(third, first, second), 201);
		assertThat(order.get("scope").asText()).isEqualTo("BUNDLE");
		assertThat(order.get("moduleId").asText()).isEqualTo(module.toString());
		assertThat(order.hasNonNull("examId")).isFalse();
		assertThat(ids(order.get("examIds"))).containsExactly(first, second, third);
		assertThat(money(order.get("amount"))).isEqualByComparingTo("600.50");
		assertThat(order.get("status").asText()).isEqualTo("INITIATED");
		assertThat(order.get("redirectUrl").asText()).startsWith("https://gateway.test/");
		UUID orderId = UUID.fromString(order.get("orderId").asText());
		String tranId = order.get("tranId").asText();
		assertThat(orderCount(studentId)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from live_exam_order_exams where order_id = ?", Integer.class,
				orderId)).isEqualTo(3);
		assertThat(jdbc.queryForMap("select scope, exam_id, currency, method, status from live_exam_orders where id = ?",
				orderId))
			.containsEntry("scope", "BUNDLE")
			.containsEntry("exam_id", null)
			.containsEntry("currency", "BDT")
			.containsEntry("method", "SSLCOMMERZ")
			.containsEntry("status", "INITIATED");

		ipn(tranId);
		assertThat(storedStatus(orderId)).isEqualTo("SUCCEEDED");
		assertThat(bundleGrants(studentId, orderId)).containsExactlyInAnyOrder(first, second, third);
		assertThat(grantsFor(studentId)).isEqualTo(3);
		assertThat(noticeCount("PURCHASE_GRANTED", studentId)).isEqualTo(3);
		assertThat(mailCount(studentId, "PAYMENT_RECEIPT")).isEqualTo(1);
		assertThat(examTimeMail(studentId)).containsExactlyInAnyOrder(first, second, third).doesNotContain(outside);

		ipn(tranId);
		assertThat(grantsFor(studentId)).isEqualTo(3);
		assertThat(noticeCount("PURCHASE_GRANTED", studentId)).isEqualTo(3);
		assertThat(mailCount(studentId, "PAYMENT_RECEIPT")).isEqualTo(1);

		JsonNode receipt = order(student, orderId);
		assertThat(receipt.get("scope").asText()).isEqualTo("BUNDLE");
		assertThat(ids(receipt.get("examIds"))).containsExactly(first, second, third);
		mockMvc.perform(get("/api/v1/live-exam-orders/status").header("Authorization", "Bearer " + student)
			.param("tranId", tranId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PAID"))
			.andExpect(jsonPath("$.examName").value("Bundle shop"));
		mockMvc.perform(get("/api/v1/me/live-exam-orders").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].scope").value("BUNDLE"))
			.andExpect(jsonPath("$.content[0].examId").doesNotExist())
			.andExpect(jsonPath("$.content[0].moduleTitleEn").value("Bundle shop"))
			.andExpect(jsonPath("$.content[0].status").value("PAID"));
		mockMvc.perform(get("/api/v1/live-exam-orders").header("Authorization", "Bearer " + master).param("tranId", tranId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].scope").value("BUNDLE"))
			.andExpect(jsonPath("$.content[0].examId").doesNotExist())
			.andExpect(jsonPath("$.content[0].moduleTitleEn").value("Bundle shop"))
			.andExpect(jsonPath("$.content[0].accessGranted").value(true));

		// The exams are owned now, so the next bundle with any of them is refused.
		initiate(student, bundle(outside, first), 409);
	}

	@Test
	void anOwnedExamRefusesTheWholeBundle() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		UUID studentId = seed("STUDENT");
		String student = token(studentId);
		UUID module = publishPricedModule(master, "Owned", "1000.00");
		UUID owned = publishMcq(master, module, "Owned paper", "100.00", "2026-10-20T12:00:00+06:00");
		UUID other = publishMcq(master, module, "Other paper", "200.00", "2026-10-20T13:00:00+06:00");
		JsonNode single = initiate(student, "{\"examId\":\"" + owned + "\"}", 201);
		ipn(single.get("tranId").asText());

		mockMvc.perform(post("/api/v1/live-exam-orders").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content(bundle(other, owned)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ALREADY_PURCHASED"));
		assertThat(orderCount(studentId)).isEqualTo(1);

		UUID rostered = seed("STUDENT");
		jdbc.update("insert into live_exam_roster (module_id, student_id, created_at) values (?, ?, now())", module,
				rostered);
		mockMvc.perform(post("/api/v1/live-exam-orders").header("Authorization", "Bearer " + token(rostered))
			.contentType(MediaType.APPLICATION_JSON)
			.content(bundle(other)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ALREADY_PURCHASED"));
		assertThat(orderCount(rostered)).isZero();
	}

	@Test
	void aMalformedBundleIsRefused() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		UUID studentId = seed("STUDENT");
		String student = token(studentId);
		UUID module = publishPricedModule(master, "Shape", "1000.00");
		UUID otherModule = publishPricedModule(master, "Shape two", "1000.00");
		UUID exam = publishMcq(master, module, "Shape paper", "100.00", "2026-10-20T12:00:00+06:00");
		UUID foreign = publishMcq(master, otherModule, "Foreign paper", "100.00", "2026-10-20T13:00:00+06:00");
		List<UUID> many = new ArrayList<>();
		for (int i = 0; i < 21; i++) {
			many.add(UUID.randomUUID());
		}

		for (String body : List.of(bundle(exam, foreign), "{\"examIds\":[]}", bundle(exam, exam),
				bundle(many.toArray(UUID[]::new)), "{\"examIds\":\"" + exam + "\"}", "{\"examIds\":[\"not-a-uuid\"]}",
				"{\"examIds\":[{\"id\":\"" + exam + "\"}]}",
				"{\"moduleId\":\"" + module + "\",\"examIds\":[\"" + exam + "\"]}",
				"{\"examId\":\"" + exam + "\",\"examIds\":[\"" + exam + "\"]}")) {
			mockMvc.perform(post("/api/v1/live-exam-orders").header("Authorization", "Bearer " + student)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		}
		assertThat(orderCount(studentId)).isZero();
	}

	@Test
	void anUnpublishedExamIsNotFoundAndAMissingPriceIsRequired() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		UUID studentId = seed("STUDENT");
		String student = token(studentId);
		UUID module = publishPricedModule(master, "Rules", "1000.00");
		UUID exam = publishMcq(master, module, "Rules paper", "100.00", "2026-10-20T12:00:00+06:00");
		UUID draft = createExam(master, module, "Draft", "2026-10-20T13:00:00+06:00", "100.00");
		UUID unpriced = publishMcq(master, module, "Module only", null, "2026-10-20T14:00:00+06:00");

		mockMvc.perform(post("/api/v1/live-exam-orders").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content(bundle(exam, draft)))
			.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/live-exam-orders").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content(bundle(exam, UUID.randomUUID())))
			.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/live-exam-orders").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content(bundle(exam, unpriced)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PRICE_REQUIRED"));
		assertThat(orderCount(studentId)).isZero();
	}

	@Test
	void theSameSetReusesTheOpenOrderAndAnotherSetMakesANewOne() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		UUID studentId = seed("STUDENT");
		String student = token(studentId);
		UUID module = publishPricedModule(master, "Reuse", "1000.00");
		UUID first = publishMcq(master, module, "Reuse one", "100.00", "2026-10-20T12:00:00+06:00");
		UUID second = publishMcq(master, module, "Reuse two", "200.00", "2026-10-20T13:00:00+06:00");
		UUID third = publishMcq(master, module, "Reuse three", "300.00", "2026-10-20T14:00:00+06:00");

		JsonNode opened = initiate(student, bundle(first, second), 201);
		JsonNode again = initiate(student, bundle(second, first), 200);
		assertThat(again.get("orderId").asText()).isEqualTo(opened.get("orderId").asText());
		assertThat(again.get("tranId").asText()).isEqualTo(opened.get("tranId").asText());
		assertThat(again.get("redirectUrl").asText()).startsWith("https://gateway.test/");

		JsonNode larger = initiate(student, bundle(first, second, third), 201);
		assertThat(larger.get("orderId").asText()).isNotEqualTo(opened.get("orderId").asText());
		JsonNode one = initiate(student, bundle(first), 201);
		assertThat(ids(one.get("examIds"))).containsExactly(first);
		assertThat(orderCount(studentId)).isEqualTo(3);

		// A cancelled bundle is no longer open, so the same set starts a new order.
		mockMvc.perform(post("/api/v1/live-exam-orders/" + opened.get("orderId").asText() + "/cancel")
			.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk());
		JsonNode fresh = initiate(student, bundle(first, second), 201);
		assertThat(fresh.get("tranId").asText()).isNotEqualTo(opened.get("tranId").asText());
	}

	@Test
	void aFailedVerificationGrantsNothing() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		UUID studentId = seed("STUDENT");
		String student = token(studentId);
		UUID module = publishPricedModule(master, "Failing", "1000.00");
		UUID first = publishMcq(master, module, "Failing one", "100.00", "2026-10-20T12:00:00+06:00");
		UUID second = publishMcq(master, module, "Failing two", "200.00", "2026-10-20T13:00:00+06:00");

		JsonNode order = initiate(student, bundle(first, second), 201);
		gateway.mode(FakeLiveExamPaymentGateway.Mode.INVALID);
		ipn(order.get("tranId").asText());
		assertThat(storedStatus(UUID.fromString(order.get("orderId").asText()))).isEqualTo("FAILED");
		assertThat(grantsFor(studentId)).isZero();
		assertThat(noticeCount("PURCHASE_GRANTED", studentId)).isZero();
	}

	@Test
	void theMasterAdminConfirmsOneBundleAndRejectsAnother() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		UUID studentId = seed("STUDENT");
		String student = token(studentId);
		UUID module = publishPricedModule(master, "Decide", "1000.00");
		UUID first = publishMcq(master, module, "Decide one", "100.00", "2026-10-20T12:00:00+06:00");
		UUID second = publishMcq(master, module, "Decide two", "200.00", "2026-10-20T13:00:00+06:00");

		JsonNode confirmed = initiate(student, bundle(first, second), 201);
		UUID confirmedId = UUID.fromString(confirmed.get("orderId").asText());
		// A grant made by hand after checkout started: confirm completes the rest without a second grant.
		jdbc.update("""
				insert into live_exam_grants (id, student_id, scope, module_id, exam_id, order_id, source, reason, created_at)
				values (?, ?, 'EXAM', ?, ?, null, 'MANUAL', 'Hand', now())
				""", UUID.randomUUID(), studentId, module, second);
		mockMvc.perform(post("/api/v1/live-exam-orders/" + confirmedId + "/confirm").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\":\"Checked at SSLCommerz\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SUCCEEDED"))
			.andExpect(jsonPath("$.scope").value("BUNDLE"))
			.andExpect(jsonPath("$.examIds.length()").value(2));
		assertThat(bundleGrants(studentId, confirmedId)).containsExactly(first);
		assertThat(grantsFor(studentId)).isEqualTo(2);
		assertThat(noticeCount("PURCHASE_GRANTED", studentId)).isEqualTo(1);

		UUID otherId = seed("STUDENT");
		String other = token(otherId);
		JsonNode rejected = initiate(other, bundle(first, second), 201);
		UUID rejectedId = UUID.fromString(rejected.get("orderId").asText());
		mockMvc.perform(post("/api/v1/live-exam-orders/" + rejectedId + "/reject").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\":\"No payment at SSLCommerz\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("FAILED"));
		assertThat(grantsFor(otherId)).isZero();
		assertThat(noticeCount("PURCHASE_GRANTED", otherId)).isZero();
	}

	@Test
	void theRecheckJobGrantsAPaidBundle() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		UUID studentId = seed("STUDENT");
		String student = token(studentId);
		UUID module = publishPricedModule(master, "Recheck", "1000.00");
		UUID first = publishMcq(master, module, "Recheck one", "100.00", "2026-10-20T12:00:00+06:00");
		UUID second = publishMcq(master, module, "Recheck two", "200.00", "2026-10-20T13:00:00+06:00");
		JsonNode order = initiate(student, bundle(first, second), 201);
		UUID orderId = UUID.fromString(order.get("orderId").asText());

		// Only this order is offered to the job, so other classes' open orders in the shared database stay untouched.
		LiveExamOrderRepository only = mock(LiveExamOrderRepository.class);
		when(only.findByStatusAndCreatedAtBetween(eq(LiveExamOrder.INITIATED), any(), any()))
			.thenAnswer(invocation -> orderRepository.findById(orderId)
				.filter(found -> LiveExamOrder.INITIATED.equals(found.getStatus()))
				.map(List::of)
				.orElse(List.of()));
		gateway.lookup(LiveExamPaymentGateway.Lookup.Kind.PAID);
		LiveExamPaymentRecheck recheck = new LiveExamPaymentRecheck(only, orderTx, gateway, clock);
		recheck.sweep(TEN_AM.plus(Duration.ofMinutes(30)));
		assertThat(storedStatus(orderId)).isEqualTo("SUCCEEDED");
		assertThat(bundleGrants(studentId, orderId)).containsExactlyInAnyOrder(first, second);

		recheck.sweep(TEN_AM.plus(Duration.ofMinutes(40)));
		assertThat(gateway.lookups()).isEqualTo(1);
		assertThat(grantsFor(studentId)).isEqualTo(2);
	}

	private static String bundleWithReceipt(String receiptEmail, UUID... examIds) {
		String base = bundle(examIds);
		return base.substring(0, base.length() - 1) + ",\"receiptEmail\":\"" + receiptEmail + "\"}";
	}

	private static String bundle(UUID... examIds) {
		StringBuilder body = new StringBuilder("{\"examIds\":[");
		for (int i = 0; i < examIds.length; i++) {
			body.append(i == 0 ? "" : ",").append('"').append(examIds[i]).append('"');
		}
		return body.append("]}").toString();
	}

	private JsonNode initiate(String student, String body, int status) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/live-exam-orders").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body))
			.andExpect(status().is(status))
			.andReturn();
		return json.readTree(result.getResponse().getContentAsString());
	}

	private void ipn(String tranId) throws Exception {
		mockMvc.perform(post("/api/v1/live-exam-orders/" + tranId + "/ipn")
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.param("status", "VALID")
			.param("tran_id", tranId)
			.param("val_id", "val-1"))
			.andExpect(status().isOk());
	}

	private JsonNode order(String student, UUID orderId) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/live-exam-orders/" + orderId)
			.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andReturn();
		return json.readTree(result.getResponse().getContentAsString());
	}

	private List<UUID> bundleGrants(UUID studentId, UUID orderId) {
		return jdbc.queryForList("""
				select exam_id from live_exam_grants
				where student_id = ? and order_id = ? and scope = 'EXAM' and source = 'BUNDLE' and revoked_at is null
				""", UUID.class, studentId, orderId);
	}

	private int grantsFor(UUID studentId) {
		return jdbc.queryForObject("select count(*) from live_exam_grants where student_id = ?", Integer.class, studentId);
	}

	private int orderCount(UUID studentId) {
		return jdbc.queryForObject("select count(*) from live_exam_orders where student_id = ?", Integer.class, studentId);
	}

	private String storedStatus(UUID orderId) {
		return jdbc.queryForObject("select status from live_exam_orders where id = ?", String.class, orderId);
	}

	private int noticeCount(String kind, UUID studentId) {
		return jdbc.queryForObject("select count(*) from student_notices where kind = ? and user_id = ?", Integer.class,
				kind, studentId);
	}

	private int mailCount(UUID userId, String kind) {
		return jdbc.queryForObject("select count(*) from exam_outbound_mail where user_id = ? and kind = ?", Integer.class,
				userId, kind);
	}

	/** The exams named in the student's exam-time mail keys. */
	private List<UUID> examTimeMail(UUID studentId) {
		List<String> keys = jdbc.queryForList(
				"select message_key from exam_outbound_mail where user_id = ? and kind = 'EXAM_TIME'", String.class, studentId);
		List<UUID> exams = new ArrayList<>();
		for (String key : keys) {
			exams.add(UUID.fromString(key.split("/")[1]));
		}
		return exams;
	}

	private static List<UUID> ids(JsonNode array) {
		List<UUID> ids = new ArrayList<>();
		for (JsonNode node : array) {
			ids.add(UUID.fromString(node.asText()));
		}
		return ids;
	}

	private static BigDecimal money(JsonNode node) {
		return new BigDecimal(node.asText());
	}

	private UUID publishPricedModule(String token, String title, String price) throws Exception {
		UUID category = createCategory(token, title);
		MvcResult created = mockMvc.perform(post("/api/v1/live-exam-modules").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"categoryId":"%s","titleEn":"%s","titleBn":"মডিউল","descriptionEn":"d","descriptionBn":"বি","price":%s}
					""".formatted(category, title, price)))
			.andExpect(status().isCreated())
			.andReturn();
		UUID module = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/final-save").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/publish").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		return module;
	}

	private UUID createCategory(String token, String title) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/live-exam-categories").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titleEn":"%s","titleBn":"বিভাগ"}
					""".formatted(title)))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private UUID publishMcq(String token, UUID module, String title, String price, String liveAt) throws Exception {
		UUID exam = createExam(token, module, title, liveAt, price);
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/questions").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"stem":"Stem","marks":1,"options":[{"text":"Yes","correct":true},{"text":"No","correct":false}]}
					"""))
			.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/final-save").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		return exam;
	}

	private UUID createExam(String token, UUID moduleId, String titleEn, String liveAt, String price) throws Exception {
		String priceField = price == null ? "" : ",\"price\":" + price;
		MvcResult result = mockMvc
			.perform(post("/api/v1/live-exam-modules/" + moduleId + "/exams").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "examType":"MCQ",
						  "paperLanguage":"BN",
						  "titleEn":"%s",
						  "titleBn":"পরীক্ষা",
						  "descriptionEn":"English description",
						  "descriptionBn":"বাংলা বিবরণ",
						  "rulesEn":"English rules",
						  "rulesBn":"বাংলা নিয়ম",
						  "durationMinutes":40,
						  "passPercent":50,
						  "liveAt":"%s"%s
						}
						""".formatted(titleEn, liveAt, priceField)))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private UUID seed(String role) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, uniquePhone(), "bundle-order-" + id + "@example.com", passwords.encode("password1"), role);
		if ("STUDENT".equals(role)) {
			jdbc.update("INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, 'Seed', ?)", id,
					"S" + id.toString().substring(0, 8));
			// A verified account email, so the receipt goes to it when no address is typed (Phase 4B).
			jdbc.update("update users set email_verified_at = now() where id = ?", id);
		}
		else {
			jdbc.update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, 'Admin')", id);
		}
		return id;
	}

	private String token(UUID userId) throws Exception {
		String phone = jdbc.queryForObject("select phone from users where id = ?", String.class, userId);
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

	@TestConfiguration
	static class ClockConfig {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(TEN_AM);
		}

	}

	@TestConfiguration
	static class GatewayConfig {

		@Bean
		@Primary
		FakeLiveExamPaymentGateway fakeGateway() {
			return new FakeLiveExamPaymentGateway();
		}

	}

}
