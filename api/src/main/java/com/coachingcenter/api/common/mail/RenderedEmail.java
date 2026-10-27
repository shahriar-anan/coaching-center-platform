package com.coachingcenter.api.common.mail;

/** A finished mail: the subject, the HTML for mail programs that show it, and the plain text for those that do not. */
public record RenderedEmail(String subject, String html, String text) {
}
