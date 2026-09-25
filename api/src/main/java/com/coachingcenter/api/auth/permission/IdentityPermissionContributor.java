package com.coachingcenter.api.auth.permission;

import org.springframework.stereotype.Component;

import com.coachingcenter.api.auth.Role;

@Component
public class IdentityPermissionContributor implements PermissionContributor {

	@Override
	public void contribute(PermissionRegistry registry) {
		grantStaff(registry, Role.MASTER_ADMIN);
		registry.grant(Role.MASTER_ADMIN, Permissions.STUDENT_DELETE, PermissionScope.ANY);
		registry.grant(Role.MASTER_ADMIN, Permissions.ADMIN_MANAGE, PermissionScope.ANY);
		registry.grant(Role.MASTER_ADMIN, Permissions.AUDIT_READ, PermissionScope.ANY);

		grantStaff(registry, Role.SYSTEM_ADMIN);

		registry.grant(Role.STUDENT, Permissions.STUDENT_READ, PermissionScope.OWN);
		registry.grant(Role.STUDENT, Permissions.STUDENT_UPDATE, PermissionScope.OWN);
	}

	private static void grantStaff(PermissionRegistry registry, Role role) {
		registry.grant(role, Permissions.STUDENT_READ, PermissionScope.ANY);
		registry.grant(role, Permissions.STUDENT_CREATE, PermissionScope.ANY);
		registry.grant(role, Permissions.STUDENT_UPDATE, PermissionScope.ANY);
		registry.grant(role, Permissions.STUDENT_STATUS, PermissionScope.ANY);
		registry.grant(role, Permissions.STUDENT_ISSUE_ACTIVATION_CODE, PermissionScope.ANY);
	}

}
