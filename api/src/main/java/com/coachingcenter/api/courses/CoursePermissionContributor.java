package com.coachingcenter.api.courses;

import org.springframework.stereotype.Component;

import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.auth.permission.PermissionContributor;
import com.coachingcenter.api.auth.permission.PermissionRegistry;
import com.coachingcenter.api.auth.permission.PermissionScope;
import com.coachingcenter.api.auth.permission.Permissions;

@Component
public class CoursePermissionContributor implements PermissionContributor {

	@Override
	public void contribute(PermissionRegistry registry) {
		registry.grant(Role.MASTER_ADMIN, Permissions.COURSE_READ, PermissionScope.ANY);
		registry.grant(Role.MASTER_ADMIN, Permissions.COURSE_MANAGE, PermissionScope.ANY);
		registry.grant(Role.MASTER_ADMIN, Permissions.COURSE_PRICE, PermissionScope.ANY);
		registry.grant(Role.MASTER_ADMIN, Permissions.COURSE_CLOSE, PermissionScope.ANY);
		registry.grant(Role.MASTER_ADMIN, Permissions.COURSE_DELETE, PermissionScope.ANY);

		registry.grant(Role.SYSTEM_ADMIN, Permissions.COURSE_READ, PermissionScope.ANY);
		registry.grant(Role.SYSTEM_ADMIN, Permissions.COURSE_MANAGE, PermissionScope.ANY);
	}

}
