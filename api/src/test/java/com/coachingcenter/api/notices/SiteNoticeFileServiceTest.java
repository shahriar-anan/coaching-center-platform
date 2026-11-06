package com.coachingcenter.api.notices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.error.FieldErrorDetail;
import com.coachingcenter.api.files.ExamImageProcessor;
import com.coachingcenter.api.files.ExamImageStorage;
import com.coachingcenter.api.files.FileStorageProperties;
import com.coachingcenter.api.files.LocalFileStorage;
import com.coachingcenter.api.files.PdfUploads;

@ExtendWith(MockitoExtension.class)
class SiteNoticeFileServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-07T04:00:00Z");

	private static final UUID ACTOR = UUID.randomUUID();

	@TempDir
	Path dir;

	@Mock
	private SiteNoticeRepository notices;

	@Mock
	private SiteNoticeFileRepository files;

	@Mock
	private PermissionChecker permissions;

	@Mock
	private AuditRecorder audit;

	private final List<SiteNoticeFile> rows = new ArrayList<>();

	private SiteNoticeFileService service;

	private SiteNotice notice;

	private UUID noticeId;

	@BeforeEach
	void setUp() {
		FileStorageProperties properties = new FileStorageProperties(dir.toString(), 104857600L, 15728640L);
		LocalFileStorage storage = new LocalFileStorage(properties);
		ExamImageStorage images = new ExamImageStorage(properties, storage, new ExamImageProcessor());
		PdfUploads pdfs = new PdfUploads(storage);
		service = new SiteNoticeFileService(notices, files, permissions, audit, Clock.fixed(NOW, ZoneOffset.UTC), images, pdfs,
				storage);
		SecurityContextHolder.getContext()
			.setAuthentication(new UsernamePasswordAuthenticationToken(new CurrentActor(ACTOR, Role.MASTER_ADMIN), null, List.of()));

		noticeId = UUID.randomUUID();
		notice = new SiteNotice();
		ReflectionTestUtils.setField(notice, "id", noticeId);
		notice.setStatus(SiteNotice.DRAFT);

		lenient().when(notices.lockActiveById(noticeId)).thenAnswer(invocation -> Optional.of(notice));
		lenient().when(notices.findByIdAndDeletedAtIsNull(noticeId)).thenAnswer(invocation -> Optional.of(notice));
		lenient().when(files.findByNoticeIdOrderByPositionAsc(noticeId)).thenAnswer(invocation -> sortedRows());
		lenient().when(files.findByIdAndNoticeId(any(), eq(noticeId)))
			.thenAnswer(invocation -> rows.stream().filter(row -> row.getId().equals(invocation.getArgument(0))).findFirst());
		lenient().when(files.save(any(SiteNoticeFile.class))).thenAnswer(invocation -> {
			SiteNoticeFile row = invocation.getArgument(0);
			if (row.getId() == null) {
				ReflectionTestUtils.setField(row, "id", UUID.randomUUID());
			}
			if (!rows.contains(row)) {
				rows.add(row);
			}
			return row;
		});
		lenient().doAnswer(invocation -> {
			rows.remove(invocation.<SiteNoticeFile>getArgument(0));
			return null;
		}).when(files).delete(any(SiteNoticeFile.class));
		lenient().when(permissions.has(Permissions.SITE_NOTICE_EDIT)).thenReturn(true);
		lenient().when(permissions.has(Permissions.SITE_NOTICE_PUBLISH)).thenReturn(true);
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Test
	void theSeventhPhotoIsRefusedAndNothingIsStored() throws IOException {
		addPhotos(6);

		var error = assertThrows(ApiException.class, () -> service.addPhoto(noticeId, photo("seventh.png", png(16, 16))));

		assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
		assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(error.details()).isEqualTo(List.of(new FieldErrorDetail("photos", "A notice can have at most 6 photos.")));
		assertThat(rows).hasSize(6);
		assertThat(fileCount()).isEqualTo(6);
	}

	@Test
	void aPhotoIsStoredAsAReEncodedJpegAtTheEnd() throws IOException {
		addPhotos(2);

		SiteNoticeService.StaffRow row = service.addPhoto(noticeId, photo("third.png", png(32, 32)));

		assertThat(rows).hasSize(3);
		SiteNoticeFile added = rows.get(2);
		assertThat(added.getPosition()).isEqualTo((short) 2);
		assertThat(added.getKind()).isEqualTo(SiteNoticeFile.PHOTO);
		assertThat(added.getContentType()).isEqualTo("image/jpeg");
		assertThat(row.photos()).hasSize(3);
		assertThat(row.photos().get(2).id()).isEqualTo(added.getId());
	}

	@Test
	void aFileThatIsNotAnAllowedImageTypeIsRefused() throws IOException {
		MockMultipartFile gif = new MockMultipartFile("file", "anim.gif", "image/gif", new byte[] {'G', 'I', 'F', '8', '9', 'a'});
		MockMultipartFile text = new MockMultipartFile("file", "note.png", "image/png",
				"not an image".getBytes(StandardCharsets.UTF_8));

		assertRefused(() -> service.addPhoto(noticeId, gif), ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST);
		assertRefused(() -> service.addPhoto(noticeId, text), ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST);
		assertThat(rows).isEmpty();
		assertThat(fileCount()).isZero();
	}

	@Test
	void pdfBytesThatDoNotStartWithTheMagicAreRefusedEvenWhenNamedPdf() throws IOException {
		MockMultipartFile html = new MockMultipartFile("file", "routine.pdf", "application/pdf",
				"<html>not a pdf</html>".getBytes(StandardCharsets.UTF_8));
		MockMultipartFile image = new MockMultipartFile("file", "photo.pdf", "application/pdf", png(16, 16));

		assertRefused(() -> service.replacePdf(noticeId, html), ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST);
		assertRefused(() -> service.replacePdf(noticeId, image), ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST);
		assertThat(rows).isEmpty();
		assertThat(fileCount()).isZero();
	}

	@Test
	void aPhotoOverEightMegabytesIsTooLargeBeforeAnyProcessing() {
		MockMultipartFile big = new MockMultipartFile("file", "big.png", "image/png",
				new byte[(int) SiteNoticeFileService.MAX_PHOTO_UPLOAD_BYTES + 1]);

		assertRefused(() -> service.addPhoto(noticeId, big), ErrorCode.FILE_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE);
		assertThat(rows).isEmpty();
	}

	@Test
	void aPdfOverTwentyMegabytesIsTooLarge() {
		byte[] bytes = new byte[(int) SiteNoticeFileService.MAX_PDF_BYTES + 1];
		System.arraycopy("%PDF-1.7".getBytes(StandardCharsets.US_ASCII), 0, bytes, 0, 8);
		MockMultipartFile big = new MockMultipartFile("file", "big.pdf", "application/pdf", bytes);

		assertRefused(() -> service.replacePdf(noticeId, big), ErrorCode.FILE_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE);
		assertThat(rows).isEmpty();
	}

	@Test
	void reorderNeedsExactlyTheCurrentPhotoIdsEachOnce() throws IOException {
		addPhotos(2);
		List<UUID> ids = photoIdsInOrder();

		assertRefused(() -> service.reorderPhotos(noticeId, new SiteNoticeFileService.PhotoOrder(List.of(ids.get(0)))),
				ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST);
		assertRefused(() -> service.reorderPhotos(noticeId,
				new SiteNoticeFileService.PhotoOrder(List.of(ids.get(0), ids.get(0)))), ErrorCode.VALIDATION_FAILED,
				HttpStatus.BAD_REQUEST);
		assertRefused(() -> service.reorderPhotos(noticeId,
				new SiteNoticeFileService.PhotoOrder(List.of(ids.get(0), UUID.randomUUID()))), ErrorCode.VALIDATION_FAILED,
				HttpStatus.BAD_REQUEST);
		assertRefused(() -> service.reorderPhotos(noticeId, null), ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST);
		assertThat(photoIdsInOrder()).containsExactly(ids.get(0), ids.get(1));

		SiteNoticeService.StaffRow row = service.reorderPhotos(noticeId,
				new SiteNoticeFileService.PhotoOrder(List.of(ids.get(1), ids.get(0))));

		assertThat(row.photos()).extracting(SiteNoticeService.PhotoView::id).containsExactly(ids.get(1), ids.get(0));
		assertThat(photoIdsInOrder()).containsExactly(ids.get(1), ids.get(0));
	}

	@Test
	void removingAPhotoClosesUpThePositions() throws IOException {
		addPhotos(3);
		List<UUID> ids = photoIdsInOrder();

		SiteNoticeService.StaffRow row = service.removePhoto(noticeId, ids.get(1));

		assertThat(row.photos()).hasSize(2);
		assertThat(rows).extracting(SiteNoticeFile::getPosition).containsExactly((short) 0, (short) 1);
		assertThat(photoIdsInOrder()).containsExactly(ids.get(0), ids.get(2));
	}

	@Test
	void aDraftUnpublishedOrDeletedNoticeHasNoPublicFiles() throws IOException {
		SiteNoticeFile photo = seedRow(SiteNoticeFile.PHOTO, 0, "public-photo", png(16, 16), "image/jpeg");
		seedRow(SiteNoticeFile.PDF, 0, "public-pdf", "%PDF-1.4 x".getBytes(StandardCharsets.US_ASCII), "application/pdf");

		notice.setStatus(SiteNotice.DRAFT);
		assertPublicNotFound(photo.getId());
		assertThrows(ApiException.class, () -> service.openPublicPdf(noticeId));

		notice.setStatus(SiteNotice.UNPUBLISHED);
		assertPublicNotFound(photo.getId());
		assertThrows(ApiException.class, () -> service.openPublicPdf(noticeId));

		when(notices.findByIdAndDeletedAtIsNull(noticeId)).thenReturn(Optional.empty());
		assertPublicNotFound(photo.getId());
		assertThrows(ApiException.class, () -> service.openPublicPdf(noticeId));
	}

	@Test
	void aPublishedNoticeServesItsPhotoAndPdfAndNoOtherPhoto() throws IOException {
		SiteNoticeFile photo = seedRow(SiteNoticeFile.PHOTO, 0, "live-photo", png(16, 16), "image/jpeg");
		seedRow(SiteNoticeFile.PDF, 0, "live-pdf", "%PDF-1.4 x".getBytes(StandardCharsets.US_ASCII), "application/pdf");
		notice.setStatus(SiteNotice.PUBLISHED);

		SiteNoticeFileService.OpenFile served = service.openPublicPhoto(noticeId, photo.getId());
		assertThat(served.contentType()).isEqualTo("image/jpeg");
		served.body().close();

		assertThat(service.openPublicPdf(noticeId).contentType()).isEqualTo("application/pdf");
		assertThrows(ApiException.class, () -> service.openPublicPhoto(noticeId, UUID.randomUUID()));
	}

	@Test
	void aSystemAdminIsRefusedOnAPublishedNotice() throws IOException {
		notice.setStatus(SiteNotice.PUBLISHED);
		when(permissions.has(Permissions.SITE_NOTICE_PUBLISH)).thenReturn(false);

		assertRefused(() -> service.addPhoto(noticeId, photo("p.png", png(16, 16))), ErrorCode.ACCESS_DENIED,
				HttpStatus.FORBIDDEN);
		assertRefused(() -> service.replacePdf(noticeId, pdf("x")), ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN);
		assertRefused(() -> service.removePdf(noticeId), ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN);
		assertThat(rows).isEmpty();
		assertThat(fileCount()).isZero();
	}

	@Test
	void aSystemAdminMayAddAPhotoToADraft() throws IOException {
		notice.setStatus(SiteNotice.DRAFT);
		lenient().when(permissions.has(Permissions.SITE_NOTICE_PUBLISH)).thenReturn(false);

		SiteNoticeService.StaffRow row = service.addPhoto(noticeId, photo("draft.png", png(16, 16)));

		assertThat(row.photos()).hasSize(1);
	}

	@Test
	void aRolledBackPhotoUploadRemovesItsOwnFile() throws IOException {
		doThrow(new IllegalStateException("database is down")).when(audit)
			.record(eq(AuditRecorder.SITE_NOTICE_FILE_ADD), anyString(), any(UUID.class));
		TransactionSynchronizationManager.initSynchronization();
		try {
			assertThrows(IllegalStateException.class, () -> service.addPhoto(noticeId, photo("p.png", png(16, 16))));
			assertThat(fileCount()).isEqualTo(1);

			rollBack();

			assertThat(fileCount()).isZero();
		}
		finally {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Test
	void aReplacedPdfIsDeletedOnlyAfterTheCommit() throws IOException {
		service.replacePdf(noticeId, pdf("first"));
		String first = rows.get(0).getStoredName();
		TransactionSynchronizationManager.initSynchronization();
		try {
			service.replacePdf(noticeId, pdf("second"));
			assertThat(Files.exists(dir.resolve(first))).isTrue();

			commit();

			assertThat(Files.exists(dir.resolve(first))).isFalse();
			assertThat(fileCount()).isEqualTo(1);
			assertThat(rows).hasSize(1);
		}
		finally {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Test
	void aReplacedPdfRollbackKeepsTheOldFileAndRemovesTheNewOne() throws IOException {
		service.replacePdf(noticeId, pdf("first"));
		String first = rows.get(0).getStoredName();
		TransactionSynchronizationManager.initSynchronization();
		try {
			service.replacePdf(noticeId, pdf("second"));

			rollBack();

			assertThat(Files.exists(dir.resolve(first))).isTrue();
			assertThat(fileCount()).isEqualTo(1);
		}
		finally {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	private List<SiteNoticeFile> sortedRows() {
		return rows.stream()
			.filter(row -> noticeId.equals(row.getNoticeId()))
			.sorted(Comparator.comparingInt(SiteNoticeFile::getPosition))
			.toList();
	}

	private List<UUID> photoIdsInOrder() {
		return sortedRows().stream()
			.filter(row -> SiteNoticeFile.PHOTO.equals(row.getKind()))
			.map(SiteNoticeFile::getId)
			.toList();
	}

	private void addPhotos(int count) throws IOException {
		for (int i = 0; i < count; i++) {
			service.addPhoto(noticeId, photo("photo-" + i + ".png", png(16, 16)));
		}
	}

	private SiteNoticeFile seedRow(String kind, int position, String storedName, byte[] bytes, String contentType)
			throws IOException {
		Files.write(dir.resolve(storedName), bytes);
		SiteNoticeFile row = new SiteNoticeFile();
		ReflectionTestUtils.setField(row, "id", UUID.randomUUID());
		row.setNoticeId(noticeId);
		row.setKind(kind);
		row.setPosition((short) position);
		row.setStoredName(storedName);
		row.setOriginalName(SiteNoticeFile.PDF.equals(kind) ? "routine.pdf" : storedName + ".jpg");
		row.setContentType(contentType);
		row.setByteSize(bytes.length);
		row.setCreatedAt(NOW);
		rows.add(row);
		return row;
	}

	private void assertPublicNotFound(UUID fileId) {
		ApiException error = assertThrows(ApiException.class, () -> service.openPublicPhoto(noticeId, fileId));
		assertThat(error.status()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	private static void assertRefused(Executable action, ErrorCode code, HttpStatus status) {
		var error = assertThrows(ApiException.class, action);
		assertThat(error.code()).isEqualTo(code);
		assertThat(error.status()).isEqualTo(status);
	}

	private static MockMultipartFile photo(String name, byte[] bytes) {
		return new MockMultipartFile("file", name, "image/png", bytes);
	}

	private static MockMultipartFile pdf(String text) {
		return new MockMultipartFile("file", "routine.pdf", "application/pdf",
				("%PDF-1.4 " + text).getBytes(StandardCharsets.US_ASCII));
	}

	private static byte[] png(int width, int height) throws IOException {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		return out.toByteArray();
	}

	private long fileCount() throws IOException {
		try (Stream<Path> stored = Files.list(dir)) {
			return stored.count();
		}
	}

	/** What Spring does when the transaction commits: afterCommit first, then afterCompletion. */
	private static void commit() {
		for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
			synchronization.afterCommit();
		}
		for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
			synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
		}
	}

	/** What Spring does when the transaction rolls back: afterCompletion only. */
	private static void rollBack() {
		for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
			synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
		}
	}

}
