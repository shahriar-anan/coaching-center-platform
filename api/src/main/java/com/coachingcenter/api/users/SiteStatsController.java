package com.coachingcenter.api.users;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public figures for the landing page. No sign-in; counts only. */
@RestController
public class SiteStatsController {

	private final SiteStatsService stats;

	public SiteStatsController(SiteStatsService stats) {
		this.stats = stats;
	}

	@GetMapping("/api/v1/public/site-stats")
	public SiteStatsService.SiteStats siteStats() {
		return stats.current();
	}

}
