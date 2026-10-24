package com.coachingcenter.api.exams;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Writes one inbox reminder per student and exam once the exam is within two days of starting. Only students who already
 * hold access (roster, module grant, or exam grant) get one. The server clock decides; an undated exam never qualifies.
 */
@Component
class StudentReminderJob {

	static final Duration LEAD = Duration.ofHours(48);

	private static final Logger log = LoggerFactory.getLogger(StudentReminderJob.class);

	private final LiveExamRepository exams;

	private final LiveExamModuleRepository modules;

	private final LiveExamCatalogVisibility visibility;

	private final StudentNoticeWriter notices;

	private final Clock clock;

	private final AtomicBoolean running = new AtomicBoolean();

	@org.springframework.beans.factory.annotation.Value("${app.exam-jobs.scheduled:true}")
	private boolean scheduled = true;

	StudentReminderJob(LiveExamRepository exams, LiveExamModuleRepository modules, LiveExamCatalogVisibility visibility,
			StudentNoticeWriter notices, Clock clock) {
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
		}
	}

	int scan() {
		if (!running.compareAndSet(false, true)) {
			log.info("student reminder job already running");
			return 0;
		}
		try {
			Instant now = clock.instant();
			int written = 0;
			for (LiveExam exam : exams.findPublishedStartingBetween(now, now.plus(LEAD))) {
				try {
					written += remind(exam);
				}
				catch (RuntimeException exception) {
					log.warn("student reminder failed examId={} error={}", exam.getId(), exception.getClass().getSimpleName());
				}
			}
			return written;
		}
		catch (RuntimeException exception) {
			log.warn("student reminder job failed error={}", exception.getClass().getSimpleName());
			return 0;
		}
		finally {
			running.set(false);
		}
	}

	private int remind(LiveExam exam) {
		if (exam.getLiveAt() == null) {
			return 0;
		}
		LiveExamModule module = modules.findByIdAndDeletedAtIsNull(exam.getModuleId()).orElse(null);
		if (module == null || !LiveExamModule.PUBLISHED.equals(module.getStatus())
				|| visibility.branchHidden(module.getCategoryId())) {
			return 0;
		}
		List<UUID> students = exams.findActiveStudentsWithAccess(exam.getModuleId(), exam.getId());
		return notices.remind(exam.getId(), exam.getLiveAt(), students);
	}

}
