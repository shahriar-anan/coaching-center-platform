package com.coachingcenter.api.exams;

import java.util.UUID;

import com.coachingcenter.api.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "question_bank_items")
public class QuestionBankItem extends BaseEntity {

	@Column(name = "bank_id", nullable = false)
	private UUID bankId;

	@Column(name = "question_id", nullable = false)
	private UUID questionId;

	@Column(nullable = false)
	private int position;

	public UUID getBankId() {
		return bankId;
	}

	public void setBankId(UUID bankId) {
		this.bankId = bankId;
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
