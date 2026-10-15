package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.coachingcenter.api.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "mcq_attempts")
public class McqAttempt extends BaseEntity {

	@Column(name = "component_id", nullable = false)
	private UUID componentId;

	@Column(name = "student_id", nullable = false)
	private UUID studentId;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt;

	@Column(name = "duration_seconds", nullable = false)
	private int durationSeconds;

	@Column(name = "submitted_at")
	private Instant submittedAt;

	@Column(precision = 12, scale = 2)
	private BigDecimal score;

	@Column(name = "max_score", nullable = false, precision = 12, scale = 2)
	private BigDecimal maxScore;

	@Column(name = "pass_percent")
	private Integer passPercent;

	@Column(nullable = false, length = 16)
	private String status;

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

	public Instant getStartedAt() {
		return startedAt;
	}

	public void setStartedAt(Instant startedAt) {
		this.startedAt = startedAt;
	}

	public int getDurationSeconds() {
		return durationSeconds;
	}

	public void setDurationSeconds(int durationSeconds) {
		this.durationSeconds = durationSeconds;
	}

	public Instant getSubmittedAt() {
		return submittedAt;
	}

	public void setSubmittedAt(Instant submittedAt) {
		this.submittedAt = submittedAt;
	}

	public BigDecimal getScore() {
		return score;
	}

	public void setScore(BigDecimal score) {
		this.score = score;
	}

	public BigDecimal getMaxScore() {
		return maxScore;
	}

	public void setMaxScore(BigDecimal maxScore) {
		this.maxScore = maxScore;
	}

	public Integer getPassPercent() {
		return passPercent;
	}

	public void setPassPercent(Integer passPercent) {
		this.passPercent = passPercent;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

}
