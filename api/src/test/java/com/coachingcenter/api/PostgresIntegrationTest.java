package com.coachingcenter.api;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class PostgresIntegrationTest {

	private static final String FILE_STORAGE_DIR = createStorageDir();

	private static String createStorageDir() {
		try {
			return java.nio.file.Files.createTempDirectory("coaching-api-files-").toAbsolutePath().toString();
		}
		catch (java.io.IOException exception) {
			throw new ExceptionInInitializerError(exception);
		}
	}

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
		registry.add("FILE_STORAGE_DIR", () -> FILE_STORAGE_DIR);
		// Tests call the deadline and reminder jobs directly; a background tick would read another context's clock.
		registry.add("app.exam-jobs.scheduled", () -> "false");
		registry.add("MATERIAL_MAX_BYTES", () -> "1024");
		registry.add("PAYMENT_BKASH_NUMBER", () -> "01700000001");
		registry.add("PAYMENT_NAGAD_NUMBER", () -> "01800000001");
		registry.add("SSLCZ_STORE_ID", () -> "");
		registry.add("SSLCZ_STORE_PASSWORD", () -> "");
		registry.add("SSLCZ_BASE_URL", () -> "");
		registry.add("SSLCZ_CALLBACK_BASE_URL", () -> "");
		registry.add("app.seed.enabled", () -> "false");
		registry.add("spring.datasource.hikari.maximum-pool-size", () -> "2");
		registry.add("spring.datasource.hikari.minimum-idle", () -> "0");
	}

}
