package com.coachingcenter.api.exams;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.web.PageResponse;

@RestController
public class LiveExamPublicController {

	private final LiveExamPublicService catalog;

	private final StudentOwnAccessService access;

	private final ModuleCoverService covers;

	private final FavouriteService favourites;

	public LiveExamPublicController(LiveExamPublicService catalog, StudentOwnAccessService access,
			ModuleCoverService covers, FavouriteService favourites) {
		this.catalog = catalog;
		this.access = access;
		this.covers = covers;
		this.favourites = favourites;
	}

	@GetMapping("/api/v1/public/live-exam-categories")
	public List<LiveExamPublicService.PublicCategory> categories(
			@RequestParam(required = false) String examType) {
		return catalog.categories(LiveExamTypeFilter.parse(examType));
	}

	/**
	 * The published exams of one category: standalone ones and those in its packages alike (NR-01, B2). {@code mode},
	 * {@code phase} and {@code popular} filter the list. {@code branch} adds the exams of every subcategory below it (NR-01,
	 * S0.1); without it only the category's own exams are listed.
	 */
	@GetMapping("/api/v1/public/live-exam-categories/{id}/exams")
	public PageResponse<LiveExamPublicService.CategoryExam> categoryExams(@PathVariable UUID id,
			@RequestParam(required = false) String examType, @RequestParam(required = false) String mode,
			@RequestParam(required = false) String phase,
			@RequestParam(required = false, defaultValue = "false") boolean popular,
			@RequestParam(required = false, defaultValue = "false") boolean branch,
			@PageableDefault(size = 20) Pageable pageable) {
		Page<LiveExamPublicService.CategoryExam> page = catalog.categoryExams(id, LiveExamTypeFilter.parse(examType),
				LiveExamTypeFilter.parseMode(mode), LiveExamTypeFilter.parsePhase(phase), popular, branch, pageable);
		UUID student = studentId();
		if (student != null) {
			Set<UUID> marked = favourites.examIds(student);
			page = page.map(row -> row.withFavourite(marked.contains(row.id())));
		}
		return PageResponse.from(page);
	}

	/** {@code q} matches either title, case-insensitively, and keeps the other filters. {@code popular} keeps the popular ones. */
	@GetMapping("/api/v1/public/live-exam-modules")
	public PageResponse<LiveExamPublicService.PublicModule> modules(@RequestParam(required = false) UUID categoryId,
			@RequestParam(required = false) String examType, @RequestParam(required = false) String q,
			@RequestParam(required = false, defaultValue = "false") boolean popular,
			@PageableDefault(size = 20) Pageable pageable) {
		StudentOwnAccessService.Viewer viewer = viewer();
		Page<LiveExamPublicService.PublicModule> page = catalog.modules(categoryId, LiveExamTypeFilter.parse(examType), q,
				popular, pageable, viewer);
		UUID student = studentId();
		if (student != null) {
			Set<UUID> marked = favourites.moduleIds(student);
			page = page.map(row -> row.withFavourite(marked.contains(row.id())));
		}
		return PageResponse.from(page);
	}

	/**
	 * Upcoming exams across the catalog. Only {@code upcoming=true} is served; {@code examType} filters the door. Standalone
	 * exams are included (NR-01, B2). {@code popular} keeps the popular exams (NR-01, S0.2).
	 */
	@GetMapping("/api/v1/public/live-exams")
	public PageResponse<LiveExamPublicService.UpcomingExam> upcoming(
			@RequestParam(required = false, defaultValue = "false") boolean upcoming,
			@RequestParam(required = false) String examType,
			@RequestParam(required = false, defaultValue = "false") boolean popular,
			@PageableDefault(size = 20) Pageable pageable) {
		if (!upcoming) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
		}
		Page<LiveExamPublicService.UpcomingExam> page = catalog.upcoming(LiveExamTypeFilter.parse(examType), popular, pageable);
		UUID student = studentId();
		if (student != null) {
			Set<UUID> marked = favourites.examIds(student);
			page = page.map(row -> row.withFavourite(marked.contains(row.id())));
		}
		return PageResponse.from(page);
	}

	@GetMapping("/api/v1/public/live-exam-modules/{id}/cover")
	public ResponseEntity<InputStreamResource> cover(@PathVariable UUID id) {
		catalog.module(id, null);
		ModuleCoverService.CoverFile file = covers.open(id);
		return ResponseEntity.ok()
			.header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
			.contentType(MediaType.parseMediaType(file.contentType()))
			.contentLength(file.byteSize())
			.body(new InputStreamResource(file.body()));
	}

	@GetMapping("/api/v1/public/live-exam-modules/{id}")
	public LiveExamPublicService.PublicModule module(@PathVariable UUID id) {
		LiveExamPublicService.PublicModule module = catalog.module(id, viewer());
		UUID student = studentId();
		return student == null ? module : module.withFavourite(favourites.moduleIds(student).contains(id));
	}

	/** {@code popular} keeps the popular exams of the package (NR-01, B5). */
	@GetMapping("/api/v1/public/live-exam-modules/{id}/exams")
	public PageResponse<LiveExamPublicService.PublicExam> exams(@PathVariable UUID id,
			@RequestParam(required = false) String examType,
			@RequestParam(required = false, defaultValue = "false") boolean popular,
			@PageableDefault(size = 20) Pageable pageable) {
		Page<LiveExamPublicService.PublicExam> page = catalog.exams(id, LiveExamTypeFilter.parse(examType), popular,
				pageable, viewer());
		UUID student = studentId();
		if (student != null) {
			Set<UUID> marked = favourites.examIds(student);
			page = page.map(row -> row.withFavourite(marked.contains(row.id())));
		}
		return PageResponse.from(page);
	}

	@GetMapping("/api/v1/public/live-exams/{id}")
	public LiveExamPublicService.PublicExam exam(@PathVariable UUID id) {
		LiveExamPublicService.PublicExam exam = catalog.exam(id, viewer());
		UUID student = studentId();
		return student == null ? exam : exam.withFavourite(favourites.examIds(student).contains(id));
	}

	/** The signed-in student, or null for a visitor and for staff. A visitor's response has no {@code access}. */
	private StudentOwnAccessService.Viewer viewer() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor
				&& actor.role() == Role.STUDENT) {
			return access.viewer(actor.userId());
		}
		return null;
	}

	/** The signed-in student's id, or null for a visitor and for staff. Only a student's response carries {@code favourite}. */
	private static UUID studentId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor
				&& actor.role() == Role.STUDENT) {
			return actor.userId();
		}
		return null;
	}

}
