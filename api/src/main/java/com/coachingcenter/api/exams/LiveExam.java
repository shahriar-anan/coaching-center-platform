package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.coachingcenter.api.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "live_exams")
public class LiveExam extends BaseEntity {

	public static final String MCQ = "MCQ";

	public static final String WRITTEN = "WRITTEN";

	public static final String DRAFT = "DRAFT";

	public static final String PUBLISHED = "PUBLISHED";

	public static final String BN = "BN";

	public static final String EN = "EN";

	@Column(name = "module_id", nullable = false)
	private UUID moduleId;

	@Column(name = "exam_type", nullable = false, length = 16)
	private String examType;

	@Column(name = "title_en", nullable = false, length = 500)
	private String titleEn;

	@Column(name = "title_bn", nullable = false, length = 500)
	private String titleBn;

	@Column(name = "description_en", nullable = false, columnDefinition = "TEXT")
	private String descriptionEn;

	@Column(name = "description_bn", nullable = false, columnDefinition = "TEXT")
	private String descriptionBn;

	@Column(name = "rules_en", nullable = false, columnDefinition = "TEXT")
	private String rulesEn;

	@Column(name = "rules_bn", nullable = false, columnDefinition = "TEXT")
	private String rulesBn;

	@Column(name = "paper_language", nullable = false, length = 2)
	private String paperLanguage;

	@Column(name = "live_at")
	private Instant liveAt;

	@Column(name = "duration_minutes", nullable = false)
	private int durationMinutes;

	@Column(name = "pass_percent")
	private Integer passPercent;

	@Column(name = "written_maximum")
	private Integer writtenMaximum;

	@Column(name = "question_stored_file_name", length = 255)
	private String questionStoredFileName;

	@Column(name = "question_original_file_name", length = 500)
	private String questionOriginalFileName;

	@Column(name = "question_byte_size")
	private Long questionByteSize;

	@Column(name = "question_content_type", length = 255)
	private String questionContentType;

	@Column(nullable = false, length = 16)
	private String status;

	@Column(name = "finally_saved", nullable = false)
	private boolean finallySaved;

	@Column(precision = 12, scale = 2)
	private BigDecimal price;

	public UUID getModuleId() {
		return moduleId;
	}

	public void setModuleId(UUID moduleId) {
		this.moduleId = moduleId;
	}

	public String getExamType() {
		return examType;
	}

	public void setExamType(String examType) {
		this.examType = examType;
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

	public String getRulesEn() {
		return rulesEn;
	}

	public void setRulesEn(String rulesEn) {
		this.rulesEn = rulesEn;
	}

	public String getRulesBn() {
		return rulesBn;
	}

	public void setRulesBn(String rulesBn) {
		this.rulesBn = rulesBn;
	}

	public String getPaperLanguage() {
		return paperLanguage;
	}

	public void setPaperLanguage(String paperLanguage) {
		this.paperLanguage = paperLanguage;
	}

	public Instant getLiveAt() {
		return liveAt;
	}

	public void setLiveAt(Instant liveAt) {
		this.liveAt = liveAt;
	}

	public int getDurationMinutes() {
		return durationMinutes;
	}

	public void setDurationMinutes(int durationMinutes) {
		this.durationMinutes = durationMinutes;
	}

	public Integer getPassPercent() {
		return passPercent;
	}

	public void setPassPercent(Integer passPercent) {
		this.passPercent = passPercent;
	}

	public Integer getWrittenMaximum() {
		return writtenMaximum;
	}

	public void setWrittenMaximum(Integer writtenMaximum) {
		this.writtenMaximum = writtenMaximum;
	}

	public String getQuestionStoredFileName() {
		return questionStoredFileName;
	}

	public void setQuestionStoredFileName(String questionStoredFileName) {
		this.questionStoredFileName = questionStoredFileName;
	}

	public String getQuestionOriginalFileName() {
		return questionOriginalFileName;
	}

	public void setQuestionOriginalFileName(String questionOriginalFileName) {
		this.questionOriginalFileName = questionOriginalFileName;
	}

	public Long getQuestionByteSize() {
		return questionByteSize;
	}

	public void setQuestionByteSize(Long questionByteSize) {
		this.questionByteSize = questionByteSize;
	}

	public String getQuestionContentType() {
		return questionContentType;
	}

	public void setQuestionContentType(String questionContentType) {
		this.questionContentType = questionContentType;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public boolean isFinallySaved() {
		return finallySaved;
	}

	public void setFinallySaved(boolean finallySaved) {
		this.finallySaved = finallySaved;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public void setPrice(BigDecimal price) {
		this.price = price;
	}


	/** When Master Admin or System Admin released the MCQ correct answers for this exam (V39, R-4). Null means withheld. */
	@Column(name = "answers_released_at")
	private Instant answersReleasedAt;

	@Column(name = "answers_released_by")
	private UUID answersReleasedBy;

	public Instant getAnswersReleasedAt() {
		return answersReleasedAt;
	}

	public void setAnswersReleasedAt(Instant answersReleasedAt) {
		this.answersReleasedAt = answersReleasedAt;
	}

	public UUID getAnswersReleasedBy() {
		return answersReleasedBy;
	}

	public void setAnswersReleasedBy(UUID answersReleasedBy) {
		this.answersReleasedBy = answersReleasedBy;
	}

}
