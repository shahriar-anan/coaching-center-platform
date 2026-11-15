package com.coachingcenter.api.exams;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Sends exam mail off the request thread. One thread takes one batch at a time, so the mail provider sees a steady trickle instead
 * of a burst. A batch that does not fit the queue is refused; its rows stay pending and the five-minute scan sends them.
 */
@Component
class ExamMailWorker implements DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(ExamMailWorker.class);

	/** Batches waiting behind the one being sent. A publish is one batch, so this is a count of publishes, not of students. */
	static final int QUEUE_CAPACITY = 200;

	private static final long SHUTDOWN_WAIT_SECONDS = 30;

	/** Null when sending happens on the calling thread (tests, or {@code app.exam-mail.async=false}). */
	private final ThreadPoolExecutor pool;

	@Autowired
	ExamMailWorker(@Value("${app.exam-mail.async:true}") boolean async) {
		this(async ? newPool() : null);
	}

	private ExamMailWorker(ThreadPoolExecutor pool) {
		this.pool = pool;
	}

	/** A worker that runs every job on the calling thread. */
	static ExamMailWorker inline() {
		return new ExamMailWorker((ThreadPoolExecutor) null);
	}

	/** Whether jobs run on the calling thread (tests, or {@code app.exam-mail.async=false}). */
	boolean runsInline() {
		return pool == null;
	}

	/** Returns false when the batch was refused because the queue is full. The rows stay pending for the scan. */
	boolean submit(Runnable batch) {
		if (pool == null) {
			batch.run();
			return true;
		}
		try {
			pool.execute(batch);
			return true;
		}
		catch (RejectedExecutionException exception) {
			log.warn("exam mail queue full; the scan will send this batch");
			return false;
		}
	}

	@Override
	public void destroy() {
		if (pool == null) {
			return;
		}
		pool.shutdown();
		try {
			if (!pool.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
				log.warn("exam mail worker still busy at shutdown; unsent rows will be sent by the scan");
			}
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		}
	}

	private static ThreadPoolExecutor newPool() {
		return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(QUEUE_CAPACITY), runnable -> {
			Thread thread = new Thread(runnable, "exam-mail-worker");
			thread.setDaemon(true);
			return thread;
		});
	}

}
