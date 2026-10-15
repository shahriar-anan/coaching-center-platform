package com.coachingcenter.api.exams;

import java.util.UUID;

import com.coachingcenter.api.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "component_questions")
public class ComponentQuestion extends BaseEntity {

	@Column(name = "component_id", nullable = false)
	private UUID componentId;

	@Column(name = "question_id", nullable = false)
	private UUID questionId;

	@Column(nullable = false)
	private int position;

	public UUID getComponentId() {
		return componentId;
	}

	public void setComponentId(UUID componentId) {
		this.componentId = componentId;
	}

	public UUID getQuestionId() {
		return questionId;
	}

	public void setQuestionId(UUID questionId) {
		this.questionId = questionId;
	}

	public int getPosition() {
		return position;
	}

	public void setPosition(int position) {
		this.position = position;
	}

}
