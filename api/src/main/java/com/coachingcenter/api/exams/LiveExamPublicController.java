package com.coachingcenter.api.exams;

import java.util.List;
import java.util.UUID;

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

	public LiveExamPublicController(LiveExamPublicService catalog, StudentOwnAccessService access,
			ModuleCoverService covers) {
		this.catalog = catalog;
		this.access = access;
		this.covers = covers;
	}

	@GetMapping("/api/v1/public/live-exam-categories")
	public List<LiveExamPublicService.PublicCategory> categories(@RequestParam(required = false) String examType) {
		return catalog.categories(LiveExamTypeFilter.parse(examType));
	}

	/** {@code q} matches either title, case-insensitively, and keeps the other filters. */
	@GetMapping("/api/v1/public/live-exam-modules")
	public PageResponse<LiveExamPublicService.PublicModule> modules(@RequestParam(required = false) UUID categoryId,
			@RequestParam(required = false) String examType, @RequestParam(required = false) String q,
			@PageableDefault(size = 20) Pageable pageable) {
		return PageResponse.from(catalog.modules(categoryId, LiveExamTypeFilter.parse(examType), q, pageable, viewer()));
	}

	/** Upcoming exams across the catalog. Only {@code upcoming=true} is served; {@code examType} filters the door. */
	@GetMapping("/api/v1/public/live-exams")
	public PageResponse<LiveExamPublicService.UpcomingExam> upcoming(
			@RequestParam(required = false, defaultValue = "false") boolean upcoming,
			@RequestParam(required = false) String examType, @PageableDefault(size = 20) Pageable pageable) {
		if (!upcoming) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
		}
		return PageResponse.from(catalog.upcoming(LiveExamTypeFilter.parse(examType), pageable));
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
		return catalog.module(id, viewer());
	}

	@GetMapping("/api/v1/public/live-exam-modules/{id}/exams")
	public PageResponse<LiveExamPublicService.PublicExam> exams(@PathVariable UUID id,
			@RequestParam(required = false) String examType, @PageableDefault(size = 20) Pageable pageable) {
		return PageResponse.from(catalog.exams(id, LiveExamTypeFilter.parse(examType), pageable, viewer()));
	}

	@GetMapping("/api/v1/public/live-exams/{id}")
	public LiveExamPublicService.PublicExam exam(@PathVariable UUID id) {
		return catalog.exam(id, viewer());
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

}
