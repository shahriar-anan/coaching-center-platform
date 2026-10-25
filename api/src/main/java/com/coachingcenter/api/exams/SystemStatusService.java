package com.coachingcenter.api.exams;

import java.util.ArrayList;
import java.util.List;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * Configuration checks for the Master Admin's status page. Each check has a name, a yes or no result, and a plain
 * sentence. No value is ever returned, so no secret can leak through this page.
 */
@Service
public class SystemStatusService {

	public record Check(String name, boolean ok, String note) {
	}

	private final Environment environment;

	public SystemStatusService(Environment environment) {
		this.environment = environment;
	}

	public List<Check> checks() {
		List<Check> checks = new ArrayList<>();
		checks.add(new Check("checkout.configured", sslcommerzConfigured(), "SSLCommerz store and callback values are set."));
		checks.add(new Check("checkout.profile", profileMatches(), "The SSLCommerz host matches the active profile."));
		checks.add(new Check("mail.configured", present("app.email.host"), "A mail host is set."));
		checks.add(new Check("google.configured", present("app.google.client-id"), "The Google sign-in client is set."));
		checks.add(new Check("mail.hour-before.off", !flag("app.exam-mail.hour-before.enabled"),
				"The one-hour-before exam mail is off."));
		checks.add(new Check("auth.dev-codes.off", !flag("app.auth.dev-expose-email-codes"),
				"Email codes are not shown in responses."));
		checks.add(new Check("seed.off", !flag("app.seed.enabled"), "The demo seed is off. It must be off in production."));
		return checks;
	}

	private boolean sslcommerzConfigured() {
		return present("app.sslcommerz.store-id") && present("app.sslcommerz.store-password")
				&& present("app.sslcommerz.base-url") && present("app.sslcommerz.callback-base-url");
	}

	private boolean profileMatches() {
		try {
			SslCommerzProfileGuard.check(environment);
			return true;
		}
		catch (IllegalStateException exception) {
			return false;
		}
	}

	private boolean present(String key) {
		String value = environment.getProperty(key);
		return value != null && !value.isBlank();
	}

	private boolean flag(String key) {
		return environment.getProperty(key, Boolean.class, false);
	}

}
