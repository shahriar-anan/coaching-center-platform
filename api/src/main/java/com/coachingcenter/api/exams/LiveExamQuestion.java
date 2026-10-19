package com.coachingcenter.api.exams;

import java.util.UUID;

import com.coachingcenter.api.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "live_exam_questions")
public class LiveExamQuestion extends BaseEntity {

	@Column(name = "exam_id", nullable = false)
	private UUID examId;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String stem;

	@Column(nullable = false)
	private int marks;

	@Column(columnDefinition = "TEXT")
	private String explanation;

	@Column(nullable = false)
	private int position;

	public UUID getExamId() {
		return examId;
	}

	public void setExamId(UUID examId) {
		this.examId = examId;
	}

	public String getStem() {
		return stem;
	}

	public void setStem(String stem) {
		this.stem = stem;
	}

	public int getMarks() {
		return marks;
	}

	public void setMarks(int marks) {
		this.marks = marks;
	}

	public String getExplanation() {
		return explanation;
	}

	public void setExplanation(String explanation) {
		this.explanation = explanation;
	}

	public int getPosition() {
		return position;
	}

	public void setPosition(int position) {
		this.position = position;
	}

}
