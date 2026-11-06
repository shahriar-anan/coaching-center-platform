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
 * A photo or the PDF of a public notice (notice media plan, F1). A notice with no rows here shows text only. A photo keeps
 * its row until it is removed; the PDF row is changed in place when it is replaced, because the table allows one PDF per
 * notice. Files are stored under the file storage directory, named by {@link #getStoredName()}.
 */
@Entity
@Table(name = "site_notice_files")
public class SiteNoticeFile {

	public static final String PHOTO = "PHOTO";

	public static final String PDF = "PDF";

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "notice_id", nullable = false, updatable = false)
	private UUID noticeId;

	@Column(nullable = false, length = 8)
	private String kind;

	@Column(nullable = false)
	private short position;

	@Column(name = "stored_name", nullable = false, length = 64)
	private String storedName;

	@Column(name = "original_name", length = 255)
	private String originalName;

	@Column(name = "content_type", nullable = false, length = 64)
	private String contentType;

	@Column(name = "byte_size", nullable = false)
	private long byteSize;

	@Column(name = "created_by")
	private UUID createdBy;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public UUID getId() {
		return id;
	}

	public UUID getNoticeId() {
		return noticeId;
	}

	public void setNoticeId(UUID noticeId) {
		this.noticeId = noticeId;
	}

	public String getKind() {
		return kind;
	}

	public void setKind(String kind) {
		this.kind = kind;
	}

	public short getPosition() {
		return position;
	}

	public void setPosition(short position) {
		this.position = position;
	}

	public String getStoredName() {
		return storedName;
	}

	public void setStoredName(String storedName) {
		this.storedName = storedName;
	}

	public String getOriginalName() {
		return originalName;
	}

	public void setOriginalName(String originalName) {
		this.originalName = originalName;
	}

	public String getContentType() {
		return contentType;
	}

	public void setContentType(String contentType) {
		this.contentType = contentType;
	}

	public long getByteSize() {
		return byteSize;
	}

	public void setByteSize(long byteSize) {
		this.byteSize = byteSize;
	}

	public UUID getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(UUID createdBy) {
		this.createdBy = createdBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

}
