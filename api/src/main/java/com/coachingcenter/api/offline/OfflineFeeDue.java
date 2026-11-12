package com.coachingcenter.api.offline;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One month of the monthly fee schedule for an offline admission (V83). A cancelled due stays as history. */
@Entity
@Table(name = "offline_fee_dues")
public class OfflineFeeDue {

	@Id
	private UUID id;

	@Column(name = "admission_id", nullable = false)
	private UUID admissionId;

	@Column(nullable = false)
	private Short seq;

	@Column(name = "due_on", nullable = false)
	private LocalDate dueOn;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "cancelled_at")
	private Instant cancelledAt;

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

	public Short getSeq() {
		return seq;
	}

	public void setSeq(Short seq) {
		this.seq = seq;
	}

	public LocalDate getDueOn() {
		return dueOn;
	}

	public void setDueOn(LocalDate dueOn) {
		this.dueOn = dueOn;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getCancelledAt() {
		return cancelledAt;
	}

	public void setCancelledAt(Instant cancelledAt) {
		this.cancelledAt = cancelledAt;
	}

}
