package com.coachingcenter.api.offline;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OfflineFeePaymentRepository extends JpaRepository<OfflineFeePayment, UUID> {

	List<OfflineFeePayment> findByAdmissionIdOrderByRecordedAtAsc(UUID admissionId);

}
