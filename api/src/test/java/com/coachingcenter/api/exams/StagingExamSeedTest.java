package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StagingExamSeedTest {

	@Test
	void productionProfileIsRefusedAndSecretsStayOutOfTheMessage() {
		assertThatThrownBy(() -> StagingExamSeed.refuseProduction(new String[] { "prod" }))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("The staging seed cannot run on the prod profile.");
		assertThatThrownBy(() -> StagingExamSeed.refuseProduction(new String[] { "staging", "prod" }))
			.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> StagingExamSeed.refuseProduction(new String[] { "dev", "prod" }))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("The staging seed cannot run on the prod profile.");
		assertThatCode(() -> StagingExamSeed.refuseProduction(new String[] { "staging" })).doesNotThrowAnyException();
		assertThatCode(() -> StagingExamSeed.refuseProduction(new String[] { "dev" })).doesNotThrowAnyException();

		assertThatThrownBy(() -> StagingExamSeed.required("SEED_ADMIN_PASSWORD", " "))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("SEED_ADMIN_PASSWORD is required.");
		assertThatThrownBy(() -> StagingExamSeed.requirePassword("SEED_STUDENT_PASSWORD", "short"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("SEED_STUDENT_PASSWORD must be 8 to 72 characters.")
			.hasMessageNotContaining("short");
		assertThat(StagingExamSeed.required("SEED_ADMIN_EMAIL", " Admin@Example.com ")).isEqualTo("Admin@Example.com");
	}

}
