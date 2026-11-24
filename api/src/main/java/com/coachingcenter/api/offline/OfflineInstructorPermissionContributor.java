package com.coachingcenter.api.offline;

import org.springframework.stereotype.Component;

import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.auth.permission.PermissionContributor;
import com.coachingcenter.api.auth.permission.PermissionRegistry;
import com.coachingcenter.api.auth.permission.PermissionScope;
import com.coachingcenter.api.auth.permission.Permissions;

/**
 * Who may pay offline instructors and see finance. Since 24 Nov (instructor-pay-simple-plan.md) the Master Admin alone adds instructors
 * and records, edits and voids their payments; System Admins keep the routines and classes only. The Master Admin alone sees the
 * finance pages (plan G3).
 */
@Component
public class OfflineInstructorPermissionContributor implements PermissionContributor {

	@Override
	public void contribute(PermissionRegistry registry) {
		registry.grant(Role.MASTER_ADMIN, Permissions.OFFLINE_INSTRUCTOR_PAY, PermissionScope.ANY);
		registry.grant(Role.MASTER_ADMIN, Permissions.FINANCE_READ, PermissionScope.ANY);
	}

}
