package com.coachingcenter.api.exams;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A package's optional routine PDF (notice media plan, F3). One row per package; no row means no PDF. */
@Entity
@Table(name = "live_exam_module_routines")
public class ModuleRoutinePdf {

	@Id
	@Column(name = "module_id", nullable = false)
	private UUID moduleId;

	@Column(name = "stored_name", nullable = false, length = 64)
	private String storedName;

	@Column(name = "original_name", length = 255)
	private String originalName;

	@Column(name = "byte_size", nullable = false)
	private long byteSize;

	@Column(name = "uploaded_by")
	private UUID uploadedBy;

	@Column(name = "uploaded_at", nullable = false)
	private Instant uploadedAt;

	public UUID getModuleId() {
		return moduleId;
	}

	public void setModuleId(UUID moduleId) {
		this.moduleId = moduleId;
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

	public long getByteSize() {
		return byteSize;
	}

	public void setByteSize(long byteSize) {
		this.byteSize = byteSize;
	}

	public UUID getUploadedBy() {
		return uploadedBy;
	}

	public void setUploadedBy(UUID uploadedBy) {
		this.uploadedBy = uploadedBy;
	}

	public Instant getUploadedAt() {
		return uploadedAt;
	}

	public void setUploadedAt(Instant uploadedAt) {
		this.uploadedAt = uploadedAt;
	}

}
