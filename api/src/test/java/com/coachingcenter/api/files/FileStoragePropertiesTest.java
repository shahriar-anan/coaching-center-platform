package com.coachingcenter.api.files;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FileStoragePropertiesTest {

	@Test
	void defaultMaterialMaxBytesIs100Mb() {
		assertThat(FileStorageProperties.DEFAULT_MATERIAL_MAX_BYTES).isEqualTo(104857600L);
	}

	@Test
	void defaultExamImageMaxBytesIs15Mb() {
		assertThat(FileStorageProperties.DEFAULT_EXAM_IMAGE_MAX_BYTES).isEqualTo(15728640L);
	}

}
