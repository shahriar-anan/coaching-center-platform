package com.coachingcenter.api.exams;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ExamMailService {

	private static final Logger log = LoggerFactory.getLogger(ExamMailService.class);

	private final ExamMailQueue queue;

	private final ExamMailDelivery delivery;

	private final Clock clock;

	private final ExamMailWorker worker;

	private final AtomicBoolean running = new AtomicBoolean();

	/** Off by default: the one-hour-before mail is skipped for now (client decision). */
	private final boolean hourBeforeEnabled;

	@Autowired
	public ExamMailService(ExamMailQueue queue, ExamMailDelivery delivery, Clock clock, ExamMailWorker worker,
			@Value("${app.exam-mail.hour-before.enabled:false}") boolean hourBeforeEnabled) {
		this.queue = queue;
		this.delivery = delivery;
		this.clock = clock;
		this.worker = worker;
		this.hourBeforeEnabled = hourBeforeEnabled;
	}

	/** Sends on the calling thread. Used where no worker is wanted. */
	public ExamMailService(ExamMailQueue queue, ExamMailDelivery delivery, Clock clock, boolean hourBeforeEnabled) {
		this(queue, delivery, clock, ExamMailWorker.inline(), hourBeforeEnabled);
	}

	public void onOrderSucceeded(UUID orderId) {
		try {
			dispatch("ORDER", queue.enqueueForSucceededOrder(orderId));
		}
		catch (RuntimeException exception) {
			log.warn("exam mail enqueue failed orderId={} error={} message={}", orderId, exception.getClass().getSimpleName(),
					exception.getMessage());
		}
	}

	public void onExamPublished(UUID examId) {
		try {
			dispatch("EXAM_PUBLISHED", queue.enqueueForExam(examId));
		}
		catch (RuntimeException exception) {
			log.warn("exam mail enqueue failed examId={} error={} message={}", examId, exception.getClass().getSimpleName(),
					exception.getMessage());
		}
	}

	public void onModulePublished(UUID moduleId) {
		try {
			dispatch("MODULE_PUBLISHED", queue.enqueueForModule(moduleId));
		}
		catch (RuntimeException exception) {
			log.warn("exam mail enqueue failed moduleId={} error={} message={}", moduleId,
					exception.getClass().getSimpleName(), exception.getMessage());
		}
	}

	public void onRosterAdded(UUID moduleId, UUID studentId) {
		try {
			dispatch("ROSTER_ADDED", queue.enqueueForRoster(moduleId, studentId));
		}
		catch (RuntimeException exception) {
			log.warn("exam mail enqueue failed moduleId={} studentId={} error={} message={}", moduleId, studentId,
					exception.getClass().getSimpleName(), exception.getMessage());
		}
	}

	/**
	 * The exam-time mail after a direct enrolment (D24). Scope is "EXAM" or "MODULE"; the target is the exam or the package.
	 * The caller invokes it after commit. A failure is logged with ids only and never reaches the enrolment.
	 */
	public void onDirectEnrolment(String scope, UUID targetId, UUID studentId) {
		try {
			dispatch("DIRECT_ENROLMENT", queue.enqueueForDirectEnrolment(scope, targetId, studentId));
		}
		catch (RuntimeException exception) {
			log.warn("exam mail enqueue failed scope={} targetId={} studentId={} error={} message={}", scope, targetId, studentId,
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
			if (hourBeforeEnabled) {
				queue.enqueueHourBefore(clock.instant());
			}
			// The safety net runs on the scheduler thread, not the worker, so it never waits behind a publish batch.
			deliver(queue.pendingIds());
		}
		catch (RuntimeException exception) {
			log.warn("exam mail scan failed error={} message={}", exception.getClass().getSimpleName(), exception.getMessage());
		}
		finally {
			running.set(false);
		}
	}

	/**
	 * Hands the rows just queued to the worker, once they are committed. The worker runs in its own transaction, so it cannot see
	 * rows that are still uncommitted here. An empty batch and a full queue both leave the rows pending for the scan.
	 */
	private void dispatch(String trigger, List<UUID> ids) {
		if (ids.isEmpty()) {
			return;
		}
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					submitBatch(trigger, ids);
				}
			});
			return;
		}
		submitBatch(trigger, ids);
	}

	private void submitBatch(String trigger, List<UUID> ids) {
		boolean queued = worker.submit(() -> {
			long started = System.nanoTime();
			for (UUID id : ids) {
				deliverQuietly(id);
			}
			log.info("exam mail batch trigger={} count={} seconds={}", trigger, ids.size(),
					String.format("%.1f", (System.nanoTime() - started) / 1_000_000_000.0));
		});
		if (!queued) {
			log.info("exam mail batch trigger={} count={} left pending for the scan", trigger, ids.size());
		}
	}

	private void deliver(List<UUID> ids) {
		for (UUID id : ids) {
			deliverQuietly(id);
		}
	}

	private void deliverQuietly(UUID id) {
		try {
			delivery.deliverOne(id);
		}
		catch (RuntimeException exception) {
			log.warn("exam mail delivery failed id={} error={} message={}", id, exception.getClass().getSimpleName(),
					exception.getMessage());
		}
	}

}
