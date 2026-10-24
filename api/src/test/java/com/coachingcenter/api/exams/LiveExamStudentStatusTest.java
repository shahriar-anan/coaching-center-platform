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
		assertThat(status(exam, early, END).retakeAvailable()).isFalse();
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

	private static LiveExamStudentStatus.Result status(LiveExam exam, LiveExamParticipant participant, Instant now) {
		return LiveExamStudentStatus.of(exam, participant, now);
	}

	private static LiveExam exam(String type, Instant liveAt) {
		LiveExam exam = new LiveExam();
		exam.setExamType(type);
		exam.setLiveAt(liveAt);
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
