package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.util.UUID;

import com.coachingcenter.api.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "mcq_answers")
public class McqAnswer extends BaseEntity {

	@Column(name = "attempt_id", nullable = false)
	private UUID attemptId;

	@Column(name = "attempt_question_id", nullable = false)
	private UUID attemptQuestionId;

	@Column(name = "attempt_option_id")
	private UUID attemptOptionId;

	@Column(name = "marks_awarded", precision = 12, scale = 2)
	private BigDecimal marksAwarded;

	public UUID getAttemptId() {
		return attemptId;
	}

	public void setAttemptId(UUID attemptId) {
		this.attemptId = attemptId;
	}

	public UUID getAttemptQuestionId() {
		return attemptQuestionId;
	}

	public void setAttemptQuestionId(UUID attemptQuestionId) {
		this.attemptQuestionId = attemptQuestionId;
	}

	public UUID getAttemptOptionId() {
		return attemptOptionId;
	}

	public void setAttemptOptionId(UUID attemptOptionId) {
		this.attemptOptionId = attemptOptionId;
	}

	public BigDecimal getMarksAwarded() {
		return marksAwarded;
	}

	public void setMarksAwarded(BigDecimal marksAwarded) {
		this.marksAwarded = marksAwarded;
	}

}
