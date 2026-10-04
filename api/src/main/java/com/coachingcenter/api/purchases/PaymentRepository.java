package com.coachingcenter.api.purchases;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

	Page<Payment> findByStatusAndDeletedAtIsNullOrderByCreatedAtDesc(String status, Pageable pageable);

	Page<Payment> findByStatusAndPayableTypeAndDeletedAtIsNullOrderByCreatedAtDesc(String status, String payableType,
			Pageable pageable);

	Optional<Payment> findByIdAndDeletedAtIsNull(UUID id);

	boolean existsByTransactionRef(String transactionRef);

	Optional<Payment> findByPayableTypeAndPayableIdAndDeletedAtIsNull(String payableType, UUID payableId);

	List<Payment> findByStudentIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID studentId);

}
