package com.coachingcenter.api;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

abstract class PostgresIntegrationTest {

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
	}

}
