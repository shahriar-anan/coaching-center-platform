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
 * The one cover image an exam can carry (E1). It mirrors {@link ModuleCoverService}: the Master Admin uploads it, the same
 * image pipeline and 2 MB limit apply, and the file is saved at once rather than staged. Anyone can see it once the exam is
 * published; staff see a draft's cover on the staff routes.
 */
@Service
public class ExamCoverService {

	/** The largest cover the Master Admin may upload, the same limit as a package cover. */
	public static final long MAX_COVER_BYTES = ModuleCoverService.MAX_COVER_BYTES;

	private final LiveExamRepository exams;

	private final ExamImageStorage images;

	private final LocalFileStorage fileStorage;

	public ExamCoverService(LiveExamRepository exams, ExamImageStorage images, LocalFileStorage fileStorage) {
		this.exams = exams;
		this.images = images;
		this.fileStorage = fileStorage;
	}

	/** Replaces the cover. The previous file is removed only after the new row commits. */
	@Transactional
	public LiveExam upload(UUID examId, MultipartFile file) {
		LiveExam exam = find(examId);
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
		String previous = exam.getCoverStoredName();
		exam.setCoverStoredName(stored.storedFileName());
		exam.setCoverContentType(stored.contentType());
		exam.setCoverByteSize(stored.byteSize());
		exam.setCoverUpdatedAt(Instant.now());
		exams.save(exam);
		fileStorage.commitReplacement(stored.storedFileName(), previous);
		return exam;
	}

	/** Clears the cover. An exam with no cover is returned unchanged. */
	@Transactional
	public LiveExam remove(UUID examId) {
		LiveExam exam = find(examId);
		String previous = exam.getCoverStoredName();
		if (previous == null) {
			return exam;
		}
		exam.setCoverStoredName(null);
		exam.setCoverContentType(null);
		exam.setCoverByteSize(null);
		exam.setCoverUpdatedAt(null);
		exams.save(exam);
		fileStorage.commitReplacement(null, previous);
		return exam;
	}

	/** The cover for staff screens, draft or published. 404 when the exam has none. */
	@Transactional(readOnly = true)
	public ModuleCoverService.CoverFile open(UUID examId) {
		return read(find(examId));
	}

	/** The cover for the public route. Only a published exam shows its cover; anything else is 404. */
	@Transactional(readOnly = true)
	public ModuleCoverService.CoverFile openPublished(UUID examId) {
		LiveExam exam = exams.findByIdAndDeletedAtIsNull(examId).orElseThrow(ExamCoverService::notFound);
		if (!LiveExam.PUBLISHED.equals(exam.getStatus())) {
			throw notFound();
		}
		return read(exam);
	}

	/** The public path of an exam's cover, with the upload time as a cache-busting version; null when there is none. */
	public static String coverPath(LiveExam exam) {
		if (exam.getCoverStoredName() == null) {
			return null;
		}
		long version = exam.getCoverUpdatedAt() == null ? 0L : exam.getCoverUpdatedAt().toEpochMilli();
		return "/api/v1/public/live-exams/" + exam.getId() + "/cover?v=" + version;
	}

	private ModuleCoverService.CoverFile read(LiveExam exam) {
		String stored = exam.getCoverStoredName();
		if (stored == null) {
			throw notFound();
		}
		Path path = fileStorage.resolve(stored);
		if (!Files.exists(path)) {
			throw notFound();
		}
		try {
			InputStream body = Files.newInputStream(path);
			return new ModuleCoverService.CoverFile(body, exam.getCoverContentType(),
					exam.getCoverByteSize() == null ? 0L : exam.getCoverByteSize(), exam.getCoverUpdatedAt());
		}
		catch (IOException exception) {
			throw notFound();
		}
	}

	private LiveExam find(UUID examId) {
		return exams.findByIdAndDeletedAtIsNull(examId).orElseThrow(ExamCoverService::notFound);
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
