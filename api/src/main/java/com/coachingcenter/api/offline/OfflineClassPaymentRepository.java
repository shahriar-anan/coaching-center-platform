package com.coachingcenter.api.offline;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OfflineClassPaymentRepository extends JpaRepository<OfflineClassPayment, UUID> {

	List<OfflineClassPayment> findByClassIdOrderByRecordedAtDesc(UUID classId);

	/** How many payments have this status (the digest counts the ones waiting for approval). */
	long countByStatus(String status);

}
