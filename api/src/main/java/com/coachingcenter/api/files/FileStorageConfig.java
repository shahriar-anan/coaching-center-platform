package com.coachingcenter.api.files;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FileStorageProperties.class)
public class FileStorageConfig {

	private final FileStorageProperties properties;

	public FileStorageConfig(FileStorageProperties properties) {
		this.properties = properties;
		if (properties.examImageMaxBytes() >= FileStorageProperties.EXAM_IMAGE_MAX_BYTES_CEILING) {
			throw new IllegalStateException("EXAM_IMAGE_MAX_BYTES must be below 20 MiB.");
		}
	}

}
