package com.coachingcenter.api.exams;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.web.PageResponse;

import tools.jackson.databind.JsonNode;

@RestController
public class LiveExamModuleController {

	private final LiveExamModuleService modules;

	private final PermissionChecker permissions;

	public LiveExamModuleController(LiveExamModuleService modules, PermissionChecker permissions) {
		this.modules = modules;
		this.permissions = permissions;
	}

	@PostMapping("/api/v1/live-exam-modules")
	public ResponseEntity<LiveExamModuleService.ModuleView> create(@RequestBody JsonNode body) {
		require(Permissions.EXAM_MANAGE);
		requirePricePermission(body);
		return ResponseEntity.status(HttpStatus.CREATED).body(modules.create(body));
	}

	@PatchMapping("/api/v1/live-exam-modules/{id}")
	public LiveExamModuleService.ModuleView update(@PathVariable UUID id, @RequestBody JsonNode body) {
		require(Permissions.EXAM_MANAGE);
		requirePricePermission(body);
		return modules.update(id, body);
	}

	@PostMapping("/api/v1/live-exam-modules/{id}/final-save")
	public LiveExamModuleService.ModuleView finalSave(@PathVariable UUID id) {
		require(Permissions.EXAM_MANAGE);
		return modules.finalSave(id);
	}

	@PostMapping("/api/v1/live-exam-modules/{id}/publish")
	public LiveExamModuleService.ModuleView publish(@PathVariable UUID id) {
		require(Permissions.EXAM_MANAGE);
		return modules.publish(id);
	}

	@GetMapping("/api/v1/live-exam-modules")
	public PageResponse<LiveExamModuleService.ModuleView> list(@RequestParam(required = false) UUID categoryId,
			@RequestParam(required = false) String examType, @PageableDefault(size = 20) Pageable pageable) {
		String type = LiveExamTypeFilter.parse(examType);
		if (permissions.has(Permissions.EXAM_READ)) {
			return PageResponse.from(modules.list(categoryId, pageable, false, null));
		}
		if (permissions.has(Permissions.LIVE_EXAM_EVALUATE)) {
			return PageResponse.from(modules.listForInstructor(actorId(), pageable));
		}
		if (permissions.has(Permissions.LIVE_EXAM_BROWSE)) {
			return PageResponse.from(modules.list(categoryId, pageable, true, actorId(), type));
		}
		throw denied();
	}

	@GetMapping("/api/v1/live-exam-modules/{id}")
	public LiveExamModuleService.ModuleView get(@PathVariable UUID id) {
		if (permissions.has(Permissions.EXAM_READ)) {
			return modules.get(id);
		}
		if (permissions.has(Permissions.LIVE_EXAM_BROWSE)) {
			return modules.getPublished(id, actorId());
		}
		throw denied();
	}

	@DeleteMapping("/api/v1/live-exam-modules/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		require(Permissions.EXAM_DELETE);
		modules.delete(id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/api/v1/live-exam-modules/{id}/archive")
	public ResponseEntity<Void> archive(@PathVariable UUID id) {
		require(Permissions.EXAM_DELETE);
		modules.archive(id);
		return ResponseEntity.noContent().build();
	}

	private void require(String permission) {
		if (!permissions.has(permission)) {
			throw denied();
		}
	}

	private void requirePricePermission(JsonNode body) {
		if (body.has("price") && !permissions.has(Permissions.EXAM_PRICE)) {
			throw denied();
		}
	}

	private static ApiException denied() {
		return new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
	}

	private static UUID actorId() {
		var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof com.coachingcenter.api.auth.permission.CurrentActor actor) {
			return actor.userId();
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

}
