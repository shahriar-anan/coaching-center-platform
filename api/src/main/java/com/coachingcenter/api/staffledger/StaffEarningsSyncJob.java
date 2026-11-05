package com.coachingcenter.api.staffledger;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the staff pay earnings sync every two minutes and the unpaid-marking reminder once a day (staff pay plan, phase 4).
 * Each run is one transaction in {@link StaffEarningsService}. A failure is logged and the next tick tries again; marking and
 * publishing are never affected.
 *
 * <p>
 * Switch both off with {@code STAFF_PAY_JOBS_ENABLED=false} (property {@code app.staff-pay.jobs-enabled}) without a new build.
 * The test base turns them off, and tests call {@link #sync} and {@link #remindUnpaid} directly.
 */
@Component
public class StaffEarningsSyncJob {

	private static final Logger log = LoggerFactory.getLogger(StaffEarningsSyncJob.class);

	private final StaffEarningsService earnings;

	private final AtomicBoolean syncing = new AtomicBoolean();

	private final AtomicBoolean reminding = new AtomicBoolean();

	@Value("${app.staff-pay.jobs-enabled:${STAFF_PAY_JOBS_ENABLED:true}}")
	private boolean enabled = true;

	public StaffEarningsSyncJob(StaffEarningsService earnings) {
		this.earnings = earnings;
	}

	@Scheduled(fixedDelay = 2, initialDelay = 1, timeUnit = TimeUnit.MINUTES)
	public void run() {
		if (enabled) {
			sync();
		}
	}

	@Scheduled(cron = "0 0 9 * * *", zone = "Asia/Dhaka")
	public void remind() {
		if (enabled) {
			remindUnpaid();
		}
	}

	/** Returns how many marking lines were written. Zero when another run is still going or when it failed. */
	int sync() {
		if (!syncing.compareAndSet(false, true)) {
			log.info("staff pay earnings sync already running");
			return 0;
		}
		try {
			return earnings.syncSheets();
		}
		catch (RuntimeException exception) {
			log.warn("staff pay earnings sync failed error={}", exception.getClass().getSimpleName());
			return 0;
		}
		finally {
			syncing.set(false);
		}
	}

	/** Returns how many reminders were sent. Zero when another run is still going or when it failed. */
	int remindUnpaid() {
		if (!reminding.compareAndSet(false, true)) {
			log.info("staff pay reminder already running");
			return 0;
		}
		try {
			return earnings.remindUnpaid();
		}
		catch (RuntimeException exception) {
			log.warn("staff pay reminder failed error={}", exception.getClass().getSimpleName());
			return 0;
		}
		finally {
			reminding.set(false);
		}
	}

}
