package com.coachingcenter.api.exams;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

import tools.jackson.databind.JsonNode;

/**
 * A question as an editor sends it, read and checked once so that the same rules apply whether the edit is written to the
 * question rows (a draft) or to a staged revision (a published exam).
 *
 * <p>
 * {@code multipleAnswer} is null when the body did not say; the question then keeps its current setting. A question with
 * one answer has exactly one correct option; one with several answers has at least one (C14, R8).
 */
record QuestionInput(String stem, int marks, String explanation, boolean explanationSent, Integer position,
		Boolean multipleAnswer, List<OptionInput> options) {

	record OptionInput(String text, boolean correct) {
	}

	/** Reads and checks a create or update body. The answer key is checked once the final {@code multipleAnswer} is known. */
	static QuestionInput parse(JsonNode body) {
		String stem = text(body, "stem");
		JsonNode marksNode = body.get("marks");
		if (marksNode == null || !marksNode.isIntegralNumber() || marksNode.intValue() <= 0) {
			throw invalid();
		}
		String explanation = null;
		boolean explanationSent = body.has("explanation");
		if (explanationSent && !body.get("explanation").isNull() && !body.get("explanation").asText().isBlank()) {
			explanation = body.get("explanation").asText().trim();
		}
		Integer position = body.has("position") && !body.get("position").isNull() ? body.get("position").asInt() : null;
		Boolean multiple = null;
		if (body.has("multipleAnswer") && !body.get("multipleAnswer").isNull()) {
			if (!body.get("multipleAnswer").isBoolean()) {
				throw invalid();
			}
			multiple = body.get("multipleAnswer").booleanValue();
		}
		JsonNode node = body.get("options");
		if (node == null || !node.isArray() || node.size() < 2) {
			throw invalid();
		}
		List<OptionInput> options = new ArrayList<>();
		for (JsonNode option : node) {
			String optionText = option.has("text") ? option.get("text").asText().trim() : "";
			if (optionText.isBlank()) {
				throw invalid();
			}
			options.add(new OptionInput(optionText, option.has("correct") && option.get("correct").asBoolean()));
		}
		return new QuestionInput(stem, marksNode.intValue(), explanation, explanationSent, position, multiple, options);
	}

	/** The setting this question takes: the body's, else the current one, else a single answer. */
	boolean multiple(boolean current) {
		return multipleAnswer != null ? multipleAnswer : current;
	}

	/** Exactly one correct option for a single-answer question, at least one for a multi-answer one. */
	void requireKey(boolean multiple) {
		long correct = options.stream().filter(OptionInput::correct).count();
		if (multiple ? correct < 1 : correct != 1) {
			throw invalid();
		}
	}

	private static String text(JsonNode body, String field) {
		JsonNode node = body.get(field);
		if (node == null || node.isNull() || node.asText().isBlank()) {
			throw invalid();
		}
		return node.asText().trim();
	}

	private static ApiException invalid() {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
	}

}
