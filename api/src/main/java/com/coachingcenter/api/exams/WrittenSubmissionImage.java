package com.coachingcenter.api.exams;

import java.util.UUID;

import com.coachingcenter.api.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "written_submission_images")
public class WrittenSubmissionImage extends BaseEntity {

	@Column(name = "submission_id", nullable = false)
	private UUID submissionId;

	@Column(name = "stored_file_name", nullable = false)
	private String storedFileName;

	@Column(name = "original_file_name", nullable = false)
	private String originalFileName;

	@Column(name = "byte_size", nullable = false)
	private long byteSize;

	@Column(name = "content_type", nullable = false, length = 64)
	private String contentType;

	@Column(nullable = false)
	private int position;

	public UUID getSubmissionId() {
		return submissionId;
	}

	public void setSubmissionId(UUID submissionId) {
		this.submissionId = submissionId;
	}

	public String getStoredFileName() {
		return storedFileName;
	}

	public void setStoredFileName(String storedFileName) {
		this.storedFileName = storedFileName;
	}

	public String getOriginalFileName() {
		return originalFileName;
	}

	public void setOriginalFileName(String originalFileName) {
		this.originalFileName = originalFileName;
	}

	public long getByteSize() {
		return byteSize;
	}

	public void setByteSize(long byteSize) {
		this.byteSize = byteSize;
	}

	public String getContentType() {
		return contentType;
	}

	public void setContentType(String contentType) {
		this.contentType = contentType;
	}

	public int getPosition() {
		return position;
	}

	public void setPosition(int position) {
		this.position = position;
	}

}
