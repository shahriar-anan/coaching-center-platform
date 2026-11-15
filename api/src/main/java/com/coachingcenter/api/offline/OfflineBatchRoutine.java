package com.coachingcenter.api.offline;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One monthly routine of a batch (V89). {@code revision} counts the uploads of the same month (1, 2, ...), so it is the column named
 * {@code version} in the plan. It is not an optimistic lock: the lock is not needed for a routine that is only ever replaced.
 * {@code legacyOriginal} marks the routine copied from the old single routine: staff only, no student copy.
 */
@Entity
@Table(name = "offline_batch_routines")
public class OfflineBatchRoutine {

	public static final String STATUS_PREVIEW = "PREVIEW";

	public static final String STATUS_CONFIRMED = "CONFIRMED";

	public static final String STATUS_SUPERSEDED = "SUPERSEDED";

	@Id
	private UUID id;

	@Column(name = "batch_id", nullable = false)
	private UUID batchId;

	/** The first day of the month of {@link #effectiveFrom}, kept for the older readers of this table. */
	@Column(nullable = false)
	private LocalDate month;

	/**
	 * The day the routine counts from: the Dhaka date it was uploaded. Its earlier dates still become classes, but a confirm only
	 * replaces the classes from this day onward.
	 */
	@Column(name = "effective_from", nullable = false)
	private LocalDate effectiveFrom;

	@Column(name = "original_file", length = 255)
	private String originalFile;

	@Column(name = "original_name", length = 500)
	private String originalName;

	@Column(name = "original_byte_size")
	private Long originalByteSize;

	@Column(name = "student_file", length = 255)
	private String studentFile;

	@Column(name = "student_byte_size")
	private Long studentByteSize;

	@Column(name = "version", nullable = false)
	private Integer revision;

	@Column(nullable = false, length = 16)
	private String status;

	@Column(name = "legacy_original", nullable = false)
	private boolean legacyOriginal;

	@Column(name = "uploaded_by")
	private UUID uploadedBy;

	@Column(name = "uploaded_at", nullable = false)
	private Instant uploadedAt;

	@Column(name = "confirmed_by")
	private UUID confirmedBy;

	@Column(name = "confirmed_at")
	private Instant confirmedAt;

	/** The rows the import flagged and why (Stage 2). Null until a routine is parsed. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "parse_report", columnDefinition = "jsonb")
	private String parseReport;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public UUID getBatchId() {
		return batchId;
	}

	public void setBatchId(UUID batchId) {
		this.batchId = batchId;
	}

	public LocalDate getMonth() {
		return month;
	}

	public void setMonth(LocalDate month) {
		this.month = month;
	}

	public LocalDate getEffectiveFrom() {
		return effectiveFrom;
	}

	public void setEffectiveFrom(LocalDate effectiveFrom) {
		this.effectiveFrom = effectiveFrom;
	}

	public String getOriginalFile() {
		return originalFile;
	}

	public void setOriginalFile(String originalFile) {
		this.originalFile = originalFile;
	}

	public String getOriginalName() {
		return originalName;
	}

	public void setOriginalName(String originalName) {
		this.originalName = originalName;
	}

	public Long getOriginalByteSize() {
		return originalByteSize;
	}

	public void setOriginalByteSize(Long originalByteSize) {
		this.originalByteSize = originalByteSize;
	}

	public String getStudentFile() {
		return studentFile;
	}

	public void setStudentFile(String studentFile) {
		this.studentFile = studentFile;
	}

	public Long getStudentByteSize() {
		return studentByteSize;
	}

	public void setStudentByteSize(Long studentByteSize) {
		this.studentByteSize = studentByteSize;
	}

	public Integer getRevision() {
		return revision;
	}

	public void setRevision(Integer revision) {
		this.revision = revision;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public boolean isLegacyOriginal() {
		return legacyOriginal;
	}

	public void setLegacyOriginal(boolean legacyOriginal) {
		this.legacyOriginal = legacyOriginal;
	}

	public UUID getUploadedBy() {
		return uploadedBy;
	}

	public void setUploadedBy(UUID uploadedBy) {
		this.uploadedBy = uploadedBy;
	}

	public Instant getUploadedAt() {
		return uploadedAt;
	}

	public void setUploadedAt(Instant uploadedAt) {
		this.uploadedAt = uploadedAt;
	}

	public UUID getConfirmedBy() {
		return confirmedBy;
	}

	public void setConfirmedBy(UUID confirmedBy) {
		this.confirmedBy = confirmedBy;
	}

	public Instant getConfirmedAt() {
		return confirmedAt;
	}

	public void setConfirmedAt(Instant confirmedAt) {
		this.confirmedAt = confirmedAt;
	}

	public String getParseReport() {
		return parseReport;
	}

	public void setParseReport(String parseReport) {
		this.parseReport = parseReport;
	}

}
