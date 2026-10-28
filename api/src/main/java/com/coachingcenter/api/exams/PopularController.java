package com.coachingcenter.api.exams;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

import tools.jackson.databind.JsonNode;

@RestController
public class PopularController {

	private final PopularService popular;

	private final PermissionChecker permissions;

	public PopularController(PopularService popular, PermissionChecker permissions) {
		this.popular = popular;
		this.permissions = permissions;
	}

	/** Body {@code {"popular": true|false}}. The Master Admin only. */
	@PutMapping("/api/v1/live-exams/{id}/popular")
	public PopularView exam(@PathVariable UUID id, @RequestBody JsonNode body) {
		require();
		boolean value = readPopular(body);
		popular.setExam(id, value);
		return new PopularView(id, value);
	}

	@PutMapping("/api/v1/live-exam-modules/{id}/popular")
	public PopularView module(@PathVariable UUID id, @RequestBody JsonNode body) {
		require();
		boolean value = readPopular(body);
		popular.setModule(id, value);
		return new PopularView(id, value);
	}

	private void require() {
		if (!permissions.has(Permissions.LIVE_EXAM_POPULAR)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
	}

	private static boolean readPopular(JsonNode body) {
		JsonNode node = body == null ? null : body.get("popular");
		if (node == null || !node.isBoolean()) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
		}
		return node.booleanValue();
	}

	public record PopularView(UUID id, boolean popular) {
	}

}
