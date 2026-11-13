package com.coachingcenter.api.offline;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OfflineClassPaymentRepository extends JpaRepository<OfflineClassPayment, UUID> {

	/** The payments of one class, newest first. */
	List<OfflineClassPayment> findByClassIdOrderByRecordedAtDesc(UUID classId);

}
