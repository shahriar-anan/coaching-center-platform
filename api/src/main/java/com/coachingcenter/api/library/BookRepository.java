package com.coachingcenter.api.library;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookRepository extends JpaRepository<Book, UUID> {

	Page<Book> findByDeletedAtIsNull(Pageable pageable);

	Optional<Book> findByIdAndDeletedAtIsNull(UUID id);

	@Query("""
			SELECT b FROM Book b
			WHERE b.deletedAt IS NULL AND b.available = TRUE
			  AND (:q = '' OR LOWER(b.titleEn) LIKE LOWER(CONCAT('%', :q, '%'))
			    OR LOWER(b.titleBn) LIKE LOWER(CONCAT('%', :q, '%')))
			  AND (:categoryId IS NULL OR b.categoryId = :categoryId)
			""")
	Page<Book> findCatalogBooks(@Param("q") String q, @Param("categoryId") UUID categoryId, Pageable pageable);

	@Query("""
			SELECT b FROM Book b
			WHERE b.id = :id AND b.deletedAt IS NULL AND b.available = TRUE
			""")
	Optional<Book> findCatalogBook(@Param("id") UUID id);

	Optional<Book> findByCoverFileIdAndDeletedAtIsNull(UUID coverFileId);

	List<Book> findByCategoryIdAndDeletedAtIsNull(UUID categoryId);

}
