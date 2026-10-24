package com.coachingcenter.api.exams;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Posts every sitting whose deadline has passed, so a score or a failed-to-submit zero does not wait for someone to open
 * the sitting. The scoring rules stay in {@link LiveExamSittingService}.
 */
@Component
class LiveExamDeadlineJob {

	static final int BATCH_SIZE = 500;

	private static final Logger log = LoggerFactory.getLogger(LiveExamDeadlineJob.class);

	private final LiveExamParticipantRepository participants;

	private final LiveExamSittingService sittings;

	private final Clock clock;

	private final AtomicBoolean running = new AtomicBoolean();

	@org.springframework.beans.factory.annotation.Value("${app.exam-jobs.scheduled:true}")
	private boolean scheduled = true;

	LiveExamDeadlineJob(LiveExamParticipantRepository participants, LiveExamSittingService sittings, Clock clock) {
		this.participants = participants;
		this.sittings = sittings;
		this.clock = clock;
	}

	@Scheduled(fixedDelay = 1, initialDelay = 1, timeUnit = TimeUnit.MINUTES)
	public void run() {
		if (scheduled) {
			settleDue();
		}
	}

	int settleDue() {
		if (!running.compareAndSet(false, true)) {
			log.info("exam deadline job already running");
			return 0;
		}
		try {
			List<UUID> due = participants.findDueIds(clock.instant(), PageRequest.of(0, BATCH_SIZE));
			int settled = 0;
			for (UUID id : due) {
				try {
					sittings.settleParticipant(id);
					settled++;
				}
				catch (RuntimeException exception) {
					log.warn("exam deadline settle failed participantId={} error={}", id,
							exception.getClass().getSimpleName());
				}
			}
			return settled;
		}
		catch (RuntimeException exception) {
			log.warn("exam deadline job failed error={}", exception.getClass().getSimpleName());
			return 0;
		}
		finally {
			running.set(false);
		}
	}

}
