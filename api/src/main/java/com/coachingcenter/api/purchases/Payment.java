package com.coachingcenter.api.purchases;

import java.math.BigDecimal;
import java.util.UUID;

import com.coachingcenter.api.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

	@Column(name = "student_id", nullable = false)
	private UUID studentId;

	@Column(name = "payable_type", nullable = false, length = 32)
	private String payableType;

	@Column(name = "payable_id", nullable = false)
	private UUID payableId;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@Column(nullable = false, length = 16)
	private String method;

	@Column(name = "transaction_ref", nullable = false, length = 128)
	private String transactionRef;

	@Column(nullable = false, length = 32)
	private String status;

	public UUID getStudentId() {
		return studentId;
	}

	public void setStudentId(UUID studentId) {
		this.studentId = studentId;
	}

	public String getPayableType() {
		return payableType;
	}

	public void setPayableType(String payableType) {
		this.payableType = payableType;
	}

	public UUID getPayableId() {
		return payableId;
	}

	public void setPayableId(UUID payableId) {
		this.payableId = payableId;
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

	public String getTransactionRef() {
		return transactionRef;
	}

	public void setTransactionRef(String transactionRef) {
		this.transactionRef = transactionRef;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

}
