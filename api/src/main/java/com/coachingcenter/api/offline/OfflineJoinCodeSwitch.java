package com.coachingcenter.api.offline;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Whether onboarding a student who has an account sends a join code ({@code app.offline-admission.join-code}, env
 * {@code OFFLINE_JOIN_CODE}). Off by default since 24 Nov 2026 (client): the Master Admin's Onboard admits the student at once, as it does
 * a desk admission. Join codes already sent keep working; onboarding again admits the student and cancels the code. On: the join code
 * goes out and the student joins with it (plan B, C).
 */
@Component
public class OfflineJoinCodeSwitch {

	private final boolean enabled;

	public OfflineJoinCodeSwitch(@Value("${app.offline-admission.join-code:false}") boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isEnabled() {
		return enabled;
	}

}
