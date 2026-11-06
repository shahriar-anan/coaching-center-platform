package com.coachingcenter.api.exams;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.web.PageResponse;
import com.coachingcenter.api.files.LocalFileStorage;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * What the public sees of reviews (reviews plan, sections 4 and 6): the averages and star counts, the approved comments
 * with a first name and a last initial, and the featured reviews for the landing page. A review counts when it is not
 * HIDDEN and its student is an active account. A removed target is not found; an archived one keeps its reviews.
 *
 * <p>Each review carries the author's profile picture as a URL keyed by the review id (notice media plan, F2 and the reviews
 * photo change). The student's own id is never in a public response.
 */
@Service
public class LiveExamReviewPublicService {

	/** An average is shown only from this many ratings (section 4). */
	static final int MIN_RATINGS = 3;

	static final int FEATURED_LIMIT = 9;

	private static final int DEFAULT_SIZE = 10;

	private static final int MAX_SIZE = 50;

	private final NamedParameterJdbcTemplate jdbc;

	private final LiveExamReviewTargets targets;

	private final LiveExamCatalogVisibility visibility;

	private final LocalFileStorage fileStorage;

	public LiveExamReviewPublicService(JdbcTemplate jdbc, LiveExamReviewTargets targets, LiveExamCatalogVisibility visibility,
			LocalFileStorage fileStorage) {
		this.jdbc = new NamedParameterJdbcTemplate(jdbc);
		this.targets = targets;
		this.visibility = visibility;
		this.fileStorage = fileStorage;
	}

	/** The star counts of each target, for targets that have any counted rating. A target with none is absent. */
	@Transactional(readOnly = true)
	public Map<UUID, Tally> tallies(LiveExamReviewKind kind, Collection<UUID> ids) {
		Map<UUID, Tally> result = new HashMap<>();
		if (ids.isEmpty()) {
			return result;
		}
		String sql = """
				SELECT r.%1$s AS target_id, r.rating AS rating, count(*) AS total
				FROM live_exam_reviews r
				JOIN users u ON u.id = r.student_id AND u.deleted_at IS NULL AND u.status = 'ACTIVE'
				WHERE r.%1$s IN (:ids) AND r.status <> 'HIDDEN'
				GROUP BY r.%1$s, r.rating
				""".formatted(kind.column());
		List<StarCount> rows = jdbc.query(sql, Map.of("ids", ids),
				(rs, number) -> new StarCount(rs.getObject("target_id", UUID.class), rs.getInt("rating"),
						rs.getLong("total")));
		Map<UUID, int[]> byTarget = new HashMap<>();
		for (StarCount row : rows) {
			byTarget.computeIfAbsent(row.target(), key -> new int[6])[row.rating()] += (int) row.total();
		}
		byTarget.forEach((id, stars) -> result.put(id, new Tally(stars[1], stars[2], stars[3], stars[4], stars[5])));
		return result;
	}

	/**
	 * One target's public reviews: the averages, the star counts, and one page of approved comments, newest first. The page
	 * size is at most 50 (default 10).
	 */
	@Transactional(readOnly = true)
	public PublicReviews reviews(LiveExamReviewKind kind, UUID targetId, int page, int size) {
		requireVisible(kind, targetId);
		int pageSize = size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
		int pageNumber = Math.max(page, 0);
		String from = """
				FROM live_exam_reviews r
				JOIN users u ON u.id = r.student_id AND u.deleted_at IS NULL AND u.status = 'ACTIVE'
				LEFT JOIN student_profiles p ON p.user_id = r.student_id
				LEFT JOIN student_photos sp ON sp.user_id = r.student_id
				WHERE r.%s = :target AND r.status = 'APPROVED' AND r.comment IS NOT NULL
				""".formatted(kind.column());
		Map<String, Object> params = Map.of("target", targetId, "limit", pageSize, "offset", (long) pageNumber * pageSize);
		long total = jdbc.queryForObject("SELECT count(*) " + from, params, Long.class);
		List<PublicReview> content = jdbc.query("SELECT r.id, p.full_name, r.rating, r.comment, r.created_at, sp.updated_at AS photo_at "
				+ from + " ORDER BY r.created_at DESC, r.id DESC LIMIT :limit OFFSET :offset", params,
				(rs, number) -> {
					UUID id = rs.getObject("id", UUID.class);
					return new PublicReview(id, displayName(rs.getString("full_name")), rs.getInt("rating"),
							rs.getString("comment"), instant(rs.getTimestamp("created_at")),
							photoUrl(id, instant(rs.getTimestamp("photo_at"))));
				});
		Tally tally = tallies(kind, List.of(targetId)).getOrDefault(targetId, Tally.NONE);
		int totalPages = (int) ((total + pageSize - 1) / pageSize);
		return new PublicReviews(tally.average(), tally.count(), tally.stars(),
				new PageResponse<>(content, pageNumber, pageSize, total, totalPages));
	}

	/**
	 * Up to nine featured reviews, the most recently featured first (reviews plan, section 6). Only approved reviews with a
	 * comment. A review whose target is removed, a draft, or in a hidden branch is left out.
	 */
	@Transactional(readOnly = true)
	public List<FeaturedReview> featured() {
		Set<UUID> hidden = visibility.hiddenCategoryIds();
		String sql = """
				SELECT r.id, r.rating, r.comment, r.created_at, p.full_name, sp.updated_at AS photo_at,
				  e.id AS exam_target, e.category_id AS exam_category, e.title_en AS exam_title_en, e.title_bn AS exam_title_bn,
				  m.id AS module_target, m.category_id AS module_category, m.title_en AS module_title_en,
				  m.title_bn AS module_title_bn
				FROM live_exam_reviews r
				JOIN users u ON u.id = r.student_id AND u.deleted_at IS NULL AND u.status = 'ACTIVE'
				LEFT JOIN student_profiles p ON p.user_id = r.student_id
				LEFT JOIN student_photos sp ON sp.user_id = r.student_id
				LEFT JOIN live_exams e ON e.id = r.exam_id AND e.deleted_at IS NULL AND e.status <> 'DRAFT'
				LEFT JOIN live_exam_modules m ON m.id = r.module_id AND m.deleted_at IS NULL AND m.status <> 'DRAFT'
				WHERE r.featured AND r.status = 'APPROVED' AND r.comment IS NOT NULL
				ORDER BY r.moderated_at DESC NULLS LAST, r.id
				""";
		List<FeaturedRow> rows = jdbc.query(sql, Map.of(), (rs, number) -> featuredRow(rs));
		List<FeaturedReview> result = new ArrayList<>();
		for (FeaturedRow row : rows) {
			if (row != null && !hidden.contains(row.categoryId()) && result.size() < FEATURED_LIMIT) {
				result.add(row.review());
			}
		}
		return result;
	}

	/**
	 * The picture of a review's author, for a visible approved review. The review id is the key, so the student's id never
	 * appears in a URL. Any other review, a hidden one, or an author with no picture is 404.
	 */
	@Transactional(readOnly = true)
	public ReviewPhoto photoOf(UUID reviewId) {
		String sql = """
				SELECT r.exam_id, r.module_id, sp.stored_name, sp.content_type, sp.byte_size
				FROM live_exam_reviews r
				JOIN users u ON u.id = r.student_id AND u.deleted_at IS NULL AND u.status = 'ACTIVE'
				JOIN student_photos sp ON sp.user_id = r.student_id
				WHERE r.id = :id AND r.status = 'APPROVED' AND r.comment IS NOT NULL
				""";
		Map<String, Object> row;
		try {
			row = jdbc.queryForMap(sql, Map.of("id", reviewId));
		}
		catch (EmptyResultDataAccessException exception) {
			throw notFound();
		}
		UUID examId = (UUID) row.get("exam_id");
		UUID moduleId = (UUID) row.get("module_id");
		LiveExamReviewKind kind = examId != null ? LiveExamReviewKind.EXAM : LiveExamReviewKind.PACKAGE;
		requireVisible(kind, examId != null ? examId : moduleId);
		Path path = fileStorage.resolve((String) row.get("stored_name"));
		if (!Files.exists(path)) {
			throw notFound();
		}
		try {
			InputStream body = Files.newInputStream(path);
			return new ReviewPhoto(body, (String) row.get("content_type"), ((Number) row.get("byte_size")).longValue());
		}
		catch (IOException exception) {
			throw notFound();
		}
	}

	/** The target must exist, must not be a draft, and must sit in a visible branch (a removed one is not found). */
	private void requireVisible(LiveExamReviewKind kind, UUID id) {
		LiveExamReviewTargets.Target target = targets.live(kind, id);
		if (target == null || "DRAFT".equals(target.status()) || visibility.branchHidden(target.categoryId())) {
			throw notFound();
		}
	}

	private static ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	/** One featured row, or null when its target is gone (the exam or the package side of the join is empty). */
	private static FeaturedRow featuredRow(ResultSet rs) throws SQLException {
		UUID examTarget = rs.getObject("exam_target", UUID.class);
		UUID moduleTarget = rs.getObject("module_target", UUID.class);
		LiveExamReviewKind kind;
		UUID targetId;
		String titleEn;
		String titleBn;
		UUID categoryId;
		if (examTarget != null) {
			kind = LiveExamReviewKind.EXAM;
			targetId = examTarget;
			titleEn = rs.getString("exam_title_en");
			titleBn = rs.getString("exam_title_bn");
			categoryId = rs.getObject("exam_category", UUID.class);
		}
		else if (moduleTarget != null) {
			kind = LiveExamReviewKind.PACKAGE;
			targetId = moduleTarget;
			titleEn = rs.getString("module_title_en");
			titleBn = rs.getString("module_title_bn");
			categoryId = rs.getObject("module_category", UUID.class);
		}
		else {
			return null;
		}
		UUID id = rs.getObject("id", UUID.class);
		FeaturedReview review = new FeaturedReview(id, displayName(rs.getString("full_name")),
				rs.getInt("rating"), rs.getString("comment"), instant(rs.getTimestamp("created_at")), kind.path(), targetId,
				titleEn, titleBn, photoUrl(id, instant(rs.getTimestamp("photo_at"))));
		return new FeaturedRow(review, categoryId);
	}

	/**
	 * The first name and the initial of the last name, as "Farhana A." A single word is shown as it is. Blank gives blank.
	 */
	static String displayName(String fullName) {
		String name = fullName == null ? "" : fullName.trim();
		if (name.isEmpty()) {
			return "";
		}
		String[] words = name.split("\\s+");
		if (words.length == 1) {
			return words[0];
		}
		String last = words[words.length - 1];
		return words[0] + " " + last.substring(0, last.offsetByCodePoints(0, 1)) + ".";
	}

	/** The public path of a reviewer's picture, with the upload time as the version; null when there is no picture. */
	static String photoUrl(UUID reviewId, Instant photoAt) {
		if (photoAt == null) {
			return null;
		}
		return "/api/v1/public/reviews/" + reviewId + "/photo?v=" + photoAt.toEpochMilli();
	}

	private static Instant instant(Timestamp value) {
		return value == null ? null : value.toInstant();
	}

	/**
	 * The star counts of one target. The average is null below {@link #MIN_RATINGS} ratings, rounded to one decimal place.
	 */
	public record Tally(int one, int two, int three, int four, int five) {

		static final Tally NONE = new Tally(0, 0, 0, 0, 0);

		public int count() {
			return one + two + three + four + five;
		}

		public Double average() {
			int count = count();
			if (count < MIN_RATINGS) {
				return null;
			}
			long sum = one + 2L * two + 3L * three + 4L * four + 5L * five;
			return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP).doubleValue();
		}

		/** The keys "1" to "5", always present. */
		public Map<String, Integer> stars() {
			Map<String, Integer> stars = new LinkedHashMap<>();
			stars.put("1", one);
			stars.put("2", two);
			stars.put("3", three);
			stars.put("4", four);
			stars.put("5", five);
			return stars;
		}

	}

	/**
	 * The public view of one target's reviews, repeated on every page. {@code ratingAverage} is null below three ratings, and
	 * is written as null rather than left out.
	 */
	public record PublicReviews(@JsonInclude(JsonInclude.Include.ALWAYS) Double ratingAverage, int ratingCount,
			Map<String, Integer> stars, PageResponse<PublicReview> page) {
	}

	/**
	 * A review with the first name and the last initial of its author, and the author's profile picture when there is one.
	 * {@code photoUrl} is null for an author with no picture. It points at the review, not at the student.
	 */
	public record PublicReview(UUID id, String name, int rating, String comment, Instant createdAt,
			@JsonInclude(JsonInclude.Include.ALWAYS) String photoUrl) {
	}

	/** A featured review, with the exam or package it is about. {@code kind} is {@code exam} or {@code package}. */
	public record FeaturedReview(UUID id, String name, int rating, String comment, Instant createdAt, String kind,
			UUID targetId, String targetTitleEn, String targetTitleBn,
			@JsonInclude(JsonInclude.Include.ALWAYS) String photoUrl) {
	}

	/** The bytes of a review author's picture, ready for {@code FileResponses}. */
	public record ReviewPhoto(InputStream body, String contentType, long byteSize) {
	}

	private record StarCount(UUID target, int rating, long total) {
	}

	private record FeaturedRow(FeaturedReview review, UUID categoryId) {
	}

}
