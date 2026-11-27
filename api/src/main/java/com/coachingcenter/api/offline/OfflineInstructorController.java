package com.coachingcenter.api.offline;

import static com.coachingcenter.api.offline.OfflineSupport.require;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.offline.OfflineInstructorService.InstructorView;

/**
 * Offline instructors for the routine picker (plan section 6). Staff and the Master Admin list them (OFFLINE_BATCH_ROUTINE). The Master
 * Admin adds, edits and archives them as payees of the Offline instructors group, in the accounting API (ACCOUNTING); this route has
 * no writes. Instructors have no account, so no student route reaches these.
 */
@RestController
public class OfflineInstructorController {

	private final OfflineInstructorService instructors;

	private final PermissionChecker permissions;

	public OfflineInstructorController(OfflineInstructorService instructors, PermissionChecker permissions) {
		this.instructors = instructors;
		this.permissions = permissions;
	}

	@GetMapping("/api/v1/offline-instructors")
	public List<InstructorView> list(@RequestParam(required = false) String q,
			@RequestParam(required = false, defaultValue = "false") boolean includeArchived) {
		require(permissions, Permissions.OFFLINE_BATCH_ROUTINE);
		return instructors.list(q, includeArchived);
	}

}
