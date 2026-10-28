package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class LiveExamStudentStatusTest {

	private static final Instant START = Instant.parse("2026-10-20T06:00:00Z");

	private static final Instant END = Instant.parse("2026-10-20T06:40:00Z");

	@Test
	void anUndatedExamIsNotAnnouncedWhateverElseIsTrue() {
		LiveExam exam = exam("MCQ", null);
		assertThat(status(exam, null, START).status()).isEqualTo("NOT_ANNOUNCED");
		assertThat(status(exam, null, END.plusSeconds(3600)).retakeAvailable()).isFalse();
	}

	@Test
	void theWindowBoundariesDecideUpcomingOpenAndMissed() {
		LiveExam exam = exam("MCQ", START);
		assertThat(status(exam, null, START.minusSeconds(1)).status()).isEqualTo("UPCOMING");
		assertThat(status(exam, null, START).status()).isEqualTo("OPEN");
		assertThat(status(exam, null, END.minusSeconds(1)).status()).isEqualTo("OPEN");
		assertThat(status(exam, null, END).status()).isEqualTo("MISSED");
	}

	@Test
	void onlyAMissedMcqOffersTheLaterAttempt() {
		assertThat(status(exam("MCQ", START), null, END).retakeAvailable()).isTrue();
		assertThat(status(exam("WRITTEN", START), null, END).status()).isEqualTo("MISSED");
		assertThat(status(exam("WRITTEN", START), null, END).retakeAvailable()).isFalse();
		assertThat(status(exam("MCQ", START), null, END.minusSeconds(1)).retakeAvailable()).isFalse();
		assertThat(status(exam("MCQ", START), null, START.minusSeconds(1)).retakeAvailable()).isFalse();
	}

	@Test
	void anMcqSittingIsInProgressThenSubmittedThenResultReadyAtTheDeadline() {
		LiveExam exam = exam("MCQ", START);
		LiveExamParticipant open = participant(END, null, null, null, "IN_PROGRESS");
		assertThat(status(exam, open, START.plusSeconds(60)).status()).isEqualTo("IN_PROGRESS");
		LiveExamParticipant early = participant(END, START.plusSeconds(120), null, null, "SUBMITTED");
		assertThat(status(exam, early, END.minusSeconds(1)).status()).isEqualTo("SUBMITTED");
		assertThat(status(exam, early, END).status()).isEqualTo("RESULT_READY");
		assertThat(status(exam, open, END).status()).isEqualTo("RESULT_READY");
		// MCQ retakes are unlimited (D19), so a finished attempt offers the next one.
		assertThat(status(exam, early, END).retakeAvailable()).isTrue();
		assertThat(status(exam, early, END.minusSeconds(1)).retakeAvailable()).isTrue();
		assertThat(status(exam, open, START.plusSeconds(60)).retakeAvailable()).isFalse();
	}

	@Test
	void aLaterAttemptUsesItsOwnPersonalDeadline() {
		LiveExam exam = exam("MCQ", START);
		Instant personalEnd = END.plusSeconds(3600);
		LiveExamParticipant retake = participant(personalEnd, null, null, null, "IN_PROGRESS");
		assertThat(status(exam, retake, END.plusSeconds(60)).status()).isEqualTo("IN_PROGRESS");
		assertThat(status(exam, retake, personalEnd).status()).isEqualTo("RESULT_READY");
	}

	@Test
	void aWrittenSittingWalksFromProgressThroughAwaitingToReady() {
		LiveExam exam = exam("WRITTEN", START);
		LiveExamParticipant none = participant(END, null, null, null, "IN_PROGRESS");
		assertThat(status(exam, none, END.minusSeconds(1)).status()).isEqualTo("IN_PROGRESS");
		LiveExamParticipant stored = participant(END, null, "answer.pdf", null, "IN_PROGRESS");
		assertThat(status(exam, stored, END.minusSeconds(1)).status()).isEqualTo("SUBMITTED");
		assertThat(status(exam, stored, END).status()).isEqualTo("AWAITING_RESULT");
		LiveExamParticipant posted = participant(END, END, "answer.pdf", null, "SUBMITTED");
		assertThat(status(exam, posted, END.plusSeconds(60)).status()).isEqualTo("AWAITING_RESULT");
		LiveExamParticipant published = participant(END, END, "answer.pdf", END.plusSeconds(600), "SUBMITTED");
		assertThat(status(exam, published, END.plusSeconds(900)).status()).isEqualTo("RESULT_READY");
	}

	@Test
	void aWrittenPaperWithNoPdfFailsAtTheDeadlineWithOrWithoutThePostingJob() {
		LiveExam exam = exam("WRITTEN", START);
		LiveExamParticipant unposted = participant(END, null, null, null, "IN_PROGRESS");
		assertThat(status(exam, unposted, END).status()).isEqualTo("FAILED_TO_SUBMIT");
		LiveExamParticipant posted = participant(END, END, null, null, "FAILED_TO_SUBMIT");
		assertThat(status(exam, posted, END.plusSeconds(60)).status()).isEqualTo("FAILED_TO_SUBMIT");
		assertThat(status(exam, posted, END.plusSeconds(60)).retakeAvailable()).isFalse();
	}

	@Test
	void anEndedMcqWithNoAttemptIsArchivedOpenAndLooksMissedToTheCurrentWeb() {
		LiveExamStudentStatus.Result result = status(exam("MCQ", START), null, END);
		assertThat(result.detail()).isEqualTo("ARCHIVED_OPEN");
		assertThat(result.status()).isEqualTo("MISSED");
		assertThat(result.retakeAvailable()).isTrue();
		LiveExamStudentStatus.Result written = status(exam("WRITTEN", START), null, END);
		assertThat(written.detail()).isEqualTo("ARCHIVED_OPEN");
		assertThat(written.status()).isEqualTo("MISSED");
		assertThat(written.retakeAvailable()).isFalse();
		// The detail equals the status for every state the web already knows.
		assertThat(status(exam("MCQ", START), null, START).detail()).isEqualTo("OPEN");
		assertThat(status(exam("MCQ", START), null, START.minusSeconds(1)).detail()).isEqualTo("UPCOMING");
	}

	@Test
	void aPracticeExamIsPracticeOpenFromPublishAndLooksOpenToTheCurrentWeb() {
		LiveExam practice = exam("MCQ", null);
		practice.setMode(LiveExam.MODE_PRACTICE);
		LiveExamStudentStatus.Result result = status(practice, null, END);
		assertThat(result.detail()).isEqualTo("PRACTICE_OPEN");
		assertThat(result.status()).isEqualTo("OPEN");
		assertThat(result.retakeAvailable()).isFalse();
		LiveExamParticipant done = participant(END, END, null, null, "SUBMITTED");
		LiveExamStudentStatus.Result after = status(practice, done, END.plusSeconds(60));
		assertThat(after.status()).isEqualTo("RESULT_READY");
		assertThat(after.retakeAvailable()).isTrue();
	}

	@Test
	void lostAccessIsExpiredAndLooksMissedAndOffersNoAttempt() {
		LiveExam exam = exam("MCQ", START);
		LiveExamStudentStatus.Result none = LiveExamStudentStatus.of(exam, null, END, false, false);
		assertThat(none.detail()).isEqualTo("EXPIRED");
		assertThat(none.status()).isEqualTo("MISSED");
		assertThat(none.retakeAvailable()).isFalse();
		// The student's own results stay readable, so the status of a finished attempt is unchanged; only the retake goes.
		LiveExamParticipant done = participant(END, END, null, null, "SUBMITTED");
		LiveExamStudentStatus.Result kept = LiveExamStudentStatus.of(exam, done, END.plusSeconds(60), false, false);
		assertThat(kept.status()).isEqualTo("RESULT_READY");
		assertThat(kept.retakeAvailable()).isFalse();
	}

	@Test
	void aWrittenRetakeIsAvailableOnlyWithAnUnusedGrantAfterTheFirstAttemptEnded() {
		LiveExam exam = exam("WRITTEN", START);
		LiveExamParticipant first = participant(END, END, "answer.pdf", END.plusSeconds(600), "SUBMITTED");
		Instant later = END.plusSeconds(900);
		assertThat(LiveExamStudentStatus.of(exam, first, later, true, false).retakeAvailable()).isFalse();
		assertThat(LiveExamStudentStatus.of(exam, first, later, true, true).retakeAvailable()).isTrue();
		assertThat(LiveExamStudentStatus.of(exam, first, later, false, true).retakeAvailable()).isFalse();
		LiveExamParticipant running = participant(END, null, "answer.pdf", null, "IN_PROGRESS");
		assertThat(LiveExamStudentStatus.of(exam, running, END.minusSeconds(1), true, true).retakeAvailable()).isFalse();
	}

	@Test
	void aSittingThatStartedInsideTheWindowKeepsRunningPastItsEnd() {
		LiveExam exam = exam("MCQ", START);
		Instant lateStart = END.minusSeconds(60);
		LiveExamParticipant running = participant(lateStart.plusSeconds(40 * 60L), null, null, null, "IN_PROGRESS");
		// The window is over, but this sitting has its own deadline (D15).
		assertThat(status(exam, running, END.plusSeconds(600)).status()).isEqualTo("IN_PROGRESS");
		assertThat(status(exam, running, END.plusSeconds(600)).retakeAvailable()).isFalse();
	}

	private static LiveExamStudentStatus.Result status(LiveExam exam, LiveExamParticipant participant, Instant now) {
		return LiveExamStudentStatus.of(exam, participant, now);
	}

	private static LiveExam exam(String type, Instant liveAt) {
		LiveExam exam = new LiveExam();
		exam.setExamType(type);
		exam.setMode(LiveExam.MODE_LIVE);
		exam.setLiveAt(liveAt);
		// The window ends when the first start would have run out; a start inside it still runs for the full duration.
		exam.setEndsAt(liveAt == null ? null : END);
		exam.setDurationMinutes(40);
		return exam;
	}

	private static LiveExamParticipant participant(Instant deadline, Instant submittedAt, String answerFile,
			Instant publishedAt, String status) {
		LiveExamParticipant participant = new LiveExamParticipant();
		participant.setDeadlineAt(deadline);
		participant.setSubmittedAt(submittedAt);
		participant.setAnswerStoredFileName(answerFile);
		participant.setPublishedAt(publishedAt);
		participant.setStatus(status);
		return participant;
	}

}
