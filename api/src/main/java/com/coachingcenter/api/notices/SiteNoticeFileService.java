package com.coachingcenter.api.notices;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.error.FieldErrorDetail;
import com.coachingcenter.api.files.ExamImageStorage;
import com.coachingcenter.api.files.LocalFileStorage;
import com.coachingcenter.api.files.PdfUploads;

/**
 * The photos and the PDF of a public notice (notice media plan, F1). A draft's files need SITE_NOTICE_EDIT; a notice that is
 * published or unpublished needs SITE_NOTICE_PUBLISH, the same rule as its texts. Every change locks the notice row, so six
 * photos is a hard limit. A file is deleted from disk only after the change commits, and a rolled-back upload removes its own
 * file (see {@link LocalFileStorage#commitReplacement}).
 */
@Service
public class SiteNoticeFileService {

	/** The most photos one notice may carry. */
	static final int MAX_PHOTOS = 6;

	/** The largest photo the admin may upload. The image pipeline re-encodes it. */
	static final long MAX_PHOTO_UPLOAD_BYTES = 8L * 1024 * 1024;

	/** The largest photo after re-encoding, the same as the package cover. */
	static final long MAX_PHOTO_BYTES = 2L * 1024 * 1024;

	/** The largest PDF. */
	static final long MAX_PDF_BYTES = 20L * 1024 * 1024;

	private static final int MAX_NAME = 255;

	private static final String DENIED = "Access is denied.";

	private final SiteNoticeRepository notices;

	private final SiteNoticeFileRepository files;

	private final PermissionChecker permissions;

	private final AuditRecorder audit;

	private final Clock clock;

	private final ExamImageStorage images;

	private final PdfUploads pdfs;

	private final LocalFileStorage fileStorage;

	public SiteNoticeFileService(SiteNoticeRepository notices, SiteNoticeFileRepository files, PermissionChecker permissions,
			AuditRecorder audit, Clock clock, ExamImageStorage images, PdfUploads pdfs, LocalFileStorage fileStorage) {
		this.notices = notices;
		this.files = files;
		this.permissions = permissions;
		this.audit = audit;
		this.clock = clock;
		this.images = images;
		this.pdfs = pdfs;
		this.fileStorage = fileStorage;
	}

	/** Adds a photo after the existing ones. A seventh is refused. The stored photo must be at most 2 MB after re-encoding. */
	@Transactional
	public SiteNoticeService.StaffRow addPhoto(UUID noticeId, MultipartFile file) {
		SiteNotice notice = lockForEdit(noticeId);
		List<SiteNoticeFile> all = files.findByNoticeIdOrderByPositionAsc(noticeId);
		List<SiteNoticeFile> photos = photosOf(all);
		if (photos.size() >= MAX_PHOTOS) {
			throw validation("photos", "A notice can have at most 6 photos.");
		}
		if (file == null || file.isEmpty()) {
			throw validation("file", "Choose a photo to upload.");
		}
		if (file.getSize() > MAX_PHOTO_UPLOAD_BYTES) {
			throw tooLarge();
		}
		ExamImageStorage.StoredExamImage stored = images.store(file);
		// Registered straight away: from here on, a rollback removes the new file.
		fileStorage.commitReplacement(stored.storedFileName(), null);
		if (stored.byteSize() > MAX_PHOTO_BYTES) {
			throw tooLarge();
		}
		UUID actor = SiteNoticeService.actorId();
		Instant now = clock.instant();
		SiteNoticeFile row = new SiteNoticeFile();
		row.setNoticeId(noticeId);
		row.setKind(SiteNoticeFile.PHOTO);
		row.setPosition((short) photos.size());
		fill(row, stored.storedFileName(), stored.originalFileName(), stored.contentType(), stored.byteSize(), actor, now);
		files.save(row);
		List<SiteNoticeFile> after = new ArrayList<>(all);
		after.add(row);
		touch(notice, actor, now);
		audit.record(AuditRecorder.SITE_NOTICE_FILE_ADD, AuditRecorder.SITE_NOTICE_ENTITY, noticeId);
		return SiteNoticeService.staffRow(notice, after);
	}

	/** Removes one photo and closes up the positions. The file is deleted after the commit. */
	@Transactional
	public SiteNoticeService.StaffRow removePhoto(UUID noticeId, UUID fileId) {
		SiteNotice notice = lockForEdit(noticeId);
		List<SiteNoticeFile> all = files.findByNoticeIdOrderByPositionAsc(noticeId);
		SiteNoticeFile target = photosOf(all).stream()
			.filter(file -> file.getId().equals(fileId))
			.findFirst()
			.orElseThrow(SiteNoticeService::notFound);
		List<SiteNoticeFile> remaining = all.stream().filter(file -> !file.getId().equals(fileId)).toList();
		files.delete(target);
		renumber(photosOf(remaining));
		UUID actor = SiteNoticeService.actorId();
		touch(notice, actor, clock.instant());
		fileStorage.commitReplacement(null, target.getStoredName());
		audit.record(AuditRecorder.SITE_NOTICE_FILE_REMOVE, AuditRecorder.SITE_NOTICE_ENTITY, noticeId);
		return SiteNoticeService.staffRow(notice, remaining);
	}

	/** Puts the photos in the order given. The ids must be exactly the notice's current photos, each once. */
	@Transactional
	public SiteNoticeService.StaffRow reorderPhotos(UUID noticeId, PhotoOrder order) {
		SiteNotice notice = lockForEdit(noticeId);
		List<SiteNoticeFile> all = files.findByNoticeIdOrderByPositionAsc(noticeId);
		List<SiteNoticeFile> photos = photosOf(all);
		List<UUID> ids = order == null || order.ids() == null ? List.of() : order.ids();
		Set<UUID> requested = new HashSet<>(ids);
		Set<UUID> current = photos.stream().map(SiteNoticeFile::getId).collect(Collectors.toSet());
		if (ids.size() != photos.size() || requested.size() != ids.size() || !requested.equals(current)) {
			throw validation("ids", "List every photo once, in the new order.");
		}
		Map<UUID, SiteNoticeFile> byId = photos.stream().collect(Collectors.toMap(SiteNoticeFile::getId, file -> file));
		for (int i = 0; i < ids.size(); i++) {
			byId.get(ids.get(i)).setPosition((short) i);
		}
		UUID actor = SiteNoticeService.actorId();
		touch(notice, actor, clock.instant());
		audit.record(AuditRecorder.SITE_NOTICE_FILE_REORDER, AuditRecorder.SITE_NOTICE_ENTITY, noticeId);
		return SiteNoticeService.staffRow(notice, all);
	}

	/**
	 * Sets the notice's PDF, replacing any earlier one. The PDF row is changed in place, so the table keeps one PDF per
	 * notice. The old file is deleted only after the commit.
	 */
	@Transactional
	public SiteNoticeService.StaffRow replacePdf(UUID noticeId, MultipartFile file) {
		SiteNotice notice = lockForEdit(noticeId);
		List<SiteNoticeFile> all = files.findByNoticeIdOrderByPositionAsc(noticeId);
		SiteNoticeFile existing = pdfOf(all);
		LocalFileStorage.StoredFile stored = pdfs.store(file, MAX_PDF_BYTES);
		// Registered straight away: the old file goes after a commit, the new one goes after a rollback.
		fileStorage.commitReplacement(stored.storedFileName(), existing == null ? null : existing.getStoredName());
		UUID actor = SiteNoticeService.actorId();
		Instant now = clock.instant();
		List<SiteNoticeFile> after = new ArrayList<>(all);
		if (existing == null) {
			SiteNoticeFile row = new SiteNoticeFile();
			row.setNoticeId(noticeId);
			row.setKind(SiteNoticeFile.PDF);
			row.setPosition((short) 0);
			fill(row, stored.storedFileName(), stored.originalFileName(), stored.contentType(), stored.byteSize(), actor, now);
			files.save(row);
			after.add(row);
		}
		else {
			fill(existing, stored.storedFileName(), stored.originalFileName(), stored.contentType(), stored.byteSize(), actor, now);
		}
		touch(notice, actor, now);
		audit.record(AuditRecorder.SITE_NOTICE_FILE_ADD, AuditRecorder.SITE_NOTICE_ENTITY, noticeId);
		return SiteNoticeService.staffRow(notice, after);
	}

	/** Removes the PDF. A notice with no PDF is returned unchanged. */
	@Transactional
	public SiteNoticeService.StaffRow removePdf(UUID noticeId) {
		SiteNotice notice = lockForEdit(noticeId);
		List<SiteNoticeFile> all = files.findByNoticeIdOrderByPositionAsc(noticeId);
		SiteNoticeFile existing = pdfOf(all);
		if (existing == null) {
			return SiteNoticeService.staffRow(notice, all);
		}
		List<SiteNoticeFile> remaining = all.stream().filter(file -> !file.getId().equals(existing.getId())).toList();
		files.delete(existing);
		UUID actor = SiteNoticeService.actorId();
		touch(notice, actor, clock.instant());
		fileStorage.commitReplacement(null, existing.getStoredName());
		audit.record(AuditRecorder.SITE_NOTICE_FILE_REMOVE, AuditRecorder.SITE_NOTICE_ENTITY, noticeId);
		return SiteNoticeService.staffRow(notice, remaining);
	}

	/** A file's bytes for the staff preview. The same rule as reading the notice: SITE_NOTICE_EDIT, and not deleted. */
	@Transactional(readOnly = true)
	public OpenFile openForStaff(UUID noticeId, UUID fileId) {
		requireEdit();
		notices.findByIdAndDeletedAtIsNull(noticeId).orElseThrow(SiteNoticeService::notFound);
		SiteNoticeFile file = files.findByIdAndNoticeId(fileId, noticeId).orElseThrow(SiteNoticeService::notFound);
		return open(file);
	}

	/** A photo of a published notice. Any other notice, or a photo of another notice, is 404. */
	@Transactional(readOnly = true)
	public OpenFile openPublicPhoto(UUID noticeId, UUID fileId) {
		requirePublished(noticeId);
		SiteNoticeFile file = files.findByIdAndNoticeId(fileId, noticeId)
			.filter(row -> SiteNoticeFile.PHOTO.equals(row.getKind()))
			.orElseThrow(SiteNoticeService::notFound);
		return open(file);
	}

	/** The PDF of a published notice. Any other notice, or one with no PDF, is 404. */
	@Transactional(readOnly = true)
	public OpenFile openPublicPdf(UUID noticeId) {
		requirePublished(noticeId);
		SiteNoticeFile pdf = pdfOf(files.findByNoticeIdOrderByPositionAsc(noticeId));
		if (pdf == null) {
			throw SiteNoticeService.notFound();
		}
		return open(pdf);
	}

	/** The positions of the photos, as the ids in the order the admin wants. */
	public record PhotoOrder(List<UUID> ids) {
	}

	/** A file's bytes, ready for {@code FileResponses}. */
	public record OpenFile(InputStream body, String contentType, long byteSize, String fileName) {
	}

	/** Loads the notice with a row lock and checks the caller may change its files. A deleted notice is 404. */
	private SiteNotice lockForEdit(UUID noticeId) {
		SiteNotice notice = notices.lockActiveById(noticeId).orElseThrow(SiteNoticeService::notFound);
		if (SiteNotice.DRAFT.equals(notice.getStatus())) {
			requireEdit();
		}
		else {
			requirePublish();
		}
		return notice;
	}

	private void requirePublished(UUID noticeId) {
		notices.findByIdAndDeletedAtIsNull(noticeId)
			.filter(notice -> SiteNotice.PUBLISHED.equals(notice.getStatus()))
			.orElseThrow(SiteNoticeService::notFound);
	}

	private void requireEdit() {
		if (!permissions.has(Permissions.SITE_NOTICE_EDIT)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, DENIED);
		}
	}

	private void requirePublish() {
		if (!permissions.has(Permissions.SITE_NOTICE_PUBLISH)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, DENIED);
		}
	}

	private OpenFile open(SiteNoticeFile file) {
		Path path = fileStorage.resolve(file.getStoredName());
		if (!Files.exists(path)) {
			throw SiteNoticeService.notFound();
		}
		try {
			InputStream body = Files.newInputStream(path);
			return new OpenFile(body, file.getContentType(), file.getByteSize(), SiteNoticeService.displayName(file));
		}
		catch (IOException exception) {
			throw SiteNoticeService.notFound();
		}
	}

	private static List<SiteNoticeFile> photosOf(List<SiteNoticeFile> all) {
		return all.stream()
			.filter(file -> SiteNoticeFile.PHOTO.equals(file.getKind()))
			.sorted(Comparator.comparingInt(SiteNoticeFile::getPosition))
			.toList();
	}

	private static SiteNoticeFile pdfOf(List<SiteNoticeFile> all) {
		return all.stream().filter(file -> SiteNoticeFile.PDF.equals(file.getKind())).findFirst().orElse(null);
	}

	/** Positions 0, 1, 2 and so on for the photos in the order given. */
	private static void renumber(List<SiteNoticeFile> photosInOrder) {
		for (int i = 0; i < photosInOrder.size(); i++) {
			photosInOrder.get(i).setPosition((short) i);
		}
	}

	private static void fill(SiteNoticeFile row, String storedName, String originalName, String contentType, long byteSize,
			UUID actor, Instant now) {
		row.setStoredName(storedName);
		row.setOriginalName(clip(originalName));
		row.setContentType(contentType);
		row.setByteSize(byteSize);
		row.setCreatedBy(actor);
		row.setCreatedAt(now);
	}

	/** The name is kept to the column's 255 characters. */
	private static String clip(String name) {
		if (name == null || name.codePointCount(0, name.length()) <= MAX_NAME) {
			return name;
		}
		return name.substring(0, name.offsetByCodePoints(0, MAX_NAME));
	}

	private static void touch(SiteNotice notice, UUID actor, Instant now) {
		notice.setUpdatedBy(actor);
		notice.setUpdatedAt(now);
	}

	private static ApiException validation(String field, String message) {
		return SiteNoticeService.validation(List.of(new FieldErrorDetail(field, message)));
	}

	private static ApiException tooLarge() {
		return new ApiException(ErrorCode.FILE_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE, "File is too large.");
	}

}
