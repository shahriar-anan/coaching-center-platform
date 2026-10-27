package com.coachingcenter.api.exams;

import org.springframework.stereotype.Component;

import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.auth.permission.PermissionContributor;
import com.coachingcenter.api.auth.permission.PermissionRegistry;
import com.coachingcenter.api.auth.permission.PermissionScope;
import com.coachingcenter.api.auth.permission.Permissions;

@Component
public class ExamPermissionContributor implements PermissionContributor {

	@Override
	public void contribute(PermissionRegistry registry) {
		registry.grant(Role.MASTER_ADMIN, Permissions.EXAM_READ, PermissionScope.ANY);
		registry.grant(Role.MASTER_ADMIN, Permissions.EXAM_MANAGE, PermissionScope.ANY);
		registry.grant(Role.MASTER_ADMIN, Permissions.EXAM_PUBLISH, PermissionScope.ANY);
		registry.grant(Role.MASTER_ADMIN, Permissions.EXAM_PRICE, PermissionScope.ANY);
		registry.grant(Role.MASTER_ADMIN, Permissions.EXAM_DELETE, PermissionScope.ANY);
		registry.grant(Role.MASTER_ADMIN, Permissions.EXAM_EVALUATE, PermissionScope.ANY);
		registry.grant(Role.MASTER_ADMIN, Permissions.QUESTION_BANK_MANAGE, PermissionScope.ANY);
		registry.grant(Role.MASTER_ADMIN, Permissions.MODULE_COVER_MANAGE, PermissionScope.ANY);

		registry.grant(Role.SYSTEM_ADMIN, Permissions.EXAM_READ, PermissionScope.ANY);
		registry.grant(Role.SYSTEM_ADMIN, Permissions.EXAM_MANAGE, PermissionScope.ANY);
		registry.grant(Role.SYSTEM_ADMIN, Permissions.EXAM_EVALUATE, PermissionScope.ANY);
	}

}
