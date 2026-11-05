package com.coachingcenter.api.notices;

import org.springframework.stereotype.Component;

import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.auth.permission.PermissionContributor;
import com.coachingcenter.api.auth.permission.PermissionRegistry;
import com.coachingcenter.api.auth.permission.PermissionScope;
import com.coachingcenter.api.auth.permission.Permissions;

/** The public notice board: System Admins and the Master Admin edit drafts; only the Master Admin publishes. */
@Component
public class SiteNoticePermissionContributor implements PermissionContributor {

	@Override
	public void contribute(PermissionRegistry registry) {
		registry.grant(Role.MASTER_ADMIN, Permissions.SITE_NOTICE_EDIT, PermissionScope.ANY);
		registry.grant(Role.SYSTEM_ADMIN, Permissions.SITE_NOTICE_EDIT, PermissionScope.ANY);
		registry.grant(Role.MASTER_ADMIN, Permissions.SITE_NOTICE_PUBLISH, PermissionScope.ANY);
	}

}
