package com.coachingcenter.api.notices;

import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.coachingcenter.api.common.web.PageResponse;
import com.coachingcenter.api.files.FileResponses;

/**
 * Staff routes for the public notice board (refunds, stats and notice board plan, section 6.2; notice media plan, F1). The
 * services check the permissions: SITE_NOTICE_EDIT for drafts, SITE_NOTICE_PUBLISH for publishing and for anything already
 * published.
 */
@RestController
public class SiteNoticeStaffController {

	private final SiteNoticeService notices;

	private final SiteNoticeFileService files;

	public SiteNoticeStaffController(SiteNoticeService notices, SiteNoticeFileService files) {
		this.notices = notices;
		this.files = files;
	}

	/** {@code status} (DRAFT, PUBLISHED or UNPUBLISHED) filters the list. {@code size} is at most 100 (default 20). */
	@GetMapping("/api/v1/site-notices")
	public PageResponse<SiteNoticeService.StaffRow> list(@RequestParam(required = false) String status,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return notices.list(status, page, size);
	}

	@GetMapping("/api/v1/site-notices/{id}")
	public SiteNoticeService.StaffRow get(@PathVariable UUID id) {
		return notices.get(id);
	}

	@PostMapping("/api/v1/site-notices")
	public ResponseEntity<SiteNoticeService.StaffRow> create(@RequestBody(required = false) SiteNoticeService.NoticeForm form) {
		return ResponseEntity.status(HttpStatus.CREATED).body(notices.create(form));
	}

	@PutMapping("/api/v1/site-notices/{id}")
	public SiteNoticeService.StaffRow update(@PathVariable UUID id,
			@RequestBody(required = false) SiteNoticeService.NoticeForm form) {
		return notices.update(id, form);
	}

	@PostMapping("/api/v1/site-notices/{id}/publish")
	public SiteNoticeService.StaffRow publish(@PathVariable UUID id) {
		return notices.publish(id);
	}

	@PostMapping("/api/v1/site-notices/{id}/unpublish")
	public SiteNoticeService.StaffRow unpublish(@PathVariable UUID id) {
		return notices.unpublish(id);
	}

	@DeleteMapping("/api/v1/site-notices/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		notices.delete(id);
		return ResponseEntity.noContent().build();
	}

	/** Adds a photo last. Six is the most: a seventh is 400 with the field {@code photos}. */
	@PostMapping(value = "/api/v1/site-notices/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public SiteNoticeService.StaffRow addPhoto(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
		return files.addPhoto(id, file);
	}

	@DeleteMapping("/api/v1/site-notices/{id}/photos/{fileId}")
	public SiteNoticeService.StaffRow removePhoto(@PathVariable UUID id, @PathVariable UUID fileId) {
		return files.removePhoto(id, fileId);
	}

	/** The new order: {@code ids} must be exactly the current photo ids, each once. */
	@PutMapping("/api/v1/site-notices/{id}/photos/order")
	public SiteNoticeService.StaffRow reorderPhotos(@PathVariable UUID id,
			@RequestBody(required = false) SiteNoticeFileService.PhotoOrder order) {
		return files.reorderPhotos(id, order);
	}

	/** Sets the PDF, replacing any earlier one. */
	@PutMapping(value = "/api/v1/site-notices/{id}/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public SiteNoticeService.StaffRow replacePdf(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
		return files.replacePdf(id, file);
	}

	@DeleteMapping("/api/v1/site-notices/{id}/pdf")
	public SiteNoticeService.StaffRow removePdf(@PathVariable UUID id) {
		return files.removePdf(id);
	}

	/** A photo or the PDF of a notice, for the staff preview. Never cached by shared caches. */
	@GetMapping("/api/v1/site-notices/{id}/files/{fileId}")
	public ResponseEntity<InputStreamResource> file(@PathVariable UUID id, @PathVariable UUID fileId) {
		SiteNoticeFileService.OpenFile file = files.openForStaff(id, fileId);
		return FileResponses.inline(file.body(), file.contentType(), file.byteSize(), file.fileName(), "private, no-cache");
	}

}
