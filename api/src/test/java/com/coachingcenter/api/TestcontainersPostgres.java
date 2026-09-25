package com.coachingcenter.api;

import org.testcontainers.containers.PostgreSQLContainer;

final class TestcontainersPostgres {

	private static PostgreSQLContainer<?> container;

	private TestcontainersPostgres() {
	}

	static PostgreSQLContainer<?> start() {
		if (container == null) {
			container = new PostgreSQLContainer<>("postgres:16-alpine");
			container.start();
		}
		return container;
	}

}
