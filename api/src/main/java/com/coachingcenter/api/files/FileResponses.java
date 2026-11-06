package com.coachingcenter.api.files;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Builds the response for a stored file. The name goes in {@code filename*=UTF-8''...} (RFC 5987), so Bangla file names
 * survive. Every response is sent with {@code X-Content-Type-Options: nosniff}.
 */
public final class FileResponses {

	private static final char[] HEX = "0123456789ABCDEF".toCharArray();

	private FileResponses() {
	}

	/** The file shown in the browser (for example in a dialog or an image tag). */
	public static ResponseEntity<InputStreamResource> inline(InputStream body, String contentType, long byteSize,
			String fileName, String cacheControl) {
		return respond("inline", body, contentType, byteSize, fileName, cacheControl);
	}

	/** The file saved to disk by the browser. */
	public static ResponseEntity<InputStreamResource> attachment(InputStream body, String contentType, long byteSize,
			String fileName, String cacheControl) {
		return respond("attachment", body, contentType, byteSize, fileName, cacheControl);
	}

	private static ResponseEntity<InputStreamResource> respond(String disposition, InputStream body, String contentType,
			long byteSize, String fileName, String cacheControl) {
		ResponseEntity.BodyBuilder response = ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename*=UTF-8''" + encodeRfc5987(fileName))
			.header("X-Content-Type-Options", "nosniff")
			.contentType(MediaType.parseMediaType(contentType))
			.contentLength(byteSize);
		if (cacheControl != null && !cacheControl.isBlank()) {
			response.header(HttpHeaders.CACHE_CONTROL, cacheControl);
		}
		return response.body(new InputStreamResource(body));
	}

	/** Percent-encodes every byte of the UTF-8 name except the RFC 5987 attr-char set. A blank name becomes "file". */
	static String encodeRfc5987(String name) {
		String value = name == null || name.isBlank() ? "file" : name;
		StringBuilder encoded = new StringBuilder();
		for (byte raw : value.getBytes(StandardCharsets.UTF_8)) {
			int c = raw & 0xFF;
			if (isAttrChar(c)) {
				encoded.append((char) c);
			}
			else {
				encoded.append('%').append(HEX[c >> 4]).append(HEX[c & 0x0F]);
			}
		}
		return encoded.toString();
	}

	private static boolean isAttrChar(int c) {
		if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
			return true;
		}
		return "!#$&+-.^_`|~".indexOf(c) >= 0;
	}

}
