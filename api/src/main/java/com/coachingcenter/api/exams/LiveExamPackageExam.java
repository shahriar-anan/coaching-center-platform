package com.coachingcenter.api.exams;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An exam's place in a package. A removed row stays as history (NR-01, R4). */
@Entity
@Table(name = "live_exam_package_exams")
public class LiveExamPackageExam {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "module_id", nullable = false)
	private UUID moduleId;

	@Column(name = "exam_id", nullable = false)
	private UUID examId;

	@Column(nullable = false)
	private int position;

	@Column(name = "added_at", nullable = false)
	private Instant addedAt;

	@Column(name = "removed_at")
	private Instant removedAt;

	public UUID getId() {
		return id;
	}

	public UUID getModuleId() {
		return moduleId;
	}

	public void setModuleId(UUID moduleId) {
		this.moduleId = moduleId;
	}

	public UUID getExamId() {
		return examId;
	}

	public void setExamId(UUID examId) {
		this.examId = examId;
	}

	public int getPosition() {
		return position;
	}

	public void setPosition(int position) {
		this.position = position;
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
