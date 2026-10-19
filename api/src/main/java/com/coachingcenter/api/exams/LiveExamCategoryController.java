package com.coachingcenter.api.exams;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

import tools.jackson.databind.JsonNode;

@RestController
public class LiveExamCategoryController {

	private final LiveExamCategoryService categories;

	private final PermissionChecker permissions;

	public LiveExamCategoryController(LiveExamCategoryService categories, PermissionChecker permissions) {
		this.categories = categories;
		this.permissions = permissions;
	}

	@GetMapping("/api/v1/live-exam-categories")
	public List<LiveExamCategoryService.TreeNode> tree() {
		require(Permissions.EXAM_READ);
		return categories.tree();
	}

	@GetMapping("/api/v1/live-exam-categories/{id}")
	public LiveExamCategoryService.CategoryView get(@PathVariable UUID id) {
		require(Permissions.EXAM_READ);
		return categories.get(id);
	}

	@PostMapping("/api/v1/live-exam-categories")
	public ResponseEntity<LiveExamCategoryService.CategoryView> create(@RequestBody JsonNode body) {
		require(Permissions.EXAM_MANAGE);
		return ResponseEntity.status(HttpStatus.CREATED).body(categories.create(body));
	}

	@PatchMapping("/api/v1/live-exam-categories/{id}")
	public LiveExamCategoryService.CategoryView update(@PathVariable UUID id, @RequestBody JsonNode body) {
		require(Permissions.EXAM_MANAGE);
		return categories.update(id, body);
	}

	@DeleteMapping("/api/v1/live-exam-categories/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		require(Permissions.EXAM_DELETE);
		categories.delete(id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/api/v1/live-exam-categories/{id}/archive")
	public ResponseEntity<Void> archive(@PathVariable UUID id) {
		require(Permissions.EXAM_DELETE);
		categories.archive(id);
		return ResponseEntity.noContent().build();
	}

	private void require(String permission) {
		if (!permissions.has(permission)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
	}

}
