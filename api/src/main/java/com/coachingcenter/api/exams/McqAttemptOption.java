package com.coachingcenter.api.exams;

import java.util.UUID;

import com.coachingcenter.api.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "mcq_attempt_options")
public class McqAttemptOption extends BaseEntity {

	@Column(name = "attempt_question_id", nullable = false)
	private UUID attemptQuestionId;

	@Column(name = "source_option_id", nullable = false)
	private UUID sourceOptionId;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String text;

	@Column(nullable = false)
	private int position;

	@Column(nullable = false)
	private boolean correct;

	public UUID getAttemptQuestionId() {
		return attemptQuestionId;
	}

	public void setAttemptQuestionId(UUID attemptQuestionId) {
		this.attemptQuestionId = attemptQuestionId;
	}

	public UUID getSourceOptionId() {
		return sourceOptionId;
	}

	public void setSourceOptionId(UUID sourceOptionId) {
		this.sourceOptionId = sourceOptionId;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public int getPosition() {
		return position;
	}

	public void setPosition(int position) {
		this.position = position;
	}

	public boolean isCorrect() {
		return correct;
	}

	public void setCorrect(boolean correct) {
		this.correct = correct;
	}

}
