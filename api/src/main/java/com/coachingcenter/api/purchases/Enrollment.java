package com.coachingcenter.api.purchases;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.coachingcenter.api.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "enrollments")
public class Enrollment extends BaseEntity {

	@Column(name = "student_id", nullable = false)
	private UUID studentId;

	@Column(name = "course_id", nullable = false)
	private UUID courseId;

	@Column(name = "opening_number", nullable = false)
	private int openingNumber;

	@Column(name = "delivery_mode", nullable = false, length = 16)
	private String deliveryMode;

	@Column(nullable = false, length = 16)
	private String source;

	@Column(nullable = false, length = 32)
	private String status;

	@Column(name = "price_paid", nullable = false, precision = 12, scale = 2)
	private BigDecimal pricePaid;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt;

	public UUID getStudentId() {
		return studentId;
	}

	public void setStudentId(UUID studentId) {
		this.studentId = studentId;
	}

	public UUID getCourseId() {
		return courseId;
	}

	public void setCourseId(UUID courseId) {
		this.courseId = courseId;
	}

	public int getOpeningNumber() {
		return openingNumber;
	}

	public void setOpeningNumber(int openingNumber) {
		this.openingNumber = openingNumber;
	}

	public String getDeliveryMode() {
		return deliveryMode;
	}

	public void setDeliveryMode(String deliveryMode) {
		this.deliveryMode = deliveryMode;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
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
