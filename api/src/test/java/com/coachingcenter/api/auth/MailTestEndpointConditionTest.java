package com.coachingcenter.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.MapPropertySource;

class MailTestEndpointConditionTest {

	@Test
	void everyProfileStaysDisabledUntilTheFlagIsTrue() {
		assertThat(enabled("staging", null)).isFalse();
		assertThat(enabled("staging", "false")).isFalse();
		assertThat(enabled("prod", "false")).isFalse();
		assertThat(enabled("dev", "true")).isFalse();
		assertThat(enabled("staging", "true")).isTrue();
		assertThat(enabled("prod", "true")).isTrue();
	}

	private static boolean enabled(String profile, String flag) {
		StandardEnvironment environment = new StandardEnvironment();
		environment.setActiveProfiles(profile);
		if (flag != null) {
			environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of("app.mail.test-enabled", flag)));
		}
		return MailTestEndpointCondition.enabled(environment);
	}

}
