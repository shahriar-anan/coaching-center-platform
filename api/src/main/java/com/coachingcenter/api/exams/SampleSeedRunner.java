package com.coachingcenter.api.exams;

import java.time.Clock;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Runs the sample seed at startup when {@code SEED_SAMPLE=true}. The seed itself refuses prod and any profile but staging or dev,
 * and does nothing when its data is already there. The Master Admin must exist first (MASTER_ADMIN_*).
 */
@Component
@Order(2)
public class SampleSeedRunner implements ApplicationRunner {

	private final Environment environment;

	private final SampleSeed seed;

	private final Clock clock;

	public SampleSeedRunner(Environment environment, SampleSeed seed, Clock clock) {
		this.environment = environment;
		this.seed = seed;
		this.clock = clock;
	}

	@Override
	public void run(ApplicationArguments args) {
		String enabled = environment.getProperty("SEED_SAMPLE");
		if (enabled == null || !"true".equalsIgnoreCase(enabled.trim())) {
			return;
		}
		seed.seed(environment.getActiveProfiles(), environment::getProperty, clock.instant());
	}

}
