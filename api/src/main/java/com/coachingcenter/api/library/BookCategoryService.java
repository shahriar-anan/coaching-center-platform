package com.coachingcenter.api.library;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

import tools.jackson.databind.JsonNode;

@Service
public class BookCategoryService {

	private final BookCategoryRepository categories;

	private final BookRepository books;

	private final AuditRecorder audit;

	public BookCategoryService(BookCategoryRepository categories, BookRepository books, AuditRecorder audit) {
		this.categories = categories;
		this.books = books;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public List<CategoryView> list() {
		return categories.findByDeletedAtIsNullOrderByPositionAsc().stream().map(this::view).toList();
	}

	@Transactional
	public CategoryView create(JsonNode body) {
		rejectParent(body);
		String titleEn = requiredText(body, "titleEn");
		String titleBn = requiredText(body, "titleBn");
		int position = readPosition(body, null);
		BookCategory category = new BookCategory();
		category.setTitleEn(titleEn);
		category.setTitleBn(titleBn);
		category.setPosition(position);
		return view(categories.save(category));
	}

	@Transactional
	public CategoryView update(UUID id, JsonNode body) {
		rejectParent(body);
		BookCategory category = require(id);
		if (body.has("titleEn") && !body.get("titleEn").isNull()) {
			category.setTitleEn(requiredText(body, "titleEn"));
		}
		if (body.has("titleBn") && !body.get("titleBn").isNull()) {
			category.setTitleBn(requiredText(body, "titleBn"));
		}
		if (body.has("position") && !body.get("position").isNull()) {
			category.setPosition(readPosition(body, id));
		}
		return view(category);
	}

	@Transactional
	public void delete(UUID id) {
		BookCategory category = require(id);
		if (!books.findByCategoryIdAndDeletedAtIsNull(id).isEmpty()) {
			throw new ApiException(ErrorCode.CATEGORY_NOT_EMPTY, HttpStatus.CONFLICT, "Category still has a book.");
		}
		category.setDeletedAt(Instant.now());
		audit.record(AuditRecorder.BOOK_CATEGORY_DELETE, AuditRecorder.BOOK_CATEGORY_ENTITY, category.getId());
	}

	@Transactional
	public void assign(Book book, JsonNode body) {
		if (!body.has("categoryId")) {
			return;
		}
		if (body.get("categoryId").isNull()) {
			book.setCategoryId(null);
			return;
		}
		UUID categoryId = readId(body.get("categoryId"));
		require(categoryId);
		book.setCategoryId(categoryId);
	}

	private int readPosition(JsonNode body, UUID ignoreId) {
		int position = body.has("position") && !body.get("position").isNull() ? body.get("position").asInt()
				: nextPosition();
		if (positionTaken(position, ignoreId)) {
			throw new ApiException(ErrorCode.CATEGORY_POSITION_TAKEN, HttpStatus.CONFLICT,
					"That position is already used.");
		}
		return position;
	}

	private int nextPosition() {
		int max = -1;
		for (BookCategory category : categories.findByDeletedAtIsNullOrderByPositionAsc()) {
			max = Math.max(max, category.getPosition());
		}
		return max + 1;
	}

	private boolean positionTaken(int position, UUID ignoreId) {
		for (BookCategory category : categories.findByDeletedAtIsNullOrderByPositionAsc()) {
			if (category.getPosition() == position && !category.getId().equals(ignoreId)) {
				return true;
			}
		}
		return false;
	}

	private void rejectParent(JsonNode body) {
		if (body.has("parentId")) {
			throw validationFailed();
		}
	}

	private BookCategory require(UUID id) {
		return categories.findByIdAndDeletedAtIsNull(id).orElseThrow(this::notFound);
	}

	private static UUID readId(JsonNode node) {
		try {
			return UUID.fromString(node.asText());
		}
		catch (IllegalArgumentException exception) {
			throw validationFailed();
		}
	}

	private static String requiredText(JsonNode body, String field) {
		JsonNode node = body.get(field);
		if (node == null || node.isNull() || node.asText().isBlank()) {
			throw validationFailed();
		}
		return node.asText().trim();
	}

	private CategoryView view(BookCategory category) {
		return new CategoryView(category.getId(), category.getTitleEn(), category.getTitleBn(), category.getPosition());
	}

	private static ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
	}

	private ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	public record CategoryView(UUID id, String titleEn, String titleBn, int position) {
	}

}
