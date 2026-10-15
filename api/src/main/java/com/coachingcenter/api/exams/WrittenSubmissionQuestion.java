package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.util.UUID;

import com.coachingcenter.api.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "written_submission_questions")
public class WrittenSubmissionQuestion extends BaseEntity {

	@Column(name = "submission_id", nullable = false)
	private UUID submissionId;

	@Column(name = "question_id", nullable = false)
	private UUID questionId;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String stem;

	@Column(columnDefinition = "TEXT")
	private String explanation;

	@Column(name = "max_marks", nullable = false, precision = 12, scale = 2)
	private BigDecimal maxMarks;

	@Column(nullable = false)
	private int position;

	@Column(name = "marks_awarded", precision = 12, scale = 2)
	private BigDecimal marksAwarded;

	public UUID getSubmissionId() {
		return submissionId;
	}

	public void setSubmissionId(UUID submissionId) {
		this.submissionId = submissionId;
	}

	public UUID getQuestionId() {
		return questionId;
	}

	public void setQuestionId(UUID questionId) {
		this.questionId = questionId;
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

	public BigDecimal getMaxMarks() {
		return maxMarks;
	}

	public void setMaxMarks(BigDecimal maxMarks) {
		this.maxMarks = maxMarks;
	}

	public int getPosition() {
		return position;
	}

	public void setPosition(int position) {
		this.position = position;
	}

	public BigDecimal getMarksAwarded() {
		return marksAwarded;
	}

	public void setMarksAwarded(BigDecimal marksAwarded) {
		this.marksAwarded = marksAwarded;
	}

}
