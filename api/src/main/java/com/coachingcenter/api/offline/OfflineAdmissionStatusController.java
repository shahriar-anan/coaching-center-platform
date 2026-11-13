package com.coachingcenter.api.offline;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public switch the website reads before it shows the offline admission pages. It carries the switch only, never a configuration value. */
@RestController
public class OfflineAdmissionStatusController {

	private final OfflineAdmissionSwitch admissionSwitch;

	public OfflineAdmissionStatusController(OfflineAdmissionSwitch admissionSwitch) {
		this.admissionSwitch = admissionSwitch;
	}

	/** Public under the {@code GET /api/v1/public/**} rule of the security config, so no sign-in is needed. */
	@GetMapping("/api/v1/public/offline-admission/status")
	public Status status() {
		return new Status(admissionSwitch.isEnabled());
	}

	public record Status(boolean enabled) {
	}

}
