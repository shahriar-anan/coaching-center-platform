package com.coachingcenter.api.exams;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "live_exam_grants")
public class LiveExamGrant {

	/** A grant made by a purchase, a bundle purchase, or by hand (V39). */
	public static final String SOURCE_ORDER = "ORDER";

	public static final String SOURCE_MANUAL = "MANUAL";

	public static final String SOURCE_BUNDLE = "BUNDLE";

	@Id
	private UUID id;

	@Column(name = "student_id", nullable = false)
	private UUID studentId;

	@Column(nullable = false, length = 8)
	private String scope;

	@Column(name = "module_id", nullable = false)
	private UUID moduleId;

	@Column(name = "exam_id")
	private UUID examId;

	@Column(name = "order_id")
	private UUID orderId;

	@Column(nullable = false, length = 8)
	private String source;

	@Column(name = "granted_by")
	private UUID grantedBy;

	@Column(length = 500)
	private String reason;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "revoked_by")
	private UUID revokedBy;

	@Column(name = "revoke_reason", length = 500)
	private String revokeReason;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public UUID getStudentId() {
		return studentId;
	}

	public void setStudentId(UUID studentId) {
		this.studentId = studentId;
	}

	public String getScope() {
		return scope;
	}

	public void setScope(String scope) {
		this.scope = scope;
	}

	public UUID getModuleId() {
		return moduleId;
	}

	public void setModuleId(UUID moduleId) {
		this.moduleId = moduleId;
	}

	public UUID getExamId() {
		return examId;
	}

	public void setExamId(UUID examId) {
		this.examId = examId;
	}

	public UUID getOrderId() {
		return orderId;
	}

	public void setOrderId(UUID orderId) {
		this.orderId = orderId;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
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

	public String getRevokeReason() {
		return revokeReason;
	}

	public void setRevokeReason(String revokeReason) {
		this.revokeReason = revokeReason;
	}

}
