package com.coachingcenter.api.exams;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ExamMailService {

	private static final Logger log = LoggerFactory.getLogger(ExamMailService.class);

	private final ExamMailQueue queue;

	private final ExamMailDelivery delivery;

	private final Clock clock;

	private final AtomicBoolean running = new AtomicBoolean();

	public ExamMailService(ExamMailQueue queue, ExamMailDelivery delivery, Clock clock) {
		this.queue = queue;
		this.delivery = delivery;
		this.clock = clock;
	}

	public void onOrderSucceeded(UUID orderId) {
		try {
			deliver(queue.enqueueForSucceededOrder(orderId));
		}
		catch (RuntimeException exception) {
			log.warn("exam mail enqueue failed orderId={} error={} message={}", orderId, exception.getClass().getSimpleName(),
					exception.getMessage());
		}
	}

	public void onExamPublished(UUID examId) {
		try {
			deliver(queue.enqueueForExam(examId));
		}
		catch (RuntimeException exception) {
			log.warn("exam mail enqueue failed examId={} error={} message={}", examId, exception.getClass().getSimpleName(),
					exception.getMessage());
		}
	}

	public void onModulePublished(UUID moduleId) {
		try {
			deliver(queue.enqueueForModule(moduleId));
		}
		catch (RuntimeException exception) {
			log.warn("exam mail enqueue failed moduleId={} error={} message={}", moduleId,
					exception.getClass().getSimpleName(), exception.getMessage());
		}
	}

	public void onRosterAdded(UUID moduleId, UUID studentId) {
		try {
			deliver(queue.enqueueForRoster(moduleId, studentId));
		}
		catch (RuntimeException exception) {
			log.warn("exam mail enqueue failed moduleId={} studentId={} error={} message={}", moduleId, studentId,
					exception.getClass().getSimpleName(), exception.getMessage());
		}
	}

	@Scheduled(fixedDelay = 5, initialDelay = 5, timeUnit = TimeUnit.MINUTES)
	public void scan() {
		if (!running.compareAndSet(false, true)) {
			log.info("exam mail scan already running");
			return;
		}
		try {
			queue.enqueueHourBefore(clock.instant());
			deliver(queue.pendingIds());
		}
		catch (RuntimeException exception) {
			log.warn("exam mail scan failed error={} message={}", exception.getClass().getSimpleName(), exception.getMessage());
		}
		finally {
			running.set(false);
		}
	}

	private void deliver(List<UUID> ids) {
		for (UUID id : ids) {
			try {
				delivery.deliverOne(id);
			}
			catch (RuntimeException exception) {
				log.warn("exam mail delivery failed id={} error={} message={}", id, exception.getClass().getSimpleName(),
						exception.getMessage());
			}
		}
	}

}
