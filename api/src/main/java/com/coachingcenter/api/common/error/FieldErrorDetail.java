package com.coachingcenter.api.common.error;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A field error. {@code code} and {@code params} are set only where the web translates the message (the result sheet's refusals, F15);
 * every other field error is written as {@code field} and {@code message} alone.
 */
public record FieldErrorDetail(String field, String message, @JsonInclude(JsonInclude.Include.NON_NULL) String code,
		@JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> params) {

	public FieldErrorDetail(String field, String message) {
		this(field, message, null, null);
	}

}
