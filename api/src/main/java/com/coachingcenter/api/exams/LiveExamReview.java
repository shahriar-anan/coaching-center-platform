package com.coachingcenter.api.exams;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A student's rating of one exam or one package, with an optional comment (reviews plan, section 5). Exactly one of
 * {@code examId} and {@code moduleId} is set. Not a {@code BaseEntity}: a review is never soft-deleted, the student removes it.
 */
@Entity
@Table(name = "live_exam_reviews")
public class LiveExamReview {

	/** A comment waits for staff. Stars only (no comment) are approved at once. */
	public static final String PENDING = "PENDING";

	public static final String APPROVED = "APPROVED";

	/** Staff hid it with a reason. The stars still stay out of the average. */
	public static final String HIDDEN = "HIDDEN";

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "exam_id")
	private UUID examId;

	@Column(name = "module_id")
	private UUID moduleId;

	@Column(name = "student_id", nullable = false)
	private UUID studentId;

	@Column(nullable = false)
	private short rating;

	@Column(columnDefinition = "TEXT")
	private String comment;

	@Column(nullable = false, length = 16)
	private String status;

	@Column(name = "hide_reason", length = 300)
	private String hideReason;

	@Column(nullable = false)
	private boolean featured;

	@Column(name = "moderated_by")
	private UUID moderatedBy;

	@Column(name = "moderated_at")
	private Instant moderatedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public UUID getId() {
		return id;
	}

	public UUID getExamId() {
		return examId;
	}

	public void setExamId(UUID examId) {
		this.examId = examId;
	}

	public UUID getModuleId() {
		return moduleId;
	}

	public void setModuleId(UUID moduleId) {
		this.moduleId = moduleId;
	}

	public UUID getStudentId() {
		return studentId;
	}

	public void setStudentId(UUID studentId) {
		this.studentId = studentId;
	}

	public short getRating() {
		return rating;
	}

	public void setRating(short rating) {
		this.rating = rating;
	}

	public String getComment() {
		return comment;
	}

	public void setComment(String comment) {
		this.comment = comment;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getHideReason() {
		return hideReason;
	}

	public void setHideReason(String hideReason) {
		this.hideReason = hideReason;
	}

	public boolean isFeatured() {
		return featured;
	}

	public void setFeatured(boolean featured) {
		this.featured = featured;
	}

	public UUID getModeratedBy() {
		return moderatedBy;
	}

	public void setModeratedBy(UUID moderatedBy) {
		this.moderatedBy = moderatedBy;
	}

	public Instant getModeratedAt() {
		return moderatedAt;
	}

	public void setModeratedAt(Instant moderatedAt) {
		this.moderatedAt = moderatedAt;
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

}
