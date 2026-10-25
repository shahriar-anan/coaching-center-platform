package com.coachingcenter.api.exams;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

/**
 * Releases or withholds the MCQ correct answers of one exam (R-4). The review that reads the flag is the student read;
 * this service only sets it.
 */
@Service
public class LiveExamAnswerReleaseService {

	private final LiveExamRepository exams;

	private final AuditRecorder audit;

	private final Clock clock;

	public LiveExamAnswerReleaseService(LiveExamRepository exams, AuditRecorder audit, Clock clock) {
		this.exams = exams;
		this.audit = audit;
		this.clock = clock;
	}

	/** Setting the state it already has changes nothing and writes no audit row. */
	@Transactional
	public ReleaseView setReleased(UUID examId, UUID actorId, boolean released) {
		LiveExam exam = exams.findByIdAndDeletedAtIsNull(examId)
			.orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found."));
		boolean current = exam.getAnswersReleasedAt() != null;
		if (released != current) {
			if (released) {
				exam.setAnswersReleasedAt(clock.instant());
				exam.setAnswersReleasedBy(actorId);
				audit.record(AuditRecorder.ANSWERS_RELEASED, AuditRecorder.LIVE_EXAM_ENTITY, exam.getId());
			}
			else {
				exam.setAnswersReleasedAt(null);
				audit.record(AuditRecorder.ANSWERS_WITHHELD, AuditRecorder.LIVE_EXAM_ENTITY, exam.getId());
			}
		}
		return new ReleaseView(exam.getId(), exam.getAnswersReleasedAt() != null, exam.getAnswersReleasedAt());
	}

	/** {@code releasedAt} is null while the answers are withheld. */
	public record ReleaseView(UUID examId, boolean released, Instant releasedAt) {
	}

}
