package com.coachingcenter.api.exams;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Writes one inbox reminder per student and exam once the exam is within two days of starting, and one in-app notice
 * once it is within the hour. Only students who already hold access (roster, module grant, or exam grant) get one. The
 * server clock decides; an undated exam never qualifies.
 */
@Component
class StudentReminderJob {

	static final Duration LEAD = Duration.ofHours(48);

	static final Duration SOON = Duration.ofMinutes(60);

	/** Matches no package, so the access query returns only the holders of an exam grant. */
	private static final UUID NO_PACKAGE = new UUID(0, 0);

	private static final Logger log = LoggerFactory.getLogger(StudentReminderJob.class);

	private final LiveExamRepository exams;

	private final LiveExamMemberships memberships;

	private final LiveExamModuleRepository modules;

	private final LiveExamCatalogVisibility visibility;

	private final StudentNoticeWriter notices;

	private final Clock clock;

	private final AtomicBoolean running = new AtomicBoolean();

	@org.springframework.beans.factory.annotation.Value("${app.exam-jobs.scheduled:true}")
	private boolean scheduled = true;

	StudentReminderJob(LiveExamRepository exams, LiveExamModuleRepository modules, LiveExamCatalogVisibility visibility,
			StudentNoticeWriter notices, Clock clock,
			LiveExamMemberships memberships) {
		this.memberships = memberships;
		this.exams = exams;
		this.modules = modules;
		this.visibility = visibility;
		this.notices = notices;
		this.clock = clock;
	}

	@Scheduled(fixedDelay = 15, initialDelay = 2, timeUnit = TimeUnit.MINUTES)
	public void run() {
		if (scheduled) {
			scan();
			scanSoon();
			scanOpened();
		}
	}

	/** The 48-hour reminder, `EXAM_REMINDER`. */
	int scan() {
		return sweep("reminder", now -> exams.findPublishedStartingBetween(now, now.plus(LEAD)), notices::remind);
	}

	/** The one-hour in-app notice, `EXAM_STARTING_SOON`. */
	int scanSoon() {
		return sweep("starting soon", now -> exams.findPublishedStartingBetween(now, now.plus(SOON)),
				notices::startingSoon);
	}

	/** The window-open notice, `EXAM_OPEN`, for every exam whose window is open. The writer keeps it to once per student. */
	int scanOpened() {
		return sweep("open", this::openNow, notices::opened);
	}

	/** The exams whose window is open now. A window can run for hours, so this reads the stored window, not a look-back. */
	private List<LiveExam> openNow(Instant now) {
		return exams.findPublishedOpenAt(now);
	}

	private int sweep(String kind, Function<Instant, List<LiveExam>> candidates, StartWriter writer) {
		if (!running.compareAndSet(false, true)) {
			log.info("student {} job already running", kind);
			return 0;
		}
		try {
			Instant now = clock.instant();
			int written = 0;
			for (LiveExam exam : candidates.apply(now)) {
				try {
					written += write(exam, writer);
				}
				catch (RuntimeException exception) {
					log.warn("student {} failed examId={} error={}", kind, exam.getId(),
							exception.getClass().getSimpleName());
				}
			}
			return written;
		}
		catch (RuntimeException exception) {
			log.warn("student {} job failed error={}", kind, exception.getClass().getSimpleName());
			return 0;
		}
		finally {
			running.set(false);
		}
	}

	private int write(LiveExam exam, StartWriter writer) {
		if (exam.getLiveAt() == null) {
			return 0;
		}
		if (exam.getCategoryId() == null || visibility.branchHidden(exam.getCategoryId())) {
			return 0;
		}
		// Everyone who holds the exam through any of its packages, or on its own, once (contracts §8). A draft package sends nothing.
		List<UUID> moduleIds = memberships.activeModuleIds(exam.getId());
		Set<UUID> students = new LinkedHashSet<>();
		if (moduleIds.isEmpty()) {
			students.addAll(exams.findActiveStudentsWithAccess(NO_PACKAGE, exam.getId()));
		}
		for (UUID moduleId : moduleIds) {
			LiveExamModule module = modules.findByIdAndDeletedAtIsNull(moduleId).orElse(null);
			if (module != null && LiveExamModule.PUBLISHED.equals(module.getStatus())) {
				students.addAll(exams.findActiveStudentsWithAccess(moduleId, exam.getId()));
			}
		}
		return writer.write(exam.getId(), exam.getLiveAt(), students);
	}

	@FunctionalInterface
	private interface StartWriter {

		int write(UUID examId, Instant liveAt, Collection<UUID> studentIds);

	}

}
