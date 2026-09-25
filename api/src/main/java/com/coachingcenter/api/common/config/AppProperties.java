package com.coachingcenter.api.common.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Email email, Cors cors) {

	public record Jwt(String secret) {
	}

	public record Email(String from, String host, int port, String username, String password) {
	}

	public record Cors(List<String> allowedOrigins) {
	}

}
