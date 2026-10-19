package com.coachingcenter.api.exams;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "live_exam_roster")
@IdClass(LiveExamRoster.Key.class)
public class LiveExamRoster {

	@Id
	@Column(name = "module_id", nullable = false)
	private UUID moduleId;

	@Id
	@Column(name = "student_id", nullable = false)
	private UUID studentId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

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

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public static class Key implements Serializable {

		private UUID moduleId;

		private UUID studentId;

		public Key() {
		}

		public Key(UUID moduleId, UUID studentId) {
			this.moduleId = moduleId;
			this.studentId = studentId;
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

		@Override
		public boolean equals(Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof Key key)) {
				return false;
			}
			return moduleId.equals(key.moduleId) && studentId.equals(key.studentId);
		}

		@Override
		public int hashCode() {
			return moduleId.hashCode() * 31 + studentId.hashCode();
		}

	}

}
