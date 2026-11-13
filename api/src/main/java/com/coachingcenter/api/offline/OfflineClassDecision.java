package com.coachingcenter.api.offline;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** A "not taken" request for one class and its approval (V90). Kept apart from the payments, so the money trail stays clean. */
@Entity
@Table(name = "offline_class_decisions")
public class OfflineClassDecision {

	public static final String STATUS_PENDING = "PENDING";

	public static final String STATUS_APPROVED = "APPROVED";

	public static final String STATUS_REJECTED = "REJECTED";

	@Id
	private UUID id;

	@Column(name = "class_id", nullable = false)
	private UUID classId;

	@Column(name = "requested_by", nullable = false)
	private UUID requestedBy;

	@Column(name = "requested_at", nullable = false)
	private Instant requestedAt;

	@Column(nullable = false, length = 500)
	private String reason;

	@Column(nullable = false, length = 16)
	private String status;

	@Column(name = "decided_by")
	private UUID decidedBy;

	@Column(name = "decided_at")
	private Instant decidedAt;

	@Column(name = "decision_reason", length = 500)
	private String decisionReason;

	@Version
	@Column(nullable = false)
	private Long version;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public UUID getClassId() {
		return classId;
	}

	public void setClassId(UUID classId) {
		this.classId = classId;
	}

	public UUID getRequestedBy() {
		return requestedBy;
	}

	public void setRequestedBy(UUID requestedBy) {
		this.requestedBy = requestedBy;
	}

	public Instant getRequestedAt() {
		return requestedAt;
	}

	public void setRequestedAt(Instant requestedAt) {
		this.requestedAt = requestedAt;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public UUID getDecidedBy() {
		return decidedBy;
	}

	public void setDecidedBy(UUID decidedBy) {
		this.decidedBy = decidedBy;
	}

	public Instant getDecidedAt() {
		return decidedAt;
	}

	public void setDecidedAt(Instant decidedAt) {
		this.decidedAt = decidedAt;
	}

	public String getDecisionReason() {
		return decisionReason;
	}

	public void setDecisionReason(String decisionReason) {
		this.decisionReason = decisionReason;
	}

	public Long getVersion() {
		return version;
	}

	public void setVersion(Long version) {
		this.version = version;
	}

}
