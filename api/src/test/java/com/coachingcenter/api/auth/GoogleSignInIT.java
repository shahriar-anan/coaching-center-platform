package com.coachingcenter.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.users.User;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class GoogleSignInIT extends PostgresIntegrationTest {

	private static final String REDIRECT = "https://api.symamlaw.com/api/v1/auth/google/callback";

	@DynamicPropertySource
	static void google(DynamicPropertyRegistry registry) {
		registry.add("GOOGLE_CLIENT_ID", () -> "test-client-id");
		registry.add("GOOGLE_CLIENT_SECRET", () -> "test-client-secret");
		registry.add("GOOGLE_REDIRECT_URI", () -> REDIRECT);
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private JwtService jwt;

	@Autowired
	private GoogleAccountService accounts;

	@Autowired
	private GoogleOAuthStateCookie states;

	@MockitoBean
	private GoogleAuthorizationCodeClient codes;

	@MockitoBean
	private GoogleIdentityReader identities;

	@BeforeEach
	void stubGoogle() {
		reset(codes, identities);
		when(codes.exchangeForIdToken(any(), any(), any())).thenReturn("id-token");
		when(identities.read(any(), any())).thenReturn(new GoogleIdentity("unused-subject", "unused@example.com", "Unused", true));
	}

	@Test
	void startRedirectsToGoogleAndStoresState() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/auth/google"))
			.andExpect(status().isFound())
			.andExpect(cookie().exists(GoogleOAuthStateCookie.NAME))
			.andExpect(cookie().httpOnly(GoogleOAuthStateCookie.NAME, true))
			.andExpect(cookie().secure(GoogleOAuthStateCookie.NAME, true))
			.andExpect(cookie().path(GoogleOAuthStateCookie.NAME, "/api/v1/auth/google"))
			.andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("SameSite=Lax")))
			.andExpect(header().string("Cache-Control", "no-store"))
			.andReturn();

		String location = result.getResponse().getHeader("Location");
		assertThat(location).contains("accounts.google.com");
		assertThat(location).contains("code_challenge=");
		assertThat(location).contains("nonce=");
		assertThat(location).contains("state=");
		assertThat(location).contains("client_id=test-client-id");
		assertThat(location).contains("redirect_uri=");
		assertThat(location).contains("api.symamlaw.com");
		assertThat(location).doesNotContain("test-client-secret");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM google_oauth_states", Integer.class)).isPositive();
	}

	@Test
	void newStudentReceivesAWebSessionAndMustSetAPhone() throws Exception {
		String email = uniqueEmail();
		String subject = "sub-" + UUID.randomUUID();
		when(identities.read(any(), any())).thenReturn(new GoogleIdentity(subject, email, "Ada Lovelace", true));

		MvcResult callback = callback(start(null), "auth-code");
		assertThat(callback.getResponse().getRedirectedUrl()).isEqualTo("http://localhost:3000/login/google");
		assertThat(callback.getResponse().getContentAsString()).doesNotContain("refreshToken");
		String refresh = cookieValue(callback, AuthController.REFRESH_COOKIE);

		Integer matches = jdbc.queryForObject("""
				SELECT count(*) FROM users u
				JOIN student_profiles p ON p.user_id = u.id
				WHERE u.email = ? AND u.role = 'STUDENT' AND u.status = 'ACTIVE'
				AND u.phone IS NULL AND u.password_hash IS NULL AND u.google_sub = ?
				AND u.email_verified_at IS NOT NULL AND p.full_name = 'Ada Lovelace'
				""", Integer.class, email, subject);
		assertThat(matches).isEqualTo(1);

		MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh").header("X-Client-Type", "web")
			.cookie(new Cookie(AuthController.REFRESH_COOKIE, refresh)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").exists())
			.andExpect(jsonPath("$.refreshToken").doesNotExist())
			.andReturn();
		String access = json(refreshed, "accessToken");
		mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + access))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.phoneRequired").value(true))
			.andExpect(jsonPath("$.phone").doesNotExist())
			.andExpect(jsonPath("$.role").value("STUDENT"));
	}

	@Test
	void verifiedStudentEmailIsLinkedWithoutChangingRolePhoneOrPassword() throws Exception {
		String phone = uniquePhone();
		String email = uniqueEmail();
		register(phone, email);
		UUID id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
		String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, id);
		String subject = "sub-" + UUID.randomUUID();
		when(identities.read(any(), any())).thenReturn(new GoogleIdentity(subject, email, "Someone Else", true));

		callback(start(null), "auth-code");

		assertThat(jdbc.queryForObject("SELECT google_sub FROM users WHERE id = ?", String.class, id)).isEqualTo(subject);
		assertThat(jdbc.queryForObject("SELECT email_verified_at IS NOT NULL FROM users WHERE id = ?", Boolean.class, id))
			.isTrue();
		assertThat(jdbc.queryForObject("SELECT role FROM users WHERE id = ?", String.class, id)).isEqualTo("STUDENT");
		assertThat(jdbc.queryForObject("SELECT phone FROM users WHERE id = ?", String.class, id)).isEqualTo(phone);
		assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, id)).isEqualTo(hash);
		assertThat(jdbc.queryForObject("SELECT full_name FROM student_profiles WHERE user_id = ?", String.class, id))
			.isEqualTo("Token User");
	}

	@Test
	void returningSubjectSignsInWithoutOverwritingTheLocalEmail() throws Exception {
		String email = uniqueEmail();
		String subject = "sub-" + UUID.randomUUID();
		when(identities.read(any(), any())).thenReturn(new GoogleIdentity(subject, email, "Ada", true));
		callback(start(null), "auth-code");
		when(identities.read(any(), any())).thenReturn(new GoogleIdentity(subject, uniqueEmail(), "Changed", true));

		MvcResult callback = callback(start(null), "auth-code");

		assertThat(callback.getResponse().getRedirectedUrl()).isEqualTo("http://localhost:3000/login/google");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE google_sub = ?", Integer.class, subject)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT email FROM users WHERE google_sub = ?", String.class, subject)).isEqualTo(email);
	}

	@Test
	void staffEmailIsRefused() throws Exception {
		for (String role : new String[] { "MASTER_ADMIN", "SYSTEM_ADMIN", "INSTRUCTOR" }) {
			reset(codes, identities);
			when(codes.exchangeForIdToken(any(), any(), any())).thenReturn("id-token");
			String email = uniqueEmail();
			insertUser(uniquePhone(), email, role, "ACTIVE", null);
			when(identities.read(any(), any())).thenReturn(new GoogleIdentity("sub-" + UUID.randomUUID(), email, "Staff", true));

			MvcResult callback = callback(start(null), "auth-code");

			assertThat(callback.getResponse().getRedirectedUrl()).contains("error=GOOGLE_ROLE_NOT_ALLOWED");
			assertThat(callback.getResponse().getCookie(AuthController.REFRESH_COOKIE)).isNull();
			assertThat(jdbc.queryForObject("SELECT google_sub FROM users WHERE email = ?", String.class, email)).isNull();
			verify(codes, times(1)).exchangeForIdToken(any(), any(), any());
		}
	}

	@Test
	void unverifiedOrMissingEmailDoesNotCreateAnAccount() throws Exception {
		String unverified = uniqueEmail();
		when(identities.read(any(), any())).thenReturn(new GoogleIdentity("sub-" + UUID.randomUUID(), unverified, "Ada", false));
		MvcResult first = callback(start(null), "auth-code");
		assertThat(first.getResponse().getRedirectedUrl()).contains("error=GOOGLE_EMAIL_NOT_VERIFIED");
		assertThat(countUsers(unverified)).isZero();

		when(identities.read(any(), any())).thenReturn(new GoogleIdentity("sub-" + UUID.randomUUID(), " ", "Ada", true));
		MvcResult second = callback(start(null), "auth-code");
		assertThat(second.getResponse().getRedirectedUrl()).contains("error=GOOGLE_EMAIL_MISSING");
	}

	@Test
	void badStateDoesNotCallGoogle() throws Exception {
		mockMvc.perform(get("/api/v1/auth/google/callback").param("code", "auth-code").param("state", "missing"))
			.andExpect(status().isFound())
			.andExpect(header().string("Location", org.hamcrest.Matchers.containsString("error=GOOGLE_STATE_INVALID")));
		verify(codes, never()).exchangeForIdToken(any(), any(), any());

		Started started = start(null);
		mockMvc.perform(get("/api/v1/auth/google/callback").param("code", "auth-code").param("state", "other-state")
			.cookie(new Cookie(GoogleOAuthStateCookie.NAME, started.cookie())))
			.andExpect(header().string("Location", org.hamcrest.Matchers.containsString("error=GOOGLE_STATE_INVALID")));
		verify(codes, never()).exchangeForIdToken(any(), any(), any());
	}

	@Test
	void tamperedStateAndOtherGoogleErrorsDoNotCallGoogle() throws Exception {
		Started started = start(null);
		mockMvc.perform(get("/api/v1/auth/google/callback").param("code", "auth-code").param("state", started.state())
			.cookie(new Cookie(GoogleOAuthStateCookie.NAME, "tampered")))
			.andExpect(status().isFound())
			.andExpect(header().string("Location", org.hamcrest.Matchers.containsString("error=GOOGLE_STATE_INVALID")));
		mockMvc.perform(get("/api/v1/auth/google/callback").param("error", "server_error").param("state", "ignored"))
			.andExpect(status().isFound())
			.andExpect(header().string("Location", org.hamcrest.Matchers.containsString("error=GOOGLE_SIGN_IN_FAILED")));
		verify(codes, never()).exchangeForIdToken(any(), any(), any());
	}

	@Test
	void nonceMismatchFailsAfterTheCodeIsExchanged() throws Exception {
		when(identities.read(any(), any())).thenThrow(new GoogleCallbackException(ErrorCode.GOOGLE_STATE_INVALID));
		MvcResult callback = callback(start(null), "auth-code");
		assertThat(callback.getResponse().getRedirectedUrl()).contains("error=GOOGLE_STATE_INVALID");
		verify(codes, times(1)).exchangeForIdToken(any(), any(), any());
	}

	@Test
	void expiredStateDoesNotCallGoogle() throws Exception {
		String state = "expired-state";
		String cookie = states.write(new GoogleOAuthState(state, "nonce", "verifier", REDIRECT,
				Instant.now().minusSeconds(700).getEpochSecond(), "")).getValue();
		mockMvc.perform(get("/api/v1/auth/google/callback").param("code", "auth-code").param("state", state)
			.cookie(new Cookie(GoogleOAuthStateCookie.NAME, cookie)))
			.andExpect(header().string("Location", org.hamcrest.Matchers.containsString("error=GOOGLE_STATE_INVALID")));
		verify(codes, never()).exchangeForIdToken(any(), any(), any());
	}

	@Test
	void cancelledAndFailedExchangesDoNotCreateAnAccount() throws Exception {
		Started cancelled = start(null);
		mockMvc.perform(get("/api/v1/auth/google/callback").param("error", "access_denied").param("state", cancelled.state())
			.cookie(new Cookie(GoogleOAuthStateCookie.NAME, cancelled.cookie())))
			.andExpect(header().string("Location", org.hamcrest.Matchers.containsString("error=GOOGLE_CANCELLED")));
		verify(codes, never()).exchangeForIdToken(any(), any(), any());

		when(codes.exchangeForIdToken(any(), any(), any())).thenThrow(new GoogleCallbackException(ErrorCode.GOOGLE_SIGN_IN_FAILED));
		MvcResult failed = callback(start(null), "auth-code");
		assertThat(failed.getResponse().getRedirectedUrl()).contains("error=GOOGLE_SIGN_IN_FAILED");
	}

	@Test
	void repeatingTheCallbackIsRejected() throws Exception {
		String email = uniqueEmail();
		when(identities.read(any(), any())).thenReturn(new GoogleIdentity("sub-" + UUID.randomUUID(), email, "Ada", true));
		Started started = start(null);
		callback(started, "auth-code");
		MvcResult repeat = callback(started, "auth-code");
		assertThat(repeat.getResponse().getRedirectedUrl()).contains("error=GOOGLE_STATE_INVALID");
		verify(codes, times(1)).exchangeForIdToken(any(), any(), any());
		assertThat(countUsers(email)).isEqualTo(1);
	}

	@Test
	void aDifferentExistingSubjectIsAConflict() throws Exception {
		String email = uniqueEmail();
		String existing = "already-" + UUID.randomUUID();
		insertUser(uniquePhone(), email, "STUDENT", "ACTIVE", existing);
		when(identities.read(any(), any())).thenReturn(new GoogleIdentity("sub-" + UUID.randomUUID(), email, "Ada", true));
		MvcResult callback = callback(start(null), "auth-code");
		assertThat(callback.getResponse().getRedirectedUrl()).contains("error=GOOGLE_IDENTITY_CONFLICT");
		assertThat(jdbc.queryForObject("SELECT google_sub FROM users WHERE email = ?", String.class, email)).isEqualTo(existing);
	}

	@Test
	void inactiveAndPendingStudentsAreNotSignedIn() throws Exception {
		for (String status : new String[] { "INACTIVE", "PENDING_ACTIVATION" }) {
			String email = uniqueEmail();
			insertUser(uniquePhone(), email, "STUDENT", status, null);
			when(identities.read(any(), any())).thenReturn(new GoogleIdentity("sub-" + UUID.randomUUID(), email, "Ada", true));
			MvcResult callback = callback(start(null), "auth-code");
			assertThat(callback.getResponse().getRedirectedUrl()).contains("error=GOOGLE_ACCOUNT_INACTIVE");
			assertThat(callback.getResponse().getCookie(AuthController.REFRESH_COOKIE)).isNull();
			assertThat(jdbc.queryForObject("SELECT google_sub FROM users WHERE email = ?", String.class, email)).isNull();
		}
	}

	@Test
	void nextIsKeptOnlyWhenItIsARelativePath() throws Exception {
		String email = uniqueEmail();
		when(identities.read(any(), any())).thenReturn(new GoogleIdentity("sub-" + UUID.randomUUID(), email, "Ada", true));
		assertThat(callback(start("https://evil.example"), "auth-code").getResponse().getRedirectedUrl())
			.isEqualTo("http://localhost:3000/login/google");

		when(identities.read(any(), any())).thenReturn(new GoogleIdentity("sub-" + UUID.randomUUID(), uniqueEmail(), "Ada", true));
		assertThat(callback(start("/dashboard"), "auth-code").getResponse().getRedirectedUrl())
			.isEqualTo("http://localhost:3000/login/google?next=/dashboard");

		when(identities.read(any(), any())).thenReturn(new GoogleIdentity("sub-" + UUID.randomUUID(), uniqueEmail(), "Ada", true));
		assertThat(callback(start("/../admin"), "auth-code").getResponse().getRedirectedUrl())
			.isEqualTo("http://localhost:3000/login/google");
	}

	@Test
	void phoneIsRequiredBeforePurchaseOrASittingAndCanBeSetOnce() throws Exception {
		UUID id = insertUser(null, uniqueEmail(), "STUDENT", "ACTIVE", null);
		String access = jwt.issue(id, Role.STUDENT);
		UUID exam = UUID.randomUUID();
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take").header("Authorization", "Bearer " + access))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("PHONE_REQUIRED"));
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/retake").header("Authorization", "Bearer " + access))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("PHONE_REQUIRED"));
		mockMvc.perform(post("/api/v1/live-exam-orders").header("Authorization", "Bearer " + access)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"examId\":\"" + exam + "\"}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("PHONE_REQUIRED"));

		String phone = uniquePhone();
		mockMvc.perform(post("/api/v1/me/phone").header("Authorization", "Bearer " + access)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"phone\":\"" + phone + "\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.phone").value(phone))
			.andExpect(jsonPath("$.phoneRequired").value(false));
		mockMvc.perform(post("/api/v1/me/phone").header("Authorization", "Bearer " + access)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"phone\":\"" + uniquePhone() + "\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PHONE_ALREADY_SET"));

		String taken = uniquePhone();
		insertUser(taken, uniqueEmail(), "STUDENT", "ACTIVE", null);
		UUID other = insertUser(null, uniqueEmail(), "STUDENT", "ACTIVE", null);
		String otherAccess = jwt.issue(other, Role.STUDENT);
		mockMvc.perform(post("/api/v1/me/phone").header("Authorization", "Bearer " + otherAccess)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"phone\":\"" + taken + "\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DUPLICATE_PHONE"));

		MvcResult after = mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take").header("Authorization", "Bearer " + access))
			.andReturn();
		assertThat(after.getResponse().getContentAsString()).doesNotContain("PHONE_REQUIRED");
	}

	@Test
	void simultaneousPhoneClaimsLeaveOneOwner() throws Exception {
		String phone = uniquePhone();
		String firstAccess = jwt.issue(insertUser(null, uniqueEmail(), "STUDENT", "ACTIVE", null), Role.STUDENT);
		String secondAccess = jwt.issue(insertUser(null, uniqueEmail(), "STUDENT", "ACTIVE", null), Role.STUDENT);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch go = new CountDownLatch(1);
		try {
			Future<Integer> left = pool.submit(() -> postPhone(firstAccess, phone, ready, go));
			Future<Integer> right = pool.submit(() -> postPhone(secondAccess, phone, ready, go));
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			go.countDown();
			int firstStatus = left.get(20, TimeUnit.SECONDS);
			int secondStatus = right.get(20, TimeUnit.SECONDS);
			assertThat(java.util.List.of(firstStatus, secondStatus)).contains(200).doesNotContain(500);
			assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE phone = ?", Integer.class, phone)).isEqualTo(1);
		}
		finally {
			pool.shutdownNow();
		}
	}

	@Test
	void concurrentCreatesForOneSubjectResolveToOneUser() throws Exception {
		String email = uniqueEmail();
		String subject = "sub-" + UUID.randomUUID();
		GoogleIdentity identity = new GoogleIdentity(subject, email, "Ada", true);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch go = new CountDownLatch(1);
		try {
			Future<User> first = pool.submit(() -> resolveTogether(identity, ready, go));
			Future<User> second = pool.submit(() -> resolveTogether(identity, ready, go));
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			go.countDown();
			UUID left = first.get(20, TimeUnit.SECONDS).getId();
			UUID right = second.get(20, TimeUnit.SECONDS).getId();
			assertThat(left).isEqualTo(right);
			assertThat(countUsers(email)).isEqualTo(1);
		}
		finally {
			pool.shutdownNow();
		}
	}

	private int postPhone(String access, String phone, CountDownLatch ready, CountDownLatch go) throws Exception {
		ready.countDown();
		assertThat(go.await(10, TimeUnit.SECONDS)).isTrue();
		return mockMvc.perform(post("/api/v1/me/phone").header("Authorization", "Bearer " + access)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"phone\":\"" + phone + "\"}"))
			.andReturn()
			.getResponse()
			.getStatus();
	}

	private User resolveTogether(GoogleIdentity identity, CountDownLatch ready, CountDownLatch go) throws InterruptedException {
		ready.countDown();
		assertThat(go.await(10, TimeUnit.SECONDS)).isTrue();
		return accounts.resolve(identity);
	}

	private Started start(String next) throws Exception {
		var request = get("/api/v1/auth/google");
		if (next != null) {
			request.param("next", next);
		}
		MvcResult result = mockMvc.perform(request).andExpect(status().isFound()).andReturn();
		String location = result.getResponse().getHeader("Location");
		return new Started(query(location, "state"), cookieValue(result, GoogleOAuthStateCookie.NAME));
	}

	private MvcResult callback(Started started, String code) throws Exception {
		return mockMvc.perform(get("/api/v1/auth/google/callback").param("code", code).param("state", started.state())
			.cookie(new Cookie(GoogleOAuthStateCookie.NAME, started.cookie())))
			.andExpect(status().isFound())
			.andReturn();
	}

	private void register(String phone, String email) throws Exception {
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"Token User","phone":"%s","email":"%s","password":"password1"}
					""".formatted(phone, email)))
			.andExpect(status().isCreated());
	}

	private UUID insertUser(String phone, String email, String role, String status, String googleSub) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO users (id, phone, email, role, status, google_sub, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, now(), now())
				""", id, phone, email, role, status, googleSub);
		return id;
	}

	private int countUsers(String email) {
		Integer count = jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, email);
		return count == null ? 0 : count;
	}

	private static String cookieValue(MvcResult result, String name) {
		String prefix = name + "=";
		for (String header : result.getResponse().getHeaders("Set-Cookie")) {
			if (header.startsWith(prefix)) {
				int end = header.indexOf(';');
				return end < 0 ? header.substring(prefix.length()) : header.substring(prefix.length(), end);
			}
		}
		throw new AssertionError("Missing cookie " + name);
	}

	private static String query(String location, String name) {
		String query = URI.create(location).getRawQuery();
		for (String part : query.split("&")) {
			if (part.startsWith(name + "=")) {
				return part.substring(name.length() + 1);
			}
		}
		throw new AssertionError("Missing " + name);
	}

	private static String json(MvcResult result, String field) throws Exception {
		String body = result.getResponse().getContentAsString();
		String marker = "\"" + field + "\":\"";
		int start = body.indexOf(marker) + marker.length();
		return body.substring(start, body.indexOf('"', start));
	}

	private static String uniqueEmail() {
		return "google-" + UUID.randomUUID() + "@example.com";
	}

	private static String uniquePhone() {
		int suffix = java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999);
		return "+8801" + suffix;
	}

	private record Started(String state, String cookie) {
	}

}
