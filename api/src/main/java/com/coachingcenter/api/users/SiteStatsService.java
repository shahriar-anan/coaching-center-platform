package com.coachingcenter.api.users;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.coachingcenter.api.auth.Role;

/**
 * The landing page's live figures: students and mentors, each a fixed base plus the live count. The counts are cached for a
 * minute, so visits to the public page never reach the database each time. Counts only, never names or emails.
 */
@Service
public class SiteStatsService {

	private static final Duration CACHE_FOR = Duration.ofSeconds(60);

	private static final String ACTIVE = "ACTIVE";

	private final UserRepository users;

	private final Clock clock;

	private final long studentsBase;

	private final long mentorsBase;

	private final Instant studentsCountedFrom;

	private final Object lock = new Object();

	private SiteStats cached;

	private Instant cachedUntil = Instant.MIN;

	public SiteStatsService(UserRepository users, Clock clock, @Value("${app.stats.students-base}") long studentsBase,
			@Value("${app.stats.mentors-base}") long mentorsBase,
			@Value("${app.stats.students-counted-from}") String studentsCountedFrom) {
		this.users = users;
		this.clock = clock;
		this.studentsBase = studentsBase;
		this.mentorsBase = mentorsBase;
		this.studentsCountedFrom = Instant.parse(studentsCountedFrom.trim());
	}

	/**
	 * Students: the base plus student accounts created at or after the counted-from instant, not deleted. Mentors: the base
	 * plus instructors who have activated their account (status ACTIVE, not deleted). A pending invitation does not count.
	 */
	public SiteStats current() {
		Instant now = clock.instant();
		synchronized (lock) {
			if (cached != null && now.isBefore(cachedUntil)) {
				return cached;
			}
			SiteStats fresh = new SiteStats(
					studentsBase + users.countByRoleAndDeletedAtIsNullAndCreatedAtGreaterThanEqual(Role.STUDENT.name(),
							studentsCountedFrom),
					mentorsBase + users.countByRoleAndStatusAndDeletedAtIsNull(Role.INSTRUCTOR.name(), ACTIVE));
			cached = fresh;
			cachedUntil = now.plus(CACHE_FOR);
			return fresh;
		}
	}

	/** The public answer: {@code {"students": 874, "mentors": 2}}. */
	public record SiteStats(long students, long mentors) {
	}

}
