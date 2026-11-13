package com.coachingcenter.api.offline;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The offline class reminders to the admins (plan sections 1.1 F14 and 7), copied from {@code OfflineFeeReminderJob}.
 *
 * <ul>
 * <li>Every day at 21:00 Dhaka: if any class is unsettled (HELD, or waiting for a payment or a not-taken approval), each active Master
 * Admin and System Admin gets one {@code OFFLINE_CLASSES_TO_SETTLE} notice. The Master Admin's notice also carries the number of
 * entries that wait for approval, in the same notice.</li>
 * <li>Every Sunday at 21:00 Dhaka: if any held class has waited more than seven days, each of them gets one
 * {@code OFFLINE_CLASSES_OVERDUE} notice, once per ISO week.</li>
 * </ul>
 *
 * <p>
 * A run reads the state when it runs, so a missed day is caught up by the next run, and the key of each notice (the person and the day, or
 * the week) stops a repeat run from adding anything. Nothing is sent when there is nothing to settle. Suspended or inactive staff are left
 * out here and refused by the writer. The job does nothing while {@code app.exam-jobs.scheduled} is false.
 */
@Component
public class OfflineClassDigestJob {

	private static final Logger log = LoggerFactory.getLogger(OfflineClassDigestJob.class);

	private static final String ADMINS = """
			select id, role from users
			where role in ('MASTER_ADMIN', 'SYSTEM_ADMIN') and status = 'ACTIVE' and deleted_at is null
			order by created_at, id
			""";

	private final JdbcTemplate jdbc;

	private final OfflineClassService classes;

	private final OfflineClassNotices notices;

	private final TransactionTemplate transactions;

	private final Clock clock;

	@Value("${app.exam-jobs.scheduled:true}")
	private boolean scheduled = true;

	public OfflineClassDigestJob(JdbcTemplate jdbc, OfflineClassService classes, OfflineClassNotices notices,
			PlatformTransactionManager transactionManager, Clock clock) {
		this.jdbc = jdbc;
		this.classes = classes;
		this.notices = notices;
		this.transactions = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	/** The daily digest: 21:00 every day in Dhaka. */
	@Scheduled(cron = "0 0 21 * * *", zone = "Asia/Dhaka")
	public void digest() {
		if (scheduled) {
			runDigest(today());
		}
	}

	/** The weekly overdue reminder: 21:00 every Sunday in Dhaka. */
	@Scheduled(cron = "0 0 21 * * SUN", zone = "Asia/Dhaka")
	public void weekly() {
		if (scheduled) {
			runWeekly(today());
		}
	}

	/** One digest run for {@code today}, a Dhaka date, read at the clock's instant. Returns how many notices were written. */
	public int runDigest(LocalDate today) {
		return runDigestWith(today, classes.snapshot(clock.instant()));
	}

	/** One weekly run for {@code today}, a Dhaka date, read at the clock's instant. Returns how many notices were written. */
	public int runWeekly(LocalDate today) {
		return runWeeklyWith(today, classes.snapshot(clock.instant()));
	}

	/** The digest with the figures given. Nothing is written when no class is unsettled. */
	int runDigestWith(LocalDate today, OfflineClassService.Snapshot snapshot) {
		if (snapshot.unsettled() == 0) {
			return 0;
		}
		return forEachAdmin(admin -> notices.digest(admin.id(), today, snapshot.unsettled(),
				admin.master() && snapshot.pendingEntries() > 0 ? snapshot.pendingEntries() : null));
	}

	/** The weekly reminder with the figures given. Nothing is written when no held class has waited more than a week. */
	int runWeeklyWith(LocalDate today, OfflineClassService.Snapshot snapshot) {
		if (snapshot.heldOverAWeek() == 0) {
			return 0;
		}
		return forEachAdmin(admin -> notices.overdue(admin.id(), today, snapshot.heldOverAWeek()));
	}

	/** Each admin in its own transaction; one that fails is logged and the rest still get theirs. */
	private int forEachAdmin(java.util.function.ToIntFunction<Admin> write) {
		int written = 0;
		for (Admin admin : admins()) {
			try {
				Integer rows = transactions.execute(status -> write.applyAsInt(admin));
				written += rows == null ? 0 : rows;
			}
			catch (RuntimeException exception) {
				log.warn("offline class notice failed recipient={} error={}", admin.id(), exception.getClass().getSimpleName());
			}
		}
		return written;
	}

	private List<Admin> admins() {
		return jdbc.query(ADMINS, (rs, row) -> new Admin(rs.getObject("id", UUID.class), "MASTER_ADMIN".equals(rs.getString("role"))));
	}

	private LocalDate today() {
		return LocalDate.now(clock.withZone(OfflineClassService.DHAKA));
	}

	/** An active Master Admin or System Admin. The Master Admin is the one who gets the waiting-entries line. */
	private record Admin(UUID id, boolean master) {
	}

}
