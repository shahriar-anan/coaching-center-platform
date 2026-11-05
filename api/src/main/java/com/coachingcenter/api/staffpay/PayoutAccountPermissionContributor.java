package com.coachingcenter.api.staffpay;

import org.springframework.stereotype.Component;

import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.auth.permission.PermissionContributor;
import com.coachingcenter.api.auth.permission.PermissionRegistry;
import com.coachingcenter.api.auth.permission.PermissionScope;
import com.coachingcenter.api.auth.permission.Permissions;

/**
 * Own pay pages and own payout accounts (staff pay plan, section 4). The Master Admin is not granted it: he reads other
 * people's accounts through STAFF_PAY_MANAGE and cannot write any.
 */
@Component
public class PayoutAccountPermissionContributor implements PermissionContributor {

	@Override
	public void contribute(PermissionRegistry registry) {
		registry.grant(Role.SYSTEM_ADMIN, Permissions.STAFF_PAY_SELF, PermissionScope.ANY);
		registry.grant(Role.INSTRUCTOR, Permissions.STAFF_PAY_SELF, PermissionScope.ANY);
	}

}
