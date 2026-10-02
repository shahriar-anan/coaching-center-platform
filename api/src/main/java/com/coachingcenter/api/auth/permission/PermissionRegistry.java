package com.coachingcenter.api.auth.permission;

import com.coachingcenter.api.auth.Role;

public interface PermissionRegistry {

	void grant(Role role, String permission, PermissionScope scope);

}
