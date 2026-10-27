package com.coachingcenter.api.common.mail;

import com.coachingcenter.api.auth.Role;

/** Who a mail is for: the address, the name to greet, and the role, which decides which site a link opens. Name and role may be unknown. */
public record MailRecipient(String email, String name, Role role) {

	public static MailRecipient of(String email) {
		return new MailRecipient(email, null, null);
	}

	/** The first word of the name, or {@code null}, for a warm greeting. */
	public String firstName() {
		if (name == null || name.isBlank()) {
			return null;
		}
		return name.trim().split("\\s+")[0];
	}

}
