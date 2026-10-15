package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.coachingcenter.api.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "exam_module_purchases")
public class ExamModulePurchase extends BaseEntity {

	@Column(name = "student_id", nullable = false)
	private UUID studentId;

	@Column(name = "module_id", nullable = false)
	private UUID moduleId;

	@Column(nullable = false, length = 32)
	private String status;

	@Column(name = "price_paid", nullable = false, precision = 12, scale = 2)
	private BigDecimal pricePaid;

	@Column(name = "started_at")
	private Instant startedAt;

	public UUID getStudentId() {
		return studentId;
	}

	public void setStudentId(UUID studentId) {
		this.studentId = studentId;
	}

	public UUID getModuleId() {
		return moduleId;
	}

	public void setModuleId(UUID moduleId) {
		this.moduleId = moduleId;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public BigDecimal getPricePaid() {
		return pricePaid;
	}

	public void setPricePaid(BigDecimal pricePaid) {
		this.pricePaid = pricePaid;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public void setStartedAt(Instant startedAt) {
		this.startedAt = startedAt;
	}

}
