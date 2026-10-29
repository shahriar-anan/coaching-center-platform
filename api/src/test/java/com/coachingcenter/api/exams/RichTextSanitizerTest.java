package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The rich text rules of R3: plain text is stored as typed, and HTML keeps only the tags and attributes the editor offers. */
class RichTextSanitizerTest {

	private final RichTextSanitizer sanitizer = new RichTextSanitizer();

	@Test
	void plainTextPassesThroughUnchanged() {
		assertThat(sanitizer.clean("Line one\nLine two & more, a < b")).isEqualTo("Line one\nLine two & more, a < b");
		assertThat(sanitizer.clean("")).isEmpty();
		assertThat(sanitizer.clean(null)).isNull();
	}

	@Test
	void allowedTagsStay() {
		String html = "<h2>Head</h2><p>Para <strong>bold</strong> <em>it</em> <u>under</u><br /></p><ul><li>one</li></ul>"
				+ "<ol><li>two</li></ol>";
		assertThat(sanitizer.clean(html)).isEqualTo(html);
	}

	@Test
	void scriptsAndEventHandlersAreRemoved() {
		String cleaned = sanitizer.clean("<p onclick=\"x()\">Hi</p><script>evil()</script><img src=\"x\" onerror=\"y()\">");
		assertThat(cleaned).contains("<p>Hi</p>");
		assertThat(cleaned).doesNotContain("onclick").doesNotContain("<script").doesNotContain("onerror")
			.doesNotContain("<img");
	}

	@Test
	void onlyHttpHttpsAndMailtoLinksKeepTheirHref() {
		String cleaned = sanitizer.clean("<a href=\"javascript:evil()\">bad</a><a href=\"https://example.com/a\">ok</a>"
				+ "<a href=\"mailto:someone@example.com\">mail</a>");
		assertThat(cleaned).doesNotContain("javascript:");
		assertThat(cleaned).contains("href=\"https://example.com/a\"").contains("href=\"mailto:someone&#64;example.com\"");
		assertThat(cleaned).contains("rel=\"nofollow\"");
	}

	@Test
	void onlyTextAlignmentIsKeptFromStyle() {
		assertThat(sanitizer.clean("<p style=\"text-align: center\">Mid</p>")).contains("text-align: center");
		assertThat(sanitizer.clean("<p style=\"color: red\">Red</p>")).doesNotContain("style").doesNotContain("color");
	}

}
