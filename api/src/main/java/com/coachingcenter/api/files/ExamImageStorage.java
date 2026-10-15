package com.coachingcenter.api.files;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

@Component
public class ExamImageStorage {

	private static final java.util.Set<String> ALLOWED_TYPES = java.util.Set.of("image/jpeg", "image/png", "image/webp");

	private final FileStorageProperties properties;

	private final LocalFileStorage fileStorage;

	private final ExamImageProcessor processor;

	public ExamImageStorage(FileStorageProperties properties, LocalFileStorage fileStorage,
			ExamImageProcessor processor) {
		this.properties = properties;
		this.fileStorage = fileStorage;
		this.processor = processor;
	}

	public StoredExamImage store(MultipartFile file) {
		if (file == null || file.isEmpty() || file.getContentType() == null || !ALLOWED_TYPES.contains(file.getContentType())) {
			throw validationFailed();
		}
		if (file.getSize() > properties.examImageMaxBytes()) {
			throw new ApiException(ErrorCode.FILE_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE, "File is too large.");
		}
		try (InputStream input = file.getInputStream()) {
			ExamImageProcessor.ProcessedImage processed = processor.process(input);
			if (processed.bytes().length > properties.examImageMaxBytes()) {
				throw new ApiException(ErrorCode.FILE_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE, "File is too large.");
			}
			String storedName = UUID.randomUUID().toString();
			Path target = fileStorage.rootDirectory().resolve(storedName);
			Files.createDirectories(fileStorage.rootDirectory());
			Files.write(target, processed.bytes());
			return new StoredExamImage(storedName, file.getOriginalFilename(), processed.bytes().length,
					processed.contentType());
		}
		catch (IOException exception) {
			throw validationFailed();
		}
	}

	public void delete(String storedFileName) {
		fileStorage.delete(storedFileName);
	}

	public record StoredExamImage(String storedFileName, String originalFileName, long byteSize, String contentType) {
	}

	private static ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
	}

}
