package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.coachingcenter.api.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "live_exam_participants")
public class LiveExamParticipant extends BaseEntity {

	/** The first attempt, started inside the window (or the first attempt of a practice exam, see PRACTICE). */
	public static final String LIVE = "LIVE";

	/** The first attempt, started after the window ended. Stored as RETAKE before V46. */
	public static final String ARCHIVED = "ARCHIVED";

	/** Any later attempt. */
	public static final String RETAKE = "RETAKE";

	/** The first attempt of a practice exam. */
	public static final String PRACTICE = "PRACTICE";

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

	@Column(precision = 8, scale = 2)
	private BigDecimal score;

	@Column
	private Boolean passed;

	@Column(name = "attempt_no", nullable = false)
	private int attemptNo;

	@Column(name = "question_paper_id")
	private UUID questionPaperId;

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

	/** A correction to a released result, kept until Master Admin releases it. Null when none is waiting. */
	@Column(name = "pending_score", precision = 8, scale = 2)
	private BigDecimal pendingScore;

	@Column(name = "pending_passed")
	private Boolean pendingPassed;

	@Column(name = "pending_comment", columnDefinition = "TEXT")
	private String pendingComment;

	@Column(name = "pending_evaluated_by")
	private UUID pendingEvaluatedBy;

	@Column(name = "pending_evaluated_at")
	private Instant pendingEvaluatedAt;

	@Column(name = "pending_stored_file_name", length = 255)
	private String pendingStoredFileName;

	@Column(name = "pending_original_file_name", length = 500)
	private String pendingOriginalFileName;

	@Column(name = "pending_byte_size")
	private Long pendingByteSize;

	@Column(name = "pending_content_type", length = 255)
	private String pendingContentType;

	/** Set by the marker when the paper is finished; cleared by any save and by a release. */
	@Column(name = "ready_for_release_at")
	private Instant readyForReleaseAt;

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

	public BigDecimal getScore() {
		return score;
	}

	public void setScore(BigDecimal score) {
		this.score = score;
	}

	public int getAttemptNo() {
		return attemptNo;
	}

	public void setAttemptNo(int attemptNo) {
		this.attemptNo = attemptNo;
	}

	public UUID getQuestionPaperId() {
		return questionPaperId;
	}

	public void setQuestionPaperId(UUID questionPaperId) {
		this.questionPaperId = questionPaperId;
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

	public Instant getReadyForReleaseAt() {
		return readyForReleaseAt;
	}

	public void setReadyForReleaseAt(Instant readyForReleaseAt) {
		this.readyForReleaseAt = readyForReleaseAt;
	}

	/** True when a correction to the released result waits for release. */
	public boolean hasPendingRevision() {
		return pendingEvaluatedAt != null;
	}

	public BigDecimal getPendingScore() {
		return pendingScore;
	}

	public void setPendingScore(BigDecimal pendingScore) {
		this.pendingScore = pendingScore;
	}

	public Boolean getPendingPassed() {
		return pendingPassed;
	}

	public void setPendingPassed(Boolean pendingPassed) {
		this.pendingPassed = pendingPassed;
	}

	public String getPendingComment() {
		return pendingComment;
	}

	public void setPendingComment(String pendingComment) {
		this.pendingComment = pendingComment;
	}

	public UUID getPendingEvaluatedBy() {
		return pendingEvaluatedBy;
	}

	public void setPendingEvaluatedBy(UUID pendingEvaluatedBy) {
		this.pendingEvaluatedBy = pendingEvaluatedBy;
	}

	public Instant getPendingEvaluatedAt() {
		return pendingEvaluatedAt;
	}

	public void setPendingEvaluatedAt(Instant pendingEvaluatedAt) {
		this.pendingEvaluatedAt = pendingEvaluatedAt;
	}

	public String getPendingStoredFileName() {
		return pendingStoredFileName;
	}

	public void setPendingStoredFileName(String pendingStoredFileName) {
		this.pendingStoredFileName = pendingStoredFileName;
	}

	public String getPendingOriginalFileName() {
		return pendingOriginalFileName;
	}

	public void setPendingOriginalFileName(String pendingOriginalFileName) {
		this.pendingOriginalFileName = pendingOriginalFileName;
	}

	public Long getPendingByteSize() {
		return pendingByteSize;
	}

	public void setPendingByteSize(Long pendingByteSize) {
		this.pendingByteSize = pendingByteSize;
	}

	public String getPendingContentType() {
		return pendingContentType;
	}

	public void setPendingContentType(String pendingContentType) {
		this.pendingContentType = pendingContentType;
	}

	/** Clears every pending field after a release has copied them into the released columns. */
	public void clearPendingRevision() {
		pendingScore = null;
		pendingPassed = null;
		pendingComment = null;
		pendingEvaluatedBy = null;
		pendingEvaluatedAt = null;
		pendingStoredFileName = null;
		pendingOriginalFileName = null;
		pendingByteSize = null;
		pendingContentType = null;
	}

}
