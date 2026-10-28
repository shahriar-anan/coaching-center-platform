package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, LiveExamPurchaseIT.ClockConfig.class, LiveExamPurchaseIT.GatewayConfig.class })
class LiveExamPurchaseIT extends PostgresIntegrationTest {

	private static final Instant TEN_AM = Instant.parse("2026-10-20T04:00:00Z");

	private static final Instant NOON = Instant.parse("2026-10-20T06:00:00Z");

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
	private ApplicationContext applicationContext;

	@Autowired
	private ObjectProvider<LiveExamPaymentGateway> paymentGateways;

	@Autowired
	private SslCommerzProperties sslCommerz;

	@BeforeEach
	void reset() {
		clock.set(TEN_AM);
		gateway.reset();
	}

	@Test
	void aMasterAdminSetsPricesAndTheCatalogShowsThem() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		String system = token(seed("SYSTEM_ADMIN"));
		UUID module = createModule(master, "Priced", "800.00");
		mockMvc.perform(patch("/api/v1/live-exam-modules/" + module).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"price\":900}"))
			.andExpect(status().isForbidden());
		mockMvc.perform(patch("/api/v1/live-exam-modules/" + module).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"price\":0}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		publishModule(master, module);

		UUID exam = createExam(master, module, "MCQ", "Priced paper", "মূল্য", "2026-10-20T12:00:00+06:00", 40, 50, null,
				"500.00");
		mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"price\":50}"))
			.andExpect(status().isForbidden());
		addQuestion(master, exam);
		finalSaveExam(master, exam);
		publishExam(master, exam);

		mockMvc.perform(get("/api/v1/public/live-exam-modules/" + module))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.price").value(800.0));
		String paper = mockMvc.perform(get("/api/v1/public/live-exams/" + exam))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.price").value(500.0))
			.andExpect(jsonPath("$.questions").doesNotExist())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(paper).doesNotContain("Stem");
		assertThat(money(exam(master, exam).get("price"))).isEqualByComparingTo("500.00");
	}

	@Test
	void aStudentInitiatesAnExamOrderAndAModuleOrder() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		UUID studentId = seed("STUDENT");
		String student = token(studentId);
		UUID module = publishPricedModule(master, "Shop", "1000.00");
		UUID draft = createExam(master, module, "MCQ", "Draft", "খসড়া", "2026-10-20T12:00:00+06:00", 40, 50, null,
				"100.00");
		UUID exam = publishMcq(master, module, "Shop paper", "500.00", "2026-10-20T12:00:00+06:00");
		UUID free = publishMcq(master, module, "No price", null, "2026-10-20T13:00:00+06:00");

		mockMvc.perform(post("/api/v1/live-exam-orders").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/live-exam-orders").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"moduleId\":\"" + module + "\",\"examId\":\"" + exam + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		mockMvc.perform(post("/api/v1/live-exam-orders").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"examId\":\"" + draft + "\"}"))
			.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/live-exam-orders").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"examId\":\"" + free + "\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EXAM_NOT_FOR_SALE"));

		JsonNode first = initiate(student, "{\"examId\":\"" + exam + "\"}", 201);
		JsonNode second = initiate(student, "{\"examId\":\"" + exam + "\"}", 200);
		assertThat(second.get("tranId").asText()).isEqualTo(first.get("tranId").asText());
		assertThat(money(first.get("amount"))).isEqualByComparingTo("500.00");
		assertThat(first.get("status").asText()).isEqualTo("INITIATED");
		assertThat(first.get("redirectUrl").asText()).startsWith("https://gateway.test/");

		JsonNode moduleOrder = initiate(student, "{\"moduleId\":\"" + module + "\"}", 201);
		assertThat(money(moduleOrder.get("amount"))).isEqualByComparingTo("1000.00");
		assertThat(moduleOrder.get("tranId").asText()).isNotEqualTo(first.get("tranId").asText());

		UUID other = seed("STUDENT");
		mockMvc.perform(get("/api/v1/live-exam-orders/" + first.get("orderId").asText())
			.header("Authorization", "Bearer " + token(other)))
			.andExpect(status().isNotFound());
	}

	@Test
	void aVerifiedNoticeGrantsOnceAndAFailureDoesNot() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		UUID studentId = seed("STUDENT");
		String student = token(studentId);
		UUID module = publishPricedModule(master, "Notice", "300.00");
		UUID exam = publishMcq(master, module, "Notice paper", "150.00", "2026-10-20T12:00:00+06:00");

		JsonNode failed = initiate(student, "{\"examId\":\"" + exam + "\"}", 201);
		gateway.mode(FakeLiveExamPaymentGateway.Mode.INVALID);
		notice(failed.get("tranId").asText(), "success");
		assertThat(orderStatus(student, failed)).isEqualTo("FAILED");
		assertThat(grantsFor(studentId)).isZero();

		JsonNode open = initiate(student, "{\"examId\":\"" + exam + "\"}", 201);
		gateway.mode(FakeLiveExamPaymentGateway.Mode.VALID);
		mockMvc.perform(get("/api/v1/live-exam-orders/" + open.get("tranId").asText() + "/return")
			.param("status", "cancel"))
			.andExpect(status().isFound())
			.andExpect(header().string(HttpHeaders.LOCATION, resultPage(open.get("tranId").asText())));
		assertThat(orderStatus(student, open)).isEqualTo("INITIATED");
		assertThat(grantsFor(studentId)).isZero();

		mockMvc.perform(post("/api/v1/live-exam-orders/" + open.get("tranId").asText() + "/return")
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.param("status", "fail")
			.param("tran_id", open.get("tranId").asText())
			.param("val_id", "val-1"))
			.andExpect(status().isFound())
			.andExpect(header().string(HttpHeaders.LOCATION, resultPage(open.get("tranId").asText())));
		assertThat(orderStatus(student, open)).isEqualTo("SUCCEEDED");
		assertThat(grantsFor(studentId)).isEqualTo(1);

		gateway.mode(FakeLiveExamPaymentGateway.Mode.INVALID);
		mockMvc.perform(post("/api/v1/live-exam-orders/" + open.get("tranId").asText() + "/ipn")
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.param("status", "VALID")
			.param("tran_id", open.get("tranId").asText())
			.param("val_id", "val-1"))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exam-orders/" + open.get("tranId").asText() + "/ipn")
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.param("tran_id", open.get("tranId").asText())
			.param("val_id", "val-2"))
			.andExpect(status().isOk());
		assertThat(orderStatus(student, open)).isEqualTo("SUCCEEDED");
		assertThat(grantsFor(studentId)).isEqualTo(1);

		mockMvc.perform(post("/api/v1/live-exam-orders/" + open.get("tranId").asText() + "/return")
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.param("status", "success")
			.param("tran_id", "other-tran")
			.param("val_id", "val-1"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void theMasterAdminFindsOrdersWithThePaymentChannelAndTheGrant() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		UUID studentId = seed("STUDENT");
		String student = token(studentId);
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		String phone = jdbc.queryForObject("select phone from users where id = ?", String.class, studentId);
		String name = "Orders " + UUID.randomUUID().toString().substring(0, 8);
		jdbc.update("update student_profiles set full_name = ? where user_id = ?", name, studentId);
		UUID module = publishPricedModule(master, "Orders", "300.00");
		UUID exam = publishMcq(master, module, "Orders paper", "150.00", "2026-10-20T12:00:00+06:00");

		JsonNode failed = initiate(student, "{\"examId\":\"" + exam + "\"}", 201);
		gateway.mode(FakeLiveExamPaymentGateway.Mode.INVALID);
		notice(failed.get("tranId").asText(), "success");
		clock.set(TEN_AM.plusSeconds(60));
		gateway.channel("BKASH-BKash");
		JsonNode paid = initiate(student, "{\"examId\":\"" + exam + "\"}", 201);
		payTran(paid.get("tranId").asText());

		mockMvc.perform(get("/api/v1/live-exam-orders").header("Authorization", "Bearer " + master).param("q", phone))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.content[0].tranId").value(paid.get("tranId").asText()))
			.andExpect(jsonPath("$.content[0].status").value("SUCCEEDED"))
			.andExpect(jsonPath("$.content[0].amount").value(150.00))
			.andExpect(jsonPath("$.content[0].scope").value("EXAM"))
			.andExpect(jsonPath("$.content[0].examTitleEn").value("Orders paper"))
			.andExpect(jsonPath("$.content[0].moduleTitleEn").value("Orders"))
			.andExpect(jsonPath("$.content[0].paymentChannel").value("BKASH-BKash"))
			.andExpect(jsonPath("$.content[0].valId").value("val-1"))
			.andExpect(jsonPath("$.content[0].accessGranted").value(true))
			.andExpect(jsonPath("$.content[0].studentPhone").value(phone))
			.andExpect(jsonPath("$.content[0].studentName").value(name))
			.andExpect(jsonPath("$.content[1].tranId").value(failed.get("tranId").asText()))
			.andExpect(jsonPath("$.content[1].status").value("FAILED"))
			.andExpect(jsonPath("$.content[1].paymentChannel").doesNotExist())
			.andExpect(jsonPath("$.content[1].accessGranted").value(false));
		assertThat(jdbc.queryForObject("select payment_channel from live_exam_orders where tran_id = ?", String.class,
				paid.get("tranId").asText())).isEqualTo("BKASH-BKash");

		mockMvc.perform(get("/api/v1/live-exam-orders").header("Authorization", "Bearer " + master)
			.param("q", phone)
			.param("status", "failed"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].status").value("FAILED"));
		mockMvc.perform(get("/api/v1/live-exam-orders").header("Authorization", "Bearer " + master)
			.param("tranId", paid.get("tranId").asText()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].studentId").value(studentId.toString()));
		mockMvc.perform(get("/api/v1/live-exam-orders").header("Authorization", "Bearer " + master)
			.param("q", name.toLowerCase())
			.param("size", "1")
			.param("page", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.size").value(1))
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.content[0].tranId").value(failed.get("tranId").asText()));
		mockMvc.perform(get("/api/v1/live-exam-orders").header("Authorization", "Bearer " + master).param("status", "PAID"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		mockMvc.perform(get("/api/v1/live-exam-orders").header("Authorization", "Bearer " + token(seed("SYSTEM_ADMIN")))
			.param("q", phone))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.content[0].tranId").value(paid.get("tranId").asText()));
		mockMvc.perform(get("/api/v1/live-exam-orders").header("Authorization", "Bearer " + token(seed("INSTRUCTOR"))))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/live-exam-orders").header("Authorization", "Bearer " + student))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/live-exam-orders")).andExpect(status().isUnauthorized());
	}

	@Test
	void aStudentReadsOnlyTheirOwnOrdersNewestFirst() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		UUID studentId = seed("STUDENT");
		String student = token(studentId);
		UUID otherId = seed("STUDENT");
		String other = token(otherId);
		UUID module = publishPricedModule(master, "History", "300.00");
		UUID exam = publishMcq(master, module, "History paper", "150.00", "2026-10-20T12:00:00+06:00");

		JsonNode failed = initiate(student, "{\"examId\":\"" + exam + "\"}", 201);
		gateway.mode(FakeLiveExamPaymentGateway.Mode.INVALID);
		notice(failed.get("tranId").asText(), "success");
		clock.set(TEN_AM.plusSeconds(60));
		gateway.channel("BKASH-BKash");
		JsonNode paid = initiate(student, "{\"examId\":\"" + exam + "\"}", 201);
		payTran(paid.get("tranId").asText());
		clock.set(TEN_AM.plusSeconds(120));
		JsonNode open = initiate(student, "{\"moduleId\":\"" + module + "\"}", 201);
		initiate(other, "{\"examId\":\"" + exam + "\"}", 201);

		String body = mockMvc.perform(get("/api/v1/me/live-exam-orders").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(3))
			.andExpect(jsonPath("$.content[0].tranId").value(open.get("tranId").asText()))
			.andExpect(jsonPath("$.content[0].status").value("PENDING"))
			.andExpect(jsonPath("$.content[0].scope").value("MODULE"))
			.andExpect(jsonPath("$.content[0].moduleTitleEn").value("History"))
			.andExpect(jsonPath("$.content[0].examTitleEn").doesNotExist())
			.andExpect(jsonPath("$.content[0].amount").value(300.00))
			.andExpect(jsonPath("$.content[1].tranId").value(paid.get("tranId").asText()))
			.andExpect(jsonPath("$.content[1].orderId").value(paid.get("orderId").asText()))
			.andExpect(jsonPath("$.content[1].status").value("PAID"))
			.andExpect(jsonPath("$.content[1].scope").value("EXAM"))
			.andExpect(jsonPath("$.content[1].examTitleEn").value("History paper"))
			.andExpect(jsonPath("$.content[1].examTitleBn").exists())
			.andExpect(jsonPath("$.content[1].moduleTitleEn").value("History"))
			.andExpect(jsonPath("$.content[1].paymentChannel").value("BKASH-BKash"))
			.andExpect(jsonPath("$.content[1].currency").value("BDT"))
			.andExpect(jsonPath("$.content[1].createdAt").exists())
			.andExpect(jsonPath("$.content[2].status").value("FAILED"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(body).doesNotContain("valId", "val-1", "studentPhone", otherId.toString());

		mockMvc.perform(get("/api/v1/me/live-exam-orders").header("Authorization", "Bearer " + student)
			.param("size", "1")
			.param("page", "2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(3))
			.andExpect(jsonPath("$.content[0].status").value("FAILED"));
		mockMvc.perform(get("/api/v1/me/live-exam-orders").header("Authorization", "Bearer " + other))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1));
		for (String role : new String[] { "MASTER_ADMIN", "SYSTEM_ADMIN", "INSTRUCTOR" }) {
			mockMvc.perform(get("/api/v1/me/live-exam-orders").header("Authorization", "Bearer " + token(seed(role))))
				.andExpect(status().isForbidden());
		}
		mockMvc.perform(get("/api/v1/me/live-exam-orders")).andExpect(status().isUnauthorized());
	}

	@Test
	void anUnreachableNoticeLeavesTheOrderOpen() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		UUID studentId = seed("STUDENT");
		String student = token(studentId);
		UUID module = publishPricedModule(master, "Retry", "300.00");
		UUID exam = publishMcq(master, module, "Retry paper", "150.00", "2026-10-20T12:00:00+06:00");
		JsonNode order = initiate(student, "{\"examId\":\"" + exam + "\"}", 201);
		gateway.mode(FakeLiveExamPaymentGateway.Mode.UNREACHABLE);
		mockMvc.perform(post("/api/v1/live-exam-orders/" + order.get("tranId").asText() + "/return")
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.param("status", "success")
			.param("tran_id", order.get("tranId").asText())
			.param("val_id", "val-1"))
			.andExpect(status().isFound())
			.andExpect(header().string(HttpHeaders.LOCATION, resultPage(order.get("tranId").asText())));
		assertThat(orderStatus(student, order)).isEqualTo("INITIATED");
		mockMvc.perform(post("/api/v1/live-exam-orders/" + order.get("tranId").asText() + "/ipn")
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.param("tran_id", order.get("tranId").asText())
			.param("val_id", "val-1"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("PAYMENT_GATEWAY_UNAVAILABLE"));
		assertThat(orderStatus(student, order)).isEqualTo("INITIATED");
		assertThat(grantsFor(studentId)).isZero();
	}

	@Test
	void takeAndTheQuestionPdfFollowTheGrant() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		UUID outsiderId = seed("STUDENT");
		String outsider = token(outsiderId);
		UUID rosterId = seed("STUDENT");
		String rosterStudent = token(rosterId);
		UUID buyerId = seed("STUDENT");
		String buyer = token(buyerId);
		UUID moduleBuyerId = seed("STUDENT");
		String moduleBuyer = token(moduleBuyerId);

		UUID module = publishPricedModule(master, "Access", "900.00");
		UUID written = publishWritten(master, module, "Paper", "400.00", "2026-10-20T12:00:00+06:00");
		UUID sibling = publishMcq(master, module, "Sibling", "200.00", "2026-10-20T13:00:00+06:00");
		new JdbcTemplate(dataSource).update(
				"insert into live_exam_roster (module_id, student_id, created_at) values (?, ?, now())", module, rosterId);

		clock.set(NOON);
		mockMvc.perform(post("/api/v1/live-exams/" + written + "/take").header("Authorization", "Bearer " + outsider))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/live-exams/" + written + "/question-pdf").header("Authorization", "Bearer " + outsider))
			.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/live-exams/" + written + "/take").header("Authorization", "Bearer " + rosterStudent))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/live-exams/" + written + "/question-pdf")
			.header("Authorization", "Bearer " + rosterStudent))
			.andExpect(status().isOk());

		pay(buyer, "{\"examId\":\"" + sibling + "\"}");
		mockMvc.perform(post("/api/v1/live-exams/" + written + "/take").header("Authorization", "Bearer " + buyer))
			.andExpect(status().isNotFound());
		clock.set(Instant.parse("2026-10-20T07:00:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + sibling + "/take").header("Authorization", "Bearer " + buyer))
			.andExpect(status().isOk());
		JsonNode moduleCheckout = initiate(buyer, "{\"moduleId\":\"" + module + "\"}", 201);
		assertThat(moduleCheckout.get("status").asText()).isEqualTo("INITIATED");

		pay(moduleBuyer, "{\"moduleId\":\"" + module + "\"}");
		UUID later = publishMcq(master, module, "Later", "250.00", "2026-10-20T14:00:00+06:00");
		clock.set(Instant.parse("2026-10-20T08:00:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + later + "/take").header("Authorization", "Bearer " + moduleBuyer))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exam-orders").header("Authorization", "Bearer " + buyer)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"examId\":\"" + sibling + "\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ALREADY_PURCHASED"));
	}

	@Test
	void anUndatedPurchaseStaysOnTheListAndOffTheRoutine() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		String student = token(seed("STUDENT"));
		UUID module = publishPricedModule(master, "Undated", "700.00");
		UUID dated = publishMcq(master, module, "Dated", "220.00", "2026-10-20T12:00:00+06:00");
		UUID undated = publishMcq(master, module, "Undated", "180.00", null);
		pay(student, "{\"examId\":\"" + dated + "\"}");
		pay(student, "{\"examId\":\"" + undated + "\"}");

		JsonNode exams = exams(student, module);
		assertThat(ids(exams)).contains(dated, undated);
		mockMvc.perform(post("/api/v1/live-exams/" + undated + "/take").header("Authorization", "Bearer " + student))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SITTING_NOT_OPEN"));

		clock.set(NOON);
		JsonNode routine = routine(student);
		// An exam with no start time stays on the routine, with its status, until a date is set.
		assertThat(ids(routine)).contains(dated, undated);
	}

	@Test
	void cancellingAnOpenOrderUsesTheCurrentPriceAndAPaidOrderStays() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		UUID studentId = seed("STUDENT");
		String student = token(studentId);
		UUID module = publishPricedModule(master, "Cancel", "50.00");
		UUID exam = publishMcq(master, module, "Cancel paper", "500.00", "2026-10-20T12:00:00+06:00");
		JsonNode open = initiate(student, "{\"examId\":\"" + exam + "\"}", 201);
		mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"price\":700}"))
			.andExpect(status().isOk());
		assertThat(money(order(student, open).get("amount"))).isEqualByComparingTo("500.00");
		mockMvc.perform(post("/api/v1/live-exam-orders/" + open.get("orderId").asText() + "/cancel")
			.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CANCELLED"));
		assertThat(grantsFor(studentId)).isZero();

		finalSaveExam(master, exam);
		publishExam(master, exam);
		JsonNode next = initiate(student, "{\"examId\":\"" + exam + "\"}", 201);
		assertThat(money(next.get("amount"))).isEqualByComparingTo("700.00");
		assertThat(next.get("tranId").asText()).isNotEqualTo(open.get("tranId").asText());
		payTran(next.get("tranId").asText());
		mockMvc.perform(post("/api/v1/live-exam-orders/" + next.get("orderId").asText() + "/cancel")
			.header("Authorization", "Bearer " + student))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PAYMENT_NOT_PENDING"));
		assertThat(grantsFor(studentId)).isEqualTo(1);
	}

	@Test
	void paymentStatusReturnsTheExamNameOnlyToTheOwner() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		UUID ownerId = seed("STUDENT");
		String owner = token(ownerId);
		String other = token(seed("STUDENT"));
		String admin = token(seed("SYSTEM_ADMIN"));
		UUID module = publishPricedModule(master, "Status shop", "300.00");
		UUID exam = publishMcq(master, module, "Status paper", "25.00", "2026-10-20T12:00:00+06:00");
		JsonNode pending = initiate(owner, "{\"examId\":\"" + exam + "\"}", 201);
		String tranId = pending.get("tranId").asText();

		mockMvc.perform(get("/api/v1/live-exam-orders/status").param("tranId", tranId)).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/live-exam-orders/status").header("Authorization", "Bearer " + owner))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		mockMvc.perform(get("/api/v1/live-exam-orders/status").header("Authorization", "Bearer " + other)
			.param("tranId", tranId))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/live-exam-orders/status").header("Authorization", "Bearer " + admin)
			.param("tranId", tranId))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/live-exam-orders/status").header("Authorization", "Bearer " + owner)
			.param("tranId", "missing-tran"))
			.andExpect(status().isNotFound());

		String body = mockMvc
			.perform(get("/api/v1/live-exam-orders/status").header("Authorization", "Bearer " + owner).param("tranId", tranId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PENDING"))
			.andExpect(jsonPath("$.examName").value("Status paper"))
			.andExpect(jsonPath("$.amount").doesNotExist())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(json.readTree(body).propertyNames()).containsExactlyInAnyOrder("status", "examName");

		JsonNode moduleOrder = initiate(owner, "{\"moduleId\":\"" + module + "\"}", 201);
		mockMvc.perform(get("/api/v1/live-exam-orders/status").header("Authorization", "Bearer " + owner)
			.param("tranId", moduleOrder.get("tranId").asText()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PENDING"))
			.andExpect(jsonPath("$.examName").value("Status shop"));

		gateway.mode(FakeLiveExamPaymentGateway.Mode.INVALID);
		notice(tranId, "fail");
		mockMvc.perform(get("/api/v1/live-exam-orders/status").header("Authorization", "Bearer " + owner).param("tranId", tranId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("FAILED"))
			.andExpect(jsonPath("$.examName").value("Status paper"));

		UUID cancelledExam = publishMcq(master, module, "Status cancel", "30.00", "2026-10-20T14:00:00+06:00");
		JsonNode cancelled = initiate(owner, "{\"examId\":\"" + cancelledExam + "\"}", 201);
		mockMvc.perform(post("/api/v1/live-exam-orders/" + cancelled.get("orderId").asText() + "/cancel")
			.header("Authorization", "Bearer " + owner))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/live-exam-orders/status").header("Authorization", "Bearer " + owner)
			.param("tranId", cancelled.get("tranId").asText()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CANCELLED"))
			.andExpect(jsonPath("$.examName").value("Status cancel"));

		gateway.mode(FakeLiveExamPaymentGateway.Mode.VALID);
		UUID paidExam = publishMcq(master, module, "Status paid", "40.00", "2026-10-20T15:00:00+06:00");
		JsonNode paid = initiate(owner, "{\"examId\":\"" + paidExam + "\"}", 201);
		payTran(paid.get("tranId").asText());
		mockMvc.perform(get("/api/v1/live-exam-orders/status").header("Authorization", "Bearer " + owner)
			.param("tranId", paid.get("tranId").asText()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PAID"))
			.andExpect(jsonPath("$.examName").value("Status paid"));
		assertThat(grantsFor(ownerId)).isEqualTo(1);
	}

	@Test
	void aMissingGatewayWritesNoOrder() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		UUID studentId = seed("STUDENT");
		String student = token(studentId);
		UUID module = publishPricedModule(master, "No gateway", "10.00");
		UUID exam = publishMcq(master, module, "No gateway paper", "10.00", "2026-10-20T12:00:00+06:00");
		int before = orderCount(studentId);

		withoutGateway(() -> {
			assertThat(paymentGateways.getIfAvailable()).isNull();
			mockMvc.perform(post("/api/v1/live-exam-orders").header("Authorization", "Bearer " + student)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"examId\":\"" + exam + "\"}"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("PAYMENT_GATEWAY_UNAVAILABLE"))
				.andExpect(jsonPath("$.orderId").doesNotExist())
				.andExpect(jsonPath("$.tranId").doesNotExist());
		});
		assertThat(orderCount(studentId)).isEqualTo(before);
		assertThat(paymentGateways.getIfAvailable()).isNotNull();

		JsonNode open = initiate(student, "{\"examId\":\"" + exam + "\"}", 201);
		withoutGateway(() -> {
			mockMvc.perform(post("/api/v1/live-exam-orders").header("Authorization", "Bearer " + student)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"examId\":\"" + exam + "\"}"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("PAYMENT_GATEWAY_UNAVAILABLE"))
				.andExpect(jsonPath("$.orderId").doesNotExist());
		});
		assertThat(orderCount(studentId)).isEqualTo(before + 1);
		assertThat(orderStatus(student, open)).isEqualTo("INITIATED");
		assertThat(grantsFor(studentId)).isZero();
	}

	@Test
	void aRejectedSessionNamesTheOrderAndTheNextBuyGetsANewTranId() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		UUID studentId = seed("STUDENT");
		String student = token(studentId);
		UUID module = publishPricedModule(master, "Rejected", "10.00");
		UUID exam = publishMcq(master, module, "Rejected paper", "10.00", "2026-10-20T12:00:00+06:00");
		gateway.mode(FakeLiveExamPaymentGateway.Mode.REJECT_SESSION);

		MvcResult rejected = mockMvc.perform(post("/api/v1/live-exam-orders").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"examId\":\"" + exam + "\"}"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("PAYMENT_GATEWAY_UNAVAILABLE"))
			.andExpect(jsonPath("$.orderId").isNotEmpty())
			.andExpect(jsonPath("$.tranId").isNotEmpty())
			.andExpect(jsonPath("$.redirectUrl").doesNotExist())
			.andReturn();
		String body = rejected.getResponse().getContentAsString();
		assertThat(body).doesNotContain("https://");
		assertThat(body).doesNotContain("store_id");
		assertThat(body).doesNotContain("store_passwd");
		if (sslCommerz.storeId() != null && !sslCommerz.storeId().isBlank()) {
			assertThat(body).doesNotContain(sslCommerz.storeId());
		}
		if (sslCommerz.storePassword() != null && !sslCommerz.storePassword().isBlank()) {
			assertThat(body).doesNotContain(sslCommerz.storePassword());
		}
		JsonNode error = json.readTree(body);
		UUID orderId = UUID.fromString(error.get("orderId").asText());
		String tranId = error.get("tranId").asText();
		assertThat(storedStatus(orderId)).isEqualTo("INITIATED");
		assertThat(grantsFor(studentId)).isZero();

		mockMvc.perform(post("/api/v1/live-exam-orders/" + orderId + "/cancel").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CANCELLED"));

		gateway.mode(FakeLiveExamPaymentGateway.Mode.VALID);
		JsonNode next = initiate(student, "{\"examId\":\"" + exam + "\"}", 201);
		assertThat(next.get("tranId").asText()).isNotEqualTo(tranId);
		assertThat(next.get("redirectUrl").asText()).startsWith("https://gateway.test/");
	}

	@Test
	void aRejectedSessionOnARepeatKeepsTheSameIds() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		UUID studentId = seed("STUDENT");
		String student = token(studentId);
		UUID module = publishPricedModule(master, "Repeat reject", "10.00");
		UUID exam = publishMcq(master, module, "Repeat reject paper", "10.00", "2026-10-20T12:00:00+06:00");
		JsonNode open = initiate(student, "{\"examId\":\"" + exam + "\"}", 201);
		gateway.mode(FakeLiveExamPaymentGateway.Mode.REJECT_SESSION);

		mockMvc.perform(post("/api/v1/live-exam-orders").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"examId\":\"" + exam + "\"}"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("PAYMENT_GATEWAY_UNAVAILABLE"))
			.andExpect(jsonPath("$.orderId").value(open.get("orderId").asText()))
			.andExpect(jsonPath("$.tranId").value(open.get("tranId").asText()))
			.andExpect(jsonPath("$.redirectUrl").doesNotExist());
		assertThat(storedStatus(UUID.fromString(open.get("orderId").asText()))).isEqualTo("INITIATED");
		assertThat(grantsFor(studentId)).isZero();
	}

	@Test
	void aGatewayBrowserReturnFromAnotherOriginStillRedirects() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		UUID studentId = seed("STUDENT");
		String student = token(studentId);
		UUID module = publishPricedModule(master, "Foreign origin", "10.00");
		JsonNode open = initiate(student,
				examBody(publishMcq(master, module, "Foreign origin paper", "10.00", "2026-10-20T12:00:00+06:00")), 201);
		gateway.lookup(LiveExamPaymentGateway.Lookup.Kind.NONE);

		mockMvc.perform(post("/api/v1/live-exam-orders/" + open.get("tranId").asText() + "/return")
			.header(HttpHeaders.ORIGIN, "https://sandbox.sslcommerz.com")
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.param("status", "fail")
			.param("tran_id", open.get("tranId").asText()))
			.andExpect(status().isFound())
			.andExpect(header().string(HttpHeaders.LOCATION, resultPage(open.get("tranId").asText())));
		assertThat(gateway.lookups()).isEqualTo(1);
		assertThat(orderStatus(student, open)).isEqualTo("INITIATED");
		assertThat(grantsFor(studentId)).isZero();
	}

	@Test
	void aReturnWithoutValIdLooksUpOnce() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		UUID studentId = seed("STUDENT");
		String student = token(studentId);
		UUID module = publishPricedModule(master, "Lookup", "10.00");

		JsonNode paid = initiate(student, examBody(publishMcq(master, module, "Lookup paid", "10.00", "2026-10-20T12:00:00+06:00")), 201);
		gateway.lookup(LiveExamPaymentGateway.Lookup.Kind.PAID);
		browserReturn(paid.get("tranId").asText(), "fail");
		assertThat(gateway.lookups()).isEqualTo(1);
		assertThat(orderStatus(student, paid)).isEqualTo("SUCCEEDED");
		assertThat(grantsFor(studentId)).isEqualTo(1);

		JsonNode invalid = initiate(student, examBody(publishMcq(master, module, "Lookup invalid", "10.00", "2026-10-20T13:00:00+06:00")), 201);
		gateway.mode(FakeLiveExamPaymentGateway.Mode.INVALID);
		gateway.lookup(LiveExamPaymentGateway.Lookup.Kind.PAID);
		browserReturn(invalid.get("tranId").asText(), "success");
		assertThat(gateway.lookups()).isEqualTo(2);
		assertThat(orderStatus(student, invalid)).isEqualTo("FAILED");
		assertThat(grantsFor(studentId)).isEqualTo(1);

		gateway.mode(FakeLiveExamPaymentGateway.Mode.VALID);
		JsonNode failed = initiate(student, examBody(publishMcq(master, module, "Lookup failed", "10.00", "2026-10-20T14:00:00+06:00")), 201);
		gateway.lookup(LiveExamPaymentGateway.Lookup.Kind.FAILED);
		browserReturn(failed.get("tranId").asText(), "success");
		assertThat(gateway.lookups()).isEqualTo(3);
		assertThat(orderStatus(student, failed)).isEqualTo("FAILED");
		assertThat(grantsFor(studentId)).isEqualTo(1);

		JsonNode cancelled = initiate(student,
				examBody(publishMcq(master, module, "Lookup cancelled", "10.00", "2026-10-20T15:00:00+06:00")), 201);
		gateway.lookup(LiveExamPaymentGateway.Lookup.Kind.CANCELLED);
		browserReturn(cancelled.get("tranId").asText(), "success");
		assertThat(gateway.lookups()).isEqualTo(4);
		assertThat(orderStatus(student, cancelled)).isEqualTo("CANCELLED");
		assertThat(grantsFor(studentId)).isEqualTo(1);

		JsonNode none = initiate(student, examBody(publishMcq(master, module, "Lookup none", "10.00", "2026-10-20T16:00:00+06:00")), 201);
		gateway.lookup(LiveExamPaymentGateway.Lookup.Kind.NONE);
		browserReturn(none.get("tranId").asText(), "fail");
		assertThat(gateway.lookups()).isEqualTo(5);
		assertThat(orderStatus(student, none)).isEqualTo("INITIATED");

		JsonNode unreachable = initiate(student, examBody(publishMcq(master, module, "Lookup down", "10.00", "2026-10-20T17:00:00+06:00")), 201);
		gateway.lookup(LiveExamPaymentGateway.Lookup.Kind.UNREACHABLE);
		browserReturn(unreachable.get("tranId").asText(), "cancel");
		assertThat(gateway.lookups()).isEqualTo(6);
		assertThat(orderStatus(student, unreachable)).isEqualTo("INITIATED");
		assertThat(grantsFor(studentId)).isEqualTo(1);

		JsonNode thrown = initiate(student, examBody(publishMcq(master, module, "Lookup throw", "10.00", "2026-10-20T18:00:00+06:00")), 201);
		gateway.failLookup(new IllegalStateException("https://payments.example/secret-body"));
		Logger logger = (Logger) LoggerFactory.getLogger(LiveExamOrderService.class);
		ListAppender<ILoggingEvent> logs = new ListAppender<>();
		logs.start();
		logger.addAppender(logs);
		try {
			browserReturn(thrown.get("tranId").asText(), "fail");
		}
		finally {
			logger.detachAppender(logs);
		}
		assertThat(gateway.lookups()).isEqualTo(7);
		assertThat(orderStatus(student, thrown)).isEqualTo("INITIATED");
		assertThat(grantsFor(studentId)).isEqualTo(1);
		String logged = logs.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (left, right) -> left + "\n" + right);
		assertThat(logged).contains("IllegalStateException");
		assertThat(logged).doesNotContain("https://");
		assertThat(logged).doesNotContain("secret-body");
		assertThat(logs.list).allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
	}

	private static String examBody(UUID examId) {
		return "{\"examId\":\"" + examId + "\"}";
	}

	private void browserReturn(String tranId, String postedStatus) throws Exception {
		mockMvc.perform(post("/api/v1/live-exam-orders/" + tranId + "/return")
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.param("status", postedStatus)
			.param("tran_id", tranId))
			.andExpect(status().isFound())
			.andExpect(header().string(HttpHeaders.LOCATION, resultPage(tranId)));
	}

	private JsonNode initiate(String student, String body, int status) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/live-exam-orders").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body))
			.andExpect(status().is(status))
			.andReturn();
		return json.readTree(result.getResponse().getContentAsString());
	}

	private void pay(String student, String body) throws Exception {
		JsonNode order = initiate(student, body, 201);
		payTran(order.get("tranId").asText());
	}

	private void payTran(String tranId) throws Exception {
		gateway.mode(FakeLiveExamPaymentGateway.Mode.VALID);
		notice(tranId, "success");
	}

	private void notice(String tranId, String outcome) throws Exception {
		mockMvc.perform(post("/api/v1/live-exam-orders/" + tranId + "/return")
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.param("status", outcome)
			.param("tran_id", tranId)
			.param("val_id", "val-1"))
			.andExpect(status().isFound())
			.andExpect(header().string(HttpHeaders.LOCATION, resultPage(tranId)));
	}

	private static String resultPage(String tranId) {
		return "http://localhost:3000/purchase/result?tranId=" + tranId;
	}

	private String orderStatus(String student, JsonNode order) throws Exception {
		return order(student, order).get("status").asText();
	}

	private JsonNode order(String student, JsonNode order) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/live-exam-orders/" + order.get("orderId").asText())
			.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andReturn();
		return json.readTree(result.getResponse().getContentAsString());
	}

	private int grantsFor(UUID studentId) {
		return new JdbcTemplate(dataSource).queryForObject(
				"select count(*) from live_exam_grants where student_id = ?", Integer.class, studentId);
	}

	private int orderCount(UUID studentId) {
		return new JdbcTemplate(dataSource).queryForObject(
				"select count(*) from live_exam_orders where student_id = ?", Integer.class, studentId);
	}

	private String storedStatus(UUID orderId) {
		return new JdbcTemplate(dataSource).queryForObject("select status from live_exam_orders where id = ?",
				String.class, orderId);
	}

	private void withoutGateway(ThrowingRunnable action) throws Exception {
		DefaultListableBeanFactory factory = (DefaultListableBeanFactory) ((ConfigurableApplicationContext) applicationContext)
			.getBeanFactory();
		String[] names = factory.getBeanNamesForType(LiveExamPaymentGateway.class);
		java.util.Map<String, BeanDefinition> definitions = new java.util.LinkedHashMap<>();
		java.util.Map<String, Object> singletons = new java.util.LinkedHashMap<>();
		for (String name : names) {
			if (factory.containsSingleton(name)) {
				singletons.put(name, factory.getSingleton(name));
			}
			if (factory.containsBeanDefinition(name)) {
				definitions.put(name, factory.getBeanDefinition(name));
				factory.removeBeanDefinition(name);
			}
		}
		for (String name : singletons.keySet()) {
			factory.destroySingleton(name);
		}
		try {
			action.run();
		}
		finally {
			definitions.forEach(factory::registerBeanDefinition);
			singletons.forEach(factory::registerSingleton);
		}
	}

	@FunctionalInterface
	private interface ThrowingRunnable {

		void run() throws Exception;

	}

	private UUID publishPricedModule(String token, String title, String price) throws Exception {
		UUID module = createModule(token, title, price);
		publishModule(token, module);
		return module;
	}

	private UUID createModule(String token, String title, String price) throws Exception {
		UUID category = createCategory(token, title);
		String priceField = price == null ? "" : ",\"price\":" + price;
		MvcResult created = mockMvc.perform(post("/api/v1/live-exam-modules").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"categoryId":"%s","titleEn":"%s","titleBn":"মডিউল","descriptionEn":"d","descriptionBn":"বি"%s}
					""".formatted(category, title, priceField)))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
	}

	private void publishModule(String token, UUID module) throws Exception {
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/final-save").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/publish").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
	}

	private UUID publishMcq(String token, UUID module, String title, String price, String liveAt) throws Exception {
		UUID exam = createExam(token, module, "MCQ", title, "পরীক্ষা", liveAt, 40, 50, null, price);
		addQuestion(token, exam);
		finalSaveExam(token, exam);
		publishExam(token, exam);
		return exam;
	}

	private UUID publishWritten(String token, UUID module, String title, String price, String liveAt) throws Exception {
		UUID exam = createExam(token, module, "WRITTEN", title, "খাতা", liveAt, 40, 50, 20, price);
		uploadPdf(token, exam);
		finalSaveExam(token, exam);
		publishExam(token, exam);
		return exam;
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

	private UUID createExam(String token, UUID moduleId, String type, String titleEn, String titleBn, String liveAt,
			int duration, Integer passPercent, Integer maximum, String price) throws Exception {
		String when = liveAt == null ? "" : ",\"liveAt\":\"" + liveAt + "\"";
		String percent = passPercent == null ? "" : ",\"passPercent\":" + passPercent;
		String max = maximum == null ? "" : ",\"maximum\":" + maximum;
		String priceField = price == null ? "" : ",\"price\":" + price;
		MvcResult result = mockMvc
			.perform(post("/api/v1/live-exam-modules/" + moduleId + "/exams").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "examType":"%s",
						  "paperLanguage":"BN",
						  "titleEn":"%s",
						  "titleBn":"%s",
						  "descriptionEn":"English description",
						  "descriptionBn":"বাংলা বিবরণ",
						  "rulesEn":"English rules",
						  "rulesBn":"বাংলা নিয়ম",
						  "durationMinutes":%d%s%s%s%s
						}
						""".formatted(type, titleEn, titleBn, duration, when, percent, max, priceField)))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private void addQuestion(String token, UUID examId) throws Exception {
		mockMvc.perform(post("/api/v1/live-exams/" + examId + "/questions").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"stem":"Stem","marks":1,"options":[{"text":"Yes","correct":true},{"text":"No","correct":false}]}
					"""))
			.andExpect(status().isCreated());
	}

	private void uploadPdf(String token, UUID examId) throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "paper.pdf", "application/pdf", """
				%PDF-1.1
				1 0 obj<</Type/Catalog>>endobj
				trailer<</Root 1 0 R>>
				%%EOF
				""".getBytes(StandardCharsets.US_ASCII));
		mockMvc.perform(multipart("/api/v1/live-exams/" + examId + "/question-pdf").file(file)
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
	}

	private void finalSaveExam(String token, UUID examId) throws Exception {
		mockMvc.perform(post("/api/v1/live-exams/" + examId + "/final-save").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
	}

	private void publishExam(String token, UUID examId) throws Exception {
		mockMvc.perform(post("/api/v1/live-exams/" + examId + "/publish").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
	}

	private JsonNode exams(String token, UUID moduleId) throws Exception {
		MvcResult result = mockMvc
			.perform(get("/api/v1/live-exam-modules/" + moduleId + "/exams").header("Authorization", "Bearer " + token)
				.param("size", "100"))
			.andExpect(status().isOk())
			.andReturn();
		return json.readTree(result.getResponse().getContentAsString()).get("content");
	}

	private JsonNode routine(String token) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/me/exam-routine").header("Authorization", "Bearer " + token)
			.param("size", "100"))
			.andExpect(status().isOk())
			.andReturn();
		return json.readTree(result.getResponse().getContentAsString()).get("content");
	}

	private JsonNode exam(String token, UUID examId) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/live-exams/" + examId).header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn();
		return json.readTree(result.getResponse().getContentAsString());
	}

	private static java.util.List<UUID> ids(JsonNode content) {
		java.util.List<UUID> ids = new java.util.ArrayList<>();
		for (JsonNode node : content) {
			ids.add(UUID.fromString(node.get("id").asText()));
		}
		return ids;
	}

	private static BigDecimal money(JsonNode node) {
		return new BigDecimal(node.asText());
	}

	private UUID seed(String role) {
		UUID id = UUID.randomUUID();
		new JdbcTemplate(dataSource).update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, uniquePhone(), uniqueEmail(), passwords.encode("password1"), role);
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
		return "live-purchase-" + UUID.randomUUID() + "@example.com";
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
