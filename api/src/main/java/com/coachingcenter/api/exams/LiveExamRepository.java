package com.coachingcenter.api.exams;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

/**
 * An exam belongs to a package through its membership rows (NR-01). The "by module" queries below join the active
 * membership, so they list an exam under every package it is in. Until NR-01 every exam had one membership, so the
 * results are unchanged.
 */
public interface LiveExamRepository extends JpaRepository<LiveExam, UUID> {

	Optional<LiveExam> findByIdAndDeletedAtIsNull(UUID id);

	Optional<LiveExam> findByTitleEnAndDeletedAtIsNull(String titleEn);

	@Query(value = """
			SELECT e FROM LiveExam e
			WHERE e.deletedAt IS NULL AND EXISTS (
			  SELECT pm FROM LiveExamPackageExam pm
			  WHERE pm.examId = e.id AND pm.moduleId = :moduleId AND pm.removedAt IS NULL
			)
			""", countQuery = """
			SELECT count(e) FROM LiveExam e
			WHERE e.deletedAt IS NULL AND EXISTS (
			  SELECT pm FROM LiveExamPackageExam pm
			  WHERE pm.examId = e.id AND pm.moduleId = :moduleId AND pm.removedAt IS NULL
			)
			""")
	Page<LiveExam> findByModuleIdAndDeletedAtIsNull(@Param("moduleId") UUID moduleId, Pageable pageable);

	@Query(value = """
			SELECT e FROM LiveExam e
			WHERE e.deletedAt IS NULL AND e.status = :status AND EXISTS (
			  SELECT pm FROM LiveExamPackageExam pm
			  WHERE pm.examId = e.id AND pm.moduleId = :moduleId AND pm.removedAt IS NULL
			)
			""", countQuery = """
			SELECT count(e) FROM LiveExam e
			WHERE e.deletedAt IS NULL AND e.status = :status AND EXISTS (
			  SELECT pm FROM LiveExamPackageExam pm
			  WHERE pm.examId = e.id AND pm.moduleId = :moduleId AND pm.removedAt IS NULL
			)
			""")
	Page<LiveExam> findByModuleIdAndStatusAndDeletedAtIsNull(@Param("moduleId") UUID moduleId,
			@Param("status") String status, Pageable pageable);

	@Query(value = """
			SELECT e FROM LiveExam e
			WHERE e.deletedAt IS NULL AND e.status = :status AND e.examType = :examType AND EXISTS (
			  SELECT pm FROM LiveExamPackageExam pm
			  WHERE pm.examId = e.id AND pm.moduleId = :moduleId AND pm.removedAt IS NULL
			)
			""", countQuery = """
			SELECT count(e) FROM LiveExam e
			WHERE e.deletedAt IS NULL AND e.status = :status AND e.examType = :examType AND EXISTS (
			  SELECT pm FROM LiveExamPackageExam pm
			  WHERE pm.examId = e.id AND pm.moduleId = :moduleId AND pm.removedAt IS NULL
			)
			""")
	Page<LiveExam> findByModuleIdAndStatusAndExamTypeAndDeletedAtIsNull(@Param("moduleId") UUID moduleId,
			@Param("status") String status, @Param("examType") String examType, Pageable pageable);

	@Query("""
			SELECT e FROM LiveExam e
			WHERE e.deletedAt IS NULL AND e.status = :status AND EXISTS (
			  SELECT pm FROM LiveExamPackageExam pm
			  WHERE pm.examId = e.id AND pm.moduleId = :moduleId AND pm.removedAt IS NULL
			)
			""")
	List<LiveExam> findByModuleIdAndStatusAndDeletedAtIsNull(@Param("moduleId") UUID moduleId,
			@Param("status") String status);

	/** Rows of package id, exam type and the number of published exams of that type. */
	@Query("""
			SELECT pm.moduleId, e.examType, count(e) FROM LiveExam e, LiveExamPackageExam pm
			WHERE pm.examId = e.id AND pm.removedAt IS NULL
			  AND e.deletedAt IS NULL AND e.status = 'PUBLISHED' AND pm.moduleId IN :moduleIds
			GROUP BY pm.moduleId, e.examType
			""")
	List<Object[]> countPublishedByModuleAndType(@Param("moduleIds") Collection<UUID> moduleIds);

	/** Rows of package id, exam type and the number of live exams of that type in any status. Staff counts only. */
	@Query("""
			SELECT pm.moduleId, e.examType, count(e) FROM LiveExam e, LiveExamPackageExam pm
			WHERE pm.examId = e.id AND pm.removedAt IS NULL
			  AND e.deletedAt IS NULL AND pm.moduleId IN :moduleIds
			GROUP BY pm.moduleId, e.examType
			""")
	List<Object[]> countByModuleAndType(@Param("moduleIds") Collection<UUID> moduleIds);

	/**
	 * Published exams the student holds: through a roster row or a grant on a published package the exam is in, or through an
	 * exam grant on the exam itself (which needs no package, so a standalone exam is found). The home category must be open.
	 */
	@Query("""
			SELECT e FROM LiveExam e
			WHERE e.deletedAt IS NULL AND e.status = 'PUBLISHED' AND e.categoryId NOT IN :hiddenCategoryIds
			  AND (
			    EXISTS (
			      SELECT pm FROM LiveExamPackageExam pm, LiveExamModule m
			      WHERE pm.examId = e.id AND pm.removedAt IS NULL
			        AND m.id = pm.moduleId AND m.deletedAt IS NULL AND m.status = 'PUBLISHED'
			        AND (
			          EXISTS (
			            SELECT r FROM LiveExamRoster r
			            WHERE r.moduleId = m.id AND r.studentId = :studentId
			          )
			          OR EXISTS (
			            SELECT gm FROM LiveExamGrant gm
			            WHERE gm.scope = 'MODULE' AND gm.moduleId = m.id AND gm.studentId = :studentId
			          )
			        )
			    )
			    OR EXISTS (
			      SELECT ge FROM LiveExamGrant ge
			      WHERE ge.scope = 'EXAM' AND ge.examId = e.id AND ge.studentId = :studentId
			    )
			  )
			""")
	List<LiveExam> findVisibleRoutineCandidates(@Param("studentId") UUID studentId,
			@Param("hiddenCategoryIds") Set<UUID> hiddenCategoryIds);

	/** {@code examType} is {@link LiveExamTypeFilter#ANY} for every type. */
	@Query(value = """
			SELECT e FROM LiveExam e
			WHERE e.deletedAt IS NULL AND e.status = 'PUBLISHED' AND (:examType = '' OR e.examType = :examType)
			  AND EXISTS (
			    SELECT pm FROM LiveExamPackageExam pm
			    WHERE pm.examId = e.id AND pm.moduleId = :moduleId AND pm.removedAt IS NULL
			  )
			  AND (
			    EXISTS (
			      SELECT r FROM LiveExamRoster r
			      WHERE r.moduleId = :moduleId AND r.studentId = :studentId
			    )
			    OR EXISTS (
			      SELECT gm FROM LiveExamGrant gm
			      WHERE gm.scope = 'MODULE' AND gm.moduleId = :moduleId AND gm.studentId = :studentId
			    )
			    OR EXISTS (
			      SELECT ge FROM LiveExamGrant ge
			      WHERE ge.scope = 'EXAM' AND ge.examId = e.id AND ge.studentId = :studentId
			    )
			  )
			""", countQuery = """
			SELECT count(e) FROM LiveExam e
			WHERE e.deletedAt IS NULL AND e.status = 'PUBLISHED' AND (:examType = '' OR e.examType = :examType)
			  AND EXISTS (
			    SELECT pm FROM LiveExamPackageExam pm
			    WHERE pm.examId = e.id AND pm.moduleId = :moduleId AND pm.removedAt IS NULL
			  )
			  AND (
			    EXISTS (
			      SELECT r FROM LiveExamRoster r
			      WHERE r.moduleId = :moduleId AND r.studentId = :studentId
			    )
			    OR EXISTS (
			      SELECT gm FROM LiveExamGrant gm
			      WHERE gm.scope = 'MODULE' AND gm.moduleId = :moduleId AND gm.studentId = :studentId
			    )
			    OR EXISTS (
			      SELECT ge FROM LiveExamGrant ge
			      WHERE ge.scope = 'EXAM' AND ge.examId = e.id AND ge.studentId = :studentId
			    )
			  )
			""")
	Page<LiveExam> findAccessibleToStudent(@Param("moduleId") UUID moduleId, @Param("studentId") UUID studentId,
			@Param("examType") String examType, Pageable pageable);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select e from LiveExam e where e.id = :id and e.deletedAt is null")
	Optional<LiveExam> lockById(@Param("id") UUID id);

	@Query("""
			select e from LiveExam e
			where e.deletedAt is null and e.status = 'PUBLISHED' and e.liveAt is not null and exists (
			  select pm from LiveExamPackageExam pm
			  where pm.examId = e.id and pm.moduleId = :moduleId and pm.removedAt is null
			)
			""")
	List<LiveExam> findDatedPublishedByModuleId(@Param("moduleId") UUID moduleId);

	@Query("""
			select e from LiveExam e
			where e.deletedAt is null and e.status = 'PUBLISHED' and e.liveAt > :after and e.liveAt < :before
			""")
	List<LiveExam> findPublishedStartingBetween(@Param("after") Instant after, @Param("before") Instant before);

	/** Published exams whose window is open at {@code now}: started before it and not yet ended. */
	@Query("""
			select e from LiveExam e
			where e.deletedAt is null and e.status = 'PUBLISHED' and e.liveAt is not null and e.liveAt < :now
			  and e.endsAt > :now
			""")
	List<LiveExam> findPublishedOpenAt(@Param("now") Instant now);

	/** The longest duration of any live exam. Used as the look-back so that no running exam is missed. */
	@Query("SELECT coalesce(max(e.durationMinutes), 0) FROM LiveExam e WHERE e.deletedAt IS NULL")
	int maxDurationMinutes();

	/** True once staff released the MCQ correct answers for the exam (V39, R-4). */
	@Query(value = "SELECT answers_released_at IS NOT NULL FROM live_exams WHERE id = :id", nativeQuery = true)
	boolean answersReleased(@Param("id") UUID id);

	/**
	 * Published, dated exams in published packages outside hidden categories, starting at or after {@code since}, soonest
	 * first. The caller drops the ones that have already ended.
	 */
	@Query("""
			SELECT e FROM LiveExam e, LiveExamPackageExam pm, LiveExamModule m
			WHERE pm.examId = e.id AND pm.removedAt IS NULL AND m.id = pm.moduleId
			AND e.deletedAt IS NULL AND e.status = 'PUBLISHED' AND e.liveAt IS NOT NULL
			AND e.liveAt >= :since AND m.deletedAt IS NULL AND m.status = 'PUBLISHED' AND m.categoryId NOT IN :hidden
			AND (:anyType = true OR e.examType = :examType)
			ORDER BY e.liveAt ASC, e.id ASC
			""")
	List<LiveExam> findUpcomingPublished(@Param("since") Instant since, @Param("hidden") java.util.Set<UUID> hidden,
			@Param("anyType") boolean anyType, @Param("examType") String examType);

	@Query("""
			select u.id from User u
			where u.deletedAt is null and u.role = 'STUDENT' and u.status = 'ACTIVE'
			  and (
			    exists (
			      select r from LiveExamRoster r
			      where r.moduleId = :moduleId and r.studentId = u.id
			    )
			    or exists (
			      select gm from LiveExamGrant gm
			      where gm.scope = 'MODULE' and gm.moduleId = :moduleId and gm.studentId = u.id
			    )
			    or exists (
			      select ge from LiveExamGrant ge
			      where ge.scope = 'EXAM' and ge.examId = :examId and ge.studentId = u.id
			    )
			  )
			""")
	List<UUID> findActiveStudentsWithAccess(@Param("moduleId") UUID moduleId, @Param("examId") UUID examId);

	/**
	 * The students a notice about this exam reaches: a roster row or a package grant on any published package the exam is
	 * in now, or a grant on the exam itself, revoked grants left out. A draft, archived or deleted package gives no access,
	 * so it reaches nobody. A free exam adds nobody (contracts section 7, item 10).
	 */
	@Query("""
			select u.id from User u
			where u.deletedAt is null and u.role = 'STUDENT' and u.status = 'ACTIVE'
			  and (
			    exists (
			      select r from LiveExamRoster r, LiveExamPackageExam pm, LiveExamModule pk
			      where pm.examId = :examId and pm.removedAt is null and r.moduleId = pm.moduleId and r.studentId = u.id
			        and pk.id = pm.moduleId and pk.deletedAt is null and pk.status = 'PUBLISHED'
			    )
			    or exists (
			      select gm from LiveExamGrant gm, LiveExamPackageExam pm, LiveExamModule pk
			      where pm.examId = :examId and pm.removedAt is null and gm.scope = 'MODULE' and gm.moduleId = pm.moduleId
			        and gm.studentId = u.id and gm.revokedAt is null
			        and pk.id = pm.moduleId and pk.deletedAt is null and pk.status = 'PUBLISHED'
			    )
			    or exists (
			      select ge from LiveExamGrant ge
			      where ge.scope = 'EXAM' and ge.examId = :examId and ge.studentId = u.id and ge.revokedAt is null
			    )
			  )
			""")
	List<UUID> findActiveStudentsWithAccessToExam(@Param("examId") UUID examId);

	/** Published exams whose home category is {@code categoryId} (NR-01, B2). */
	List<LiveExam> findByCategoryIdAndStatusAndDeletedAtIsNull(UUID categoryId, String status);

	/** Published exams whose home category is not in {@code categoryIds}: the catalog's visible exams (NR-01, B2). */
	List<LiveExam> findByStatusAndDeletedAtIsNullAndCategoryIdNotIn(String status, Set<UUID> categoryIds);

	/** Every exam not deleted, in any status: the staff category tree (NR-01, B2). */
	List<LiveExam> findByDeletedAtIsNull();

	/**
	 * Published, dated exams in categories outside {@code hidden} whose window has not ended. An exam joins the catalog
	 * through its home category, not through a package (NR-01, B2). Soonest first is the caller's order.
	 */
	@Query("""
			SELECT e FROM LiveExam e
			WHERE e.deletedAt IS NULL AND e.status = 'PUBLISHED' AND e.liveAt IS NOT NULL AND e.endsAt > :now
			  AND e.categoryId NOT IN :hidden AND (:anyType = true OR e.examType = :examType)
			""")
	List<LiveExam> findUpcomingInCatalog(@Param("now") Instant now, @Param("hidden") Set<UUID> hidden,
			@Param("anyType") boolean anyType, @Param("examType") String examType);

	/**
	 * The staff list across packages (NR-01, B3). Every filter is switched off by its flag, so no parameter is null.
	 * {@code phase} is one of the {@link ExamWindow} phases, or empty for any. {@code pattern} is a lower-case LIKE
	 * pattern on either title.
	 */
	@Query(value = """
			SELECT e FROM LiveExam e
			WHERE e.deletedAt IS NULL
			  AND (:anyCategory = true OR e.categoryId = :categoryId)
			  AND (:anyPackage = true OR EXISTS (
			    SELECT pm FROM LiveExamPackageExam pm
			    WHERE pm.examId = e.id AND pm.moduleId = :packageId AND pm.removedAt IS NULL))
			  AND (:standalone = false OR NOT EXISTS (
			    SELECT pm FROM LiveExamPackageExam pm WHERE pm.examId = e.id AND pm.removedAt IS NULL))
			  AND (:anyType = true OR e.examType = :examType)
			  AND (:anyMode = true OR e.mode = :mode)
			  AND (:anyStatus = true OR e.status = :status)
			  AND (:pendingOnly = false OR EXISTS (SELECT r FROM LiveExamRevision r WHERE r.examId = e.id))
			  AND (:popularOnly = false OR e.popular = true)
			  AND (:scoped = false OR e.instructorId = :instructorId)
			  AND (:anyText = true OR lower(e.titleEn) LIKE :pattern OR lower(e.titleBn) LIKE :pattern)
			  AND (:phase = ''
			    OR (:phase = 'PRACTICE' AND e.mode = 'PRACTICE')
			    OR (:phase = 'UNANNOUNCED' AND e.mode = 'LIVE' AND e.liveAt IS NULL)
			    OR (:phase = 'UPCOMING' AND e.liveAt > :now)
			    OR (:phase = 'LIVE' AND e.liveAt <= :now AND e.endsAt > :now)
			    OR (:phase = 'ENDED' AND e.endsAt <= :now))
			""", countQuery = """
			SELECT count(e) FROM LiveExam e
			WHERE e.deletedAt IS NULL
			  AND (:anyCategory = true OR e.categoryId = :categoryId)
			  AND (:anyPackage = true OR EXISTS (
			    SELECT pm FROM LiveExamPackageExam pm
			    WHERE pm.examId = e.id AND pm.moduleId = :packageId AND pm.removedAt IS NULL))
			  AND (:standalone = false OR NOT EXISTS (
			    SELECT pm FROM LiveExamPackageExam pm WHERE pm.examId = e.id AND pm.removedAt IS NULL))
			  AND (:anyType = true OR e.examType = :examType)
			  AND (:anyMode = true OR e.mode = :mode)
			  AND (:anyStatus = true OR e.status = :status)
			  AND (:pendingOnly = false OR EXISTS (SELECT r FROM LiveExamRevision r WHERE r.examId = e.id))
			  AND (:popularOnly = false OR e.popular = true)
			  AND (:scoped = false OR e.instructorId = :instructorId)
			  AND (:anyText = true OR lower(e.titleEn) LIKE :pattern OR lower(e.titleBn) LIKE :pattern)
			  AND (:phase = ''
			    OR (:phase = 'PRACTICE' AND e.mode = 'PRACTICE')
			    OR (:phase = 'UNANNOUNCED' AND e.mode = 'LIVE' AND e.liveAt IS NULL)
			    OR (:phase = 'UPCOMING' AND e.liveAt > :now)
			    OR (:phase = 'LIVE' AND e.liveAt <= :now AND e.endsAt > :now)
			    OR (:phase = 'ENDED' AND e.endsAt <= :now))
			""")
	Page<LiveExam> searchForStaff(@Param("anyCategory") boolean anyCategory, @Param("categoryId") UUID categoryId,
			@Param("anyPackage") boolean anyPackage, @Param("packageId") UUID packageId,
			@Param("standalone") boolean standalone, @Param("anyType") boolean anyType,
			@Param("examType") String examType, @Param("anyMode") boolean anyMode, @Param("mode") String mode,
			@Param("anyStatus") boolean anyStatus, @Param("status") String status,
			@Param("pendingOnly") boolean pendingOnly, @Param("popularOnly") boolean popularOnly,
			@Param("scoped") boolean scoped, @Param("instructorId") UUID instructorId, @Param("anyText") boolean anyText,
			@Param("pattern") String pattern, @Param("phase") String phase, @Param("now") Instant now, Pageable pageable);

}
