package com.coachingcenter.api.auth.permission;

import org.springframework.stereotype.Component;

import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.common.error.MasterAdminProtectedException;

@Component
public class MasterAdminGuard {

	public void rejectIfMasterAdmin(Role targetRole) {
		if (targetRole == Role.MASTER_ADMIN) {
			throw new MasterAdminProtectedException();
		}
	}

}
