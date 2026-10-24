package com.coachingcenter.api.exams;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(SslCommerzProperties.class)
public class SslCommerzConfig {

	@Bean
	@Conditional(SslCommerzConfigured.class)
	LiveExamPaymentGateway sslCommerzGateway(SslCommerzProperties properties, ObjectMapper json) {
		return new SslCommerzGateway(properties, json);
	}

	@Bean
	ApplicationRunner sslCommerzProfileGuard(Environment environment) {
		return args -> SslCommerzProfileGuard.check(environment);
	}

}
