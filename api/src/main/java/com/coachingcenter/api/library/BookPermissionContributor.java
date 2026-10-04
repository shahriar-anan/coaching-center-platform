package com.coachingcenter.api.library;

import org.springframework.stereotype.Component;

import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.auth.permission.PermissionContributor;
import com.coachingcenter.api.auth.permission.PermissionRegistry;
import com.coachingcenter.api.auth.permission.PermissionScope;
import com.coachingcenter.api.auth.permission.Permissions;

@Component
public class BookPermissionContributor implements PermissionContributor {

	@Override
	public void contribute(PermissionRegistry registry) {
		registry.grant(Role.MASTER_ADMIN, Permissions.BOOK_READ, PermissionScope.ANY);
		registry.grant(Role.MASTER_ADMIN, Permissions.BOOK_MANAGE, PermissionScope.ANY);
		registry.grant(Role.MASTER_ADMIN, Permissions.BOOK_PRICE, PermissionScope.ANY);
		registry.grant(Role.MASTER_ADMIN, Permissions.BOOK_DELETE, PermissionScope.ANY);

		registry.grant(Role.SYSTEM_ADMIN, Permissions.BOOK_READ, PermissionScope.ANY);
		registry.grant(Role.SYSTEM_ADMIN, Permissions.BOOK_MANAGE, PermissionScope.ANY);
	}

}
