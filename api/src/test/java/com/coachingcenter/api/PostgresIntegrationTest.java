package com.coachingcenter.api;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class PostgresIntegrationTest {

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		if (ExternalPostgres.isConfigured()) {
			registry.add("DB_URL", ExternalPostgres::url);
			registry.add("DB_USERNAME", ExternalPostgres::user);
			registry.add("DB_PASSWORD", ExternalPostgres::password);
		}
		else {
			var postgres = TestcontainersPostgres.start();
			registry.add("DB_URL", postgres::getJdbcUrl);
			registry.add("DB_USERNAME", postgres::getUsername);
			registry.add("DB_PASSWORD", postgres::getPassword);
		}
		registry.add("JWT_SECRET", () -> "test-only-jwt-secret-not-a-real-credential");
		registry.add("WEB_ORIGIN", () -> "http://localhost:3000");
		registry.add("app.auth.lockout.max-failures", () -> "3");
		registry.add("app.auth.code-max-attempts", () -> "3");
		registry.add("MASTER_ADMIN_PHONE", () -> "+8801700000001");
		registry.add("MASTER_ADMIN_EMAIL", () -> "master-bootstrap@example.com");
		registry.add("MASTER_ADMIN_NAME", () -> "Master Admin");
		registry.add("MASTER_ADMIN_PASSWORD", () -> "bootstrap-password-1");
	}

}
