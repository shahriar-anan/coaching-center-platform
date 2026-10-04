package com.coachingcenter.api.courses;

import java.math.BigDecimal;
import java.util.UUID;

import com.coachingcenter.api.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "courses")
public class Course extends BaseEntity {

	@Column(name = "title_en", nullable = false, length = 500)
	private String titleEn;

	@Column(name = "title_bn", nullable = false, length = 500)
	private String titleBn;

	@Column(name = "content_language", nullable = false, length = 2)
	private String contentLanguage;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String description;

	@Column(precision = 12, scale = 2)
	private BigDecimal price;

	@Column(name = "delivery_mode", nullable = false, length = 16)
	private String deliveryMode;

	@Column(name = "access_type", nullable = false, length = 16)
	private String accessType;

	@Column(nullable = false, length = 16)
	private String status;

	@Column(nullable = false)
	private boolean closed;

	@Column(name = "opening_number", nullable = false)
	private int openingNumber;

	@Column(name = "cover_file_id")
	private UUID coverFileId;

	@Column(name = "category_id")
	private UUID categoryId;

	@Column(name = "category_position")
	private Integer categoryPosition;

	public String getTitleEn() {
		return titleEn;
	}

	public void setTitleEn(String titleEn) {
		this.titleEn = titleEn;
	}

	public String getTitleBn() {
		return titleBn;
	}

	public void setTitleBn(String titleBn) {
		this.titleBn = titleBn;
	}

	public String getContentLanguage() {
		return contentLanguage;
	}

	public void setContentLanguage(String contentLanguage) {
		this.contentLanguage = contentLanguage;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public void setPrice(BigDecimal price) {
		this.price = price;
	}

	public String getDeliveryMode() {
		return deliveryMode;
	}

	public void setDeliveryMode(String deliveryMode) {
		this.deliveryMode = deliveryMode;
	}

	public String getAccessType() {
		return accessType;
	}

	public void setAccessType(String accessType) {
		this.accessType = accessType;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public boolean isClosed() {
		return closed;
	}

	public void setClosed(boolean closed) {
		this.closed = closed;
	}

	public int getOpeningNumber() {
		return openingNumber;
	}

	public void setOpeningNumber(int openingNumber) {
		this.openingNumber = openingNumber;
	}

	public UUID getCoverFileId() {
		return coverFileId;
	}

	public void setCoverFileId(UUID coverFileId) {
		this.coverFileId = coverFileId;
	}

	public UUID getCategoryId() {
		return categoryId;
	}

	public void setCategoryId(UUID categoryId) {
		this.categoryId = categoryId;
	}

	public Integer getCategoryPosition() {
		return categoryPosition;
	}

	public void setCategoryPosition(Integer categoryPosition) {
		this.categoryPosition = categoryPosition;
	}

}
