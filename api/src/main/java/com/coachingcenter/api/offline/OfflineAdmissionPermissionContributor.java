package com.coachingcenter.api.offline;

import org.springframework.stereotype.Component;

import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.auth.permission.PermissionContributor;
import com.coachingcenter.api.auth.permission.PermissionRegistry;
import com.coachingcenter.api.auth.permission.PermissionScope;
import com.coachingcenter.api.auth.permission.Permissions;

/**
 * Who may do what with offline admissions (plan section 4.6). The Master Admin has everything. The System Admin (staff) can
 * review, set up, print, record a payment and see the batches, but cannot onboard, approve a payment or manage batches.
 * Students get no permission here: they reach their own admission by ownership in the service.
 */
@Component
public class OfflineAdmissionPermissionContributor implements PermissionContributor {

	@Override
	public void contribute(PermissionRegistry registry) {
		for (String permission : new String[] {
				Permissions.OFFLINE_ADMISSION_READ,
				Permissions.OFFLINE_ADMISSION_REVIEW,
				Permissions.OFFLINE_ADMISSION_ONBOARD,
				Permissions.OFFLINE_BATCH_MANAGE,
				Permissions.OFFLINE_BATCH_ROUTINE,
				Permissions.OFFLINE_FEE_RECORD,
				Permissions.OFFLINE_FEE_APPROVE }) {
			registry.grant(Role.MASTER_ADMIN, permission, PermissionScope.ANY);
		}
		registry.grant(Role.SYSTEM_ADMIN, Permissions.OFFLINE_ADMISSION_READ, PermissionScope.ANY);
		registry.grant(Role.SYSTEM_ADMIN, Permissions.OFFLINE_ADMISSION_REVIEW, PermissionScope.ANY);
		registry.grant(Role.SYSTEM_ADMIN, Permissions.OFFLINE_BATCH_ROUTINE, PermissionScope.ANY);
		registry.grant(Role.SYSTEM_ADMIN, Permissions.OFFLINE_FEE_RECORD, PermissionScope.ANY);
	}
}
