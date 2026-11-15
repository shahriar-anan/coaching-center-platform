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

	/** Empty for a public application until onboarding makes or matches the account (V94). */
	@Column(name = "student_id")
	private UUID studentId;

	@Column(nullable = false, length = 24)
	private String status;

	@Column(name = "created_by")
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

	/** The roll number the onboarding gave (digits, unique across admissions). Null until onboarded (rules R4, A6). */
	@Column(name = "roll_no", length = 12)
	private String rollNo;

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

	/** The first instalment of the course fee, kept for the previous release (V100). The course fee is the total. */
	@Column(name = "monthly_fee", precision = 12, scale = 2)
	private BigDecimal monthlyFee;

	/** The total this student pays for the batch (V100, course-fee-plan.md). */
	@Column(name = "course_fee", precision = 12, scale = 2)
	private BigDecimal courseFee;

	/** The number of monthly instalments the course fee is paid in, chosen by the admin (V100). */
	@Column(name = "installments")
	private Short installments;

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

	/** The plain activation code, kept only while the desk may read it out (V87). Cleared on activation and on expiry. */
	@Column(name = "activation_code", length = 16)
	private String activationCode;

	@Column(name = "activation_code_expires_at")
	private Instant activationCodeExpiresAt;

	@Column(name = "activation_code_issued_by")
	private UUID activationCodeIssuedBy;

	@Column(name = "activated_at")
	private Instant activatedAt;

	/** Where the application came from: PUBLIC (no account yet), ACCOUNT (signed in), DESK (a walk-in). V94. */
	@Column(nullable = false, length = 8)
	private String source = SOURCE_DESK;

	/** The token of a public applicant (hash only). Kept while the application is open. V94. */
	@Column(name = "access_token_hash", length = 255)
	private String accessTokenHash;

	/** The join code of an account that already exists (hash only), with its expiry, send time and sender. V94. */
	@Column(name = "join_code_hash", length = 255)
	private String joinCodeHash;

	@Column(name = "join_code_expires_at")
	private Instant joinCodeExpiresAt;

	@Column(name = "join_code_sent_at")
	private Instant joinCodeSentAt;

	@Column(name = "join_code_sent_by")
	private UUID joinCodeSentBy;

	@Column(name = "join_code_used_at")
	private Instant joinCodeUsedAt;

	/** Wrong join codes typed for this admission. Five lock it until a new code is sent (plan section 9). */
	@Column(name = "join_code_failures", nullable = false)
	private int joinCodeFailures;

	/** The reopen link of a changes-requested public application (hash only, 7 days). V94. */
	@Column(name = "reopen_token_hash", length = 255)
	private String reopenTokenHash;

	@Column(name = "reopen_token_expires_at")
	private Instant reopenTokenExpiresAt;

	/** The account found for this applicant at onboarding, by email then phone (Q3). V94. */
	@Column(name = "matched_user_id")
	private UUID matchedUserId;

	public static final String SOURCE_PUBLIC = "PUBLIC";

	public static final String SOURCE_ACCOUNT = "ACCOUNT";

	public static final String SOURCE_DESK = "DESK";

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

	public String getRollNo() {
		return rollNo;
	}

	public void setRollNo(String rollNo) {
		this.rollNo = rollNo;
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

	public BigDecimal getCourseFee() {
		return courseFee;
	}

	public void setCourseFee(BigDecimal courseFee) {
		this.courseFee = courseFee;
	}

	public Integer getInstallments() {
		return installments == null ? null : installments.intValue();
	}

	public void setInstallments(Integer installments) {
		this.installments = installments == null ? null : installments.shortValue();
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

	public String getActivationCode() {
		return activationCode;
	}

	public void setActivationCode(String activationCode) {
		this.activationCode = activationCode;
	}

	public Instant getActivationCodeExpiresAt() {
		return activationCodeExpiresAt;
	}

	public void setActivationCodeExpiresAt(Instant activationCodeExpiresAt) {
		this.activationCodeExpiresAt = activationCodeExpiresAt;
	}

	public UUID getActivationCodeIssuedBy() {
		return activationCodeIssuedBy;
	}

	public void setActivationCodeIssuedBy(UUID activationCodeIssuedBy) {
		this.activationCodeIssuedBy = activationCodeIssuedBy;
	}

	public Instant getActivatedAt() {
		return activatedAt;
	}

	public void setActivatedAt(Instant activatedAt) {
		this.activatedAt = activatedAt;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public String getAccessTokenHash() {
		return accessTokenHash;
	}

	public void setAccessTokenHash(String accessTokenHash) {
		this.accessTokenHash = accessTokenHash;
	}

	public String getJoinCodeHash() {
		return joinCodeHash;
	}

	public void setJoinCodeHash(String joinCodeHash) {
		this.joinCodeHash = joinCodeHash;
	}

	public Instant getJoinCodeExpiresAt() {
		return joinCodeExpiresAt;
	}

	public void setJoinCodeExpiresAt(Instant joinCodeExpiresAt) {
		this.joinCodeExpiresAt = joinCodeExpiresAt;
	}

	public Instant getJoinCodeSentAt() {
		return joinCodeSentAt;
	}

	public void setJoinCodeSentAt(Instant joinCodeSentAt) {
		this.joinCodeSentAt = joinCodeSentAt;
	}

	public UUID getJoinCodeSentBy() {
		return joinCodeSentBy;
	}

	public void setJoinCodeSentBy(UUID joinCodeSentBy) {
		this.joinCodeSentBy = joinCodeSentBy;
	}

	public Instant getJoinCodeUsedAt() {
		return joinCodeUsedAt;
	}

	public void setJoinCodeUsedAt(Instant joinCodeUsedAt) {
		this.joinCodeUsedAt = joinCodeUsedAt;
	}

	public int getJoinCodeFailures() {
		return joinCodeFailures;
	}

	public void setJoinCodeFailures(int joinCodeFailures) {
		this.joinCodeFailures = joinCodeFailures;
	}

	public String getReopenTokenHash() {
		return reopenTokenHash;
	}

	public void setReopenTokenHash(String reopenTokenHash) {
		this.reopenTokenHash = reopenTokenHash;
	}

	public Instant getReopenTokenExpiresAt() {
		return reopenTokenExpiresAt;
	}

	public void setReopenTokenExpiresAt(Instant reopenTokenExpiresAt) {
		this.reopenTokenExpiresAt = reopenTokenExpiresAt;
	}

	public UUID getMatchedUserId() {
		return matchedUserId;
	}

	public void setMatchedUserId(UUID matchedUserId) {
		this.matchedUserId = matchedUserId;
	}

}
