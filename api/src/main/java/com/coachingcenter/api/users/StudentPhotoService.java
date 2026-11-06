package com.coachingcenter.api.users;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.files.ExamImageStorage;
import com.coachingcenter.api.files.LocalFileStorage;

/**
 * A student's own optional profile picture. Only students have one: every call by staff is refused with 403, and the
 * picture never appears in a staff or public response.
 *
 * <p>The upload goes through {@link ExamImageStorage}, which re-encodes it, so the stored file carries no EXIF data (the
 * GPS location of a phone photo included). The stored file is at most {@link #MAX_STORED_BYTES}.
 *
 * <p>Account deletion, if it is ever added, must also call {@link #remove} (or delete the row and the file the same way).
 */
@Service
public class StudentPhotoService {

	/** The largest upload accepted before processing. */
	public static final long MAX_UPLOAD_BYTES = 8L * 1024 * 1024;

	/** The largest picture kept after processing. */
	public static final long MAX_STORED_BYTES = 1L * 1024 * 1024;

	/** The name sent with the picture. The processed file is always JPEG. */
	public static final String FILE_NAME = "profile-picture.jpg";

	private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A };

	private static final byte[] RIFF = { 'R', 'I', 'F', 'F' };

	private static final byte[] WEBP = { 'W', 'E', 'B', 'P' };

	private static final int HEADER_BYTES = 12;

	private final UserRepository users;

	private final StudentPhotoRepository photos;

	private final ExamImageStorage images;

	private final LocalFileStorage fileStorage;

	public StudentPhotoService(UserRepository users, StudentPhotoRepository photos, ExamImageStorage images,
			LocalFileStorage fileStorage) {
		this.users = users;
		this.photos = photos;
		this.images = images;
		this.fileStorage = fileStorage;
	}

	/**
	 * Replaces the picture. The new file is written first and registered with the transaction, so a rolled-back upload
	 * removes its own file; the old file is removed only after the new row commits.
	 */
	@Transactional
	public void upload(UUID userId, MultipartFile file) {
		requireStudent(userId);
		if (file == null || file.isEmpty()) {
			throw validationFailed();
		}
		if (file.getSize() > MAX_UPLOAD_BYTES) {
			throw tooLarge();
		}
		if (!hasJpegPngOrWebpSignature(file)) {
			throw validationFailed();
		}
		StudentPhoto photo = photos.findById(userId).orElseGet(() -> newPhoto(userId));
		String previous = photo.getStoredName();
		ExamImageStorage.StoredExamImage stored = images.store(file);
		if (stored.byteSize() > MAX_STORED_BYTES) {
			images.delete(stored.storedFileName());
			throw tooLarge();
		}
		fileStorage.commitReplacement(stored.storedFileName(), previous);
		photo.setStoredName(stored.storedFileName());
		photo.setContentType(stored.contentType());
		photo.setByteSize(stored.byteSize());
		photo.setUpdatedAt(Instant.now());
		photos.save(photo);
	}

	/** Removes the picture. A student with no picture is left unchanged. */
	@Transactional
	public void remove(UUID userId) {
		requireStudent(userId);
		StudentPhoto photo = photos.findById(userId).orElse(null);
		if (photo == null) {
			return;
		}
		String previous = photo.getStoredName();
		photos.delete(photo);
		fileStorage.commitReplacement(null, previous);
	}

	/** The picture bytes for the student, or 404 when there is none. */
	@Transactional(readOnly = true)
	public PhotoFile open(UUID userId) {
		requireStudent(userId);
		StudentPhoto photo = photos.findById(userId).orElseThrow(StudentPhotoService::notFound);
		Path path = fileStorage.resolve(photo.getStoredName());
		if (!Files.exists(path)) {
			throw notFound();
		}
		try {
			InputStream body = Files.newInputStream(path);
			return new PhotoFile(body, photo.getContentType(), photo.getByteSize());
		}
		catch (IOException exception) {
			throw notFound();
		}
	}

	/**
	 * The path of a student's picture, with the upload time as a cache-busting version. The caller decides whether the
	 * student is a student; staff never get a path.
	 */
	public static String photoPath(StudentPhoto photo) {
		long version = photo.getUpdatedAt() == null ? 0L : photo.getUpdatedAt().toEpochMilli();
		return "/api/v1/me/photo?v=" + version;
	}

	public record PhotoFile(InputStream body, String contentType, long byteSize) {
	}

	private void requireStudent(UUID userId) {
		User user = users.findByIdAndDeletedAtIsNull(userId).orElseThrow(StudentPhotoService::unauthenticated);
		if (!Role.STUDENT.name().equals(user.getRole())) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN,
					"Only a student can have a profile picture.");
		}
	}

	private static StudentPhoto newPhoto(UUID userId) {
		StudentPhoto photo = new StudentPhoto();
		photo.setUserId(userId);
		return photo;
	}

	private static boolean hasJpegPngOrWebpSignature(MultipartFile file) {
		byte[] head = new byte[HEADER_BYTES];
		int read;
		try (InputStream input = file.getInputStream()) {
			read = input.readNBytes(head, 0, HEADER_BYTES);
		}
		catch (IOException exception) {
			return false;
		}
		return startsWith(head, read, JPEG) || startsWith(head, read, PNG) || isWebp(head, read);
	}

	private static boolean isWebp(byte[] head, int read) {
		return read >= HEADER_BYTES && startsWith(head, read, RIFF) && Arrays.equals(head, 8, 12, WEBP, 0, 4);
	}

	private static boolean startsWith(byte[] head, int read, byte[] magic) {
		return read >= magic.length && Arrays.equals(head, 0, magic.length, magic, 0, magic.length);
	}

	private static ApiException unauthenticated() {
		return new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
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
