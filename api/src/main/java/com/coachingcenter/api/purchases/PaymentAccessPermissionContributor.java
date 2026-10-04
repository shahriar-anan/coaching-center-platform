package com.coachingcenter.api.purchases;

import org.springframework.stereotype.Component;

import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.auth.permission.PermissionContributor;
import com.coachingcenter.api.auth.permission.PermissionRegistry;
import com.coachingcenter.api.auth.permission.PermissionScope;
import com.coachingcenter.api.auth.permission.Permissions;

@Component
public class PaymentAccessPermissionContributor implements PermissionContributor {

	@Override
	public void contribute(PermissionRegistry registry) {
		registry.grant(Role.MASTER_ADMIN, Permissions.PAYMENT_VERIFY, PermissionScope.ANY);
		registry.grant(Role.MASTER_ADMIN, Permissions.CASH_RECEIVE, PermissionScope.ANY);
		registry.grant(Role.MASTER_ADMIN, Permissions.ENROLLMENT_GRANT, PermissionScope.ANY);
		registry.grant(Role.MASTER_ADMIN, Permissions.ORDER_FULFILL, PermissionScope.ANY);

		registry.grant(Role.SYSTEM_ADMIN, Permissions.ENROLLMENT_GRANT, PermissionScope.ANY);
		registry.grant(Role.SYSTEM_ADMIN, Permissions.ORDER_FULFILL, PermissionScope.ANY);
	}

}
