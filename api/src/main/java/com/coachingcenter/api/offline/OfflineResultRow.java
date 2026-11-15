package com.coachingcenter.api.offline;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One student of a result sheet (V97, plan section 3): a row of the file that matched a student, or an expected student with no row, who is
 * absent (no marks, no rank). {@code rowNumber} is the row in the file (null for an absent student). The file's total and position are kept
 * for the mismatch check; the calculated marks are the ones students see.
 */
@Entity
@Table(name = "offline_result_rows")
public class OfflineResultRow {

	@Id
	private UUID id;

	@Column(name = "sheet_id", nullable = false)
	private UUID sheetId;

	@Column(name = "admission_id")
	private UUID admissionId;

	@Column(name = "roll_no", length = 12)
	private String rollNo;

	@Column(nullable = false)
	private boolean absent;

	@Column(name = "right_count")
	private Integer rightCount;

	@Column(name = "wrong_count")
	private Integer wrongCount;

	private BigDecimal mcq;

	private BigDecimal written;

	private BigDecimal total;

	@Column(name = "rank")
	private Integer rank;

	private Boolean passed;

	@Column(name = "file_total")
	private BigDecimal fileTotal;

	@Column(name = "file_rank")
	private Integer fileRank;

	@Column(name = "row_number")
	private Integer rowNumber;

	protected OfflineResultRow() {
	}

	OfflineResultRow(UUID id, UUID sheetId, UUID admissionId, String rollNo, boolean absent, Integer rightCount, Integer wrongCount,
			BigDecimal mcq, BigDecimal written, BigDecimal total, Integer rank, Boolean passed, BigDecimal fileTotal, Integer fileRank,
			Integer rowNumber) {
		this.id = id;
		this.sheetId = sheetId;
		this.admissionId = admissionId;
		this.rollNo = rollNo;
		this.absent = absent;
		this.rightCount = rightCount;
		this.wrongCount = wrongCount;
		this.mcq = mcq;
		this.written = written;
		this.total = total;
		this.rank = rank;
		this.passed = passed;
		this.fileTotal = fileTotal;
		this.fileRank = fileRank;
		this.rowNumber = rowNumber;
	}

	public UUID getId() {
		return id;
	}

	public UUID getSheetId() {
		return sheetId;
	}

	public UUID getAdmissionId() {
		return admissionId;
	}

	public String getRollNo() {
		return rollNo;
	}

	public boolean isAbsent() {
		return absent;
	}

	public Integer getRightCount() {
		return rightCount;
	}

	public Integer getWrongCount() {
		return wrongCount;
	}

	public BigDecimal getMcq() {
		return mcq;
	}

	public BigDecimal getWritten() {
		return written;
	}

	public BigDecimal getTotal() {
		return total;
	}

	public Integer getRank() {
		return rank;
	}

	public Boolean getPassed() {
		return passed;
	}

	public BigDecimal getFileTotal() {
		return fileTotal;
	}

	public Integer getFileRank() {
		return fileRank;
	}

	public Integer getRowNumber() {
		return rowNumber;
	}

}
