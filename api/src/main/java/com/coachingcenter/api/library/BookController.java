package com.coachingcenter.api.library;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.web.PageResponse;

import tools.jackson.databind.JsonNode;

@RestController
public class BookController {

	private final BookService books;

	private final PermissionChecker permissions;

	public BookController(BookService books, PermissionChecker permissions) {
		this.books = books;
		this.permissions = permissions;
	}

	@PostMapping("/api/v1/books")
	public ResponseEntity<BookService.BookView> create(@RequestBody JsonNode body) {
		require(Permissions.BOOK_MANAGE);
		return ResponseEntity.status(HttpStatus.CREATED).body(books.create(body, canSetPrice()));
	}

	@PatchMapping("/api/v1/books/{id}")
	public BookService.BookView update(@PathVariable UUID id, @RequestBody JsonNode body) {
		require(Permissions.BOOK_MANAGE);
		return books.update(id, body, canSetPrice());
	}

	@GetMapping("/api/v1/books")
	public PageResponse<BookService.BookView> list(@PageableDefault(size = 20) Pageable pageable) {
		require(Permissions.BOOK_READ);
		return PageResponse.from(books.list(pageable));
	}

	@GetMapping("/api/v1/books/{id}")
	public BookService.BookView get(@PathVariable UUID id) {
		require(Permissions.BOOK_READ);
		return books.get(id);
	}

	@DeleteMapping("/api/v1/books/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		require(Permissions.BOOK_DELETE);
		books.delete(id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping(path = "/api/v1/books/{id}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public BookService.BookView uploadCover(@PathVariable UUID id, @RequestPart("file") MultipartFile file) {
		require(Permissions.BOOK_MANAGE);
		return books.uploadCover(id, file);
	}

	private void require(String permission) {
		if (!permissions.has(permission)) {
			throw denied();
		}
	}

	private boolean canSetPrice() {
		return permissions.has(Permissions.BOOK_PRICE);
	}

	private static ApiException denied() {
		return new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
	}

}
