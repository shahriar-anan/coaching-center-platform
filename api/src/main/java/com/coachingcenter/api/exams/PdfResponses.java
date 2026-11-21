package com.coachingcenter.api.exams;

import java.nio.file.Path;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.coachingcenter.api.files.PdfViewCopies;

/**
 * A stored PDF as a response, streamed from disk rather than read into memory. Spring sets Content-Length (the viewer
 * shows download progress from it) and answers Range requests. {@code X-Pdf-Copy} says whether the smaller viewing copy
 * or the original was sent, for checking from the browser's network panel.
 */
final class PdfResponses {

	private PdfResponses() {
	}

	/** The builder for {@code path}, with the type and the copy header set; add any other headers, then call {@link #body}. */
	static ResponseEntity.BodyBuilder ok(Path path) {
		return ResponseEntity.ok()
			.contentType(MediaType.APPLICATION_PDF)
			.header("X-Pdf-Copy", PdfViewCopies.isCopy(path) ? "view" : "original");
	}

	static ResponseEntity<Resource> body(ResponseEntity.BodyBuilder builder, Path path) {
		return builder.body(new FileSystemResource(path));
	}

	/** The file to send: the original when asked for (Download), otherwise the viewing copy when one is ready. */
	static Path choose(PdfViewCopies copies, Path original, boolean wantOriginal) {
		return wantOriginal ? original : copies.forViewing(original);
	}

}
