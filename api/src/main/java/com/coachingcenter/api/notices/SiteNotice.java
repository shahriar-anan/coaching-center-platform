package com.coachingcenter.api.notices;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A notice on the public notice board (refunds, stats and notice board plan, section 6). Drafts may be incomplete; a notice
 * that has been published keeps all four texts. Text is plain: the website shows it as text, never as HTML.
 */
@Entity
@Table(name = "site_notices")
public class SiteNotice {

	public static final String DRAFT = "DRAFT";

	public static final String PUBLISHED = "PUBLISHED";

	public static final String UNPUBLISHED = "UNPUBLISHED";

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "title_en", length = 300)
	private String titleEn;

	@Column(name = "title_bn", length = 300)
	private String titleBn;

	@Column(name = "body_en", columnDefinition = "TEXT")
	private String bodyEn;

	@Column(name = "body_bn", columnDefinition = "TEXT")
	private String bodyBn;

	@Column(nullable = false, length = 16)
	private String status;

	@Column(name = "created_by")
	private UUID createdBy;

	@Column(name = "updated_by")
	private UUID updatedBy;

	@Column(name = "published_by")
	private UUID publishedBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "published_at")
	private Instant publishedAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	public UUID getId() {
		return id;
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

	public String getBodyEn() {
		return bodyEn;
	}

	public void setBodyEn(String bodyEn) {
		this.bodyEn = bodyEn;
	}

	public String getBodyBn() {
		return bodyBn;
	}

	public void setBodyBn(String bodyBn) {
		this.bodyBn = bodyBn;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public UUID getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(UUID createdBy) {
		this.createdBy = createdBy;
	}

	public UUID getUpdatedBy() {
		return updatedBy;
	}

	public void setUpdatedBy(UUID updatedBy) {
		this.updatedBy = updatedBy;
	}

	public UUID getPublishedBy() {
		return publishedBy;
	}

	public void setPublishedBy(UUID publishedBy) {
		this.publishedBy = publishedBy;
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

	public Instant getPublishedAt() {
		return publishedAt;
	}

	public void setPublishedAt(Instant publishedAt) {
		this.publishedAt = publishedAt;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

	public void setDeletedAt(Instant deletedAt) {
		this.deletedAt = deletedAt;
	}

}
