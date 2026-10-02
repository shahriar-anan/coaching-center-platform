package com.coachingcenter.api.users;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.web.PageResponse;
import tools.jackson.databind.JsonNode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@RestController
public class AccountController {

	private final AccountService accounts;

	private final PermissionChecker permissions;

	public AccountController(AccountService accounts, PermissionChecker permissions) {
		this.accounts = accounts;
		this.permissions = permissions;
	}

	@PostMapping("/api/v1/admins")
	public ResponseEntity<AccountService.CreatedAccount> createAdmin(@RequestBody JsonNode body) {
		require(Permissions.ADMIN_MANAGE);
		rejectPassword(body);
		rejectMasterAdminRole(body);
		NameRequest request = nameRequest(body);
		AccountService.CreatedAccount created = accounts.createSystemAdmin(request.fullName(), request.phone(),
				request.email(), actor().userId());
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}

	@GetMapping("/api/v1/admins")
	public PageResponse<AccountService.AccountView> listAdmins(@PageableDefault(size = 20) Pageable pageable) {
		require(Permissions.ADMIN_MANAGE);
		return PageResponse.from(accounts.listAdmins(pageable));
	}

	@PatchMapping("/api/v1/admins/{id}/status")
	public ResponseEntity<Void> changeAdminStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
		require(Permissions.ADMIN_MANAGE);
		accounts.changeStatus(id, request.status(), Role.SYSTEM_ADMIN);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/api/v1/admins/{id}")
	public ResponseEntity<Void> deleteAdmin(@PathVariable UUID id) {
		require(Permissions.ADMIN_DELETE);
		accounts.delete(id, Role.SYSTEM_ADMIN);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/api/v1/admins/{id}/activation-code")
	public ActivationCodeResponse reissueAdminCode(@PathVariable UUID id) {
		require(Permissions.ADMIN_MANAGE);
		return new ActivationCodeResponse(accounts.reissueActivationCode(id, Role.SYSTEM_ADMIN, actor().userId()));
	}

	@PostMapping("/api/v1/students")
	public ResponseEntity<AccountService.CreatedAccount> createStudent(@RequestBody JsonNode body) {
		require(Permissions.STUDENT_CREATE);
		rejectPassword(body);
		NameRequest request = nameRequest(body);
		AccountService.CreatedAccount created = accounts.createStudent(request.fullName(), request.phone(), request.email(),
				actor().userId());
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}

	@GetMapping("/api/v1/students")
	public PageResponse<AccountService.AccountView> listStudents(@RequestParam(defaultValue = "") String q,
			@PageableDefault(size = 20) Pageable pageable) {
		require(Permissions.STUDENT_READ);
		return PageResponse.from(accounts.listStudents(q, pageable));
	}

	@GetMapping("/api/v1/students/{id}")
	public AccountService.AccountView getStudent(@PathVariable UUID id) {
		CurrentActor actor = actor();
		boolean own = actor.userId().equals(id);
		if (!permissions.has(Permissions.STUDENT_READ, own)) {
			throw denied();
		}
		return accounts.getStudent(id, actor);
	}

	@PatchMapping("/api/v1/students/{id}")
	public AccountService.AccountView updateStudent(@PathVariable UUID id, @Valid @RequestBody UpdateStudentRequest request) {
		CurrentActor actor = actor();
		boolean own = actor.userId().equals(id);
		if (!permissions.has(Permissions.STUDENT_UPDATE, own)) {
			throw denied();
		}
		return accounts.updateStudent(id, request.fullName(), actor);
	}

	@PatchMapping("/api/v1/students/{id}/status")
	public ResponseEntity<Void> changeStudentStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
		require(Permissions.STUDENT_STATUS);
		accounts.changeStatus(id, request.status(), Role.STUDENT);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/api/v1/students/{id}/activation-code")
	public ActivationCodeResponse reissueStudentCode(@PathVariable UUID id) {
		require(Permissions.STUDENT_ISSUE_ACTIVATION_CODE);
		return new ActivationCodeResponse(accounts.reissueActivationCode(id, Role.STUDENT, actor().userId()));
	}

	@DeleteMapping("/api/v1/students/{id}")
	public ResponseEntity<Void> deleteStudent(@PathVariable UUID id) {
		require(Permissions.STUDENT_DELETE);
		accounts.delete(id, Role.STUDENT);
		return ResponseEntity.noContent().build();
	}

	private void require(String permission) {
		if (!permissions.has(permission)) {
			throw denied();
		}
	}

	private CurrentActor actor() {
		var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor) {
			return actor;
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

	private static void rejectPassword(JsonNode body) {
		if (body.has("password") || body.has("passwordHash") || body.has("newPassword") || body.has("currentPassword")) {
			throw new ApiException(ErrorCode.PASSWORD_NOT_ACCEPTED, HttpStatus.BAD_REQUEST,
					"Password cannot be set by this request.");
		}
	}

	private static void rejectMasterAdminRole(JsonNode body) {
		if (body.has("role") && Role.MASTER_ADMIN.name().equals(body.get("role").asText())) {
			throw new com.coachingcenter.api.common.error.MasterAdminProtectedException();
		}
	}

	private static NameRequest nameRequest(JsonNode body) {
		String fullName = text(body, "fullName");
		String phone = text(body, "phone");
		String email = text(body, "email");
		if (fullName == null || phone == null || email == null || !email.contains("@")) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
		}
		return new NameRequest(fullName, phone, email);
	}

	private static String text(JsonNode body, String field) {
		JsonNode node = body.get(field);
		if (node == null || node.isNull() || node.asText().isBlank()) {
			return null;
		}
		return node.asText();
	}

	private static ApiException denied() {
		return new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
	}

	public record NameRequest(@NotBlank String fullName, @NotBlank String phone, @Email @NotBlank String email) {
	}

	public record StatusRequest(@NotBlank String status) {
	}

	public record UpdateStudentRequest(@NotBlank String fullName) {
	}

	public record ActivationCodeResponse(String activationCode) {
	}

}
