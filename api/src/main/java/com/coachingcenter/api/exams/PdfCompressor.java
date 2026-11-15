package com.coachingcenter.api.exams;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.error.FieldErrorDetail;

/**
 * Makes an uploaded PDF smaller by compressing its page content streams. Compressing only saves space: a PDF it cannot rewrite
 * (encrypted, unusual, slightly damaged) is kept exactly as it was sent, because a student's answer script must never be refused
 * just because it could not be made smaller (written-upload incident, 15 Nov 2026).
 */
@Component
public class PdfCompressor {

	private static final Logger log = LoggerFactory.getLogger(PdfCompressor.class);

	/** How far into the file the "%PDF" header may start. Readers allow bytes before it; phone scanner apps sometimes add some. */
	static final int HEADER_WINDOW = 1024;

	/**
	 * Whether the bytes are a PDF: a "%PDF" header within the first kilobyte, the same rule the browser checks before upload. A file
	 * the page accepts must not be refused here for the header alone.
	 */
	public static boolean looksLikePdf(byte[] bytes) {
		if (bytes == null) {
			return false;
		}
		int end = Math.min(bytes.length, HEADER_WINDOW) - 4;
		for (int start = 0; start <= end; start++) {
			if (bytes[start] == '%' && bytes[start + 1] == 'P' && bytes[start + 2] == 'D' && bytes[start + 3] == 'F') {
				return true;
			}
		}
		return false;
	}

	/**
	 * The PDF with its page contents compressed, or the bytes unchanged when they cannot be rewritten. The one refusal is a PDF that
	 * needs a password to open: nobody could read it, so the uploader is told now, while they can still send another.
	 */
	public byte[] compress(byte[] pdf) {
		try (PDDocument document = Loader.loadPDF(pdf); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			// A PDF locked only by an owner password (common from scanner apps) opens without a password but cannot be saved while
			// encrypted. Its pages are readable by anyone, so the lock is dropped from the copy that is stored.
			if (document.isEncrypted()) {
				document.setAllSecurityToBeRemoved(true);
			}
			for (PDPage page : document.getPages()) {
				try (InputStream contents = page.getContents()) {
					if (contents == null) {
						continue;
					}
					byte[] raw = contents.readAllBytes();
					PDStream compressed = new PDStream(document);
					try (OutputStream encoded = compressed.createOutputStream(COSName.FLATE_DECODE)) {
						encoded.write(raw);
					}
					page.setContents(compressed);
				}
			}
			document.save(output);
			byte[] smaller = output.toByteArray();
			return smaller.length < pdf.length ? smaller : pdf;
		}
		catch (InvalidPasswordException exception) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.",
					java.util.List.of(new FieldErrorDetail("file", "This PDF needs a password to open. Save it again without a password.")));
		}
		catch (IOException | RuntimeException exception) {
			log.warn("pdf kept as uploaded, not compressed bytes={} error={} message={}", pdf.length, exception.getClass().getSimpleName(),
					exception.getMessage());
			return pdf;
		}
	}

}
