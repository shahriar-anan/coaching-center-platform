package com.coachingcenter.api.users;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.auth.AuthService;
import com.coachingcenter.api.auth.PhoneNumbers;
import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.auth.permission.MasterAdminGuard;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

@Service
public class AccountService {

	private final UserRepository users;

	private final StudentProfileRepository studentProfiles;

	private final AdminProfileRepository adminProfiles;

	private final AuthService auth;

	private final MasterAdminGuard masterAdminGuard;

	public AccountService(UserRepository users, StudentProfileRepository studentProfiles,
			AdminProfileRepository adminProfiles, AuthService auth, MasterAdminGuard masterAdminGuard) {
		this.users = users;
		this.studentProfiles = studentProfiles;
		this.adminProfiles = adminProfiles;
		this.auth = auth;
		this.masterAdminGuard = masterAdminGuard;
	}

	@Transactional
	public CreatedAccount createStudent(String fullName, String phone, String email, UUID createdBy) {
		User user = newPendingUser(fullName, phone, email, Role.STUDENT, createdBy);
		StudentProfile profile = new StudentProfile();
		profile.setUserId(user.getId());
		profile.setFullName(fullName.trim());
		profile.setStudentCode(nextStudentCode());
		studentProfiles.save(profile);
		String code = auth.issueActivationCode(user, createdBy);
		return new CreatedAccount(user.getId(), profile.getFullName(), user.getPhone(), user.getEmail(), user.getRole(),
				user.getStatus(), code, profile.getStudentCode());
	}

	@Transactional
	public CreatedAccount createSystemAdmin(String fullName, String phone, String email, UUID createdBy) {
		User user = newPendingUser(fullName, phone, email, Role.SYSTEM_ADMIN, createdBy);
		AdminProfile profile = new AdminProfile();
		profile.setUserId(user.getId());
		profile.setFullName(fullName.trim());
		adminProfiles.save(profile);
		String code = auth.issueActivationCode(user, createdBy);
		return new CreatedAccount(user.getId(), profile.getFullName(), user.getPhone(), user.getEmail(), user.getRole(),
				user.getStatus(), code, null);
	}

	@Transactional(readOnly = true)
	public Page<AccountView> listStudents(String query, Pageable pageable) {
		String q = query == null ? "" : query.trim();
		return users.searchStudents(q, pageable).map(this::view);
	}

	@Transactional(readOnly = true)
	public Page<AccountView> listAdmins(Pageable pageable) {
		return users.findByRoleAndDeletedAtIsNull(Role.SYSTEM_ADMIN.name(), pageable).map(this::view);
	}

	@Transactional(readOnly = true)
	public AccountView getStudent(UUID id, CurrentActor actor) {
		User user = requireStudent(id);
		if (actor.role() == Role.STUDENT && !actor.userId().equals(id)) {
			throw denied();
		}
		return view(user);
	}

	@Transactional
	public AccountView updateStudent(UUID id, String fullName, CurrentActor actor) {
		if (actor.role() == Role.STUDENT && !actor.userId().equals(id)) {
			throw denied();
		}
		User user = requireStudent(id);
		masterAdminGuard.rejectIfMasterAdmin(Role.valueOf(user.getRole()));
		StudentProfile profile = studentProfiles.findById(id).orElseThrow(this::notFound);
		profile.setFullName(fullName.trim());
		return view(user);
	}

	@Transactional
	public void changeStatus(UUID id, String status, Role expectedRole) {
		if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status)) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Status is invalid.");
		}
		User user = requireRole(id, expectedRole);
		masterAdminGuard.rejectIfMasterAdmin(Role.valueOf(user.getRole()));
		user.setStatus(status);
	}

	@Transactional
	public void delete(UUID id, Role expectedRole) {
		User user = requireRole(id, expectedRole);
		masterAdminGuard.rejectIfMasterAdmin(Role.valueOf(user.getRole()));
		user.setDeletedAt(Instant.now());
	}

	@Transactional
	public String reissueActivationCode(UUID id, Role expectedRole, UUID createdBy) {
		User user = requireRole(id, expectedRole);
		masterAdminGuard.rejectIfMasterAdmin(Role.valueOf(user.getRole()));
		return auth.issueActivationCode(user, createdBy);
	}

	private User newPendingUser(String fullName, String phone, String email, Role role, UUID createdBy) {
		String normalizedPhone = PhoneNumbers.normalize(phone);
		String normalizedEmail = email.trim().toLowerCase();
		if (users.findByPhoneAndDeletedAtIsNull(normalizedPhone).isPresent()) {
			throw new ApiException(ErrorCode.DUPLICATE_PHONE, HttpStatus.CONFLICT, "Phone number is already in use.");
		}
		if (users.findByEmailAndDeletedAtIsNull(normalizedEmail).isPresent()) {
			throw new ApiException(ErrorCode.DUPLICATE_EMAIL, HttpStatus.CONFLICT, "Email is already in use.");
		}
		User user = new User();
		user.setPhone(normalizedPhone);
		user.setEmail(normalizedEmail);
		user.setPasswordHash(null);
		user.setRole(role.name());
		user.setStatus("PENDING_ACTIVATION");
		user.setCreatedBy(createdBy);
		return users.save(user);
	}

	private String nextStudentCode() {
		java.security.SecureRandom random = new java.security.SecureRandom();
		for (int attempt = 0; attempt < 5; attempt++) {
			String code = "S" + (10000000 + random.nextInt(90000000));
			if (!studentProfiles.existsByStudentCode(code)) {
				return code;
			}
		}
		throw new IllegalStateException("Could not allocate a student code.");
	}

	private User requireStudent(UUID id) {
		return requireRole(id, Role.STUDENT);
	}

	private User requireRole(UUID id, Role expectedRole) {
		User user = users.findByIdAndDeletedAtIsNull(id).orElseThrow(this::notFound);
		masterAdminGuard.rejectIfMasterAdmin(Role.valueOf(user.getRole()));
		if (!expectedRole.name().equals(user.getRole())) {
			throw notFound();
		}
		return user;
	}

	private AccountView view(User user) {
		String fullName = null;
		String studentCode = null;
		if (Role.STUDENT.name().equals(user.getRole())) {
			StudentProfile profile = studentProfiles.findById(user.getId()).orElse(null);
			if (profile != null) {
				fullName = profile.getFullName();
				studentCode = profile.getStudentCode();
			}
		}
		else {
			AdminProfile profile = adminProfiles.findById(user.getId()).orElse(null);
			if (profile != null) {
				fullName = profile.getFullName();
			}
		}
		return new AccountView(user.getId(), fullName, user.getPhone(), user.getEmail(), user.getRole(), user.getStatus(),
				studentCode);
	}

	private ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	private ApiException denied() {
		return new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
	}

	public record CreatedAccount(UUID id, String fullName, String phone, String email, String role, String status,
			String activationCode, String studentCode) {
	}

	public record AccountView(UUID id, String fullName, String phone, String email, String role, String status,
			String studentCode) {
	}

}
