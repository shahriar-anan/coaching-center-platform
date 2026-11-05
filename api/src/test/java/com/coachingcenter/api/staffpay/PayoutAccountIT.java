package com.coachingcenter.api.staffpay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
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
import com.coachingcenter.api.common.audit.AuditRecorder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Payout accounts (staff pay plan, phase 2): the owner's own routes, the preferred account, the limit, the Master Admin's
 * notice without a number, the audit row that holds the account id only, and the refusals for anyone else.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class PayoutAccountIT extends PostgresIntegrationTest {

	private static final String BANK_NUMBER = "1234567890";

	private static final String ROUTING = "200262123";

	private static final String WALLET = "01712345678";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Autowired
	private PayoutAccountService accounts;

	private JdbcTemplate jdbc;

	@BeforeEach
	void setUp() {
		jdbc = new JdbcTemplate(dataSource);
	}

	@Test
	void theOwnerAddsABankAndAWalletTheFirstIsPreferredAndTheListShowsBothInFull() throws Exception {
		UUID admin = seed("SYSTEM_ADMIN");
		String token = token(admin);

		JsonNode bank = json.readTree(mockMvc.perform(post("/api/v1/me/payout-accounts").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON).content(bankBody("Rahim Uddin", BANK_NUMBER)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.preferred").value(true))
			.andExpect(jsonPath("$.maskedNumber").value("•••• 7890"))
			.andReturn().getResponse().getContentAsString());
		assertThat(bank.get("accountNumber").asText()).isEqualTo(BANK_NUMBER);

		mockMvc.perform(post("/api/v1/me/payout-accounts").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"bkash\",\"walletNumber\":\"" + WALLET + "\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.kind").value("BKASH"))
			.andExpect(jsonPath("$.walletNumber").value("+8801712345678"))
			.andExpect(jsonPath("$.preferred").value(false));

		MvcResult list = mockMvc.perform(get("/api/v1/me/payout-accounts").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode rows = json.readTree(list.getResponse().getContentAsString());
		assertThat(rows.size()).isEqualTo(2);
		assertThat(rows.get(0).get("kind").asText()).isEqualTo("BANK");
		assertThat(rows.get(0).get("preferred").asBoolean()).isTrue();
		assertThat(rows.get(0).get("routingNumber").asText()).isEqualTo(ROUTING);
	}

	@Test
	void makingAnotherPreferredAndRemovingThePreferredOnePromotesTheEarliest() throws Exception {
		UUID instructor = seed("INSTRUCTOR");
		String token = token(instructor);
		UUID bank = add(token, bankBody("Shahana Begum", BANK_NUMBER));
		UUID bkash = add(token, "{\"kind\":\"BKASH\",\"walletNumber\":\"" + WALLET + "\"}");

		mockMvc.perform(post("/api/v1/me/payout-accounts/" + bkash + "/preferred").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.preferred").value(true));
		assertThat(preferred(bank)).isFalse();
		assertThat(preferred(bkash)).isTrue();

		mockMvc.perform(delete("/api/v1/me/payout-accounts/" + bkash).header("Authorization", "Bearer " + token))
			.andExpect(status().isNoContent());
		assertThat(preferred(bank)).isTrue();
		assertThat(jdbc.queryForObject("select deleted_at is not null from staff_payout_accounts where id = ?", Boolean.class,
				bkash)).isTrue();

		mockMvc.perform(get("/api/v1/me/payout-accounts").header("Authorization", "Bearer " + token))
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].id").value(bank.toString()));
	}

	@Test
	void editingAnAccountChangesItsNumberAndKeepsItsPreferredFlag() throws Exception {
		UUID instructor = seed("INSTRUCTOR");
		String token = token(instructor);
		UUID bank = add(token, bankBody("Shahana Begum", BANK_NUMBER));

		mockMvc.perform(put("/api/v1/me/payout-accounts/" + bank).header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON).content(bankBody("Shahana Begum", "9876543210")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accountNumber").value("9876543210"))
			.andExpect(jsonPath("$.preferred").value(true));
	}

	@Test
	void everyChangeNotifiesTheMasterAdminWithoutAnyAccountNumber() throws Exception {
		UUID master = seed("MASTER_ADMIN");
		UUID instructor = seed("INSTRUCTOR");
		String token = token(instructor);

		UUID bank = add(token, bankBody("Shahana Begum", BANK_NUMBER));
		add(token, "{\"kind\":\"BKASH\",\"walletNumber\":\"" + WALLET + "\"}");
		mockMvc.perform(put("/api/v1/me/payout-accounts/" + bank).header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON).content(bankBody("Shahana Begum", "9876543210")))
			.andExpect(status().isOk());
		mockMvc.perform(delete("/api/v1/me/payout-accounts/" + bank).header("Authorization", "Bearer " + token))
			.andExpect(status().isNoContent());

		assertThat(count("select count(*) from staff_notifications where recipient_id = ? and kind = 'PAYOUT_ACCOUNT_CHANGED'",
				master)).isEqualTo(4);
		String payloads = jdbc.queryForObject(
				"select string_agg(payload::text, ' ') from staff_notifications where recipient_id = ?", String.class, master);
		assertThat(payloads).contains("Staff INSTRUCTOR").contains("ADDED").contains("UPDATED").contains("REMOVED");
		for (String secret : new String[] { BANK_NUMBER, "9876543210", ROUTING, WALLET, "+8801712345678" }) {
			assertThat(payloads).doesNotContain(secret);
		}
		assertThat(count("select count(*) from staff_notifications where recipient_id = ? and kind = 'PAYOUT_ACCOUNT_CHANGED'"
				+ " and link = '/staff/instructors'", master)).isEqualTo(4);
	}

	@Test
	void theAuditLogHoldsTheAccountIdOnlyAndTheOwnerAsActor() throws Exception {
		UUID admin = seed("SYSTEM_ADMIN");
		String token = token(admin);
		UUID account = add(token, bankBody("Rahim Uddin", BANK_NUMBER));

		assertThat(count("""
				select count(*) from audit_log
				where action = ? and entity_type = ? and entity_id = ? and actor_user_id = ?
				""", AuditRecorder.PAYOUT_ACCOUNT_ADDED, AuditRecorder.PAYOUT_ACCOUNT_ENTITY, account, admin)).isEqualTo(1);
		assertThat(count("select count(*) from audit_log where action like 'PAYOUT_ACCOUNT_%' and entity_id = ?", account))
			.isEqualTo(1);
	}

	@Test
	void theMasterAdminCannotWriteAnyPayoutAccountAndAStudentIsRefused() throws Exception {
		UUID master = seed("MASTER_ADMIN");
		UUID instructor = seed("INSTRUCTOR");
		UUID account = add(token(instructor), bankBody("Shahana Begum", BANK_NUMBER));
		String masterToken = token(master);

		mockMvc.perform(post("/api/v1/me/payout-accounts").header("Authorization", "Bearer " + masterToken)
			.contentType(MediaType.APPLICATION_JSON).content(bankBody("Master", "5555555555")))
			.andExpect(status().isForbidden());
		mockMvc.perform(put("/api/v1/me/payout-accounts/" + account).header("Authorization", "Bearer " + masterToken)
			.contentType(MediaType.APPLICATION_JSON).content(bankBody("Master", "5555555555")))
			.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/v1/me/payout-accounts/" + account).header("Authorization", "Bearer " + masterToken))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/me/payout-accounts/" + account + "/preferred").header("Authorization", "Bearer " + masterToken)
			.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/me/payout-accounts").header("Authorization", "Bearer " + masterToken))
			.andExpect(status().isForbidden());

		UUID student = seed("STUDENT");
		mockMvc.perform(get("/api/v1/me/payout-accounts").header("Authorization", "Bearer " + token(student)))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/me/payout-accounts")).andExpect(status().isUnauthorized());

		assertThat(count("select count(*) from staff_payout_accounts where user_id = ?", master)).isZero();
		assertThat(count("select count(*) from staff_payout_accounts where id = ? and account_number = ?", account, BANK_NUMBER))
			.isEqualTo(1);
	}

	@Test
	void anotherPersonsAccountIsNotFoundToTheOwnerAndTheMasterAdminReadsItThroughTheService() throws Exception {
		UUID owner = seed("INSTRUCTOR");
		UUID other = seed("INSTRUCTOR");
		UUID account = add(token(owner), bankBody("Shahana Begum", BANK_NUMBER));
		String otherToken = token(other);

		mockMvc.perform(put("/api/v1/me/payout-accounts/" + account).header("Authorization", "Bearer " + otherToken)
			.contentType(MediaType.APPLICATION_JSON).content(bankBody("Other", "5555555555")))
			.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/v1/me/payout-accounts/" + account).header("Authorization", "Bearer " + otherToken))
			.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/me/payout-accounts/" + account + "/preferred").header("Authorization", "Bearer " + otherToken)
			.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isNotFound());
		assertThat(count("select count(*) from staff_payout_accounts where id = ? and account_number = ?", account, BANK_NUMBER))
			.isEqualTo(1);

		assertThat(accounts.list(owner)).hasSize(1);
		assertThat(accounts.list(owner).get(0).accountNumber()).isEqualTo(BANK_NUMBER);
		assertThat(accounts.list(other)).isEmpty();
	}

	@Test
	void aPersonKeepsAtMostFiveLiveAccountsAndABadEntryIsRefusedWithoutAWrite() throws Exception {
		UUID instructor = seed("INSTRUCTOR");
		String token = token(instructor);
		for (int i = 0; i < 5; i++) {
			add(token, "{\"kind\":\"NAGAD\",\"walletNumber\":\"0171234567" + i + "\"}");
		}
		mockMvc.perform(post("/api/v1/me/payout-accounts").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"ROCKET\",\"walletNumber\":\"01812345678\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PAYOUT_ACCOUNT_LIMIT"));

		UUID other = seed("SYSTEM_ADMIN");
		String otherToken = token(other);
		mockMvc.perform(post("/api/v1/me/payout-accounts").header("Authorization", "Bearer " + otherToken)
			.contentType(MediaType.APPLICATION_JSON).content(bankBody("Rahim", "12345")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		mockMvc.perform(post("/api/v1/me/payout-accounts").header("Authorization", "Bearer " + otherToken)
			.contentType(MediaType.APPLICATION_JSON).content(bankBody("Rahim", BANK_NUMBER).replace(ROUTING, "2002621")))
			.andExpect(status().isBadRequest());

		assertThat(count("select count(*) from staff_payout_accounts where user_id = ?", instructor)).isEqualTo(5);
		assertThat(count("select count(*) from staff_payout_accounts where user_id = ?", other)).isZero();
	}

	private UUID add(String token, String body) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/me/payout-accounts").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private boolean preferred(UUID id) {
		return Boolean.TRUE.equals(jdbc.queryForObject("select preferred from staff_payout_accounts where id = ?", Boolean.class, id));
	}

	private static String bankBody(String name, String number) {
		return """
				{"kind":"BANK","accountName":"%s","accountNumber":"%s","bankName":"Sonali Bank","branch":"Dhanmondi","routingNumber":"%s"}
				""".formatted(name, number, ROUTING);
	}

	private long count(String sql, Object... args) {
		Long total = jdbc.queryForObject(sql, Long.class, args);
		return total == null ? 0 : total;
	}

	private UUID seed(String role) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, uniquePhone(), "payout-" + id + "@example.com", passwords.encode("password1"), role);
		if ("STUDENT".equals(role)) {
			jdbc.update("INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, 'Seed', ?)", id,
					"S" + id.toString().substring(0, 8));
		}
		else {
			jdbc.update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, ?)", id, "Staff " + role);
		}
		return id;
	}

	private String token(UUID userId) throws Exception {
		String phone = jdbc.queryForObject("select phone from users where id = ?", String.class, userId);
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content(json.writeValueAsString(Map.of("identifier", phone, "password", "password1"))))
			.andExpect(status().isOk())
			.andReturn();
		return json.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
	}

	private static String uniquePhone() {
		int suffix = java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999);
		return "+8801" + suffix;
	}

}
