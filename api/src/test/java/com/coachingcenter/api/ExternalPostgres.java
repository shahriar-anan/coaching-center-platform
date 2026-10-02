package com.coachingcenter.api;

final class ExternalPostgres {

	private ExternalPostgres() {
	}

	static boolean isConfigured() {
		boolean url = present("TEST_DB_URL");
		boolean user = present("TEST_DB_USER");
		boolean password = present("TEST_DB_PASSWORD");
		if (url || user || password) {
			if (!(url && user && password)) {
				throw new IllegalStateException(
						"Set TEST_DB_URL, TEST_DB_USER, and TEST_DB_PASSWORD together for local integration tests.");
			}
			return true;
		}
		return false;
	}

	static String url() {
		return System.getenv("TEST_DB_URL");
	}

	static String user() {
		return System.getenv("TEST_DB_USER");
	}

	static String password() {
		return System.getenv("TEST_DB_PASSWORD");
	}

	private static boolean present(String name) {
		String value = System.getenv(name);
		return value != null && !value.isBlank();
	}

}
