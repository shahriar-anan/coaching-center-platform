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

/** A class group an offline student is admitted into (V81). */
@Entity
@Table(name = "offline_batches")
public class OfflineBatch {

	public static final String STATUS_ACTIVE = "ACTIVE";

	public static final String STATUS_ARCHIVED = "ARCHIVED";

	@Id
	private UUID id;

	@Column(name = "name_en", nullable = false, length = 200)
	private String nameEn;

	@Column(name = "name_bn", nullable = false, length = 200)
	private String nameBn;

	@Column(name = "starts_on")
	private LocalDate startsOn;

	@Column(name = "ends_on", nullable = false)
	private LocalDate endsOn;

	@Column(name = "default_monthly_fee", nullable = false, precision = 12, scale = 2)
	private BigDecimal defaultMonthlyFee;

	/** The roll the batch's first student gets (rule R5). Digits only, 4 to 12 long; null when the batch names none. */
	@Column(name = "roll_start", length = 12)
	private String rollStart;

	/** The batch's WhatsApp group or channel link, or null (15 Nov). Only onboarded students of the batch are given it. */
	@Column(name = "whatsapp_url", length = 500)
	private String whatsappUrl;

	@Column(name = "routine_stored_name", length = 255)
	private String routineStoredName;

	@Column(name = "routine_original_name", length = 500)
	private String routineOriginalName;

	@Column(name = "routine_byte_size")
	private Long routineByteSize;

	@Column(name = "routine_content_type", length = 255)
	private String routineContentType;

	@Column(name = "routine_uploaded_by")
	private UUID routineUploadedBy;

	@Column(name = "routine_uploaded_at")
	private Instant routineUploadedAt;

	/** How many onboarded students the last routine upload notified (V86). Null for an upload made before V86. */
	@Column(name = "routine_students_notified")
	private Integer routineStudentsNotified;

	@Column(nullable = false, length = 16)
	private String status;

	/** The length of a class when a routine gives only its start time (V91). */
	@Column(name = "default_class_minutes", nullable = false)
	private Integer defaultClassMinutes = 80;

	/** The description in each language, plain paragraphs, up to 2,000 characters each (V93). Optional. */
	@Column(name = "description_en", length = 2000)
	private String descriptionEn;

	@Column(name = "description_bn", length = 2000)
	private String descriptionBn;

	/** The batch photo, stored like the profile photo (V93). Null when there is none. */
	@Column(name = "photo_stored_name", length = 255)
	private String photoStoredName;

	@Column(name = "photo_content_type", length = 100)
	private String photoContentType;

	@Column(name = "photo_byte_size")
	private Long photoByteSize;

	@Column(name = "photo_uploaded_by")
	private UUID photoUploadedBy;

	@Column(name = "photo_uploaded_at")
	private Instant photoUploadedAt;

	/** Off shows "Admissions closed" on the card and refuses new applications (V93, P6). */
	@Column(name = "admissions_open", nullable = false)
	private boolean admissionsOpen = true;

	/** The order of the cards and of the staff list: the lowest first (V93). */
	@Column(name = "display_order", nullable = false)
	private Integer displayOrder = 0;

	@Column(name = "created_by", nullable = false)
	private UUID createdBy;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private Long version;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public String getNameEn() {
		return nameEn;
	}

	public void setNameEn(String nameEn) {
		this.nameEn = nameEn;
	}

	public String getNameBn() {
		return nameBn;
	}

	public void setNameBn(String nameBn) {
		this.nameBn = nameBn;
	}

	public LocalDate getStartsOn() {
		return startsOn;
	}

	public void setStartsOn(LocalDate startsOn) {
		this.startsOn = startsOn;
	}

	public LocalDate getEndsOn() {
		return endsOn;
	}

	public void setEndsOn(LocalDate endsOn) {
		this.endsOn = endsOn;
	}

	public String getWhatsappUrl() {
		return whatsappUrl;
	}

	public void setWhatsappUrl(String whatsappUrl) {
		this.whatsappUrl = whatsappUrl;
	}

	public String getRollStart() {
		return rollStart;
	}

	public void setRollStart(String rollStart) {
		this.rollStart = rollStart;
	}

	public BigDecimal getDefaultMonthlyFee() {
		return defaultMonthlyFee;
	}

	public void setDefaultMonthlyFee(BigDecimal defaultMonthlyFee) {
		this.defaultMonthlyFee = defaultMonthlyFee;
	}

	public String getRoutineStoredName() {
		return routineStoredName;
	}

	public void setRoutineStoredName(String routineStoredName) {
		this.routineStoredName = routineStoredName;
	}

	public String getRoutineOriginalName() {
		return routineOriginalName;
	}

	public void setRoutineOriginalName(String routineOriginalName) {
		this.routineOriginalName = routineOriginalName;
	}

	public Long getRoutineByteSize() {
		return routineByteSize;
	}

	public void setRoutineByteSize(Long routineByteSize) {
		this.routineByteSize = routineByteSize;
	}

	public String getRoutineContentType() {
		return routineContentType;
	}

	public void setRoutineContentType(String routineContentType) {
		this.routineContentType = routineContentType;
	}

	public UUID getRoutineUploadedBy() {
		return routineUploadedBy;
	}

	public void setRoutineUploadedBy(UUID routineUploadedBy) {
		this.routineUploadedBy = routineUploadedBy;
	}

	public Instant getRoutineUploadedAt() {
		return routineUploadedAt;
	}

	public void setRoutineUploadedAt(Instant routineUploadedAt) {
		this.routineUploadedAt = routineUploadedAt;
	}

	public Integer getRoutineStudentsNotified() {
		return routineStudentsNotified;
	}

	public void setRoutineStudentsNotified(Integer routineStudentsNotified) {
		this.routineStudentsNotified = routineStudentsNotified;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Integer getDefaultClassMinutes() {
		return defaultClassMinutes;
	}

	public void setDefaultClassMinutes(Integer defaultClassMinutes) {
		this.defaultClassMinutes = defaultClassMinutes;
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

	public UUID getPhotoUploadedBy() {
		return photoUploadedBy;
	}

	public void setPhotoUploadedBy(UUID photoUploadedBy) {
		this.photoUploadedBy = photoUploadedBy;
	}

	public Instant getPhotoUploadedAt() {
		return photoUploadedAt;
	}

	public void setPhotoUploadedAt(Instant photoUploadedAt) {
		this.photoUploadedAt = photoUploadedAt;
	}

	public boolean isAdmissionsOpen() {
		return admissionsOpen;
	}

	public void setAdmissionsOpen(boolean admissionsOpen) {
		this.admissionsOpen = admissionsOpen;
	}

	public Integer getDisplayOrder() {
		return displayOrder;
	}

	public void setDisplayOrder(Integer displayOrder) {
		this.displayOrder = displayOrder;
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

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

	public Long getVersion() {
		return version;
	}

	public void setVersion(Long version) {
		this.version = version;
	}

}
