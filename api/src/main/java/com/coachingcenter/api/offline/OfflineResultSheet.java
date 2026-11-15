package com.coachingcenter.api.offline;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One uploaded result sheet of an exam class (V97, plan section 3). A sheet is STAGED until the Master Admin publishes it; the published one
 * becomes REPLACED when a newer one is published. The file itself is kept beside it, so staff can download what was uploaded.
 */
@Entity
@Table(name = "offline_result_sheets")
public class OfflineResultSheet {

	public static final String STATUS_STAGED = "STAGED";

	public static final String STATUS_PUBLISHED = "PUBLISHED";

	public static final String STATUS_REPLACED = "REPLACED";

	@Id
	private UUID id;

	@Column(name = "class_id", nullable = false)
	private UUID classId;

	@Column(name = "batch_id", nullable = false)
	private UUID batchId;

	@Column(nullable = false, length = 12)
	private String status;

	@Column(name = "exam_type", nullable = false, length = 16)
	private String examType;

	@Column(name = "full_marks", nullable = false)
	private BigDecimal fullMarks;

	@Column(nullable = false)
	private BigDecimal negative;

	@Column(name = "pass_mark")
	private BigDecimal passMark;

	@Column(name = "file_name", nullable = false, length = 500)
	private String fileName;

	@Column(name = "content_type", length = 255)
	private String contentType;

	@Column(name = "byte_size", nullable = false)
	private long byteSize;

	@Column(name = "stored_name", nullable = false, length = 255)
	private String storedName;

	@Column(name = "uploaded_by")
	private UUID uploadedBy;

	@Column(name = "uploaded_at", nullable = false)
	private Instant uploadedAt;

	@Column(name = "published_by")
	private UUID publishedBy;

	@Column(name = "published_at")
	private Instant publishedAt;

	protected OfflineResultSheet() {
	}

	OfflineResultSheet(UUID id, UUID classId, UUID batchId, String examType, BigDecimal fullMarks, BigDecimal negative, BigDecimal passMark,
			String fileName, String contentType, long byteSize, String storedName, UUID uploadedBy, Instant uploadedAt) {
		this.id = id;
		this.classId = classId;
		this.batchId = batchId;
		this.status = STATUS_STAGED;
		this.examType = examType;
		this.fullMarks = fullMarks;
		this.negative = negative;
		this.passMark = passMark;
		this.fileName = fileName;
		this.contentType = contentType;
		this.byteSize = byteSize;
		this.storedName = storedName;
		this.uploadedBy = uploadedBy;
		this.uploadedAt = uploadedAt;
	}

	/** The staged sheet goes live: it is published by {@code by} at {@code at}. */
	void markPublished(UUID by, Instant at) {
		this.status = STATUS_PUBLISHED;
		this.publishedBy = by;
		this.publishedAt = at;
	}

	/** A published sheet that a newer one has replaced. It stays as the record. */
	void markReplaced() {
		this.status = STATUS_REPLACED;
	}

	public UUID getId() {
		return id;
	}

	public UUID getClassId() {
		return classId;
	}

	public UUID getBatchId() {
		return batchId;
	}

	public String getStatus() {
		return status;
	}

	public String getExamType() {
		return examType;
	}

	public BigDecimal getFullMarks() {
		return fullMarks;
	}

	public BigDecimal getNegative() {
		return negative;
	}

	public BigDecimal getPassMark() {
		return passMark;
	}

	public String getFileName() {
		return fileName;
	}

	public String getContentType() {
		return contentType;
	}

	public long getByteSize() {
		return byteSize;
	}

	public String getStoredName() {
		return storedName;
	}

	public UUID getUploadedBy() {
		return uploadedBy;
	}

	public Instant getUploadedAt() {
		return uploadedAt;
	}

	public UUID getPublishedBy() {
		return publishedBy;
	}

	public Instant getPublishedAt() {
		return publishedAt;
	}

}
