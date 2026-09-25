package com.coachingcenter.api.auth.permission;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component("permissions")
public class PermissionChecker {

	private final PermissionCatalog catalog;

	public PermissionChecker(PermissionCatalog catalog) {
		this.catalog = catalog;
	}

	public boolean has(String permission) {
		return has(permission, false);
	}

	public boolean has(String permission, boolean ownRecord) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof CurrentActor actor)) {
			return false;
		}
		return catalog.allows(actor.role(), permission, ownRecord);
	}

}
