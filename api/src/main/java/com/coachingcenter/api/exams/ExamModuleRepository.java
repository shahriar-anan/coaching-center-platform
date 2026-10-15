package com.coachingcenter.api.exams;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExamModuleRepository extends JpaRepository<ExamModule, UUID> {

	Page<ExamModule> findByDeletedAtIsNull(Pageable pageable);

	Optional<ExamModule> findByIdAndDeletedAtIsNull(UUID id);

	@Query("""
			SELECT m FROM ExamModule m
			WHERE m.deletedAt IS NULL AND m.status = 'PUBLISHED' AND m.accessMode IS NOT NULL
			  AND (:q = '' OR LOWER(m.titleEn) LIKE LOWER(CONCAT('%', :q, '%'))
			    OR LOWER(m.titleBn) LIKE LOWER(CONCAT('%', :q, '%')))
			""")
	Page<ExamModule> findCatalogModules(@Param("q") String q, Pageable pageable);

	@Query("""
			SELECT m FROM ExamModule m
			WHERE m.id = :id AND m.deletedAt IS NULL AND m.status = 'PUBLISHED' AND m.accessMode IS NOT NULL
			""")
	Optional<ExamModule> findCatalogModule(@Param("id") UUID id);

}
