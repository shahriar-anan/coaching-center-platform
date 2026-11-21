package com.coachingcenter.api.files;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.annotation.PreDestroy;

/**
 * Smaller copies of large PDFs, for viewing (plan: docs/exam-site/plans/pdf-viewer-fix-plan.md, Stage 3).
 * <ul>
 * <li>The copy is {@code <stored>.view.pdf} next to the original, written under a temp name and renamed when finished;
 * {@code <stored>.view.none} marks a file that would not shrink. Both go when the original is deleted
 * ({@link LocalFileStorage#delete}). The original is never changed, and the Download buttons still get it.</li>
 * <li>Copies are made by ONE background thread, so an upload's request never waits for one and the second CPU stays
 * free for the site. The thread waits while {@link ViewCopyPause} says so (around exam deadlines).</li>
 * <li>Requests come after an upload commits and from a periodic sweep, which also covers files from before this change
 * and anything lost to a restart. The queue is bounded and deduplicated; a dropped request is picked up by the sweep.</li>
 * </ul>
 */
@Component
public class PdfViewCopies {

	private static final Logger log = LoggerFactory.getLogger(PdfViewCopies.class);

	/** Smaller files open quickly as they are; copying them is not worth the CPU. */
	static final long MIN_BYTES = 3L * 1024 * 1024;

	private static final int QUEUE_LIMIT = 2_000;

	private static final long PAUSE_CHECK_MILLIS = 30_000;

	private final LocalFileStorage files;

	private final PdfViewCopyMaker maker;

	private final ObjectProvider<ViewCopyPause> pauses;

	private final boolean enabled;

	private final Set<String> queued = ConcurrentHashMap.newKeySet();

	private final ThreadPoolExecutor worker;

	public PdfViewCopies(LocalFileStorage files, PdfViewCopyMaker maker, ObjectProvider<ViewCopyPause> pauses,
			@Value("${app.files.view-copy.enabled:true}") boolean enabled) {
		this.files = files;
		this.maker = maker;
		this.pauses = pauses;
		this.enabled = enabled;
		this.worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(QUEUE_LIMIT), runnable -> {
			Thread thread = new Thread(runnable, "pdf-view-copy");
			thread.setDaemon(true);
			thread.setPriority(Thread.MIN_PRIORITY);
			return thread;
		}, new ThreadPoolExecutor.AbortPolicy());
	}

	/**
	 * The file to serve for viewing: the smaller copy when one is ready, otherwise the original. Pass the original's
	 * path, as {@link LocalFileStorage#resolve} gives it.
	 */
	public Path forViewing(Path original) {
		Path copy = original.resolveSibling(LocalFileStorage.viewCopyName(original.getFileName().toString()));
		return Files.isRegularFile(copy) ? copy : original;
	}

	/** Whether {@code path} is a viewing copy rather than an original (for the response header). */
	public static boolean isCopy(Path path) {
		return path.getFileName().toString().endsWith(".view.pdf");
	}

	/** Asks for a copy once the current transaction commits (at once without one). A rolled-back upload is never copied. */
	public void requestAfterCommit(String storedFileName) {
		if (!enabled || storedFileName == null || storedFileName.isBlank()) {
			return;
		}
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					request(storedFileName);
				}
			});
			return;
		}
		request(storedFileName);
	}

	/** Queues a copy if the file is a large PDF without one. Cheap; safe to call for every stored name the sweep finds. */
	public void request(String storedFileName) {
		if (!enabled || storedFileName == null || storedFileName.isBlank() || !needsCopy(storedFileName)) {
			return;
		}
		if (!queued.add(storedFileName)) {
			return;
		}
		try {
			worker.execute(() -> {
				try {
					if (waitWhilePaused()) {
						makeNow(storedFileName);
					}
				}
				finally {
					queued.remove(storedFileName);
				}
			});
		}
		catch (RejectedExecutionException full) {
			// The sweep asks again later.
			queued.remove(storedFileName);
		}
	}

	boolean needsCopy(String storedFileName) {
		try {
			Path original = files.resolve(storedFileName);
			return Files.isRegularFile(original) && Files.size(original) >= MIN_BYTES
					&& !Files.exists(original.resolveSibling(LocalFileStorage.viewCopyName(storedFileName)))
					&& !Files.exists(original.resolveSibling(LocalFileStorage.noGainName(storedFileName)));
		}
		catch (IOException | RuntimeException exception) {
			return false;
		}
	}

	/** Makes the copy on the calling thread. Package-private for tests. */
	PdfViewCopyMaker.Result makeNow(String storedFileName) {
		Path original = files.resolve(storedFileName);
		Path temp = original.resolveSibling(storedFileName + ".view.tmp");
		long started = System.nanoTime();
		PdfViewCopyMaker.Result result = maker.make(original, temp);
		long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
		try {
			if (!Files.exists(original)) {
				// Replaced or deleted while the copy was made: drop it.
				Files.deleteIfExists(temp);
				return result;
			}
			switch (result.outcome()) {
				case MADE -> Files.move(temp, original.resolveSibling(LocalFileStorage.viewCopyName(storedFileName)),
						StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
				// Either way the file is served as it is, and the marker stops the sweep from trying it every few minutes.
				case NO_GAIN, SKIPPED -> {
					Files.deleteIfExists(temp);
					Files.writeString(original.resolveSibling(LocalFileStorage.noGainName(storedFileName)), "");
				}
			}
		}
		catch (IOException exception) {
			log.warn("pdf view copy not saved file={} error={}", storedFileName, exception.getClass().getSimpleName());
			try {
				Files.deleteIfExists(temp);
			}
			catch (IOException ignored) {
				// Never served: only a renamed .view.pdf is.
			}
			return result;
		}
		if (result.outcome() == PdfViewCopyMaker.Outcome.MADE) {
			log.info("pdf view copy made file={} bytes={} copyBytes={} images={} ms={}", storedFileName,
					result.originalBytes(), result.copyBytes(), result.imagesScaled(), millis);
		}
		else {
			log.info("pdf view copy skipped file={} outcome={} reason={} bytes={} ms={}", storedFileName, result.outcome(),
					result.reason(), result.originalBytes(), millis);
		}
		return result;
	}

	/** Waits while copies must pause. False when the wait was interrupted (shutting down): make nothing then. */
	private boolean waitWhilePaused() {
		ViewCopyPause pause = pauses.getIfAvailable();
		if (pause == null) {
			return true;
		}
		try {
			while (pause.pauseNow()) {
				Thread.sleep(PAUSE_CHECK_MILLIS);
			}
			return true;
		}
		catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			return false;
		}
		catch (RuntimeException ignored) {
			// If the check fails, make the copy: it only costs CPU.
			return true;
		}
	}

	@PreDestroy
	void stop() {
		worker.shutdownNow();
	}

	/** Says when copies must wait, for example close to an exam deadline when students are uploading. */
	public interface ViewCopyPause {

		boolean pauseNow();

	}

}
