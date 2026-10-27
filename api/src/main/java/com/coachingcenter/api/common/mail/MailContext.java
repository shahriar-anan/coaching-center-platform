package com.coachingcenter.api.common.mail;

import java.util.List;

import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.common.config.AppProperties;

/**
 * What a mail needs to know about the site: where the student and admin sites live (the first and second {@code WEB_ORIGIN}),
 * and how long each code lives. Links in a mail are always absolute, because a mail is read outside the site.
 */
public record MailContext(String siteUrl, String staffUrl, int activationDays, int resetMinutes, int emailMinutes) {

	public static MailContext from(AppProperties properties) {
		List<String> origins = properties.cors() == null || properties.cors().allowedOrigins() == null ? List.of()
				: properties.cors().allowedOrigins().stream().filter(origin -> origin != null && !origin.isBlank()).map(MailContext::trim).toList();
		String site = origins.isEmpty() ? "http://localhost:3000" : origins.get(0);
		String staff = origins.size() > 1 ? origins.get(1) : site;
		AppProperties.Auth auth = properties.auth();
		return new MailContext(site, staff, auth == null || auth.activationCodeDays() <= 0 ? 7 : auth.activationCodeDays(),
				auth == null || auth.resetCodeMinutes() <= 0 ? 10 : auth.resetCodeMinutes(),
				auth == null || auth.emailCodeMinutes() <= 0 ? 10 : auth.emailCodeMinutes());
	}

	public static MailContext defaults() {
		return new MailContext("https://exam.example", "https://exam.example", 7, 10, 10);
	}

	/** An absolute address on the student site. */
	public String site(String path) {
		return siteUrl + path;
	}

	/** An absolute address on the admin site for staff and instructors, on the student site for everyone else. */
	public String forRole(Role role, String path) {
		return (role == null || role == Role.STUDENT ? siteUrl : staffUrl) + path;
	}

	/** The brand image, served by the student site. A mail links to it; nothing is attached. */
	public String logoUrl() {
		return siteUrl + "/brand/logo-light.png";
	}

	/** The copy icon used on the "copy code" button. */
	public String copyIconUrl() {
		return siteUrl + "/brand/copy-icon.png";
	}

	private static String trim(String origin) {
		String value = origin.trim();
		while (value.endsWith("/")) {
			value = value.substring(0, value.length() - 1);
		}
		return value;
	}

}
