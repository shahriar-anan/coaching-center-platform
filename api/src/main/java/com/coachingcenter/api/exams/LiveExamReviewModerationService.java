package com.coachingcenter.api.exams;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.error.FieldErrorDetail;
import com.coachingcenter.api.common.web.PageResponse;
import com.coachingcenter.api.files.LocalFileStorage;
import com.coachingcenter.api.users.StudentPhoto;
import com.coachingcenter.api.users.StudentPhotoRepository;
import com.coachingcenter.api.users.StudentProfile;
import com.coachingcenter.api.users.StudentProfileRepository;
import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.databind.JsonNode;

/**
 * Staff moderation of reviews (reviews plan, sections 4 and 6). System Admins and the Master Admin read and change the
 * whole queue. An instructor reads the reviews of the exams they teach, and of the packages that hold one of them, and
 * changes nothing. Every write is audited.
 */
@Service
public class LiveExamReviewModerationService {

	private static final int MAX_REASON = 300;

	private static final int MAX_SEARCH = 100;

	private static final String HIDE_REASON_REQUIRED = "A reason is required to hide a review.";

	private final EntityManager entityManager;

	private final LiveExamReviewRepository reviews;

	private final LiveExamReviewTargets targets;

	private final StudentProfileRepository students;

	private final AuditRecorder audit;

	private final Clock clock;

	private final StudentPhotoRepository photos;

	private final LocalFileStorage fileStorage;

	public LiveExamReviewModerationService(EntityManager entityManager, LiveExamReviewRepository reviews,
			LiveExamReviewTargets targets, StudentProfileRepository students, AuditRecorder audit, Clock clock,
			StudentPhotoRepository photos, LocalFileStorage fileStorage) {
		this.entityManager = entityManager;
		this.reviews = reviews;
		this.targets = targets;
		this.students = students;
		this.audit = audit;
		this.clock = clock;
		this.photos = photos;
		this.fileStorage = fileStorage;
	}

	/**
	 * The author's picture of one review in the staff queue, for the same readers and the same rows the queue shows. An
	 * instructor gets only the reviews of their own exams; any other review is 404. The student's id is never in the URL.
	 */
	@Transactional(readOnly = true)
	public LiveExamReviewPublicService.ReviewPhoto photoOf(CurrentActor actor, UUID reviewId) {
		Scope scope = scope(actor, Filters.parse(null, null, null, null, null, null, null));
		var query = entityManager.createQuery("select r.studentId" + scope.where() + " and r.id = :rid", UUID.class);
		scope.params().forEach(query::setParameter);
		query.setParameter("rid", reviewId);
		UUID studentId = query.getResultStream().findFirst().orElseThrow(LiveExamReviewModerationService::notFound);
		StudentPhoto photo = photos.findById(studentId).orElseThrow(LiveExamReviewModerationService::notFound);
		Path path = fileStorage.resolve(photo.getStoredName());
		if (!Files.exists(path)) {
			throw notFound();
		}
		try {
			return new LiveExamReviewPublicService.ReviewPhoto(Files.newInputStream(path), photo.getContentType(),
					photo.getByteSize());
		}
		catch (java.io.IOException exception) {
			throw notFound();
		}
	}

	private static ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	/**
	 * The queue, in the order {@code sort} names (newest first by default, ties by id). Every filter is optional; see
	 * {@link Filters}.
	 */
	@Transactional(readOnly = true)
	public PageResponse<StaffRow> list(CurrentActor actor, Filters filters, int page, int size) {
		Scope scope = scope(actor, filters);
		var query = entityManager.createQuery("select r" + scope.where() + " order by " + filters.sort().order(),
				LiveExamReview.class);
		var count = entityManager.createQuery("select count(r)" + scope.where(), Long.class);
		scope.params().forEach((name, value) -> {
			query.setParameter(name, value);
			count.setParameter(name, value);
		});
		long total = count.getSingleResult();
		List<LiveExamReview> rows = query.setFirstResult(page * size).setMaxResults(size).getResultList();
		List<StaffRow> content = rows(rows);
		int totalPages = (int) ((total + size - 1) / size);
		return new PageResponse<>(content, page, size, total, totalPages);
	}

	/**
	 * The tab counts for the same filters as the queue, except the status: pending, approved, hidden and featured. The
	 * status of {@code filters} is ignored, so the tab badges stay right under any filter.
	 */
	@Transactional(readOnly = true)
	public Counts counts(CurrentActor actor, Filters filters) {
		Scope scope = scope(actor, filters);
		var query = entityManager.createQuery("select r.status, r.featured, count(r)" + scope.where()
				+ " group by r.status, r.featured", Object[].class);
		scope.params().forEach((name, value) -> query.setParameter(name, value));
		long pending = 0;
		long approved = 0;
		long hidden = 0;
		long featured = 0;
		for (Object[] row : query.getResultList()) {
			String status = (String) row[0];
			long n = (Long) row[2];
			if (LiveExamReview.PENDING.equals(status)) {
				pending += n;
			}
			else if (LiveExamReview.APPROVED.equals(status)) {
				approved += n;
			}
			else if (LiveExamReview.HIDDEN.equals(status)) {
				hidden += n;
			}
			if ((Boolean) row[1]) {
				featured += n;
			}
		}
		return new Counts(pending, approved, hidden, featured);
	}

	/** The number of PENDING reviews, for the sidebar badge. */
	@Transactional(readOnly = true)
	public PendingCount pendingCount() {
		return new PendingCount(reviews.countByStatus(LiveExamReview.PENDING));
	}

	/**
	 * Approves or hides a review. Hiding needs a reason of at most 300 characters, and clears the featured flag. Approving
	 * clears the reason.
	 */
	@Transactional
	public StaffRow setStatus(CurrentActor actor, UUID id, JsonNode body) {
		LiveExamReview review = find(id);
		JsonNode statusNode = body == null ? null : body.get("status");
		String status = statusNode != null && statusNode.isTextual() ? statusNode.textValue() : null;
		List<FieldErrorDetail> errors = new ArrayList<>();
		if (!LiveExamReview.APPROVED.equals(status) && !LiveExamReview.HIDDEN.equals(status)) {
			errors.add(new FieldErrorDetail("status", "Status must be APPROVED or HIDDEN."));
		}
		String reason = null;
		if (LiveExamReview.HIDDEN.equals(status)) {
			JsonNode node = body.get("reason");
			String text = node == null || !node.isTextual() ? "" : node.textValue().trim();
			if (text.isEmpty()) {
				errors.add(new FieldErrorDetail("reason", HIDE_REASON_REQUIRED));
			}
			else if (text.codePointCount(0, text.length()) > MAX_REASON) {
				errors.add(new FieldErrorDetail("reason", "Reason can be at most 300 characters."));
			}
			else {
				reason = text;
			}
		}
		if (!errors.isEmpty()) {
			throw validation(errors);
		}
		Instant now = clock.instant();
		if (LiveExamReview.APPROVED.equals(status)) {
			review.setStatus(LiveExamReview.APPROVED);
			review.setHideReason(null);
			audit.record(AuditRecorder.LIVE_EXAM_REVIEW_APPROVE, AuditRecorder.LIVE_EXAM_REVIEW_ENTITY, id);
		}
		else {
			review.setStatus(LiveExamReview.HIDDEN);
			review.setHideReason(reason);
			review.setFeatured(false);
			audit.record(AuditRecorder.LIVE_EXAM_REVIEW_HIDE, AuditRecorder.LIVE_EXAM_REVIEW_ENTITY, id);
		}
		review.setModeratedBy(actor.userId());
		review.setModeratedAt(now);
		review.setUpdatedAt(now);
		return rows(List.of(reviews.save(review))).get(0);
	}

	/**
	 * Features or unfeatures a review. Only an APPROVED review with a comment can be featured, so any other review gets 409
	 * {@code REVIEW_NOT_FEATURABLE}.
	 */
	@Transactional
	public StaffRow setFeatured(CurrentActor actor, UUID id, JsonNode body) {
		LiveExamReview review = find(id);
		JsonNode node = body == null ? null : body.get("featured");
		if (node == null || !node.isBoolean()) {
			throw validation(List.of(new FieldErrorDetail("featured", "Featured must be true or false.")));
		}
		boolean featured = node.booleanValue();
		if (!LiveExamReview.APPROVED.equals(review.getStatus()) || review.getComment() == null) {
			throw new ApiException(ErrorCode.REVIEW_NOT_FEATURABLE, HttpStatus.CONFLICT,
					"Only an approved review with a comment can be featured.");
		}
		Instant now = clock.instant();
		review.setFeatured(featured);
		review.setModeratedBy(actor.userId());
		review.setModeratedAt(now);
		review.setUpdatedAt(now);
		audit.record(featured ? AuditRecorder.LIVE_EXAM_REVIEW_FEATURE : AuditRecorder.LIVE_EXAM_REVIEW_UNFEATURE,
				AuditRecorder.LIVE_EXAM_REVIEW_ENTITY, id);
		return rows(List.of(reviews.save(review))).get(0);
	}

	private LiveExamReview find(UUID id) {
		return reviews.findById(id).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND,
				"Resource not found."));
	}

	/**
	 * The JPQL from-and-where for the queue and the counts, with its parameters. An instructor's scope is their own exams
	 * (and the packages that hold one of them).
	 */
	private static Scope scope(CurrentActor actor, Filters filters) {
		Map<String, Object> params = new LinkedHashMap<>();
		StringBuilder where = new StringBuilder(" from LiveExamReview r where 1 = 1");
		if (filters.status() != null) {
			where.append(" and r.status = :status");
			params.put("status", filters.status());
		}
		if (filters.kind() == LiveExamReviewKind.EXAM) {
			where.append(" and r.examId is not null");
		}
		else if (filters.kind() == LiveExamReviewKind.PACKAGE) {
			where.append(" and r.moduleId is not null");
		}
		if (filters.targetId() != null) {
			where.append(" and (r.examId = :target or r.moduleId = :target)");
			params.put("target", filters.targetId());
		}
		if (filters.rating() != null) {
			where.append(" and r.rating = :rating");
			params.put("rating", filters.rating().shortValue());
		}
		if (filters.featured() != null) {
			where.append(" and r.featured = :featured");
			params.put("featured", filters.featured());
		}
		if (filters.q() != null) {
			where.append(" and r.studentId in (select sp.userId from StudentProfile sp, User u where u.id = sp.userId"
					+ " and (lower(sp.fullName) like :q escape '!' or lower(sp.studentCode) like :q escape '!'"
					+ " or lower(u.phone) like :q escape '!'))");
			params.put("q", "%" + escapeLike(filters.q().toLowerCase(Locale.ROOT)) + "%");
		}
		if (actor.role() == Role.INSTRUCTOR) {
			where.append(" and (r.examId in (select e.id from LiveExam e where e.instructorId = :actor and e.deletedAt is null)"
					+ " or r.moduleId in (select pe.moduleId from LiveExamPackageExam pe, LiveExam e"
					+ " where pe.examId = e.id and pe.removedAt is null and e.instructorId = :actor and e.deletedAt is null))");
			params.put("actor", actor.userId());
		}
		return new Scope(where.toString(), params);
	}

	/** Escapes the LIKE wildcards in user text. The escape character is {@code !}, as in the query. */
	private static String escapeLike(String text) {
		return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
	}

	/** The queue rows for a list of reviews, with the titles of their targets and the students' names, read in one go each. */
	private List<StaffRow> rows(List<LiveExamReview> rows) {
		List<UUID> examIds = rows.stream().filter(row -> row.getExamId() != null).map(LiveExamReview::getExamId).toList();
		List<UUID> moduleIds = rows.stream().filter(row -> row.getModuleId() != null).map(LiveExamReview::getModuleId)
			.toList();
		Map<UUID, LiveExamReviewTargets.Target> examTargets = targets.load(LiveExamReviewKind.EXAM, examIds, true);
		Map<UUID, LiveExamReviewTargets.Target> moduleTargets = targets.load(LiveExamReviewKind.PACKAGE, moduleIds, true);
		Map<UUID, String> names = new HashMap<>();
		List<UUID> studentIds = rows.stream().map(LiveExamReview::getStudentId).distinct().toList();
		for (StudentProfile profile : students.findAllById(studentIds)) {
			names.put(profile.getUserId(), profile.getFullName());
		}
		Map<UUID, StudentPhoto> pictures = new HashMap<>();
		for (StudentPhoto photo : photos.findAllById(studentIds)) {
			pictures.put(photo.getUserId(), photo);
		}
		List<StaffRow> result = new ArrayList<>();
		for (LiveExamReview row : rows) {
			LiveExamReviewTargets.Target target = row.getExamId() != null ? examTargets.get(row.getExamId())
					: moduleTargets.get(row.getModuleId());
			StudentPhoto picture = pictures.get(row.getStudentId());
			String photoUrl = picture == null || picture.getUpdatedAt() == null ? null
					: "/api/v1/staff/reviews/" + row.getId() + "/photo?v=" + picture.getUpdatedAt().toEpochMilli();
			result.add(new StaffRow(row.getId(), target == null ? null : target.kind().path(),
					target == null ? null : target.id(), target == null ? null : target.titleEn(),
					target == null ? null : target.titleBn(), row.getStudentId(), names.getOrDefault(row.getStudentId(), ""),
					row.getRating(), row.getComment(), row.getStatus(), row.getHideReason(), row.isFeatured(),
					row.getCreatedAt(), row.getUpdatedAt(), row.getModeratedAt(), photoUrl));
		}
		return result;
	}

	private static ApiException validation(List<FieldErrorDetail> errors) {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.", errors);
	}

	/**
	 * The queue's filters, checked once from the raw request values. Every field is optional. {@code q} matches the student's
	 * full name, student code or phone, case-insensitively and in part. A blank {@code q} is no search. {@code status} is
	 * null for the counts, which ignore it.
	 */
	public record Filters(String status, LiveExamReviewKind kind, UUID targetId, Integer rating, Boolean featured, String q,
			Sort sort) {

		/** The order of the queue. Each order ends with the review id, so the paging is stable. */
		public enum Sort {

			NEWEST("newest", "r.createdAt desc, r.id desc"),

			OLDEST("oldest", "r.createdAt asc, r.id asc"),

			LOWEST("lowest", "r.rating asc, r.createdAt desc, r.id desc"),

			HIGHEST("highest", "r.rating desc, r.createdAt desc, r.id desc");

			private final String path;

			private final String order;

			Sort(String path, String order) {
				this.path = path;
				this.order = order;
			}

			String order() {
				return order;
			}

			/** The sort named by a request value, or null for any other word. */
			static Sort of(String value) {
				for (Sort sort : values()) {
					if (sort.path.equals(value)) {
						return sort;
					}
				}
				return null;
			}

		}

		public static Filters parse(String status, String kind, UUID targetId, Integer rating, String featured, String q,
				String sort) {
			List<FieldErrorDetail> errors = new ArrayList<>();
			if (status != null && !List.of(LiveExamReview.PENDING, LiveExamReview.APPROVED, LiveExamReview.HIDDEN).contains(status)) {
				errors.add(new FieldErrorDetail("status", "Status must be PENDING, APPROVED or HIDDEN."));
			}
			LiveExamReviewKind parsedKind = kind == null ? null : LiveExamReviewKind.of(kind);
			if (kind != null && parsedKind == null) {
				errors.add(new FieldErrorDetail("kind", "Kind must be exam or package."));
			}
			if (rating != null && (rating < 1 || rating > 5)) {
				errors.add(new FieldErrorDetail("rating", "Rating must be from 1 to 5."));
			}
			Boolean parsedFeatured = null;
			if (featured != null) {
				if ("true".equals(featured) || "false".equals(featured)) {
					parsedFeatured = Boolean.valueOf(featured);
				}
				else {
					errors.add(new FieldErrorDetail("featured", "Featured must be true or false."));
				}
			}
			String search = q == null || q.isBlank() ? null : q.trim();
			if (search != null && search.codePointCount(0, search.length()) > MAX_SEARCH) {
				errors.add(new FieldErrorDetail("q", "Search can be at most 100 characters."));
			}
			Sort parsedSort = Sort.NEWEST;
			if (sort != null) {
				parsedSort = Sort.of(sort);
				if (parsedSort == null) {
					errors.add(new FieldErrorDetail("sort", "Sort must be newest, oldest, lowest or highest."));
				}
			}
			if (!errors.isEmpty()) {
				throw validation(errors);
			}
			return new Filters(status, parsedKind, targetId, rating, parsedFeatured, search, parsedSort);
		}

	}

	private record Scope(String where, Map<String, Object> params) {
	}

	/** The tab counts. {@code featured} counts the featured reviews, which are all approved. */
	public record Counts(long pending, long approved, long hidden, long featured) {
	}

	/**
	 * One queue row. {@code comment}, {@code hideReason}, {@code moderatedAt} and {@code photoUrl} are always written, null
	 * included. {@code photoUrl} is the author's picture on the staff route, or null when there is none.
	 */
	public record StaffRow(UUID id, String kind, UUID targetId, String targetTitleEn, String targetTitleBn, UUID studentId,
			String studentName, int rating, @JsonInclude(JsonInclude.Include.ALWAYS) String comment, String status,
			@JsonInclude(JsonInclude.Include.ALWAYS) String hideReason, boolean featured, Instant createdAt,
			Instant updatedAt, @JsonInclude(JsonInclude.Include.ALWAYS) Instant moderatedAt,
			@JsonInclude(JsonInclude.Include.ALWAYS) String photoUrl) {
	}

	public record PendingCount(long count) {
	}

}
