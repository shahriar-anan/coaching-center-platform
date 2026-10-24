package com.coachingcenter.api.exams;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

class SslCommerzConfigured implements Condition {

	@Override
	public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
		Environment environment = context.getEnvironment();
		return present(environment, "SSLCZ_STORE_ID") && present(environment, "SSLCZ_STORE_PASSWORD")
				&& present(environment, "SSLCZ_BASE_URL") && present(environment, "SSLCZ_CALLBACK_BASE_URL");
	}

	private static boolean present(Environment environment, String key) {
		String value = environment.getProperty(key);
		return value != null && !value.isBlank();
	}

}
