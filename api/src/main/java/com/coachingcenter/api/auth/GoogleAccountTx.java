package com.coachingcenter.api.auth;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.users.StudentProfile;
import com.coachingcenter.api.users.StudentProfileRepository;
import com.coachingcenter.api.users.User;
import com.coachingcenter.api.users.UserRepository;

@Service
class GoogleAccountTx {

	private static final String ACTIVE = "ACTIVE";

	private final UserRepository users;

	private final StudentProfileRepository profiles;

	private final AuthService auth;

	private final AuditRecorder audit;

	GoogleAccountTx(UserRepository users, StudentProfileRepository profiles, AuthService auth, AuditRecorder audit) {
		this.users = users;
		this.profiles = profiles;
		this.auth = auth;
		this.audit = audit;
	}

	@Transactional
	public User resolve(GoogleIdentity identity) {
		String subject = identity.subject();
		if (subject == null || !subject.matches("^[\\x21-\\x7E]{1,255}$")) {
			throw new GoogleCallbackException(ErrorCode.GOOGLE_SIGN_IN_FAILED);
		}
		User linked = users.findByGoogleSubAndDeletedAtIsNull(subject).orElse(null);
		if (linked != null) {
			return acceptLinked(linked);
		}
		String email = normalizeEmail(identity.email());
		if (email.isBlank() || !email.contains("@")) {
			throw new GoogleCallbackException(ErrorCode.GOOGLE_EMAIL_MISSING);
		}
		if (!identity.emailVerified()) {
			throw new GoogleCallbackException(ErrorCode.GOOGLE_EMAIL_NOT_VERIFIED);
		}
		User existing = users.findByEmailAndDeletedAtIsNull(email).orElse(null);
		if (existing != null) {
			return link(existing, subject);
		}
		return create(email, subject, identity.name());
	}

	private User acceptLinked(User user) {
		if (!Role.STUDENT.name().equals(user.getRole())) {
			throw new GoogleCallbackException(ErrorCode.GOOGLE_ROLE_NOT_ALLOWED);
		}
		if (!ACTIVE.equals(user.getStatus())) {
			throw new GoogleCallbackException(ErrorCode.GOOGLE_ACCOUNT_INACTIVE);
		}
		return user;
	}

	private User link(User user, String subject) {
		if (!Role.STUDENT.name().equals(user.getRole())) {
			throw new GoogleCallbackException(ErrorCode.GOOGLE_ROLE_NOT_ALLOWED);
		}
		if (!ACTIVE.equals(user.getStatus())) {
			throw new GoogleCallbackException(ErrorCode.GOOGLE_ACCOUNT_INACTIVE);
		}
		if (user.getGoogleSub() != null && !user.getGoogleSub().equals(subject)) {
			throw new GoogleCallbackException(ErrorCode.GOOGLE_IDENTITY_CONFLICT);
		}
		boolean linked = user.getGoogleSub() == null;
		if (linked) {
			user.setGoogleSub(subject);
		}
		boolean markVerified = user.getEmailVerifiedAt() == null;
		if (markVerified) {
			user.setEmailVerifiedAt(Instant.now());
		}
		if (linked || markVerified) {
			users.saveAndFlush(user);
		}
		if (linked) {
			audit.record(AuditRecorder.GOOGLE_ACCOUNT_LINKED, user.getId());
		}
		return user;
	}

	private User create(String email, String subject, String name) {
		User user = new User();
		user.setEmail(email);
		user.setPhone(null);
		user.setPasswordHash(null);
		user.setRole(Role.STUDENT.name());
		user.setStatus(ACTIVE);
		user.setEmailVerifiedAt(Instant.now());
		user.setGoogleSub(subject);
		user = users.saveAndFlush(user);
		StudentProfile profile = new StudentProfile();
		profile.setUserId(user.getId());
		profile.setFullName(fullName(name, email));
		profile.setStudentCode(auth.allocateStudentCode());
		profiles.save(profile);
		audit.record(AuditRecorder.GOOGLE_ACCOUNT_CREATED, user.getId());
		return user;
	}

	private static String fullName(String name, String email) {
		String value = name == null ? "" : name.trim();
		if (value.isBlank()) {
			int at = email.indexOf('@');
			value = at > 0 ? email.substring(0, at) : "Student";
		}
		if (value.length() > 255) {
			return value.substring(0, 255);
		}
		return value;
	}

	private static String normalizeEmail(String email) {
		return email == null ? "" : email.trim().toLowerCase();
	}

}
