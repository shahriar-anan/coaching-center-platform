package com.coachingcenter.api.exams;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "live_exam_instructors")
public class LiveExamInstructor {

	@Id
	@Column(name = "module_id", nullable = false)
	private UUID moduleId;

	@Column(name = "instructor_id", nullable = false)
	private UUID instructorId;

	@Column(name = "assigned_at", nullable = false)
	private Instant assignedAt;

	public UUID getModuleId() {
		return moduleId;
	}

	public void setModuleId(UUID moduleId) {
		this.moduleId = moduleId;
	}

	public UUID getInstructorId() {
		return instructorId;
	}

	public void setInstructorId(UUID instructorId) {
		this.instructorId = instructorId;
	}

	public Instant getAssignedAt() {
		return assignedAt;
	}

	public void setAssignedAt(Instant assignedAt) {
		this.assignedAt = assignedAt;
	}

}
