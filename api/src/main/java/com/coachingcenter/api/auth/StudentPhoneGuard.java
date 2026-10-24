package com.coachingcenter.api.auth;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.users.User;
import com.coachingcenter.api.users.UserRepository;

@Component
public class StudentPhoneGuard {

	private final UserRepository users;

	public StudentPhoneGuard(UserRepository users) {
		this.users = users;
	}

	public void require(UUID userId) {
		User user = users.findByIdAndDeletedAtIsNull(userId)
			.orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED,
					"Authentication is required."));
		if (user.getPhone() == null || user.getPhone().isBlank()) {
			throw new ApiException(ErrorCode.PHONE_REQUIRED, HttpStatus.FORBIDDEN, "A phone number is required.");
		}
	}

}
