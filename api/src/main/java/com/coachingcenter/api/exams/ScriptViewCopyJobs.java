package com.coachingcenter.api.exams;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.coachingcenter.api.files.PdfViewCopies;

/**
 * The exam side of the smaller viewing copies ({@link PdfViewCopies}): when they must wait, and which files need one.
 * <ul>
 * <li><b>Pause</b>: while any sitting's deadline is from 10 minutes ahead to 5 minutes past, students are uploading
 * scripts and saving answers, so the copy thread waits (plan decision D4).</li>
 * <li><b>Sweep</b>: every 10 minutes, every stored script (draft, answer, marked, pending marked) and question paper is
 * offered to {@link PdfViewCopies#request}, which skips small files and files already done. This makes copies of the
 * files from before this change, and of anything an upload request missed (a restart, a full queue).</li>
 * </ul>
 */
@Component
class ScriptViewCopyJobs implements PdfViewCopies.ViewCopyPause {

	private static final Logger log = LoggerFactory.getLogger(ScriptViewCopyJobs.class);

	static final Duration PAUSE_BEFORE_DEADLINE = Duration.ofMinutes(10);

	static final Duration PAUSE_AFTER_DEADLINE = Duration.ofMinutes(5);

	private final LiveExamParticipantRepository participants;

	private final LiveExamQuestionPaperRepository papers;

	private final PdfViewCopies copies;

	private final Clock clock;

	@Value("${app.exam-jobs.scheduled:true}")
	private boolean scheduled = true;

	ScriptViewCopyJobs(LiveExamParticipantRepository participants, LiveExamQuestionPaperRepository papers,
			PdfViewCopies copies, Clock clock) {
		this.participants = participants;
		this.papers = papers;
		this.copies = copies;
		this.clock = clock;
	}

	@Override
	public boolean pauseNow() {
		Instant now = clock.instant();
		return participants.existsByDeletedAtIsNullAndDeadlineAtBetween(now.minus(PAUSE_AFTER_DEADLINE),
				now.plus(PAUSE_BEFORE_DEADLINE));
	}

	@Scheduled(fixedDelay = 10, initialDelay = 10, timeUnit = TimeUnit.MINUTES)
	public void run() {
		if (scheduled) {
			sweep();
		}
	}

	/** Offers every stored script and paper. Returns how many names were offered. */
	int sweep() {
		try {
			Set<String> names = new LinkedHashSet<>();
			for (Object[] row : participants.findStoredScriptNames()) {
				for (Object name : row) {
					if (name instanceof String stored && !stored.isBlank()) {
						names.add(stored);
					}
				}
			}
			names.addAll(papers.findStoredPaperNames());
			names.addAll(papers.findStoredExamPaperNames());
			names.forEach(copies::request);
			return names.size();
		}
		catch (RuntimeException exception) {
			log.warn("pdf view copy sweep failed error={}", exception.getClass().getSimpleName());
			return 0;
		}
	}

}
