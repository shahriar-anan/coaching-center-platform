package com.coachingcenter.api.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
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

	@Test
	void serviceFieldErrorsGoToFieldErrorsNotDetails() throws Exception {
		mockMvc.perform(post("/api/v1/probe/service"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors[0].field").value("price"))
			.andExpect(jsonPath("$.fieldErrors[0].message").value("A free exam has no price."))
			.andExpect(jsonPath("$.details").doesNotExist());
	}

	@Test
	void otherDetailsStayInDetails() throws Exception {
		mockMvc.perform(post("/api/v1/probe/conflict"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.details.id").value("other-exam"))
			.andExpect(jsonPath("$.fieldErrors").doesNotExist());
	}

	@RestController
	static class ProbeController {

		@PostMapping("/api/v1/probe")
		void create(@Valid @RequestBody ProbeRequest request) {
		}

		@PostMapping("/api/v1/probe/service")
		void service() {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.",
					List.of(new FieldErrorDetail("price", "A free exam has no price.")));
		}

		@PostMapping("/api/v1/probe/conflict")
		void conflict() {
			throw new ApiException(ErrorCode.EXAM_WINDOW_CONFLICT, HttpStatus.CONFLICT, "Clash.",
					java.util.Map.of("id", "other-exam"));
		}

	}

	record ProbeRequest(@NotBlank String name) {
	}

}
