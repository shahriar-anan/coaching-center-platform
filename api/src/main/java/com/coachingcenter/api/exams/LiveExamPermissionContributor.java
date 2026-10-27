package com.coachingcenter.api.exams;

import org.springframework.stereotype.Component;

import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.auth.permission.PermissionContributor;
import com.coachingcenter.api.auth.permission.PermissionRegistry;
import com.coachingcenter.api.auth.permission.PermissionScope;
import com.coachingcenter.api.auth.permission.Permissions;

@Component
public class LiveExamPermissionContributor implements PermissionContributor {

	@Override
	public void contribute(PermissionRegistry registry) {
		registry.grant(Role.STUDENT, Permissions.LIVE_EXAM_BROWSE, PermissionScope.ANY);
		registry.grant(Role.MASTER_ADMIN, Permissions.LIVE_EXAM_ROSTER, PermissionScope.ANY);
		registry.grant(Role.MASTER_ADMIN, Permissions.LIVE_EXAM_ASSIGN, PermissionScope.ANY);
		registry.grant(Role.MASTER_ADMIN, Permissions.LIVE_EXAM_EVALUATE, PermissionScope.ANY);
		registry.grant(Role.MASTER_ADMIN, Permissions.LIVE_EXAM_PUBLISH, PermissionScope.ANY);
		registry.grant(Role.MASTER_ADMIN, Permissions.LIVE_EXAM_ORDER_READ, PermissionScope.ANY);
		registry.grant(Role.SYSTEM_ADMIN, Permissions.LIVE_EXAM_ORDER_READ, PermissionScope.ANY);
		registry.grant(Role.MASTER_ADMIN, Permissions.LIVE_EXAM_GRANT, PermissionScope.ANY);
		registry.grant(Role.MASTER_ADMIN, Permissions.LIVE_EXAM_REVOKE, PermissionScope.ANY);
		registry.grant(Role.MASTER_ADMIN, Permissions.LIVE_EXAM_ORDER_CONFIRM, PermissionScope.ANY);
		registry.grant(Role.MASTER_ADMIN, Permissions.LIVE_EXAM_RELEASE, PermissionScope.ANY);
		registry.grant(Role.MASTER_ADMIN, Permissions.LIVE_EXAM_ANNOUNCE, PermissionScope.ANY);
		registry.grant(Role.INSTRUCTOR, Permissions.LIVE_EXAM_EVALUATE, PermissionScope.ANY);
	}

}
