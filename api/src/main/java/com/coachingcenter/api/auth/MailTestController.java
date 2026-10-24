package com.coachingcenter.api.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.users.UserRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Remove or leave disabled after go-live.
 * The endpoint is absent unless {@code MAIL_TEST_ENABLED=true}.
 */
@RestController
@Conditional(MailTestEndpointCondition.class)
public class MailTestController {

	private static final int MAX_SENDS = 5;

	private static final Duration WINDOW = Duration.ofMinutes(15);

	private final SmtpEmailSender mail;

	private final UserRepository users;

	private final ConcurrentHashMap<UUID, Deque<Instant>> sends = new ConcurrentHashMap<>();

	public MailTestController(SmtpEmailSender mail, UserRepository users) {
		this.mail = mail;
		this.users = users;
	}

	@PostMapping("/api/v1/admin/mail-test")
	public MailTestResponse send(@Valid @RequestBody MailTestRequest request) {
		CurrentActor actor = actor();
		if (actor.role() != Role.MASTER_ADMIN && actor.role() != Role.SYSTEM_ADMIN) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
		String to = request.to().trim();
		if (to.indexOf('\r') >= 0 || to.indexOf('\n') >= 0 || to.indexOf(',') >= 0 || to.indexOf(';') >= 0) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
		}
		String own = users.findByIdAndDeletedAtIsNull(actor.userId())
			.map(user -> user.getEmail())
			.orElse("");
		if (own == null || !own.trim().equalsIgnoreCase(to)) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
		}
		if (!allow(actor.userId())) {
			throw new ApiException(ErrorCode.MAIL_TEST_LIMITED, HttpStatus.TOO_MANY_REQUESTS,
					"Too many mail test requests. Try again later.");
		}
		SmtpEmailSender.DeliveryResult result = mail.sendPasswordResetTest(own.trim());
		return new MailTestResponse(result.accepted(), result.providerResponse());
	}

	private boolean allow(UUID userId) {
		Instant now = Instant.now();
		Deque<Instant> recent = sends.computeIfAbsent(userId, ignored -> new ArrayDeque<>());
		synchronized (recent) {
			Instant cutoff = now.minus(WINDOW);
			while (!recent.isEmpty() && recent.peekFirst().isBefore(cutoff)) {
				recent.removeFirst();
			}
			if (recent.size() >= MAX_SENDS) {
				return false;
			}
			recent.addLast(now);
			return true;
		}
	}

	private static CurrentActor actor() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor) {
			return actor;
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

	public record MailTestRequest(@NotBlank @Email String to) {
	}

	public record MailTestResponse(boolean accepted, String providerResponse) {
	}

}
