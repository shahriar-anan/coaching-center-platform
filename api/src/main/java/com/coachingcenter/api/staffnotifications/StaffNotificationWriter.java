package com.coachingcenter.api.staffnotifications;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

/**
 * Writes staff notifications. Later pay phases call {@link #notify}; nothing else writes this table.
 *
 * <p>
 * A row goes to staff only: a recipient who is not an active staff member is skipped without an error, as the student
 * notices are. A repeated {@code dedupeKey} adds nothing (insert or ignore), so a reminder job cannot repeat itself. The
 * insert joins the caller's transaction when there is one.
 *
 * <p>
 * The payload is checked before it is written: flat values only, no key that names an account, and no string of nine or
 * more digits, so an account or wallet number cannot reach a notification by mistake.
 */
@Service
public class StaffNotificationWriter {

	static final int MAX_LINK = 300;

	static final int MAX_DEDUPE_KEY = 200;

	static final int MAX_TEXT = 300;

	/** A link is a path inside the staff site. Anything else (another host, a scheme, a protocol-relative link) is refused. */
	private static final Pattern STAFF_LINK = Pattern.compile("^/staff[A-Za-z0-9/_.?=&%-]*$");

	/** Nine or more digits in a row: a bank account, a routing number or a mobile number. */
	private static final Pattern LONG_DIGITS = Pattern.compile("\\d{9,}");

	/** Key fragments that name a payout account field. A payload key containing one is refused. */
	private static final String[] SENSITIVE_KEYS = { "account", "bank", "routing", "wallet", "iban", "card", "password" };

	private static final String INSERT = """
			insert into staff_notifications (id, recipient_id, kind, payload, link, created_at, dedupe_key)
			select ?, u.id, ?, cast(? as jsonb), ?, ?, ?
			from users u
			where u.id = ? and u.role in ('MASTER_ADMIN', 'SYSTEM_ADMIN', 'INSTRUCTOR')
			  and u.status = 'ACTIVE' and u.deleted_at is null
			on conflict (dedupe_key) do nothing
			""";

	private final JdbcTemplate jdbc;

	private final ObjectMapper json;

	private final Clock clock;

	public StaffNotificationWriter(JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
		this.jdbc = jdbc;
		this.json = json;
		this.clock = clock;
	}

	/**
	 * Fits free text taken from a record (an exam title, a person's name) to the payload rules, so it can never make
	 * {@link #notify} refuse the row: any run of nine or more digits keeps its first four and drops the rest, and text
	 * longer than {@link #MAX_TEXT} is cut with an ellipsis. Exam titles allow 500 characters, more than a payload holds.
	 * Callers whose notice runs inside a money transaction (the pay jobs) pass titles and names through this, so one odd
	 * title cannot roll back the ledger lines it was written with.
	 */
	public static String fitText(String text) {
		if (text == null) {
			return null;
		}
		String masked = LONG_DIGITS.matcher(text)
			.replaceAll(match -> java.util.regex.Matcher.quoteReplacement(match.group().substring(0, 4) + "…"));
		return masked.length() <= MAX_TEXT ? masked : masked.substring(0, MAX_TEXT - 1) + "…";
	}

	/**
	 * Adds one notification for one staff member. {@code payload} holds the values the row's text needs, as described on
	 * {@link StaffNotificationKind}. {@code link} is a staff page. {@code dedupeKey} may be null; when set it must be unique
	 * across the table, so include the recipient in it. Returns true when a row was written.
	 *
	 * @throws IllegalArgumentException when the link, payload or key breaks the rules above (a programming error)
	 */
	@Transactional
	public boolean notify(UUID recipientId, StaffNotificationKind kind, Map<String, ?> payload, String link, String dedupeKey) {
		if (recipientId == null || kind == null) {
			throw new IllegalArgumentException("A staff notification needs a recipient and a kind.");
		}
		checkLink(link);
		checkDedupeKey(dedupeKey);
		Map<String, Object> values = checkPayload(payload);
		String payloadJson = json.writeValueAsString(values);
		return jdbc.update(INSERT, UUID.randomUUID(), kind.name(), payloadJson, link, Timestamp.from(clock.instant()),
				dedupeKey, recipientId) == 1;
	}

	/** A staff page path: starts with {@code /staff}, uses plain path characters, and is at most 300 characters. */
	static void checkLink(String link) {
		if (link == null || link.length() > MAX_LINK || !STAFF_LINK.matcher(link).matches()) {
			throw new IllegalArgumentException("A staff notification links to a staff page only.");
		}
	}

	static void checkDedupeKey(String dedupeKey) {
		if (dedupeKey != null && (dedupeKey.isBlank() || dedupeKey.length() > MAX_DEDUPE_KEY)) {
			throw new IllegalArgumentException("A dedupe key is 1 to 200 characters.");
		}
	}

	/**
	 * A flat copy of the payload in a fixed order. Values are text, numbers, booleans or null. A key that names an account
	 * is refused, and so is a text value with nine or more digits in a row.
	 */
	static Map<String, Object> checkPayload(Map<String, ?> payload) {
		Map<String, Object> copy = new LinkedHashMap<>();
		if (payload == null) {
			return copy;
		}
		for (Map.Entry<String, ?> entry : payload.entrySet()) {
			String key = entry.getKey();
			if (key == null || key.isBlank()) {
				throw new IllegalArgumentException("A payload key cannot be blank.");
			}
			String lower = key.toLowerCase(Locale.ROOT);
			for (String fragment : SENSITIVE_KEYS) {
				if (lower.contains(fragment)) {
					throw new IllegalArgumentException("A staff notification never carries account details.");
				}
			}
			Object value = entry.getValue();
			if (value instanceof String text) {
				if (text.length() > MAX_TEXT || LONG_DIGITS.matcher(text).find()) {
					throw new IllegalArgumentException("A payload text is short and carries no long number.");
				}
			}
			else if (value != null && !(value instanceof Number) && !(value instanceof Boolean)) {
				throw new IllegalArgumentException("A payload value is text, a number, a boolean or null.");
			}
			copy.put(key, value);
		}
		return copy;
	}

}
