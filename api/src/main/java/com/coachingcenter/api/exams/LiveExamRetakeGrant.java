package com.coachingcenter.api.exams;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A written retake granted on a student's result (NR-01, R14). The retake is used once, and a grant is revoked only while unused. */
@Entity
@Table(name = "live_exam_retake_grants")
public class LiveExamRetakeGrant {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "exam_id", nullable = false)
	private UUID examId;

	@Column(name = "student_id", nullable = false)
	private UUID studentId;

	@Column(name = "granted_by", nullable = false)
	private UUID grantedBy;

	@Column(length = 500)
	private String reason;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "used_participant_id")
	private UUID usedParticipantId;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "revoked_by")
	private UUID revokedBy;

	public UUID getId() {
		return id;
	}

	public UUID getExamId() {
		return examId;
	}

	public void setExamId(UUID examId) {
		this.examId = examId;
	}

	public UUID getStudentId() {
		return studentId;
	}

	public void setStudentId(UUID studentId) {
		this.studentId = studentId;
	}

	public UUID getGrantedBy() {
		return grantedBy;
	}

	public void setGrantedBy(UUID grantedBy) {
		this.grantedBy = grantedBy;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public UUID getUsedParticipantId() {
		return usedParticipantId;
	}

	public void setUsedParticipantId(UUID usedParticipantId) {
		this.usedParticipantId = usedParticipantId;
	}

	public Instant getRevokedAt() {
		return revokedAt;
	}

	public void setRevokedAt(Instant revokedAt) {
		this.revokedAt = revokedAt;
	}

	public UUID getRevokedBy() {
		return revokedBy;
	}

	public void setRevokedBy(UUID revokedBy) {
		this.revokedBy = revokedBy;
	}

}
