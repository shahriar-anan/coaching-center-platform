package com.coachingcenter.api.files;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.files")
public record FileStorageProperties(String storageDir, long materialMaxBytes, long examImageMaxBytes) {

	public static final long DEFAULT_MATERIAL_MAX_BYTES = 104857600L;

	public static final long DEFAULT_EXAM_IMAGE_MAX_BYTES = 15728640L;

	public static final long EXAM_IMAGE_MAX_BYTES_CEILING = 20971520L;

}
