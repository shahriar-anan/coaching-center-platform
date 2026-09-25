package com.coachingcenter.api.auth.permission;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.coachingcenter.api.auth.Role;

@Component
public class PermissionCatalog implements PermissionRegistry {

	private final Map<Role, Map<String, PermissionScope>> grants = new EnumMap<>(Role.class);

	public PermissionCatalog(List<PermissionContributor> contributors) {
		for (Role role : Role.values()) {
			grants.put(role, new java.util.HashMap<>());
		}
		contributors.forEach(contributor -> contributor.contribute(this));
	}

	@Override
	public void grant(Role role, String permission, PermissionScope scope) {
		if (permission.endsWith("_DELETE") && role != Role.MASTER_ADMIN) {
			throw new IllegalArgumentException("Only MASTER_ADMIN may receive a delete permission.");
		}
		grants.get(role).put(permission, scope);
	}

	public PermissionScope scope(Role role, String permission) {
		return grants.get(role).getOrDefault(permission, PermissionScope.NONE);
	}

	public boolean allows(Role role, String permission, boolean ownRecord) {
		PermissionScope scope = scope(role, permission);
		return scope == PermissionScope.ANY || (scope == PermissionScope.OWN && ownRecord);
	}

}
