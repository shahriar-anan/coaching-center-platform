package com.coachingcenter.api.files;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.files")
public record FileStorageProperties(String storageDir, long materialMaxBytes) {

	public static final long DEFAULT_MATERIAL_MAX_BYTES = 104857600L;

}
