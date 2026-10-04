package com.coachingcenter.api.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.courses.ContentNode;
import com.coachingcenter.api.courses.ContentNodeRepository;
import com.coachingcenter.api.courses.Course;
import com.coachingcenter.api.courses.CourseCategoryService;
import com.coachingcenter.api.courses.CourseCategoryService.CategoryPathItem;
import com.coachingcenter.api.courses.CourseRepository;
import com.coachingcenter.api.courses.Material;
import com.coachingcenter.api.courses.MaterialRepository;
import com.coachingcenter.api.library.Book;
import com.coachingcenter.api.library.BookRepository;

@Service
public class CatalogService {

	private final CourseRepository courses;

	private final BookRepository books;

	private final ContentNodeRepository nodes;

	private final CourseCategoryService courseCategories;

	private final MaterialRepository materials;

	public CatalogService(CourseRepository courses, BookRepository books, ContentNodeRepository nodes,
			CourseCategoryService courseCategories, MaterialRepository materials) {
		this.courses = courses;
		this.books = books;
		this.nodes = nodes;
		this.courseCategories = courseCategories;
		this.materials = materials;
	}

	@Transactional(readOnly = true)
	public Page<CourseSummary> listCourses(String query, UUID categoryId, Pageable pageable) {
		String q = normalizeQuery(query);
		if (categoryId == null) {
			return courses.findCatalogCourses(q, false, List.of(), pageable).map(this::courseSummary);
		}
		var categoryIds = new java.util.ArrayList<>(courseCategories.selfAndDescendantCategoryIds(categoryId));
		return courses.findCatalogCourses(q, true, categoryIds, pageable).map(this::courseSummary);
	}

	@Transactional(readOnly = true)
	public CourseDetail getCourse(UUID id) {
		Course course = courses.findCatalogCourse(id).orElseThrow(this::notFound);
		List<CatalogNode> tree = nodes.findByCourseIdAndDeletedAtIsNullOrderByPositionAsc(course.getId())
			.stream()
			.map(this::catalogNode)
			.toList();
		List<CategoryPathItem> categoryPath = courseCategories.pathToCategory(course.getCategoryId());
		List<CourseMaterialSummary> courseMaterials = listCourseMaterials(course);
		return new CourseDetail(course.getId(), course.getTitleEn(), course.getTitleBn(), course.getDescription(),
				course.getContentLanguage(), course.getPrice(), course.getDeliveryMode(), course.getAccessType(),
				course.getCoverFileId(), categoryPath, courseMaterials, tree);
	}

	@Transactional(readOnly = true)
	public Page<BookSummary> listBooks(String query, UUID categoryId, Pageable pageable) {
		String q = normalizeQuery(query);
		return books.findCatalogBooks(q, categoryId, pageable).map(this::bookSummary);
	}

	@Transactional(readOnly = true)
	public BookDetail getBook(UUID id) {
		Book book = books.findCatalogBook(id).orElseThrow(this::notFound);
		return new BookDetail(book.getId(), book.getTitleEn(), book.getTitleBn(), book.getAuthor(), book.getDescription(),
				book.getPrice(), book.getCoverFileId());
	}

	private CourseSummary courseSummary(Course course) {
		return new CourseSummary(course.getId(), course.getTitleEn(), course.getTitleBn(), course.getPrice(),
				course.getDeliveryMode(), course.getAccessType(), course.getCoverFileId());
	}

	private BookSummary bookSummary(Book book) {
		return new BookSummary(book.getId(), book.getTitleEn(), book.getTitleBn(), book.getAuthor(), book.getPrice(),
				book.getCoverFileId());
	}

	private CatalogNode catalogNode(ContentNode node) {
		return new CatalogNode(node.getId(), node.getParentId(), node.getNodeType(), node.getTitle(), node.getPosition(),
				node.isFree());
	}

	private List<CourseMaterialSummary> listCourseMaterials(Course course) {
		UUID coverFileId = course.getCoverFileId();
		return materials.findByCourseIdAndDeletedAtIsNullOrderByCreatedAtAsc(course.getId())
			.stream()
			.filter(material -> material.getLectureNodeId() == null)
			.filter(material -> coverFileId == null || !coverFileId.equals(material.getId()))
			.map(material -> new CourseMaterialSummary(material.getId(), material.getOriginalFileName(),
					material.getContentType(), material.getByteSize(), material.isFree()))
			.toList();
	}

	private static String normalizeQuery(String query) {
		return query == null ? "" : query.trim();
	}

	private ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	public record CourseSummary(UUID id, String titleEn, String titleBn, BigDecimal price, String deliveryMode,
			String accessType, UUID coverFileId) {
	}

	public record CourseMaterialSummary(UUID id, String originalFileName, String contentType, long byteSize,
			boolean free) {
	}

	public record CourseDetail(UUID id, String titleEn, String titleBn, String description, String contentLanguage,
			BigDecimal price, String deliveryMode, String accessType, UUID coverFileId,
			List<CategoryPathItem> categoryPath, List<CourseMaterialSummary> courseMaterials,
			List<CatalogNode> contentTree) {
	}

	public record CatalogNode(UUID id, UUID parentId, String nodeType, String title, int position, boolean free) {
	}

	public record BookSummary(UUID id, String titleEn, String titleBn, String author, BigDecimal price, UUID coverFileId) {
	}

	public record BookDetail(UUID id, String titleEn, String titleBn, String author, String description,
			BigDecimal price, UUID coverFileId) {
	}

}
