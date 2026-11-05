package com.coachingcenter.api.staffnotifications;

import org.springframework.stereotype.Component;

import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.auth.permission.PermissionContributor;
import com.coachingcenter.api.auth.permission.PermissionRegistry;
import com.coachingcenter.api.auth.permission.PermissionScope;
import com.coachingcenter.api.auth.permission.Permissions;

/** The staff notification centre: every staff role reads its own notifications. Students are not granted it. */
@Component
public class StaffNotificationPermissionContributor implements PermissionContributor {

	@Override
	public void contribute(PermissionRegistry registry) {
		registry.grant(Role.MASTER_ADMIN, Permissions.STAFF_NOTICE_READ, PermissionScope.ANY);
		registry.grant(Role.SYSTEM_ADMIN, Permissions.STAFF_NOTICE_READ, PermissionScope.ANY);
		registry.grant(Role.INSTRUCTOR, Permissions.STAFF_NOTICE_READ, PermissionScope.ANY);
	}

}
