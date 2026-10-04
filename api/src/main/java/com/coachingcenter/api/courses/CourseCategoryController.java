package com.coachingcenter.api.courses;

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
public class CourseCategoryController {

	private final CourseCategoryService categories;

	private final PermissionChecker permissions;

	public CourseCategoryController(CourseCategoryService categories, PermissionChecker permissions) {
		this.categories = categories;
		this.permissions = permissions;
	}

	@GetMapping("/api/v1/course-categories")
	public java.util.List<CourseCategoryService.TreeNode> tree() {
		require(Permissions.COURSE_READ);
		return categories.tree();
	}

	@PostMapping("/api/v1/course-categories")
	public ResponseEntity<CourseCategoryService.CategoryView> create(@RequestBody JsonNode body) {
		require(Permissions.COURSE_MANAGE);
		return ResponseEntity.status(HttpStatus.CREATED).body(categories.create(body));
	}

	@PatchMapping("/api/v1/course-categories/{id}")
	public CourseCategoryService.CategoryView update(@PathVariable UUID id, @RequestBody JsonNode body) {
		require(Permissions.COURSE_MANAGE);
		return categories.update(id, body);
	}

	@DeleteMapping("/api/v1/course-categories/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		require(Permissions.COURSE_DELETE);
		categories.delete(id);
		return ResponseEntity.noContent().build();
	}

	private void require(String permission) {
		if (!permissions.has(permission)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
	}

}
