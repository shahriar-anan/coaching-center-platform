package com.coachingcenter.api.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.coachingcenter.api.common.config.AppProperties;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class MailSendLimiter {

	private final int perEmail;

	private final int perIp;

	private final Duration cooldown;

	private final Clock clock;

	private final ConcurrentHashMap<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

	private final ConcurrentHashMap<String, Instant> lastSent = new ConcurrentHashMap<>();

	private final Object lock = new Object();

	@Autowired
	public MailSendLimiter(AppProperties properties) {
		this(limits(properties), Clock.systemUTC());
	}

	MailSendLimiter(Limits limits, Clock clock) {
		this.perEmail = limits.perEmail();
		this.perIp = limits.perIp();
		this.cooldown = Duration.ofSeconds(Math.max(0, limits.cooldownSeconds()));
		this.clock = clock;
	}

	public void acquire(String email, String ip, boolean enforceCooldown) {
		String address = email == null ? "" : email.trim().toLowerCase();
		String client = ip == null || ip.isBlank() ? "unknown" : ip.trim();
		Instant now = clock.instant();
		synchronized (lock) {
			if (enforceCooldown && !cooldown.isZero()) {
				Instant previous = lastSent.get(address);
				if (previous != null && previous.plus(cooldown).isAfter(now)) {
					throw limited();
				}
			}
			if (!allow("e:" + address, perEmail, now) || !allow("i:" + client, perIp, now)) {
				throw limited();
			}
			lastSent.put(address, now);
		}
	}

	public static String clientAddress(HttpServletRequest request) {
		String remote = request.getRemoteAddr();
		if (remote == null || remote.isBlank()) {
			return "unknown";
		}
		if (!loopback(remote)) {
			return remote;
		}
		String realIp = request.getHeader("X-Real-IP");
		if (realIp == null || realIp.isBlank() || realIp.indexOf(',') >= 0) {
			return remote;
		}
		return realIp.trim();
	}

	private static boolean loopback(String remote) {
		return "127.0.0.1".equals(remote) || "::1".equals(remote) || "0:0:0:0:0:0:0:1".equals(remote);
	}

	private boolean allow(String key, int limit, Instant now) {
		Deque<Instant> recent = hits.computeIfAbsent(key, ignored -> new ArrayDeque<>());
		Instant cutoff = now.minus(Duration.ofHours(1));
		while (!recent.isEmpty() && !recent.peekFirst().isAfter(cutoff)) {
			recent.removeFirst();
		}
		if (recent.size() >= limit) {
			return false;
		}
		recent.addLast(now);
		return true;
	}

	private static Limits limits(AppProperties properties) {
		AppProperties.Mail mail = properties.mail();
		if (mail == null) {
			return new Limits(3, 10, 60);
		}
		return new Limits(mail.sendsPerEmailPerHour(), mail.sendsPerIpPerHour(), mail.resendCooldownSeconds());
	}

	private static ApiException limited() {
		return new ApiException(ErrorCode.EMAIL_RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS,
				"Too many requests. Try again later.");
	}

	record Limits(int perEmail, int perIp, int cooldownSeconds) {
	}

}
