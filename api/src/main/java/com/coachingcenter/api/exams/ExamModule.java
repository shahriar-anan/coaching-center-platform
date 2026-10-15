package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.util.UUID;

import com.coachingcenter.api.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "exam_modules")
public class ExamModule extends BaseEntity {

	@Column(name = "title_en", nullable = false, length = 500)
	private String titleEn;

	@Column(name = "title_bn", nullable = false, length = 500)
	private String titleBn;

	@Column(name = "description_en", nullable = false, columnDefinition = "TEXT")
	private String descriptionEn;

	@Column(name = "description_bn", nullable = false, columnDefinition = "TEXT")
	private String descriptionBn;

	@Column(precision = 12, scale = 2)
	private BigDecimal price;

	@Column(nullable = false, length = 16)
	private String status;

	@Column(name = "access_mode", length = 32)
	private String accessMode;

	@Column(name = "course_id")
	private UUID courseId;

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

	public BigDecimal getPrice() {
		return price;
	}

	public void setPrice(BigDecimal price) {
		this.price = price;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getAccessMode() {
		return accessMode;
	}

	public void setAccessMode(String accessMode) {
		this.accessMode = accessMode;
	}

	public UUID getCourseId() {
		return courseId;
	}

	public void setCourseId(UUID courseId) {
		this.courseId = courseId;
	}

}
