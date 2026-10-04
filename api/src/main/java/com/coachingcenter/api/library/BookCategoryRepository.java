package com.coachingcenter.api.library;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BookCategoryRepository extends JpaRepository<BookCategory, UUID> {

	Optional<BookCategory> findByIdAndDeletedAtIsNull(UUID id);

	List<BookCategory> findByDeletedAtIsNullOrderByPositionAsc();

}
