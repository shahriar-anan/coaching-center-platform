package com.coachingcenter.api.exams;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** A question the student made final. With "answers can be changed" off, a confirmed question cannot change (NR-01, R9). */
@Entity
@Table(name = "live_exam_answer_confirmations")
@IdClass(LiveExamAnswerConfirmation.Key.class)
public class LiveExamAnswerConfirmation {

	@Id
	@Column(name = "participant_id", nullable = false)
	private UUID participantId;

	@Id
	@Column(name = "question_id", nullable = false)
	private UUID questionId;

	@Column(name = "confirmed_at", nullable = false)
	private Instant confirmedAt;

	public UUID getParticipantId() {
		return participantId;
	}

	public void setParticipantId(UUID participantId) {
		this.participantId = participantId;
	}

	public UUID getQuestionId() {
		return questionId;
	}

	public void setQuestionId(UUID questionId) {
		this.questionId = questionId;
	}

	public Instant getConfirmedAt() {
		return confirmedAt;
	}

	public void setConfirmedAt(Instant confirmedAt) {
		this.confirmedAt = confirmedAt;
	}

	public static class Key implements Serializable {

		private UUID participantId;

		private UUID questionId;

		public Key() {
		}

		public Key(UUID participantId, UUID questionId) {
			this.participantId = participantId;
			this.questionId = questionId;
		}

		@Override
		public boolean equals(Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof Key key)) {
				return false;
			}
			return participantId.equals(key.participantId) && questionId.equals(key.questionId);
		}

		@Override
		public int hashCode() {
			return participantId.hashCode() * 31 + questionId.hashCode();
		}

	}

}
