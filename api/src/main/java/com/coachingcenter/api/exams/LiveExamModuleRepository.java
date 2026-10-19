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

	@Query(value = """
			SELECT m FROM LiveExamModule m
			WHERE m.deletedAt IS NULL AND m.status = 'PUBLISHED'
			  AND m.categoryId NOT IN :hiddenCategoryIds
			  AND EXISTS (
			    SELECT r FROM LiveExamRoster r
			    WHERE r.moduleId = m.id AND r.studentId = :studentId
			  )
			""", countQuery = """
			SELECT count(m) FROM LiveExamModule m
			WHERE m.deletedAt IS NULL AND m.status = 'PUBLISHED'
			  AND m.categoryId NOT IN :hiddenCategoryIds
			  AND EXISTS (
			    SELECT r FROM LiveExamRoster r
			    WHERE r.moduleId = m.id AND r.studentId = :studentId
			  )
			""")
	Page<LiveExamModule> findVisibleToStudent(@Param("studentId") UUID studentId,
			@Param("hiddenCategoryIds") Set<UUID> hiddenCategoryIds, Pageable pageable);

	@Query(value = """
			SELECT m FROM LiveExamModule m
			WHERE m.deletedAt IS NULL AND m.status = 'PUBLISHED' AND m.categoryId = :categoryId
			  AND m.categoryId NOT IN :hiddenCategoryIds
			  AND EXISTS (
			    SELECT r FROM LiveExamRoster r
			    WHERE r.moduleId = m.id AND r.studentId = :studentId
			  )
			""", countQuery = """
			SELECT count(m) FROM LiveExamModule m
			WHERE m.deletedAt IS NULL AND m.status = 'PUBLISHED' AND m.categoryId = :categoryId
			  AND m.categoryId NOT IN :hiddenCategoryIds
			  AND EXISTS (
			    SELECT r FROM LiveExamRoster r
			    WHERE r.moduleId = m.id AND r.studentId = :studentId
			  )
			""")
	Page<LiveExamModule> findVisibleToStudentInCategory(@Param("studentId") UUID studentId,
			@Param("categoryId") UUID categoryId, @Param("hiddenCategoryIds") Set<UUID> hiddenCategoryIds,
			Pageable pageable);

}
