package com.coachingcenter.api.courses;

import java.util.UUID;

import com.coachingcenter.api.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "materials")
public class Material extends BaseEntity {

	@Column(name = "course_id")
	private UUID courseId;

	@Column(name = "book_id")
	private UUID bookId;

	@Column(name = "lecture_node_id")
	private UUID lectureNodeId;

	@Column(name = "section_node_id")
	private UUID sectionNodeId;

	@Column(nullable = false, length = 500)
	private String title;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(nullable = false, length = 16)
	private String kind;

	@Column(nullable = false)
	private int position;

	@Column(name = "stored_file_name", nullable = false, length = 255)
	private String storedFileName;

	@Column(name = "original_file_name", nullable = false, length = 500)
	private String originalFileName;

	@Column(name = "byte_size", nullable = false)
	private long byteSize;

	@Column(name = "content_type", nullable = false, length = 255)
	private String contentType;

	@Column(nullable = false)
	private boolean free;

	public UUID getCourseId() {
		return courseId;
	}

	public void setCourseId(UUID courseId) {
		this.courseId = courseId;
	}

	public UUID getBookId() {
		return bookId;
	}

	public void setBookId(UUID bookId) {
		this.bookId = bookId;
	}

	public UUID getLectureNodeId() {
		return lectureNodeId;
	}

	public void setLectureNodeId(UUID lectureNodeId) {
		this.lectureNodeId = lectureNodeId;
	}

	public UUID getSectionNodeId() {
		return sectionNodeId;
	}

	public void setSectionNodeId(UUID sectionNodeId) {
		this.sectionNodeId = sectionNodeId;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getKind() {
		return kind;
	}

	public void setKind(String kind) {
		this.kind = kind;
	}

	public int getPosition() {
		return position;
	}

	public void setPosition(int position) {
		this.position = position;
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

	public boolean isFree() {
		return free;
	}

	public void setFree(boolean free) {
		this.free = free;
	}

}
