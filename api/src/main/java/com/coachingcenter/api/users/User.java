package com.coachingcenter.api.users;

import java.time.Instant;
import java.util.UUID;

import com.coachingcenter.api.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

	@Column(nullable = false, length = 16)
	private String phone;

	@Column(nullable = false, length = 320)
	private String email;

	@Column(name = "email_verified_at")
	private Instant emailVerifiedAt;

	@Column(name = "password_hash", length = 255)
	private String passwordHash;

	@Column(nullable = false, length = 32)
	private String role;

	@Column(nullable = false, length = 32)
	private String status;

	@Column(name = "last_login_at")
	private Instant lastLoginAt;

	@Column(name = "created_by")
	private UUID createdBy;

	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
		this.phone = phone;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public Instant getEmailVerifiedAt() {
		return emailVerifiedAt;
	}

	public void setEmailVerifiedAt(Instant emailVerifiedAt) {
		this.emailVerifiedAt = emailVerifiedAt;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public String getRole() {
		return role;
	}

	public void setRole(String role) {
		this.role = role;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Instant getLastLoginAt() {
		return lastLoginAt;
	}

	public void setLastLoginAt(Instant lastLoginAt) {
		this.lastLoginAt = lastLoginAt;
	}

	public UUID getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(UUID createdBy) {
		this.createdBy = createdBy;
	}

}
