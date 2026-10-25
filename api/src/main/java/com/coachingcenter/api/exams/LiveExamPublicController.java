package com.coachingcenter.api.exams;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.web.PageResponse;

@RestController
public class LiveExamPublicController {

	private final LiveExamPublicService catalog;

	public LiveExamPublicController(LiveExamPublicService catalog) {
		this.catalog = catalog;
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
		return PageResponse.from(catalog.modules(categoryId, LiveExamTypeFilter.parse(examType), q, pageable));
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

	@GetMapping("/api/v1/public/live-exam-modules/{id}")
	public LiveExamPublicService.PublicModule module(@PathVariable UUID id) {
		return catalog.module(id);
	}

	@GetMapping("/api/v1/public/live-exam-modules/{id}/exams")
	public PageResponse<LiveExamPublicService.PublicExam> exams(@PathVariable UUID id,
			@RequestParam(required = false) String examType, @PageableDefault(size = 20) Pageable pageable) {
		return PageResponse.from(catalog.exams(id, LiveExamTypeFilter.parse(examType), pageable));
	}

	@GetMapping("/api/v1/public/live-exams/{id}")
	public LiveExamPublicService.PublicExam exam(@PathVariable UUID id) {
		return catalog.exam(id);
	}

}
