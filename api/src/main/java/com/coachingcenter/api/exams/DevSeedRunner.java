package com.coachingcenter.api.exams;

import java.time.Clock;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Runs the dev seed at startup when {@code SEED_DEV=true}. The seed itself refuses prod and any profile but dev. */
@Component
@Order(2)
public class DevSeedRunner implements ApplicationRunner {

	private final Environment environment;

	private final DevSeed seed;

	private final Clock clock;

	public DevSeedRunner(Environment environment, DevSeed seed, Clock clock) {
		this.environment = environment;
		this.seed = seed;
		this.clock = clock;
	}

	@Override
	public void run(ApplicationArguments args) {
		String enabled = environment.getProperty("SEED_DEV");
		if (enabled == null || !"true".equalsIgnoreCase(enabled.trim())) {
			return;
		}
		seed.seed(environment.getActiveProfiles(), environment::getProperty, clock.instant());
	}

}
