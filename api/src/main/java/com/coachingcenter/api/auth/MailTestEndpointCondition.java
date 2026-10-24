package com.coachingcenter.api.auth;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Remove or leave disabled after go-live.
 */
public final class MailTestEndpointCondition implements Condition {

	@Override
	public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
		return enabled(context.getEnvironment());
	}

	static boolean enabled(Environment environment) {
		if (!environment.acceptsProfiles(Profiles.of("staging", "prod"))) {
			return false;
		}
		return environment.getProperty("app.mail.test-enabled", Boolean.class, false);
	}

}
