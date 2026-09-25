package com.coachingcenter.api.auth;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.config.AppProperties;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.users.StudentProfile;
import com.coachingcenter.api.users.StudentProfileRepository;
import com.coachingcenter.api.users.User;
import com.coachingcenter.api.users.UserRepository;

@Service
public class AuthService {

	private static final String ACTIVE = "ACTIVE";

	private static final String PENDING = "PENDING_ACTIVATION";

	private static final String ACTIVATION = "ACTIVATION";

	private static final String PASSWORD_RESET = "PASSWORD_RESET";

	private static final String EMAIL_VERIFY = "EMAIL_VERIFY";

	private static final String INVALID_CREDENTIALS = "Invalid credentials.";

	private final UserRepository users;

	private final StudentProfileRepository profiles;

	private final RefreshTokenRepository refreshTokens;

	private final OneTimeCodeRepository codes;

	private final PasswordEncoder passwords;

	private final SecretHasher hasher;

	private final JwtService jwt;

	private final EmailSender email;

	private final LoginLockout lockout;

	private final AppProperties properties;

	private final RefreshTokenRevocation revocations;

	private final SecureRandom random = new SecureRandom();

	public AuthService(UserRepository users, StudentProfileRepository profiles, RefreshTokenRepository refreshTokens,
			OneTimeCodeRepository codes, PasswordEncoder passwords, SecretHasher hasher, JwtService jwt,
			EmailSender email, LoginLockout lockout, AppProperties properties, RefreshTokenRevocation revocations) {
		this.users = users;
		this.profiles = profiles;
		this.refreshTokens = refreshTokens;
		this.codes = codes;
		this.passwords = passwords;
		this.hasher = hasher;
		this.jwt = jwt;
		this.email = email;
		this.lockout = lockout;
		this.properties = properties;
		this.revocations = revocations;
	}

	@Transactional
	public User register(String fullName, String phone, String emailAddress, String password) {
		String normalizedPhone = PhoneNumbers.normalize(phone);
		String normalizedEmail = normalizeEmail(emailAddress);
		if (users.findByPhoneAndDeletedAtIsNull(normalizedPhone).isPresent()) {
			throw new ApiException(ErrorCode.DUPLICATE_PHONE, HttpStatus.CONFLICT, "Phone number is already in use.");
		}
		if (users.findByEmailAndDeletedAtIsNull(normalizedEmail).isPresent()) {
			throw new ApiException(ErrorCode.DUPLICATE_EMAIL, HttpStatus.CONFLICT, "Email is already in use.");
		}
		User user = new User();
		user.setPhone(normalizedPhone);
		user.setEmail(normalizedEmail);
		user.setPasswordHash(passwords.encode(password));
		user.setRole(Role.STUDENT.name());
		user.setStatus(ACTIVE);
		user = users.save(user);
		StudentProfile profile = new StudentProfile();
		profile.setUserId(user.getId());
		profile.setFullName(fullName.trim());
		profile.setStudentCode(nextStudentCode());
		profiles.save(profile);
		issueCode(user, EMAIL_VERIFY, null, Instant.now().plusSeconds(properties.auth().emailCodeMinutes() * 60L));
		return user;
	}

	@Transactional
	public String issueActivationCode(User user, UUID createdBy) {
		return issueCode(user, ACTIVATION, createdBy,
				Instant.now().plusSeconds(properties.auth().activationCodeDays() * 24L * 60L * 60L));
	}

	@Transactional
	public void activate(String phone, String code, String password) {
		User user = users.findByPhoneAndDeletedAtIsNull(PhoneNumbers.normalize(phone))
			.orElseThrow(this::invalidCode);
		OneTimeCode match = codes.findFirstByUserIdAndPurposeAndCodeHashOrderByCreatedAtDesc(user.getId(), ACTIVATION,
				hasher.sha256(code)).orElse(null);
		if (match == null) {
			recordFailedCode(user.getId(), ACTIVATION);
			throw invalidCode();
		}
		acceptCode(match);
		user.setPasswordHash(passwords.encode(password));
		user.setStatus(ACTIVE);
	}

	@Transactional
	public IssuedTokens login(String identifier, String password, ClientType clientType, String ip, String userAgent) {
		String key = identifier.trim().toLowerCase();
		if (lockout.isLocked(key, ip)) {
			throw new ApiException(ErrorCode.LOGIN_LOCKED, HttpStatus.TOO_MANY_REQUESTS, INVALID_CREDENTIALS);
		}
		User user = findForLogin(identifier);
		if (user == null || user.getPasswordHash() == null || !ACTIVE.equals(user.getStatus())
				|| !passwords.matches(password, user.getPasswordHash())) {
			lockout.recordFailure(key, ip);
			throw new ApiException(ErrorCode.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS);
		}
		user.setLastLoginAt(Instant.now());
		return issueTokens(user, clientType, ip, userAgent, UUID.randomUUID());
	}

	@Transactional
	public IssuedTokens refresh(String rawToken, ClientType clientType, String ip, String userAgent) {
		RefreshToken current = refreshTokens.findByTokenHash(hasher.sha256(rawToken)).orElseThrow(this::invalidRefresh);
		if (current.getReplacedBy() != null) {
			revocations.revokeFamily(current.getFamilyId());
			throw invalidRefresh();
		}
		if (current.getRevokedAt() != null || current.getExpiresAt().isBefore(Instant.now())) {
			throw invalidRefresh();
		}
		User user = users.findByIdAndDeletedAtIsNull(current.getUserId()).orElseThrow(this::invalidRefresh);
		if (!ACTIVE.equals(user.getStatus())) {
			throw invalidRefresh();
		}
		IssuedTokens issued = issueTokens(user, clientType, ip, userAgent, current.getFamilyId());
		current.setRevokedAt(Instant.now());
		current.setReplacedBy(issued.refreshTokenId());
		return issued;
	}

	@Transactional
	public void logout(String rawToken) {
		if (rawToken == null || rawToken.isBlank()) {
			throw invalidRefresh();
		}
		RefreshToken current = refreshTokens.findByTokenHash(hasher.sha256(rawToken)).orElseThrow(this::invalidRefresh);
		if (current.getRevokedAt() == null) {
			current.setRevokedAt(Instant.now());
		}
	}

	@Transactional
	public void changePassword(UUID userId, String currentPassword, String newPassword) {
		User user = users.findByIdAndDeletedAtIsNull(userId).orElseThrow(this::unauthenticated);
		if (user.getPasswordHash() == null || !passwords.matches(currentPassword, user.getPasswordHash())) {
			throw new ApiException(ErrorCode.CURRENT_PASSWORD_INVALID, HttpStatus.BAD_REQUEST,
					"Current password is incorrect.");
		}
		user.setPasswordHash(passwords.encode(newPassword));
	}

	@Transactional
	public void forgotPassword(String emailAddress) {
		users.findByEmailAndDeletedAtIsNull(normalizeEmail(emailAddress)).ifPresent(user -> {
			String raw = issueCode(user, PASSWORD_RESET, null,
					Instant.now().plusSeconds(properties.auth().resetCodeMinutes() * 60L));
			email.sendPasswordReset(user.getEmail(), raw);
		});
	}

	@Transactional
	public void resetPassword(String code, String newPassword) {
		OneTimeCode match = codes.findFirstByPurposeAndCodeHashOrderByCreatedAtDesc(PASSWORD_RESET, hasher.sha256(code))
			.orElseThrow(this::invalidCode);
		acceptCode(match);
		User user = users.findByIdAndDeletedAtIsNull(match.getUserId()).orElseThrow(this::invalidCode);
		user.setPasswordHash(passwords.encode(newPassword));
		refreshTokens.revokeAllForUser(user.getId(), Instant.now());
	}

	@Transactional
	public void verifyEmail(String code) {
		OneTimeCode match = codes.findFirstByPurposeAndCodeHashOrderByCreatedAtDesc(EMAIL_VERIFY, hasher.sha256(code))
			.orElseThrow(this::invalidCode);
		acceptCode(match);
		User user = users.findByIdAndDeletedAtIsNull(match.getUserId()).orElseThrow(this::invalidCode);
		user.setEmailVerifiedAt(Instant.now());
	}

	@Transactional
	public void resendEmailVerification(String emailAddress) {
		users.findByEmailAndDeletedAtIsNull(normalizeEmail(emailAddress)).ifPresent(user -> {
			if (user.getEmailVerifiedAt() != null) {
				return;
			}
			String raw = issueCode(user, EMAIL_VERIFY, null,
					Instant.now().plusSeconds(properties.auth().emailCodeMinutes() * 60L));
			email.sendEmailVerification(user.getEmail(), raw);
		});
	}

	@Transactional(readOnly = true)
	public MeView me(UUID userId) {
		User user = users.findByIdAndDeletedAtIsNull(userId).orElseThrow(this::unauthenticated);
		StudentProfile profile = profiles.findById(userId).orElse(null);
		return new MeView(user.getId(), user.getPhone(), user.getEmail(), user.getEmailVerifiedAt(), user.getRole(),
				user.getStatus(), profile == null ? null : profile.getFullName(),
				profile == null ? null : profile.getStudentCode());
	}

	private IssuedTokens issueTokens(User user, ClientType clientType, String ip, String userAgent, UUID familyId) {
		String rawRefresh = randomToken();
		RefreshToken token = new RefreshToken();
		token.setUserId(user.getId());
		token.setTokenHash(hasher.sha256(rawRefresh));
		token.setFamilyId(familyId);
		token.setClientType(clientType.name());
		token.setExpiresAt(Instant.now().plusSeconds(properties.auth().refreshTokenDays() * 24L * 60L * 60L));
		token.setCreatedAt(Instant.now());
		token.setIp(ip);
		token.setUserAgent(userAgent);
		token = refreshTokens.save(token);
		String access = jwt.issue(user.getId(), Role.valueOf(user.getRole()));
		return new IssuedTokens(access, jwt.accessTokenSeconds(), rawRefresh, token.getId());
	}

	private String issueCode(User user, String purpose, UUID createdBy, Instant expiresAt) {
		codes.findFirstByUserIdAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(user.getId(), purpose).ifPresent(existing -> {
			existing.setUsedAt(Instant.now());
		});
		String raw = randomCode();
		OneTimeCode code = new OneTimeCode();
		code.setUserId(user.getId());
		code.setPurpose(purpose);
		code.setCodeHash(hasher.sha256(raw));
		code.setExpiresAt(expiresAt);
		code.setFailedAttempts(0);
		code.setCreatedBy(createdBy);
		code.setCreatedAt(Instant.now());
		codes.save(code);
		if (EMAIL_VERIFY.equals(purpose)) {
			email.sendEmailVerification(user.getEmail(), raw);
		}
		return raw;
	}

	private void acceptCode(OneTimeCode code) {
		if (code.getUsedAt() != null) {
			throw new ApiException(ErrorCode.CODE_ALREADY_USED, HttpStatus.BAD_REQUEST, "Code is no longer valid.");
		}
		if (code.getFailedAttempts() >= properties.auth().codeMaxAttempts()) {
			throw new ApiException(ErrorCode.CODE_ATTEMPTS_EXCEEDED, HttpStatus.BAD_REQUEST, "Code is no longer valid.");
		}
		if (code.getExpiresAt().isBefore(Instant.now())) {
			throw new ApiException(ErrorCode.CODE_EXPIRED, HttpStatus.BAD_REQUEST, "Code is no longer valid.");
		}
		code.setUsedAt(Instant.now());
	}

	private void recordFailedCode(UUID userId, String purpose) {
		codes.findFirstByUserIdAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(userId, purpose).ifPresent(code -> {
			int attempts = code.getFailedAttempts() + 1;
			code.setFailedAttempts(attempts);
			if (attempts >= properties.auth().codeMaxAttempts()) {
				code.setUsedAt(Instant.now());
			}
		});
	}

	private User findForLogin(String identifier) {
		String trimmed = identifier.trim();
		if (trimmed.contains("@")) {
			return users.findByEmailAndDeletedAtIsNull(normalizeEmail(trimmed)).orElse(null);
		}
		try {
			return users.findByPhoneAndDeletedAtIsNull(PhoneNumbers.normalize(trimmed)).orElse(null);
		}
		catch (ApiException exception) {
			return null;
		}
	}

	private String nextStudentCode() {
		for (int attempt = 0; attempt < 5; attempt++) {
			String code = "S" + (10000000 + random.nextInt(90000000));
			if (!profiles.existsByStudentCode(code)) {
				return code;
			}
		}
		throw new IllegalStateException("Could not allocate a student code.");
	}

	private String randomCode() {
		char[] alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
		char[] value = new char[8];
		for (int index = 0; index < value.length; index++) {
			value[index] = alphabet[random.nextInt(alphabet.length)];
		}
		return new String(value);
	}

	private String randomToken() {
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		return java.util.HexFormat.of().formatHex(bytes);
	}

	private static String normalizeEmail(String email) {
		return email.trim().toLowerCase();
	}

	private ApiException invalidCode() {
		return new ApiException(ErrorCode.INVALID_CODE, HttpStatus.BAD_REQUEST, "Code is no longer valid.");
	}

	private ApiException invalidRefresh() {
		return new ApiException(ErrorCode.INVALID_REFRESH_TOKEN, HttpStatus.UNAUTHORIZED, "Refresh token is invalid.");
	}

	private ApiException unauthenticated() {
		return new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

	public record IssuedTokens(String accessToken, int expiresInSeconds, String refreshToken, UUID refreshTokenId) {
	}

	public record MeView(UUID id, String phone, String email, Instant emailVerifiedAt, String role, String status,
			String fullName, String studentCode) {
	}

}
