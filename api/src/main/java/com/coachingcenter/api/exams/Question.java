package com.coachingcenter.api.exams;

import java.math.BigDecimal;

import com.coachingcenter.api.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "questions")
public class Question extends BaseEntity {

	@Column(name = "question_type", nullable = false, length = 16)
	private String questionType;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String stem;

	@Column(columnDefinition = "TEXT")
	private String explanation;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal marks;

	@Column(nullable = false, length = 255)
	private String subject;

	public String getQuestionType() {
		return questionType;
	}

	public void setQuestionType(String questionType) {
		this.questionType = questionType;
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

	public String getSubject() {
		return subject;
	}

	public void setSubject(String subject) {
		this.subject = subject;
	}

}
