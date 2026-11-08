package com.coachingcenter.api.exams;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The helpers the installment tests share: seeding people, making a published package, enrolling a student with a payment, and
 * reading a plan. Concrete tests add their own {@code @SpringBootTest}, {@code @AutoConfigureMockMvc} and {@code @Import} of
 * {@link ClockConfig}. The clock is fixed at 10:00 in Dhaka on 20 October 2026, so "today" is that day.
 */
abstract class InstallmentTestSupport extends PostgresIntegrationTest {

	static final String TODAY = "2026-10-20";

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected DataSource dataSource;

	@Autowired
	protected PasswordEncoder passwords;

	@Autowired
	protected ObjectMapper json;

	protected JdbcTemplate jdbc() {
		return new JdbcTemplate(dataSource);
	}

	/** One installment as the schedule form sends it. */
	protected static String row(String amount, String dueOn) {
		return "{\"amount\":" + amount + ",\"dueOn\":\"" + dueOn + "\"}";
	}

	/** A payment on a plan, taken on today's date. */
	protected static String paymentBody(String amount, String method, String reference) {
		return "{\"amount\":" + amount + ",\"method\":\"" + method + "\",\"reference\":\"" + reference + "\",\"paidOn\":\"" + TODAY
				+ "\",\"note\":null}";
	}

	/** Enrols one student on a published package with a payment taken today, and returns the grant response. */
	protected JsonNode enrol(String master, UUID student, UUID module, String total, String paid, String reference,
			String... rows) throws Exception {
		String body = "{\"studentId\":\"" + student + "\",\"scope\":\"MODULE\",\"moduleId\":\"" + module
				+ "\",\"examId\":null,\"reason\":\"\",\"payment\":{\"agreedTotal\":" + total + ",\"amount\":" + paid
				+ ",\"method\":\"BKASH\",\"reference\":\"" + reference + "\",\"paidOn\":\"" + TODAY
				+ "\",\"note\":null,\"schedule\":[" + String.join(",", rows) + "]}}";
		MvcResult result = mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body))
			.andExpect(status().isCreated())
			.andReturn();
		return json.readTree(result.getResponse().getContentAsString());
	}

	/** A plan as a Master Admin reads it: the figures, the schedule and the payments. */
	protected JsonNode detail(String token, String planId) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/installment-plans/" + planId).header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn();
		return json.readTree(result.getResponse().getContentAsString());
	}

	/** Records a payment on a plan as the Master Admin, and expects it to be taken. */
	protected void pay(String master, String planId, String amount, String reference) throws Exception {
		mockMvc.perform(post("/api/v1/installment-plans/" + planId + "/payments").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(paymentBody(amount, "BKASH", reference)))
			.andExpect(status().isCreated());
	}

	/** A published package with one category, made by a System Admin. */
	protected UUID module(String token, String title) throws Exception {
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

	protected UUID seed(String role, String name) {
		UUID id = UUID.randomUUID();
		String phone = "+8801" + ThreadLocalRandom.current().nextInt(100000000, 999999999);
		jdbc().update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, phone, "inst-" + id + "@example.com", passwords.encode("password1"), role);
		if ("STUDENT".equals(role)) {
			jdbc().update("INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, ?, ?)", id, name,
					"I" + id.toString().substring(0, 8));
		}
		else {
			jdbc().update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, ?)", id, name);
		}
		return id;
	}

	protected String token(UUID userId) throws Exception {
		String phone = jdbc().queryForObject("select phone from users where id = ?", String.class, userId);
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
	public static class ClockConfig {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(Instant.parse("2026-10-20T04:00:00Z"));
		}

	}

}
