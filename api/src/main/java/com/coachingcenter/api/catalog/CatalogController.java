package com.coachingcenter.api.catalog;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.courses.CourseCategoryService;
import com.coachingcenter.api.library.BookCategoryService;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.web.PageResponse;

@RestController
public class CatalogController {

	private final CatalogService catalog;

	private final ContentAccessService contentAccess;

	private final PermissionChecker permissions;

	private final CourseCategoryService courseCategories;

	private final BookCategoryService bookCategories;

	public CatalogController(CatalogService catalog, ContentAccessService contentAccess,
			PermissionChecker permissions, CourseCategoryService courseCategories, BookCategoryService bookCategories) {
		this.catalog = catalog;
		this.contentAccess = contentAccess;
		this.permissions = permissions;
		this.courseCategories = courseCategories;
		this.bookCategories = bookCategories;
	}

	@GetMapping("/api/v1/catalog/course-categories")
	public java.util.List<CourseCategoryService.BrowseCategoryNode> listCourseCategories() {
		requireBrowse();
		return courseCategories.browseTree();
	}

	@GetMapping("/api/v1/catalog/book-categories")
	public java.util.List<BookCategoryService.CategoryView> listBookCategories() {
		requireBrowse();
		return bookCategories.list();
	}

	@GetMapping("/api/v1/catalog/courses")
	public PageResponse<CatalogService.CourseSummary> listCourses(@RequestParam(defaultValue = "") String q,
			@RequestParam(required = false) UUID categoryId, @PageableDefault(size = 20) Pageable pageable) {
		requireBrowse();
		return PageResponse.from(catalog.listCourses(q, categoryId, pageable));
	}

	@GetMapping("/api/v1/catalog/courses/{id}")
	public CatalogService.CourseDetail getCourse(@PathVariable UUID id) {
		requireBrowse();
		return catalog.getCourse(id);
	}

	@GetMapping("/api/v1/catalog/books")
	public PageResponse<CatalogService.BookSummary> listBooks(@RequestParam(defaultValue = "") String q,
			@RequestParam(required = false) UUID categoryId, @PageableDefault(size = 20) Pageable pageable) {
		requireBrowse();
		return PageResponse.from(catalog.listBooks(q, categoryId, pageable));
	}

	@GetMapping("/api/v1/catalog/books/{id}")
	public CatalogService.BookDetail getBook(@PathVariable UUID id) {
		requireBrowse();
		return catalog.getBook(id);
	}

	@GetMapping("/api/v1/catalog/courses/{id}/lectures/{lectureId}")
	public ContentAccessService.LectureContent getLecture(@PathVariable UUID id, @PathVariable UUID lectureId) {
		requireContent();
		return contentAccess.getLecture(id, lectureId, actor().userId());
	}

	@GetMapping("/api/v1/catalog/courses/{id}/materials/{materialId}")
	public ContentAccessService.MaterialContent getMaterial(@PathVariable UUID id, @PathVariable UUID materialId) {
		requireContent();
		return contentAccess.getCourseMaterial(id, materialId, actor().userId());
	}

	@GetMapping("/api/v1/me/courses")
	public java.util.List<ContentAccessService.MyCourse> myCourses() {
		requireContent();
		return contentAccess.listMyCourses(actor().userId());
	}

	private void requireBrowse() {
		if (!permissions.has(Permissions.CATALOG_BROWSE)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, org.springframework.http.HttpStatus.FORBIDDEN,
					"Access is denied.");
		}
	}

	private void requireContent() {
		if (!permissions.has(Permissions.CONTENT_READ, true)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, org.springframework.http.HttpStatus.FORBIDDEN,
					"Access is denied.");
		}
	}

	private com.coachingcenter.api.auth.permission.CurrentActor actor() {
		var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext()
			.getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof com.coachingcenter.api.auth.permission.CurrentActor actor) {
			return actor;
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, org.springframework.http.HttpStatus.UNAUTHORIZED,
				"Authentication is required.");
	}

}
