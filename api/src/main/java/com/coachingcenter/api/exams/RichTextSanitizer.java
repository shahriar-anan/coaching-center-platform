package com.coachingcenter.api.exams;

import java.util.regex.Pattern;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;

/**
 * Cleans the rich text of an exam or package description and rules before it is saved (R3). Only the tags the editor
 * offers stay. A value with no tags is plain text, and it is stored as it was typed, line breaks included.
 */
@Component
public class RichTextSanitizer {

	private static final Pattern HAS_TAG = Pattern.compile("</?[a-zA-Z][^<>]*>");

	private static final Pattern TEXT_ALIGN = Pattern.compile("text-align:\\s*(left|right|center|justify);?",
			Pattern.CASE_INSENSITIVE);

	private static final PolicyFactory POLICY = new HtmlPolicyBuilder()
		.allowElements("p", "br", "h2", "h3", "strong", "em", "u", "ul", "ol", "li", "a")
		.allowUrlProtocols("http", "https", "mailto")
		.allowAttributes("href").onElements("a")
		.allowAttributes("style").matching(TEXT_ALIGN).onElements("p", "h2", "h3")
		.requireRelNofollowOnLinks()
		.toFactory();

	/** The value as it is stored: sanitized when it has tags, unchanged when it is plain text. */
	public String clean(String value) {
		if (value == null || !HAS_TAG.matcher(value).find()) {
			return value;
		}
		return POLICY.sanitize(value);
	}

}
