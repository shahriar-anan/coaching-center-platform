package com.coachingcenter.api.offline;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An exam or package an offline admission is given access to (V82). A removed item stays as history. */
@Entity
@Table(name = "offline_admission_items")
public class OfflineAdmissionItem {

	public static final String SCOPE_EXAM = "EXAM";

	public static final String SCOPE_MODULE = "MODULE";

	@Id
	private UUID id;

	@Column(name = "admission_id", nullable = false)
	private UUID admissionId;

	@Column(nullable = false, length = 8)
	private String scope;

	@Column(name = "exam_id")
	private UUID examId;

	@Column(name = "module_id")
	private UUID moduleId;

	@Column(name = "grant_id")
	private UUID grantId;

	@Column(name = "added_by", nullable = false)
	private UUID addedBy;

	@Column(name = "added_at", nullable = false)
	private Instant addedAt;

	@Column(name = "removed_at")
	private Instant removedAt;

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

	public String getScope() {
		return scope;
	}

	public void setScope(String scope) {
		this.scope = scope;
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

	public UUID getGrantId() {
		return grantId;
	}

	public void setGrantId(UUID grantId) {
		this.grantId = grantId;
	}

	public UUID getAddedBy() {
		return addedBy;
	}

	public void setAddedBy(UUID addedBy) {
		this.addedBy = addedBy;
	}

	public Instant getAddedAt() {
		return addedAt;
	}

	public void setAddedAt(Instant addedAt) {
		this.addedAt = addedAt;
	}

	public Instant getRemovedAt() {
		return removedAt;
	}

	public void setRemovedAt(Instant removedAt) {
		this.removedAt = removedAt;
	}

}
