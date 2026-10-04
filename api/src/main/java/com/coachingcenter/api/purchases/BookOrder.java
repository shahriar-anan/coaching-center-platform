package com.coachingcenter.api.purchases;

import java.util.UUID;

import com.coachingcenter.api.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "book_orders")
public class BookOrder extends BaseEntity {

	@Column(name = "student_id", nullable = false)
	private UUID studentId;

	@Column(name = "book_id", nullable = false)
	private UUID bookId;

	@Column(nullable = false, length = 16)
	private String fulfillment;

	@Column(name = "payment_method", nullable = false, length = 16)
	private String paymentMethod;

	@Column(nullable = false, length = 32)
	private String status;

	@Column(nullable = false, length = 32)
	private String phone;

	@Column(columnDefinition = "TEXT")
	private String address;

	@Column(length = 255)
	private String area;

	public UUID getStudentId() {
		return studentId;
	}

	public void setStudentId(UUID studentId) {
		this.studentId = studentId;
	}

	public UUID getBookId() {
		return bookId;
	}

	public void setBookId(UUID bookId) {
		this.bookId = bookId;
	}

	public String getFulfillment() {
		return fulfillment;
	}

	public void setFulfillment(String fulfillment) {
		this.fulfillment = fulfillment;
	}

	public String getPaymentMethod() {
		return paymentMethod;
	}

	public void setPaymentMethod(String paymentMethod) {
		this.paymentMethod = paymentMethod;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
		this.phone = phone;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public String getArea() {
		return area;
	}

	public void setArea(String area) {
		this.area = area;
	}

}
