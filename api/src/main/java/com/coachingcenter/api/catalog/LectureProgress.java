package com.coachingcenter.api.catalog;

import java.time.Instant;
import java.util.UUID;

import com.coachingcenter.api.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "lecture_progress")
public class LectureProgress extends BaseEntity {

	@Column(name = "student_id", nullable = false)
	private UUID studentId;

	@Column(name = "course_id", nullable = false)
	private UUID courseId;

	@Column(name = "lecture_node_id", nullable = false)
	private UUID lectureNodeId;

	@Column(nullable = false)
	private boolean watched;

	@Column(name = "last_opened_at", nullable = false)
	private Instant lastOpenedAt;

	public UUID getStudentId() {
		return studentId;
	}

	public void setStudentId(UUID studentId) {
		this.studentId = studentId;
	}

	public UUID getCourseId() {
		return courseId;
	}

	public void setCourseId(UUID courseId) {
		this.courseId = courseId;
	}

	public UUID getLectureNodeId() {
		return lectureNodeId;
	}

	public void setLectureNodeId(UUID lectureNodeId) {
		this.lectureNodeId = lectureNodeId;
	}

	public boolean isWatched() {
		return watched;
	}

	public void setWatched(boolean watched) {
		this.watched = watched;
	}

	public Instant getLastOpenedAt() {
		return lastOpenedAt;
	}

	public void setLastOpenedAt(Instant lastOpenedAt) {
		this.lastOpenedAt = lastOpenedAt;
	}

}
