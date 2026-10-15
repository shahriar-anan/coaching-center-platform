package com.coachingcenter.api.courses;

import java.util.UUID;

import com.coachingcenter.api.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "content_nodes")
public class ContentNode extends BaseEntity {

	@Column(name = "course_id", nullable = false)
	private UUID courseId;

	@Column(name = "parent_id")
	private UUID parentId;

	@Column(name = "node_type", nullable = false, length = 16)
	private String nodeType;

	@Column(nullable = false, length = 500)
	private String title;

	@Column(nullable = false)
	private int position;

	@Column(nullable = false)
	private boolean free;

	@Column(name = "video_provider", length = 32)
	private String videoProvider;

	@Column(name = "video_ref", length = 64)
	private String videoRef;

	@Column(columnDefinition = "TEXT")
	private String notes;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(name = "duration_minutes")
	private Integer durationMinutes;

	public UUID getCourseId() {
		return courseId;
	}

	public void setCourseId(UUID courseId) {
		this.courseId = courseId;
	}

	public UUID getParentId() {
		return parentId;
	}

	public void setParentId(UUID parentId) {
		this.parentId = parentId;
	}

	public String getNodeType() {
		return nodeType;
	}

	public void setNodeType(String nodeType) {
		this.nodeType = nodeType;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public int getPosition() {
		return position;
	}

	public void setPosition(int position) {
		this.position = position;
	}

	public boolean isFree() {
		return free;
	}

	public void setFree(boolean free) {
		this.free = free;
	}

	public String getVideoProvider() {
		return videoProvider;
	}

	public void setVideoProvider(String videoProvider) {
		this.videoProvider = videoProvider;
	}

	public String getVideoRef() {
		return videoRef;
	}

	public void setVideoRef(String videoRef) {
		this.videoRef = videoRef;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Integer getDurationMinutes() {
		return durationMinutes;
	}

	public void setDurationMinutes(Integer durationMinutes) {
		this.durationMinutes = durationMinutes;
	}

}
