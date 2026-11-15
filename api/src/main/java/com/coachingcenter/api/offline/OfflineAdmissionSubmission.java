package com.coachingcenter.api.offline;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One form the student sent in for an admission, kept as the JSON text they sent (V82). */
@Entity
@Table(name = "offline_admission_submissions")
public class OfflineAdmissionSubmission {

	@Id
	private UUID id;

	@Column(name = "admission_id", nullable = false)
	private UUID admissionId;

	@Column(name = "submitted_at", nullable = false)
	private Instant submittedAt;

	@Column(name = "submitted_by")
	private UUID submittedBy;

	@Column(name = "form_json", nullable = false)
	private String formJson;

	@Column(name = "returned_reason", length = 500)
	private String returnedReason;

	@Column(name = "returned_by")
	private UUID returnedBy;

	@Column(name = "returned_at")
	private Instant returnedAt;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public UUID getAdmissionId() {
		return admissionId;
	}

	public void setAdmissionId(UUID admissionId) {
		this.admissionId = admissionId;
	}

	public Instant getSubmittedAt() {
		return submittedAt;
	}

	public void setSubmittedAt(Instant submittedAt) {
		this.submittedAt = submittedAt;
	}

	public UUID getSubmittedBy() {
		return submittedBy;
	}

	public void setSubmittedBy(UUID submittedBy) {
		this.submittedBy = submittedBy;
	}

	public String getFormJson() {
		return formJson;
	}

	public void setFormJson(String formJson) {
		this.formJson = formJson;
	}

	public String getReturnedReason() {
		return returnedReason;
	}

	public void setReturnedReason(String returnedReason) {
		this.returnedReason = returnedReason;
	}

	public UUID getReturnedBy() {
		return returnedBy;
	}

	public void setReturnedBy(UUID returnedBy) {
		this.returnedBy = returnedBy;
	}

	public Instant getReturnedAt() {
		return returnedAt;
	}

	public void setReturnedAt(Instant returnedAt) {
		this.returnedAt = returnedAt;
	}

}
