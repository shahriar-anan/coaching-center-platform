package com.coachingcenter.api.exams;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A student's request for a written retake, decided by the Master Admin or a System Admin. Approving it makes a grant. */
@Entity
@Table(name = "live_exam_retake_requests")
public class LiveExamRetakeRequest {

	public static final String PENDING = "PENDING";

	public static final String APPROVED = "APPROVED";

	public static final String DECLINED = "DECLINED";

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "exam_id", nullable = false)
	private UUID examId;

	@Column(name = "student_id", nullable = false)
	private UUID studentId;

	@Column(nullable = false, length = 16)
	private String status;

	@Column(length = 500)
	private String reason;

	@Column(name = "decision_note", length = 500)
	private String decisionNote;

	@Column(name = "requested_at", nullable = false)
	private Instant requestedAt;

	@Column(name = "decided_at")
	private Instant decidedAt;

	@Column(name = "decided_by")
	private UUID decidedBy;

	@Column(name = "grant_id")
	private UUID grantId;

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

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public String getDecisionNote() {
		return decisionNote;
	}

	public void setDecisionNote(String decisionNote) {
		this.decisionNote = decisionNote;
	}

	public Instant getRequestedAt() {
		return requestedAt;
	}

	public void setRequestedAt(Instant requestedAt) {
		this.requestedAt = requestedAt;
	}

	public Instant getDecidedAt() {
		return decidedAt;
	}

	public void setDecidedAt(Instant decidedAt) {
		this.decidedAt = decidedAt;
	}

	public UUID getDecidedBy() {
		return decidedBy;
	}

	public void setDecidedBy(UUID decidedBy) {
		this.decidedBy = decidedBy;
	}

	public UUID getGrantId() {
		return grantId;
	}

	public void setGrantId(UUID grantId) {
		this.grantId = grantId;
	}

}
