package com.coachingcenter.api.offline;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OfflineFeeAllocationRepository extends JpaRepository<OfflineFeeAllocation, UUID> {

	List<OfflineFeeAllocation> findByPaymentId(UUID paymentId);

	List<OfflineFeeAllocation> findByDueId(UUID dueId);

}
