package com.coachingcenter.api.common.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class ProfileStartupTest {

	@Test
	void stagingAndProdRequireAWebOrigin() {
		assertThatThrownBy(() -> ProfileStartup.requireWebOrigin(List.of("staging"), " "))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("WEB_ORIGIN is required on the staging and prod profiles.");
		assertThatThrownBy(() -> ProfileStartup.requireWebOrigin(List.of("prod"), null))
			.isInstanceOf(IllegalStateException.class);
		assertThatCode(() -> ProfileStartup.requireWebOrigin(List.of("staging"), "https://exams.example.com"))
			.doesNotThrowAnyException();
		assertThatCode(() -> ProfileStartup.requireWebOrigin(List.of("dev"), null)).doesNotThrowAnyException();
	}

	@Test
	void prodRefusesTheStagingSeed() {
		assertThatThrownBy(() -> ProfileStartup.refuseSeedOnProd(List.of("prod"), "true"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("SEED_ENABLED cannot be true on the prod profile.");
		assertThatThrownBy(() -> ProfileStartup.refuseSeedOnProd(List.of("staging", "prod"), " TRUE "))
			.isInstanceOf(IllegalStateException.class);
		assertThatCode(() -> ProfileStartup.refuseSeedOnProd(List.of("prod"), "false")).doesNotThrowAnyException();
		assertThatCode(() -> ProfileStartup.refuseSeedOnProd(List.of("staging"), "true")).doesNotThrowAnyException();
		assertThatCode(() -> ProfileStartup.refuseSeedOnProd(List.of("prod"), null)).doesNotThrowAnyException();
	}

}
