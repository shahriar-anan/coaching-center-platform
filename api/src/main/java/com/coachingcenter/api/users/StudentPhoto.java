package com.coachingcenter.api.users;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A student's optional profile picture. One row per student; no row means no picture. */
@Entity
@Table(name = "student_photos")
public class StudentPhoto {

	@Id
	@Column(name = "user_id")
	private UUID userId;

	@Column(name = "stored_name", nullable = false, length = 64)
	private String storedName;

	@Column(name = "content_type", nullable = false, length = 64)
	private String contentType;

	@Column(name = "byte_size", nullable = false)
	private long byteSize;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public UUID getUserId() {
		return userId;
	}

	public void setUserId(UUID userId) {
		this.userId = userId;
	}

	public String getStoredName() {
		return storedName;
	}

	public void setStoredName(String storedName) {
		this.storedName = storedName;
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

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

}
