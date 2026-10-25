package com.coachingcenter.api.exams;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LiveExamModuleRepository extends JpaRepository<LiveExamModule, UUID> {

	Optional<LiveExamModule> findByIdAndDeletedAtIsNull(UUID id);

	List<LiveExamModule> findByDeletedAtIsNull();

	List<LiveExamModule> findByCategoryIdAndDeletedAtIsNull(UUID categoryId);

	Page<LiveExamModule> findByDeletedAtIsNull(Pageable pageable);

	Page<LiveExamModule> findByCategoryIdAndDeletedAtIsNull(UUID categoryId, Pageable pageable);

	Page<LiveExamModule> findByStatusAndDeletedAtIsNull(String status, Pageable pageable);

	Page<LiveExamModule> findByCategoryIdAndStatusAndDeletedAtIsNull(UUID categoryId, String status, Pageable pageable);

	Page<LiveExamModule> findByStatusAndDeletedAtIsNullAndCategoryIdNotIn(String status, Set<UUID> categoryIds,
			Pageable pageable);

	@Query(value = """
			SELECT m FROM LiveExamModule m
			WHERE m.deletedAt IS NULL AND m.status = 'PUBLISHED'
			  AND m.categoryId NOT IN :hiddenCategoryIds
			  AND EXISTS (
			    SELECT e FROM LiveExam e
			    WHERE e.moduleId = m.id AND e.deletedAt IS NULL AND e.status = 'PUBLISHED' AND e.examType = :examType
			  )
			""", countQuery = """
			SELECT count(m) FROM LiveExamModule m
			WHERE m.deletedAt IS NULL AND m.status = 'PUBLISHED'
			  AND m.categoryId NOT IN :hiddenCategoryIds
			  AND EXISTS (
			    SELECT e FROM LiveExam e
			    WHERE e.moduleId = m.id AND e.deletedAt IS NULL AND e.status = 'PUBLISHED' AND e.examType = :examType
			  )
			""")
	Page<LiveExamModule> findPublishedWithExamType(@Param("examType") String examType,
			@Param("hiddenCategoryIds") Set<UUID> hiddenCategoryIds, Pageable pageable);

	@Query(value = """
			SELECT m FROM LiveExamModule m
			WHERE m.deletedAt IS NULL AND m.status = 'PUBLISHED' AND m.categoryId = :categoryId
			  AND EXISTS (
			    SELECT e FROM LiveExam e
			    WHERE e.moduleId = m.id AND e.deletedAt IS NULL AND e.status = 'PUBLISHED' AND e.examType = :examType
			  )
			""", countQuery = """
			SELECT count(m) FROM LiveExamModule m
			WHERE m.deletedAt IS NULL AND m.status = 'PUBLISHED' AND m.categoryId = :categoryId
			  AND EXISTS (
			    SELECT e FROM LiveExam e
			    WHERE e.moduleId = m.id AND e.deletedAt IS NULL AND e.status = 'PUBLISHED' AND e.examType = :examType
			  )
			""")
	Page<LiveExamModule> findPublishedInCategoryWithExamType(@Param("categoryId") UUID categoryId,
			@Param("examType") String examType, Pageable pageable);

	@Query(value = """
			SELECT m FROM LiveExamModule m
			WHERE m.deletedAt IS NULL
			  AND EXISTS (
			    SELECT i FROM LiveExamInstructor i
			    WHERE i.moduleId = m.id AND i.instructorId = :instructorId
			  )
			""", countQuery = """
			SELECT count(m) FROM LiveExamModule m
			WHERE m.deletedAt IS NULL
			  AND EXISTS (
			    SELECT i FROM LiveExamInstructor i
			    WHERE i.moduleId = m.id AND i.instructorId = :instructorId
			  )
			""")
	Page<LiveExamModule> findAssignedToInstructor(@Param("instructorId") UUID instructorId, Pageable pageable);

	/** Staff list. {@code status} is empty for any status, {@code pattern} is a lower-case LIKE pattern on either title. */
	@Query("""
			SELECT m FROM LiveExamModule m
			WHERE m.deletedAt IS NULL AND (:anyCategory = true OR m.categoryId = :categoryId)
			AND (:status = '' OR m.status = :status)
			AND (lower(m.titleEn) LIKE :pattern OR lower(m.titleBn) LIKE :pattern)
			""")
	Page<LiveExamModule> searchForStaff(@Param("anyCategory") boolean anyCategory, @Param("categoryId") UUID categoryId,
			@Param("status") String status, @Param("pattern") String pattern, Pageable pageable);

	/**
	 * Public search with a title pattern. Hidden categories are left out. {@code examType} applies only when
	 * {@code anyType} is false, and keeps a module that has a published exam of that type.
	 */
	@Query("""
			SELECT m FROM LiveExamModule m
			WHERE m.deletedAt IS NULL AND m.status = 'PUBLISHED' AND m.categoryId NOT IN :hidden
			AND (:anyCategory = true OR m.categoryId = :categoryId)
			AND (lower(m.titleEn) LIKE :pattern OR lower(m.titleBn) LIKE :pattern)
			AND (:anyType = true OR EXISTS (SELECT e.id FROM LiveExam e WHERE e.moduleId = m.id AND e.deletedAt IS NULL
			     AND e.status = 'PUBLISHED' AND e.examType = :examType))
			""")
	Page<LiveExamModule> searchPublished(@Param("hidden") java.util.Set<UUID> hidden,
			@Param("anyCategory") boolean anyCategory, @Param("categoryId") UUID categoryId,
			@Param("pattern") String pattern, @Param("anyType") boolean anyType, @Param("examType") String examType,
			Pageable pageable);

	/** {@code examType} is {@link LiveExamTypeFilter#ANY} for every type. */
	@Query(value = """
			SELECT m FROM LiveExamModule m
			WHERE m.deletedAt IS NULL AND m.status = 'PUBLISHED'
			  AND m.categoryId NOT IN :hiddenCategoryIds
			  AND (
			    EXISTS (
			      SELECT r FROM LiveExamRoster r
			      WHERE r.moduleId = m.id AND r.studentId = :studentId
			    )
			    OR EXISTS (
			      SELECT g FROM LiveExamGrant g
			      WHERE g.moduleId = m.id AND g.studentId = :studentId
			    )
			  )
			  AND (:examType = '' OR EXISTS (
			    SELECT e FROM LiveExam e
			    WHERE e.moduleId = m.id AND e.deletedAt IS NULL AND e.status = 'PUBLISHED' AND e.examType = :examType
			  ))
			""", countQuery = """
			SELECT count(m) FROM LiveExamModule m
			WHERE m.deletedAt IS NULL AND m.status = 'PUBLISHED'
			  AND m.categoryId NOT IN :hiddenCategoryIds
			  AND (
			    EXISTS (
			      SELECT r FROM LiveExamRoster r
			      WHERE r.moduleId = m.id AND r.studentId = :studentId
			    )
			    OR EXISTS (
			      SELECT g FROM LiveExamGrant g
			      WHERE g.moduleId = m.id AND g.studentId = :studentId
			    )
			  )
			  AND (:examType = '' OR EXISTS (
			    SELECT e FROM LiveExam e
			    WHERE e.moduleId = m.id AND e.deletedAt IS NULL AND e.status = 'PUBLISHED' AND e.examType = :examType
			  ))
			""")
	Page<LiveExamModule> findVisibleToStudent(@Param("studentId") UUID studentId,
			@Param("hiddenCategoryIds") Set<UUID> hiddenCategoryIds, @Param("examType") String examType,
			Pageable pageable);

	/** {@code examType} is {@link LiveExamTypeFilter#ANY} for every type. */
	@Query(value = """
			SELECT m FROM LiveExamModule m
			WHERE m.deletedAt IS NULL AND m.status = 'PUBLISHED' AND m.categoryId = :categoryId
			  AND m.categoryId NOT IN :hiddenCategoryIds
			  AND (
			    EXISTS (
			      SELECT r FROM LiveExamRoster r
			      WHERE r.moduleId = m.id AND r.studentId = :studentId
			    )
			    OR EXISTS (
			      SELECT g FROM LiveExamGrant g
			      WHERE g.moduleId = m.id AND g.studentId = :studentId
			    )
			  )
			  AND (:examType = '' OR EXISTS (
			    SELECT e FROM LiveExam e
			    WHERE e.moduleId = m.id AND e.deletedAt IS NULL AND e.status = 'PUBLISHED' AND e.examType = :examType
			  ))
			""", countQuery = """
			SELECT count(m) FROM LiveExamModule m
			WHERE m.deletedAt IS NULL AND m.status = 'PUBLISHED' AND m.categoryId = :categoryId
			  AND m.categoryId NOT IN :hiddenCategoryIds
			  AND (
			    EXISTS (
			      SELECT r FROM LiveExamRoster r
			      WHERE r.moduleId = m.id AND r.studentId = :studentId
			    )
			    OR EXISTS (
			      SELECT g FROM LiveExamGrant g
			      WHERE g.moduleId = m.id AND g.studentId = :studentId
			    )
			  )
			  AND (:examType = '' OR EXISTS (
			    SELECT e FROM LiveExam e
			    WHERE e.moduleId = m.id AND e.deletedAt IS NULL AND e.status = 'PUBLISHED' AND e.examType = :examType
			  ))
			""")
	Page<LiveExamModule> findVisibleToStudentInCategory(@Param("studentId") UUID studentId,
			@Param("categoryId") UUID categoryId, @Param("hiddenCategoryIds") Set<UUID> hiddenCategoryIds,
			@Param("examType") String examType, Pageable pageable);

}
