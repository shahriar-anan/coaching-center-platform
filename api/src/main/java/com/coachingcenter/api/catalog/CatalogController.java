package com.coachingcenter.api.catalog;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.courses.CourseCategoryService;
import com.coachingcenter.api.exams.ExamCatalogService;
import com.coachingcenter.api.exams.ExamResultService;
import com.coachingcenter.api.exams.McqAttemptService;
import com.coachingcenter.api.exams.WrittenSubmissionService;
import com.coachingcenter.api.library.BookCategoryService;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.web.PageResponse;

import tools.jackson.databind.JsonNode;

@RestController
public class CatalogController {

	private final CatalogService catalog;

	private final ContentAccessService contentAccess;

	private final PermissionChecker permissions;

	private final CourseCategoryService courseCategories;

	private final BookCategoryService bookCategories;

	private final ExamCatalogService examCatalog;

	private final McqAttemptService mcqAttempts;

	private final WrittenSubmissionService writtenSubmissions;

	private final ExamResultService examResults;

	public CatalogController(CatalogService catalog, ContentAccessService contentAccess,
			PermissionChecker permissions, CourseCategoryService courseCategories, BookCategoryService bookCategories,
			ExamCatalogService examCatalog, McqAttemptService mcqAttempts, WrittenSubmissionService writtenSubmissions,
			ExamResultService examResults) {
		this.catalog = catalog;
		this.contentAccess = contentAccess;
		this.permissions = permissions;
		this.courseCategories = courseCategories;
		this.bookCategories = bookCategories;
		this.examCatalog = examCatalog;
		this.mcqAttempts = mcqAttempts;
		this.writtenSubmissions = writtenSubmissions;
		this.examResults = examResults;
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

	@GetMapping("/api/v1/catalog/exam-modules")
	public PageResponse<ExamCatalogService.ExamModuleSummary> listExamModules(
			@RequestParam(defaultValue = "") String q, @PageableDefault(size = 20) Pageable pageable) {
		requireBrowse();
		return PageResponse.from(examCatalog.list(q, pageable, actor().userId()));
	}

	@GetMapping("/api/v1/catalog/exam-modules/{id}")
	public ExamCatalogService.ExamModuleDetail getExamModule(@PathVariable UUID id) {
		requireBrowse();
		return examCatalog.get(id, actor().userId());
	}

	@GetMapping("/api/v1/catalog/exam-modules/{moduleId}/components/{componentId}")
	public ExamCatalogService.StudentComponentView getExamComponent(@PathVariable UUID moduleId,
			@PathVariable UUID componentId) {
		requireBrowse();
		return examCatalog.getComponent(moduleId, componentId, actor().userId());
	}

	@PostMapping("/api/v1/catalog/exam-modules/{moduleId}/components/{componentId}/attempts")
	public McqAttemptService.AttemptView startMcqAttempt(@PathVariable UUID moduleId,
			@PathVariable UUID componentId) {
		requireBrowse();
		return mcqAttempts.start(moduleId, componentId, actor().userId());
	}

	@GetMapping("/api/v1/catalog/exam-modules/{moduleId}/components/{componentId}/attempts")
	public McqAttemptService.AttemptView getMcqAttempt(@PathVariable UUID moduleId, @PathVariable UUID componentId) {
		requireBrowse();
		return mcqAttempts.getStudent(moduleId, componentId, actor().userId());
	}

	@org.springframework.web.bind.annotation.PutMapping("/api/v1/catalog/exam-modules/{moduleId}/components/{componentId}/attempts/answers")
	public McqAttemptService.AttemptView saveMcqAnswers(@PathVariable UUID moduleId, @PathVariable UUID componentId,
			@RequestBody JsonNode body) {
		requireBrowse();
		return mcqAttempts.saveAnswers(moduleId, componentId, actor().userId(), body);
	}

	@PostMapping("/api/v1/catalog/exam-modules/{moduleId}/components/{componentId}/attempts/submit")
	public McqAttemptService.AttemptView submitMcqAttempt(@PathVariable UUID moduleId, @PathVariable UUID componentId) {
		requireBrowse();
		return mcqAttempts.submit(moduleId, componentId, actor().userId());
	}

	@PostMapping("/api/v1/catalog/exam-modules/{moduleId}/components/{componentId}/submission/images")
	public WrittenSubmissionService.SubmissionView uploadSubmissionImage(@PathVariable UUID moduleId,
			@PathVariable UUID componentId, @RequestParam("file") MultipartFile file) {
		requireBrowse();
		return writtenSubmissions.uploadImage(moduleId, componentId, actor().userId(), file);
	}

	@PostMapping("/api/v1/catalog/exam-modules/{moduleId}/components/{componentId}/submission/images/reorder")
	public WrittenSubmissionService.SubmissionView reorderSubmissionImages(@PathVariable UUID moduleId,
			@PathVariable UUID componentId, @RequestBody JsonNode body) {
		requireBrowse();
		return writtenSubmissions.reorderImages(moduleId, componentId, actor().userId(), body);
	}

	@DeleteMapping("/api/v1/catalog/exam-modules/{moduleId}/components/{componentId}/submission/images/{imageId}")
	public WrittenSubmissionService.SubmissionView deleteSubmissionImage(@PathVariable UUID moduleId,
			@PathVariable UUID componentId, @PathVariable UUID imageId) {
		requireBrowse();
		return writtenSubmissions.deleteImage(moduleId, componentId, actor().userId(), imageId);
	}

	@PostMapping("/api/v1/catalog/exam-modules/{moduleId}/components/{componentId}/submission/submit")
	public WrittenSubmissionService.SubmissionView submitWritten(@PathVariable UUID moduleId,
			@PathVariable UUID componentId) {
		requireBrowse();
		return writtenSubmissions.submit(moduleId, componentId, actor().userId());
	}

	@GetMapping("/api/v1/catalog/exam-modules/{moduleId}/components/{componentId}/submission")
	public WrittenSubmissionService.SubmissionView getWrittenSubmission(@PathVariable UUID moduleId,
			@PathVariable UUID componentId) {
		requireBrowse();
		return writtenSubmissions.getStudent(moduleId, componentId, actor().userId());
	}

	@GetMapping("/api/v1/catalog/exam-modules/{moduleId}/components/{componentId}/result")
	public ExamResultService.ComponentResultView componentResult(@PathVariable UUID moduleId,
			@PathVariable UUID componentId) {
		requireBrowse();
		return examResults.studentComponentResult(moduleId, componentId, actor().userId());
	}

	@GetMapping("/api/v1/catalog/exam-modules/{moduleId}/papers/{paperId}/result")
	public ExamResultService.PaperResultView paperResult(@PathVariable UUID moduleId, @PathVariable UUID paperId) {
		requireBrowse();
		return examResults.studentPaperResult(moduleId, paperId, actor().userId());
	}

	@GetMapping("/api/v1/catalog/exam-modules/{moduleId}/papers/{paperId}/leaderboard")
	public PageResponse<ExamResultService.LeaderboardRow> studentLeaderboard(@PathVariable UUID moduleId,
			@PathVariable UUID paperId, @PageableDefault(size = 20) Pageable pageable) {
		requireBrowse();
		return examResults.leaderboard(paperId, pageable, true, actor().userId(), moduleId);
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

	@PostMapping("/api/v1/me/courses/{courseId}/progress")
	public ContentAccessService.ProgressView recordProgress(@PathVariable UUID courseId, @RequestBody JsonNode body) {
		requireContent();
		return contentAccess.recordProgress(courseId, actor().userId(), body);
	}

	@GetMapping("/api/v1/me/courses/{courseId}/progress")
	public ContentAccessService.CourseProgress courseProgress(@PathVariable UUID courseId) {
		requireContent();
		return contentAccess.courseProgress(courseId, actor().userId());
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
