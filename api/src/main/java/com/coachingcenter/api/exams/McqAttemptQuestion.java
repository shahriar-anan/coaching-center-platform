package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.util.UUID;

import com.coachingcenter.api.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "mcq_attempt_questions")
public class McqAttemptQuestion extends BaseEntity {

	@Column(name = "attempt_id", nullable = false)
	private UUID attemptId;

	@Column(name = "question_id", nullable = false)
	private UUID questionId;

	@Column(nullable = false)
	private int position;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String stem;

	@Column(columnDefinition = "TEXT")
	private String explanation;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal marks;

	public UUID getAttemptId() {
		return attemptId;
	}

	public void setAttemptId(UUID attemptId) {
		this.attemptId = attemptId;
	}

	public UUID getQuestionId() {
		return questionId;
	}

	public void setQuestionId(UUID questionId) {
		this.questionId = questionId;
	}

	public int getPosition() {
		return position;
	}

	public void setPosition(int position) {
		this.position = position;
	}

	public String getStem() {
		return stem;
	}

	public void setStem(String stem) {
		this.stem = stem;
	}

	public String getExplanation() {
		return explanation;
	}

	public void setExplanation(String explanation) {
		this.explanation = explanation;
	}

	public BigDecimal getMarks() {
		return marks;
	}

	public void setMarks(BigDecimal marks) {
		this.marks = marks;
	}

}
