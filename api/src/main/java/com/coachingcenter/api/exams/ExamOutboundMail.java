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

	/** The receipt of one payment on a direct enrolment's plan. Key: the payment id (plan section 7). */
	public static final String INSTALLMENT_RECEIPT = "INSTALLMENT_RECEIPT";

	/** A reminder that an installment is due within the window. Key: recipient, installment and due date (plan section 7). */
	public static final String INSTALLMENT_DUE_SOON = "INSTALLMENT_DUE_SOON";

	/** A weekly reminder that an installment is overdue. Key: as due soon, plus the ISO week of the reminder. */
	public static final String INSTALLMENT_OVERDUE = "INSTALLMENT_OVERDUE";

	/** Offline admission: the form was approved and the admission is open. Key: student and admission. Payload: the summary. */
	public static final String ADMISSION_APPROVED = "ADMISSION_APPROVED";

	/** Offline admission: the form was sent back. Key: student, admission and the time. Payload: the reason. */
	public static final String ADMISSION_CHANGES_REQUESTED = "ADMISSION_CHANGES_REQUESTED";

	/** Offline admission: the fee or the course end, or the exams and packages, changed. Payload: the old and new values. */
	public static final String ADMISSION_UPDATED = "ADMISSION_UPDATED";

	/** Offline admission: the admission was closed. Payload: the reason and the close date. */
	public static final String ADMISSION_CLOSED = "ADMISSION_CLOSED";

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

	/** The values an admission mail was written with (V85). Null for every other kind. */
	@Column(name = "payload")
	private String payload;

	public String getPayload() {
		return payload;
	}

	public void setPayload(String payload) {
		this.payload = payload;
	}

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
