package com.coachingcenter.api.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.common.config.AppProperties;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

	static final String REFRESH_COOKIE = "refresh_token";

	private final AuthService auth;

	private final AppProperties properties;

	public AuthController(AuthService auth, AppProperties properties) {
		this.auth = auth;
		this.properties = properties;
	}

	@PostMapping("/auth/register")
	public ResponseEntity<AuthService.MeView> register(@Valid @RequestBody RegisterRequest request) {
		var user = auth.register(request.fullName(), request.phone(), request.email(), request.password());
		return ResponseEntity.status(HttpStatus.CREATED).body(auth.me(user.getId()));
	}

	@PostMapping("/auth/activate")
	public ResponseEntity<Void> activate(@Valid @RequestBody ActivateRequest request) {
		auth.activate(request.phone(), request.code(), request.password());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/auth/login")
	public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request,
			@RequestHeader(value = "X-Client-Type", required = false) String clientType, HttpServletRequest http) {
		ClientType client = ClientType.fromHeader(clientType);
		AuthService.IssuedTokens tokens = auth.login(request.identifier(), request.password(), client, http.getRemoteAddr(),
				http.getHeader(HttpHeaders.USER_AGENT));
		return tokenResponse(client, tokens);
	}

	@PostMapping("/auth/refresh")
	public ResponseEntity<TokenResponse> refresh(@RequestBody(required = false) RefreshRequest request,
			@RequestHeader(value = "X-Client-Type", required = false) String clientType, HttpServletRequest http) {
		ClientType client = ClientType.fromHeader(clientType);
		AuthService.IssuedTokens tokens = auth.refresh(presentedRefresh(client, request, http), client, http.getRemoteAddr(),
				http.getHeader(HttpHeaders.USER_AGENT));
		return tokenResponse(client, tokens);
	}

	@PostMapping("/auth/logout")
	public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshRequest request,
			@RequestHeader(value = "X-Client-Type", required = false) String clientType, HttpServletRequest http) {
		ClientType client = ClientType.fromHeader(clientType);
		auth.logout(presentedRefresh(client, request, http));
		if (client == ClientType.WEB) {
			return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, clearedCookie().toString()).build();
		}
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/auth/change-password")
	public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
		auth.changePassword(currentUser().userId(), request.currentPassword(), request.newPassword());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/auth/forgot-password")
	public MessageResponse forgotPassword(@Valid @RequestBody EmailRequest request) {
		auth.forgotPassword(request.email());
		return MessageResponse.generic();
	}

	@PostMapping("/auth/reset-password")
	public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
		auth.resetPassword(request.code(), request.password());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/auth/verify-email")
	public ResponseEntity<Void> verifyEmail(@Valid @RequestBody CodeRequest request) {
		auth.verifyEmail(request.code());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/auth/verify-email/resend")
	public MessageResponse resendEmailVerification(@Valid @RequestBody EmailRequest request) {
		auth.resendEmailVerification(request.email());
		return MessageResponse.generic();
	}

	@GetMapping("/me")
	public AuthService.MeView me() {
		return auth.me(currentUser().userId());
	}

	private ResponseEntity<TokenResponse> tokenResponse(ClientType client, AuthService.IssuedTokens tokens) {
		TokenResponse body = new TokenResponse(tokens.accessToken(), tokens.expiresInSeconds(),
				client == ClientType.MOBILE ? tokens.refreshToken() : null);
		if (client == ClientType.WEB) {
			return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, refreshCookie(tokens.refreshToken()).toString()).body(body);
		}
		return ResponseEntity.ok(body);
	}

	private ResponseCookie refreshCookie(String rawToken) {
		return ResponseCookie.from(REFRESH_COOKIE, rawToken)
			.httpOnly(true)
			.secure(true)
			.sameSite("Strict")
			.path("/api/v1/auth")
			.maxAge(properties.auth().refreshTokenDays() * 24L * 60L * 60L)
			.build();
	}

	private ResponseCookie clearedCookie() {
		return ResponseCookie.from(REFRESH_COOKIE, "")
			.httpOnly(true)
			.secure(true)
			.sameSite("Strict")
			.path("/api/v1/auth")
			.maxAge(0)
			.build();
	}

	private String presentedRefresh(ClientType client, RefreshRequest request, HttpServletRequest http) {
		if (client == ClientType.WEB) {
			if (http.getCookies() != null) {
				for (Cookie cookie : http.getCookies()) {
					if (REFRESH_COOKIE.equals(cookie.getName())) {
						return cookie.getValue();
					}
				}
			}
			throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN, HttpStatus.UNAUTHORIZED, "Refresh token is invalid.");
		}
		if (request == null || request.refreshToken() == null || request.refreshToken().isBlank()) {
			throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN, HttpStatus.UNAUTHORIZED, "Refresh token is invalid.");
		}
		return request.refreshToken();
	}

	private CurrentActor currentUser() {
		var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor) {
			return actor;
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

	public record RegisterRequest(@NotBlank String fullName, @NotBlank String phone, @Email @NotBlank String email,
			@NotBlank @Size(min = 8, max = 72) String password) {
	}

	public record ActivateRequest(@NotBlank String phone, @NotBlank String code,
			@NotBlank @Size(min = 8, max = 72) String password) {
	}

	public record LoginRequest(@NotBlank String identifier, @NotBlank String password) {
	}

	public record RefreshRequest(String refreshToken) {
	}

	public record ChangePasswordRequest(@NotBlank String currentPassword,
			@NotBlank @Size(min = 8, max = 72) String newPassword) {
	}

	public record EmailRequest(@Email @NotBlank String email) {
	}

	public record ResetPasswordRequest(@NotBlank String code, @NotBlank @Size(min = 8, max = 72) String password) {
	}

	public record CodeRequest(@NotBlank String code) {
	}

	public record TokenResponse(String accessToken, int expiresInSeconds, String refreshToken) {
	}

	public record MessageResponse(String message) {

		static MessageResponse generic() {
			return new MessageResponse("If an account exists for that email, a message has been sent.");
		}

	}

}
