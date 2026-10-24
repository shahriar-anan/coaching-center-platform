package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SslCommerzPropertiesTest {

	@Test
	void surroundingSpacesAreRemoved() {
		SslCommerzProperties properties = new SslCommerzProperties("  test-store  ", "  test-password  ",
				"  https://example.test  ", "  http://localhost:8080  ");

		assertThat(properties.storeId()).isEqualTo("test-store");
		assertThat(properties.storePassword()).isEqualTo("test-password");
		assertThat(properties.baseUrl()).isEqualTo("https://example.test");
		assertThat(properties.callbackBaseUrl()).isEqualTo("http://localhost:8080");
		assertThat(new SslCommerzProperties(null, null, null, null).storeId()).isNull();
	}

}
