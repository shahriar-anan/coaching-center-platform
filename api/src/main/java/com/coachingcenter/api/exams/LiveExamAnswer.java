package com.coachingcenter.api.exams;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "live_exam_answers")
@IdClass(LiveExamAnswer.Key.class)
public class LiveExamAnswer {

	@Id
	@Column(name = "participant_id", nullable = false)
	private UUID participantId;

	@Id
	@Column(name = "question_id", nullable = false)
	private UUID questionId;

	@Column(name = "option_id", nullable = false)
	private UUID optionId;

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

	public UUID getOptionId() {
		return optionId;
	}

	public void setOptionId(UUID optionId) {
		this.optionId = optionId;
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
