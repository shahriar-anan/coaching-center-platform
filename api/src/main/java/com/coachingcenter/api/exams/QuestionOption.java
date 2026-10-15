package com.coachingcenter.api.exams;

import java.util.UUID;

import com.coachingcenter.api.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "question_options")
public class QuestionOption extends BaseEntity {

	@Column(name = "question_id", nullable = false)
	private UUID questionId;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String text;

	@Column(nullable = false)
	private boolean correct;

	@Column(nullable = false)
	private int position;

	public UUID getQuestionId() {
		return questionId;
	}

	public void setQuestionId(UUID questionId) {
		this.questionId = questionId;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public boolean isCorrect() {
		return correct;
	}

	public void setCorrect(boolean correct) {
		this.correct = correct;
	}

	public int getPosition() {
		return position;
	}

	public void setPosition(int position) {
		this.position = position;
	}

}
