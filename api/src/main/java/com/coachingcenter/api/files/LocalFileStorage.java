package com.coachingcenter.api.files;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

@Component
public class LocalFileStorage {

	private final FileStorageProperties properties;

	public LocalFileStorage(FileStorageProperties properties) {
		this.properties = properties;
	}

	public StoredFile save(String originalFileName, String contentType, InputStream input, long byteSize) {
		return save(originalFileName, contentType, input, byteSize, properties.materialMaxBytes());
	}

	public StoredFile save(String originalFileName, String contentType, InputStream input, long byteSize, long maxBytes) {
		if (byteSize > maxBytes) {
			throw new ApiException(ErrorCode.FILE_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE, "File is too large.");
		}
		String storedName = UUID.randomUUID().toString();
		Path root = rootDirectory();
		Path target = root.resolve(storedName);
		try {
			Files.createDirectories(root);
			Files.copy(input, target);
		}
		catch (IOException exception) {
			throw new IllegalStateException("Could not store file.", exception);
		}
		return new StoredFile(storedName, originalFileName, byteSize, contentType);
	}

	public Path resolve(String storedFileName) {
		return rootDirectory().resolve(storedFileName);
	}

	public void commitReplacement(String newStoredFileName, String previousStoredFileName) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			deleteQuietly(previousStoredFileName, newStoredFileName);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				deleteQuietly(previousStoredFileName, newStoredFileName);
			}

			@Override
			public void afterCompletion(int status) {
				if (status != TransactionSynchronization.STATUS_COMMITTED) {
					deleteQuietly(newStoredFileName, null);
				}
			}
		});
	}

	public void delete(String storedFileName) {
		if (storedFileName == null || storedFileName.isBlank()) {
			return;
		}
		try {
			Files.deleteIfExists(resolve(storedFileName));
		}
		catch (IOException exception) {
			throw new IllegalStateException("Could not delete file.", exception);
		}
	}

	private void deleteQuietly(String storedFileName, String keep) {
		if (storedFileName == null || storedFileName.isBlank() || storedFileName.equals(keep)) {
			return;
		}
		try {
			delete(storedFileName);
		}
		catch (RuntimeException ignored) {
			// The committed row already names the file that should stay.
		}
	}

	public Path rootDirectory() {
		String dir = properties.storageDir();
		if (dir == null || dir.isBlank()) {
			throw new IllegalStateException("FILE_STORAGE_DIR is not configured.");
		}
		return Path.of(dir);
	}

	public record StoredFile(String storedFileName, String originalFileName, long byteSize, String contentType) {
	}

}
