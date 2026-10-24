package com.coachingcenter.api.common.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class StartupChecks {

	@Bean
	ApplicationRunner profileStartup(Environment environment) {
		return args -> ProfileStartup.check(environment);
	}

}
