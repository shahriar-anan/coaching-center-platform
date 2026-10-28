package com.coachingcenter.api.exams;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
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

	/** The window-open notice, `EXAM_OPEN`. Each run looks back the longest duration, and the writer keeps it to once. */
	int scanOpened() {
		return sweep("open", this::openNow, notices::opened);
	}

	private List<LiveExam> openNow(Instant now) {
		Instant since = now.minus(Duration.ofMinutes(exams.maxDurationMinutes()));
		return exams.findPublishedStartingBetween(since, now).stream()
			.filter(exam -> now.isBefore(exam.getLiveAt().plusSeconds(exam.getDurationMinutes() * 60L)))
			.toList();
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
		LiveExamModule module = modules.findByIdAndDeletedAtIsNull(memberships.requirePrimaryModuleId(exam.getId())).orElse(null);
		if (module == null || !LiveExamModule.PUBLISHED.equals(module.getStatus())
				|| visibility.branchHidden(module.getCategoryId())) {
			return 0;
		}
		List<UUID> students = exams.findActiveStudentsWithAccess(memberships.requirePrimaryModuleId(exam.getId()), exam.getId());
		return writer.write(exam.getId(), exam.getLiveAt(), students);
	}

	@FunctionalInterface
	private interface StartWriter {

		int write(UUID examId, Instant liveAt, Collection<UUID> studentIds);

	}

}
