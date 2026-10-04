package com.coachingcenter.api.courses;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class YouTubeUrls {

	private static final Pattern WATCH = Pattern.compile("[?&]v=([A-Za-z0-9_-]{11})");

	private static final Pattern SHORT = Pattern.compile("youtu\\.be/([A-Za-z0-9_-]{11})");

	private static final Pattern EMBED = Pattern.compile("youtube\\.com/embed/([A-Za-z0-9_-]{11})");

	private YouTubeUrls() {
	}

	static String parseVideoId(String url) {
		if (url == null || url.isBlank()) {
			return null;
		}
		String value = url.trim();
		Matcher embed = EMBED.matcher(value);
		if (embed.find()) {
			return embed.group(1);
		}
		Matcher shortLink = SHORT.matcher(value);
		if (shortLink.find()) {
			return shortLink.group(1);
		}
		Matcher watch = WATCH.matcher(value);
		if (watch.find()) {
			return watch.group(1);
		}
		return null;
	}

}
