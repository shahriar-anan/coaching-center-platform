package com.coachingcenter.api.exams;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Receipt numbers for paid orders, such as {@code RCPT-2026-000123} (V52). The sequence keeps every number unique; the year
 * is the year of the paid time in Bangladesh time.
 */
@Service
class LiveExamReceipts {

	static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

	private final JdbcTemplate jdbc;

	LiveExamReceipts(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Joins the caller's transaction. Called once, when an order becomes paid. */
	String next(Instant paidAt) {
		Long number = jdbc.queryForObject("select nextval('live_exam_receipt_seq')", Long.class);
		return format(paidAt.atZone(DHAKA).getYear(), number == null ? 0L : number);
	}

	static String format(int year, long number) {
		return "RCPT-" + year + "-" + String.format(Locale.ROOT, "%06d", number);
	}

}
