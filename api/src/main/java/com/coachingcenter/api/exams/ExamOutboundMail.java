package com.coachingcenter.api.exams;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "exam_outbound_mail")
public class ExamOutboundMail {

	public static final String PAYMENT_RECEIPT = "PAYMENT_RECEIPT";

	public static final String EXAM_TIME = "EXAM_TIME";

	public static final String HOUR_BEFORE = "HOUR_BEFORE";

	public static final String REFUND = "REFUND";

	public static final String PURCHASE_NOTICE = "PURCHASE_NOTICE";

	public static final String PENDING = "PENDING";

	public static final String SENT = "SENT";

	public static final String SKIPPED = "SKIPPED";

	public static final String FAILED = "FAILED";

	@Id
	private UUID id;

	@Column(nullable = false, length = 32)
	private String kind;

	@Column(name = "message_key", nullable = false, length = 220)
	private String messageKey;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	@Column(nullable = false, length = 16)
	private String status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public String getKind() {
		return kind;
	}

	public void setKind(String kind) {
		this.kind = kind;
	}

	public String getMessageKey() {
		return messageKey;
	}

	public void setMessageKey(String messageKey) {
		this.messageKey = messageKey;
	}

	public UUID getUserId() {
		return userId;
	}

	public void setUserId(UUID userId) {
		this.userId = userId;
	}

	public int getAttemptCount() {
		return attemptCount;
	}

	public void setAttemptCount(int attemptCount) {
		this.attemptCount = attemptCount;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

}
