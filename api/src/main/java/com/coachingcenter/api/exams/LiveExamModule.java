package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.coachingcenter.api.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "live_exam_modules")
public class LiveExamModule extends BaseEntity {

	public static final String DRAFT = "DRAFT";

	public static final String PUBLISHED = "PUBLISHED";

	public static final String ARCHIVED = "ARCHIVED";

	public static final String MANUAL = "MANUAL";

	@Column(name = "category_id", nullable = false)
	private UUID categoryId;

	@Column(name = "title_en", nullable = false, length = 500)
	private String titleEn;

	@Column(name = "title_bn", nullable = false, length = 500)
	private String titleBn;

	@Column(name = "description_en", nullable = false, columnDefinition = "TEXT")
	private String descriptionEn;

	@Column(name = "description_bn", nullable = false, columnDefinition = "TEXT")
	private String descriptionBn;

	@Column(name = "access_mode", nullable = false, length = 32)
	private String accessMode;

	@Column(nullable = false, length = 16)
	private String status;

	@Column(name = "finally_saved", nullable = false)
	private boolean finallySaved;

	@Column(nullable = false)
	private int position;

	@Column(precision = 12, scale = 2)
	private BigDecimal price;

	@Column(name = "cover_stored_name", length = 80)
	private String coverStoredName;

	@Column(name = "cover_content_type", length = 40)
	private String coverContentType;

	@Column(name = "cover_byte_size")
	private Long coverByteSize;

	@Column(name = "cover_updated_at")
	private Instant coverUpdatedAt;

	@Column(name = "discount_percent")
	private Integer discountPercent;

	@Column(name = "discount_ends_at")
	private Instant discountEndsAt;

	@Column(nullable = false)
	private boolean popular;

	public Integer getDiscountPercent() {
		return discountPercent;
	}

	public void setDiscountPercent(Integer discountPercent) {
		this.discountPercent = discountPercent;
	}

	public Instant getDiscountEndsAt() {
		return discountEndsAt;
	}

	public void setDiscountEndsAt(Instant discountEndsAt) {
		this.discountEndsAt = discountEndsAt;
	}

	public boolean isPopular() {
		return popular;
	}

	public void setPopular(boolean popular) {
		this.popular = popular;
	}

	public UUID getCategoryId() {
		return categoryId;
	}

	public void setCategoryId(UUID categoryId) {
		this.categoryId = categoryId;
	}

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

	public String getDescriptionEn() {
		return descriptionEn;
	}

	public void setDescriptionEn(String descriptionEn) {
		this.descriptionEn = descriptionEn;
	}

	public String getDescriptionBn() {
		return descriptionBn;
	}

	public void setDescriptionBn(String descriptionBn) {
		this.descriptionBn = descriptionBn;
	}

	public String getAccessMode() {
		return accessMode;
	}

	public void setAccessMode(String accessMode) {
		this.accessMode = accessMode;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public boolean isFinallySaved() {
		return finallySaved;
	}

	public void setFinallySaved(boolean finallySaved) {
		this.finallySaved = finallySaved;
	}

	public int getPosition() {
		return position;
	}

	public void setPosition(int position) {
		this.position = position;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public void setPrice(BigDecimal price) {
		this.price = price;
	}


	public String getCoverStoredName() {
		return coverStoredName;
	}

	public void setCoverStoredName(String coverStoredName) {
		this.coverStoredName = coverStoredName;
	}

	public String getCoverContentType() {
		return coverContentType;
	}

	public void setCoverContentType(String coverContentType) {
		this.coverContentType = coverContentType;
	}

	public Long getCoverByteSize() {
		return coverByteSize;
	}

	public void setCoverByteSize(Long coverByteSize) {
		this.coverByteSize = coverByteSize;
	}

	public Instant getCoverUpdatedAt() {
		return coverUpdatedAt;
	}

	public void setCoverUpdatedAt(Instant coverUpdatedAt) {
		this.coverUpdatedAt = coverUpdatedAt;
	}
}
