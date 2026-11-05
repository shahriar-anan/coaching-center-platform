package com.coachingcenter.api.notices;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.common.web.PageResponse;

/**
 * Staff routes for the public notice board (refunds, stats and notice board plan, section 6.2). The service checks the
 * permissions: SITE_NOTICE_EDIT for drafts, SITE_NOTICE_PUBLISH for publishing and for anything already published.
 */
@RestController
public class SiteNoticeStaffController {

	private final SiteNoticeService notices;

	public SiteNoticeStaffController(SiteNoticeService notices) {
		this.notices = notices;
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

}
