package com.coachingcenter.api.auth.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.common.error.MasterAdminProtectedException;

class AuthorizationTest {

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void grantsMatchThePhaseMatrix() {
		PermissionCatalog catalog = catalog();

		assertThat(catalog.allows(Role.MASTER_ADMIN, Permissions.STUDENT_DELETE, false)).isTrue();
		assertThat(catalog.allows(Role.MASTER_ADMIN, Permissions.ADMIN_MANAGE, false)).isTrue();
		assertThat(catalog.allows(Role.MASTER_ADMIN, Permissions.AUDIT_READ, false)).isTrue();
		assertThat(catalog.allows(Role.SYSTEM_ADMIN, Permissions.STUDENT_CREATE, false)).isTrue();
		assertThat(catalog.allows(Role.SYSTEM_ADMIN, Permissions.STUDENT_DELETE, false)).isFalse();
		assertThat(catalog.allows(Role.SYSTEM_ADMIN, Permissions.ADMIN_MANAGE, false)).isFalse();
		assertThat(catalog.allows(Role.SYSTEM_ADMIN, Permissions.AUDIT_READ, false)).isFalse();
		assertThat(catalog.allows(Role.STUDENT, Permissions.STUDENT_READ, true)).isTrue();
		assertThat(catalog.allows(Role.STUDENT, Permissions.STUDENT_READ, false)).isFalse();
		assertThat(catalog.allows(Role.STUDENT, Permissions.STUDENT_UPDATE, true)).isTrue();
		assertThat(catalog.allows(Role.STUDENT, Permissions.STUDENT_CREATE, false)).isFalse();
	}

	@Test
	void onlyMasterAdminCanReceiveADeletePermission() {
		PermissionCatalog catalog = catalog();
		catalog.grant(Role.MASTER_ADMIN, "COURSE_DELETE", PermissionScope.ANY);

		assertThat(catalog.allows(Role.MASTER_ADMIN, "COURSE_DELETE", false)).isTrue();
		assertThatThrownBy(() -> catalog.grant(Role.SYSTEM_ADMIN, "COURSE_DELETE", PermissionScope.ANY))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(catalog.allows(Role.SYSTEM_ADMIN, "COURSE_DELETE", false)).isFalse();
	}

	@Test
	void checkerUsesThePermissionNameForTheCurrentRole() {
		PermissionChecker checker = new PermissionChecker(catalog());
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				new CurrentActor(UUID.randomUUID(), Role.SYSTEM_ADMIN), null, List.of()));

		assertThat(checker.has(Permissions.STUDENT_STATUS)).isTrue();
		assertThat(checker.has(Permissions.STUDENT_DELETE)).isFalse();
	}

	@Test
	void masterAdminCannotBeCreatedChangedDeactivatedOrDeleted() {
		MasterAdminGuard guard = new MasterAdminGuard();

		assertThatThrownBy(() -> guard.rejectIfMasterAdmin(Role.MASTER_ADMIN))
			.isInstanceOf(MasterAdminProtectedException.class);
		guard.rejectIfMasterAdmin(Role.SYSTEM_ADMIN);
		guard.rejectIfMasterAdmin(Role.STUDENT);
	}

	private static PermissionCatalog catalog() {
		return new PermissionCatalog(List.of(new IdentityPermissionContributor()));
	}

}
