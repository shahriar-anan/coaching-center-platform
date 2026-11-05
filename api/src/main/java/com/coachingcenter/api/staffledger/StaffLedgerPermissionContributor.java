package com.coachingcenter.api.staffledger;

import org.springframework.stereotype.Component;

import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.auth.permission.PermissionContributor;
import com.coachingcenter.api.auth.permission.PermissionRegistry;
import com.coachingcenter.api.auth.permission.PermissionScope;
import com.coachingcenter.api.auth.permission.Permissions;

/**
 * The staff pay ledger (staff pay plan, section 4). The Master Admin manages everyone's pay; a System Admin and an instructor
 * see their own pay. The same self permission is also granted by the payout accounts contributor; granting it twice is harmless.
 */
@Component
public class StaffLedgerPermissionContributor implements PermissionContributor {

	@Override
	public void contribute(PermissionRegistry registry) {
		registry.grant(Role.MASTER_ADMIN, Permissions.STAFF_PAY_MANAGE, PermissionScope.ANY);
		registry.grant(Role.SYSTEM_ADMIN, Permissions.STAFF_PAY_SELF, PermissionScope.ANY);
		registry.grant(Role.INSTRUCTOR, Permissions.STAFF_PAY_SELF, PermissionScope.ANY);
	}

}
