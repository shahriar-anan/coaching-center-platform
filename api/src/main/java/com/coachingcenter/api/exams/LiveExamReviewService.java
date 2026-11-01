package com.coachingcenter.api.exams;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.error.FieldErrorDetail;
import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.databind.JsonNode;

/**
 * The student's own review of an exam or a package (reviews plan, sections 4 and 6). Writing needs the student to have sat
 * the exam, or to hold the package and to have sat one of its exams. A comment waits for staff (PENDING); stars alone are
 * APPROVED at once. A review that staff hid stays hidden when the student edits it.
 */
@Service
public class LiveExamReviewService {

	static final int MAX_COMMENT = 1000;

	private final LiveExamReviewRepository reviews;

	private final LiveExamReviewTargets targets;

	private final StudentOwnAccessService access;

	private final Clock clock;

	public LiveExamReviewService(LiveExamReviewRepository reviews, LiveExamReviewTargets targets,
			StudentOwnAccessService access, Clock clock) {
		this.reviews = reviews;
		this.targets = targets;
		this.access = access;
		this.clock = clock;
	}

	/** Whether the student may review, and their review if they have one. A missing target is not found; ineligible is still 200. */
	@Transactional(readOnly = true)
	public Mine mine(UUID studentId, String kindPath, UUID targetId) {
		LiveExamReviewTargets.Target target = targets.requireLive(LiveExamReviewKind.require(kindPath), targetId);
		Optional<LiveExamReview> review = find(target, studentId);
		boolean canReview = eligible(studentId, target);
		return new Mine(canReview, review.isPresent(), review.map(MyReview::of).orElse(null));
	}

	/** Creates the student's review, or replaces it (idempotent). Returns the saved review. */
	@Transactional
	public MyReview save(UUID studentId, String kindPath, UUID targetId, JsonNode body) {
		LiveExamReviewTargets.Target target = targets.requireLive(LiveExamReviewKind.require(kindPath), targetId);
		if (!eligible(studentId, target)) {
			throw new ApiException(ErrorCode.REVIEW_NOT_ALLOWED, HttpStatus.FORBIDDEN,
					"You can review this after you have sat it.");
		}
		Input input = Input.from(body);
		Instant now = clock.instant();
		LiveExamReview review = find(target, studentId).map(existing -> update(existing, input, now))
			.orElseGet(() -> create(target, studentId, input, now));
		return MyReview.of(reviews.save(review));
	}

	/** Removes the student's review. A review that is not there is not an error. */
	@Transactional
	public void delete(UUID studentId, String kindPath, UUID targetId) {
		LiveExamReviewTargets.Target target = targets.requireLive(LiveExamReviewKind.require(kindPath), targetId);
		find(target, studentId).ifPresent(reviews::delete);
	}

	/** Every review the student has, newest first, for targets that still exist. */
	@Transactional(readOnly = true)
	public List<MyReviewRow> all(UUID studentId) {
		List<LiveExamReview> rows = reviews.findByStudentIdOrderByCreatedAtDescIdDesc(studentId);
		List<UUID> examIds = rows.stream().filter(row -> row.getExamId() != null).map(LiveExamReview::getExamId).toList();
		List<UUID> moduleIds = rows.stream().filter(row -> row.getModuleId() != null).map(LiveExamReview::getModuleId)
			.toList();
		Map<UUID, LiveExamReviewTargets.Target> examTargets = targets.load(LiveExamReviewKind.EXAM, examIds, false);
		Map<UUID, LiveExamReviewTargets.Target> moduleTargets = targets.load(LiveExamReviewKind.PACKAGE, moduleIds, false);
		List<MyReviewRow> result = new ArrayList<>();
		for (LiveExamReview row : rows) {
			LiveExamReviewTargets.Target target = row.getExamId() != null ? examTargets.get(row.getExamId())
					: moduleTargets.get(row.getModuleId());
			if (target != null) {
				result.add(new MyReviewRow(target.kind().path(), target.id(), target.titleEn(), target.titleBn(),
						MyReview.of(row)));
			}
		}
		return result;
	}

	/**
	 * The eligibility rule (section 4). An exam: the student has a submitted attempt on it. A package: the student holds it
	 * (active or expired) and has submitted an attempt on one of its active exams.
	 */
	private boolean eligible(UUID studentId, LiveExamReviewTargets.Target target) {
		if (target.kind() == LiveExamReviewKind.EXAM) {
			return reviews.countSatExam(studentId, target.id()) > 0;
		}
		if (reviews.countSatExamsInPackage(studentId, target.id()) == 0) {
			return false;
		}
		return access.mine(studentId).modules().stream().anyMatch(module -> module.moduleId().equals(target.id()));
	}

	private Optional<LiveExamReview> find(LiveExamReviewTargets.Target target, UUID studentId) {
		return target.kind() == LiveExamReviewKind.EXAM
				? reviews.findByExamIdAndStudentId(target.id(), studentId)
				: reviews.findByModuleIdAndStudentId(target.id(), studentId);
	}

	private static LiveExamReview create(LiveExamReviewTargets.Target target, UUID studentId, Input input, Instant now) {
		LiveExamReview review = new LiveExamReview();
		if (target.kind() == LiveExamReviewKind.EXAM) {
			review.setExamId(target.id());
		}
		else {
			review.setModuleId(target.id());
		}
		review.setStudentId(studentId);
		review.setRating(input.rating());
		review.setComment(input.comment());
		review.setStatus(input.comment() == null ? LiveExamReview.APPROVED : LiveExamReview.PENDING);
		review.setFeatured(false);
		review.setCreatedAt(now);
		review.setUpdatedAt(now);
		return review;
	}

	/**
	 * A new comment, or a changed one, sends the review back to PENDING; a removed comment makes it APPROVED. A HIDDEN review
	 * stays HIDDEN until staff change it. Featured clears when the status leaves APPROVED or the comment changes.
	 */
	private static LiveExamReview update(LiveExamReview review, Input input, Instant now) {
		boolean commentChanged = !Objects.equals(review.getComment(), input.comment());
		if (commentChanged && !LiveExamReview.HIDDEN.equals(review.getStatus())) {
			review.setStatus(input.comment() == null ? LiveExamReview.APPROVED : LiveExamReview.PENDING);
			review.setModeratedBy(null);
			review.setModeratedAt(null);
		}
		if (commentChanged || !LiveExamReview.APPROVED.equals(review.getStatus())) {
			review.setFeatured(false);
		}
		review.setRating(input.rating());
		review.setComment(input.comment());
		review.setUpdatedAt(now);
		return review;
	}

	/** The student's standing on one target. {@code review} is omitted when there is none. */
	public record Mine(boolean canReview, boolean reviewed, MyReview review) {
	}

	/** {@code comment} and {@code hideReason} are always written, null included. */
	public record MyReview(UUID id, int rating, @JsonInclude(JsonInclude.Include.ALWAYS) String comment, String status,
			@JsonInclude(JsonInclude.Include.ALWAYS) String hideReason, Instant createdAt, Instant updatedAt) {

		static MyReview of(LiveExamReview review) {
			return new MyReview(review.getId(), review.getRating(), review.getComment(), review.getStatus(),
					review.getHideReason(), review.getCreatedAt(), review.getUpdatedAt());
		}

	}

	public record MyReviewRow(String kind, UUID targetId, String targetTitleEn, String targetTitleBn, MyReview review) {
	}

	/** A validated body: a whole rating from 1 to 5, and a trimmed comment or null. */
	private record Input(short rating, String comment) {

		static Input from(JsonNode body) {
			List<FieldErrorDetail> errors = new ArrayList<>();
			JsonNode rating = body == null ? null : body.get("rating");
			short stars = 0;
			if (rating == null || !rating.isIntegralNumber() || !rating.canConvertToInt() || rating.intValue() < 1
					|| rating.intValue() > 5) {
				errors.add(new FieldErrorDetail("rating", "Rating must be a whole number from 1 to 5."));
			}
			else {
				stars = (short) rating.intValue();
			}
			String comment = null;
			JsonNode node = body == null ? null : body.get("comment");
			if (node != null && !node.isNull()) {
				if (!node.isTextual()) {
					errors.add(new FieldErrorDetail("comment", "Comment must be text."));
				}
				else {
					String text = node.textValue().trim();
					if (text.chars().anyMatch(Input::isControl)) {
						errors.add(new FieldErrorDetail("comment", "Comment must be plain text."));
					}
					else if (text.codePointCount(0, text.length()) > MAX_COMMENT) {
						errors.add(new FieldErrorDetail("comment", "Comment can be at most 1000 characters."));
					}
					else if (!text.isEmpty()) {
						comment = text;
					}
				}
			}
			if (!errors.isEmpty()) {
				throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.",
						errors);
			}
			return new Input(stars, comment);
		}

		/** A control character, except the line breaks and the tab that a comment may keep. */
		private static boolean isControl(int ch) {
			return Character.getType(ch) == Character.CONTROL && ch != '\n' && ch != '\r' && ch != '\t';
		}

	}

}
