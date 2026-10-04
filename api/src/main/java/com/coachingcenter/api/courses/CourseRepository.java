package com.coachingcenter.api.courses;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRepository extends JpaRepository<Course, UUID> {

	Page<Course> findByDeletedAtIsNull(Pageable pageable);

	Optional<Course> findByIdAndDeletedAtIsNull(UUID id);

	@Query("""
			SELECT c FROM Course c
			WHERE c.deletedAt IS NULL AND c.status = 'PUBLISHED' AND c.closed = FALSE
			  AND (:q = '' OR LOWER(c.titleEn) LIKE LOWER(CONCAT('%', :q, '%'))
			    OR LOWER(c.titleBn) LIKE LOWER(CONCAT('%', :q, '%')))
			  AND (:categoryFilter = FALSE OR c.categoryId IN :categoryIds)
			""")
	Page<Course> findCatalogCourses(@Param("q") String q, @Param("categoryFilter") boolean categoryFilter,
			@Param("categoryIds") List<UUID> categoryIds, Pageable pageable);

	@Query("""
			SELECT c FROM Course c
			WHERE c.id = :id AND c.deletedAt IS NULL AND c.status = 'PUBLISHED' AND c.closed = FALSE
			""")
	Optional<Course> findCatalogCourse(@Param("id") UUID id);

	Optional<Course> findByCoverFileIdAndDeletedAtIsNull(UUID coverFileId);

	List<Course> findByCategoryIdAndDeletedAtIsNull(UUID categoryId);

	List<Course> findByCategoryIdIsNotNullAndDeletedAtIsNull();

}
