package com.coachingcenter.api.offline;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * One class of a batch's routine (V89). "Held" is not stored: a SCHEDULED class whose end time has passed is held (plan G5, 4.2).
 * {@code amount} is the due amount, kept on the row so a rate change can update the unsettled classes; {@code rateOverride} wins over
 * the instructor's rate when it is set.
 */
@Entity
@Table(name = "offline_classes")
public class OfflineClass {

	public static final String KIND_LECTURE = "LECTURE";

	public static final String KIND_EXAM = "EXAM";

	public static final String KIND_OTHER = "OTHER";

	public static final String STATUS_SCHEDULED = "SCHEDULED";

	public static final String STATUS_PAID_PENDING = "PAID_PENDING";

	public static final String STATUS_PAID = "PAID";

	public static final String STATUS_NOT_TAKEN_PENDING = "NOT_TAKEN_PENDING";

	public static final String STATUS_NOT_TAKEN = "NOT_TAKEN";

	public static final String STATUS_CANCELLED = "CANCELLED";

	@Id
	private UUID id;

	@Column(name = "batch_id", nullable = false)
	private UUID batchId;

	@Column(name = "routine_id")
	private UUID routineId;

	@Column(name = "held_on", nullable = false)
	private LocalDate heldOn;

	@Column(name = "starts_at")
	private LocalTime startsAt;

	@Column(name = "ends_at")
	private LocalTime endsAt;

	@Column(nullable = false, length = 200)
	private String subject;

	@Column(length = 500)
	private String topic;

	@Column(nullable = false, length = 16)
	private String kind;

	@Column(name = "instructor_id")
	private UUID instructorId;

	@Column(name = "instructor_text", length = 200)
	private String instructorText;

	@Column(precision = 12, scale = 2)
	private BigDecimal amount;

	@Column(name = "rate_override", precision = 12, scale = 2)
	private BigDecimal rateOverride;

	@Column(nullable = false, length = 20)
	private String status;

	@Column(name = "status_by")
	private UUID statusBy;

	@Column(name = "status_at")
	private Instant statusAt;

	@Column(name = "cancelled_at")
	private Instant cancelledAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private Long version;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public UUID getBatchId() {
		return batchId;
	}

	public void setBatchId(UUID batchId) {
		this.batchId = batchId;
	}

	public UUID getRoutineId() {
		return routineId;
	}

	public void setRoutineId(UUID routineId) {
		this.routineId = routineId;
	}

	public LocalDate getHeldOn() {
		return heldOn;
	}

	public void setHeldOn(LocalDate heldOn) {
		this.heldOn = heldOn;
	}

	public LocalTime getStartsAt() {
		return startsAt;
	}

	public void setStartsAt(LocalTime startsAt) {
		this.startsAt = startsAt;
	}

	public LocalTime getEndsAt() {
		return endsAt;
	}

	public void setEndsAt(LocalTime endsAt) {
		this.endsAt = endsAt;
	}

	public String getSubject() {
		return subject;
	}

	public void setSubject(String subject) {
		this.subject = subject;
	}

	public String getTopic() {
		return topic;
	}

	public void setTopic(String topic) {
		this.topic = topic;
	}

	public String getKind() {
		return kind;
	}

	public void setKind(String kind) {
		this.kind = kind;
	}

	public UUID getInstructorId() {
		return instructorId;
	}

	public void setInstructorId(UUID instructorId) {
		this.instructorId = instructorId;
	}

	public String getInstructorText() {
		return instructorText;
	}

	public void setInstructorText(String instructorText) {
		this.instructorText = instructorText;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public BigDecimal getRateOverride() {
		return rateOverride;
	}

	public void setRateOverride(BigDecimal rateOverride) {
		this.rateOverride = rateOverride;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public UUID getStatusBy() {
		return statusBy;
	}

	public void setStatusBy(UUID statusBy) {
		this.statusBy = statusBy;
	}

	public Instant getStatusAt() {
		return statusAt;
	}

	public void setStatusAt(Instant statusAt) {
		this.statusAt = statusAt;
	}

	public Instant getCancelledAt() {
		return cancelledAt;
	}

	public void setCancelledAt(Instant cancelledAt) {
		this.cancelledAt = cancelledAt;
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

	public Long getVersion() {
		return version;
	}

	public void setVersion(Long version) {
		this.version = version;
	}

}
