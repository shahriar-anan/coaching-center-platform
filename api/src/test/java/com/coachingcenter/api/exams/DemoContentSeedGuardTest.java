package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DemoContentSeedGuardTest {

	@Test
	void productionProfileIsRefusedAndSecretsStayOutOfTheMessage() {
		assertThatThrownBy(() -> DemoContentSeed.refuseProduction(new String[] { "prod" }))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("The demo seed cannot run on the prod profile.");
		assertThatThrownBy(() -> DemoContentSeed.refuseProduction(new String[] { "staging", "prod" }))
			.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> DemoContentSeed.refuseProduction(new String[] { "dev", "prod" }))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("The demo seed cannot run on the prod profile.");
		assertThatCode(() -> DemoContentSeed.refuseProduction(new String[] { "staging" })).doesNotThrowAnyException();
		assertThatCode(() -> DemoContentSeed.refuseProduction(new String[] { "dev" })).doesNotThrowAnyException();

		assertThatThrownBy(() -> DemoContentSeed.required("SEED_SYSTEM_ADMIN_PASSWORD", " "))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("SEED_SYSTEM_ADMIN_PASSWORD is required.");
		assertThatThrownBy(() -> DemoContentSeed.requirePassword("SEED_TEST_STUDENT_PASSWORD", "short"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("SEED_TEST_STUDENT_PASSWORD must be 8 to 72 characters.")
			.hasMessageNotContaining("short");
		assertThat(DemoContentSeed.required("SEED_SYSTEM_ADMIN_EMAIL", " Admin@Example.com ")).isEqualTo("Admin@Example.com");
	}

}
