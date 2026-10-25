package com.coachingcenter.api.users;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.exams.LiveExamGrantRepository;
import com.coachingcenter.api.exams.LiveExamInstructorModules;
import com.coachingcenter.api.exams.LiveExamOrder;
import com.coachingcenter.api.exams.LiveExamOrderRepository;
import com.coachingcenter.api.exams.LiveExamParticipantRepository;

@Service
public class AccountService {

	private final UserRepository users;

	private final StudentProfileRepository studentProfiles;

	private final AdminProfileRepository adminProfiles;

	private final AuthService auth;

	private final MasterAdminGuard masterAdminGuard;

	private final AuditRecorder audit;

	private final LiveExamInstructorModules assignments;

	private final LiveExamOrderRepository orders;

	private final LiveExamGrantRepository grants;

	private final LiveExamParticipantRepository participants;

	public AccountService(UserRepository users, StudentProfileRepository studentProfiles,
			AdminProfileRepository adminProfiles, AuthService auth, MasterAdminGuard masterAdminGuard,
			AuditRecorder audit, LiveExamInstructorModules assignments, LiveExamOrderRepository orders,
			LiveExamGrantRepository grants, LiveExamParticipantRepository participants) {
		this.users = users;
		this.studentProfiles = studentProfiles;
		this.adminProfiles = adminProfiles;
		this.auth = auth;
		this.masterAdminGuard = masterAdminGuard;
		this.audit = audit;
		this.assignments = assignments;
		this.orders = orders;
		this.grants = grants;
		this.participants = participants;
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
	public CreatedAccount createInstructor(String fullName, String phone, String email, UUID createdBy) {
		User user = newPendingUser(fullName, phone, email, Role.INSTRUCTOR, createdBy);
		AdminProfile profile = new AdminProfile();
		profile.setUserId(user.getId());
		profile.setFullName(fullName.trim());
		adminProfiles.save(profile);
		String code = auth.issueActivationCode(user, createdBy);
		return new CreatedAccount(user.getId(), profile.getFullName(), user.getPhone(), user.getEmail(), user.getRole(),
				user.getStatus(), code, null);
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
	public Page<InstructorView> listInstructors(String query, String status, String assigned, Pageable pageable) {
		String q = query == null ? "" : query.trim();
		String state = status == null ? "" : status.trim();
		if (!state.isEmpty() && !List.of("ACTIVE", "PENDING_ACTIVATION", "INACTIVE").contains(state)) {
			throw validationFailed();
		}
		String held = assigned == null ? "" : assigned.trim().toLowerCase();
		if (!held.isEmpty() && !held.equals("true") && !held.equals("false")) {
			throw validationFailed();
		}
		Page<User> page = users.searchInstructors(q, state, held, pageable);
		Map<UUID, List<LiveExamInstructorModules.ModuleRef>> modules = assignments
			.modulesFor(page.getContent().stream().map(User::getId).toList());
		return page.map(user -> {
			AdminProfile profile = adminProfiles.findById(user.getId()).orElse(null);
			return new InstructorView(user.getId(), profile == null ? null : profile.getFullName(), user.getPhone(),
					user.getEmail(), user.getStatus(), user.getCreatedAt(), modules.getOrDefault(user.getId(), List.of()));
		});
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
		audit.record(AuditRecorder.USER_STATUS_CHANGED, user.getId());
	}

	@Transactional
	public void delete(UUID id, Role expectedRole) {
		User user = requireRole(id, expectedRole);
		masterAdminGuard.rejectIfMasterAdmin(Role.valueOf(user.getRole()));
		// A student who paid, or who was ever granted access, keeps the record: they can be suspended instead (R-10).
		if (expectedRole == Role.STUDENT
				&& (orders.existsByStudentIdAndStatus(user.getId(), LiveExamOrder.SUCCEEDED)
						|| grants.existsByStudentId(user.getId()))) {
			throw new ApiException(ErrorCode.ACCOUNT_HAS_PAID_ORDERS, HttpStatus.CONFLICT,
					"This student has paid or has access, so the account cannot be deleted. Suspend it instead.");
		}
		// An instructor who has marked a paper keeps the record of that mark (S-023).
		if (expectedRole == Role.INSTRUCTOR && participants.existsByEvaluatedBy(user.getId())) {
			throw new ApiException(ErrorCode.ACCOUNT_HAS_MARKED_WORK, HttpStatus.CONFLICT,
					"This instructor has marked papers, so the account cannot be deleted. Suspend it instead.");
		}
		user.setDeletedAt(Instant.now());
		audit.record(AuditRecorder.USER_DELETED, user.getId());
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

	private ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request is invalid.");
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

	public record InstructorView(UUID id, String fullName, String phone, String email, String status,
			java.time.Instant createdAt, List<LiveExamInstructorModules.ModuleRef> modules) {
	}

	public record AccountView(UUID id, String fullName, String phone, String email, String role, String status,
			String studentCode) {
	}

}
