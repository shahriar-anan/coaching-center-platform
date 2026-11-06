package com.coachingcenter.api.notices;

import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.common.web.PageResponse;
import com.coachingcenter.api.files.FileResponses;

/**
 * The public notice board (refunds, stats and notice board plan, section 6.2; notice media plan, F1). No sign-in; published
 * notices only. Every file route is 404 for a draft, an unpublished notice or a deleted one.
 */
@RestController
public class SiteNoticePublicController {

	private static final String PUBLIC_CACHE = "public, max-age=3600";

	private final SiteNoticeService notices;

	private final SiteNoticeFileService files;

	public SiteNoticePublicController(SiteNoticeService notices, SiteNoticeFileService files) {
		this.notices = notices;
		this.files = files;
	}

	/** One page of ten published notices, most recently published first. */
	@GetMapping("/api/v1/public/site-notices")
	public PageResponse<SiteNoticeService.PublicRow> list(@RequestParam(defaultValue = "0") int page) {
		return notices.published(page);
	}

	@GetMapping("/api/v1/public/site-notices/{id}")
	public SiteNoticeService.PublicRow get(@PathVariable UUID id) {
		return notices.publicNotice(id);
	}

	/** One photo of a published notice, shown in the browser. The URL carries a version, so it may be cached for an hour. */
	@GetMapping("/api/v1/public/site-notices/{id}/photos/{fileId}")
	public ResponseEntity<InputStreamResource> photo(@PathVariable UUID id, @PathVariable UUID fileId) {
		SiteNoticeFileService.OpenFile file = files.openPublicPhoto(id, fileId);
		return FileResponses.inline(file.body(), file.contentType(), file.byteSize(), file.fileName(), PUBLIC_CACHE);
	}

	/** The PDF of a published notice: shown in the browser, or saved when {@code download=1}. */
	@GetMapping("/api/v1/public/site-notices/{id}/pdf")
	public ResponseEntity<InputStreamResource> pdf(@PathVariable UUID id,
			@RequestParam(required = false) String download) {
		SiteNoticeFileService.OpenFile file = files.openPublicPdf(id);
		if ("1".equals(download)) {
			return FileResponses.attachment(file.body(), file.contentType(), file.byteSize(), file.fileName(), PUBLIC_CACHE);
		}
		return FileResponses.inline(file.body(), file.contentType(), file.byteSize(), file.fileName(), PUBLIC_CACHE);
	}

}
