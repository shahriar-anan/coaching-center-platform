package com.coachingcenter.api.notices;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.common.web.PageResponse;

/** The public notice board (refunds, stats and notice board plan, section 6.2). No sign-in; published notices only. */
@RestController
public class SiteNoticePublicController {

	private final SiteNoticeService notices;

	public SiteNoticePublicController(SiteNoticeService notices) {
		this.notices = notices;
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

}
