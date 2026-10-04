package com.coachingcenter.api.library;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.courses.Material;
import com.coachingcenter.api.courses.MaterialRepository;
import com.coachingcenter.api.files.LocalFileStorage;
import com.coachingcenter.api.files.LocalFileStorage.StoredFile;

import tools.jackson.databind.JsonNode;

@Service
public class BookService {

	private final BookRepository books;

	private final MaterialRepository materials;

	private final LocalFileStorage fileStorage;

	private final BookCategoryService categories;

	private final AuditRecorder audit;

	public BookService(BookRepository books, MaterialRepository materials, LocalFileStorage fileStorage,
			BookCategoryService categories, AuditRecorder audit) {
		this.books = books;
		this.materials = materials;
		this.fileStorage = fileStorage;
		this.categories = categories;
		this.audit = audit;
	}

	@Transactional
	public BookView create(JsonNode body, boolean canSetPrice) {
		String titleEn = requiredText(body, "titleEn");
		String titleBn = requiredText(body, "titleBn");
		String author = requiredText(body, "author");
		String description = requiredText(body, "description");
		BigDecimal price = null;
		if (body.has("price") && !body.get("price").isNull()) {
			if (!canSetPrice) {
				throw denied();
			}
			price = parsePrice(body.get("price"));
		}
		Book book = new Book();
		book.setTitleEn(titleEn);
		book.setTitleBn(titleBn);
		book.setAuthor(author);
		book.setDescription(description);
		book.setPrice(price);
		book.setAvailable(false);
		return view(books.save(book));
	}

	@Transactional
	public BookView update(UUID id, JsonNode body, boolean canSetPrice) {
		Book book = requireBook(id);
		if (body.has("titleEn") && !body.get("titleEn").isNull()) {
			book.setTitleEn(requiredText(body, "titleEn"));
		}
		if (body.has("titleBn") && !body.get("titleBn").isNull()) {
			book.setTitleBn(requiredText(body, "titleBn"));
		}
		if (body.has("author") && !body.get("author").isNull()) {
			book.setAuthor(requiredText(body, "author"));
		}
		if (body.has("description") && !body.get("description").isNull()) {
			book.setDescription(requiredText(body, "description"));
		}
		if (body.has("price")) {
			if (!canSetPrice) {
				throw denied();
			}
			if (body.get("price").isNull()) {
				book.setPrice(null);
				if (book.isAvailable()) {
					book.setAvailable(false);
				}
			}
			else {
				book.setPrice(parsePrice(body.get("price")));
			}
		}
		if (body.has("available") && !body.get("available").isNull()) {
			boolean available = body.get("available").asBoolean();
			if (available && book.getPrice() == null) {
				throw conflict(ErrorCode.PRICE_REQUIRED, "A price is required before the book can be made available.");
			}
			book.setAvailable(available);
		}
		categories.assign(book, body);
		return view(book);
	}

	@Transactional
	public BookView uploadCover(UUID id, MultipartFile file) {
		Book book = requireBook(id);
		Material material = storeFile(book.getId(), file);
		book.setCoverFileId(material.getId());
		return view(book);
	}

	@Transactional(readOnly = true)
	public Page<BookView> list(Pageable pageable) {
		return books.findByDeletedAtIsNull(pageable).map(this::view);
	}

	@Transactional(readOnly = true)
	public BookView get(UUID id) {
		return view(requireBook(id));
	}

	@Transactional
	public void delete(UUID id) {
		Book book = requireBook(id);
		book.setDeletedAt(Instant.now());
		audit.record(AuditRecorder.BOOK_DELETE, AuditRecorder.BOOK_ENTITY, book.getId());
	}

	private Material storeFile(UUID bookId, MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw validationFailed();
		}
		String originalName = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
		String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
		StoredFile stored;
		try {
			stored = fileStorage.save(originalName, contentType, file.getInputStream(), file.getSize());
		}
		catch (ApiException exception) {
			throw exception;
		}
		catch (Exception exception) {
			throw new IllegalStateException("Could not read upload.", exception);
		}
		Material material = new Material();
		material.setBookId(bookId);
		material.setStoredFileName(stored.storedFileName());
		material.setOriginalFileName(stored.originalFileName());
		material.setByteSize(stored.byteSize());
		material.setContentType(stored.contentType());
		material.setFree(false);
		return materials.save(material);
	}

	private Book requireBook(UUID id) {
		return books.findByIdAndDeletedAtIsNull(id).orElseThrow(this::notFound);
	}

	private BookView view(Book book) {
		return new BookView(book.getId(), book.getTitleEn(), book.getTitleBn(), book.getAuthor(), book.getDescription(),
				book.getPrice(), book.isAvailable(), book.getCoverFileId(), book.getCategoryId());
	}

	private static String requiredText(JsonNode body, String field) {
		JsonNode node = body.get(field);
		if (node == null || node.isNull() || node.asText().isBlank()) {
			throw validationFailed();
		}
		return node.asText().trim();
	}

	private static BigDecimal parsePrice(JsonNode node) {
		if (node.isNumber()) {
			return node.decimalValue();
		}
		try {
			return new BigDecimal(node.asText().trim());
		}
		catch (NumberFormatException exception) {
			throw validationFailed();
		}
	}

	private static ApiException conflict(ErrorCode code, String message) {
		return new ApiException(code, HttpStatus.CONFLICT, message);
	}

	private static ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
	}

	private ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	private static ApiException denied() {
		return new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
	}

	public record BookView(UUID id, String titleEn, String titleBn, String author, String description, BigDecimal price,
			boolean available, UUID coverFileId, UUID categoryId) {
	}

}
