package com.coachingcenter.api.exams;

import java.util.UUID;

import com.coachingcenter.api.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "exam_nodes")
public class ExamNode extends BaseEntity {

	@Column(name = "module_id", nullable = false)
	private UUID moduleId;

	@Column(name = "parent_id")
	private UUID parentId;

	@Column(name = "node_type", nullable = false, length = 16)
	private String nodeType;

	@Column(name = "title_en", nullable = false, length = 500)
	private String titleEn;

	@Column(name = "title_bn", nullable = false, length = 500)
	private String titleBn;

	@Column(nullable = false)
	private int position;

	@Column(name = "pass_percent")
	private Integer passPercent;

	public UUID getModuleId() {
		return moduleId;
	}

	public void setModuleId(UUID moduleId) {
		this.moduleId = moduleId;
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

	public String getTitleEn() {
		return titleEn;
	}

	public void setTitleEn(String titleEn) {
		this.titleEn = titleEn;
	}

	public String getTitleBn() {
		return titleBn;
	}

	public void setTitleBn(String titleBn) {
		this.titleBn = titleBn;
	}

	public int getPosition() {
		return position;
	}

	public void setPosition(int position) {
		this.position = position;
	}

	public Integer getPassPercent() {
		return passPercent;
	}

	public void setPassPercent(Integer passPercent) {
		this.passPercent = passPercent;
	}

}
