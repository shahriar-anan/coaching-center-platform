package com.coachingcenter.api.notices;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SiteNoticeRowShapeTest {

	private static final Instant CREATED = Instant.parse("2026-11-07T04:00:00Z");

	private final UUID noticeId = UUID.randomUUID();

	@Test
	void aNoticeWithNoFilesHasEmptyPhotosAndNoPdfOnBothRows() {
		SiteNotice notice = published();

		SiteNoticeService.StaffRow staff = SiteNoticeService.staffRow(notice, List.of());
		SiteNoticeService.PublicRow publicRow = SiteNoticeService.publicRow(notice, List.of());

		assertThat(staff.photos()).isNotNull().isEmpty();
		assertThat(staff.pdf()).isNull();
		assertThat(staff.titleEn()).isEqualTo("Notice");
		assertThat(publicRow.photos()).isNotNull().isEmpty();
		assertThat(publicRow.pdf()).isNull();
		assertThat(publicRow.titleBn()).isEqualTo("Bangla notice");
	}

	@Test
	void publicRowsPointAtThePublicRoutesAndStaffRowsAtTheStaffRoutes() {
		SiteNotice notice = published();
		SiteNoticeFile photo = file(SiteNoticeFile.PHOTO, 0, null);
		SiteNoticeFile pdf = file(SiteNoticeFile.PDF, 0, "routine.pdf");

		SiteNoticeService.StaffRow staff = SiteNoticeService.staffRow(notice, List.of(pdf, photo));
		SiteNoticeService.PublicRow publicRow = SiteNoticeService.publicRow(notice, List.of(pdf, photo));

		String version = "?v=" + CREATED.toEpochMilli();
		assertThat(staff.photos()).containsExactly(
				new SiteNoticeService.PhotoView(photo.getId(),
						"/api/v1/site-notices/" + noticeId + "/files/" + photo.getId() + version));
		assertThat(staff.pdf()).isEqualTo(new SiteNoticeService.PdfView(
				"/api/v1/site-notices/" + noticeId + "/files/" + pdf.getId() + version, "routine.pdf", 482113L));
		assertThat(publicRow.photos()).containsExactly(
				new SiteNoticeService.PhotoView(photo.getId(),
						"/api/v1/public/site-notices/" + noticeId + "/photos/" + photo.getId() + version));
		assertThat(publicRow.pdf()).isEqualTo(new SiteNoticeService.PdfView(
				"/api/v1/public/site-notices/" + noticeId + "/pdf" + version, "routine.pdf", 482113L));
	}

	@Test
	void photosAreListedInPositionOrderWhateverOrderTheyArriveIn() {
		SiteNotice notice = published();
		SiteNoticeFile second = file(SiteNoticeFile.PHOTO, 1, "second.jpg");
		SiteNoticeFile first = file(SiteNoticeFile.PHOTO, 0, "first.jpg");

		SiteNoticeService.PublicRow row = SiteNoticeService.publicRow(notice, List.of(second, first));

		assertThat(row.photos()).extracting(SiteNoticeService.PhotoView::id).containsExactly(first.getId(), second.getId());
	}

	private SiteNotice published() {
		SiteNotice notice = new SiteNotice();
		ReflectionTestUtils.setField(notice, "id", noticeId);
		notice.setStatus(SiteNotice.PUBLISHED);
		notice.setTitleEn("Notice");
		notice.setTitleBn("Bangla notice");
		notice.setBodyEn("Body");
		notice.setBodyBn("Bangla body");
		notice.setCreatedAt(CREATED);
		notice.setUpdatedAt(CREATED);
		notice.setPublishedAt(CREATED);
		return notice;
	}

	private SiteNoticeFile file(String kind, int position, String originalName) {
		SiteNoticeFile file = new SiteNoticeFile();
		ReflectionTestUtils.setField(file, "id", UUID.randomUUID());
		file.setNoticeId(noticeId);
		file.setKind(kind);
		file.setPosition((short) position);
		file.setStoredName(UUID.randomUUID().toString());
		file.setOriginalName(originalName);
		file.setContentType(SiteNoticeFile.PDF.equals(kind) ? "application/pdf" : "image/jpeg");
		file.setByteSize(SiteNoticeFile.PDF.equals(kind) ? 482113L : 1024L);
		file.setCreatedAt(CREATED);
		return file;
	}

}
