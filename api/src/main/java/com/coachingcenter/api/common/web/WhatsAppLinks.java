package com.coachingcenter.api.common.web;

import java.util.regex.Pattern;

/**
 * The WhatsApp group or channel link of a batch or a package (15 Nov). Only an invite link to a group
 * ({@code https://chat.whatsapp.com/...}) or a channel ({@code https://whatsapp.com/channel/...}) is taken, so a link
 * cannot point a student anywhere else. Blank means no link.
 */
public final class WhatsAppLinks {

	/** The longest link kept (the column length). */
	public static final int MAX_LENGTH = 500;

	private static final Pattern GROUP = Pattern.compile("^https://chat\\.whatsapp\\.com/[A-Za-z0-9]{8,}(\\?\\S*)?$");

	private static final Pattern CHANNEL = Pattern.compile("^https://(www\\.)?whatsapp\\.com/channel/[A-Za-z0-9]{8,}(/)?(\\?\\S*)?$");

	/** The message a refused link gets. */
	public static final String INVALID = "Paste a WhatsApp group invite link (https://chat.whatsapp.com/...) or a channel link "
			+ "(https://whatsapp.com/channel/...).";

	private WhatsAppLinks() {
	}

	/** The link trimmed, or null when it is blank (the link is removed). */
	public static String clean(String raw) {
		if (raw == null) {
			return null;
		}
		String value = raw.trim();
		return value.isEmpty() ? null : value;
	}

	/** Null when the cleaned link is acceptable (or absent); otherwise the reason it is refused. */
	public static String problem(String cleaned) {
		if (cleaned == null) {
			return null;
		}
		if (cleaned.length() > MAX_LENGTH) {
			return "The link is longer than " + MAX_LENGTH + " characters.";
		}
		return GROUP.matcher(cleaned).matches() || CHANNEL.matcher(cleaned).matches() ? null : INVALID;
	}

}
