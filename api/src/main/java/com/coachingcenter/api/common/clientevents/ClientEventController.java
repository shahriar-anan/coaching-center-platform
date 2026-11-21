package com.coachingcenter.api.common.clientevents;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.MailSendLimiter;

import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.JsonNode;

/**
 * Anonymous reports of failures that happen only in the visitor's browser, written to the log as one line each. Added
 * 22 Nov 2026, when PDFs never opened on older phones and the only trace was a retry pattern in the nginx log.
 * Nothing is stored. The body carries no user data (the web sends a kind, a stage, a file kind and the error text); the
 * user agent comes from the request. Open to anyone, so each address may send a few per hour, and every value is
 * checked against a short list or cut short before it reaches the log.
 */
@RestController
public class ClientEventController {

	private static final Logger log = LoggerFactory.getLogger(ClientEventController.class);

	static final int PER_ADDRESS_PER_HOUR = 20;

	private static final int MAX_ADDRESSES = 5_000;

	private static final Set<String> KINDS = Set.of("pdf");

	private static final Set<String> STAGES = Set.of("download", "open", "render");

	private static final Set<String> FILES = Set.of("question", "answer", "marked", "routine", "other");

	/** Anything that could break a log line or forge another one. */
	private static final Pattern UNSAFE = Pattern.compile("[\\p{Cntrl}\"\\\\]");

	private final Clock clock;

	private final Map<String, Deque<Instant>> hits = new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Deque<Instant>> eldest) {
			return size() > MAX_ADDRESSES;
		}
	};

	public ClientEventController(Clock clock) {
		this.clock = clock;
	}

	@PostMapping("/api/v1/public/client-events")
	public ResponseEntity<Void> report(@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
		// Always 204: the page never waits on this, and a refusal tells a sender nothing useful.
		if (body == null || !body.isObject() || !allow(MailSendLimiter.clientAddress(request))) {
			return ResponseEntity.noContent().build();
		}
		String kind = pick(body, "kind", KINDS);
		String stage = pick(body, "stage", STAGES);
		if (kind == null || stage == null) {
			return ResponseEntity.noContent().build();
		}
		String file = pick(body, "file", FILES);
		log.warn("client_event kind={} stage={} file={} message=\"{}\" ua=\"{}\"", kind, stage, file == null ? "other" : file,
				clean(text(body, "message"), 300), clean(request.getHeader("User-Agent"), 300));
		return ResponseEntity.noContent().build();
	}

	private synchronized boolean allow(String address) {
		Instant now = clock.instant();
		Deque<Instant> recent = hits.computeIfAbsent(address, ignored -> new ArrayDeque<>());
		Instant cutoff = now.minus(Duration.ofHours(1));
		while (!recent.isEmpty() && !recent.peekFirst().isAfter(cutoff)) {
			recent.removeFirst();
		}
		if (recent.size() >= PER_ADDRESS_PER_HOUR) {
			return false;
		}
		recent.addLast(now);
		return true;
	}

	private static String pick(JsonNode body, String field, Set<String> allowed) {
		String value = text(body, field);
		return value != null && allowed.contains(value) ? value : null;
	}

	private static String text(JsonNode body, String field) {
		JsonNode value = body.get(field);
		return value != null && value.isString() ? value.asString() : null;
	}

	static String clean(String value, int max) {
		if (value == null) {
			return "";
		}
		String safe = UNSAFE.matcher(value).replaceAll(" ").strip();
		return safe.length() > max ? safe.substring(0, max) : safe;
	}

}
