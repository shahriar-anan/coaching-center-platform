package com.coachingcenter.api.auth;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.common.config.AppProperties;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.users.User;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/auth/google")
public class GoogleSignInController {

	private static final Logger log = LoggerFactory.getLogger(GoogleSignInController.class);

	private final GoogleOAuthClient google;

	private final GoogleAuthorizationRequests requests;

	private final GoogleOAuthStateCookie states;

	private final GoogleOAuthStateStore stateStore;

	private final GoogleAuthorizationCodeClient codes;

	private final GoogleIdentityReader identities;

	private final GoogleAccountService accounts;

	private final AuthService auth;

	private final AppProperties properties;

	public GoogleSignInController(GoogleOAuthClient google, GoogleAuthorizationRequests requests,
			GoogleOAuthStateCookie states, GoogleOAuthStateStore stateStore, GoogleAuthorizationCodeClient codes,
			GoogleIdentityReader identities, GoogleAccountService accounts, AuthService auth, AppProperties properties) {
		this.google = google;
		this.requests = requests;
		this.states = states;
		this.stateStore = stateStore;
		this.codes = codes;
		this.identities = identities;
		this.accounts = accounts;
		this.auth = auth;
		this.properties = properties;
	}

	@GetMapping
	public ResponseEntity<Void> start(@RequestParam(name = "next", required = false) String next) {
		if (!google.enabled()) {
			throw new ApiException(ErrorCode.GOOGLE_SIGN_IN_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
					"Google sign-in is unavailable.");
		}
		GoogleAuthorizationRequests.Started started = requests.start(safeNext(next));
		stateStore.save(started.state().state(),
				Instant.ofEpochSecond(started.state().issuedAtEpochSecond()).plusSeconds(GoogleOAuthStateCookie.MAX_AGE_SECONDS));
		return ResponseEntity.status(HttpStatus.FOUND)
			.header(HttpHeaders.CACHE_CONTROL, "no-store")
			.header(HttpHeaders.SET_COOKIE, states.write(started.state()).toString())
			.location(URI.create(started.authorizationUri()))
			.build();
	}

	@GetMapping("/callback")
	public ResponseEntity<Void> callback(@RequestParam(name = "code", required = false) String code,
			@RequestParam(name = "state", required = false) String state,
			@RequestParam(name = "error", required = false) String error, HttpServletRequest http) {
		if (!google.enabled() || origin() == null) {
			throw new ApiException(ErrorCode.GOOGLE_SIGN_IN_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
					"Google sign-in is unavailable.");
		}
		if (error != null && !error.isBlank()) {
			consumeIfPresent(state, http);
			return fail("access_denied".equals(error) ? ErrorCode.GOOGLE_CANCELLED : ErrorCode.GOOGLE_SIGN_IN_FAILED);
		}
		GoogleOAuthState saved = states.read(http);
		if (saved == null || !same(saved.state(), state) || !stateStore.consume(saved.state())) {
			return fail(ErrorCode.GOOGLE_STATE_INVALID);
		}
		try {
			String idToken = codes.exchangeForIdToken(code, saved.verifier(), saved.redirectUri());
			GoogleIdentity identity = identities.read(idToken, saved.nonce());
			User user = accounts.resolve(identity);
			AuthService.IssuedTokens issued = auth.issueWebSession(user.getId(), http.getRemoteAddr(),
					http.getHeader(HttpHeaders.USER_AGENT));
			HttpHeaders headers = new HttpHeaders();
			headers.setCacheControl("no-store");
			headers.add(HttpHeaders.SET_COOKIE, states.clear().toString());
			headers.add(HttpHeaders.SET_COOKIE,
					RefreshCookies.issue(issued.refreshToken(), properties.auth().refreshTokenDays() * 24L * 60L * 60L)
						.toString());
			headers.setLocation(successLocation(saved.next()));
			return new ResponseEntity<>(headers, HttpStatus.FOUND);
		}
		catch (GoogleCallbackException exception) {
			return fail(exception.code());
		}
		catch (ApiException exception) {
			if (exception.code() == ErrorCode.GOOGLE_ROLE_NOT_ALLOWED
					|| exception.code() == ErrorCode.GOOGLE_ACCOUNT_INACTIVE) {
				return fail(exception.code());
			}
			throw exception;
		}
		catch (RuntimeException exception) {
			log.warn("Google sign-in failed: {}", exception.getClass().getSimpleName());
			return fail(ErrorCode.GOOGLE_SIGN_IN_FAILED);
		}
	}

	private ResponseEntity<Void> fail(ErrorCode code) {
		HttpHeaders headers = new HttpHeaders();
		headers.setCacheControl("no-store");
		headers.add(HttpHeaders.SET_COOKIE, states.clear().toString());
		headers.setLocation(URI.create(origin() + google.postLoginPath() + "?error=" + code.name()));
		return new ResponseEntity<>(headers, HttpStatus.FOUND);
	}

	private URI successLocation(String next) {
		String target = origin() + google.postLoginPath();
		if (next != null && !next.isBlank()) {
			target = target + "?next=" + next;
		}
		return URI.create(target);
	}

	private String origin() {
		if (properties.cors() == null || properties.cors().allowedOrigins() == null
				|| properties.cors().allowedOrigins().isEmpty()) {
			return null;
		}
		String origin = properties.cors().allowedOrigins().get(0);
		if (origin == null || origin.isBlank()) {
			return null;
		}
		String trimmed = origin.trim();
		if (trimmed.endsWith("/")) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed;
	}

	private void consumeIfPresent(String state, HttpServletRequest http) {
		GoogleOAuthState saved = states.read(http);
		if (saved != null && same(saved.state(), state)) {
			stateStore.consume(saved.state());
		}
	}

	static String safeNext(String next) {
		if (next == null || next.isBlank()) {
			return "";
		}
		if (!next.matches("^/[A-Za-z0-9._~/-]{1,200}$") || next.startsWith("//") || next.contains("\\")
				|| next.contains("..")) {
			return "";
		}
		return next;
	}

	private static boolean same(String left, String right) {
		if (left == null || right == null) {
			return false;
		}
		return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
	}

}
