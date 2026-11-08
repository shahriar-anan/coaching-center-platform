package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import javax.sql.DataSource;

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

/**
 * The direct-enrolment payment plans end to end, through the HTTP routes (plan direct-enrolment-installments, sections 4, 6 and 10).
 * The clock is fixed at 10:00 in Dhaka on 20 October 2026, so "today" in every test is that day.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, InstallmentPlanIT.ClockConfig.class })
class InstallmentPlanIT extends PostgresIntegrationTest {

	private static final String TODAY = "2026-10-20";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Test
	void enrollingWithAPaymentOpensAPlanWithItsScheduleAndReceipt() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID student = seed("STUDENT", "Plan Student");
		UUID module = module(system, "Plan enrol");

		JsonNode grant = enrol(master, student, module, null, "300", "100", "TXN-1001", row("100", TODAY),
				row("100", "2026-11-20"), row("100", "2026-12-20"));
		assertThat(grant.get("paymentPlanId").asText()).isNotBlank();
		assertThat(grant.get("receiptNo").asText()).startsWith("RCPT-2026-");

		JsonNode plan = detail(master, grant.get("paymentPlanId").asText());
		assertThat(plan.get("plan").get("status").asText()).isEqualTo("ACTIVE");
		assertThat(plan.get("plan").get("due").decimalValue()).isEqualByComparingTo("200");
		assertThat(plan.get("schedule").size()).isEqualTo(3);
		assertThat(plan.get("payments").size()).isEqualTo(1);
		assertThat(plan.get("payments").get(0).get("receiptNo").asText()).isEqualTo(grant.get("receiptNo").asText());
	}

	@Test
	void aPaymentAboveWhatIsDueOrAReusedReferenceIsRefused() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID student = seed("STUDENT", "Overpaid Student");
		UUID module = module(system, "Overpaid");
		String plan = enrol(master, student, module, null, "300", "100", "TXN-2001", row("100", TODAY),
				row("100", "2026-11-20"), row("100", "2026-12-20")).get("paymentPlanId").asText();

		mockMvc.perform(post("/api/v1/installment-plans/" + plan + "/payments").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(paymentBody("250", "BKASH", "REF-A")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INSTALLMENT_OVERPAYMENT"));

		mockMvc.perform(post("/api/v1/installment-plans/" + plan + "/payments").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(paymentBody("50", "BKASH", "REF-A")))
			.andExpect(status().isCreated());

		// The same reference, in other case and with spaces, is the same reference (plan section 4).
		mockMvc.perform(post("/api/v1/installment-plans/" + plan + "/payments").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(paymentBody("30", "BKASH", "  ref-a ")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INSTALLMENT_DUPLICATE_REFERENCE"));
	}

	@Test
	void onlyTheLatestLivePaymentCanBeVoided() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID student = seed("STUDENT", "Voided Student");
		UUID module = module(system, "Voided");
		String plan = enrol(master, student, module, null, "300", "100", "TXN-3001", row("100", TODAY),
				row("100", "2026-11-20"), row("100", "2026-12-20")).get("paymentPlanId").asText();
		mockMvc.perform(post("/api/v1/installment-plans/" + plan + "/payments").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(paymentBody("50", "BKASH", "REF-B")))
			.andExpect(status().isCreated());

		JsonNode before = detail(master, plan);
		String first = before.get("payments").get(0).get("id").asText();
		String latest = before.get("payments").get(1).get("id").asText();

		mockMvc.perform(post("/api/v1/installment-payments/" + first + "/void").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\":\"Typed in twice\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INSTALLMENT_VOID_NOT_LATEST"));

		mockMvc.perform(post("/api/v1/installment-payments/" + latest + "/void").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\":\"Wrong amount\"}"))
			.andExpect(status().isOk());

		JsonNode after = detail(master, plan);
		assertThat(after.get("plan").get("due").decimalValue()).isEqualByComparingTo("200");
		// The API leaves a null field out, so a live payment has no voidedAt at all and a voided one has it.
		assertThat(after.get("payments").get(1).hasNonNull("voidedAt")).isTrue();
		// The voided row stays in the record and its receipt number is not reused.
		assertThat(after.get("payments").size()).isEqualTo(2);
	}

	@Test
	void aScheduleChangeKeepsTheIdOfEveryRowItUpdatesOrKeeps() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID student = seed("STUDENT", "Rescheduled Student");
		UUID module = module(system, "Rescheduled");
		String plan = enrol(master, student, module, null, "300", "100", "TXN-4001", row("100", TODAY),
				row("100", "2026-11-20"), row("100", "2026-12-20")).get("paymentPlanId").asText();
		Map<Integer, String> idsBefore = installmentIds(plan);

		// Row 1 is paid and stays. Row 2 is unchanged. Row 3 moves to January.
		mockMvc.perform(put("/api/v1/installment-plans/" + plan + "/schedule").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"schedule\":[" + row("100", TODAY) + "," + row("100", "2026-11-20") + "," + row("100", "2027-01-20") + "]}"))
			.andExpect(status().isOk());

		Map<Integer, String> idsAfter = installmentIds(plan);
		assertThat(idsAfter).hasSize(3);
		assertThat(idsAfter).isEqualTo(idsBefore);
		assertThat(dueOn(plan, 3)).isEqualTo("2027-01-20");
	}

	@Test
	void revokedAccessCanBeRestoredOnItsOwnPlanOnce() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID student = seed("STUDENT", "Restored Student");
		UUID module = module(system, "Restored");
		JsonNode grant = enrol(master, student, module, null, "300", "100", "TXN-5001", row("100", TODAY),
				row("100", "2026-11-20"), row("100", "2026-12-20"));
		String plan = grant.get("paymentPlanId").asText();

		mockMvc.perform(delete("/api/v1/live-exam-grants/" + grant.get("id").asText()).param("reason", "Did not pay on time")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
		assertThat(detail(master, plan).get("plan").get("accessRevoked").asBoolean()).isTrue();

		mockMvc.perform(post("/api/v1/installment-plans/" + plan + "/restore-access").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
		JsonNode restored = detail(master, plan);
		assertThat(restored.get("plan").get("accessRevoked").asBoolean()).isFalse();
		assertThat(restored.get("plan").get("grantId").asText()).isNotEqualTo(grant.get("id").asText());

		// Access is already back, so a second restore is refused.
		mockMvc.perform(post("/api/v1/installment-plans/" + plan + "/restore-access").header("Authorization", "Bearer " + master))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INSTALLMENT_RESTORE_REFUSED"));
	}

	@Test
	void aStudentSeesOnlyTheirOwnPlan() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID owner = seed("STUDENT", "Owner Student");
		UUID other = seed("STUDENT", "Other Student");
		UUID module = module(system, "Private");
		String plan = enrol(master, owner, module, null, "300", "100", "TXN-6001", row("100", TODAY),
				row("100", "2026-11-20"), row("100", "2026-12-20")).get("paymentPlanId").asText();

		mockMvc.perform(get("/api/v1/me/installment-plans/" + plan).header("Authorization", "Bearer " + token(owner)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.plan.planId").value(plan));
		mockMvc.perform(get("/api/v1/me/installment-plans/" + plan).header("Authorization", "Bearer " + token(other)))
			.andExpect(status().isNotFound());
	}

	@Test
	void aPackageWithALivePlanCannotBeDeletedAndAStaffMemberWithoutTheRightRoleCannotRecordAPayment() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID student = seed("STUDENT", "Guarded Student");
		UUID module = module(system, "Guarded");
		String plan = enrol(master, student, module, null, "300", "100", "TXN-7001", row("100", TODAY),
				row("100", "2026-11-20"), row("100", "2026-12-20")).get("paymentPlanId").asText();

		mockMvc.perform(delete("/api/v1/live-exam-modules/" + module).header("Authorization", "Bearer " + master))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PACKAGE_HAS_INSTALLMENTS"));

		mockMvc.perform(post("/api/v1/installment-plans/" + plan + "/payments").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content(paymentBody("50", "BKASH", "REF-C")))
			.andExpect(status().isForbidden());
	}

	/** One installment as the schedule form sends it, in the order given. */
	private static String row(String amount, String dueOn) {
		return "{\"amount\":" + amount + ",\"dueOn\":\"" + dueOn + "\"}";
	}

	private static String paymentBody(String amount, String method, String reference) {
		return "{\"amount\":" + amount + ",\"method\":\"" + method + "\",\"reference\":\"" + reference + "\",\"paidOn\":\"" + TODAY
				+ "\",\"note\":null}";
	}

	/** Enrols one student on a package with a payment, and returns the grant response. */
	private JsonNode enrol(String master, UUID student, UUID module, UUID exam, String total, String paid, String reference,
			String... rows) throws Exception {
		String scope = exam == null ? "MODULE" : "EXAM";
		String body = "{\"studentId\":\"" + student + "\",\"scope\":\"" + scope + "\",\"moduleId\":"
				+ (module == null ? "null" : "\"" + module + "\"") + ",\"examId\":"
				+ (exam == null ? "null" : "\"" + exam + "\"") + ",\"reason\":\"\",\"payment\":{\"agreedTotal\":" + total
				+ ",\"amount\":" + paid + ",\"method\":\"BKASH\",\"reference\":\"" + reference + "\",\"paidOn\":\"" + TODAY
				+ "\",\"note\":null,\"schedule\":[" + String.join(",", rows) + "]}}";
		MvcResult result = mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body))
			.andExpect(status().isCreated())
			.andReturn();
		return json.readTree(result.getResponse().getContentAsString());
	}

	private JsonNode detail(String token, String planId) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/installment-plans/" + planId).header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn();
		return json.readTree(result.getResponse().getContentAsString());
	}

	/** The id of each installment row of a plan, keyed by its position. */
	private Map<Integer, String> installmentIds(String planId) {
		Map<Integer, String> out = new LinkedHashMap<>();
		for (Map<String, Object> row : new JdbcTemplate(dataSource).queryForList(
				"select seq, id from live_exam_installments where plan_id = ? order by seq", UUID.fromString(planId))) {
			out.put(((Number) row.get("seq")).intValue(), row.get("id").toString());
		}
		return out;
	}

	private String dueOn(String planId, int seq) {
		return new JdbcTemplate(dataSource).queryForObject(
				"select due_on::text from live_exam_installments where plan_id = ? and seq = ?", String.class,
				UUID.fromString(planId), seq);
	}

	private UUID module(String token, String title) throws Exception {
		MvcResult category = mockMvc.perform(post("/api/v1/live-exam-categories").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titleEn":"%s cat","titleBn":"বিভাগ"}
					""".formatted(title)))
			.andExpect(status().isCreated())
			.andReturn();
		UUID categoryId = UUID.fromString(json.readTree(category.getResponse().getContentAsString()).get("id").asText());
		MvcResult created = mockMvc.perform(post("/api/v1/live-exam-modules").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"categoryId":"%s","titleEn":"%s","titleBn":"মডিউল","descriptionEn":"d","descriptionBn":"বি"}
					""".formatted(categoryId, title)))
			.andExpect(status().isCreated())
			.andReturn();
		UUID id = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
		mockMvc.perform(post("/api/v1/live-exam-modules/" + id + "/final-save").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exam-modules/" + id + "/publish").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		return id;
	}

	private UUID seed(String role, String name) {
		UUID id = UUID.randomUUID();
		String phone = "+8801" + ThreadLocalRandom.current().nextInt(100000000, 999999999);
		new JdbcTemplate(dataSource).update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, phone, "plan-" + id + "@example.com", passwords.encode("password1"), role);
		if ("STUDENT".equals(role)) {
			new JdbcTemplate(dataSource).update(
					"INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, ?, ?)", id, name,
					"P" + id.toString().substring(0, 8));
		}
		else {
			new JdbcTemplate(dataSource).update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, ?)", id, name);
		}
		return id;
	}

	private String token(UUID userId) throws Exception {
		String phone = new JdbcTemplate(dataSource).queryForObject("select phone from users where id = ?", String.class, userId);
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

	@TestConfiguration
	static class ClockConfig {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(Instant.parse("2026-10-20T04:00:00Z"));
		}

	}

}
