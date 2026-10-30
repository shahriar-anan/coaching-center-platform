package com.coachingcenter.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import javax.sql.DataSource;

import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.coachingcenter.api.PostgresIntegrationTest;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class PasswordIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CapturingEmailSender emails;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping mappings;

	@Test
	void forgotPasswordResponseDoesNotRevealWhetherTheEmailExists() throws Exception {
		String email = uniqueEmail();
		register(uniquePhone(), email);
		MvcResult existing = forgot(email);
		MvcResult missing = forgot("missing-" + email);
		assertThat(existing.getResponse().getContentAsString()).isEqualTo(missing.getResponse().getContentAsString());
		assertThat(emails.latestReset(email)).isNotBlank();
		assertThat(emails.latestReset("missing-" + email)).isNull();
	}

	@Test
	void resetPasswordRevokesRefreshTokens() throws Exception {
		String phone = uniquePhone();
		String email = uniqueEmail();
		register(phone, email);
		String refresh = json(login(phone), "refreshToken");
		String code = emails.latestReset(email);
		if (code == null) {
			forgot(email);
			code = emails.latestReset(email);
		}
		mockMvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"code":"%s","password":"newpassword1"}
					""".formatted(code)))
			.andExpect(status().isNoContent());
		mockMvc.perform(post("/api/v1/auth/refresh").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"refreshToken\":\"" + refresh + "\"}"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"identifier":"%s","password":"newpassword1"}
					""".formatted(phone)))
			.andExpect(status().isOk());
	}

	@Test
	void changePasswordRequiresTheCurrentPassword() throws Exception {
		String phone = uniquePhone();
		String access = json(login(registerAndPhone(phone)), "accessToken");
		mockMvc.perform(post("/api/v1/auth/change-password").header("Authorization", "Bearer " + access)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"currentPassword":"wrong-password","newPassword":"newpassword1"}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"));
		mockMvc.perform(post("/api/v1/auth/change-password").header("Authorization", "Bearer " + access)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"currentPassword":"password1","newPassword":"newpassword1"}
					"""))
			.andExpect(status().isNoContent());
	}

	@Test
	void changePasswordKeepsThisSessionAndEndsTheOthers() throws Exception {
		String phone = registerAndPhone(uniquePhone());
		MvcResult here = webLogin(phone, "password1");
		MvcResult elsewhere = webLogin(phone, "password1");
		Cookie hereCookie = here.getResponse().getCookie(AuthController.REFRESH_COOKIE);

		mockMvc.perform(post("/api/v1/auth/change-password").header("Authorization", "Bearer " + json(here, "accessToken"))
			.cookie(hereCookie)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"currentPassword":"password1","newPassword":"newpassword1"}
					"""))
			.andExpect(status().isNoContent());

		webRefresh(hereCookie).andExpect(status().isOk());
		webRefresh(elsewhere.getResponse().getCookie(AuthController.REFRESH_COOKIE)).andExpect(status().isUnauthorized());
		webLoginAttempt(phone, "password1").andExpect(status().isUnauthorized());
		webLoginAttempt(phone, "newpassword1").andExpect(status().isOk());
		Integer audited = new JdbcTemplate(dataSource).queryForObject("""
				SELECT count(*) FROM audit_log a JOIN users u ON u.id = a.entity_id
				WHERE a.action = 'PASSWORD_CHANGED' AND u.phone = ?
				""", Integer.class, phone);
		assertThat(audited).isEqualTo(1);
	}

	@Test
	void changePasswordWithoutTheCookieEndsEverySession() throws Exception {
		String phone = registerAndPhone(uniquePhone());
		MvcResult web = webLogin(phone, "password1");
		String mobileAccess = json(login(phone), "accessToken");

		mockMvc.perform(post("/api/v1/auth/change-password").header("Authorization", "Bearer " + mobileAccess)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"currentPassword":"password1","newPassword":"newpassword1"}
					"""))
			.andExpect(status().isNoContent());

		webRefresh(web.getResponse().getCookie(AuthController.REFRESH_COOKIE)).andExpect(status().isUnauthorized());
	}

	@Test
	void adminSurfaceDoesNotAcceptOrReturnAPassword() throws Exception {
		String access = masterAccess();
		mockMvc.perform(post("/api/v1/students").header("Authorization", "Bearer " + access)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"No Password","phone":"%s","email":"%s","password":"password1"}
					""".formatted(uniquePhone(), uniqueEmail())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("PASSWORD_NOT_ACCEPTED"))
			.andExpect(jsonPath("$.password").doesNotExist());
		mockMvc.perform(post("/api/v1/admins").header("Authorization", "Bearer " + access)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"No Password","phone":"%s","email":"%s","password":"password1"}
					""".formatted(uniquePhone(), uniqueEmail())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("PASSWORD_NOT_ACCEPTED"));
		assertThat(mappings).isNotNull();
	}

	private String masterAccess() throws Exception {
		String phone = uniquePhone();
		String email = uniqueEmail();
		new JdbcTemplate(dataSource).update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, 'MASTER_ADMIN', 'ACTIVE', now(), now())
				""", java.util.UUID.randomUUID(), phone, email, passwords.encode("password1"));
		return json(login(phone), "accessToken");
	}

	private String registerAndPhone(String phone) throws Exception {
		register(phone, uniqueEmail());
		return phone;
	}

	private void register(String phone, String email) throws Exception {
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"Password User","phone":"%s","email":"%s","password":"password1"}
					""".formatted(phone, email)))
			.andExpect(status().isCreated());
	}

	private MvcResult forgot(String email) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\"}")).andExpect(status().isOk()).andReturn();
	}

	private MvcResult login(String phone) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"identifier":"%s","password":"password1"}
					""".formatted(phone)))
			.andExpect(status().isOk())
			.andReturn();
	}

	private MvcResult webLogin(String phone, String password) throws Exception {
		return webLoginAttempt(phone, password).andExpect(status().isOk()).andReturn();
	}

	private org.springframework.test.web.servlet.ResultActions webLoginAttempt(String phone, String password)
			throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "web")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"identifier":"%s","password":"%s"}
					""".formatted(phone, password)));
	}

	private org.springframework.test.web.servlet.ResultActions webRefresh(Cookie cookie) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/refresh").header("X-Client-Type", "web")
			.cookie(cookie)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"));
	}

	private static String json(MvcResult result, String field) throws Exception {
		String body = result.getResponse().getContentAsString();
		String marker = "\"" + field + "\":\"";
		int start = body.indexOf(marker) + marker.length();
		return body.substring(start, body.indexOf('"', start));
	}

	private static String uniquePhone() {
		int suffix = java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999);
		return "+8801" + suffix;
	}

	private static String uniqueEmail() {
		return "user-" + UUID() + "@example.com";
	}

	private static String UUID() {
		return java.util.UUID.randomUUID().toString();
	}

}
