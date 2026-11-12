package com.coachingcenter.api.offline;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** One student admitted in person, from the first form to the close (V82). At most one row per student is not CLOSED. */
@Entity
@Table(name = "offline_admissions")
public class OfflineAdmission {

	public static final String AWAITING_FORM = "AWAITING_FORM";

	public static final String SUBMITTED = "SUBMITTED";

	public static final String CHANGES_REQUESTED = "CHANGES_REQUESTED";

	public static final String READY = "READY";

	public static final String ONBOARDED = "ONBOARDED";

	public static final String CLOSED = "CLOSED";

	@Id
	private UUID id;

	@Column(name = "student_id", nullable = false)
	private UUID studentId;

	@Column(nullable = false, length = 24)
	private String status;

	@Column(name = "created_by", nullable = false)
	private UUID createdBy;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Version
	@Column(nullable = false)
	private Long version;

	@Column(name = "full_name", length = 200)
	private String fullName;

	@Column(name = "father_name", length = 200)
	private String fatherName;

	@Column(name = "mother_name", length = 200)
	private String motherName;

	@Column(name = "date_of_birth")
	private LocalDate dateOfBirth;

	@Column(length = 255)
	private String email;

	@Column(length = 16)
	private String gender;

	@Column(length = 200)
	private String university;

	@Column(name = "semester_batch", length = 100)
	private String semesterBatch;

	@Column(length = 32)
	private String nid;

	@Column(length = 32)
	private String mobile;

	@Column(name = "emergency_mobile", length = 32)
	private String emergencyMobile;

	@Column(name = "hall_mess", length = 300)
	private String hallMess;

	@Column(length = 100)
	private String thana;

	@Column(length = 100)
	private String district;

	@Column(name = "present_address", length = 1000)
	private String presentAddress;

	@Column(name = "permanent_address", length = 1000)
	private String permanentAddress;

	@Column(name = "photo_stored_name", length = 255)
	private String photoStoredName;

	@Column(name = "photo_content_type", length = 100)
	private String photoContentType;

	@Column(name = "photo_byte_size")
	private Long photoByteSize;

	@Column(name = "draft_saved_at")
	private Instant draftSavedAt;

	@Column(name = "submitted_at")
	private Instant submittedAt;

	@Column(name = "submit_count", nullable = false)
	private int submitCount;

	@Column(name = "changes_reason", length = 500)
	private String changesReason;

	@Column(name = "changes_requested_by")
	private UUID changesRequestedBy;

	@Column(name = "changes_requested_at")
	private Instant changesRequestedAt;

	@Column(name = "batch_id")
	private UUID batchId;

	@Column(name = "monthly_fee", precision = 12, scale = 2)
	private BigDecimal monthlyFee;

	@Column(name = "admitted_on")
	private LocalDate admittedOn;

	@Column(name = "first_due_on")
	private LocalDate firstDueOn;

	@Column(name = "course_ends_on")
	private LocalDate courseEndsOn;

	@Column(name = "ready_by")
	private UUID readyBy;

	@Column(name = "ready_at")
	private Instant readyAt;

	@Column(name = "onboarded_by")
	private UUID onboardedBy;

	@Column(name = "onboarded_at")
	private Instant onboardedAt;

	@Column(name = "signed_stored_name", length = 255)
	private String signedStoredName;

	@Column(name = "signed_original_name", length = 500)
	private String signedOriginalName;

	@Column(name = "signed_content_type", length = 100)
	private String signedContentType;

	@Column(name = "signed_byte_size")
	private Long signedByteSize;

	@Column(name = "signed_uploaded_by")
	private UUID signedUploadedBy;

	@Column(name = "signed_uploaded_at")
	private Instant signedUploadedAt;

	@Column(name = "closed_at")
	private Instant closedAt;

	@Column(name = "closed_reason", length = 500)
	private String closedReason;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public UUID getStudentId() {
		return studentId;
	}

	public void setStudentId(UUID studentId) {
		this.studentId = studentId;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public UUID getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(UUID createdBy) {
		this.createdBy = createdBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Long getVersion() {
		return version;
	}

	public void setVersion(Long version) {
		this.version = version;
	}

	public String getFullName() {
		return fullName;
	}

	public void setFullName(String fullName) {
		this.fullName = fullName;
	}

	public String getFatherName() {
		return fatherName;
	}

	public void setFatherName(String fatherName) {
		this.fatherName = fatherName;
	}

	public String getMotherName() {
		return motherName;
	}

	public void setMotherName(String motherName) {
		this.motherName = motherName;
	}

	public LocalDate getDateOfBirth() {
		return dateOfBirth;
	}

	public void setDateOfBirth(LocalDate dateOfBirth) {
		this.dateOfBirth = dateOfBirth;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getGender() {
		return gender;
	}

	public void setGender(String gender) {
		this.gender = gender;
	}

	public String getUniversity() {
		return university;
	}

	public void setUniversity(String university) {
		this.university = university;
	}

	public String getSemesterBatch() {
		return semesterBatch;
	}

	public void setSemesterBatch(String semesterBatch) {
		this.semesterBatch = semesterBatch;
	}

	public String getNid() {
		return nid;
	}

	public void setNid(String nid) {
		this.nid = nid;
	}

	public String getMobile() {
		return mobile;
	}

	public void setMobile(String mobile) {
		this.mobile = mobile;
	}

	public String getEmergencyMobile() {
		return emergencyMobile;
	}

	public void setEmergencyMobile(String emergencyMobile) {
		this.emergencyMobile = emergencyMobile;
	}

	public String getHallMess() {
		return hallMess;
	}

	public void setHallMess(String hallMess) {
		this.hallMess = hallMess;
	}

	public String getThana() {
		return thana;
	}

	public void setThana(String thana) {
		this.thana = thana;
	}

	public String getDistrict() {
		return district;
	}

	public void setDistrict(String district) {
		this.district = district;
	}

	public String getPresentAddress() {
		return presentAddress;
	}

	public void setPresentAddress(String presentAddress) {
		this.presentAddress = presentAddress;
	}

	public String getPermanentAddress() {
		return permanentAddress;
	}

	public void setPermanentAddress(String permanentAddress) {
		this.permanentAddress = permanentAddress;
	}

	public String getPhotoStoredName() {
		return photoStoredName;
	}

	public void setPhotoStoredName(String photoStoredName) {
		this.photoStoredName = photoStoredName;
	}

	public String getPhotoContentType() {
		return photoContentType;
	}

	public void setPhotoContentType(String photoContentType) {
		this.photoContentType = photoContentType;
	}

	public Long getPhotoByteSize() {
		return photoByteSize;
	}

	public void setPhotoByteSize(Long photoByteSize) {
		this.photoByteSize = photoByteSize;
	}

	public Instant getDraftSavedAt() {
		return draftSavedAt;
	}

	public void setDraftSavedAt(Instant draftSavedAt) {
		this.draftSavedAt = draftSavedAt;
	}

	public Instant getSubmittedAt() {
		return submittedAt;
	}

	public void setSubmittedAt(Instant submittedAt) {
		this.submittedAt = submittedAt;
	}

	public int getSubmitCount() {
		return submitCount;
	}

	public void setSubmitCount(int submitCount) {
		this.submitCount = submitCount;
	}

	public String getChangesReason() {
		return changesReason;
	}

	public void setChangesReason(String changesReason) {
		this.changesReason = changesReason;
	}

	public UUID getChangesRequestedBy() {
		return changesRequestedBy;
	}

	public void setChangesRequestedBy(UUID changesRequestedBy) {
		this.changesRequestedBy = changesRequestedBy;
	}

	public Instant getChangesRequestedAt() {
		return changesRequestedAt;
	}

	public void setChangesRequestedAt(Instant changesRequestedAt) {
		this.changesRequestedAt = changesRequestedAt;
	}

	public UUID getBatchId() {
		return batchId;
	}

	public void setBatchId(UUID batchId) {
		this.batchId = batchId;
	}

	public BigDecimal getMonthlyFee() {
		return monthlyFee;
	}

	public void setMonthlyFee(BigDecimal monthlyFee) {
		this.monthlyFee = monthlyFee;
	}

	public LocalDate getAdmittedOn() {
		return admittedOn;
	}

	public void setAdmittedOn(LocalDate admittedOn) {
		this.admittedOn = admittedOn;
	}

	public LocalDate getFirstDueOn() {
		return firstDueOn;
	}

	public void setFirstDueOn(LocalDate firstDueOn) {
		this.firstDueOn = firstDueOn;
	}

	public LocalDate getCourseEndsOn() {
		return courseEndsOn;
	}

	public void setCourseEndsOn(LocalDate courseEndsOn) {
		this.courseEndsOn = courseEndsOn;
	}

	public UUID getReadyBy() {
		return readyBy;
	}

	public void setReadyBy(UUID readyBy) {
		this.readyBy = readyBy;
	}

	public Instant getReadyAt() {
		return readyAt;
	}

	public void setReadyAt(Instant readyAt) {
		this.readyAt = readyAt;
	}

	public UUID getOnboardedBy() {
		return onboardedBy;
	}

	public void setOnboardedBy(UUID onboardedBy) {
		this.onboardedBy = onboardedBy;
	}

	public Instant getOnboardedAt() {
		return onboardedAt;
	}

	public void setOnboardedAt(Instant onboardedAt) {
		this.onboardedAt = onboardedAt;
	}

	public String getSignedStoredName() {
		return signedStoredName;
	}

	public void setSignedStoredName(String signedStoredName) {
		this.signedStoredName = signedStoredName;
	}

	public String getSignedOriginalName() {
		return signedOriginalName;
	}

	public void setSignedOriginalName(String signedOriginalName) {
		this.signedOriginalName = signedOriginalName;
	}

	public String getSignedContentType() {
		return signedContentType;
	}

	public void setSignedContentType(String signedContentType) {
		this.signedContentType = signedContentType;
	}

	public Long getSignedByteSize() {
		return signedByteSize;
	}

	public void setSignedByteSize(Long signedByteSize) {
		this.signedByteSize = signedByteSize;
	}

	public UUID getSignedUploadedBy() {
		return signedUploadedBy;
	}

	public void setSignedUploadedBy(UUID signedUploadedBy) {
		this.signedUploadedBy = signedUploadedBy;
	}

	public Instant getSignedUploadedAt() {
		return signedUploadedAt;
	}

	public void setSignedUploadedAt(Instant signedUploadedAt) {
		this.signedUploadedAt = signedUploadedAt;
	}

	public Instant getClosedAt() {
		return closedAt;
	}

	public void setClosedAt(Instant closedAt) {
		this.closedAt = closedAt;
	}

	public String getClosedReason() {
		return closedReason;
	}

	public void setClosedReason(String closedReason) {
		this.closedReason = closedReason;
	}

}
