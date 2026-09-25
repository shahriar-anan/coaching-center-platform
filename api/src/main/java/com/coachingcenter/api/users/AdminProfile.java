package com.coachingcenter.api.users;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "admin_profiles")
public class AdminProfile {

	@Id
	@Column(name = "user_id")
	private UUID userId;

	@Column(name = "full_name", nullable = false, length = 255)
	private String fullName;

	public UUID getUserId() {
		return userId;
	}

	public void setUserId(UUID userId) {
		this.userId = userId;
	}

	public String getFullName() {
		return fullName;
	}

	public void setFullName(String fullName) {
		this.fullName = fullName;
	}

}
