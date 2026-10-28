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

	@Query("""
			SELECT e FROM LiveExam e
			WHERE e.deletedAt IS NULL AND e.status = 'PUBLISHED'
			  AND EXISTS (
			    SELECT pm FROM LiveExamPackageExam pm, LiveExamModule m
			    WHERE pm.examId = e.id AND pm.removedAt IS NULL
			      AND m.id = pm.moduleId AND m.deletedAt IS NULL AND m.status = 'PUBLISHED'
			      AND m.categoryId NOT IN :hiddenCategoryIds
			      AND (
			        EXISTS (
			          SELECT r FROM LiveExamRoster r
			          WHERE r.moduleId = m.id AND r.studentId = :studentId
			        )
			        OR EXISTS (
			          SELECT gm FROM LiveExamGrant gm
			          WHERE gm.scope = 'MODULE' AND gm.moduleId = m.id AND gm.studentId = :studentId
			        )
			        OR EXISTS (
			          SELECT ge FROM LiveExamGrant ge
			          WHERE ge.scope = 'EXAM' AND ge.examId = e.id AND ge.studentId = :studentId
			        )
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

}
