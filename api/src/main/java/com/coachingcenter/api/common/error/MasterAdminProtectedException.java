package com.coachingcenter.api.common.error;

public class MasterAdminProtectedException extends RuntimeException {

	public MasterAdminProtectedException() {
		super("A Master Admin account cannot be created, changed, deactivated, or deleted.");
	}

}
