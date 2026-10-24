package com.coachingcenter.api.exams;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.sslcommerz")
public record SslCommerzProperties(String storeId, String storePassword, String baseUrl, String callbackBaseUrl) {

	public SslCommerzProperties {
		storeId = trim(storeId);
		storePassword = trim(storePassword);
		baseUrl = trim(baseUrl);
		callbackBaseUrl = trim(callbackBaseUrl);
	}

	private static String trim(String value) {
		return value == null ? null : value.trim();
	}

}
