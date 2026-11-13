package com.coachingcenter.api.offline;

import org.springframework.stereotype.Component;

import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.auth.permission.PermissionContributor;
import com.coachingcenter.api.auth.permission.PermissionRegistry;
import com.coachingcenter.api.auth.permission.PermissionScope;
import com.coachingcenter.api.auth.permission.Permissions;

/**
 * Who may settle offline classes and see finance (plan side effect 3). Staff record a payment or a "not taken" (it waits for the Master
 * Admin). The Master Admin approves, rejects and voids, and alone sees the finance pages.
 */
@Component
public class OfflineClassPermissionContributor implements PermissionContributor {

	@Override
	public void contribute(PermissionRegistry registry) {
		registry.grant(Role.MASTER_ADMIN, Permissions.OFFLINE_CLASS_SETTLE, PermissionScope.ANY);
		registry.grant(Role.MASTER_ADMIN, Permissions.OFFLINE_CLASS_APPROVE, PermissionScope.ANY);
		registry.grant(Role.MASTER_ADMIN, Permissions.FINANCE_READ, PermissionScope.ANY);
		registry.grant(Role.SYSTEM_ADMIN, Permissions.OFFLINE_CLASS_SETTLE, PermissionScope.ANY);
	}

}
