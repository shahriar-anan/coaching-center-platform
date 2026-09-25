package com.coachingcenter.api.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.coachingcenter.api.common.web.RequestIdFilter;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class ApiExceptionHandlerTest {

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
			.setControllerAdvice(new ApiExceptionHandler())
			.addFilters(new RequestIdFilter())
			.build();
	}

	@Test
	void validationFailureUsesOneCodeAndFieldErrors() throws Exception {
		mockMvc.perform(post("/api/v1/probe").contentType(MediaType.APPLICATION_JSON).content("{}")
			.header("X-Request-Id", "validation-check-1"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.message").value("Request validation failed."))
			.andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
			.andExpect(jsonPath("$.timestamp").exists())
			.andExpect(jsonPath("$.requestId").value("validation-check-1"));
	}

	@RestController
	static class ProbeController {

		@PostMapping("/api/v1/probe")
		void create(@Valid @RequestBody ProbeRequest request) {
		}

	}

	record ProbeRequest(@NotBlank String name) {
	}

}
