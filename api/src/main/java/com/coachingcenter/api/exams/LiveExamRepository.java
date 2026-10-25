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

public interface LiveExamRepository extends JpaRepository<LiveExam, UUID> {

	Optional<LiveExam> findByIdAndDeletedAtIsNull(UUID id);

	Optional<LiveExam> findByTitleEnAndDeletedAtIsNull(String titleEn);

	Page<LiveExam> findByModuleIdAndDeletedAtIsNull(UUID moduleId, Pageable pageable);

	Page<LiveExam> findByModuleIdAndStatusAndDeletedAtIsNull(UUID moduleId, String status, Pageable pageable);

	Page<LiveExam> findByModuleIdAndStatusAndExamTypeAndDeletedAtIsNull(UUID moduleId, String status, String examType,
			Pageable pageable);

	List<LiveExam> findByModuleIdAndStatusAndDeletedAtIsNull(UUID moduleId, String status);

	/** Rows of module id, exam type and the number of published exams of that type. */
	@Query("""
			SELECT e.moduleId, e.examType, count(e) FROM LiveExam e
			WHERE e.deletedAt IS NULL AND e.status = 'PUBLISHED' AND e.moduleId IN :moduleIds
			GROUP BY e.moduleId, e.examType
			""")
	List<Object[]> countPublishedByModuleAndType(@Param("moduleIds") Collection<UUID> moduleIds);

	/** Rows of module id, exam type and the number of live exams of that type in any status. Staff counts only. */
	@Query("""
			SELECT e.moduleId, e.examType, count(e) FROM LiveExam e
			WHERE e.deletedAt IS NULL AND e.moduleId IN :moduleIds
			GROUP BY e.moduleId, e.examType
			""")
	List<Object[]> countByModuleAndType(@Param("moduleIds") Collection<UUID> moduleIds);

	@Query("""
			SELECT e FROM LiveExam e
			WHERE e.deletedAt IS NULL AND e.status = 'PUBLISHED'
			  AND EXISTS (
			    SELECT m FROM LiveExamModule m
			    WHERE m.id = e.moduleId AND m.deletedAt IS NULL AND m.status = 'PUBLISHED'
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
			WHERE e.deletedAt IS NULL AND e.status = 'PUBLISHED' AND e.moduleId = :moduleId
			  AND (:examType = '' OR e.examType = :examType)
			  AND (
			    EXISTS (
			      SELECT r FROM LiveExamRoster r
			      WHERE r.moduleId = e.moduleId AND r.studentId = :studentId
			    )
			    OR EXISTS (
			      SELECT gm FROM LiveExamGrant gm
			      WHERE gm.scope = 'MODULE' AND gm.moduleId = e.moduleId AND gm.studentId = :studentId
			    )
			    OR EXISTS (
			      SELECT ge FROM LiveExamGrant ge
			      WHERE ge.scope = 'EXAM' AND ge.examId = e.id AND ge.studentId = :studentId
			    )
			  )
			""", countQuery = """
			SELECT count(e) FROM LiveExam e
			WHERE e.deletedAt IS NULL AND e.status = 'PUBLISHED' AND e.moduleId = :moduleId
			  AND (:examType = '' OR e.examType = :examType)
			  AND (
			    EXISTS (
			      SELECT r FROM LiveExamRoster r
			      WHERE r.moduleId = e.moduleId AND r.studentId = :studentId
			    )
			    OR EXISTS (
			      SELECT gm FROM LiveExamGrant gm
			      WHERE gm.scope = 'MODULE' AND gm.moduleId = e.moduleId AND gm.studentId = :studentId
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
			where e.moduleId = :moduleId and e.deletedAt is null and e.status = 'PUBLISHED' and e.liveAt is not null
			""")
	List<LiveExam> findDatedPublishedByModuleId(@Param("moduleId") UUID moduleId);

	@Query("""
			select e from LiveExam e
			where e.deletedAt is null and e.status = 'PUBLISHED' and e.liveAt > :after and e.liveAt < :before
			""")
	List<LiveExam> findPublishedStartingBetween(@Param("after") Instant after, @Param("before") Instant before);

	/**
	 * Published, dated exams in published modules outside hidden categories, starting at or after {@code since}, soonest
	 * first. The caller drops the ones that have already ended.
	 */
	@Query("""
			SELECT e FROM LiveExam e, LiveExamModule m
			WHERE e.moduleId = m.id AND e.deletedAt IS NULL AND e.status = 'PUBLISHED' AND e.liveAt IS NOT NULL
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
