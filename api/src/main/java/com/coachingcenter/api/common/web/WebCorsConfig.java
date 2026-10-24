package com.coachingcenter.api.common.web;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.coachingcenter.api.common.config.AppProperties;

@Configuration
public class WebCorsConfig implements WebMvcConfigurer {

	private final AppProperties appProperties;

	public WebCorsConfig(AppProperties appProperties) {
		this.appProperties = appProperties;
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		List<String> origins = appProperties.cors().allowedOrigins();
		// A browser return is a navigation from the payment host, and an IPN is a server post.
		// The website allowlist must not reject either. These patterns are registered first.
		registry.addMapping("/api/v1/live-exam-orders/*/return")
			.allowedOriginPatterns("*")
			.allowedMethods("GET", "POST")
			.allowCredentials(false);
		registry.addMapping("/api/v1/live-exam-orders/*/ipn")
			.allowedOriginPatterns("*")
			.allowedMethods("POST")
			.allowCredentials(false);
		registry.addMapping("/api/**")
			.allowedOrigins(origins.toArray(String[]::new))
			.allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
			.allowedHeaders("*")
			.exposedHeaders(RequestIds.HEADER)
			.allowCredentials(true);
	}

}
