package com.coachingcenter.api.files;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

/**
 * Stores an uploaded PDF through {@link LocalFileStorage}. The file's name and declared type are not trusted: the first
 * bytes must be {@code %PDF-}. The caller commits the row and then calls {@link LocalFileStorage#commitReplacement}.
 */
@Component
public class PdfUploads {

	private static final byte[] MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

	private final LocalFileStorage files;

	public PdfUploads(LocalFileStorage files) {
		this.files = files;
	}

	public LocalFileStorage.StoredFile store(MultipartFile file, long maxBytes) {
		if (file == null || file.isEmpty()) {
			throw new ApiException(ErrorCode.FILE_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE, "File is too large.");
		}
		if (file.getSize() > maxBytes) {
			throw new ApiException(ErrorCode.FILE_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE, "File is too large.");
		}
		byte[] bytes;
		try {
			bytes = file.getBytes();
		}
		catch (IOException exception) {
			throw validationFailed();
		}
		if (bytes.length > maxBytes) {
			throw new ApiException(ErrorCode.FILE_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE, "File is too large.");
		}
		if (!startsWithMagic(bytes)) {
			throw validationFailed();
		}
		return files.save(file.getOriginalFilename(), "application/pdf", new ByteArrayInputStream(bytes), bytes.length, maxBytes);
	}

	private static boolean startsWithMagic(byte[] bytes) {
		if (bytes.length < MAGIC.length) {
			return false;
		}
		for (int i = 0; i < MAGIC.length; i++) {
			if (bytes[i] != MAGIC[i]) {
				return false;
			}
		}
		return true;
	}

	private static ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
	}

}
