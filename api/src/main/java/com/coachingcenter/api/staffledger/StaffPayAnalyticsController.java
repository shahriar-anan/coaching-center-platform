package com.coachingcenter.api.staffledger;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Master Admin's staff pay overview and analytics (staff pay plan, section 5, phase 6). Both routes are read only and need
 * STAFF_PAY_MANAGE, which the service checks on every call.
 */
@RestController
public class StaffPayAnalyticsController {

	private final StaffPayAnalyticsService analytics;

	public StaffPayAnalyticsController(StaffPayAnalyticsService analytics) {
		this.analytics = analytics;
	}

	/** What is owed now and overdue, paid this month, salary due in the next thirty days, and who is owed. */
	@GetMapping("/api/v1/staff-pay/overview")
	public StaffPayAnalyticsService.Overview overview() {
		return analytics.overview();
	}

	/** Payroll by month, sheets per instructor by month, and each person's figures for the range. Months are YYYY-MM. */
	@GetMapping("/api/v1/staff-pay/analytics")
	public StaffPayAnalyticsService.Analytics analytics(@RequestParam(required = false) String from,
			@RequestParam(required = false) String to) {
		return analytics.analytics(from, to);
	}

}
