package com.coachingcenter.api.courses;

import java.util.UUID;

import com.coachingcenter.api.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "course_instructors")
public class CourseInstructor extends BaseEntity {

	@Column(name = "course_id", nullable = false)
	private UUID courseId;

	@Column(nullable = false, length = 500)
	private String name;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(name = "photo_file_id")
	private UUID photoFileId;

	@Column(nullable = false)
	private int position;

	public UUID getCourseId() {
		return courseId;
	}

	public void setCourseId(UUID courseId) {
		this.courseId = courseId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public UUID getPhotoFileId() {
		return photoFileId;
	}

	public void setPhotoFileId(UUID photoFileId) {
		this.photoFileId = photoFileId;
	}

	public int getPosition() {
		return position;
	}

	public void setPosition(int position) {
		this.position = position;
	}

}
