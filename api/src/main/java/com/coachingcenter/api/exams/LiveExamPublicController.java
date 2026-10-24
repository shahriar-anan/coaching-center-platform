package com.coachingcenter.api.exams;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.common.web.PageResponse;

@RestController
public class LiveExamPublicController {

	private final LiveExamPublicService catalog;

	public LiveExamPublicController(LiveExamPublicService catalog) {
		this.catalog = catalog;
	}

	@GetMapping("/api/v1/public/live-exam-categories")
	public List<LiveExamPublicService.PublicCategory> categories() {
		return catalog.categories();
	}

	@GetMapping("/api/v1/public/live-exam-modules")
	public PageResponse<LiveExamPublicService.PublicModule> modules(@RequestParam(required = false) UUID categoryId,
			@PageableDefault(size = 20) Pageable pageable) {
		return PageResponse.from(catalog.modules(categoryId, pageable));
	}

	@GetMapping("/api/v1/public/live-exam-modules/{id}")
	public LiveExamPublicService.PublicModule module(@PathVariable UUID id) {
		return catalog.module(id);
	}

	@GetMapping("/api/v1/public/live-exam-modules/{id}/exams")
	public PageResponse<LiveExamPublicService.PublicExam> exams(@PathVariable UUID id,
			@PageableDefault(size = 20) Pageable pageable) {
		return PageResponse.from(catalog.exams(id, pageable));
	}

	@GetMapping("/api/v1/public/live-exams/{id}")
	public LiveExamPublicService.PublicExam exam(@PathVariable UUID id) {
		return catalog.exam(id);
	}

}
