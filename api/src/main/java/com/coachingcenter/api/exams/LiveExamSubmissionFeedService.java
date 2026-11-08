package com.coachingcenter.api.exams;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.users.AdminProfileRepository;
import com.coachingcenter.api.users.StudentProfile;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

/**
 * Written submissions across packages, for the marking dashboard and the submissions browser. Master Admin and
 * System Admin see every exam; an instructor sees only the exams assigned to them.
 */
@Service
public class LiveExamSubmissionFeedService {

	private static final Set<String> STATUSES = Set.of("AWAITING", "DRAFT", "PUBLISHED", "FAILED_TO_SUBMIT");

	private static final Set<String> SORTS = Set.of("submittedAt,desc", "submittedAt,asc", "studentName");

	@PersistenceContext
	private EntityManager entityManager;

	private final LiveExamCategoryRepository categories;

	private final AdminProfileRepository adminProfiles;

	private final LiveExamMemberships memberships;

	private final LiveExamModuleRepository modules;

	public LiveExamSubmissionFeedService(LiveExamCategoryRepository categories, AdminProfileRepository adminProfiles,
			LiveExamMemberships memberships, LiveExamModuleRepository modules) {
		this.categories = categories;
		this.adminProfiles = adminProfiles;
		this.memberships = memberships;
		this.modules = modules;
	}

	@Transactional(readOnly = true)
	public Page<FeedRow> list(CurrentActor actor, UUID moduleId, UUID examId, String status, String q, String sort,
			Pageable pageable) {
		return list(actor, moduleId, examId, null, status, q, sort, false, pageable);
	}

	/**
	 * With {@code readyOnly}, only the papers the marker flagged ready for release. With {@code categoryId}, the papers of
	 * the exams in that category and in every category under it.
	 */
	public Page<FeedRow> list(CurrentActor actor, UUID moduleId, UUID examId, UUID categoryId, String status, String q,
			String sort, boolean readyOnly, Pageable pageable) {
		String state = status == null ? "" : status.trim().toUpperCase();
		if (!state.isEmpty() && !STATUSES.contains(state)) {
			throw validationFailed();
		}
		String order = sort == null || sort.isBlank() ? "submittedAt,desc" : sort.trim();
		if (!SORTS.contains(order)) {
			throw validationFailed();
		}
		boolean instructor = actor.role() == Role.INSTRUCTOR;
		if (!instructor && actor.role() != Role.MASTER_ADMIN && actor.role() != Role.SYSTEM_ADMIN) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}

		Map<UUID, LiveExamCategory> byId = LiveExamStaffModuleService.categoriesById(categories);
		// An instructor sees the papers of the exams assigned to them, wherever the exam sits (D6); the package is display only.
		// Same exam rule as the counts (LiveExamParticipationService): published exams only. A sitting past its deadline
		// shows here once the deadline job posts it, so the counts can run up to a minute ahead of this list.
		StringBuilder where = new StringBuilder("""
				FROM LiveExamParticipant p JOIN LiveExam e ON e.id = p.examId
				LEFT JOIN StudentProfile s ON s.userId = p.studentId
				WHERE e.examType = :written AND p.deletedAt IS NULL AND e.deletedAt IS NULL AND e.status = :published
				AND p.status IN (:submitted, :failed)
				""");
		Map<String, Object> params = new HashMap<>();
		params.put("written", LiveExam.WRITTEN);
		params.put("published", LiveExam.PUBLISHED);
		params.put("submitted", LiveExamParticipant.SUBMITTED);
		params.put("failed", LiveExamParticipant.FAILED_TO_SUBMIT);
		if (instructor) {
			where.append(" AND e.instructorId = :instructorId");
			params.put("instructorId", actor.userId());
		}
		if (moduleId != null) {
			where.append("""
					 AND EXISTS (SELECT pe.id FROM LiveExamPackageExam pe
					 WHERE pe.examId = e.id AND pe.moduleId = :moduleId AND pe.removedAt IS NULL)
					""");
			params.put("moduleId", moduleId);
		}
		if (examId != null) {
			where.append(" AND e.id = :examId");
			params.put("examId", examId);
		}
		if (categoryId != null) {
			where.append(" AND e.categoryId IN (:categoryIds)");
			params.put("categoryIds", LiveExamCategoryTree.categoryAndDescendants(categoryId, byId.values()));
		}
		if (q != null && !q.isBlank()) {
			where.append(" AND lower(s.fullName) LIKE :name");
			params.put("name", "%" + q.trim().toLowerCase() + "%");
		}
		where.append(statusClause(state));
		if (readyOnly) {
			where.append(" AND p.readyForReleaseAt IS NOT NULL");
		}

		Query count = entityManager.createQuery("SELECT count(p) " + where);
		params.forEach(count::setParameter);
		long total = (Long) count.getSingleResult();

		Query rows = entityManager.createQuery("SELECT p, e, s " + where + orderBy(order));
		params.forEach(rows::setParameter);
		rows.setFirstResult((int) pageable.getOffset());
		rows.setMaxResults(pageable.getPageSize());
		List<Object[]> found = rows.getResultList();

		Map<UUID, String> evaluators = namesOf(found.stream().map(row -> evaluatorOf((LiveExamParticipant) row[0])).toList());
		Map<UUID, String> instructors = namesOf(found.stream().map(row -> ((LiveExam) row[1]).getInstructorId()).toList());
		List<FeedRow> content = new ArrayList<>();
		for (Object[] row : found) {
			LiveExamParticipant participant = (LiveExamParticipant) row[0];
			LiveExam exam = (LiveExam) row[1];
			// The student profile is optional in the data model, so a paper can have no name.
			StudentProfile student = (StudentProfile) row[2];
			String studentName = student == null ? "" : student.getFullName();
			LiveExamModule module = memberships.primaryModuleId(exam.getId()).flatMap(modules::findById).orElse(null);
			content.add(new FeedRow(participant.getId(), exam.getId(), exam.getTitleEn(), exam.getTitleBn(),
					module == null ? null : module.getId(), module == null ? null : module.getTitleEn(),
					module == null ? null : module.getTitleBn(),
					LiveExamStaffModuleService.pathOf(exam.getCategoryId(), byId), studentName,
					statusOf(participant), LiveExamParticipant.legacyKind(participant.getKind()), participant.getKind(),
					participant.getAnswerStoredFileName() != null,
					participant.getSubmittedAt(), markedAtOf(participant), participant.getPublishedAt(),
					evaluatorOf(participant) == null ? null : evaluators.get(evaluatorOf(participant)),
					exam.getInstructorId() == null ? null : instructors.get(exam.getInstructorId()),
					participant.hasPendingRevision() ? Boolean.TRUE : null,
					participant.getReadyForReleaseAt() == null ? null : Boolean.TRUE, participant.getAttemptNo()));
		}
		return new PageImpl<>(content, pageable, total);
	}

	/** The display names of the given people, read in one query. Null ids are skipped. */
	private Map<UUID, String> namesOf(List<UUID> ids) {
		List<UUID> distinct = ids.stream().filter(id -> id != null).distinct().toList();
		Map<UUID, String> names = new HashMap<>();
		if (!distinct.isEmpty()) {
			adminProfiles.findAllById(distinct).forEach(profile -> names.put(profile.getUserId(), profile.getFullName()));
		}
		return names;
	}

	/** Matches the status the row shows: failed, published, draft (marked but not published) or awaiting a mark. */
	private static String statusClause(String state) {
		return switch (state) {
			case "AWAITING" -> " AND p.status = :submitted AND p.evaluatedAt IS NULL";
			// A released paper with a correction waiting is a draft again: it needs a release.
			case "DRAFT" -> " AND p.status = :submitted AND p.evaluatedAt IS NOT NULL AND (p.publishedAt IS NULL OR p.pendingEvaluatedAt IS NOT NULL)";
			case "PUBLISHED" -> " AND p.status = :submitted AND p.publishedAt IS NOT NULL AND p.pendingEvaluatedAt IS NULL";
			case "FAILED_TO_SUBMIT" -> " AND p.status = :failed";
			default -> "";
		};
	}

	private static String orderBy(String sort) {
		return switch (sort) {
			case "submittedAt,asc" -> " ORDER BY p.submittedAt ASC NULLS LAST, p.id";
			case "studentName" -> " ORDER BY s.fullName ASC, p.id";
			default -> " ORDER BY p.submittedAt DESC NULLS LAST, p.id";
		};
	}

	private static String statusOf(LiveExamParticipant participant) {
		if (LiveExamParticipant.FAILED_TO_SUBMIT.equals(participant.getStatus())) {
			return "FAILED_TO_SUBMIT";
		}
		if (participant.hasPendingRevision()) {
			return "DRAFT";
		}
		if (participant.getPublishedAt() != null) {
			return "PUBLISHED";
		}
		return participant.getEvaluatedAt() != null ? "DRAFT" : "AWAITING";
	}

	/** The evaluator the row shows: the one who wrote the correction waiting for release, else the marker of record. */
	private static UUID evaluatorOf(LiveExamParticipant participant) {
		return participant.hasPendingRevision() ? participant.getPendingEvaluatedBy() : participant.getEvaluatedBy();
	}

	private static Instant markedAtOf(LiveExamParticipant participant) {
		return participant.hasPendingRevision() ? participant.getPendingEvaluatedAt() : participant.getEvaluatedAt();
	}

	private static ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
	}

	/**
	 * One submission. {@code evaluatorName} is staff-only; students never see this feed. {@code instructorName} is the
	 * exam's instructor, null when the exam has none.
	 */
	public record FeedRow(UUID participantId, UUID examId, String examTitleEn, String examTitleBn, UUID moduleId,
			String moduleTitleEn, String moduleTitleBn, List<LiveExamModuleService.CategoryRef> categoryPath,
			String studentName, String status, String kind, String attemptKind, boolean answerPdf, Instant submittedAt, Instant evaluatedAt,
			Instant publishedAt, String evaluatorName, String instructorName, Boolean releasePending, Boolean readyForRelease,
			int attemptNo) {
	}

}
