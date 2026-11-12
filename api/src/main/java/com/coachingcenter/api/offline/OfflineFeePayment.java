package com.coachingcenter.api.offline;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** A payment taken in person from an offline student (V83). Receipt numbers are never reused, even after a void. */
@Entity
@Table(name = "offline_fee_payments")
public class OfflineFeePayment {

	public static final String METHOD_CASH = "CASH";

	public static final String METHOD_BKASH = "BKASH";

	public static final String METHOD_NAGAD = "NAGAD";

	public static final String METHOD_ROCKET = "ROCKET";

	public static final String METHOD_BANK = "BANK";

	public static final String STATUS_PENDING = "PENDING";

	public static final String STATUS_APPROVED = "APPROVED";

	public static final String STATUS_REJECTED = "REJECTED";

	public static final String STATUS_VOIDED = "VOIDED";

	@Id
	private UUID id;

	@Column(name = "admission_id", nullable = false)
	private UUID admissionId;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@Column(nullable = false, length = 16)
	private String method;

	@Column(nullable = false, length = 100)
	private String reference;

	@Column(name = "paid_on", nullable = false)
	private LocalDate paidOn;

	@Column(length = 500)
	private String note;

	@Column(nullable = false, length = 16)
	private String status;

	@Column(name = "recorded_by", nullable = false)
	private UUID recordedBy;

	@Column(name = "recorded_at", nullable = false)
	private Instant recordedAt;

	@Column(name = "decided_by")
	private UUID decidedBy;

	@Column(name = "decided_at")
	private Instant decidedAt;

	@Column(name = "decision_reason", length = 500)
	private String decisionReason;

	@Column(name = "receipt_no", unique = true, length = 24)
	private String receiptNo;

	@Column(name = "voided_by")
	private UUID voidedBy;

	@Column(name = "voided_at")
	private Instant voidedAt;

	@Column(name = "void_reason", length = 500)
	private String voidReason;

	@Version
	@Column(nullable = false)
	private Long version;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public UUID getAdmissionId() {
		return admissionId;
	}

	public void setAdmissionId(UUID admissionId) {
		this.admissionId = admissionId;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public String getMethod() {
		return method;
	}

	public void setMethod(String method) {
		this.method = method;
	}

	public String getReference() {
		return reference;
	}

	public void setReference(String reference) {
		this.reference = reference;
	}

	public LocalDate getPaidOn() {
		return paidOn;
	}

	public void setPaidOn(LocalDate paidOn) {
		this.paidOn = paidOn;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public UUID getRecordedBy() {
		return recordedBy;
	}

	public void setRecordedBy(UUID recordedBy) {
		this.recordedBy = recordedBy;
	}

	public Instant getRecordedAt() {
		return recordedAt;
	}

	public void setRecordedAt(Instant recordedAt) {
		this.recordedAt = recordedAt;
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

	public String getReceiptNo() {
		return receiptNo;
	}

	public void setReceiptNo(String receiptNo) {
		this.receiptNo = receiptNo;
	}

	public UUID getVoidedBy() {
		return voidedBy;
	}

	public void setVoidedBy(UUID voidedBy) {
		this.voidedBy = voidedBy;
	}

	public Instant getVoidedAt() {
		return voidedAt;
	}

	public void setVoidedAt(Instant voidedAt) {
		this.voidedAt = voidedAt;
	}

	public String getVoidReason() {
		return voidReason;
	}

	public void setVoidReason(String voidReason) {
		this.voidReason = voidReason;
	}

	public Long getVersion() {
		return version;
	}

	public void setVersion(Long version) {
		this.version = version;
	}

}
