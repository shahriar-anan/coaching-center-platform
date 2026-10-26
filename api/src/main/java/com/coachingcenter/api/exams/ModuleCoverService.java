package com.coachingcenter.api.exams;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.files.ExamImageStorage;
import com.coachingcenter.api.files.LocalFileStorage;

/**
 * The one cover image a package (module) can carry. The Master Admin uploads it; anyone can see it once the
 * module is public. The file goes through the same image pipeline as question images, with its own 2 MB limit.
 */
@Service
public class ModuleCoverService {

	/** The largest cover the Master Admin may upload. */
	public static final long MAX_COVER_BYTES = 2L * 1024 * 1024;

	private final LiveExamModuleRepository modules;

	private final ExamImageStorage images;

	private final LocalFileStorage fileStorage;

	public ModuleCoverService(LiveExamModuleRepository modules, ExamImageStorage images, LocalFileStorage fileStorage) {
		this.modules = modules;
		this.images = images;
		this.fileStorage = fileStorage;
	}

	/** Replaces the cover. The previous file is removed only after the new row commits. */
	@Transactional
	public LiveExamModule upload(UUID moduleId, MultipartFile file) {
		LiveExamModule module = find(moduleId);
		if (file == null || file.isEmpty()) {
			throw validationFailed();
		}
		if (file.getSize() > MAX_COVER_BYTES) {
			throw tooLarge();
		}
		ExamImageStorage.StoredExamImage stored = images.store(file);
		if (stored.byteSize() > MAX_COVER_BYTES) {
			images.delete(stored.storedFileName());
			throw tooLarge();
		}
		String previous = module.getCoverStoredName();
		module.setCoverStoredName(stored.storedFileName());
		module.setCoverContentType(stored.contentType());
		module.setCoverByteSize(stored.byteSize());
		module.setCoverUpdatedAt(Instant.now());
		modules.save(module);
		fileStorage.commitReplacement(stored.storedFileName(), previous);
		return module;
	}

	/** Clears the cover. A module with no cover is returned unchanged. */
	@Transactional
	public LiveExamModule remove(UUID moduleId) {
		LiveExamModule module = find(moduleId);
		String previous = module.getCoverStoredName();
		if (previous == null) {
			return module;
		}
		module.setCoverStoredName(null);
		module.setCoverContentType(null);
		module.setCoverByteSize(null);
		module.setCoverUpdatedAt(null);
		modules.save(module);
		fileStorage.commitReplacement(null, previous);
		return module;
	}

	/** The cover bytes for a module, or 404 when it has none. The caller checks the module is public. */
	@Transactional(readOnly = true)
	public CoverFile open(UUID moduleId) {
		LiveExamModule module = find(moduleId);
		String stored = module.getCoverStoredName();
		if (stored == null) {
			throw notFound();
		}
		Path path = fileStorage.resolve(stored);
		if (!Files.exists(path)) {
			throw notFound();
		}
		try {
			InputStream body = Files.newInputStream(path);
			return new CoverFile(body, module.getCoverContentType(), module.getCoverByteSize() == null ? 0L : module.getCoverByteSize(),
					module.getCoverUpdatedAt());
		}
		catch (IOException exception) {
			throw notFound();
		}
	}

	public record CoverFile(InputStream body, String contentType, long byteSize, Instant updatedAt) {
	}

	/** The public path of a module's cover, with the upload time as a cache-busting version; null when there is none. */
	public static String coverPath(LiveExamModule module) {
		if (module.getCoverStoredName() == null) {
			return null;
		}
		long version = module.getCoverUpdatedAt() == null ? 0L : module.getCoverUpdatedAt().toEpochMilli();
		return "/api/v1/public/live-exam-modules/" + module.getId() + "/cover?v=" + version;
	}

	private LiveExamModule find(UUID moduleId) {
		return modules.findByIdAndDeletedAtIsNull(moduleId)
				.orElseThrow(ModuleCoverService::notFound);
	}

	private static ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	private static ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
	}

	private static ApiException tooLarge() {
		return new ApiException(ErrorCode.FILE_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE, "File is too large.");
	}

}
