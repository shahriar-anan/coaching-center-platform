package com.coachingcenter.api.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.coachingcenter.api.common.config.AppProperties;

@Component
public class LoginLockout {

	private final int maxFailures;

	private final Duration window;

	private final ConcurrentHashMap<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

	public LoginLockout(AppProperties properties) {
		this.maxFailures = properties.auth().lockout().maxFailures();
		this.window = Duration.ofMinutes(properties.auth().lockout().windowMinutes());
	}

	public boolean isLocked(String identifier, String ip) {
		return recent(key(identifier, ip)).size() >= maxFailures;
	}

	public void recordFailure(String identifier, String ip) {
		recent(key(identifier, ip)).addLast(Instant.now());
	}

	private Deque<Instant> recent(String key) {
		Deque<Instant> attempts = failures.computeIfAbsent(key, ignored -> new ArrayDeque<>());
		Instant cutoff = Instant.now().minus(window);
		synchronized (attempts) {
			while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) {
				attempts.removeFirst();
			}
			return attempts;
		}
	}

	private static String key(String identifier, String ip) {
		return identifier + "|" + ip;
	}

}
