package com.coachingcenter.api.exams;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The change list that a publish applied, kept as the record of what changed and when (NR-01, E31). */
@Entity
@Table(name = "live_exam_change_log")
public class LiveExamChangeLog {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "exam_id")
	private UUID examId;

	@Column(name = "module_id")
	private UUID moduleId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private String changes;

	@Column(nullable = false)
	private int rescored;

	@Column(name = "published_by", nullable = false)
	private UUID publishedBy;

	@Column(name = "published_at", nullable = false)
	private Instant publishedAt;

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

	public String getChanges() {
		return changes;
	}

	public void setChanges(String changes) {
		this.changes = changes;
	}

	public int getRescored() {
		return rescored;
	}

	public void setRescored(int rescored) {
		this.rescored = rescored;
	}

	public UUID getPublishedBy() {
		return publishedBy;
	}

	public void setPublishedBy(UUID publishedBy) {
		this.publishedBy = publishedBy;
	}

	public Instant getPublishedAt() {
		return publishedAt;
	}

	public void setPublishedAt(Instant publishedAt) {
		this.publishedAt = publishedAt;
	}

}
