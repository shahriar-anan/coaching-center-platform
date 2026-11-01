package com.coachingcenter.api.exams;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The public reviews (reviews plan, section 6). No sign-in; only approved comments and no student ids, phones or emails. */
@RestController
public class LiveExamReviewPublicController {

	private final LiveExamReviewPublicService reviews;

	public LiveExamReviewPublicController(LiveExamReviewPublicService reviews) {
		this.reviews = reviews;
	}

	/** The averages, the star counts and one page of approved comments. {@code size} is at most 50 (default 10). */
	@GetMapping("/api/v1/public/reviews/{kind}/{id}")
	public LiveExamReviewPublicService.PublicReviews forTarget(@PathVariable String kind, @PathVariable UUID id,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
		return reviews.reviews(LiveExamReviewKind.require(kind), id, page, size);
	}

	/** Up to nine featured reviews, the most recently featured first. */
	@GetMapping("/api/v1/public/reviews/featured")
	public List<LiveExamReviewPublicService.FeaturedReview> featured() {
		return reviews.featured();
	}

}
