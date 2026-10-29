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

	public static final String ARCHIVED = "ARCHIVED";

	public static final String BN = "BN";

	public static final String EN = "EN";

	/** Both languages; a null paper language means none was chosen (D5, C2). */
	public static final String BOTH = "BOTH";

	/** An exam with a window: live at {@code liveAt}, open until {@code endsAt}. */
	public static final String MODE_LIVE = "LIVE";

	/** An exam with no window, open from publish. */
	public static final String MODE_PRACTICE = "PRACTICE";

	/** The package the exam was created in. Kept for history only; access and listing read the membership table. */
	@Column(name = "module_id")
	private UUID moduleId;

	@Column(name = "category_id", nullable = false)
	private UUID categoryId;

	@Column(name = "instructor_id")
	private UUID instructorId;

	@Column(nullable = false, length = 16)
	private String mode;

	@Column(name = "ends_at")
	private Instant endsAt;

	@Column(name = "published_at")
	private Instant publishedAt;

	@Column(name = "negative_marks", precision = 5, scale = 2)
	private BigDecimal negativeMarks;

	@Column(name = "answers_changeable", nullable = false)
	private boolean answersChangeable;

	@Column(name = "question_paper_id")
	private UUID questionPaperId;

	@Column(name = "is_free", nullable = false)
	private boolean free;

	@Column(name = "discount_percent")
	private Integer discountPercent;

	@Column(name = "discount_ends_at")
	private Instant discountEndsAt;

	@Column(name = "access_days")
	private Integer accessDays;

	@Column(nullable = false)
	private boolean popular;

	@Column(name = "preview_enabled", nullable = false)
	private boolean previewEnabled;

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

	@Column(name = "paper_language", length = 8)
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

	public UUID getCategoryId() {
		return categoryId;
	}

	public void setCategoryId(UUID categoryId) {
		this.categoryId = categoryId;
	}

	public UUID getInstructorId() {
		return instructorId;
	}

	public void setInstructorId(UUID instructorId) {
		this.instructorId = instructorId;
	}

	public String getMode() {
		return mode;
	}

	public void setMode(String mode) {
		this.mode = mode;
	}

	public Instant getEndsAt() {
		return endsAt;
	}

	public void setEndsAt(Instant endsAt) {
		this.endsAt = endsAt;
	}

	public Instant getPublishedAt() {
		return publishedAt;
	}

	public void setPublishedAt(Instant publishedAt) {
		this.publishedAt = publishedAt;
	}

	public BigDecimal getNegativeMarks() {
		return negativeMarks;
	}

	public void setNegativeMarks(BigDecimal negativeMarks) {
		this.negativeMarks = negativeMarks;
	}

	public boolean isAnswersChangeable() {
		return answersChangeable;
	}

	public void setAnswersChangeable(boolean answersChangeable) {
		this.answersChangeable = answersChangeable;
	}

	public UUID getQuestionPaperId() {
		return questionPaperId;
	}

	public void setQuestionPaperId(UUID questionPaperId) {
		this.questionPaperId = questionPaperId;
	}

	public boolean isFree() {
		return free;
	}

	public void setFree(boolean free) {
		this.free = free;
	}

	public Integer getDiscountPercent() {
		return discountPercent;
	}

	public void setDiscountPercent(Integer discountPercent) {
		this.discountPercent = discountPercent;
	}

	public Instant getDiscountEndsAt() {
		return discountEndsAt;
	}

	public void setDiscountEndsAt(Instant discountEndsAt) {
		this.discountEndsAt = discountEndsAt;
	}

	public Integer getAccessDays() {
		return accessDays;
	}

	public void setAccessDays(Integer accessDays) {
		this.accessDays = accessDays;
	}

	public boolean isPopular() {
		return popular;
	}

	public void setPopular(boolean popular) {
		this.popular = popular;
	}

	public boolean isPreviewEnabled() {
		return previewEnabled;
	}

	public void setPreviewEnabled(boolean previewEnabled) {
		this.previewEnabled = previewEnabled;
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

	/** The exam's own cover image (V53, E1). Null when none. Saved at once, not staged, as the package cover is. */
	@Column(name = "cover_stored_name", length = 80)
	private String coverStoredName;

	@Column(name = "cover_content_type", length = 40)
	private String coverContentType;

	@Column(name = "cover_byte_size")
	private Long coverByteSize;

	@Column(name = "cover_updated_at")
	private Instant coverUpdatedAt;

	public String getCoverStoredName() {
		return coverStoredName;
	}

	public void setCoverStoredName(String coverStoredName) {
		this.coverStoredName = coverStoredName;
	}

	public String getCoverContentType() {
		return coverContentType;
	}

	public void setCoverContentType(String coverContentType) {
		this.coverContentType = coverContentType;
	}

	public Long getCoverByteSize() {
		return coverByteSize;
	}

	public void setCoverByteSize(Long coverByteSize) {
		this.coverByteSize = coverByteSize;
	}

	public Instant getCoverUpdatedAt() {
		return coverUpdatedAt;
	}

	public void setCoverUpdatedAt(Instant coverUpdatedAt) {
		this.coverUpdatedAt = coverUpdatedAt;
	}

}
