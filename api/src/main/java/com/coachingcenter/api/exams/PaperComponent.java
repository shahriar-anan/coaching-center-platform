package com.coachingcenter.api.exams;

import java.time.Instant;
import java.util.UUID;

import com.coachingcenter.api.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "paper_components")
public class PaperComponent extends BaseEntity {

	@Column(name = "paper_id", nullable = false)
	private UUID paperId;

	@Column(name = "component_type", nullable = false, length = 16)
	private String componentType;

	@Column(name = "description_en")
	private String descriptionEn;

	@Column(name = "description_bn")
	private String descriptionBn;

	@Column(name = "starts_at")
	private Instant startsAt;

	@Column(name = "ends_at")
	private Instant endsAt;

	@Column(name = "duration_seconds")
	private Integer durationSeconds;

	@Column(name = "immediate_results", nullable = false)
	private boolean immediateResults;

	@Column(name = "scheduled", nullable = false)
	private boolean scheduled;

	@Column(name = "deadline_at")
	private Instant deadlineAt;

	@Column(name = "results_published", nullable = false)
	private boolean resultsPublished;

	public UUID getPaperId() {
		return paperId;
	}

	public void setPaperId(UUID paperId) {
		this.paperId = paperId;
	}

	public String getComponentType() {
		return componentType;
	}

	public void setComponentType(String componentType) {
		this.componentType = componentType;
	}

	public String getDescriptionEn() {
		return descriptionEn;
	}

	public void setDescriptionEn(String descriptionEn) {
		this.descriptionEn = descriptionEn;
	}

	public String getDescriptionBn() {
		return descriptionBn;
	}

	public void setDescriptionBn(String descriptionBn) {
		this.descriptionBn = descriptionBn;
	}

	public Instant getStartsAt() {
		return startsAt;
	}

	public void setStartsAt(Instant startsAt) {
		this.startsAt = startsAt;
	}

	public Instant getEndsAt() {
		return endsAt;
	}

	public void setEndsAt(Instant endsAt) {
		this.endsAt = endsAt;
	}

	public Integer getDurationSeconds() {
		return durationSeconds;
	}

	public void setDurationSeconds(Integer durationSeconds) {
		this.durationSeconds = durationSeconds;
	}

	public boolean isImmediateResults() {
		return immediateResults;
	}

	public void setImmediateResults(boolean immediateResults) {
		this.immediateResults = immediateResults;
	}

	public boolean isScheduled() {
		return scheduled;
	}

	public void setScheduled(boolean scheduled) {
		this.scheduled = scheduled;
	}

	public Instant getDeadlineAt() {
		return deadlineAt;
	}

	public void setDeadlineAt(Instant deadlineAt) {
		this.deadlineAt = deadlineAt;
	}

	public boolean isResultsPublished() {
		return resultsPublished;
	}

	public void setResultsPublished(boolean resultsPublished) {
		this.resultsPublished = resultsPublished;
	}

}
