package com.coachingcenter.api.purchases;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookOrderRepository extends JpaRepository<BookOrder, UUID> {

	List<BookOrder> findByStudentIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID studentId);

	Page<BookOrder> findByDeletedAtIsNullOrderByCreatedAtDesc(Pageable pageable);

	Optional<BookOrder> findByIdAndDeletedAtIsNull(UUID id);

}
