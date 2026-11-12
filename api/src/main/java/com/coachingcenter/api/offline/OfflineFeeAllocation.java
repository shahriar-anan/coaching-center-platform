package com.coachingcenter.api.offline;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** How much of one payment is applied to one due (V83). */
@Entity
@Table(name = "offline_fee_allocations")
public class OfflineFeeAllocation {

	@Id
	private UUID id;

	@Column(name = "payment_id", nullable = false)
	private UUID paymentId;

	@Column(name = "due_id", nullable = false)
	private UUID dueId;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public UUID getPaymentId() {
		return paymentId;
	}

	public void setPaymentId(UUID paymentId) {
		this.paymentId = paymentId;
	}

	public UUID getDueId() {
		return dueId;
	}

	public void setDueId(UUID dueId) {
		this.dueId = dueId;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

}
