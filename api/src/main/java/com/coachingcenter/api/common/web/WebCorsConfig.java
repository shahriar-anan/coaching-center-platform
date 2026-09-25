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
		registry.addMapping("/api/**")
			.allowedOrigins(origins.toArray(String[]::new))
			.allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
			.allowedHeaders("*")
			.exposedHeaders(RequestIds.HEADER)
			.allowCredentials(true);
	}

}
