package com.coachingcenter.api.exams;

import java.time.Instant;
import java.util.UUID;

import com.coachingcenter.api.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "live_exam_participants")
public class LiveExamParticipant extends BaseEntity {

	public static final String LIVE = "LIVE";

	public static final String RETAKE = "RETAKE";

	public static final String IN_PROGRESS = "IN_PROGRESS";

	public static final String SUBMITTED = "SUBMITTED";

	public static final String FAILED_TO_SUBMIT = "FAILED_TO_SUBMIT";

	@Column(name = "exam_id", nullable = false)
	private UUID examId;

	@Column(name = "student_id", nullable = false)
	private UUID studentId;

	@Column(nullable = false, length = 16)
	private String kind;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt;

	@Column(name = "deadline_at", nullable = false)
	private Instant deadlineAt;

	@Column(name = "submitted_at")
	private Instant submittedAt;

	@Column
	private Integer score;

	@Column
	private Boolean passed;

	@Column(nullable = false, length = 32)
	private String status;

	@Column(name = "answer_stored_file_name", length = 255)
	private String answerStoredFileName;

	@Column(name = "answer_original_file_name", length = 500)
	private String answerOriginalFileName;

	@Column(name = "answer_byte_size")
	private Long answerByteSize;

	@Column(name = "answer_content_type", length = 255)
	private String answerContentType;

	@Column(columnDefinition = "TEXT")
	private String comment;

	@Column(name = "evaluated_by")
	private UUID evaluatedBy;

	@Column(name = "evaluated_at")
	private Instant evaluatedAt;

	@Column(name = "published_at")
	private Instant publishedAt;

	@Column(name = "evaluated_stored_file_name", length = 255)
	private String evaluatedStoredFileName;

	@Column(name = "evaluated_original_file_name", length = 500)
	private String evaluatedOriginalFileName;

	@Column(name = "evaluated_byte_size")
	private Long evaluatedByteSize;

	@Column(name = "evaluated_content_type", length = 255)
	private String evaluatedContentType;

	public UUID getExamId() {
		return examId;
	}

	public void setExamId(UUID examId) {
		this.examId = examId;
	}

	public UUID getStudentId() {
		return studentId;
	}

	public void setStudentId(UUID studentId) {
		this.studentId = studentId;
	}

	public String getKind() {
		return kind;
	}

	public void setKind(String kind) {
		this.kind = kind;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public void setStartedAt(Instant startedAt) {
		this.startedAt = startedAt;
	}

	public Instant getDeadlineAt() {
		return deadlineAt;
	}

	public void setDeadlineAt(Instant deadlineAt) {
		this.deadlineAt = deadlineAt;
	}

	public Instant getSubmittedAt() {
		return submittedAt;
	}

	public void setSubmittedAt(Instant submittedAt) {
		this.submittedAt = submittedAt;
	}

	public Integer getScore() {
		return score;
	}

	public void setScore(Integer score) {
		this.score = score;
	}

	public Boolean getPassed() {
		return passed;
	}

	public void setPassed(Boolean passed) {
		this.passed = passed;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getAnswerStoredFileName() {
		return answerStoredFileName;
	}

	public void setAnswerStoredFileName(String answerStoredFileName) {
		this.answerStoredFileName = answerStoredFileName;
	}

	public String getAnswerOriginalFileName() {
		return answerOriginalFileName;
	}

	public void setAnswerOriginalFileName(String answerOriginalFileName) {
		this.answerOriginalFileName = answerOriginalFileName;
	}

	public Long getAnswerByteSize() {
		return answerByteSize;
	}

	public void setAnswerByteSize(Long answerByteSize) {
		this.answerByteSize = answerByteSize;
	}

	public String getAnswerContentType() {
		return answerContentType;
	}

	public void setAnswerContentType(String answerContentType) {
		this.answerContentType = answerContentType;
	}

	public String getComment() {
		return comment;
	}

	public void setComment(String comment) {
		this.comment = comment;
	}

	public UUID getEvaluatedBy() {
		return evaluatedBy;
	}

	public void setEvaluatedBy(UUID evaluatedBy) {
		this.evaluatedBy = evaluatedBy;
	}

	public Instant getEvaluatedAt() {
		return evaluatedAt;
	}

	public void setEvaluatedAt(Instant evaluatedAt) {
		this.evaluatedAt = evaluatedAt;
	}

	public Instant getPublishedAt() {
		return publishedAt;
	}

	public void setPublishedAt(Instant publishedAt) {
		this.publishedAt = publishedAt;
	}

	public String getEvaluatedStoredFileName() {
		return evaluatedStoredFileName;
	}

	public void setEvaluatedStoredFileName(String evaluatedStoredFileName) {
		this.evaluatedStoredFileName = evaluatedStoredFileName;
	}

	public String getEvaluatedOriginalFileName() {
		return evaluatedOriginalFileName;
	}

	public void setEvaluatedOriginalFileName(String evaluatedOriginalFileName) {
		this.evaluatedOriginalFileName = evaluatedOriginalFileName;
	}

	public Long getEvaluatedByteSize() {
		return evaluatedByteSize;
	}

	public void setEvaluatedByteSize(Long evaluatedByteSize) {
		this.evaluatedByteSize = evaluatedByteSize;
	}

	public String getEvaluatedContentType() {
		return evaluatedContentType;
	}

	public void setEvaluatedContentType(String evaluatedContentType) {
		this.evaluatedContentType = evaluatedContentType;
	}

}
