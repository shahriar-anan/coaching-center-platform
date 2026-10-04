package com.coachingcenter.api.library;

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
public class BookCategoryController {

	private final BookCategoryService categories;

	private final PermissionChecker permissions;

	public BookCategoryController(BookCategoryService categories, PermissionChecker permissions) {
		this.categories = categories;
		this.permissions = permissions;
	}

	@GetMapping("/api/v1/book-categories")
	public List<BookCategoryService.CategoryView> list() {
		require(Permissions.BOOK_READ);
		return categories.list();
	}

	@PostMapping("/api/v1/book-categories")
	public ResponseEntity<BookCategoryService.CategoryView> create(@RequestBody JsonNode body) {
		require(Permissions.BOOK_MANAGE);
		return ResponseEntity.status(HttpStatus.CREATED).body(categories.create(body));
	}

	@PatchMapping("/api/v1/book-categories/{id}")
	public BookCategoryService.CategoryView update(@PathVariable UUID id, @RequestBody JsonNode body) {
		require(Permissions.BOOK_MANAGE);
		return categories.update(id, body);
	}

	@DeleteMapping("/api/v1/book-categories/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		require(Permissions.BOOK_DELETE);
		categories.delete(id);
		return ResponseEntity.noContent().build();
	}

	private void require(String permission) {
		if (!permissions.has(permission)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
	}

}
