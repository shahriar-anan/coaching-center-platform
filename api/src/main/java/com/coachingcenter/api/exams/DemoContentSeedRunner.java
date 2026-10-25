package com.coachingcenter.api.exams;

import java.time.Clock;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Runs the demo seed at startup when {@code SEED_DEMO=true}. The seed itself refuses prod and any profile but staging or dev. */
@Component
public class DemoContentSeedRunner implements ApplicationRunner {

	private final Environment environment;

	private final DemoContentSeed seed;

	private final Clock clock;

	public DemoContentSeedRunner(Environment environment, DemoContentSeed seed, Clock clock) {
		this.environment = environment;
		this.seed = seed;
		this.clock = clock;
	}

	@Override
	public void run(ApplicationArguments args) {
		String enabled = environment.getProperty("SEED_DEMO");
		if (enabled == null || !"true".equalsIgnoreCase(enabled.trim())) {
			return;
		}
		seed.seed(environment.getActiveProfiles(), environment::getProperty, clock.instant());
	}

}
