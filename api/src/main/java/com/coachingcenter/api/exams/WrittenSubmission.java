package com.coachingcenter.api.exams;

import java.time.Instant;
import java.util.UUID;

import com.coachingcenter.api.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "written_submissions")
public class WrittenSubmission extends BaseEntity {

	@Column(name = "component_id", nullable = false)
	private UUID componentId;

	@Column(name = "student_id", nullable = false)
	private UUID studentId;

	@Column(nullable = false, length = 16)
	private String status;

	@Column(name = "submitted_at")
	private Instant submittedAt;

	@Column(name = "pass_percent")
	private Integer passPercent;

	@Column(columnDefinition = "TEXT")
	private String feedback;

	@Column(name = "evaluated_at")
	private Instant evaluatedAt;

	@Column(name = "evaluated_by")
	private UUID evaluatedBy;

	public UUID getComponentId() {
		return componentId;
	}

	public void setComponentId(UUID componentId) {
		this.componentId = componentId;
	}

	public UUID getStudentId() {
		return studentId;
	}

	public void setStudentId(UUID studentId) {
		this.studentId = studentId;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Instant getSubmittedAt() {
		return submittedAt;
	}

	public void setSubmittedAt(Instant submittedAt) {
		this.submittedAt = submittedAt;
	}

	public Integer getPassPercent() {
		return passPercent;
	}

	public void setPassPercent(Integer passPercent) {
		this.passPercent = passPercent;
	}

	public String getFeedback() {
		return feedback;
	}

	public void setFeedback(String feedback) {
		this.feedback = feedback;
	}

	public Instant getEvaluatedAt() {
		return evaluatedAt;
	}

	public void setEvaluatedAt(Instant evaluatedAt) {
		this.evaluatedAt = evaluatedAt;
	}

	public UUID getEvaluatedBy() {
		return evaluatedBy;
	}

	public void setEvaluatedBy(UUID evaluatedBy) {
		this.evaluatedBy = evaluatedBy;
	}

}
