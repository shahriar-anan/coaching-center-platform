package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "live_exam_orders")
public class LiveExamOrder {

	public static final String MODULE = "MODULE";

	public static final String EXAM = "EXAM";

	/** A paid order for a package as a whole, sold as a bundle (NR-01). */
	public static final String BUNDLE = "BUNDLE";

	public static final String INITIATED = "INITIATED";

	public static final String SUCCEEDED = "SUCCEEDED";

	public static final String FAILED = "FAILED";

	public static final String CANCELLED = "CANCELLED";

	public static final String SSLCOMMERZ = "SSLCOMMERZ";

	public static final String BDT = "BDT";

	@Id
	private UUID id;

	@Column(name = "student_id", nullable = false)
	private UUID studentId;

	@Column(nullable = false, length = 8)
	private String scope;

	@Column(name = "module_id")
	private UUID moduleId;

	@Column(name = "exam_id")
	private UUID examId;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@Column(nullable = false, length = 3)
	@JdbcTypeCode(SqlTypes.CHAR)
	private String currency;

	@Column(nullable = false, length = 16)
	private String method;

	@Column(name = "tran_id", nullable = false, length = 30)
	private String tranId;

	@Column(nullable = false, length = 16)
	private String status;

	@Column(name = "val_id", length = 128)
	private String valId;

	@Column(name = "payment_channel", length = 64)
	private String paymentChannel;

	/** The price the student saw at checkout, before the discount (NR-01, R19). */
	@Column(name = "list_price", precision = 12, scale = 2)
	private BigDecimal listPrice;

	@Column(name = "discount_percent")
	private Integer discountPercent;

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

	public UUID getStudentId() {
		return studentId;
	}

	public void setStudentId(UUID studentId) {
		this.studentId = studentId;
	}

	public String getScope() {
		return scope;
	}

	public void setScope(String scope) {
		this.scope = scope;
	}

	public UUID getModuleId() {
		return moduleId;
	}

	public void setModuleId(UUID moduleId) {
		this.moduleId = moduleId;
	}

	public UUID getExamId() {
		return examId;
	}

	public void setExamId(UUID examId) {
		this.examId = examId;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public String getMethod() {
		return method;
	}

	public void setMethod(String method) {
		this.method = method;
	}

	public String getTranId() {
		return tranId;
	}

	public void setTranId(String tranId) {
		this.tranId = tranId;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getValId() {
		return valId;
	}

	public void setValId(String valId) {
		this.valId = valId;
	}

	public BigDecimal getListPrice() {
		return listPrice;
	}

	public void setListPrice(BigDecimal listPrice) {
		this.listPrice = listPrice;
	}

	public Integer getDiscountPercent() {
		return discountPercent;
	}

	public void setDiscountPercent(Integer discountPercent) {
		this.discountPercent = discountPercent;
	}

	public String getPaymentChannel() {
		return paymentChannel;
	}

	public void setPaymentChannel(String paymentChannel) {
		this.paymentChannel = paymentChannel;
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


	/** When the order became paid (V52). Set once, in the transaction that marks it SUCCEEDED. */
	@Column(name = "paid_at")
	private Instant paidAt;

	/** The receipt number of a paid order, such as RCPT-2026-000123 (V52). */
	@Column(name = "receipt_no", length = 24)
	private String receiptNo;

	public Instant getPaidAt() {
		return paidAt;
	}

	public void setPaidAt(Instant paidAt) {
		this.paidAt = paidAt;
	}

	public String getReceiptNo() {
		return receiptNo;
	}

	public void setReceiptNo(String receiptNo) {
		this.receiptNo = receiptNo;
	}

	/** The receipt address the student typed for this order (V54), or null: the account email is used when verified. */
	@Column(name = "receipt_email", length = 254)
	private String receiptEmail;

	public String getReceiptEmail() {
		return receiptEmail;
	}

	public void setReceiptEmail(String receiptEmail) {
		this.receiptEmail = receiptEmail;
	}

	/** Set when Master Admin confirms a pending order after checking the payment (V39). */
	@Column(name = "confirmed_by")
	private UUID confirmedBy;

	@Column(name = "confirm_reason", length = 500)
	private String confirmReason;

	@Column(name = "rejected_reason", length = 500)
	private String rejectedReason;

	public UUID getConfirmedBy() {
		return confirmedBy;
	}

	public void setConfirmedBy(UUID confirmedBy) {
		this.confirmedBy = confirmedBy;
	}

	public String getConfirmReason() {
		return confirmReason;
	}

	public void setConfirmReason(String confirmReason) {
		this.confirmReason = confirmReason;
	}

	public String getRejectedReason() {
		return rejectedReason;
	}

	public void setRejectedReason(String rejectedReason) {
		this.rejectedReason = rejectedReason;
	}

}
