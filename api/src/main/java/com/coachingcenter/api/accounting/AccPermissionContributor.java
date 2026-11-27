package com.coachingcenter.api.accounting;

import org.springframework.stereotype.Component;

import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.auth.permission.PermissionContributor;
import com.coachingcenter.api.auth.permission.PermissionRegistry;
import com.coachingcenter.api.auth.permission.PermissionScope;
import com.coachingcenter.api.auth.permission.Permissions;

/**
 * The accounting section is the Master Admin's alone (plan A15, 4.9). System Admins get nothing here. This does not touch the offline
 * instructor contributor, which also grants FINANCE_READ.
 */
@Component
public class AccPermissionContributor implements PermissionContributor {

	@Override
	public void contribute(PermissionRegistry registry) {
		registry.grant(Role.MASTER_ADMIN, Permissions.ACCOUNTING, PermissionScope.ANY);
	}

}
