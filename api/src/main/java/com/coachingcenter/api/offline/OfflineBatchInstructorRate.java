package com.coachingcenter.api.offline;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** The default pay rate of one instructor within one batch (V88). A class takes it unless the class has its own override (plan F7). */
@Entity
@Table(name = "offline_batch_instructor_rates")
@IdClass(OfflineBatchInstructorRate.Key.class)
public class OfflineBatchInstructorRate {

	@Id
	@Column(name = "batch_id", nullable = false)
	private UUID batchId;

	@Id
	@Column(name = "instructor_id", nullable = false)
	private UUID instructorId;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal rate;

	@Column(name = "updated_by")
	private UUID updatedBy;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public UUID getBatchId() {
		return batchId;
	}

	public void setBatchId(UUID batchId) {
		this.batchId = batchId;
	}

	public UUID getInstructorId() {
		return instructorId;
	}

	public void setInstructorId(UUID instructorId) {
		this.instructorId = instructorId;
	}

	public BigDecimal getRate() {
		return rate;
	}

	public void setRate(BigDecimal rate) {
		this.rate = rate;
	}

	public UUID getUpdatedBy() {
		return updatedBy;
	}

	public void setUpdatedBy(UUID updatedBy) {
		this.updatedBy = updatedBy;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

	public static class Key implements Serializable {

		private UUID batchId;

		private UUID instructorId;

		public Key() {
		}

		public Key(UUID batchId, UUID instructorId) {
			this.batchId = batchId;
			this.instructorId = instructorId;
		}

		public UUID getBatchId() {
			return batchId;
		}

		public void setBatchId(UUID batchId) {
			this.batchId = batchId;
		}

		public UUID getInstructorId() {
			return instructorId;
		}

		public void setInstructorId(UUID instructorId) {
			this.instructorId = instructorId;
		}

		@Override
		public boolean equals(Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof Key key)) {
				return false;
			}
			return batchId.equals(key.batchId) && instructorId.equals(key.instructorId);
		}

		@Override
		public int hashCode() {
			return batchId.hashCode() * 31 + instructorId.hashCode();
		}

	}

}
