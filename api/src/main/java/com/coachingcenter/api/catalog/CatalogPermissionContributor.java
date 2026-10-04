package com.coachingcenter.api.catalog;

import org.springframework.stereotype.Component;

import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.auth.permission.PermissionContributor;
import com.coachingcenter.api.auth.permission.PermissionRegistry;
import com.coachingcenter.api.auth.permission.PermissionScope;
import com.coachingcenter.api.auth.permission.Permissions;

@Component
public class CatalogPermissionContributor implements PermissionContributor {

	@Override
	public void contribute(PermissionRegistry registry) {
		registry.grant(Role.STUDENT, Permissions.CATALOG_BROWSE, PermissionScope.ANY);
		registry.grant(Role.STUDENT, Permissions.CONTENT_READ, PermissionScope.OWN);
	}

}
