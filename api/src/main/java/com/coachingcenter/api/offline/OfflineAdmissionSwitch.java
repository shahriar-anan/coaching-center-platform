package com.coachingcenter.api.offline;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The offline admission feature switch ({@code app.offline-admission.enabled}, env {@code OFFLINE_ADMISSION_ENABLED}). Off by default:
 * while it is off the routes answer 404 ({@link OfflineAdmissionSwitchFilter}), a student create ignores {@code offlineAdmission}, and
 * no admission is ever pending.
 */
@Component
public class OfflineAdmissionSwitch {

	private final boolean enabled;

	public OfflineAdmissionSwitch(@Value("${app.offline-admission.enabled:false}") boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isEnabled() {
		return enabled;
	}

}
