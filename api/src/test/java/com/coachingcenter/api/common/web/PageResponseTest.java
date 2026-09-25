package com.coachingcenter.api.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PageResponseTest {

	@Test
	void envelopeUsesPageAndSize() {
		PageResponse<String> response = PageResponse.from(new PageImpl<>(List.of("a"), PageRequest.of(1, 20), 21));

		assertThat(response.content()).containsExactly("a");
		assertThat(response.page()).isEqualTo(1);
		assertThat(response.size()).isEqualTo(20);
		assertThat(response.totalElements()).isEqualTo(21);
		assertThat(response.totalPages()).isEqualTo(2);
	}

}
